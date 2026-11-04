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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginSourceFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private val pin="a".repeat(40)
    private val inspected="b".repeat(40)
    private fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
    private fun exercise(hold:Boolean,changed:Boolean=false,install:Boolean=false) {
        val tickets=AtomicInteger();val calls=AtomicInteger();val lookups=AtomicInteger();val writes=AtomicInteger();val mcpReads=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        credentials.managementServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when {
                q.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                q.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                q.path=="/dashboard/api/auth/me"->json("""{"provider":"password","user_id":"fixture-user","display_name":"Source fixture"}""")
                q.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                q.path=="/dashboard/api/mcp/servers?profile=worker"->{mcpReads.incrementAndGet();json("""{"servers":[]}""")}
                q.path=="/dashboard/api/auth/ws-ticket"->{assertEquals("Bearer source-management-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json("""{"ticket":"${"s".repeat(43)}","ttl_seconds":30}""")}
                q.path=="/dashboard/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val p=j["params"]!!.jsonObject;assertEquals("worker",p.text("profile"));calls.incrementAndGet()
                        val result=if(j.text("method")=="plugins.catalog.lookup") {assertEquals(setOf("profile","name"),p.keys);assertEquals("fixture-plugin",p.text("name"));val advertised=if(changed && lookups.incrementAndGet()>1) "c".repeat(40) else pin;Json.parseToJsonElement("""{"status":"found","entry":{"name":"fixture-plugin","repo":"https://github.com/example/fixture","sha":"$advertised","subdir":"plugin","tier":"community"}}""")}
                        else {assertEquals("plugins.manage",j.text("method"));when(p.text("action")) {
                            "onboarding"->{assertEquals(setOf("profile","action"),p.keys);Json.parseToJsonElement("""{"onboarding":[{"name":"fixture-plugin","title":"Featured fixture","description":"Host selection fixture","tier":"community","platforms":["linux"],"app_state":"present","sentence":"Host fixture availability"}]}""")}
                            "inspect"->{if(hold) {assertEquals("https://git.example/fixture?key=private-source-ui",p.text("identifier"));assertFalse(p.containsKey("catalog_name"));entered.countDown();release.await(10,TimeUnit.SECONDS)} else {assertEquals("fixture-plugin",p.text("catalog_name"));assertFalse(p.containsKey("identifier"))};Json.parseToJsonElement("""{"ok":true,"agent":true,"desktop":true,"agent_name":"fixture-agent","desktop_name":"fixture-desktop","sha":"$inspected","warnings":["Host inspection fixture warning"],"insecure":false}""")}
                            "list"->PluginInstallFixture.inventory(writes.get()>0)
                            "install"->{assertTrue(install);assertEquals(setOf("profile","action","force","enable","inspected_sha","catalog_name"),p.keys);assertEquals(JsonPrimitive(false),p["force"]);assertEquals(JsonPrimitive(true),p["enable"]);assertEquals(inspected,p.text("inspected_sha"));assertFalse(p.containsKey("ref"));writes.incrementAndGet();PluginInstallFixture.acknowledgement()}
                            else->error("Unexpected plugin operation")
                        }}
                        s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString());returned.countDown()
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->json("{}").setResponseCode(404)
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("source-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("source-management-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("plugins-open"));compose.onNodeWithTag("plugins-open").performClick();compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugin-catalog-open"));compose.onNodeWithTag("plugin-catalog-open").performClick();assertEquals(0,tickets.get())
            try {
                if(hold) {
                    compose.onNodeWithTag("plugin-catalog-list").performScrollToNode(hasTestTag("plugin-repository"));compose.onNodeWithTag("plugin-repository").performTextInput("https://git.example/fixture?key=private-source-ui")
                    compose.runOnUiThread {compose.activity.currentFocus?.clearFocus();androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())};compose.waitForIdle()
                    compose.onNodeWithTag("plugin-catalog-list").performScrollToNode(hasTestTag("plugin-inspect-repository"));compose.onNodeWithTag("plugin-inspect-repository").performClick();assertEquals(0,tickets.get());compose.onNodeWithTag("plugin-source-confirm").performClick();compose.waitUntil(10000) {entered.count==0L}
                    compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(5000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.plugins.inspection);assertNull(management.state.value.plugins.lookup);assertEquals("unconfigured",management.state.value.apiScope);assertEquals(1,calls.get())
                } else {
                    compose.onNodeWithTag("plugin-featured-read").performClick();assertEquals(0,tickets.get());compose.onNodeWithTag("plugin-source-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.featured!=null}
                    compose.onNodeWithTag("plugin-catalog-list").performScrollToNode(hasTestTag("plugin-featured-0"));compose.onNodeWithTag("plugin-featured-0").performClick();assertEquals(1,tickets.get());compose.onNodeWithTag("plugin-source-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.lookup?.entry!=null}
                    compose.onNodeWithTag("plugin-catalog-list").performScrollToNode(hasTestTag("plugin-inspect-catalog"));compose.onNodeWithTag("plugin-inspect-catalog").performClick();assertEquals(2,tickets.get());compose.onNodeWithTag("plugin-source-review").performScrollToNode(hasText(Words.PluginInspectionEffect.en));compose.onNodeWithText(Words.PluginInspectionEffect.en).assertExists();compose.onNodeWithTag("plugin-source-confirm").performClick()
                    if(changed) {
                        compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertNull(management.state.value.plugins.inspection);assertEquals(3,calls.get());return
                    }
                    compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inspection?.ok==true}
                    compose.onNodeWithTag("plugin-catalog-list").performScrollToNode(hasTestTag("plugin-inspection-sha"));compose.onNodeWithTag("plugin-inspection-sha").assertTextEquals(inspected);assertEquals("fixture-agent",management.state.value.plugins.inspection!!.agentName)
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-source-inspection.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                    assertEquals(5,calls.get());assertEquals(3,tickets.get());assertEquals(0,writes.get())
                    if(install) {
                        compose.onNodeWithTag("plugin-catalog-list").performScrollToNode(hasTestTag("plugin-install-enable"));compose.onNodeWithTag("plugin-install-enable").assertIsOff().performClick();compose.onNodeWithTag("plugin-catalog-list").performScrollToNode(hasTestTag("plugin-install-review"));compose.onNodeWithTag("plugin-install-review").performClick();assertEquals(3,tickets.get());assertEquals(0,writes.get())
                        compose.onNodeWithTag("plugin-install-confirmation").performScrollToNode(hasText(Words.PluginInstallEnableEffect.en));compose.onNodeWithText(Words.PluginInstallEnableEffect.en).assertExists();compose.onNodeWithTag("plugin-install-confirm").assertIsDisplayed()
                        val reviewShot=File(compose.activity.getExternalFilesDir(null),"qa/plugin-install-review.png");reviewShot.parentFile!!.mkdirs();reviewShot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                        compose.onNodeWithTag("plugin-install-confirm").performClick();compose.waitUntil(15000) {!management.state.value.busy && management.receipts.value.any {it.action=="PluginInstall" && it.state=="acknowledged"}}
                        assertEquals(1,writes.get());assertEquals(4,tickets.get());assertEquals(10,calls.get());assertNull(management.state.value.plugins.inspection)
                        val report=management.state.value.plugins.installReport!!;assertEquals(listOf("fixture_deferred_tool"),report.deferred["tools"]);assertEquals(listOf("fixture_hook"),report.activated["hooks"]);assertEquals(false,report.servers.single().connected);assertEquals(true,report.restartRequired)
                        compose.onNodeWithTag("plugin-catalog-list").performScrollToNode(hasText("fixture_deferred_tool"));compose.onNodeWithText("fixture_deferred_tool").assertExists();compose.onNodeWithText("private-install-error",substring=true).assertDoesNotExist();compose.onNodeWithText("private-install-dependency",substring=true).assertDoesNotExist();compose.onNodeWithText("private-install-home",substring=true).assertDoesNotExist()
                        val receipt=management.receipts.value.single {it.action=="PluginInstall"};assertEquals(inspected,Json.parseToJsonElement(receipt.requestSummary).jsonObject.text("approved_commit"));assertFalse(receipt.toString().contains("private-install-dependency"))
                        compose.onNodeWithTag("plugin-catalog-close").performClick();compose.onNodeWithTag("plugins-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(4,tickets.get())
                        compose.onNodeWithTag("plugins-list").performScrollToNode(hasTestTag("plugins-read"));compose.onNodeWithTag("plugins-read").performClick();compose.onNodeWithTag("plugins-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.plugins.inventory!=null}
                        assertEquals(1,writes.get());assertEquals(5,tickets.get());assertEquals(11,calls.get());assertEquals(1,mcpReads.get());assertEquals("acknowledged",management.receipts.value.single {it.key==receipt.key}.state)
                    } else {compose.onNodeWithTag("plugin-catalog-close").performClick();assertNull(management.state.value.plugins.inspection);assertNull(management.state.value.plugins.lookup)}
                }
            } finally {release.countDown()}
        }
    }
    @Test fun featuredLookupAndInspectionEachRequireReviewAndNeverInstallAnything()=exercise(false)
    @Test fun aHeldRepositoryInspectionIsDiscardedAfterTheConnectionChanges()=exercise(true)
    @Test fun changedCatalogMetadataRefusesBeforeSourceInspection()=exercise(false,true)
    @Test fun installationRequiresItsOwnReviewAndKeepsActivationStagesSeparate()=exercise(false,install=true)
}
