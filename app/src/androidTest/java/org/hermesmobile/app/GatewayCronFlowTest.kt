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

class GatewayCronFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
    private fun openGatewaySection() {
        try {
            compose.waitUntil(5000) {!management.state.value.busy && management.state.value.identity!=null && management.state.value.profile.isNotBlank()}
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("gateway-cron-open"))
            compose.waitForIdle()
            compose.waitUntil(5000) {compose.onAllNodesWithTag("gateway-cron-open").fetchSemanticsNodes().size==1}
            compose.onNodeWithTag("gateway-cron-open").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        } catch(e:Throwable) {
            runCatching {val shot=File(compose.activity.getExternalFilesDir(null),"qa/gateway-entry-failure.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
            val state=management.state.value
            throw AssertionError("Gateway entry: busy=${state.busy}, signedIn=${state.signedIn}, identity=${state.identity!=null}, profile=${state.profile}, loaded=${state.loaded}",e)
        }
    }
    @Test fun gatewayReadRequiresConnectionEffectReviewAndRejectsUnprovenProfile() {
        val tickets=AtomicInteger();val reads=AtomicInteger();var proof="other"
        credentials.managementServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Gateway fixture"}""")
                request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"},{"name":"hermes"}]}""")
                request.path in listOf("/dashboard/api/mcp/servers?profile=worker","/dashboard/api/mcp/servers?profile=hermes")->json("""{"servers":[]}""")
                request.path=="/dashboard/api/auth/ws-ticket"->{assertEquals("POST",request.method);assertEquals("Bearer management-gateway-fixture",request.getHeader("Authorization"));tickets.incrementAndGet();json("""{"ticket":"${"g".repeat(43)}","ttl_seconds":30}""")}
                request.path=="/dashboard/api/ws"->{
                    assertNull(request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"));assertEquals("hermes-gateway-v1, hermes-gateway-ticket.${"g".repeat(43)}",request.getHeader("Sec-WebSocket-Protocol"))
                    MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                        override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                        override fun onMessage(webSocket:WebSocket,text:String) {
                            val q=Json.parseToJsonElement(text).jsonObject;assertEquals("cron.manage",q.text("method"));assertEquals("list",q["params"]!!.jsonObject.text("action"));assertEquals("worker",q["params"]!!.jsonObject.text("profile"));reads.incrementAndGet()
                            webSocket.send(buildJsonObject {put("jsonrpc","2.0");put("id",q["id"]!!);put("result",buildJsonObject {put("success",true);put("scoped",proof);put("count",1);put("gateway_running",false);put("jobs",buildJsonArray {add(buildJsonObject {put("job_id","gateway-fixture");put("name","Gateway paused fixture");put("enabled",false);put("state","paused");put("schedule","Every hour");put("prompt_preview","Read-only preview");put("last_error","private-error-fixture");put("base_url","https://private-provider-fixture/?key=private-fixture")})})})}.toString())
                        }
                    }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                }
                else->json("{}").setResponseCode(404)
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,"inference-gateway-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("management-gateway-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick();openGatewaySection();assertEquals(0,tickets.get())
            compose.onNodeWithTag("gateway-cron-list").performScrollToNode(hasTestTag("gateway-cron-load"));compose.onNodeWithTag("gateway-cron-load").performClick();assertEquals(0,tickets.get())
            compose.onNodeWithTag("gateway-cron-review").performScrollToNode(hasText(Words.GatewayConnectionEffect.en));compose.onNodeWithTag("gateway-cron-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error!=null};assertNull(management.state.value.gateway.inventory);assertEquals(Words.GatewayScopeUnsupported,management.state.value.error);assertEquals(1,reads.get())
            proof="worker";compose.onNodeWithTag("gateway-cron-list").performScrollToNode(hasTestTag("gateway-cron-load"));compose.onNodeWithTag("gateway-cron-load").performClick();assertEquals(1,tickets.get());compose.onNodeWithTag("gateway-cron-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.gateway.inventory!=null};assertEquals(2,tickets.get());assertEquals(2,reads.get())
            compose.onNodeWithTag("gateway-cron-list").performScrollToNode(hasText("Gateway paused fixture"));compose.onNodeWithText("Gateway paused fixture").assertExists();compose.onNodeWithText("private-error-fixture",substring=true).assertDoesNotExist();compose.onNodeWithText("private-provider-fixture",substring=true).assertDoesNotExist()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/gateway-cron-read.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("gateway-cron-close").performClick()
            compose.runOnUiThread {management.chooseProfile("hermes")};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.profile=="hermes"}
            openGatewaySection()
            compose.onNodeWithTag("gateway-cron-list").performScrollToNode(hasTestTag("gateway-cron-load"));compose.onNodeWithTag("gateway-cron-load").assertIsNotEnabled();assertEquals(2,tickets.get())
            compose.onNodeWithTag("gateway-cron-close").performClick();compose.runOnUiThread {management.bind("unconfigured")};assertNull(management.state.value.gateway.inventory);assertEquals(2,reads.get())
        }
    }
}
