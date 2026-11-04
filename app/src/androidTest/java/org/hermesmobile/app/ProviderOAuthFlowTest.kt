package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import java.util.concurrent.atomic.AtomicReference

class ProviderOAuthFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String) {
        val reads=AtomicInteger();val starts=AtomicInteger();val polls=AtomicInteger();val cancels=AtomicInteger();val disconnects=AtomicInteger();val other=AtomicInteger();val status=AtomicReference("pending");val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        MockWebServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val u=q.requestUrl!!;if(u.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer provider-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
                if(u.encodedPath.contains("providers/oauth")) assertEquals("worker",u.queryParameter("profile"))
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    u.encodedPath=="/dashboard/api/mcp/servers"->json(buildJsonObject {put("servers",JsonArray(emptyList()))})
                    u.encodedPath=="/dashboard/api/providers/oauth"->{reads.incrementAndGet();if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()};json(ProviderOAuthFixture.catalog())}
                    u.encodedPath=="/dashboard/api/providers/oauth/fixture-oauth/start"->{starts.incrementAndGet();assertEquals("POST",q.method);assertEquals("{}",q.body.readUtf8());if(mode=="unknown") MockResponse().setResponseCode(422) else json(ProviderOAuthFixture.start())}
                    u.encodedPath=="/dashboard/api/providers/oauth/fixture-oauth/poll/${ProviderOAuthFixture.session}"->{polls.incrementAndGet();json(ProviderOAuthFixture.poll(status.get()))}
                    u.encodedPath=="/dashboard/api/providers/oauth/sessions/${ProviderOAuthFixture.session}"->{cancels.incrementAndGet();assertEquals("DELETE",q.method);json(if(mode=="not-found") buildJsonObject {put("ok",false);put("message","session not found")} else buildJsonObject {put("ok",true);put("session_id",ProviderOAuthFixture.session)})}
                    u.encodedPath=="/dashboard/api/providers/oauth/fixture-oauth"->{disconnects.incrementAndGet();assertEquals("DELETE",q.method);json(buildJsonObject {put("ok",true);put("provider",ProviderOAuthFixture.provider)})}
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("provider-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("provider-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("provider-accounts-open"));compose.onNodeWithTag("provider-accounts-open").performClick();assertEquals(0,reads.get())
                compose.onNodeWithTag("provider-accounts-read").performClick();compose.onNodeWithTag("provider-accounts-read-cancel").performClick();assertEquals(0,reads.get());compose.onNodeWithTag("provider-accounts-read").performClick();compose.onNodeWithTag("provider-accounts-read-confirm").performClick()
                if(mode=="held") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.providerAccounts);assertTrue(management.state.value.providerFlows.isEmpty());assertEquals(0,starts.get());return@use}
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.providerAccounts!=null};for(secret in listOf("private-preview","private-path","private-command","private-email@example.com","private-error")) compose.onNodeWithText(secret,substring=true).assertDoesNotExist();assertEquals(0,starts.get())
                if(mode=="external") {compose.onNodeWithTag("provider-accounts-list").performScrollToNode(hasTestTag("provider-start-external"));compose.onNodeWithTag("provider-start-external").assertIsNotEnabled();compose.onNodeWithTag("provider-accounts-list").performScrollToNode(hasText(Words.ProviderOAuthExternal.en));compose.onNodeWithText(Words.ProviderOAuthExternal.en).assertExists();assertEquals(0,starts.get());return@use}
                val action=if(mode=="disconnect") "disconnect" else "start"
                fun review() {compose.onNodeWithTag("provider-accounts-list").performScrollToNode(hasTestTag("provider-$action-fixture-oauth"));compose.onNodeWithTag("provider-$action-fixture-oauth").performClick();compose.onNodeWithTag("provider-oauth-confirm").assertIsNotEnabled();compose.onNodeWithTag("provider-oauth-review").performScrollToNode(hasTestTag("provider-oauth-approve"));compose.onNodeWithTag("provider-oauth-approve").assertIsOff().performClick()}
                review();compose.onNodeWithTag("provider-oauth-review-cancel").performClick();assertEquals(0,starts.get());assertEquals(0,disconnects.get());review()
                val shot=File(compose.activity.getExternalFilesDir(null),"qa/provider-$mode-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                compose.onNodeWithTag("provider-oauth-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action==(if(mode=="disconnect") "ProviderDisconnect" else "ProviderOAuthStart") && it.state==(if(mode=="unknown") "unknown" else "acknowledged")}}
                val receipt=management.receipts.value.first {it.action==if(mode=="disconnect") "ProviderDisconnect" else "ProviderOAuthStart"};assertFalse(receipt.toString().contains(ProviderOAuthFixture.code));assertFalse(receipt.toString().contains("private"))
                if(mode=="unknown") {assertEquals("unknown",receipt.state);compose.onNodeWithTag("provider-accounts-list").performScrollToNode(hasTestTag("provider-accounts-read"));compose.onNodeWithTag("provider-accounts-read").performClick();compose.onNodeWithTag("provider-accounts-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.providerAccounts!=null};compose.onNodeWithTag("provider-accounts-list").performScrollToNode(hasTestTag("provider-start-fixture-oauth"));compose.onNodeWithTag("provider-start-fixture-oauth").assertIsNotEnabled();assertEquals(1,starts.get());return@use}
                if(mode=="disconnect") {assertEquals("acknowledged",receipt.state);assertEquals(1,disconnects.get());assertEquals(0,starts.get());return@use}
                assertEquals(1,starts.get());compose.onNodeWithTag("provider-accounts-list").performScrollToNode(hasTestTag("provider-flow-${receipt.key}"));compose.onNodeWithTag("provider-flow-${receipt.key}").performClick();compose.onNodeWithText(ProviderOAuthFixture.code).assertDoesNotExist();compose.onNodeWithTag("provider-flow-list").performScrollToNode(hasTestTag("provider-flow-code"));compose.onNodeWithTag("provider-flow-code").performClick();compose.onNodeWithTag("provider-flow-list").performScrollToNode(hasText(ProviderOAuthFixture.code));compose.onNodeWithText(ProviderOAuthFixture.code).assertIsDisplayed()
                compose.onNodeWithTag("provider-flow-list").performScrollToNode(hasTestTag("provider-flow-browser"));compose.onNodeWithTag("provider-flow-browser").performClick();compose.onNodeWithText("https://provider.example/device").assertExists();compose.onNodeWithText(ProviderOAuthFixture.url).assertDoesNotExist();val n=polls.get();compose.onNodeWithTag("provider-browser-cancel").performClick();assertEquals(n,polls.get())
                if(mode=="browser") {val opened=AtomicReference<String>();val shown=management.state.value.providerFlows.getValue(receipt.key);compose.runOnUiThread {management.openProviderBrowser(receipt.key,shown) {opened.set(it)}};compose.waitUntil(10000) {!management.state.value.busy && opened.get()!=null};assertEquals(ProviderOAuthFixture.url,opened.get());assertEquals(n+1,polls.get());assertEquals(1,starts.get());return@use}
                if(mode in setOf("cancel","not-found")) {compose.onNodeWithTag("provider-flow-list").performScrollToNode(hasTestTag("provider-flow-cancel"));compose.onNodeWithTag("provider-flow-cancel").performClick();compose.onNodeWithTag("provider-oauth-confirm").assertIsNotEnabled();compose.onNodeWithTag("provider-oauth-review").performScrollToNode(hasTestTag("provider-oauth-approve"));compose.onNodeWithTag("provider-oauth-approve").performClick();compose.onNodeWithTag("provider-oauth-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="ProviderOAuthCancel" && it.state=="acknowledged"}};assertEquals(1,cancels.get());assertEquals(n+1,polls.get());compose.onNodeWithTag("provider-flow-list").assertExists();compose.onNodeWithText(ProviderOAuthFixture.code).assertDoesNotExist();if(mode=="not-found") {compose.onNodeWithTag("provider-flow-list").performScrollToNode(hasText(Words.ProviderOAuthUnavailable.en));compose.onNode(hasText(Words.ProviderOAuthUnavailable.en) and hasAnyAncestor(hasTestTag("provider-flow-list"))).assertIsDisplayed();compose.onNodeWithText(Words.ProviderOAuthCancelled.en).assertDoesNotExist()}}
                else {status.set("approved");compose.onNodeWithTag("provider-flow-list").performScrollToNode(hasTestTag("provider-flow-read"));compose.onNodeWithTag("provider-flow-read").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.providerFlows[receipt.key]?.status=="approved"};compose.onNodeWithText(ProviderOAuthFixture.code).assertDoesNotExist();compose.onNodeWithTag("provider-flow-list").performScrollToNode(hasTestTag("provider-flow-code"));compose.onNodeWithTag("provider-flow-code").assertIsNotEnabled();assertEquals(0,cancels.get())}
                assertEquals(0,other.get());assertEquals(1,starts.get())
            } finally {release.countDown()}
        }
    }
    @Test fun startNeedsFreshReviewAndApprovalRemovesCode()=exercise("approved")
    @Test fun browserReviewShowsPublicDestinationAndFreshlyChecksOwnership()=exercise("browser")
    @Test fun explicitCancelNeedsSeparateReviewAndProviderCheck()=exercise("cancel")
    @Test fun knownMissingSessionNeverClaimsCancellation()=exercise("not-found")
    @Test fun disconnectNeedsFreshConsentAndSeparateReceipt()=exercise("disconnect")
    @Test fun externalCliFlowsRemainVisibleAndDisabledWithReason()=exercise("external")
    @Test fun unknownStartCannotReplayThroughCatalogRefresh()=exercise("unknown")
    @Test fun lateOldScopeCatalogCannotEnterNewConnection()=exercise("held")
}
