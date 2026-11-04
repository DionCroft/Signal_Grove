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
import java.util.concurrent.atomic.AtomicReference

class PluginInventoryFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
    private val inventory="""{"plugins":[{"name":"Fixture plugin","key":"tools/fixture","version":"1.2.3","description":"Public plugin description","source":"user","status":"not enabled","portable":true,"has_desktop_half":false,"install_dir":"/private-plugin-home","settings_schema":[{"value":"private-plugin-setting"}],"servers":[{"name":"fixture-server","state":"missing_env","sentence":"Missing FIXTURE_KEY"}],"tools":["fixture_tool"]}],"user_count":1,"bundled_count":0}"""
    private fun exercise(hold:Boolean,toggleFlow:Boolean=false) {
        val writes=AtomicInteger();val mcpReads=AtomicInteger();val status=AtomicReference("not enabled");val tickets=AtomicInteger();val calls=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when {
                q.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                q.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                q.path=="/dashboard/api/auth/me"->json("""{"provider":"password","user_id":"fixture-user","display_name":"Plugin fixture"}""")
                q.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"},{"name":"hermes"}]}""")
                q.path in setOf("/dashboard/api/mcp/servers?profile=worker","/dashboard/api/mcp/servers?profile=hermes")->{mcpReads.incrementAndGet();json("""{"servers":[]}""")}
                q.path=="/dashboard/api/auth/ws-ticket"->{assertEquals("Bearer plugin-management-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json("""{"ticket":"${"p".repeat(43)}","ttl_seconds":30}""")}
                q.path=="/dashboard/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(socket:WebSocket,response:Response) {socket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(socket:WebSocket,text:String) {
                        val request=Json.parseToJsonElement(text).jsonObject;assertEquals("plugins.manage",request.text("method"));val params=request["params"]!!.jsonObject;assertEquals("worker",params.text("profile"));calls.incrementAndGet();entered.countDown();if(hold) release.await(10,TimeUnit.SECONDS)
                        val result=when(params.text("action")) {
                            "list"->{assertEquals(buildJsonObject {put("profile","worker");put("action","list")},params);Json.parseToJsonElement(inventory.replace("\"status\":\"not enabled\"","\"status\":\"${status.get()}\""))}
                            "toggle"->{assertTrue(toggleFlow);assertEquals(setOf("profile","action","key","enable"),params.keys);assertEquals("tools/fixture",params.text("key"));val enabled=params["enable"]!!.jsonPrimitive.boolean;status.set(if(enabled) "enabled" else "disabled");writes.incrementAndGet();buildJsonObject {put("ok",true);put("name","tools/fixture");put("restart_required",!enabled);put("activation",PluginInstallFixture.acknowledgement()["activation"]!!)}}
                            else->error("Unexpected plugin action")
                        }
                        socket.send(buildJsonObject {put("jsonrpc","2.0");put("id",request["id"]!!);put("result",result)}.toString());returned.countDown()
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->json("{}").setResponseCode(404)
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("plugin-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("plugin-management-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("plugins-open"));compose.onNodeWithTag("plugins-open").performClick();assertEquals(0,tickets.get())
            compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();assertEquals(0,tickets.get());compose.onNodeWithTag("plugins-connection-review").performScrollToNode(hasText(Words.PluginConnectionEffect.en));compose.onNodeWithTag("plugins-read-confirm").performClick()
            try {
                if(hold) {
                    compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(5000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.plugins.inventory);assertEquals("unconfigured",management.state.value.apiScope)
                } else if(toggleFlow) {
                    compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null}
                    fun openToggle() {
                        compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-details-0"));compose.onNodeWithTag("plugin-details-0").performClick()
                        compose.onNodeWithTag("plugin-detail-view").performScrollToNode(hasTestTag("plugin-toggle-tools/fixture"));compose.onNodeWithTag("plugin-toggle-tools/fixture").performClick()
                    }
                    openToggle();compose.onNodeWithTag("plugin-toggle-review").assertExists();assertEquals(0,writes.get());compose.onNodeWithTag("plugin-toggle-cancel").performClick();assertEquals(0,writes.get())
                    compose.onNodeWithTag("plugin-toggle-tools/fixture").performClick();compose.onNodeWithTag("plugin-toggle-review").performScrollToNode(hasText(Words.PluginEnableEffect.en));compose.onNodeWithTag("plugin-toggle-confirm").performClick()
                    compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="PluginEnable" && it.state=="confirmed"}}
                    assertEquals("enabled",management.state.value.plugins.inventory!!.plugins.single().status);assertEquals(1,writes.get());assertNotNull(management.state.value.plugins.toggleReport)
                    openToggle();compose.onNodeWithTag("plugin-toggle-review").performScrollToNode(hasText(Words.PluginDisableEffect.en));compose.onNodeWithText(Words.PluginDisableEffect.en).assertExists();assertEquals(1,writes.get())
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-disable-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                    compose.onNodeWithTag("plugin-toggle-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="PluginDisable" && it.state=="confirmed"}}
                    assertEquals("disabled",management.state.value.plugins.inventory!!.plugins.single().status);assertEquals(2,writes.get());val receipt=management.receipts.value.single {it.action=="PluginDisable"};assertFalse(receipt.toString().contains("private-plugin"));assertEquals(3,tickets.get());assertEquals(7,calls.get())
                    compose.onNodeWithTag("plugins-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(3,tickets.get())
                    compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();assertEquals(3,tickets.get());compose.onNodeWithTag("plugins-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null}
                    assertEquals(2,writes.get());assertEquals(4,tickets.get());assertEquals(8,calls.get());assertEquals(1,mcpReads.get())
                } else {
                    compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null};compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-details-0"));compose.onNodeWithTag("plugin-details-0").performClick()
                    compose.onNodeWithTag("plugin-detail-view").performScrollToNode(hasText("fixture_tool"));compose.onNodeWithText("fixture_tool").assertExists();compose.onNodeWithText("private-plugin-home",substring=true).assertDoesNotExist();compose.onNodeWithText("private-plugin-setting",substring=true).assertDoesNotExist()
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-inventory.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                    compose.onNodeWithTag("plugin-detail-close").performClick();compose.onNodeWithTag("plugins-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-profile"));compose.onNodeWithTag("management-profile").performClick();compose.onNodeWithText("hermes").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.profile=="hermes"};assertNull(management.state.value.plugins.inventory)
                    compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("plugins-open"));compose.onNodeWithTag("plugins-open").performClick();compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").assertIsNotEnabled();assertEquals(1,tickets.get())
                }
                if(!toggleFlow) assertEquals(1,calls.get())
            } finally {release.countDown()}
        }
    }
    @Test fun enableAndDisableRequireSeparateReviewsAndObservePluginsWithoutReplayingChanges()=exercise(false,true)
    @Test fun explicitConnectionReviewReadsOnlyScopedPluginReportsAndRejectsAliasProfiles()=exercise(false)
    @Test fun aDelayedPluginReportCannotCrossConnections()=exercise(true)
}
