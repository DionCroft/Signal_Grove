package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
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

class HostMoaFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String) {
        val puts=AtomicInteger();val reads=AtomicInteger();val modelReads=AtomicInteger();val mcp=AtomicInteger();val other=AtomicInteger();val current=AtomicReference(HostMoaFixture.initial(bespoke=mode=="bespoke"));val submitted=AtomicReference<JsonObject>();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val url=q.requestUrl!!;if(url.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer moa-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    q.path=="/dashboard/api/mcp/servers?profile=worker"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    url.encodedPath=="/dashboard/api/model/auxiliary"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));modelReads.incrementAndGet();json(HostMainModelFixture.auxiliary())}
                    url.encodedPath=="/dashboard/api/model/options"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));assertEquals("false",url.queryParameter("refresh"));json(HostMoaFixture.catalog())}
                    url.encodedPath=="/dashboard/api/model/moa"->{assertEquals("worker",url.queryParameter("profile"));if(q.method=="GET") {val n=reads.incrementAndGet();json(if(mode=="changed" && n>1) HostMoaFixture.initial(changed=true) else current.get())} else {
                        assertEquals("PUT",q.method);val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(setOf("profile","default_preset","active_preset","presets"),body.keys);assertEquals(JsonPrimitive("worker"),body["profile"]);assertFalse(body.toString().contains("private"));submitted.set(body);puts.incrementAndGet()
                        if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()}
                        current.set(HostMoaFixture.after(body,mode=="normalized"));json(JsonObject(current.get()+mapOf("ok" to JsonPrimitive(true))))
                    }}
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("moa-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("moa-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-models-open"));compose.onNodeWithTag("host-models-open").performClick();compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-moa-open"));compose.onNodeWithTag("host-moa-open").performClick();assertEquals(0,reads.get());assertEquals(0,modelReads.get())
                compose.onNodeWithTag("host-moa-read").performClick();compose.onNodeWithTag("host-moa-read-cancel").performClick();assertEquals(0,reads.get());compose.onNodeWithTag("host-moa-read").performClick();compose.onNodeWithTag("host-moa-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.moa!=null};assertEquals(1,reads.get());val mcpBefore=mcp.get()
                fun scroll(tag:String)=compose.onNodeWithTag("host-moa-list").performScrollToNode(hasTestTag(tag))
                fun editor(tag:String)=compose.onNodeWithTag("host-moa-editor").performScrollToNode(hasTestTag(tag))
                fun approve() {compose.onNodeWithTag("host-moa-review").performScrollToNode(hasTestTag("host-moa-approve"));compose.onNodeWithTag("host-moa-approve").assertIsOff().performClick();compose.onNodeWithText(Words.HostMoaApprove.en).performScrollTo().assertIsDisplayed()}
                if(mode=="bespoke") {compose.onNodeWithTag("host-moa-review-open").assertIsNotEnabled();scroll("host-moa-edit");compose.onNodeWithTag("host-moa-edit").assertIsNotEnabled();compose.onNodeWithTag("host-moa-list").performScrollToNode(hasText(Words.HostMoaUnrepresentable.en));compose.onNodeWithText(Words.HostMoaUnrepresentable.en).assertIsDisplayed();assertEquals(0,puts.get());return@use}
                if(mode=="remove") {scroll("host-moa-select-archive");compose.onNodeWithTag("host-moa-select-archive").performClick();scroll("host-moa-remove");compose.onNodeWithTag("host-moa-remove").performClick();assertEquals(0,puts.get())}
                else if(mode=="edit" || mode=="limits") {
                    scroll("host-moa-edit");compose.onNodeWithTag("host-moa-edit").performClick();editor("host-moa-aggregator-edit");compose.onNodeWithTag("host-moa-aggregator-edit").performClick();compose.onNodeWithTag("host-moa-effort-default").assertIsNotEnabled();compose.onNodeWithText(Words.HostMoaAggClearUnsupported.en).assertExists();compose.onNodeWithTag("host-moa-slot-cancel").performClick()
                    if(mode=="limits") {editor("host-moa-timeout");compose.onNodeWithTag("host-moa-timeout").performTextReplacement("0");compose.onNodeWithTag("host-moa-editor-apply").assertIsNotEnabled();compose.onNodeWithTag("host-moa-timeout").performTextReplacement("NaN");compose.onNodeWithTag("host-moa-editor-apply").assertIsNotEnabled();compose.onNodeWithTag("host-moa-editor-cancel").performClick();assertEquals(0,puts.get());return@use}
                    editor("host-moa-ref-copy-0");compose.onNodeWithTag("host-moa-ref-copy-0").performClick();editor("host-moa-ref-down-0");compose.onNodeWithTag("host-moa-ref-down-0").performClick();editor("host-moa-ref-enabled-1");compose.onNodeWithTag("host-moa-ref-enabled-1").performClick();editor("host-moa-ref-remove-0");compose.onNodeWithTag("host-moa-ref-remove-0").performClick()
                    editor("host-moa-add-reference");compose.onNodeWithTag("host-moa-add-reference").performClick();compose.onNodeWithTag("host-moa-provider-moa").performClick();compose.onNodeWithTag("host-moa-model-moa-main").assertIsNotEnabled();compose.onNodeWithText(Words.HostMoaRecursive.en).assertExists();compose.onNodeWithText(Words.HostModelsProviders.en).performClick();compose.onNodeWithTag("host-moa-provider-fixture").performClick();compose.onNodeWithTag("host-moa-catalog").performScrollToNode(hasTestTag("host-moa-model-fixture-blocked-model"));compose.onNodeWithTag("host-moa-model-fixture-blocked-model").assertIsNotEnabled();compose.onNodeWithTag("host-moa-catalog").performScrollToNode(hasTestTag("host-moa-model-fixture-new-model"));compose.onNodeWithTag("host-moa-model-fixture-new-model").performClick();compose.onNodeWithTag("host-moa-slot").performScrollToNode(hasTestTag("host-moa-effort-High"));compose.onNodeWithTag("host-moa-effort-High").performClick();compose.onNodeWithTag("host-moa-slot-apply").performClick();editor("host-moa-ref-up-1");compose.onNodeWithTag("host-moa-ref-up-1").performClick()
                    editor("host-moa-reference-temperature");compose.onNodeWithTag("host-moa-reference-temperature").performTextReplacement("0.7");editor("host-moa-aggregator-temperature");compose.onNodeWithTag("host-moa-aggregator-temperature").performTextReplacement("0.2");editor("host-moa-timeout");compose.onNodeWithTag("host-moa-timeout").performTextReplacement("1200");editor("host-moa-cadence-every_n");compose.onNodeWithTag("host-moa-cadence-every_n").performClick();editor("host-moa-n");compose.onNodeWithTag("host-moa-n").performTextReplacement("3");Espresso.closeSoftKeyboard();compose.waitForIdle();editor("host-moa-policy-silent");compose.onNodeWithTag("host-moa-policy-silent").performClick();compose.onNodeWithTag("host-moa-editor-apply").performClick()
                } else if(mode=="duplicate") {
                    scroll("host-moa-new-name");compose.onNodeWithTag("host-moa-new-name").performTextInput("mobile");Espresso.closeSoftKeyboard();compose.waitForIdle();scroll("host-moa-duplicate");compose.onNodeWithTag("host-moa-duplicate").assertIsEnabled().performClick();scroll("host-moa-select-mobile");compose.onNodeWithTag("host-moa-select-mobile").assertIsDisplayed();scroll("host-moa-default");compose.onNodeWithTag("host-moa-default").performClick();scroll("host-moa-active");compose.onNodeWithTag("host-moa-active").performClick()
                } else {scroll("host-moa-inactive");compose.onNodeWithTag("host-moa-inactive").performClick()}
                compose.onNodeWithTag("host-moa-review-open").performClick();compose.onNodeWithTag("host-moa-confirm").assertIsNotEnabled();approve();compose.onNodeWithTag("host-moa-review-cancel").performClick();assertEquals(0,puts.get());compose.onNodeWithTag("host-moa-review-open").performClick();compose.onNodeWithTag("host-moa-confirm").assertIsNotEnabled();approve()
                if(mode in setOf("duplicate","remove","edit")) {val shot=File(compose.activity.getExternalFilesDir(null),"qa/host-moa-$mode-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
                compose.onNodeWithTag("host-moa-confirm").performClick()
                if(mode=="held") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first()}.any {it.action=="MoaConfig" && it.state=="unknown"}};assertNull(management.state.value.moa);assertEquals(1,puts.get());return@use}
                if(mode=="changed") {compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertEquals(0,puts.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first()}.none {it.action=="MoaConfig"});return@use}
                compose.waitUntil(15000) {!management.state.value.busy && management.state.value.moa!=null && management.receipts.value.any {it.action=="MoaConfig" && it.state=="acknowledged"}};assertEquals(1,puts.get());val body=submitted.get();val presets=body["presets"]!!.jsonObject
                if(mode=="duplicate") {assertEquals(setOf("main","archive","mobile"),presets.keys);assertEquals(JsonPrimitive("mobile"),body["default_preset"]);assertEquals(JsonPrimitive("mobile"),body["active_preset"])}
                if(mode=="remove") {assertEquals(setOf("main"),presets.keys);assertEquals(JsonPrimitive(""),body["active_preset"])}
                if(mode=="edit") {val p=presets["main"]!!.jsonObject;assertEquals(JsonPrimitive("every_n:3"),p["fanout"]);assertEquals(JsonPrimitive("silent"),p["degraded_reference_policy"]);assertEquals(1200.0,p["reference_timeout"]!!.jsonPrimitive.double,0.0);assertEquals(2,p["reference_models"]!!.jsonArray.size);assertEquals(JsonPrimitive(false),p["reference_models"]!!.jsonArray[1].jsonObject["enabled"]);assertEquals(JsonPrimitive("high"),p["reference_models"]!!.jsonArray[0].jsonObject["reasoning_effort"]);assertEquals(JsonPrimitive(HostMainModelFixture.next),p["reference_models"]!!.jsonArray[0].jsonObject["model"]);assertEquals(JsonPrimitive(HostMainModelFixture.old),p["reference_models"]!!.jsonArray[1].jsonObject["model"])}
                if(mode=="normalized") {assertEquals(Words.HostMoaNormalized,management.state.value.notice);assertEquals("archive",management.state.value.moa!!.config.activePreset)}
                val receipt=runBlocking {app.database.dao().managementOperations(scope).first()}.single {it.action=="MoaConfig"};assertFalse(receipt.toString().contains("private"));compose.onNodeWithTag("host-moa-close").performClick();compose.onNodeWithTag("host-models-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-result-${receipt.key}"));compose.onNodeWithTag("management-result-${receipt.key}").performClick();compose.onNodeWithText(Words.HostMoaReceipt.en).assertExists();if(mode=="normalized") {compose.onNodeWithText(Words.HostMainRequested.en).assertExists();compose.onNodeWithText(Words.HostMainReported.en).assertExists();compose.onNodeWithText(Words.HostMoaActive.en+" · archive").assertExists()};compose.onNodeWithTag("management-result-close").performClick();val oldReads=reads.get();val oldModels=modelReads.get();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(oldReads,reads.get());assertEquals(oldModels,modelReads.get());compose.onNodeWithTag("host-moa-read").performClick();compose.onNodeWithTag("host-moa-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.moa!=null};assertEquals(oldReads+1,reads.get());assertEquals(mcpBefore,mcp.get());assertEquals(1,puts.get());assertEquals(0,other.get())
            } finally {release.countDown();compose.runOnUiThread {management.cancel()}}
        }
    }
    @Test fun duplicateSelectorsAndFreshFullMapConsentAreSeparateFromSaveAndObservation()=exercise("duplicate")
    @Test fun removedNamesAndClearedActiveSelectorAreExplicitInTheReviewedFullMap()=exercise("remove")
    @Test fun referencesModelsOrderingReasoningCadenceAndNumbersRoundTripThroughOneReviewedSave()=exercise("edit")
    @Test fun impossibleAggregatorClearAndInvalidTimeoutCannotBecomeFakeSaveControls()=exercise("limits")
    @Test fun bespokeReasoningRemainsVisibleAndCannotBeSilentlyDropped()=exercise("bespoke")
    @Test fun normalizedHostMapRemainsAcknowledgedAndIsCalledOut()=exercise("normalized")
    @Test fun concurrentVisiblePresetEditRefusesBeforeClaimAndPut()=exercise("changed")
    @Test fun lateOldConnectionSaveCannotRepopulateTheNewConnection()=exercise("held")
}
