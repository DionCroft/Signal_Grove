package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ManagedSkillsStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("skill-storage-fixture","refresh-fixture","password","fixture",4102444800)
    private fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
    private fun row(enabled:Boolean,locked:Boolean)=ManagedSkill("fixture","private-description-fixture",enabled,locked,"hub")
    private fun inventory(enabled:Boolean,locked:Boolean)="""[{"name":"fixture","description":"private-description-fixture","enabled":$enabled,"locked":$locked,"provenance":"hub"}]"""
    @Test fun claimedToggleAndLockRequireReadbackAndKeepPrivateContentOutOfReceipts()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger();var enabled=true;var locked=false
        MockWebServer().use {server ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/dashboard/api/auth/me"->json("""{"provider":"password","user_id":"fixture"}""")
                request.path=="/dashboard/api/skills?profile=worker"->json(inventory(enabled,locked))
                request.method=="PUT"->{
                    assertEquals(1,runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).size});writes.incrementAndGet()
                    val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;assertEquals("worker",body.text("profile"));assertEquals("fixture",body.text("name"))
                    if(body.containsKey("enabled")) {enabled=body["enabled"]!!.jsonPrimitive.boolean;json("""{"ok":true,"name":"fixture","enabled":$enabled}""")}
                    else {locked=body["locked"]!!.jsonPrimitive.boolean;json("""{"ok":true,"name":"fixture","locked":$locked}""")}
                }
                else->json("{}").setResponseCode(404)
            }}
            try {
                val repo=SkillOperationsRepository(db.dao(),CompletableDeferred(Unit))
                val disabled=repo.execute(target,SkillRequest(row(true,false),SkillAction.Toggle),api,tokens);assertEquals("confirmed",disabled.receipt.state);assertFalse(disabled.rows.single().enabled!!)
                val protected=repo.execute(target,SkillRequest(disabled.rows.single(),SkillAction.Lock),api,tokens);assertEquals("confirmed",protected.receipt.state);assertTrue(protected.rows.single().locked!!);assertEquals(2,writes.get())
                listOf(disabled.receipt,protected.receipt).forEach {assertFalse(it.toString().contains("private-description-fixture"));assertFalse(it.toString().contains(tokens.access))}
                db.dao().clearChats();assertEquals(protected.receipt,db.dao().managementOperation(protected.receipt.key))
            } finally {db.close()}
        }
    }
    @Test fun lostReplyIsDurableUnknownAndPreventsReplayAfterRecovery()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/api/auth/me"->json("""{"provider":"password","user_id":"fixture"}""")
                request.path=="/api/skills?profile=worker"->json(inventory(true,false))
                request.method=="PUT"->{writes.incrementAndGet();MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)}
                else->json("{}").setResponseCode(404)
            }}
            val api=ManagementClient(server.url("/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val request=SkillRequest(row(true,false),SkillAction.Lock)
            try {
                assertTrue(runCatching {SkillOperationsRepository(db.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.isFailure)
                val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state)
                db.dao().recoverManagementOperations();db.dao().clearChats()
                assertTrue(runCatching {SkillOperationsRepository(db.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get())
            } finally {db.close()}
        }
    }
    @Test fun changedReviewRefusesBeforeWriteAndReadbackFailureRetainsAcknowledgement()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger();var current=inventory(false,false)
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/api/auth/me"->json("""{"provider":"password","user_id":"fixture"}""")
                request.path=="/api/skills?profile=worker"->json(current)
                request.method=="PUT"->{writes.incrementAndGet();json("""{"ok":true,"name":"fixture","locked":true}""")}
                else->json("{}").setResponseCode(404)
            }}
            val api=ManagementClient(server.url("/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val request=SkillRequest(row(true,false),SkillAction.Lock)
            try {
                val repo=SkillOperationsRepository(db.dao(),CompletableDeferred(Unit));assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is SkillReviewChanged);assertEquals(0,writes.get())
                current=inventory(true,false);assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is SkillReadbackPending);assertEquals(1,writes.get())
                val records=db.dao().managementResourceOperations(target.resourceScope).first();assertEquals("acknowledged",records.single().state)
                assertEquals("""{"locked":true}""",records.single().resultSummary)
            } finally {db.close()}
        }
    }
}
