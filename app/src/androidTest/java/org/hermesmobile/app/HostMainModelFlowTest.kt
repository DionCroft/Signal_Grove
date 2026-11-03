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

class HostMainModelFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String="normal") {
        val sends=AtomicInteger();val reads=AtomicInteger();val options=AtomicInteger();val mcp=AtomicInteger();val other=AtomicInteger();val model=AtomicReference(HostMainModelFixture.old);val provider=AtomicReference(HostMainModelFixture.provider);val expectedModel=if(mode=="normalized") "normalized-model" else HostMainModelFixture.next;val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val url=q.requestUrl!!
                if(url.encodedPath.startsWith("/dashboard/")) assertEquals("Bearer main-ui-fixture",q.getHeader("Authorization"))
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    q.path=="/dashboard/api/mcp/servers?profile=worker"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    url.encodedPath=="/dashboard/api/model/auxiliary"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));reads.incrementAndGet();json(HostMainModelFixture.auxiliary(model.get(),provider.get()))}
                    url.encodedPath=="/dashboard/api/model/options"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));val n=options.incrementAndGet();json(HostMainModelFixture.catalog(model.get(),provider.get(),changed=mode=="changed" && n>1))}
                    url.encodedPath=="/dashboard/api/model/set"->{assertEquals("POST",q.method);assertEquals("worker",url.queryParameter("profile"));val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(setOf("scope","profile","provider","model","confirm_expensive_model"),body.keys);assertEquals(JsonPrimitive("main"),body["scope"]);assertEquals(JsonPrimitive("worker"),body["profile"]);assertEquals(JsonPrimitive(HostMainModelFixture.provider),body["provider"]);assertEquals(JsonPrimitive(HostMainModelFixture.next),body["model"]);val n=sends.incrementAndGet();assertEquals(JsonPrimitive(mode=="warning" && n==3),body["confirm_expensive_model"])
                        if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()}
                        if(mode=="warning" && n<3) json(HostMainModelFixture.warning()) else {model.set(expectedModel);if(mode=="normalized") provider.set("normalized-provider");json(HostMainModelFixture.saved(model.get(),provider.get()))}
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,"main-inference-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("main-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-models-open"));compose.onNodeWithTag("host-models-open").performClick();compose.onNodeWithTag("host-models-read").performClick();compose.onNodeWithTag("host-models-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostModels!=null};val mcpBefore=mcp.get()
                fun choose() {
                    compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-models-provider-fixture"));compose.onNodeWithTag("host-models-provider-fixture").performClick();compose.onNodeWithTag("host-models-catalog").performScrollToNode(hasTestTag("host-main-pick-fixture-blocked-model"));compose.onNodeWithTag("host-main-pick-fixture-blocked-model").assertIsNotEnabled();compose.onNodeWithTag("host-models-catalog").performScrollToNode(hasTestTag("host-main-pick-fixture-new-model"));compose.onNodeWithTag("host-main-pick-fixture-new-model").performClick();compose.onNodeWithTag("host-main-confirm").assertIsNotEnabled()
                }
                fun approve() {compose.onNodeWithTag("host-main-review").performScrollToNode(hasTestTag("host-main-approve"));compose.onNodeWithTag("host-main-approve").assertIsOff().performClick()}
                choose();approve();compose.onNodeWithTag("host-main-cancel").performClick();assertEquals(0,sends.get());choose();approve();compose.onNodeWithTag("host-main-confirm").performClick()
                if(mode=="held") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first()}.any {it.action=="MainModel" && it.state=="unknown"}};assertNull(management.state.value.hostModels);assertNull(management.state.value.hostMainModel.warning);assertEquals(1,sends.get());return@use}
                if(mode=="changed") {compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertEquals(0,sends.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first()}.none {it.action=="MainModel"});return@use}
                if(mode=="warning") {
                    compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostMainModel.warning!=null};assertEquals(1,sends.get());assertEquals(HostMainModelFixture.old,model.get());compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-main-warning-review"));compose.onNodeWithTag("host-main-warning-review").performClick();compose.onNodeWithTag("host-main-review").performScrollToNode(hasTestTag("host-main-warning-message"));compose.onNodeWithText(HostMainModelFixture.warningText).assertExists();approve();compose.onNodeWithTag("host-main-cancel").performClick();assertNull(management.state.value.hostMainModel.warning);assertEquals(1,sends.get())
                    choose();approve();compose.onNodeWithTag("host-main-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostMainModel.warning!=null};assertEquals(2,sends.get());compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-main-warning-review"));compose.onNodeWithTag("host-main-warning-review").performClick();compose.onNodeWithTag("host-main-confirm").assertIsNotEnabled();approve()
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/host-main-warning-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)};compose.onNodeWithTag("host-main-confirm").performClick()
                }
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostModels?.main?.model==expectedModel && management.receipts.value.any {it.action=="MainModel" && it.state=="acknowledged"}};assertEquals(if(mode=="warning") 3 else 1,sends.get());val receipts=runBlocking {app.database.dao().managementOperations(scope).first()}.filter {it.action=="MainModel"};assertEquals(if(mode=="warning") 3 else 1,receipts.size);assertTrue(receipts.all {it.state=="acknowledged"});assertTrue(receipts.none {it.toString().contains(HostMainModelFixture.warningText) || it.toString().contains("private-") || it.toString().contains("Fixture post-assignment notice")})
                if(mode=="normalized") {assertEquals(Words.HostMainNormalized,management.state.value.notice);compose.onNodeWithTag("host-models-list").performScrollToNode(hasText(Words.HostMainNormalized.en));compose.onNodeWithText(Words.HostMainNormalized.en).assertExists();assertEquals("normalized-provider",management.state.value.hostModels!!.main.provider)}
                compose.onNodeWithTag("host-models-close").performClick();val currentReads=reads.get();val saved=receipts.first {Json.parseToJsonElement(it.resultSummary).jsonObject["reported_assigned"]==JsonPrimitive(true)};compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${saved.key}"));compose.onNodeWithTag("management-observe-${saved.key}").performClick();assertEquals(currentReads,reads.get());compose.onNodeWithTag("host-models-read").performClick();compose.onNodeWithTag("host-models-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostModels!=null};assertEquals(currentReads+1,reads.get());assertEquals(mcpBefore,mcp.get());assertEquals(if(mode=="warning") 3 else 1,sends.get());assertEquals(0,other.get())
            } finally {release.countDown();compose.runOnUiThread {management.cancel()}}
        }
    }
    @Test fun reviewedMainAssignmentRequiresFreshConsentAndObservesWithoutMcpOrReplay()=exercise()
    @Test fun normalizedReportedPairIsCalledOutAndRemainsAcknowledged()=exercise("normalized")
    @Test fun hostPriceAndDataTermsRequireAnotherExplicitReviewWithNoCarriedApproval()=exercise("warning")
    @Test fun changedCatalogRefusesBeforeClaimOrMutation()=exercise("changed")
    @Test fun cancelledOldConnectionReplyKeepsOriginalUncertaintyAndCannotRepopulateUi()=exercise("held")
}
