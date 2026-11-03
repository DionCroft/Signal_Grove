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

class PluginUpdateFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String) {
        val catalog=mode in setOf("caps","catalog-scan");val hold=mode=="held";val tickets=AtomicInteger();val calls=AtomicInteger();val sends=AtomicInteger();val publications=AtomicInteger();val mcpReads=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server ->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when {
                q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user");put("display_name","Update fixture")})
                q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                q.path=="/dashboard/api/mcp/servers?profile=worker"->{mcpReads.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                q.path=="/dashboard/api/auth/ws-ticket"->{assertEquals("Bearer update-ui-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json(buildJsonObject {put("ticket","u".repeat(43));put("ttl_seconds",30)})}
                q.path=="/dashboard/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val p=j["params"]!!.jsonObject;assertEquals("worker",p.text("profile"));calls.incrementAndGet()
                        val result=if(j.text("method")=="plugins.catalog.lookup") PluginUpdateFixture.catalog() else {
                            assertEquals("plugins.manage",j.text("method"));when(p.text("action")) {
                                "list"->PluginUpdateFixture.inventory(catalog,publications.get()>0,if(mode=="caps") "rename" else "")
                                "update"->{
                                    val n=sends.incrementAndGet();assertEquals(setOf("profile","action","name","accept_capabilities","force"),p.keys);assertEquals(PluginUpdateFixture.install,p.text("name"));assertEquals(JsonPrimitive(n==2 && mode=="caps"),p["accept_capabilities"]);assertEquals(JsonPrimitive(n==2 && mode=="caution"),p["force"]);entered.countDown()
                                    if(hold) {release.await(10,TimeUnit.SECONDS);publications.incrementAndGet();PluginUpdateFixture.accepted()}
                                    else when(mode) {"caps"->if(n==1) PluginUpdateFixture.capability() else {publications.incrementAndGet();PluginUpdateFixture.accepted("rename",true)};"caution"->PluginUpdateFixture.scan(if(n==1) "caution" else "dangerous");else->PluginUpdateFixture.scan()}
                                }
                                else->error("Unexpected action")
                            }
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString());if(p.text("action")=="update") returned.countDown()
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->MockResponse().setResponseCode(404)
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,"update-inference-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("update-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
                compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}}
                compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("plugins-open"));compose.onNodeWithTag("plugins-open").performClick();assertEquals(0,tickets.get());compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();compose.onNodeWithTag("plugins-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null}
                fun openUpdateReview() {
                    val tag="plugin-update-manifest-key"
                    try {
                        compose.waitUntil(10000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.let {isEnabled().matches(it)}==true && management.receipts.value.none {it.state in setOf("sending","unknown")}}
                        compose.onNodeWithTag(tag).assertIsEnabled().performClick()
                        compose.waitUntil(10000) {compose.onAllNodesWithTag("plugin-update-review").fetchSemanticsNodes().size==1}
                    } catch(e:Exception) {
                        runCatching {val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-update-review-failure.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
                        val state=management.state.value;val node=compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()
                        throw AssertionError("Plugin review missing: busy=${state.busy}, error=${state.error?.name}, receipts=${management.receipts.value.map {it.action+":"+it.state}}, inventory=${state.plugins.inventory?.plugins?.map {it.key}}, enabled=${node?.let {isEnabled().matches(it)}}, bounds=${node?.boundsInRoot}",e)
                    }
                }
                compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-details-0"));compose.onNodeWithTag("plugin-details-0").performClick();compose.onNodeWithTag("plugin-detail-view").performScrollToNode(hasTestTag("plugin-update-manifest-key"));openUpdateReview();compose.onNodeWithTag("plugin-update-review").assertExists();assertEquals(0,sends.get())
                if(!hold) {compose.onNodeWithTag("plugin-update-cancel").performClick();assertEquals(0,sends.get());openUpdateReview()}
                compose.onNodeWithTag("plugin-update-confirm").performClick()
                if(hold) {
                    compose.waitUntil(10000) {entered.count==0L};assertNull(management.state.value.plugins.inventory);compose.runOnUiThread {management.bind("unconfigured")};compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first().singleOrNull()?.state=="unknown"}};release.countDown();compose.waitUntil(5000) {returned.count==0L};compose.waitForIdle();assertEquals("unconfigured",management.state.value.apiScope);assertNull(management.state.value.plugins.updateDecision);assertNull(management.state.value.plugins.updateReport);assertEquals(1,sends.get());assertEquals(2,tickets.get());return@use
                }
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.updateDecision!=null};val origin=management.state.value.plugins.updateDecision!!.receiptKey;assertEquals(1,sends.get());assertEquals(0,publications.get());compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-update-review-decision"))
                if(mode=="catalog-scan") {compose.onNodeWithTag("plugin-update-review-decision").assertIsNotEnabled();compose.onNodeWithTag("plugins-list").performScrollToNode(hasText(Words.PluginUpdateCatalogScanLimit.en));compose.onNodeWithText(Words.PluginUpdateCatalogScanLimit.en).assertExists();assertEquals(1,sends.get());return@use}
                compose.onNodeWithTag("plugin-update-review-decision").performClick();compose.onNodeWithTag("plugin-update-confirm").assertIsNotEnabled();compose.onNodeWithTag("plugin-update-review").performScrollToNode(hasTestTag("plugin-update-grant"));compose.onNodeWithTag("plugin-update-grant").performClick()
                if(mode=="caps") {
                    compose.onNodeWithTag("plugin-update-confirm").assertIsNotEnabled();compose.onNodeWithText("private-update-reference",substring=true).assertDoesNotExist();compose.onNodeWithTag("plugin-update-review").performScrollToNode(hasTestTag("plugin-update-review-show-details"));compose.onNodeWithTag("plugin-update-review-show-details").performClick();compose.onNodeWithText("private-update-reference",substring=true).assertExists()
                    compose.onNodeWithTag("plugin-update-review").performScrollToNode(hasText(Words.PluginUpdateCapabilityGrant.en));val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-update-capability-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                }
                compose.onNodeWithTag("plugin-update-confirm").assertIsEnabled().performClick()
                val expectedState=if(mode=="caps") "confirmed" else "acknowledged"
                try {compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.count {it.action=="PluginUpdate"}==2 && management.receipts.value.any {it.action=="PluginUpdate" && it.key!=origin && it.state==expectedState} && (mode!="caution" || management.state.value.plugins.updateDecision?.verdict=="dangerous")}}
                catch(e:androidx.compose.ui.test.ComposeTimeoutException) {
                    runCatching {val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-update-outcome-failure.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
                    val saved=runBlocking {app.database.dao().managementOperations(scope).first().map {it.action+":"+it.state}}
                    val state=management.state.value
                    throw AssertionError("Update outcome: expected=$expectedState, error=${state.error?.name}, observable=${management.receipts.value.map {it.action+":"+it.state}}, saved=$saved, rows=${state.plugins.inventory?.plugins?.map {it.key+":"+it.installedSha+":"+it.catalogSha+":"+it.updateAvailable}}",e)
                }
                assertEquals(2,sends.get());assertEquals(3,tickets.get())
                if(mode=="caution") {assertEquals("dangerous",management.state.value.plugins.updateDecision!!.verdict);compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-update-review-decision"));compose.onNodeWithTag("plugin-update-review-decision").assertIsNotEnabled();assertEquals(0,publications.get());return@use}
                val receipt=management.receipts.value.single {it.action=="PluginUpdate" && it.key!=origin && it.state=="confirmed"};assertEquals("renamed-key",management.state.value.plugins.inventory!!.plugins.single().key);assertNotNull(management.state.value.plugins.updateReport);assertEquals(1,publications.get());assertEquals(8,calls.get());assertFalse(receipt.toString().contains("private-update-reference"));compose.onNodeWithTag("plugins-close").performClick()
                compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(3,tickets.get());compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();assertEquals(3,tickets.get());compose.onNodeWithTag("plugins-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null};assertEquals(2,sends.get());assertEquals(4,tickets.get());assertEquals(9,calls.get());assertEquals(1,mcpReads.get())
            } finally {release.countDown()}
        }
    }
    @Test fun capabilityGrantRequiresDisclosureAndFreshReviewThenObservesPluginsWithoutReplay()=exercise("caps")
    @Test fun cautionOverrideIsSeparateAndADangerousRescanHasNoFurtherOverride()=exercise("caution")
    @Test fun catalogScanPolicyNeverExposesAnIneffectiveForceButton()=exercise("catalog-scan")
    @Test fun aHeldUpdateReplyCannotPopulateAnotherConnection()=exercise("held")
}
