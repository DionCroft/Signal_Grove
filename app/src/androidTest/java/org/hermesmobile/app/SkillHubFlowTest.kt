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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SkillHubFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(value:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun basic(request:RecordedRequest):MockResponse?=when {
        request.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
        request.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
        request.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("user_id","fixture-user");put("provider","password");put("display_name","Hub fixture")})
        request.path=="/dashboard/api/profiles"->json(buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker")})})})
        request.path=="/dashboard/api/mcp/servers?profile=worker"->json(buildJsonObject {put("servers",JsonArray(emptyList()))})
        else->null
    }
    private fun open(server:MockWebServer) {
        server.start()
        compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(server.url("inference/").toString(),"inference-hub-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
        compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
        val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",server.url("dashboard/").toString());put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("management-hub-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
        compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
        compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
        compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("skill-hub-open"));compose.onNodeWithTag("skill-hub-open").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hub.loaded}
    }
    @Test fun catalogPreviewSearchAndScanRemainReadsWithReportedIncompleteAndMaskedEvidence() {
        val writes=AtomicInteger();val changed=AtomicBoolean(true);val previews=AtomicInteger();val scans=AtomicInteger();val searches=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method!="GET") writes.incrementAndGet()
                basic(request)?.let {return it}
                assertEquals("Bearer management-hub-fixture",request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"));assertEquals("worker",request.requestUrl!!.queryParameter("profile"))
                return when(request.requestUrl!!.encodedPath) {
                    "/dashboard/api/skills/hub/sources"->json(SkillHubFixture.sources())
                    "/dashboard/api/skills/hub/official"->json(buildJsonObject {put("skills",buildJsonArray {add(SkillHubFixture.entry())})})
                    "/dashboard/api/skills/hub/preview"->{previews.incrementAndGet();val claw=request.requestUrl!!.queryParameter("identifier")=="clawhub/fixture-slug";json(SkillHubFixture.preview(claw,changed.getAndSet(false)))}
                    "/dashboard/api/skills/hub/scan"->{assertEquals("clawhub/fixture-slug",request.requestUrl!!.queryParameter("identifier"));scans.incrementAndGet();json(SkillHubFixture.scan())}
                    "/dashboard/api/skills/hub/search"->{assertEquals("fixture & source",request.requestUrl!!.queryParameter("q"));assertEquals("all",request.requestUrl!!.queryParameter("source"));assertEquals("20",request.requestUrl!!.queryParameter("limit"));searches.incrementAndGet();json(buildJsonObject {put("results",buildJsonArray {add(SkillHubFixture.entry(true))});put("timed_out",buildJsonArray {add("github")});put("installed",SkillHubFixture.sources()["installed"]!!)})}
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }}
            open(server);assertEquals(0,writes.get());compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-preview-0"));compose.onNodeWithTag("skill-hub-preview-0").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertNull(management.state.value.hub.preview)
            compose.onNodeWithTag("skill-hub-preview-0").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hub.preview!=null}
            compose.onNodeWithTag("skill-hub-preview-view").performScrollToNode(hasText("# Fixture instruction text"));compose.onNodeWithText("# Fixture instruction text").assertExists();assertEquals(0,writes.get());compose.onNodeWithTag("skill-hub-preview-close").performClick();assertNull(management.state.value.hub.preview)
            compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-featured"));compose.onNodeWithTag("skill-hub-featured").performClick();compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-preview-0"));compose.onNodeWithTag("skill-hub-preview-0").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hub.preview?.skill?.canonicalId=="clawhub/fixture-slug"}
            compose.onNodeWithTag("skill-hub-preview-view").performScrollToNode(hasTestTag("skill-hub-scan"));compose.onNodeWithTag("skill-hub-scan").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hub.scan!=null}
            compose.onNodeWithTag("skill-hub-scan-view").performScrollToNode(hasText(Words.SkillHubIncomplete.en));compose.onNodeWithText("license").assertExists();compose.onNodeWithTag("skill-hub-scan-view").performScrollToNode(hasText(Words.SkillHubSensitiveFinding.en));compose.onNodeWithText(Words.SkillHubSensitiveFinding.en).assertExists()
            compose.onNodeWithTag("skill-hub-scan-view").performScrollToNode(hasTestTag("skill-hub-install-review"));compose.onNodeWithTag("skill-hub-install-review").assertIsNotEnabled();compose.onNodeWithText("private-tier1-message",substring=true).assertDoesNotExist();compose.onNodeWithText("private-scan-home",substring=true).assertDoesNotExist();compose.onNodeWithText("private-install-home",substring=true).assertDoesNotExist()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/skill-hub-scan.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("skill-hub-scan-close").performClick();compose.onNodeWithTag("skill-hub-preview-close").performClick();assertNull(management.state.value.hub.preview);assertNull(management.state.value.hub.scan)
            compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-search-tab"));compose.onNodeWithTag("skill-hub-search-tab").performClick();compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-source"));compose.onNodeWithTag("skill-hub-source").performClick();compose.onNodeWithTag("skill-hub-source-github").assertIsNotEnabled();compose.onNodeWithTag("skill-hub-source-all").performClick()
            compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-query"));compose.onNodeWithTag("skill-hub-query").performTextInput("fixture & source")
            compose.runOnUiThread {compose.activity.currentFocus?.clearFocus();androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())};compose.waitForIdle()
            compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-search"));compose.onNodeWithTag("skill-hub-search").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hub.search!=null}
            compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasText(Words.SkillHubTimedOut.en));compose.onNodeWithText(Words.SkillHubTimedOut.en).assertExists();assertEquals(1,searches.get());assertEquals(1,scans.get());assertEquals(3,previews.get());assertEquals(0,writes.get())
            compose.onNodeWithTag("skill-hub-close").performClick();compose.runOnUiThread {management.bind("unconfigured")};assertFalse(management.state.value.hub.loaded)
        }
    }
    @Test fun unavailableOfficialCatalogLeavesFeaturedBrowseAndLatePreviewCannotCrossConnections() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
                basic(request)?.let {return it}
                return when(request.requestUrl!!.encodedPath) {
                    "/dashboard/api/skills/hub/sources"->json(SkillHubFixture.sources())
                    "/dashboard/api/skills/hub/official"->json(JsonObject(emptyMap())).setResponseCode(404)
                    "/dashboard/api/skills/hub/preview"->{entered.countDown();release.await(5,TimeUnit.SECONDS);returned.countDown();json(SkillHubFixture.preview(true))}
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }}
            open(server);assertNull(management.state.value.hub.official);compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-official"));compose.onNodeWithTag("skill-hub-official").assertIsNotEnabled()
            compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-featured"));compose.onNodeWithTag("skill-hub-featured").performClick();compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-preview-0"));compose.onNodeWithTag("skill-hub-preview-0").performClick();compose.waitUntil(5000) {entered.count==0L}
            compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(5000) {returned.count==0L};compose.waitForIdle();assertEquals("unconfigured",management.state.value.apiScope);assertNull(management.state.value.hub.preview);assertNull(management.state.value.hub.scan);assertFalse(management.state.value.hub.loaded)
        }
    }

    @Test fun installationNeedsSeparateReviewThenRecordsOnlyStartupAndObservesSkillsWithoutReplay() {
        val writes=AtomicInteger();val previews=AtomicInteger();val scans=AtomicInteger();val installedReads=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
                basic(request)?.let {return it}
                assertEquals("Bearer management-hub-fixture",request.getHeader("Authorization"));assertEquals("worker",request.requestUrl!!.queryParameter("profile"))
                return when(request.requestUrl!!.encodedPath) {
                    "/dashboard/api/skills/hub/sources"->json(JsonObject(SkillHubFixture.sources()+mapOf("installed" to JsonObject(emptyMap()))))
                    "/dashboard/api/skills/hub/official"->json(buildJsonObject {put("skills",buildJsonArray {add(SkillHubFixture.entry())})})
                    "/dashboard/api/skills/hub/preview"->{previews.incrementAndGet();json(SkillHubFixture.preview())}
                    "/dashboard/api/skills/hub/scan"->{scans.incrementAndGet();json(JsonObject(SkillHubFixture.scan()+mapOf("identifier" to JsonPrimitive("official/general/fixture"),"name" to JsonPrimitive("Official fixture"),"source" to JsonPrimitive("official/general/fixture"),"policy" to JsonPrimitive("allow"))))}
                    "/dashboard/api/skills"->{installedReads.incrementAndGet();MockResponse().setHeader("Content-Type","application/json").setBody("[]")}
                    "/dashboard/api/skills/hub/install"->{assertEquals("POST",request.method);assertEquals(buildJsonObject {put("identifier","official/general/fixture");put("profile","worker")},Json.parseToJsonElement(request.body.readUtf8()));writes.incrementAndGet();json(buildJsonObject {put("ok",true);put("pid",333);put("name",HubInstallRequest.actionName("official/general/fixture"))})}
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }}
            open(server);compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("skill-hub-preview-0"));compose.onNodeWithTag("skill-hub-preview-0").performClick();compose.waitUntil(10000) {management.state.value.hub.preview!=null && !management.state.value.busy}
            compose.onNodeWithTag("skill-hub-preview-view").performScrollToNode(hasTestTag("skill-hub-scan"));compose.onNodeWithTag("skill-hub-scan").performClick();compose.waitUntil(10000) {management.state.value.hub.scan!=null && !management.state.value.busy}
            compose.onNodeWithTag("skill-hub-scan-view").performScrollToNode(hasTestTag("skill-hub-install-review"));compose.onNodeWithTag("skill-hub-install-review").performClick();assertEquals(0,writes.get())
            compose.onNodeWithTag("skill-hub-install-confirmation").performScrollToNode(hasText(Words.SkillHubMutableSource.en));compose.onNodeWithText(Words.SkillHubMutableSource.en).assertExists();compose.onNodeWithTag("skill-hub-install-confirm").assertIsDisplayed();compose.onNodeWithText("Cancel").assertExists()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/skill-hub-install-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("skill-hub-install-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="HubInstall" && it.state=="acknowledged"}}
            assertEquals(1,writes.get());assertEquals(2,previews.get());assertEquals(2,scans.get());assertNull(management.state.value.hub.preview);assertNull(management.state.value.hub.scan)
            val receipt=management.receipts.value.single {it.action=="HubInstall"};assertFalse(receipt.resultSummary.contains("333"));assertFalse(receipt.resultSummary.contains("skills-install-"))
            compose.onNodeWithTag("skill-hub-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.skills.loaded};assertEquals(2,installedReads.get());assertEquals(1,writes.get());assertEquals("acknowledged",management.receipts.value.single {it.key==receipt.key}.state)
            compose.onNodeWithTag("host-skills-close").performClick()
        }
    }

    @Test fun maintenanceReviewsRemovalAndAllProfileUpdatesAndObservesSkillsWithoutReplay() {
        val writes=AtomicInteger();val removed=AtomicBoolean();val skillReads=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                basic(q)?.let {return it}
                assertEquals("worker",q.requestUrl!!.queryParameter("profile"));assertEquals("Bearer management-hub-fixture",q.getHeader("Authorization"))
                return when(q.requestUrl!!.encodedPath) {
                    "/dashboard/api/skills/hub/sources"->json(JsonObject(SkillHubFixture.sources()+mapOf("installed" to buildJsonObject {
                        if(!removed.get()) put("clawhub/fixture-slug",buildJsonObject {put("name","fixture-slug");put("trust_level","community");put("scan_verdict","safe")})
                        put("https://skills.example/second?key=private-maintenance-ui",buildJsonObject {put("name","second-fixture");put("trust_level","trusted");put("scan_verdict","safe")})
                    })))
                    "/dashboard/api/skills/hub/official"->json(buildJsonObject {put("skills",buildJsonArray {add(SkillHubFixture.entry())})})
                    "/dashboard/api/skills/hub/uninstall"->{assertEquals(buildJsonObject {put("profile","worker");put("name","fixture-slug")},Json.parseToJsonElement(q.body.readUtf8()));writes.incrementAndGet();removed.set(true);json(buildJsonObject {put("ok",true);put("pid",123);put("name",HubInstallRequest.actionName("fixture-slug","uninstall"))})}
                    "/dashboard/api/skills/hub/update"->{assertEquals(buildJsonObject {put("profile","worker")},Json.parseToJsonElement(q.body.readUtf8()));writes.incrementAndGet();json(buildJsonObject {put("ok",true);put("pid",124);put("name","skills-update")})}
                    "/dashboard/api/skills"->{skillReads.incrementAndGet();MockResponse().setHeader("Content-Type","application/json").setBody("[]")}
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }}
            open(server);compose.onNodeWithTag("skill-hub-list").performScrollToNode(hasTestTag("hub-maintenance-open"));compose.onNodeWithTag("hub-maintenance-open").performClick()
            compose.onNodeWithTag("hub-maintenance-list").performScrollToNode(hasTestTag("hub-remove-fixture-slug"));compose.onNodeWithTag("hub-remove-fixture-slug").performClick();assertEquals(0,writes.get())
            compose.onNodeWithTag("hub-maintenance-review").performScrollToNode(hasText(Words.SkillHubRemoveEffect.en));compose.onNodeWithText(Words.SkillHubRemoveEffect.en).assertExists();compose.onNodeWithTag("hub-maintenance-confirm").assertIsDisplayed()
            compose.onNodeWithTag("hub-maintenance-review-cancel").performClick();assertEquals(0,writes.get());compose.onNodeWithTag("hub-remove-fixture-slug").performClick();compose.onNodeWithTag("hub-maintenance-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="HubRemove" && it.state=="acknowledged"}};assertEquals(1,writes.get())
            compose.onNodeWithTag("hub-maintenance-list").performScrollToNode(hasTestTag("hub-maintenance-refresh"));compose.onNodeWithTag("hub-maintenance-refresh").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hub.loaded}
            compose.onNodeWithTag("hub-maintenance-list").performScrollToNode(hasTestTag("hub-update-all-review"));compose.onNodeWithTag("hub-update-all-review").performClick();assertEquals(1,writes.get())
            compose.onNodeWithTag("hub-maintenance-review").performScrollToNode(hasText(Words.SkillHubUpdatePolicy.en));compose.onNodeWithText(Words.SkillHubUpdatePolicy.en).assertExists();compose.onNodeWithTag("hub-maintenance-confirm").assertIsDisplayed();compose.onNodeWithText("private-maintenance-ui",substring=true).assertDoesNotExist()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/skill-hub-update-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("hub-maintenance-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="HubUpdateAll" && it.state=="acknowledged"}};assertEquals(2,writes.get())
            val receipt=management.receipts.value.single {it.action=="HubUpdateAll"};assertFalse(receipt.toString().contains("private-maintenance-ui"))
            compose.onNodeWithTag("hub-maintenance-close").performClick();compose.onNodeWithTag("skill-hub-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.skills.loaded};assertEquals(1,skillReads.get());assertEquals(2,writes.get());assertEquals("acknowledged",management.receipts.value.single {it.key==receipt.key}.state);compose.onNodeWithTag("host-skills-close").performClick()
        }
    }
}
