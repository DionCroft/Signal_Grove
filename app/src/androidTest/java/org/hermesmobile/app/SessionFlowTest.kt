package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class SessionFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun text(word:Words)=word.localized(vm.preferences.value)
    private fun connect(server:MockWebServer) {
        compose.waitUntil(10000) {!vm.connecting.value};val url=server.url("/").toString()
        compose.runOnUiThread {vm.configure(url,"session-fixture",true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected"}
    }
    @After fun reset() {compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {vm.preferences.value.demo && !vm.connecting.value}}
    @Test fun reviewedDemoLockAndBranchConfirmExactTargetsAndKeepHistory() {
        compose.waitUntil(10000) {!vm.connecting.value};compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
        val choice=RuntimeChoice("demo-model","demo")
        compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,choice)}
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value==choice}
        compose.runOnUiThread {vm.newChat();vm.send("Session control fixture")}
        compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
        val original=vm.selectedConversation.value!!
        compose.onAllNodesWithContentDescription(text(Words.Chat),useUnmergedTree=true).onLast().performClick()
        compose.onNodeWithContentDescription(text(Words.SessionControls)).performClick()
        compose.onNodeWithTag("session-control-list").performScrollToNode(hasTestTag("session-lock"))
        compose.onNodeWithTag("session-lock").performClick();compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(10000) {!vm.controlBusy.value && vm.serverOperations.value.any {it.sessionKey==original.key && it.action=="Lock" && it.state=="confirmed"}}
        compose.onNodeWithContentDescription(text(Words.SessionControls)).performClick()
        compose.onNodeWithTag("session-control-list").performScrollToNode(hasTestTag("fork-title"))
        compose.onNodeWithTag("fork-title").performTextReplacement("Reviewed branch")
        compose.onNodeWithTag("session-control-list").performScrollToNode(hasTestTag("session-fork"))
        compose.onNodeWithTag("session-fork").performClick();compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(10000) {!vm.controlBusy.value && vm.selected.value!=original.key && vm.selectedConversation.value?.title=="Reviewed branch"}
        val child=vm.selectedConversation.value!!
        assertNotEquals(original.remoteId,child.remoteId)
        val oldHistory=runBlocking {app.database.dao().history(original.key)}
        val copied=runBlocking {app.database.dao().history(child.key)}
        assertEquals(oldHistory.map {it.content},copied.map {it.content})
        assertEquals(original.remoteId,Json.parseToJsonElement(child.detailsJson).jsonObject.text("parent_session_id"))
        assertTrue(vm.serverOperations.value.any {it.action=="Fork" && it.state=="confirmed"})
        val file=File(compose.activity.getExternalFilesDir(null),"qa/session-branch.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice())}
        compose.waitUntil(5000) {!vm.controlBusy.value}
    }
    @Test fun acceptedGuidanceDoesNotCompleteOrReadmitTheRun() {
        val admissions=AtomicInteger();val steers=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/v1/capabilities"->json("""{"features":{"session_resources":true,"session_chat":true,"run_events_sse":true,"run_steer":true},"endpoints":{"runs":{},"run_steer":{}}}""")
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path.orEmpty().startsWith("/api/sessions?")->json("""{"data":[],"has_more":false}""")
                request.path=="/api/sessions"->{val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;json(buildJsonObject {put("session",buildJsonObject {put("id",body.text("id"));put("title","Guidance fixture")})}.toString())}
                request.path=="/v1/runs"->{admissions.incrementAndGet();json("""{"run_id":"steer-fixture"}""")}
                request.path=="/v1/runs/steer-fixture/events"->MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"event\":\"run.started\",\"run_id\":\"steer-fixture\",\"seq\":0}\n\ndata: {\"event\":\"message.delta\",\"delta\":\"Working\",\"seq\":1}\n\n")
                request.path=="/v1/runs/steer-fixture"->json("""{"run_id":"steer-fixture","status":"running","pending_controls":[]}""")
                request.path=="/v1/runs/steer-fixture/steer"->{steers.incrementAndGet();assertEquals("Review the tests first",Json.parseToJsonElement(request.body.readUtf8()).jsonObject.text("input"));json("""{"object":"hermes.run.steer","run_id":"steer-fixture","accepted":true}""")}
                else->json("{}").setResponseCode(404)
            }}
            connect(server);compose.runOnUiThread {vm.newChat();vm.send("Start harmless fixture")}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="running"}
            val current=vm.selectedConversation.value!!
            compose.runOnUiThread {vm.sessionAction(current,SessionAction.Steer,buildJsonObject {put("input","Review the tests first")})}
            compose.waitUntil(10000) {!vm.controlBusy.value && vm.serverOperations.value.any {it.action=="Steer" && it.state=="confirmed"}}
            assertEquals(1,admissions.get());assertEquals(1,steers.get());assertEquals("running",vm.runStatus.value)
            assertEquals("running",runBlocking {app.database.dao().conversation(current.key)!!.status})
        }
    }
    @Test fun staleAppearanceSnapshotCannotMakeALiveConnectionLookLikeDemo() {
        compose.waitUntil(10000) {!vm.connecting.value}
        val before=vm.preferences.value
        compose.runOnUiThread {vm.updatePreferences(before.copy(allowNetworkSpeech=true),before)}
        compose.waitUntil(5000) {vm.preferences.value.allowNetworkSpeech}
        val old=vm.preferences.value
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/v1/capabilities"->json("""{"model":"fixture","features":{},"endpoints":{}}""")
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path=="/v1/chat/completions"->MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Fixture reply\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                else->json("{}").setResponseCode(404)
            }}
            connect(server);val connected=vm.preferences.value
            compose.runOnUiThread {vm.updatePreferences(connected.copy(allowNetworkSpeech=false),connected)}
            compose.waitUntil(5000) {!vm.preferences.value.allowNetworkSpeech}
            val nextTheme=if(old.theme=="JADE") "MINT" else "JADE"
            compose.runOnUiThread {vm.updatePreferences(old.copy(theme=nextTheme),old)}
            compose.waitUntil(5000) {vm.preferences.value.theme==nextTheme}
            assertFalse(vm.preferences.value.demo);assertEquals(connected.url,vm.preferences.value.url);assertFalse(vm.preferences.value.allowNetworkSpeech)
            compose.runOnUiThread {vm.newChat();vm.send("Harmless live-mode fixture")}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="response_finished"}
            assertEquals("compat",vm.selectedConversation.value!!.transport)
        }
    }
    @Test fun compatibilityHistorySurvivesCapabilityUpgradeAndNeverTargetsAnUnverifiedSession() {
        val native=java.util.concurrent.atomic.AtomicBoolean(false);val creates=AtomicInteger();val admissions=AtomicInteger();val historyGets=AtomicInteger();val bodies=mutableListOf<JsonObject>()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/v1/capabilities"->json(if(native.get()) """{"features":{"session_resources":true,"session_chat":true,"run_events_sse":true,"session_fork":true},"endpoints":{"runs":{},"session":{},"session_fork":{}}}""" else """{"features":{},"endpoints":{}}""")
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path.orEmpty().startsWith("/api/sessions?")->json("""{"data":[],"has_more":false}""")
                request.path=="/api/sessions"->{creates.incrementAndGet();json("{}")}
                request.path.orEmpty().contains("/messages?")->{historyGets.incrementAndGet();json("""{"data":[]}""")}
                request.path=="/v1/runs"->{admissions.incrementAndGet();json("""{"run_id":"unexpected"}""")}
                request.path=="/v1/runs/unexpected/events"->MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"event\":\"run.completed\",\"run_id\":\"unexpected\"}\n\n")
                request.path=="/v1/chat/completions"->{synchronized(bodies) {bodies+=Json.parseToJsonElement(request.body.readUtf8()).jsonObject};MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Saved reply\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")}
                else->json("{}").setResponseCode(404)
            }}
            connect(server);compose.runOnUiThread {vm.newChat();vm.send("First question")}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="response_finished"}
            native.set(true);compose.runOnUiThread {vm.refresh()};compose.waitUntil(10000) {!vm.connecting.value && vm.capabilities.value?.runs==true}
            compose.runOnUiThread {vm.refreshHistory()};compose.waitUntil(5000) {!vm.historyLoading.value}
            compose.runOnUiThread {vm.send("Follow-up")}
            compose.waitUntil(10000) {!vm.busy.value && synchronized(bodies) {bodies.size==2}}
            assertEquals(0,creates.get());assertEquals(0,admissions.get());assertEquals(0,historyGets.get())
            assertEquals(listOf("user","assistant","user"),bodies.last()["messages"]!!.jsonArray.map {it.jsonObject.text("role")})
        }
    }
}
