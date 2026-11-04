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

class HostAuxModelFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String="normal") {
        val sends=AtomicInteger();val reads=AtomicInteger();val options=AtomicInteger();val mcp=AtomicInteger();val other=AtomicInteger();val model=AtomicReference(HostMainModelFixture.old);val provider=AtomicReference(HostMainModelFixture.provider);val expectedModel=if(mode in setOf("reset","auto")) "" else HostMainModelFixture.next;val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        fun aux():JsonObject {
            val raw=HostAuxModelFixture.auxiliary(model.get(),if(mode=="inherit" && sends.get()>0) null else "low",reset=mode=="reset" && sends.get()>0)
            return when {
                mode=="bulk" && sends.get()>0->JsonObject(raw+mapOf("tasks" to JsonArray(raw["tasks"]!!.jsonArray.map {JsonObject(it.jsonObject+mapOf("provider" to JsonPrimitive(HostMainModelFixture.provider),"model" to JsonPrimitive(HostMainModelFixture.next)))})))
                mode=="auto" && sends.get()>0->JsonObject(raw+mapOf("tasks" to JsonArray(raw["tasks"]!!.jsonArray.map {if(it.jsonObject["task"]==JsonPrimitive(HostAuxModelFixture.task)) JsonObject(it.jsonObject+mapOf("provider" to JsonPrimitive("auto"),"model" to JsonPrimitive(""),"base_url" to JsonPrimitive(""),"reasoning_effort" to JsonPrimitive("none"))) else it})))
                else->raw
            }
        }
        MockWebServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val url=q.requestUrl!!
                if(url.encodedPath.startsWith("/dashboard/")) assertEquals("Bearer aux-ui-fixture",q.getHeader("Authorization"))
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    q.path=="/dashboard/api/mcp/servers?profile=worker"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    url.encodedPath=="/dashboard/api/model/auxiliary"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));reads.incrementAndGet();json(aux())}
                    url.encodedPath=="/dashboard/api/model/options"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));val n=options.incrementAndGet();json(HostMainModelFixture.catalog(changed=mode=="changed" && n>1))}

                    url.encodedPath=="/dashboard/api/model/set"->{
                        assertEquals("POST",q.method);assertEquals("worker",url.queryParameter("profile"));val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                        assertEquals(setOf("scope","profile","task","provider","model","confirm_expensive_model")+if(mode in setOf("inherit","auto")) setOf("reasoning_effort") else emptySet(),body.keys);assertEquals(JsonPrimitive("auxiliary"),body["scope"]);assertEquals(JsonPrimitive("worker"),body["profile"])
                        if(mode=="reset") {assertEquals(JsonPrimitive("__reset__"),body["task"]);assertEquals(JsonPrimitive(""),body["provider"]);assertEquals(JsonPrimitive(""),body["model"])} else {assertEquals(JsonPrimitive(if(mode=="bulk") "" else HostAuxModelFixture.task),body["task"]);assertEquals(JsonPrimitive(if(mode=="auto") "auto" else HostMainModelFixture.provider),body["provider"]);assertEquals(JsonPrimitive(expectedModel),body["model"])}
                        if(mode=="inherit") assertEquals(JsonNull,body["reasoning_effort"])
                        if(mode=="auto") assertEquals(JsonPrimitive("none"),body["reasoning_effort"])
                        val n=sends.incrementAndGet();assertEquals(JsonPrimitive(mode=="warning" && n==3),body["confirm_expensive_model"])
                        if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()}
                        if(mode=="warning" && n<3) json(HostAuxModelFixture.warning()) else {model.set(expectedModel);if(mode=="reset") json(buildJsonObject {put("ok",true);put("scope","auxiliary");put("reset",true)}) else if(mode=="auto") json(JsonObject(HostAuxModelFixture.saved(effort=JsonPrimitive("none"))+mapOf("provider" to JsonPrimitive("auto"),"model" to JsonPrimitive("")))) else json(HostAuxModelFixture.saved(tasks=if(mode=="bulk") listOf(HostAuxModelFixture.task,"voice_chat","plugin.task") else listOf(HostAuxModelFixture.task),effort=if(mode=="inherit") JsonNull else null))}
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("aux-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("aux-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-models-open"));compose.onNodeWithTag("host-models-open").performClick();compose.onNodeWithTag("host-models-read").performClick();compose.onNodeWithTag("host-models-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostModels!=null};val mcpBefore=mcp.get()

                fun choose() {
                    if(mode=="reset") {compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-aux-reset"));compose.onNodeWithTag("host-aux-reset").performClick()}
                    else {
                        val tag=if(mode=="bulk") "host-aux-all" else "host-aux-edit-title_generation"
                        compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()
                        if(mode=="auto") {compose.onNodeWithTag("host-aux-editor").performScrollToNode(hasTestTag("host-aux-auto"));compose.onNodeWithTag("host-aux-auto").performClick();compose.onNodeWithTag("host-aux-editor").performScrollToNode(hasTestTag("host-aux-effort-None"));compose.onNodeWithTag("host-aux-effort-None").performClick()}
                        else {compose.onNodeWithTag("host-aux-editor").performScrollToNode(hasTestTag("host-aux-pick"));compose.onNodeWithTag("host-aux-pick").performClick();compose.onNodeWithTag("host-aux-provider-fixture").performClick();compose.onNodeWithTag("host-aux-catalog").performScrollToNode(hasTestTag("host-aux-model-fixture-blocked-model"));compose.onNodeWithTag("host-aux-model-fixture-blocked-model").assertIsNotEnabled();compose.onNodeWithTag("host-aux-catalog").performScrollToNode(hasTestTag("host-aux-model-fixture-new-model"));compose.onNodeWithTag("host-aux-model-fixture-new-model").performClick()}
                        if(mode=="inherit") {compose.onNodeWithTag("host-aux-editor").performScrollToNode(hasTestTag("host-aux-effort-Inherit"));compose.onNodeWithTag("host-aux-effort-Inherit").performClick()}
                        compose.onNodeWithTag("host-aux-editor-review").performClick()
                    }
                    compose.onNodeWithTag("host-aux-confirm").assertIsNotEnabled()
                }
                fun approve() {compose.onNodeWithTag("host-aux-review").performScrollToNode(hasTestTag("host-aux-approve"));compose.onNodeWithTag("host-aux-approve").assertIsOff().performClick();compose.onNodeWithText(if(management.state.value.hostAuxModel.warning!=null) Words.HostMainWarningApprove.en else Words.HostAuxApprove.en).performScrollTo().assertIsDisplayed()}
                choose();approve();compose.onNodeWithTag("host-aux-cancel").performClick();assertEquals(0,sends.get());choose();approve()
                if(mode in setOf("inherit","reset","bulk","auto")) {val shot=File(compose.activity.getExternalFilesDir(null),"qa/host-aux-$mode-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
                compose.onNodeWithTag("host-aux-confirm").performClick()
                if(mode=="held") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first()}.any {it.action=="AuxModel" && it.state=="unknown"}};assertNull(management.state.value.hostModels);assertNull(management.state.value.hostAuxModel.warning);assertEquals(1,sends.get());return@use}
                if(mode=="changed") {compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertEquals(0,sends.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first()}.none {it.action=="AuxModel"});return@use}
                if(mode=="warning") {
                    compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostAuxModel.warning!=null};assertEquals(1,sends.get());assertEquals(HostMainModelFixture.old,model.get());compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-aux-warning-review"));compose.onNodeWithTag("host-aux-warning-review").performClick();compose.onNodeWithTag("host-aux-review").performScrollToNode(hasTestTag("host-aux-warning-message"));compose.onNodeWithText(HostMainModelFixture.warningText).assertExists();approve();compose.onNodeWithTag("host-aux-cancel").performClick();assertNull(management.state.value.hostAuxModel.warning);assertEquals(1,sends.get())
                    choose();approve();compose.onNodeWithTag("host-aux-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostAuxModel.warning!=null};assertEquals(2,sends.get());compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-aux-warning-review"));compose.onNodeWithTag("host-aux-warning-review").performClick();compose.onNodeWithTag("host-aux-confirm").assertIsNotEnabled();approve()
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/host-aux-warning-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)};compose.onNodeWithTag("host-aux-confirm").performClick()
                }
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostModels?.auxiliary?.singleOrNull {it.task==HostAuxModelFixture.task}?.route?.model==expectedModel && management.receipts.value.any {it.action=="AuxModel" && it.state=="acknowledged"}};assertEquals(if(mode=="warning") 3 else 1,sends.get());val receipts=runBlocking {app.database.dao().managementOperations(scope).first()}.filter {it.action=="AuxModel"};assertEquals(if(mode=="warning") 3 else 1,receipts.size);assertTrue(receipts.all {it.state=="acknowledged"});assertTrue(receipts.none {it.toString().contains(HostMainModelFixture.warningText) || it.toString().contains("private-") || it.toString().contains("Fixture post-assignment notice")})
                if(mode=="bulk") assertTrue(management.state.value.hostModels!!.auxiliary.all {it.route.provider==HostMainModelFixture.provider && it.route.model==HostMainModelFixture.next})
                compose.onNodeWithTag("host-models-close").performClick()
                val resultReceipt=receipts.last()
                compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-result-${resultReceipt.key}"));compose.onNodeWithTag("management-result-${resultReceipt.key}").performClick();compose.onNodeWithText("__reset__").assertDoesNotExist();compose.onNodeWithText(Words.HostAuxReceipt.en).assertExists();compose.onNodeWithTag("management-result-close").performClick()
                val currentReads=reads.get();val saved=receipts.first {Json.parseToJsonElement(it.resultSummary).jsonObject["reported_assigned"]==JsonPrimitive(true)};compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${saved.key}"));compose.onNodeWithTag("management-observe-${saved.key}").performClick();assertEquals(currentReads,reads.get());compose.onNodeWithTag("host-models-read").performClick();compose.onNodeWithTag("host-models-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostModels!=null};assertEquals(currentReads+1,reads.get());assertEquals(mcpBefore,mcp.get());assertEquals(if(mode=="warning") 3 else 1,sends.get());assertEquals(0,other.get())
            } finally {release.countDown();compose.runOnUiThread {management.cancel()}}
        }
    }
    @Test fun reviewedAuxAssignmentRequiresFreshConsentAndObservesWithoutMcpOrReplay()=exercise()
    @Test fun explicitInheritDoesNotBecomeKeepOrDisabled()=exercise("inherit")
    @Test fun allSlotAssignmentUsesBulkReviewAndReportedTargets()=exercise("bulk")
    @Test fun perSlotAutoRoutingIsSeparateFromGlobalReset()=exercise("auto")
    @Test fun globalResetNeedsDedicatedReviewAndFreshConsent()=exercise("reset")
    @Test fun hostPriceAndDataTermsRequireAnotherExplicitReviewWithNoCarriedApproval()=exercise("warning")
    @Test fun changedCatalogRefusesBeforeClaimOrMutation()=exercise("changed")
    @Test fun cancelledOldConnectionReplyKeepsOriginalUncertaintyAndCannotRepopulateUi()=exercise("held")
}
