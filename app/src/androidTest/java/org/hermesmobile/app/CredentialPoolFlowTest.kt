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

class CredentialPoolFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun editableEquals(value:String)=SemanticsMatcher("Editable text matches the exact reviewed value") {node->node.config.getOrNull(SemanticsProperties.EditableText)?.text==value}
    private fun exercise(mode:String) {
        val provider=if(mode=="custom") "custom:fixture-endpoint" else CredentialPoolFixture.provider
        val current=AtomicReference(CredentialPoolFixture.inventory(provider));val adds=AtomicInteger();val removes=AtomicInteger();val reads=AtomicInteger();val mcp=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val u=q.requestUrl!!
                if(u.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer pool-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    u.encodedPath=="/dashboard/api/mcp/servers"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    u.encodedPath=="/dashboard/api/credentials/pool" && q.method=="GET"->{assertEquals("worker",u.queryParameter("profile"));val n=reads.incrementAndGet();if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()};if(mode=="cleanup-readback" && n==3) MockResponse().setResponseCode(500) else json(current.get())}
                    u.encodedPath=="/dashboard/api/credentials/pool" && q.method=="POST"->{
                        assertEquals("worker",u.queryParameter("profile"));adds.incrementAndGet();val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(setOf("provider","api_key","label"),body.keys);assertEquals(provider,body.text("provider"));assertEquals(CredentialPoolFixture.secret,body.text("api_key"));assertEquals(CredentialPoolFixture.label,body.text("label"));current.set(CredentialPoolFixture.add(provider));if(mode=="unknown") MockResponse().setResponseCode(400).setBody("private-pool-error-fixture") else json(CredentialPoolFixture.ack(provider))
                    }
                    u.pathSegments.take(4)==listOf("dashboard","api","credentials","pool") && u.pathSegments.size==6 && q.method=="DELETE"->{
                        assertEquals("worker",u.queryParameter("profile"));assertEquals(provider,u.pathSegments[4]);assertEquals("2",u.pathSegments[5]);assertEquals(0L,q.bodySize);removes.incrementAndGet();current.set(CredentialPoolFixture.remove(provider));val ack=CredentialPoolFixture.ack(provider,1,true);json(if(mode=="cleanup") JsonObject(ack+mapOf("hints" to JsonPrimitive("private-cli-command-fixture"))) else ack)
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,"pool-inference-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("pool-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("credential-pool-open"));compose.onNodeWithTag("credential-pool-open").performClick();assertEquals(0,reads.get())
                fun scroll(tag:String)=compose.onNodeWithTag("credential-pool-list").performScrollToNode(hasTestTag(tag))
                fun read() {scroll("credential-pool-read");compose.onNodeWithTag("credential-pool-read").performClick();compose.onNodeWithTag("credential-pool-read-confirm").performClick()}
                scroll("credential-pool-read");compose.onNodeWithTag("credential-pool-read").performClick();compose.onNodeWithTag("credential-pool-read-cancel").performClick();assertEquals(0,reads.get());read()
                if(mode=="held") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.credentialPool);assertEquals(0,adds.get());assertEquals(0,removes.get());return@use}
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.credentialPool!=null};for(secret in listOf(CredentialPoolFixture.preview,CredentialPoolFixture.source)) compose.onNodeWithText(secret,substring=true).assertDoesNotExist()
                fun addDraft() {
                    scroll("credential-pool-add-$provider");compose.onNodeWithTag("credential-pool-add-$provider").performClick();compose.onNodeWithTag("credential-pool-provider").assert(editableEquals(provider))
                    compose.onNodeWithTag("credential-pool-editor").performScrollToNode(hasTestTag("credential-pool-value"));compose.onNodeWithTag("credential-pool-value").assert(editableEquals(""));compose.onNodeWithTag("credential-pool-value").performTextReplacement("***");compose.onNodeWithTag("credential-pool-review-open").assertIsNotEnabled();compose.onNodeWithTag("credential-pool-value").performTextReplacement(CredentialPoolFixture.secret);compose.onNodeWithTag("credential-pool-editor").performScrollToNode(hasTestTag("credential-pool-label"));compose.onNodeWithTag("credential-pool-label").performTextReplacement(CredentialPoolFixture.label);androidx.test.espresso.Espresso.closeSoftKeyboard();compose.onNodeWithTag("credential-pool-review-open").performClick()
                }
                fun approve(add:Boolean) {
                    compose.onNodeWithTag("credential-pool-confirm").assertIsNotEnabled();compose.onNodeWithTag("credential-pool-review").performScrollToNode(hasTestTag("credential-pool-approve"));compose.onNodeWithTag("credential-pool-approve").assertIsOff().performClick()
                    if(add) {compose.onNodeWithTag("credential-pool-confirm").assertIsNotEnabled();compose.onNodeWithTag("credential-pool-review").performScrollToNode(hasTestTag("credential-pool-review-reveal"));compose.onNodeWithTag("credential-pool-review-reveal").performClick();compose.onNodeWithTag("credential-pool-review").performScrollToNode(hasTestTag("credential-pool-reviewed-value"));compose.onNodeWithTag("credential-pool-reviewed-value").assert(editableEquals(CredentialPoolFixture.secret));compose.onNodeWithTag("credential-pool-review").performScrollToNode(hasTestTag("credential-pool-approve"))}
                    compose.onNodeWithTag("credential-pool-confirm").assertIsEnabled()
                }
                if(mode in setOf("remove","cleanup","cleanup-readback")) {
                    scroll("credential-pool-remove-$provider-2");compose.onNodeWithTag("credential-pool-remove-$provider-2").performClick();compose.onNodeWithTag("credential-pool-review").performScrollToNode(hasText(Words.CredentialPoolIndexEffect.en));compose.onNode(hasText(Words.CredentialPoolIndexEffect.en) and hasAnyAncestor(hasTestTag("credential-pool-review"))).assertIsDisplayed();approve(false)
                } else {
                    addDraft();approve(true);compose.onNodeWithTag("credential-pool-review-cancel").performClick();assertEquals(0,adds.get());addDraft();approve(true)
                }
                val shot=File(compose.activity.getExternalFilesDir(null),"qa/credential-pool-$mode-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                if(mode=="stale") current.set(CredentialPoolFixture.inventory(provider,CredentialPoolFixture.rows("source")))
                compose.onNodeWithTag("credential-pool-confirm").performClick()
                if(mode=="stale") {compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertEquals(0,adds.get());assertTrue(management.receipts.value.none {it.action.startsWith("CredentialPool")});return@use}
                val action=if(mode in setOf("remove","cleanup","cleanup-readback")) "CredentialPoolRemove" else "CredentialPoolAdd"
                compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action==action && it.state==(if(mode=="unknown") "unknown" else "acknowledged")}}
                val receipt=management.receipts.value.first {it.action==action};for(secret in listOf(CredentialPoolFixture.secret,CredentialPoolFixture.label,CredentialPoolFixture.preview,CredentialPoolFixture.source,"private-cli-command-fixture")) assertFalse(secret,receipt.toString().contains(secret))
                compose.onNodeWithTag("credential-pool-list").assertExists()
                if(mode=="unknown") {val count=adds.get();read();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.credentialPool!=null};scroll("credential-pool-add");compose.onNodeWithTag("credential-pool-add").assertIsNotEnabled();assertEquals(1,count);assertEquals(count,adds.get());return@use}
                if(mode=="cleanup") {scroll("credential-pool-receipt-${receipt.key}");compose.onNodeWithTag("credential-pool-list").performScrollToNode(hasText(Words.CredentialPoolCleanupUnavailable.en));compose.onNode(hasText(Words.CredentialPoolCleanupUnavailable.en) and hasAnyAncestor(hasTestTag("credential-pool-receipt-${receipt.key}"))).assertIsDisplayed()}
                if(mode in setOf("remove","cleanup-readback")) {
                    for(message in listOf("private-cleanup-file-fixture","private-cli-command-fixture")) compose.onNodeWithText(message).assertDoesNotExist()
                    scroll("credential-pool-report-open");compose.onNodeWithTag("credential-pool-report-open").performClick();compose.onNodeWithText("private-cli-command-fixture").assertDoesNotExist();compose.onNodeWithTag("credential-pool-details").performScrollToNode(hasTestTag("credential-pool-details-reveal"));compose.onNodeWithTag("credential-pool-details-reveal").performClick();compose.onNodeWithTag("credential-pool-details").performScrollToNode(hasText("private-cli-command-fixture"));compose.onNode(hasText("private-cli-command-fixture") and hasAnyAncestor(hasTestTag("credential-pool-details"))).assertIsDisplayed();assertFalse(management.state.value.toString().contains("private-cli-command-fixture"));compose.onNodeWithTag("credential-pool-details-close").performClick();compose.onNodeWithTag("credential-pool-close").performClick();assertNull(management.state.value.poolCleanup);compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();compose.onNodeWithTag("credential-pool-report-open").assertDoesNotExist()
                }
                if(mode=="observe") {
                    val before=reads.get();compose.onNodeWithTag("credential-pool-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(before,reads.get());read();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.credentialPool!=null};scroll("credential-pool-remove-$provider-2");compose.onNodeWithTag("credential-pool-remove-$provider-2").performClick();approve(false);compose.onNodeWithTag("credential-pool-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="CredentialPoolRemove" && it.state=="acknowledged"}};compose.onNodeWithTag("credential-pool-list").assertExists();assertEquals(1,removes.get())
                }
                assertEquals(if(action=="CredentialPoolAdd") 1 else 0,adds.get());assertEquals(if(action=="CredentialPoolRemove" || mode=="observe") 1 else 0,removes.get());assertEquals(1,mcp.get());assertEquals(0,other.get())
            } finally {release.countDown()}
        }
    }
    @Test fun freshCredentialNeedsRevealAndFreshConsentAndCancelClearsDraft()=exercise("add")
    @Test fun removeReviewsMutableIndexAndDoesNotClaimRemovedIdentity()=exercise("remove")
    @Test fun receiptObservationDoesNotFetchOrReplayAndDialogSurvivesAnotherMutation()=exercise("observe")
    @Test fun unknownWriteBlocksEvenAfterCatalogRefresh()=exercise("unknown")
    @Test fun stalePrivateSourceRefusesBeforeWrite()=exercise("stale")
    @Test fun lateOldScopeReadCannotEnterNewConnection()=exercise("held")
    @Test fun canonicalCustomPoolCanReceiveFreshCredential()=exercise("custom")
    @Test fun failedReadbackStillOffersBoundOriginalCleanupHints()=exercise("cleanup-readback")
    @Test fun malformedCleanupDetailsKeepKnownAckAndShowReason()=exercise("cleanup")
}
