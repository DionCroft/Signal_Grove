package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class CustomEndpointAdoptionFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private val alias="private-discovered-alias"
    private val canonical="private-canonical-model"
    private val prior="prior-shared-private"
    private val fresh="fresh-adoption-private"
    private val resolved="https://resolved-private.test/v2"
    private fun editable(value:String)=SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value))
    private fun scroll(list:String,tag:String) {compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()}
    private fun tap(list:String,tag:String) {scroll(list,tag);compose.onNodeWithTag(tag).assertIsEnabled().performClick()}
    private fun shot(name:String) {val file=File(compose.activity.getExternalFilesDir(null),"qa/"+name+".png");file.parentFile!!.mkdirs();file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun exercise(kind:String) {
        val probes=AtomicInteger();val saves=AtomicInteger();val rotate=AtomicBoolean(false);val submitted=AtomicReference<JsonObject>()
        val initial=JsonObject(CustomEndpointFixture.config()+mapOf("agent" to if(kind=="unsupported") JsonPrimitive("unsupported-agent-private") else buildJsonObject {put("reasoning_overrides",buildJsonObject {put(canonical,prior)});put("other","not-imported-private")}))
        val config=AtomicReference(initial);val metadata=AtomicReference(CustomEndpointFixture.metadata())
        credentials.managementServer().use {server->
            fun json(body:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(body.toString())
            fun reasoning(value:String)=buildJsonObject {put("reasoning_overrides",buildJsonObject {put(canonical,value)});put("other","not-imported-private")}
            fun save(body:JsonObject) {
                var next=CustomEndpointFixture.savedConfig(config.get(),body)
                val providers=next["providers"]!!.jsonObject.toMutableMap();val entry=providers[CustomEndpointFixture.id]!!.jsonObject.toMutableMap()
                val models=(entry["models"] as? JsonObject).orEmpty().toMutableMap()
                val details=(body["model_details"] as? JsonArray).orEmpty().map {it.jsonObject}.associateBy {it["id"]!!.jsonPrimitive.content.trim()}
                val selected=body["model"]!!.jsonPrimitive.content
                val posted=(body["models"] as? JsonArray).orEmpty().map {it.jsonPrimitive.content}+details.keys+selected
                for(id in posted) {
                    val row=(models[id] as? JsonObject).orEmpty().toMutableMap()
                    details[id]?.let {detail->for(key in listOf("canonical_model","reasoning_effort")) (detail[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf {it.isNotEmpty()}?.let {row[key]=JsonPrimitive(it)}}
                    models[id]=JsonObject(row)
                }
                val chosen=details[selected];val target=(chosen?.get("canonical_model") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                val effective=target.takeIf {it.isNotEmpty() && it!=selected} ?: selected
                if(effective!=selected) {
                    entry["model"]=JsonPrimitive(effective);models.putIfAbsent(effective,JsonObject(emptyMap()))
                    val effort=(chosen?.get("reasoning_effort") as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()
                    if(effort in setOf("minimal","low","medium","high","xhigh","max","ultra","none","false","disabled") && kind!="managed") next=JsonObject(next+mapOf("agent" to reasoning(effort!!)))
                }
                body["context_length"]?.let {value->entry["context_length"]=value;models[effective]=JsonObject((models[effective] as? JsonObject).orEmpty()+mapOf("context_length" to value))}
                entry["models"]=JsonObject(models);providers[CustomEndpointFixture.id]=JsonObject(entry)
                next=JsonObject(next+mapOf("providers" to JsonObject(providers)));config.set(next)
                val saved=CustomEndpointFixture.saved(metadata.get(),body)
                metadata.set(JsonObject(saved+mapOf("endpoints" to JsonArray(saved["endpoints"]!!.jsonArray.map {value->val row=value.jsonObject;JsonObject(row+mapOf("model" to JsonPrimitive(effective),"models" to JsonArray(models.keys.map(::JsonPrimitive))))}))))
            }
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
                val url=request.requestUrl!!
                if(url.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer endpoint-adoption-fixture",request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"));assertNull(request.getHeader("Origin"))}
                return when {
                    request.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    request.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    request.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    request.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    url.encodedPath=="/dashboard/api/mcp/servers"->json(buildJsonObject {put("servers",JsonArray(emptyList()))})
                    url.encodedPath=="/dashboard/api/config"->{assertEquals("GET",request.method);assertEquals("worker",url.queryParameter("profile"));assertEquals("false",url.queryParameter("include_defaults"));json(if(rotate.get()) JsonObject(config.get()+mapOf("agent" to reasoning("concurrent-private"))) else config.get())}
                    url.encodedPath=="/dashboard/api/providers/custom-endpoints/validate"->{
                        val n=probes.incrementAndGet();assertNull(url.query);val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertFalse(body.containsKey("profile"));assertFalse(body.toString().contains(CustomEndpointFixture.inline));assertFalse(body.toString().contains(CustomEndpointFixture.header))
                        if(kind=="url" && n==2) {assertEquals(resolved,body.text("base_url"));assertFalse(body.containsKey("api_key"))} else if(kind in setOf("url","noop")) assertEquals(fresh,body.text("api_key")) else assertFalse(body.containsKey("api_key"))
                        json(buildJsonObject {put("ok",false);put("reachable",false);put("message","private-discovery-message");put("resolved_base_url",if(kind=="noop") CustomEndpointFixture.url else resolved);put("transport_checked","codex_responses");put("models",buildJsonArray {add(alias);add(canonical);add("other-discovered")});put("model_details",buildJsonArray {add(buildJsonObject {put("id",alias);put("canonical_model",canonical);put("reasoning_effort","HIGH")})})})
                    }
                    url.encodedPath=="/dashboard/api/providers/custom-endpoints" && request.method=="GET"->{assertEquals("worker",url.queryParameter("profile"));json(if(kind=="readback" && saves.get()>0) buildJsonObject {put("endpoints",17)} else metadata.get())}
                    url.encodedPath=="/dashboard/api/providers/custom-endpoints" && request.method=="POST"->{
                        assertEquals("worker",url.queryParameter("profile"));val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertFalse(body.containsKey("profile"));assertFalse(body.containsKey("agent"));assertFalse(body.containsKey("api_key"));assertFalse(body.toString().contains(CustomEndpointFixture.header));assertFalse(body.toString().contains(CustomEndpointFixture.inline))
                        submitted.set(body);saves.incrementAndGet();save(body);json(CustomEndpointFixture.ack(id=CustomEndpointFixture.id))
                    }
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("endpoint-adoption-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese && !api.preferences.value.pinyin}
            val scope=api.profileScope.value
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("endpoint-adoption-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick();tap("management-list","endpoint-open");tap("endpoint-list","endpoint-read");compose.onNodeWithTag("endpoint-read-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.customEndpoints!=null}
            tap("endpoint-list","endpoint-edit-"+CustomEndpointFixture.id);tap("endpoint-editor","endpoint-editor-reveal")
            if(kind in setOf("url","noop")) {tap("endpoint-editor","endpoint-key-Fresh");scroll("endpoint-editor","endpoint-secret");compose.onNodeWithTag("endpoint-secret").performTextReplacement(fresh);closeDialogKeyboard()}
            if(kind=="normal") {tap("endpoint-editor","endpoint-context-set");scroll("endpoint-editor","endpoint-context");compose.onNodeWithTag("endpoint-context").performTextReplacement("8192");closeDialogKeyboard()}
            fun probe() {
                val previous=probes.get();tap("endpoint-editor","endpoint-probe-open");compose.onNodeWithTag("endpoint-probe-confirm").assertIsNotEnabled()
                tap("endpoint-probe-review","endpoint-probe-reveal");tap("endpoint-probe-review","endpoint-probe-approve");compose.onNodeWithTag("endpoint-probe-confirm").assertIsEnabled().performClick()
                compose.waitUntil(10000) {!management.state.value.busy && probes.get()==previous+1 && management.state.value.customEndpointProbe!=null}
            }
            fun report() {tap("endpoint-editor","endpoint-probe-report-open");compose.onNodeWithText("private-discovery-message",substring=true).assertDoesNotExist();tap("endpoint-probe-report","endpoint-probe-report-reveal")}
            probe();assertEquals(0,saves.get());report()
            assertFalse(management.state.value.customEndpointProbe!!.ack.ok)
            if(kind=="stale") {
                compose.runOnUiThread {management.bind("unconfigured-fixture")};compose.waitUntil(5000) {management.state.value.customEndpointProbe==null && management.state.value.customEndpoints==null}
                compose.onNodeWithTag("endpoint-probe-stage-detail-0").assertDoesNotExist();assertEquals(0,saves.get());return@use
            }
            if(kind=="url") {
                tap("endpoint-probe-report","endpoint-probe-stage-url");assertEquals(0,saves.get());scroll("endpoint-editor","endpoint-key-Keep");compose.onNodeWithTag("endpoint-key-Keep").assert(hasText("✓",substring=true));compose.onNodeWithTag("endpoint-secret").assertDoesNotExist()
                scroll("endpoint-editor","endpoint-url");compose.onNodeWithTag("endpoint-url").assert(editable(resolved));probe();assertEquals(2,probes.get());assertEquals(0,saves.get());assertEquals(initial,config.get());return@use
            }
            if(kind=="noop") {
                scroll("endpoint-probe-report","endpoint-probe-stage-url");compose.onNodeWithTag("endpoint-probe-stage-url").assertIsNotEnabled();compose.onNodeWithText(Words.EndpointStageAlready.en).assertExists()
                tap("endpoint-probe-report","endpoint-probe-stage-mode");scroll("endpoint-editor","endpoint-secret");compose.onNodeWithTag("endpoint-secret").assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText,AnnotatedString(fresh)));scroll("endpoint-editor","endpoint-mode-codex_responses");compose.onNodeWithTag("endpoint-mode-codex_responses").assert(hasText("✓",substring=true));assertEquals(0,saves.get());assertEquals(initial,config.get());return@use
            }
            if(kind=="normal") {tap("endpoint-probe-report","endpoint-probe-stage-catalog");assertEquals(0,saves.get());report()}
            tap("endpoint-probe-report","endpoint-probe-stage-detail-0");assertEquals(0,saves.get())
            if(kind=="unsupported") {
                scroll("endpoint-editor","endpoint-metadata-unsupported");compose.onNodeWithTag("endpoint-review-open").assertIsNotEnabled();tap("endpoint-editor","endpoint-metadata-clear");compose.onNodeWithTag("endpoint-review-open").assertIsEnabled();assertEquals(initial,config.get());assertEquals(0,saves.get());return@use
            }
            compose.onNodeWithTag("endpoint-review-open").assertIsEnabled().performClick();compose.onNodeWithTag("endpoint-confirm").assertIsNotEnabled()
            compose.onNodeWithText(prior,substring=true).assertDoesNotExist();tap("endpoint-review","endpoint-review-reveal")
            scroll("endpoint-review","endpoint-alias-effective");compose.onNodeWithTag("endpoint-alias-effective").assert(hasText(alias+" → "+canonical))
            scroll("endpoint-review","endpoint-alias-prior");compose.onNodeWithTag("endpoint-alias-prior").assert(hasText(prior,substring=true))
            scroll("endpoint-review","endpoint-alias-new");compose.onNodeWithTag("endpoint-alias-new").assert(hasText("high"))
            if(kind=="normal") shot("endpoint-alias-global-review")
            tap("endpoint-review","endpoint-approve");compose.onNodeWithTag("endpoint-confirm").assertIsEnabled()
            if(kind=="changed") rotate.set(true)
            compose.onNodeWithTag("endpoint-confirm").performClick()
            if(kind=="changed") {
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged}
                assertEquals(0,saves.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first()}.none {it.action=="CustomEndpointSave"});return@use
            }
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="CustomEndpointSave" && it.state=="acknowledged"}}
            assertEquals(1,saves.get());val body=submitted.get();assertEquals(JsonPrimitive(alias),body["model"]);assertEquals(JsonPrimitive(false),body["make_default"])
            assertEquals(buildJsonObject {put("id",alias);put("canonical_model",canonical);put("reasoning_effort","HIGH")},body["model_details"]!!.jsonArray.single())
            if(kind=="readback") {assertEquals(Words.ManagementReadbackPending,management.state.value.error);assertNull(management.state.value.customEndpoints)}
            else {assertEquals(if(kind=="managed") Words.CustomEndpointsDifferent else Words.CustomEndpointsAcknowledged,management.state.value.notice);assertEquals(canonical,management.state.value.customEndpoints!!.rows.single().model)}
            val receipt=management.receipts.value.single {it.action=="CustomEndpointSave"};assertEquals(JsonPrimitive(true),Json.parseToJsonElement(receipt.resultSummary).jsonObject["acknowledged"])
            for(value in listOf(alias,canonical,prior,"HIGH",CustomEndpointFixture.header,CustomEndpointFixture.inline,CustomEndpointFixture.url)) {assertFalse(receipt.requestSummary.contains(value));assertFalse(receipt.resultSummary.contains(value))}
        }
    }
    @Test fun catalogAndAliasStagingNeedsSeparateSaveApprovalAndObservesCanonicalContextAndGlobalReasoning()=exercise("normal")
    @Test fun managedReasoningOmissionKeepsTheSaveAckAndReportsTheObservationMismatch()=exercise("managed")
    @Test fun stagingAReturnedAddressClearsTheNewKeyBeforeAnotherExplicitProbe()=exercise("url")
    @Test fun anAlreadyReturnedAddressKeepsTheNewKeyWhileModeStagingOnlyChangesTheDraft()=exercise("noop")
    @Test fun anOldConnectionReportCannotStageValuesAfterOwnerSwitch()=exercise("stale")
    @Test fun unsupportedReasoningShapeCannotBeOverwrittenAndClearingDraftMetadataSendsNothing()=exercise("unsupported")
    @Test fun malformedReadbackAfterAliasSaveKeepsTheKnownAckWithoutReplay()=exercise("readback")
    @Test fun concurrentSharedReasoningChangeRefusesBeforeClaimAndSave()=exercise("changed")
}
