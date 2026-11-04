package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class GatewayCronControlsFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
    @Test fun pauseResumeAndRemovalEachNeedTheirOwnReviewAndReceiptObservationNeverMutates() {
        val writes=AtomicInteger();val tickets=AtomicInteger();val reads=AtomicInteger();val mcpReads=AtomicInteger()
        val rows=java.util.concurrent.atomic.AtomicReference(listOf(GatewayCronFixture.job(),GatewayCronFixture.job(id="other-job",name="GATEWAY-CONTROL-FIXTURE")))
        credentials.managementServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Gateway controls fixture"}""")
                request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                request.path=="/dashboard/api/mcp/servers?profile=worker"->{mcpReads.incrementAndGet();json("""{"servers":[]}""")}
                request.path=="/dashboard/api/auth/ws-ticket"->{tickets.incrementAndGet();json("""{"ticket":"gateway-control-ticket-fixture","ttl_seconds":30}""")}
                request.path=="/dashboard/api/ws"->MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send(GatewayCronFixture.ready())}
                    override fun onMessage(webSocket:WebSocket,text:String) {
                        val q=Json.parseToJsonElement(text).jsonObject;val params=q["params"]!!.jsonObject;assertEquals("worker",params.text("profile"));assertEquals("cron.manage",q.text("method"))
                        if(params.text("action")=="list") {reads.incrementAndGet();webSocket.send(GatewayCronFixture.result(q,GatewayCronFixture.inventory(rows.get())));return}
                        assertEquals("gateway-control-fixture",params.text("name"));writes.incrementAndGet()
                        val before=rows.get().single {it.text("job_id")==params.text("name")};val action=params.text("action");val enabled=action=="resume"
                        rows.set(if(action=="remove") emptyList() else listOf(JsonObject(before+mapOf("enabled" to JsonPrimitive(enabled),"state" to JsonPrimitive(if(enabled) "scheduled" else "paused")))))
                        val result=if(action=="remove") buildJsonObject {put("success",true);put("removed_job",buildJsonObject {put("id",before.text("job_id"));put("name",before.text("name"))})} else buildJsonObject {put("success",true);put("job",rows.get().single())}
                        webSocket.send(GatewayCronFixture.result(q,result))
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                else->json("{}").setResponseCode(404)
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,"inference-gateway-control-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("management-gateway-control-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("gateway-cron-open"));compose.onNodeWithTag("gateway-cron-open").performClick()
            fun readGateway() {compose.onNodeWithTag("gateway-cron-list").performScrollToNode(hasTestTag("gateway-cron-load"));compose.onNodeWithTag("gateway-cron-load").performClick();compose.onNodeWithTag("gateway-cron-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.gateway.inventory!=null && management.state.value.error==null}}
            readGateway();compose.onNodeWithTag("gateway-cron-list").performScrollToNode(hasTestTag("gateway-pause-gateway-control-fixture"));compose.onNodeWithTag("gateway-pause-gateway-control-fixture").assertIsNotEnabled();assertEquals(0,writes.get())
            rows.set(listOf(GatewayCronFixture.job()));readGateway()
            for((index,action) in listOf("pause","resume","delete").withIndex()) {
                compose.onNodeWithTag("gateway-cron-list").performScrollToNode(hasTestTag("gateway-$action-gateway-control-fixture"));compose.onNodeWithTag("gateway-$action-gateway-control-fixture").assertIsEnabled().performClick();assertEquals(index,writes.get())
                val effect=when(action) {"pause"->Words.GatewayPauseEffect;"resume"->Words.GatewayResumeEffect;else->Words.GatewayDeleteEffect}
                if(action=="resume") {
                    compose.onNodeWithTag("gateway-action-review").performScrollToNode(hasText(Words.McpShowQuery.en));compose.onNodeWithText("private-provider-key",substring=true).assertDoesNotExist();compose.onNodeWithText(Words.McpShowQuery.en).performClick();compose.onNodeWithText("private-provider-key",substring=true).assertExists();compose.onNodeWithTag("gateway-action-review").performScrollToNode(hasText(Words.McpHideQuery.en));compose.onNodeWithText(Words.McpHideQuery.en).performClick()
                }
                compose.onNodeWithTag("gateway-action-review").performScrollToNode(hasText(effect.en));compose.onNodeWithText(effect.en).assertExists();assertEquals(index,writes.get())
                if(action=="delete") {val file=File(compose.activity.getExternalFilesDir(null),"qa/gateway-remove-review.png");file.parentFile!!.mkdirs();file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
                compose.onNodeWithTag("gateway-action-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.notice==Words.GatewayCronChanged};assertEquals(index+1,writes.get())
            }
            assertTrue(management.state.value.gateway.inventory!!.jobs.isEmpty());compose.onNodeWithTag("gateway-cron-close").performClick()
            compose.waitUntil(5000) {management.receipts.value.any {it.action=="GatewayCronDelete" && it.state=="confirmed"}}
            val receipt=management.receipts.value.single {it.action=="GatewayCronDelete"};val priorTickets=tickets.get();val priorReads=reads.get();val priorMcp=mcpReads.get()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(priorTickets,tickets.get())
            readGateway();assertEquals(priorTickets+1,tickets.get());assertEquals(priorReads+1,reads.get());assertEquals(priorMcp,mcpReads.get());assertEquals(3,writes.get())
            compose.onNodeWithTag("gateway-cron-close").performClick();compose.runOnUiThread {management.bind("unconfigured")}
        }
    }
}
