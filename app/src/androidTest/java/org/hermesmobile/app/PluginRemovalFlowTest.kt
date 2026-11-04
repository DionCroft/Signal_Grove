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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PluginRemovalFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(hold:Boolean) {
        val tickets=AtomicInteger();val calls=AtomicInteger();val writes=AtomicInteger();val mcpReads=AtomicInteger();val removed=AtomicBoolean();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        credentials.managementServer().use {server ->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            fun inventory():JsonObject {
                val base=PluginRemovalFixture.inventory(if(removed.get()) "fallback" else "before");val bundled=buildJsonObject {put("name","Bundled fixture");put("key","bundled-key");put("source","bundled");put("version","1");put("status","enabled");put("servers",JsonArray(emptyList()))}
                return JsonObject(base+mapOf("plugins" to JsonArray(base["plugins"]!!.jsonArray+bundled),"bundled_count" to JsonPrimitive(if(removed.get()) 2 else 1)))
            }
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when {
                q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user");put("display_name","Removal fixture")})
                q.path=="/dashboard/api/profiles"->json(PluginRemovalFixture.profiles())
                q.path=="/dashboard/api/mcp/servers?profile=worker"->{mcpReads.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                q.path=="/dashboard/api/auth/ws-ticket"->{assertEquals("Bearer removal-ui-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json(buildJsonObject {put("ticket","r".repeat(43));put("ttl_seconds",30)})}
                q.path=="/dashboard/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val p=j["params"]!!.jsonObject;assertEquals("plugins.manage",j.text("method"));assertEquals("worker",p.text("profile"));calls.incrementAndGet()
                        val result=when(p.text("action")) {
                            "list"->inventory()
                            "remove"->{assertEquals(setOf("profile","action","name"),p.keys);assertEquals(PluginRemovalFixture.install,p.text("name"));writes.incrementAndGet();entered.countDown();if(hold) release.await(10,TimeUnit.SECONDS);removed.set(true);buildJsonObject {put("ok",true);put("name",PluginRemovalFixture.install);put("cleared_memory_provider",true)}}
                            else->error("Unexpected plugin action")
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString());if(p.text("action")=="remove") returned.countDown()
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->MockResponse().setResponseCode(404)
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("removal-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("removal-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
                compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}}
                compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("plugins-open"));compose.onNodeWithTag("plugins-open").performClick();assertEquals(0,tickets.get())
                compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();compose.onNodeWithTag("plugins-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null}
                if(!hold) {
                    compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-details-1"));compose.onNodeWithTag("plugin-details-1").performClick();compose.onNodeWithTag("plugin-detail-view").performScrollToNode(hasTestTag("plugin-remove-bundled-key"));compose.onNodeWithTag("plugin-remove-bundled-key").assertIsNotEnabled();compose.onNodeWithTag("plugin-detail-close").performClick()
                }
                compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-details-0"));compose.onNodeWithTag("plugin-details-0").performClick();compose.onNodeWithTag("plugin-detail-view").performScrollToNode(hasTestTag("plugin-remove-manifest-key"));compose.onNodeWithTag("plugin-remove-manifest-key").performClick()
                compose.onNodeWithTag("plugin-remove-review").assertExists();compose.onNodeWithTag("plugin-remove-review").performScrollToNode(hasText(PluginRemovalFixture.install));compose.onNodeWithText(PluginRemovalFixture.install).assertExists();compose.onNodeWithText("private-removal-profile",substring=true).assertDoesNotExist();assertEquals(0,writes.get())
                if(!hold) {compose.onNodeWithTag("plugin-remove-cancel").performClick();assertEquals(0,writes.get());compose.onNodeWithTag("plugin-remove-manifest-key").performClick()}
                compose.onNodeWithTag("plugin-remove-review").performScrollToNode(hasText(Words.PluginRemoveLimit.en));compose.onNodeWithText(Words.PluginRemoveLimit.en).assertExists()
                if(!hold) {val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-remove-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
                compose.onNodeWithTag("plugin-remove-confirm").performClick()
                if(hold) {
                    compose.waitUntil(10000) {entered.count==0L};assertNull(management.state.value.plugins.inventory);compose.runOnUiThread {management.bind("unconfigured")}
                    compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first().singleOrNull()?.state=="unknown"}}
                    release.countDown();compose.waitUntil(5000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.plugins.inventory);assertEquals("unconfigured",management.state.value.apiScope);assertEquals(1,writes.get());assertEquals(2,tickets.get())
                    val receipt=runBlocking {app.database.dao().managementOperations(scope).first().single()};assertEquals("PluginRemove",receipt.action);assertEquals("manifest-key",receipt.target);assertEquals("unknown",receipt.state)
                } else {
                    compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="PluginRemove" && it.state=="confirmed"}}
                    assertEquals("bundled",management.state.value.plugins.inventory!!.plugins.single {it.key=="manifest-key"}.source);assertEquals(1,writes.get());assertEquals(2,tickets.get());assertEquals(4,calls.get());val receipt=management.receipts.value.single {it.action=="PluginRemove"};assertFalse(receipt.toString().contains("private-removal-profile"))
                    compose.onNodeWithTag("plugins-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-result-${receipt.key}"));compose.onNodeWithTag("management-result-${receipt.key}").performClick();compose.onNodeWithText(Words.PluginMemoryProviderCleared.en+": "+Words.PluginReportedYes.en).assertExists();compose.onNodeWithTag("management-result-close").performClick()
                    compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(2,tickets.get());compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();assertEquals(2,tickets.get());compose.onNodeWithTag("plugins-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null}
                    assertEquals(1,writes.get());assertEquals(3,tickets.get());assertEquals(5,calls.get());assertEquals(1,mcpReads.get())
                }
            } finally {release.countDown()}
        }
    }
    @Test fun removalNeedsASeparateDirectoryReviewAndObservesFallbackWithoutReplaying()=exercise(false)
    @Test fun aLateRemovalReplyCannotPopulateAnotherManagementConnection()=exercise(true)
}
