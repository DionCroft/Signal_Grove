package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

class PluginSettingsFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(held:Boolean=false) {
        val tickets=AtomicInteger();val reads=AtomicInteger();val writes=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when {
                q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                q.path=="/dashboard/api/mcp/servers?profile=worker"->json(buildJsonObject {put("servers",JsonArray(emptyList()))})
                q.path=="/dashboard/api/auth/ws-ticket"->{assertEquals("Bearer settings-ui-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json(buildJsonObject {put("ticket","f".repeat(43));put("ttl_seconds",30)})}
                q.path=="/dashboard/api/ws"->MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val params=j["params"]!!.jsonObject;assertEquals("plugins.manage",j.text("method"));assertEquals("worker",params.text("profile"))
                        val result=when(params.text("action")) {
                            "list"->{val n=reads.incrementAndGet();if(held && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)};PluginSettingsFixture.inventory(after=writes.get()>0)}
                            "settings"->{assertEquals(setOf("profile","action","key","values"),params.keys);assertEquals(PluginUpdateFixture.key,params.text("key"));assertEquals(buildJsonObject {put("endpoint",PluginSettingsFixture.privateValue)},params["values"]);writes.incrementAndGet();buildJsonObject {put("ok",true);put("name",PluginUpdateFixture.key);put("written",buildJsonArray {add("endpoint")})}}
                            else->error("Unexpected mutation")
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString());if(held && reads.get()>=2) returned.countDown()
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                else->MockResponse().setResponseCode(404)
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,"settings-inference-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("settings-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
                compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}}
                compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("plugins-open"));compose.onNodeWithTag("plugins-open").performClick();assertEquals(0,tickets.get());compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();compose.onNodeWithTag("plugins-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null}
                assertNull(management.state.value.plugins.settings);compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-details-0"));compose.onNodeWithTag("plugin-details-0").performClick();compose.onNodeWithTag("plugin-detail-view").performScrollToNode(hasTestTag("plugin-settings-open"));compose.onNodeWithTag("plugin-settings-open").performClick();assertEquals(1,tickets.get());compose.onNodeWithTag("plugin-settings-read-cancel").performClick();assertEquals(1,tickets.get());compose.onNodeWithTag("plugin-settings-open").performClick();compose.onNodeWithTag("plugin-settings-read-confirm").performClick()
                if(held) {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitForIdle();assertEquals("unconfigured",management.state.value.apiScope);assertNull(management.state.value.plugins.settings);assertEquals(0,writes.get());return@use}
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.settings!=null};assertEquals(2,tickets.get());assertEquals(0,writes.get());compose.onNodeWithText(PluginSettingsFixture.privateDefault,substring=true).assertDoesNotExist();compose.onNodeWithTag("plugin-settings-list").performScrollToNode(hasTestTag("plugin-settings-show"));compose.onNodeWithTag("plugin-settings-show").performClick();compose.onNodeWithTag("plugin-settings-list").performScrollToNode(hasText("\""+PluginSettingsFixture.privateDefault+"\""));compose.onNodeWithText("\""+PluginSettingsFixture.privateDefault+"\"").assertExists();compose.onNodeWithTag("plugin-setting-edit-api_key").assertDoesNotExist()
                fun editAndReview() {compose.onNodeWithTag("plugin-settings-list").performScrollToNode(hasTestTag("plugin-setting-edit-endpoint"));compose.onNodeWithTag("plugin-setting-edit-endpoint").performClick();compose.onNodeWithTag("plugin-setting-value").performTextReplacement(PluginSettingsFixture.privateValue);androidx.test.espresso.Espresso.closeSoftKeyboard();compose.onNodeWithTag("plugin-setting-review").performClick();compose.onNodeWithTag("plugin-setting-confirm").assertIsNotEnabled()}
                editAndReview();compose.onNodeWithTag("plugin-setting-confirm-review").performScrollToNode(hasTestTag("plugin-setting-review-show"));compose.onNodeWithTag("plugin-setting-review-show").performClick();compose.onNodeWithTag("plugin-setting-confirm").assertIsNotEnabled();compose.onNodeWithTag("plugin-setting-confirm-review").performScrollToNode(hasTestTag("plugin-setting-approve"));compose.onNodeWithTag("plugin-setting-approve").performClick();compose.onNodeWithTag("plugin-setting-confirm").assertIsEnabled();compose.onNodeWithTag("plugin-setting-cancel").performClick();assertEquals(0,writes.get())
                editAndReview();compose.onNodeWithTag("plugin-setting-confirm-review").performScrollToNode(hasTestTag("plugin-setting-review-show"));compose.onNodeWithTag("plugin-setting-review-show").performClick();compose.onNodeWithTag("plugin-setting-confirm").assertIsNotEnabled();compose.onNodeWithTag("plugin-setting-confirm-review").performScrollToNode(hasTestTag("plugin-setting-approve"));compose.onNodeWithTag("plugin-setting-approve").performClick()
                val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-setting-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                compose.onNodeWithTag("plugin-setting-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.settings!=null && management.receipts.value.any {it.action=="PluginSettings" && it.state=="confirmed"}};assertEquals(1,writes.get());assertEquals(3,tickets.get());val receipt=runBlocking {app.database.dao().managementOperations(scope).first().single {it.action=="PluginSettings"}};assertFalse(receipt.toString().contains(PluginSettingsFixture.privateValue));assertFalse(receipt.toString().contains(PluginSettingsFixture.privateDefault))
                compose.onNodeWithTag("plugin-settings-close").performClick();compose.waitUntil(5000) {management.state.value.plugins.settings==null};assertEquals(1,writes.get())
            } finally {release.countDown();compose.runOnUiThread {management.cancel()}}
        }
    }
    @Test fun privateSettingsReadAndOneFreshReviewedSaveRequireSeparateConsent()=exercise()
    @Test fun delayedPrivateReadCannotRepopulateAnotherConnection()=exercise(true)
}
