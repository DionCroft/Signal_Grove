package org.hermesmobile.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.ui.HermesViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Real client/VM/Room over fixture HTTP. No Hermes credentials, agent or microphone. */
class RunRecoveryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    @After fun resetConnection() {compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(5000) {vm.preferences.value.demo}}
    private class Fixture(private val gap:Boolean):Dispatcher() {
        val admissions=AtomicInteger();val streams=AtomicInteger();val cursors=mutableListOf<String?>()
        @Volatile var terminal=false
        private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
        private fun sse(text:String)=MockResponse().setHeader("Content-Type","text/event-stream").setBody(text)
        override fun dispatch(request:RecordedRequest):MockResponse {
            val path=request.path.orEmpty().removePrefix("/p/qa/").substringBefore('?')
            return when {
                path=="v1/capabilities" -> json("""{"features":{"session_resources":true,"session_chat":true,"run_events_sse":true,"run_status":true},"endpoints":{"runs":{"method":"POST","path":"/v1/runs"}}}""")
                path=="api/sessions" && request.method=="GET" -> json("""{"data":[],"has_more":false}""")
                path=="api/sessions" && request.method=="POST" -> {
                    val id=Json.parseToJsonElement(request.body.readUtf8()).jsonObject["id"]!!.jsonPrimitive.content
                    json(buildJsonObject {put("session",buildJsonObject {put("id",id);put("title","Recovery fixture")})}.toString())
                }
                path=="api/jobs" -> json("""{"jobs":[]}""")
                path=="v1/runs" && request.method=="POST" -> {admissions.incrementAndGet();json("""{"run_id":"run_fixture","status":"started"}""").setResponseCode(202)}
                path=="v1/runs/run_fixture" -> json(if(terminal) """{"run_id":"run_fixture","status":"completed","output":"Hello world","pending_controls":[]}""" else """{"run_id":"run_fixture","status":"running","pending_controls":[]}""")
                path=="v1/runs/run_fixture/events" -> {
                    val number=streams.incrementAndGet();synchronized(cursors) {cursors+=request.getHeader("Last-Event-ID")}
                    when {
                        number==1 -> sse("""id: 0
data: {"event":"run.started","run_id":"run_fixture","seq":0}

id: 1
data: {"event":"message.delta","run_id":"run_fixture","delta":"Hello","seq":1}


""")
                        gap -> sse("""data: {"event":"replay.truncated","run_id":"run_fixture","oldest_retained_seq":20}


""")
                        else -> {terminal=true;sse("""id: 2
data: {"event":"message.delta","run_id":"run_fixture","delta":" world","seq":2}

id: 3
data: {"event":"run.completed","run_id":"run_fixture","output":"Hello world","seq":3}


""")}
                    }
                }
                else -> json("{}").setResponseCode(404)
            }
        }
    }
    private fun connectAndSend(server:MockWebServer) {
        compose.waitUntil(10000) {!vm.connecting.value}
        val url=server.url("p/qa/").toString()
        compose.runOnUiThread {vm.configure(url,"instrumentation-fixture-token",true)}
        compose.waitUntil(10000) {!vm.connecting.value && !vm.preferences.value.demo && vm.connectionStatus.value=="connected"}
        compose.runOnUiThread {vm.newChat();vm.send("Harmless recovery fixture")}
        compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="running" && vm.selected.value.isNotBlank()}
        assertEquals("Hello",runBlocking {app.database.dao().history(vm.selected.value).last().content})
    }
    @Test fun reconnectUsesSavedCursorWithoutAnotherAdmissionOrDuplicatedText() {
        MockWebServer().use {server ->
            val fixture=Fixture(false);server.dispatcher=fixture;connectAndSend(server)
            compose.runOnUiThread {vm.retryOrRefresh()}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
            assertEquals(1,fixture.admissions.get());assertEquals(2,fixture.streams.get());assertEquals("1",fixture.cursors.last())
            assertEquals("Hello world",runBlocking {app.database.dao().history(vm.selected.value).last().content})
        }
    }
    @Test fun replayGapFallsBackToStatusWithoutResendingOrContinuingAnIncompleteStream() {
        MockWebServer().use {server ->
            val fixture=Fixture(true);server.dispatcher=fixture;connectAndSend(server)
            compose.runOnUiThread {vm.retryOrRefresh()}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="unknown"}
            assertEquals(-2L,runBlocking {app.database.dao().conversation(vm.selected.value)!!.lastEventSeq})
            compose.runOnUiThread {vm.retryOrRefresh()}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="running"}
            assertEquals(1,fixture.admissions.get());assertEquals(2,fixture.streams.get())
            fixture.terminal=true;compose.runOnUiThread {vm.retryOrRefresh()}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
            assertEquals("Hello world",runBlocking {app.database.dao().history(vm.selected.value).last().content})
        }
    }
}
