package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ActivityFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val context get()=compose.activity.applicationContext
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun word(w:Words)=w.localized(vm.preferences.value)
    @After fun reset() {compose.waitUntil(10000) {!vm.busy.value && !vm.controlBusy.value && !vm.connecting.value};compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo}}
    private fun shot(name:String) {
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun parallelToolAndChildActivityAndServedRuntimeAreReadableAfterStreaming() {
        val frames=listOf(
            """{"event":"run.started","run_id":"activity-run","seq":0}""",
            """{"event":"tool.started","tool":"shell","tool_call_id":"call-a","preview":"First local fixture","seq":1}""",
            """{"event":"tool.started","tool":"shell","tool_call_id":"call-b","preview":"Second local fixture","seq":2}""",
            """{"event":"tool.completed","tool":"shell","tool_call_id":"call-a","preview":"First result","duration":.1,"seq":3}""",
            """{"event":"tool.completed","tool":"shell","tool_call_id":"call-b","error":true,"duration":.2,"seq":4}""",
            """{"event":"subagent.start","subagent_id":"agent-1","child_session_id":"child-1","goal":"Review fixture","seq":5}""",
            """{"event":"subagent.complete","subagent_id":"agent-1","child_session_id":"child-1","summary":"Reviewed synthetic fixture","status":"completed","input_tokens":20,"cost_usd":.001,"seq":6}""",
            """{"event":"run.completed","run_id":"activity-run","output":"Activity fixture complete","usage":{"input_tokens":200,"output_tokens":15,"cache_read_tokens":150},"runtime":{"model":"fallback-model","provider":"fallback-provider","route_source":"global","requested":{"model":"requested-model","provider":"requested-provider"}},"seq":7}"""
        ).joinToString("") {"data: $it\n\n"}.replace(":.",":0.")
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/v1/capabilities"->json("""{"features":{"session_resources":true,"session_chat":true,"run_events_sse":true},"endpoints":{"runs":{}}}""")
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path.orEmpty().startsWith("/api/sessions?")->json("""{"data":[],"has_more":false}""")
                request.path=="/api/sessions"->{val input=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;json(buildJsonObject {put("session",buildJsonObject {put("id",input.text("id"));put("title","Activity fixture")})}.toString())}
                request.path=="/v1/runs"->json("""{"run_id":"activity-run"}""")
                request.path=="/v1/runs/activity-run/events"->MockResponse().setHeader("Content-Type","text/event-stream").setBody(frames)
                else->json("{}").setResponseCode(404)
            }}
            compose.waitUntil(10000) {!vm.connecting.value};val url=server.url("/").toString()
            compose.runOnUiThread {vm.configure(url,"activity-fixture",true)}
            compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected"}
            compose.runOnUiThread {vm.newChat();vm.send("Harmless activity fixture")}
            compose.waitUntil(15000) {!vm.busy.value && vm.runStatus.value=="completed" && vm.toolEvents.value.size==6 && vm.messages.value.any {it.telemetryJson.contains("fallback-model")}}
            val entries=activityTimeline(vm.toolEvents.value);assertEquals(3,entries.size)
            assertEquals("failed",entries.first {it.first.callId=="call-b"}.latest.kind)
            assertEquals("child-1",entries.first {it.first.category=="subagent"}.details.text("child_session_id"))
            val answer=vm.messages.value.first {it.role=="assistant"};assertFalse(answer.telemetryJson.contains("total_tokens"))
            compose.onAllNodesWithContentDescription(word(Words.Chat),useUnmergedTree=true).onLast().performClick()
            compose.onNodeWithContentDescription(word(Words.ToolActivity),substring=true).performClick()
            compose.onNodeWithTag("activity-timeline").performScrollToNode(hasText("child-1"));compose.onNodeWithText("child-1").assertExists();shot("rich-child-activity")
            compose.onNodeWithTag("activity-timeline").performScrollToNode(hasText("call-b"));compose.onNodeWithText("call-b").assertExists()
            compose.onNodeWithText(word(Words.Close)).performClick()
            compose.onNodeWithTag("chat-messages").performScrollToNode(hasTestTag("runtime-usage-${answer.id}"))
            compose.onNodeWithTag("runtime-usage-${answer.id}").performClick()
            compose.onNodeWithTag("runtime-usage-list").performScrollToNode(hasText("fallback-model"));compose.onNodeWithText("fallback-model").assertExists();shot("served-runtime")
            compose.onNodeWithTag("runtime-usage-list").performScrollToNode(hasText("requested-model"));compose.onNodeWithText("requested-model").assertExists()
            compose.onNodeWithText(word(Words.Close)).performClick()
            compose.onNodeWithContentDescription(word(Words.Completed)).performClick()
            compose.onNodeWithTag("accounting-history").performScrollTo().performClick()
            compose.onNodeWithTag("accounting-history-list").performScrollToNode(hasText("activity-run"))
            compose.onNodeWithText("activity-run").performClick()
            compose.onNodeWithTag("runtime-usage-list").performScrollToNode(hasText("fallback-model"));compose.onNodeWithText("fallback-model").assertExists()
            compose.onNodeWithText(word(Words.Close)).performClick();compose.onNodeWithText(word(Words.Close)).performClick()
        }
    }
    @Test fun anotherRunIdentityCannotRewriteTheReceiptOrItsActivity() {
        var admissions=0
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/v1/capabilities"->json("""{"features":{"session_resources":true,"session_chat":true,"run_events_sse":true},"endpoints":{"runs":{}}}""")
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path.orEmpty().startsWith("/api/sessions?")->json("""{"data":[],"has_more":false}""")
                request.path=="/api/sessions"->{val input=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;json(buildJsonObject {put("session",buildJsonObject {put("id",input.text("id"));put("title","Identity fixture")})}.toString())}
                request.path=="/v1/runs"->{admissions++;json("""{"run_id":"owned-run"}""")}
                request.path=="/v1/runs/owned-run/events"->MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"event\":\"tool.started\",\"run_id\":\"another-run\",\"tool\":\"wrong\",\"tool_call_id\":\"wrong-call\",\"seq\":0}\n\n")
                else->json("{}").setResponseCode(404)
            }}
            compose.waitUntil(10000) {!vm.connecting.value};val url=server.url("/").toString()
            compose.runOnUiThread {vm.configure(url,"identity-fixture",true)}
            compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected"}
            compose.runOnUiThread {vm.newChat();vm.send("Harmless identity fixture")}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="unknown"}
            assertEquals("owned-run",vm.selectedConversation.value!!.runId);assertTrue(vm.toolEvents.value.isEmpty());assertEquals(1,admissions)
        }
    }
    @Test fun accountingSnapshotsRespectSubmissionIdentityAndSurviveLaterTextWrites()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();val c=Conversation("c","scope","api",pendingKey="old",pendingAt=1,runId="r",status="running")
            dao.save(c);dao.save(ChatMessage("old_assistant","c","assistant","Working"))
            dao.applyRunSnapshot(c,Json.parseToJsonElement("""{"status":"running","runtime":{"model":"served"},"usage":{"input_tokens":12}}""").jsonObject)
            dao.saveTurn(c,ChatMessage("old_assistant","c","assistant","More text"),null,null,"")
            assertTrue(dao.message("old_assistant")!!.telemetryJson.contains("served"))
            val settled=c.copy(status="completed");dao.save(settled)
            dao.replaceHistory(settled,c.remoteId,listOf(ChatMessage("remote","c","assistant","Refreshed")))
            assertEquals("",dao.message("remote")!!.telemetryJson)
            assertTrue(dao.reports(c.key).first().single().telemetryJson.contains("served"))
            val newer=c.copy(pendingKey="new",pendingAt=2,runId="r2");dao.save(newer);dao.save(ChatMessage("new_assistant","c","assistant","New"))
            assertNull(dao.applyRunSnapshot(c,Json.parseToJsonElement("""{"status":"completed","usage":{"input_tokens":999}}""").jsonObject))
            assertEquals("",dao.message("new_assistant")!!.telemetryJson)
            dao.deleteConversation(c.key);assertTrue(dao.reports(c.key).first().isEmpty())
        } finally {db.close()}
    }
    @Test fun schemaSixMigratesLegacyActivityWithoutInventingCallIdsOrAccounting()=runBlocking {
        val name="activity-upgrade6.db";context.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/6.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        context.getDatabasePath(name).parentFile!!.mkdirs();val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry -> val e=entry.jsonObject;val table=e["tableName"]!!.jsonPrimitive.content
                old.execSQL(e["createSql"]!!.jsonPrimitive.content.replace(placeholder,table));e["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}}
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            old.execSQL("INSERT INTO messages VALUES('m','c','assistant','Saved',1,'completed')")
            old.execSQL("INSERT INTO tool_events VALUES('e','c','r','shell','completed','Saved result',12,1)");old.version=6
        }
        val db=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {assertEquals("Saved",db.dao().message("m")!!.content);assertEquals("",db.dao().message("m")!!.telemetryJson)
            val event=db.openHelper.writableDatabase.query("SELECT callId,category,generation FROM tool_events WHERE `key`='e'")
            event.use {assertTrue(it.moveToFirst());assertEquals("",it.getString(0));assertEquals("tool",it.getString(1));assertTrue(it.isNull(2))}
        } finally {db.close();context.deleteDatabase(name)}
    }
}
