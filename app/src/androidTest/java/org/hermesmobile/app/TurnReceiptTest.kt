package org.hermesmobile.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.HermesViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TurnReceiptTest {
    @get:Rule(order=0) val credentialFixture=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    @After fun reset() {compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {vm.preferences.value.demo && !vm.connecting.value}}
    @Test fun lostAdmissionAcknowledgementReusesTheFrozenBodyKeyAndStableChannel() {
        val posts=mutableListOf<Triple<String,String?,String?>>()
        val count=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/p/work/v1/capabilities"->json("""{"model":"initial-default","features":{"session_resources":true,"session_chat":true,"run_events_sse":true,"model_options":true,"session_key_header":"X-Hermes-Session-Key"},"endpoints":{"runs":{"method":"POST","path":"/v1/runs"}}}""")
                    request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                    request.path.orEmpty().startsWith("/p/work/api/sessions?")->json("""{"data":[],"has_more":false}""")
                    request.path=="/p/work/api/sessions"->{val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;json(buildJsonObject {put("session",buildJsonObject {put("id",body.text("id"));put("title","Receipt fixture")})}.toString())}
                    request.path=="/p/work/v1/runs"->{
                        synchronized(posts) {posts+=Triple(request.body.readUtf8(),request.getHeader("Idempotency-Key"),request.getHeader("X-Hermes-Session-Key"))}
                        if(count.incrementAndGet()==1) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                        else json("""{"run_id":"receipt-run","status":"queued"}""").setResponseCode(202)
                    }
                    request.path=="/p/work/v1/runs/receipt-run/events"->MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"event\":\"run.completed\",\"run_id\":\"receipt-run\",\"seq\":0,\"output\":\"Verified fixture\"}\n\n")
                    else->json("{}").setResponseCode(404)
                }
            }
            compose.waitUntil(10000) {!vm.connecting.value}
            val url=server.url("p/work/").toString()
            compose.runOnUiThread {vm.configure(url,"receipt-fixture",true)}
            compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected"}
            val original=RuntimeChoice("original-model","original-provider",false,serviceTier="priority")
            compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,original)}
            compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value==original}
            compose.runOnUiThread {vm.newChat();vm.send("Original work")}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="unknown"}
            val before=runBlocking {app.database.dao().conversation(vm.selected.value)!!}
            assertEquals("",before.runId);assertEquals(64,before.pendingRequestHash.length);assertTrue(before.channelKey.startsWith("mobile_"))
            val frozen=app.requests.read(before.scope,before.key,before.pendingKey,before.pendingRequestHash)
            compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice("different-model","different-provider"))}
            compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.model=="different-model"}
            compose.runOnUiThread {vm.changeDraft("Different work");vm.retryOrRefresh()}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
            assertEquals(2,count.get());assertEquals(posts.first(),posts.last())
            assertEquals(frozen.body,Json.parseToJsonElement(posts.last().first).jsonObject)
            assertEquals("original-model",frozen.body.text("model"));assertEquals("original-provider",frozen.body.text("provider"))
            assertEquals(original.fields()["model_options"],frozen.body["model_options"])
            val after=runBlocking {app.database.dao().conversation(before.key)!!}
            assertEquals(before.channelKey,after.channelKey);assertEquals(before.pendingRequestHash,after.pendingRequestHash)
            assertEquals("Original work",after.pendingInput)
        }
    }
    @Test fun schemaFourPendingReceiptsMigrateWithoutInventingAChannelOrNewRequest()=runBlocking {
        val name="receipt-upgrade4.db";app.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/4.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        app.getDatabasePath(name).parentFile!!.mkdirs();val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry -> val entity=entry.jsonObject;val table=entity["tableName"]!!.jsonPrimitive.content
                old.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))
                entity["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}
            }
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            old.execSQL("INSERT INTO sessions (`key`,scope,remoteId,title,updatedAt,pendingInput,pendingKey,pendingAt,transport,runId,status) VALUES('c','scope','api-old','Saved',1,'Old work','original-key',1,'runs','','unknown')")
            old.version=4
        }
        val db=Room.databaseBuilder(app,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {
            val old=db.dao().conversation("c")!!
            assertEquals("original-key",old.pendingKey);assertEquals("Old work",old.pendingInput)
            assertEquals("",old.channelKey);assertEquals("",old.pendingRequestHash)
            val legacy=TurnRequest.create(old,emptyList(),"ignored")
            assertEquals(setOf("session_id","input"),legacy.body.keys);assertEquals("",legacy.channelKey)
        } finally {db.close();app.deleteDatabase(name)}
    }
}
