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
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginInventoryFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
    private val inventory="""{"plugins":[{"name":"Fixture plugin","key":"tools/fixture","version":"1.2.3","description":"Public plugin description","source":"user","status":"not enabled","portable":true,"has_desktop_half":false,"install_dir":"/private-plugin-home","settings_schema":[{"value":"private-plugin-setting"}],"servers":[{"name":"fixture-server","state":"missing_env","sentence":"Missing FIXTURE_KEY"}],"tools":["fixture_tool"]}],"user_count":1,"bundled_count":0}"""
    private fun exercise(hold:Boolean) {
        val tickets=AtomicInteger();val calls=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when {
                q.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                q.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                q.path=="/dashboard/api/auth/me"->json("""{"provider":"password","user_id":"fixture-user","display_name":"Plugin fixture"}""")
                q.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"},{"name":"hermes"}]}""")
                q.path in setOf("/dashboard/api/mcp/servers?profile=worker","/dashboard/api/mcp/servers?profile=hermes")->json("""{"servers":[]}""")
                q.path=="/dashboard/api/auth/ws-ticket"->{assertEquals("Bearer plugin-management-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json("""{"ticket":"${"p".repeat(43)}","ttl_seconds":30}""")}
                q.path=="/dashboard/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(socket:WebSocket,response:Response) {socket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(socket:WebSocket,text:String) {
                        val request=Json.parseToJsonElement(text).jsonObject;assertEquals("plugins.manage",request.text("method"));assertEquals(buildJsonObject {put("profile","worker");put("action","list")},request["params"]);calls.incrementAndGet();entered.countDown();if(hold) release.await(10,TimeUnit.SECONDS)
                        socket.send(buildJsonObject {put("jsonrpc","2.0");put("id",request["id"]!!);put("result",Json.parseToJsonElement(inventory))}.toString());returned.countDown()
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->json("{}").setResponseCode(404)
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,"plugin-inference-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("plugin-management-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("plugins-open"));compose.onNodeWithTag("plugins-open").performClick();assertEquals(0,tickets.get())
            compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();assertEquals(0,tickets.get());compose.onNodeWithTag("plugins-connection-review").performScrollToNode(hasText(Words.GatewayConnectionEffect.en));compose.onNodeWithTag("plugins-read-confirm").performClick()
            try {
                if(hold) {
                    compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(5000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.plugins.inventory);assertEquals("unconfigured",management.state.value.apiScope)
                } else {
                    compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null};compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-details-0"));compose.onNodeWithTag("plugin-details-0").performClick()
                    compose.onNodeWithTag("plugin-detail-view").performScrollToNode(hasText("fixture_tool"));compose.onNodeWithText("fixture_tool").assertExists();compose.onNodeWithText("private-plugin-home",substring=true).assertDoesNotExist();compose.onNodeWithText("private-plugin-setting",substring=true).assertDoesNotExist()
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-inventory.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                    compose.onNodeWithTag("plugin-detail-close").performClick();compose.onNodeWithTag("plugins-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-profile"));compose.onNodeWithTag("management-profile").performClick();compose.onNodeWithText("hermes").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.profile=="hermes"};assertNull(management.state.value.plugins.inventory)
                    compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("plugins-open"));compose.onNodeWithTag("plugins-open").performClick();compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").assertIsNotEnabled();assertEquals(1,tickets.get())
                }
                assertEquals(1,calls.get())
            } finally {release.countDown()}
        }
    }
    @Test fun explicitConnectionReviewReadsOnlyScopedPluginReportsAndRejectsAliasProfiles()=exercise(false)
    @Test fun aDelayedPluginReportCannotCrossConnections()=exercise(true)
}
