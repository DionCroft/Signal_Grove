package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class CustomEndpointFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun editable(value:String)=SemanticsMatcher("Exact endpoint field text") {it.config.getOrNull(SemanticsProperties.EditableText)?.text==value}
    private fun exercise(mode:String) {
        val inferenceToken=credentials.uniqueToken("endpoint-inference-fixture")
        val kind=when(mode) {"legacy"->"legacy";"direct"->"direct";else->"provider"};val rowId=when(kind) {"legacy"->"legacy-endpoint";"direct"->"custom";else->if(mode=="body-id") "Team/Model?%entry" else CustomEndpointFixture.id}
        val current=AtomicReference(CustomEndpointFixture.metadata(kind));val config=AtomicReference(CustomEndpointFixture.config(kind));val saves=AtomicInteger();val removes=AtomicInteger();val activates=AtomicInteger();val reads=AtomicInteger();val rawReads=AtomicInteger();val mcp=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        if(mode=="body-id") {current.set(buildJsonObject {put("endpoints",JsonArray(listOf(CustomEndpointFixture.row(rowId))));put("current",CustomEndpointFixture.route())});config.set(buildJsonObject {put("providers",buildJsonObject {put(rowId,CustomEndpointFixture.provider())});put("custom_providers",JsonArray(emptyList()))})}
        MockWebServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val u=q.requestUrl!!
                if(u.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer endpoint-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    u.encodedPath=="/dashboard/api/mcp/servers"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    u.encodedPath=="/dashboard/api/config" && q.method=="GET"->{assertEquals("worker",u.queryParameter("profile"));assertEquals("false",u.queryParameter("include_defaults"));rawReads.incrementAndGet();if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()};json(config.get())}
                    u.encodedPath=="/dashboard/api/providers/custom-endpoints" && q.method=="GET"->{assertEquals("worker",u.queryParameter("profile"));reads.incrementAndGet();json(current.get())}
                    u.encodedPath=="/dashboard/api/providers/custom-endpoints" && q.method=="POST"->{
                        assertEquals("worker",u.queryParameter("profile"));saves.incrementAndGet();val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertFalse(body.containsKey("profile"));assertFalse(body.containsKey("confirm_expensive_model"));assertFalse(body.toString().contains(CustomEndpointFixture.inline));assertFalse(body.toString().contains(CustomEndpointFixture.header));assertEquals(if(mode=="create") CustomEndpointFixture.newId else rowId,body.text("id"))
                        if(mode=="create") {assertEquals(setOf("id","name","base_url","model","discover_models","make_default","api_key","api_mode","context_length","models"),body.keys);assertEquals(CustomEndpointFixture.fresh,body.text("api_key"));assertEquals(JsonPrimitive(""),body["api_mode"]);assertEquals(JsonPrimitive(8192),body["context_length"]);assertEquals(JsonPrimitive(false),body["discover_models"]);assertEquals(JsonPrimitive(true),body["make_default"]);assertEquals(JsonArray(listOf(JsonPrimitive("added-model"))),body["models"])}
                        else if(mode=="clear") {assertEquals(JsonPrimitive(""),body["api_key"]);assertFalse(body.containsKey("api_mode"));assertFalse(body.containsKey("context_length"))}
                        else {assertFalse(body.containsKey("api_key"));assertFalse(body.containsKey("api_mode"));assertFalse(body.containsKey("context_length"))}
                        current.set(CustomEndpointFixture.saved(current.get(),body));config.set(CustomEndpointFixture.savedConfig(config.get(),body))
                        if(mode=="unknown") MockResponse().setResponseCode(400).setBody("private-endpoint-partial-error") else json(CustomEndpointFixture.ack(id=body.text("id")))
                    }
                    u.pathSegments.take(4)==listOf("dashboard","api","providers","custom-endpoints") && u.pathSegments.size==6 && q.method=="POST"->{assertEquals("worker",u.queryParameter("profile"));assertEquals(listOf(rowId,"activate"),u.pathSegments.drop(4));assertEquals(JsonObject(emptyMap()),Json.parseToJsonElement(q.body.readUtf8()));activates.incrementAndGet();current.set(CustomEndpointFixture.activated(current.get(),rowId));json(CustomEndpointFixture.ack(provider=rowId,model=CustomEndpointFixture.model))}
                    u.pathSegments.take(4)==listOf("dashboard","api","providers","custom-endpoints") && u.pathSegments.size==5 && q.method=="DELETE"->{assertEquals("worker",u.queryParameter("profile"));assertEquals(rowId,u.pathSegments[4]);assertEquals(0L,q.bodySize);removes.incrementAndGet();current.set(JsonObject(current.get()+mapOf("endpoints" to JsonArray(emptyList()))));config.set(buildJsonObject {put("providers",JsonObject(emptyMap()));put("custom_providers",JsonArray(emptyList()))});json(CustomEndpointFixture.ack())}
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,inferenceToken,true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("endpoint-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("endpoint-open"));compose.onNodeWithTag("endpoint-open").performClick();assertEquals(0,rawReads.get());assertEquals(0,reads.get())
                fun scroll(tag:String)=compose.onNodeWithTag("endpoint-list").performScrollToNode(hasTestTag(tag))
                fun read() {scroll("endpoint-read");compose.onNodeWithTag("endpoint-read").performClick();compose.onNodeWithTag("endpoint-read-confirm").performClick()}
                scroll("endpoint-read");compose.onNodeWithTag("endpoint-read").performClick();compose.onNodeWithTag("endpoint-read-cancel").performClick();assertEquals(0,rawReads.get());read()
                if(mode=="held") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.customEndpoints);assertEquals(0,saves.get());return@use}
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.customEndpoints!=null}
                for(secret in listOf(CustomEndpointFixture.inline,CustomEndpointFixture.header,CustomEndpointFixture.preview,CustomEndpointFixture.url,"PRIVATE_ENDPOINT_ALIAS")) compose.onNodeWithText(secret,substring=true).assertDoesNotExist()
                if(mode=="direct") {for(action in listOf("edit","activate","remove")) {scroll("endpoint-$action-$rowId");compose.onNodeWithTag("endpoint-$action-$rowId").assertIsNotEnabled()};scroll("endpoint-details-$rowId");compose.onNodeWithTag("endpoint-details-$rowId").performClick();compose.onNodeWithText(CustomEndpointFixture.url).assertDoesNotExist();compose.onNodeWithTag("endpoint-details").performScrollToNode(hasTestTag("endpoint-details-reveal"));compose.onNodeWithTag("endpoint-details-reveal").performClick();compose.onNodeWithTag("endpoint-details").performScrollToNode(hasText(CustomEndpointFixture.url));compose.onNode(hasText(CustomEndpointFixture.url) and hasAnyAncestor(hasTestTag("endpoint-details"))).assertIsDisplayed();compose.onNodeWithText(CustomEndpointFixture.preview).assertDoesNotExist();assertEquals(0,saves.get()+activates.get()+removes.get());return@use}
                if(mode=="body-id") {for(action in listOf("activate","remove")) {scroll("endpoint-$action-$rowId");compose.onNodeWithTag("endpoint-$action-$rowId").assertIsNotEnabled()};scroll("endpoint-edit-$rowId");compose.onNodeWithTag("endpoint-edit-$rowId").assertIsEnabled()}
                fun editor(create:Boolean) {
                    val tag=if(create) "endpoint-create" else "endpoint-edit-$rowId";scroll(tag);compose.onNodeWithTag(tag).performClick();compose.onNodeWithTag("endpoint-review-open").assertIsNotEnabled();compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-editor-reveal"));compose.onNodeWithTag("endpoint-editor-reveal").performClick()
                    if(create) {compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-id"));compose.onNodeWithTag("endpoint-id").performTextReplacement(CustomEndpointFixture.newId)} else {compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-id"));compose.onNodeWithTag("endpoint-id").assert(editable(rowId))}
                    compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-name"));compose.onNodeWithTag("endpoint-name").performTextReplacement(if(create) "New endpoint fixture" else "Updated endpoint fixture")
                    if(create) {compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-url"));compose.onNodeWithTag("endpoint-url").performTextReplacement(CustomEndpointFixture.url);compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-model"));compose.onNodeWithTag("endpoint-model").performTextReplacement(CustomEndpointFixture.model)}
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    if(create || mode=="clear") {val key=if(create) "Fresh" else "Clear";compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-key-$key"));compose.onNodeWithTag("endpoint-key-$key").performClick()}
                    if(create) {
                        compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-secret"));compose.onNodeWithTag("endpoint-secret").assert(editable(""));compose.onNodeWithTag("endpoint-secret").performTextReplacement("***");compose.onNodeWithTag("endpoint-review-open").assertIsNotEnabled();compose.onNodeWithTag("endpoint-secret").performTextReplacement(CustomEndpointFixture.fresh);androidx.test.espresso.Espresso.closeSoftKeyboard()
                        compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-context-set"));compose.onNodeWithTag("endpoint-context-set").performClick();compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-context"));compose.onNodeWithTag("endpoint-context").performTextReplacement("8192");compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-models"));compose.onNodeWithTag("endpoint-models").performTextReplacement("added-model");androidx.test.espresso.Espresso.closeSoftKeyboard();compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-discover"));compose.onNodeWithTag("endpoint-discover").performClick();compose.onNodeWithTag("endpoint-editor").performScrollToNode(hasTestTag("endpoint-main"));compose.onNodeWithTag("endpoint-main").performClick()
                    }
                    compose.onNodeWithTag("endpoint-review-open").performClick()
                }
                fun approve(fresh:Boolean=false) {
                    compose.onNodeWithTag("endpoint-confirm").assertIsNotEnabled();compose.onNodeWithTag("endpoint-review").performScrollToNode(hasTestTag("endpoint-approve"));compose.onNodeWithTag("endpoint-approve").assertIsOff().performClick();compose.onNodeWithTag("endpoint-confirm").assertIsNotEnabled();compose.onNodeWithTag("endpoint-review").performScrollToNode(hasTestTag("endpoint-review-reveal"));compose.onNodeWithTag("endpoint-review-reveal").performClick();compose.onNodeWithText(CustomEndpointFixture.inline,substring=true).assertDoesNotExist();if(fresh) {compose.onNodeWithTag("endpoint-review").performScrollToNode(hasTestTag("endpoint-reviewed-secret"));compose.onNodeWithTag("endpoint-reviewed-secret").assert(editable(CustomEndpointFixture.fresh))};compose.onNodeWithTag("endpoint-review").performScrollToNode(hasTestTag("endpoint-approve"));compose.onNodeWithTag("endpoint-confirm").assertIsEnabled()
                }
                if(mode in setOf("activate","remove")) {val tag="endpoint-$mode-$rowId";scroll(tag);compose.onNodeWithTag(tag).performClick()} else editor(mode in setOf("create","cancel"))
                approve(mode in setOf("create","cancel"))
                if(mode=="cancel") {compose.onNodeWithTag("endpoint-review-cancel").performClick();assertEquals(0,saves.get());editor(true);approve(true);compose.onNodeWithTag("endpoint-review-cancel").performClick();compose.onNodeWithTag("endpoint-close").performClick();assertNull(management.state.value.customEndpoints);compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("endpoint-open"));compose.onNodeWithTag("endpoint-open").performClick();compose.onNodeWithTag("endpoint-create").assertDoesNotExist();assertEquals(1,rawReads.get());return@use}
                val shot=File(compose.activity.getExternalFilesDir(null),"qa/endpoint-$mode-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                if(mode=="stale") config.set(CustomEndpointFixture.config(kind,"private"));compose.onNodeWithTag("endpoint-confirm").performClick()
                if(mode=="stale") {compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertEquals(0,saves.get());assertTrue(management.receipts.value.none {it.action.startsWith("CustomEndpoint")});return@use}
                val action=when(mode) {"activate"->"CustomEndpointActivate";"remove"->"CustomEndpointRemove";else->"CustomEndpointSave"}
                compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action==action && it.state==(if(mode=="unknown") "unknown" else "acknowledged")}}
                val receipt=management.receipts.value.first {it.action==action};for(secret in listOf(rowId,CustomEndpointFixture.url,CustomEndpointFixture.inline,CustomEndpointFixture.header,CustomEndpointFixture.preview,CustomEndpointFixture.fresh,CustomEndpointFixture.model,"private-endpoint-partial-error")) assertFalse(secret,receipt.toString().contains(secret));compose.onNodeWithTag("endpoint-list").assertExists()
                if(mode=="unknown") {read();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.customEndpoints!=null};scroll("endpoint-edit-$rowId");compose.onNodeWithTag("endpoint-edit-$rowId").assertIsNotEnabled();assertEquals(1,saves.get());return@use}
                if(mode=="observe") {val before=reads.get();val beforeRaw=rawReads.get();compose.onNodeWithTag("endpoint-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(before,reads.get());assertEquals(beforeRaw,rawReads.get());read();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.customEndpoints!=null};scroll("endpoint-remove-$rowId");compose.onNodeWithTag("endpoint-remove-$rowId").performClick();approve();compose.onNodeWithTag("endpoint-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="CustomEndpointRemove" && it.state=="acknowledged"}};compose.onNodeWithTag("endpoint-list").assertExists();assertEquals(1,removes.get())}
                assertEquals(if(action=="CustomEndpointSave") 1 else 0,saves.get());assertEquals(if(action=="CustomEndpointRemove" || mode=="observe") 1 else 0,removes.get());assertEquals(if(action=="CustomEndpointActivate") 1 else 0,activates.get());assertEquals(1,mcp.get());assertEquals(0,other.get())
            } finally {release.countDown()}
        }
    }
    @Test fun freshCreationReviewsEveryFieldAndFreshKeyWithUncheckedConsent()=exercise("create")
    @Test fun bodyOnlyLiteralIdCanBeEditedWhilePathControlsExplainTheirLimit()=exercise("body-id")
    @Test fun editingLiteralCaseAndColonIdKeepsStoredKeyModeAndContext()=exercise("edit")
    @Test fun explicitCredentialClearIsSeparateFromKeeping()=exercise("clear")
    @Test fun activationNeedsSeparateDestinationAndMainRouteReview()=exercise("activate")
    @Test fun removalNeedsSeparateSharedKeyAndMainDetachReview()=exercise("remove")
    @Test fun reviewedLegacySavePromotesExactlyTheSelectedEntry()=exercise("legacy")
    @Test fun syntheticMainRouteExplainsDisabledControlsAndHidesPreviews()=exercise("direct")
    @Test fun receiptObservationDoesNotFetchMcpOrReplayAndSurvivesAnotherWrite()=exercise("observe")
    @Test fun unknownWriteStaysBlockedAfterAnotherCatalogRead()=exercise("unknown")
    @Test fun privateHeaderRotationRefusesBeforeWrite()=exercise("stale")
    @Test fun oldScopePrivateReadCannotPopulateAnotherConnection()=exercise("held")
    @Test fun cancelDiscardsFreshSecretAndRequiresNewReviewAndConsent()=exercise("cancel")
}
