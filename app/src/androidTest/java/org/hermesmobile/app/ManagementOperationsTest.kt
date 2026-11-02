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

class ManagementOperationsTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HermesApplication
    private val tokens=ManagementTokens("management-access-fixture","refresh-fixture","password","fixture",4102444800)
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun inventory(enabled:Boolean)=json("""{"servers":[{"name":"fixture","transport":"http","source":"config","enabled":$enabled}]}""")
    private fun target(api:ManagementClient)=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
    private fun db()=Room.inMemoryDatabaseBuilder(app,HermesDatabase::class.java).build()

    @Test fun lostAcknowledgementIsDurableAndNeverRepeatsEvenWhenReadbackMatches()=runBlocking {
        val db=db();val writes=AtomicInteger();var enabled=true
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture","provider":"password"}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker"->inventory(enabled)
                    request.path=="/dashboard/api/mcp/servers/fixture/enabled?profile=worker"->{
                        assertEquals("PUT",request.method);assertEquals("Bearer management-access-fixture",request.getHeader("Authorization"))
                        assertEquals("worker",Json.parseToJsonElement(request.body.readUtf8()).jsonObject.text("profile"));enabled=false;writes.incrementAndGet()
                        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    }
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=target(api)
                val repo=ManagementOperationsRepository(db.dao(),CompletableDeferred(Unit));val request=McpRequest(McpAction.Toggle,"fixture",true,"config",false)
                assertTrue(runCatching {repo.execute(target,request,api,tokens)}.isFailure);assertEquals(1,writes.get())
                val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state)
                assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get())
                // Current state can be read, but does not settle an uncertain request or establish causality.
                assertTrue(McpProtocol.observed(request,McpProtocol.inventory(api.mcp(tokens,"worker"))))
                assertEquals("unknown",db.dao().managementOperation(receipt.key)!!.state)
                db.dao().clearChats();assertNotNull(db.dao().managementOperation(receipt.key))
                repo.review(target.copy(profile="other"),receipt.key);assertEquals("unknown",db.dao().managementOperation(receipt.key)!!.state)
                repo.review(target,receipt.key);assertEquals("reviewed",db.dao().managementOperation(receipt.key)!!.state)
                val result=repo.execute(target,request,api,tokens);assertTrue(result.observedOnly);assertNull(result.receipt);assertEquals(1,writes.get())
            } finally {db.close()}
        }
    }
    @Test fun successfulAcknowledgementCannotBeDowngradedWhenReadbackFails()=runBlocking {
        val db=db();val reads=AtomicInteger();val writes=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/api/auth/me"->json("""{"user_id":"fixture","provider":"password"}""")
                    request.path=="/api/mcp/servers?profile=worker"->if(reads.incrementAndGet()==1) inventory(true) else json("{}").setResponseCode(503)
                    request.path=="/api/mcp/servers/fixture/enabled?profile=worker"->{writes.incrementAndGet();json("""{"ok":true,"name":"fixture","enabled":false,"secret":"never-store"}""")}
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("/").toString(),true,true);val target=target(api)
                val repo=ManagementOperationsRepository(db.dao(),CompletableDeferred(Unit))
                assertTrue(runCatching {repo.execute(target,McpRequest(McpAction.Toggle,"fixture",true,"config",false),api,tokens)}.isFailure)
                val receipt=db.dao().managementOperations(target.apiScope).first().single()
                assertEquals("acknowledged",receipt.state);assertFalse(receipt.resultSummary.contains("never-store"));assertTrue(db.dao().unresolvedManagementOperations(target.resourceScope).isEmpty());assertEquals(1,writes.get())
            } finally {db.close()}
        }
    }
    @Test fun claimsSerializePerResourceAndInterruptedReceiptsSurviveChatCleanup()=runBlocking {
        val db=db()
        try {
            val first=ManagementOperation("one","resource","api","https://fixture.example/","worker","fixture","Probe","server","{}","sending","",1)
            val results=coroutineScope {(0..1).map {i->async(Dispatchers.IO) {runCatching {db.dao().claimManagementOperation(first.copy(key=i.toString()))}}}.awaitAll()}
            assertEquals(1,results.count {it.isSuccess})
            db.dao().claimManagementOperation(first.copy(key="other",resourceScope="other"))
            db.dao().recoverManagementOperations();assertEquals("unknown",db.dao().unresolvedManagementOperations("resource").single().state)
            db.dao().clearChats();assertEquals(2,db.dao().managementOperations("api").first().size)
        } finally {db.close()}
    }
    @Test fun schemaEightPreservesImagesAccountingAndNativeUncertainty()=runBlocking {
        val name="management-upgrade8-qa.db";app.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/8.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        app.getDatabasePath(name).parentFile!!.mkdirs();val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry->val e=entry.jsonObject;val table=e["tableName"]!!.jsonPrimitive.content
                old.execSQL(e["createSql"]!!.jsonPrimitive.content.replace(placeholder,table));e["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}}
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            old.execSQL("INSERT INTO messages VALUES('m','c','assistant','Saved',1,'completed','{\"usage\":{\"input_tokens\":12}}','[\"saved-image-reference\"]')")
            old.execSQL("INSERT INTO run_reports VALUES('r','c','run','pending','runs','{\"usage\":{\"input_tokens\":12}}','completed',1)")
            old.execSQL("INSERT INTO server_operations VALUES('native','api','c','Fork','child','{}','unknown','',1)");old.version=8
        }
        val db=Room.databaseBuilder(app,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {
            assertTrue(db.dao().message("m")!!.imagesJson.contains("saved-image-reference"));assertTrue(db.dao().report("r")!!.telemetryJson.contains("12"))
            assertEquals("native",db.dao().unresolvedServerOperations("api").single().key);assertTrue(db.dao().managementOperations("api").first().isEmpty())
        } finally {db.close();app.deleteDatabase(name)}
    }
}
