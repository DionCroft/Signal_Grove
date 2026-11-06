package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionOriginStorageTest {
    private suspend fun storage(block:suspend(HermesDao)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        try {block(db.dao())} finally {db.close()}
    }
    @Test fun importedOriginsSurviveRoomRoundTripWithoutChangingPendingWork()=runBlocking {storage {dao->
        val original=Conversation("existing","scope","api",pendingInput="private prompt",pendingKey="key",pendingAt=1,transport="runs",runId="run",status="unknown",pendingRequestHash="a".repeat(64),detailsJson="""{"model":"private-model"}""")
        dao.save(original);dao.mergeRemoteSession("scope",RemoteSession("api","Updated",2,"api_server"),1)
        dao.mergeRemoteSession("scope",RemoteSession("desk","Desktop",3,"desktop"),1);dao.mergeRemoteSession("scope",RemoteSession("disc","Discord",4,"discord"),1)
        val saved=dao.conversation("existing")!!;assertEquals(original.pendingKey,saved.pendingKey);assertEquals(original.pendingRequestHash,saved.pendingRequestHash);assertEquals("unknown",saved.status);assertEquals("private-model",SessionOrigin.metadata(saved.detailsJson).text("model"));assertFalse(SessionOrigin.readOnly(saved))
        assertEquals("desktop",SessionOrigin.source(dao.byRemoteId("scope","desk")!!));assertTrue(SessionOrigin.readOnly(dao.byRemoteId("scope","disc")!!))
    }}
    @Test fun sameRemoteIdStaysIsolatedAcrossConnectionPartitions()=runBlocking {storage {dao->
        dao.mergeRemoteSession("first",RemoteSession("shared","Private first",1,"desktop"),1);dao.mergeRemoteSession("second",RemoteSession("shared","Private second",2,"api_server"),1)
        val first=dao.byRemoteId("first","shared")!!;val second=dao.byRemoteId("second","shared")!!
        assertNotEquals(first.key,second.key);assertTrue(SessionOrigin.readOnly(first));assertFalse(SessionOrigin.readOnly(second));assertEquals("Private first",first.title);assertEquals("Private second",second.title)
    }}
    @Test fun refreshingPartialMetadataDoesNotRemoveReadOnlyOrigin()=runBlocking {storage {dao->
        dao.mergeRemoteSession("scope",RemoteSession("disc","Discord",1,"discord"),1);val c=dao.byRemoteId("scope","disc")!!
        dao.saveSessionDetails(c,buildJsonObject {put("id","disc");put("model","observed")})
        val saved=dao.conversation(c.key)!!;assertEquals("discord",SessionOrigin.source(saved));assertTrue(SessionOrigin.readOnly(saved));assertEquals("observed",SessionOrigin.metadata(saved.detailsJson).text("model"))
    }}
    @Test fun foreignAndUnknownOperationsDoNotCreateReceiptsOrNetworkCalls()=runBlocking {storage {dao->
        val sends=AtomicInteger();val api=object:FakeHermesApiClient() {override suspend fun sessionAction(action:SessionAction,target:String,body:JsonObject):JsonObject {sends.incrementAndGet();return super.sessionAction(action,target,body)}}
        for(source in listOf("desktop","discord","unknown")) {
            val c=Conversation(source,"scope",source,status="completed",detailsJson=SessionOrigin.retain("",source));dao.save(c)
            for(action in SessionAction.entries) {
                val body=when(action) {SessionAction.Lock->RuntimeChoice("model","provider").fields();SessionAction.Fork->buildJsonObject {put("id","child");put("title","Branch")};SessionAction.Steer->buildJsonObject {put("input","Guidance")}}
                try {SessionOperationsRepository(dao).perform(api,c,action,c.remoteId,body);fail("Foreign action admitted")} catch(_:ReadOnlySessionException) {}
            }
        }
        assertEquals(0,sends.get());assertTrue(dao.serverOperations("scope").first().isEmpty())
    }}
    @Test fun legacyApiOperationStillUsesItsNormalDurableReceipt()=runBlocking {storage {dao->
        val c=Conversation("legacy","scope","api",status="completed");dao.save(c)
        SessionOperationsRepository(dao).perform(FakeHermesApiClient(),c,SessionAction.Lock,c.remoteId,RuntimeChoice("model","provider").fields())
        assertEquals("confirmed",dao.serverOperations("scope").first().single().state)
    }}
    @Test fun inventoryRefreshPreservesLocalPinHideAndTaskData()=runBlocking {storage {dao->
        val task=LocalTask("local","Retain local task","",1);dao.save(task)
        val c=Conversation("desktop","scope","desk","Old",pinned=true,hidden=true,detailsJson=SessionOrigin.retain("","desktop"));dao.save(c)
        dao.mergeRemoteSession("scope",RemoteSession("desk","Updated",8,"desktop"),1)
        val saved=dao.conversation(c.key)!!;assertTrue(saved.pinned);assertTrue(saved.hidden);assertEquals("Updated",saved.title);assertEquals(listOf(task),dao.tasks().first())
    }}
    @Test fun realForeignHistoryReadImportsMessagesWithoutWritingTheHost()=runBlocking {storage {dao->
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"session_id":"desk","data":[{"id":"1","role":"user","content":"Private 你好","timestamp":1},{"id":"2","role":"tool","content":"not a user turn","timestamp":2},{"id":"3","role":"assistant","content":"Existing reply","timestamp":3}]}"""))
            dao.mergeRemoteSession("scope",RemoteSession("desk","Desktop",1,"desktop"),1);val c=dao.byRemoteId("scope","desk")!!
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/work/").toString(),"fixture",true),true)
            HermesSessionRepository(dao).importMessages(api,c)
            val rows=dao.history(c.key);assertEquals(listOf("user","assistant"),rows.map {it.role});assertEquals("Private 你好",rows.first().content);assertTrue(SessionOrigin.readOnly(dao.conversation(c.key)!!))
            val request=server.takeRequest();assertEquals("GET",request.method);assertEquals("/p/work/api/sessions/desk/messages?inline_images=false&limit=500&offset=0&order=oldest",request.path);assertEquals(1,server.requestCount)
        }
    }}
}
