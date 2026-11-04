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

class CustomEndpointProbeFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String) {
        val inferenceToken=credentials.uniqueToken("endpoint-probe-inference-fixture");val sends=AtomicInteger();val saves=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        credentials.managementServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val u=q.requestUrl!!;if(u.encodedPath.startsWith("/dashboard/")) assertEquals("Bearer endpoint-probe-ui-fixture",q.getHeader("Authorization"))
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    u.encodedPath=="/dashboard/api/mcp/servers"->json(buildJsonObject {put("servers",JsonArray(emptyList()))})
                    u.encodedPath=="/dashboard/api/config"->{assertEquals("GET",q.method);assertEquals("worker",u.queryParameter("profile"));json(CustomEndpointFixture.config())}
                    u.encodedPath=="/dashboard/api/providers/custom-endpoints" && q.method=="GET"->json(CustomEndpointFixture.metadata())
                    u.encodedPath=="/dashboard/api/providers/custom-endpoints/validate"->{
                        sends.incrementAndGet();assertNull(u.query);val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(CustomEndpointFixture.url,body.text("base_url"));assertEquals(CustomEndpointFixture.model,body.text("model"));assertEquals("",body.text("api_mode"));assertFalse(body.containsKey("profile"));if(mode=="fresh") assertEquals(CustomEndpointFixture.fresh,body.text("api_key")) else assertFalse(body.containsKey("api_key"));assertFalse(body.toString().contains(CustomEndpointFixture.inline));assertFalse(body.toString().contains(CustomEndpointFixture.header))
                        if(mode=="late") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()}
                        if(mode=="unknown") MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                        else json(buildJsonObject {put("ok",mode!="negative");put("reachable",true);put("models",if(mode=="optional") JsonPrimitive(17) else JsonArray(listOf(JsonPrimitive("private-probed-model"))));put("message","private-probe-message");put("resolved_base_url","https://private-resolved.test/v1");put("model_details",buildJsonArray {add(buildJsonObject {put("id","private-probed-model");put("canonical_model","private-canonical");put("reasoning_effort","high")})})})
                    }
                    u.encodedPath.startsWith("/dashboard/api/providers/custom-endpoints")->{saves.incrementAndGet();MockResponse().setResponseCode(500)}
                    else->MockResponse().setResponseCode(404)
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,inferenceToken,true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("endpoint-probe-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("endpoint-open"));compose.onNodeWithTag("endpoint-open").performClick();compose.onNodeWithTag("endpoint-list").performScrollToNode(hasTestTag("endpoint-read"));compose.onNodeWithTag("endpoint-read").performClick();compose.onNodeWithTag("endpoint-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.customEndpoints!=null}
                compose.onNodeWithTag("endpoint-list").performScrollToNode(hasTestTag("endpoint-edit-${CustomEndpointFixture.id}"));compose.onNodeWithTag("endpoint-edit-${CustomEndpointFixture.id}").performClick();compose.onNodeWithTag("endpoint-editor-reveal").performClick()
                if(mode=="fresh") {compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-key-Fresh"));compose.onNodeWithTag("endpoint-key-Fresh").performClick();compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-secret"));compose.onNodeWithTag("endpoint-secret").performTextReplacement(CustomEndpointFixture.fresh);closeDialogKeyboard()}
                fun open() {compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-probe-open"));compose.onNodeWithTag("endpoint-probe-open").performClick();compose.onNodeWithTag("endpoint-probe-confirm").assertIsNotEnabled();compose.onNodeWithTag("endpoint-probe-review").performScrollToNode(hasTestTag("endpoint-probe-approve"));compose.onNodeWithTag("endpoint-probe-approve").assertIsOff().performClick();compose.onNodeWithTag("endpoint-probe-confirm").assertIsNotEnabled();compose.onNodeWithTag("endpoint-probe-review").performScrollToNode(hasTestTag("endpoint-probe-reveal"));compose.onNodeWithTag("endpoint-probe-reveal").performClick();compose.onNodeWithText(CustomEndpointFixture.inline,substring=true).assertDoesNotExist();compose.onNodeWithTag("endpoint-probe-review").performScrollToNode(hasTestTag("endpoint-probe-approve"));compose.onNodeWithTag("endpoint-probe-confirm").assertIsEnabled()}
                open();compose.onNodeWithTag("endpoint-probe-review-cancel").performClick();assertEquals(0,sends.get());open()
                val shot=File(compose.activity.getExternalFilesDir(null),"qa/endpoint-probe-$mode-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                compose.onNodeWithTag("endpoint-probe-confirm").performClick()
                if(mode=="late") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.customEndpointProbe);assertNull(management.state.value.customEndpoints);assertEquals(0,saves.get());return@use}
                compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="CustomEndpointProbe" && it.state!="sending"}}
                val receipt=management.receipts.value.single {it.action=="CustomEndpointProbe"};assertEquals(1,sends.get());assertEquals(0,saves.get());assertEquals(if(mode=="unknown") "unknown" else "acknowledged",receipt.state)
                for(value in listOf(CustomEndpointFixture.fresh,CustomEndpointFixture.url,CustomEndpointFixture.model,"private-probed","private-canonical","private-probe-message","private-resolved")) {assertFalse(receipt.requestSummary.contains(value));assertFalse(receipt.resultSummary.contains(value))}
                if(mode=="unknown") {assertNull(management.state.value.customEndpointProbe);compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-probe-open"));compose.onNodeWithTag("endpoint-probe-open").assertIsNotEnabled();return@use}
                val report=management.state.value.customEndpointProbe!!;assertEquals(mode!="negative",report.ack.ok);assertEquals(mode=="optional",report.details==null)
                compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-probe-report-open"));compose.onNodeWithTag("endpoint-probe-report-open").performClick();compose.onNodeWithText("private-probe-message").assertDoesNotExist();compose.onNodeWithTag("endpoint-probe-report").performScrollToNode(hasTestTag("endpoint-probe-report-reveal"));compose.onNodeWithTag("endpoint-probe-report-reveal").performClick()
                if(mode=="optional") compose.onNodeWithTag("endpoint-probe-report").performScrollToNode(hasText(Words.EndpointProbeDetailsUnavailable.en)) else {compose.onNodeWithTag("endpoint-probe-report").performScrollToNode(hasText("private-probe-message"));compose.onNodeWithText("private-probe-message").assertIsDisplayed()}
                compose.onNodeWithTag("endpoint-probe-report-close").performClick();compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-model"));compose.onNodeWithTag("endpoint-model").performTextReplacement("changed-model");closeDialogKeyboard();compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-probe-open"));compose.onNodeWithTag("endpoint-probe-report-open").assertDoesNotExist();compose.onNodeWithTag("endpoint-editor-cancel").performClick();assertNull(management.state.value.customEndpointProbe);assertEquals(1,sends.get());assertEquals(0,saves.get())
            } finally {release.countDown();compose.runOnUiThread {management.cancel()}}
        }
    }
    @Test fun keptCredentialNeedsFreshOutboundConsentAndPrivateReportReveal()=exercise("keep")
    @Test fun freshCredentialReviewShowsOnlyTheExplicitNewKey()=exercise("fresh")
    @Test fun negativeReportDoesNotBecomeAnUnknownOrPreventFutureEditing()=exercise("negative")
    @Test fun malformedOptionalDetailsRetainAcknowledgedStatusFlags()=exercise("optional")
    @Test fun lostReplyBlocksAnotherProbeWithoutReplayingOrSaving()=exercise("unknown")
    @Test fun lateOldConnectionReportCannotRepopulateNewConnection()=exercise("late")
}
