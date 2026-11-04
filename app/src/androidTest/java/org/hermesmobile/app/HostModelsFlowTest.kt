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
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HostModelsFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String="normal") {
        val reads=AtomicInteger();val refreshes=AtomicInteger();val mutations=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        credentials.managementServer().use {server->
            fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                if(q.method!="GET") mutations.incrementAndGet()
                return when {
                    q.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    q.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    q.path=="/dashboard/api/auth/me"->json("""{"provider":"password","user_id":"fixture-user"}""")
                    q.path=="/dashboard/api/profiles"->MockResponse().setHeader("Content-Type","application/json").setBody(if(mode=="alias") """{"profiles":[{"name":"hermes","path":"/private-home"}]}""" else PluginUpdateFixture.profiles().toString())
                    q.path=="/dashboard/api/mcp/servers?profile="+if(mode=="alias") "hermes" else "worker"->json("""{"servers":[]}""")
                    q.requestUrl!!.encodedPath=="/dashboard/api/model/auxiliary"->{assertEquals("GET",q.method);assertEquals("worker",q.requestUrl!!.queryParameter("profile"));assertEquals("Bearer models-ui-fixture",q.getHeader("Authorization"));reads.incrementAndGet();json("""{"main":{"provider":"fixture","model":"main-model"},"tasks":[{"task":"plugin:summary","provider":"auto","model":"","label":"Fixture task","plugin":"fixture-plugin","inherit_from":"vision","effective":{"provider":"fixture","model":"vision-model","base_url":"https://private-inherited-url"}}]}""")}
                    q.requestUrl!!.encodedPath=="/dashboard/api/model/options"->{assertEquals("GET",q.method);assertEquals("worker",q.requestUrl!!.queryParameter("profile"));assertEquals("true",q.requestUrl!!.queryParameter("include_unconfigured"));assertEquals("false",q.requestUrl!!.queryParameter("explicit_only"));assertEquals("Bearer models-ui-fixture",q.getHeader("Authorization"));reads.incrementAndGet();if(q.requestUrl!!.queryParameter("refresh")=="true") refreshes.incrementAndGet();if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()};json(if(mode=="invalid") """{"providers":"malformed"}""" else """{"provider":"fixture","model":"main-model","providers":[{"slug":"fixture","name":"Fixture provider","models":["main-model","second-model"],"warning":"private-provider-error","api_key":"private-provider-key","api_url":"https://private-provider-url"}]}""")}
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("models-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile",if(mode=="alias") "hermes" else "worker");put("debugHttp",true);put("tokens",ManagementTokens("models-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-models-open"));compose.onNodeWithTag("host-models-open").performClick();assertEquals(0,reads.get())
                if(mode=="alias") {compose.onNodeWithTag("host-models-read").assertIsNotEnabled();compose.onNodeWithText(Words.PluginProfileUnsupported.en).assertExists();assertEquals(0,mutations.get());return@use}
                compose.onNodeWithTag("host-models-read").performClick();compose.onNodeWithTag("host-models-read-cancel").performClick();assertEquals(0,reads.get());compose.onNodeWithTag("host-models-read").performClick();compose.onNodeWithTag("host-models-read-confirm").performClick()
                if(mode=="held") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitForIdle();assertEquals("unconfigured",management.state.value.apiScope);assertNull(management.state.value.hostModels);assertEquals(2,reads.get());assertEquals(0,mutations.get());return@use}
                compose.waitUntil(10000) {!management.state.value.busy}
                if(mode=="invalid") {assertNull(management.state.value.hostModels);assertEquals(Words.HostModelsUnavailable,management.state.value.error);assertEquals(2,reads.get());assertEquals(0,mutations.get());return@use}
                assertNotNull(management.state.value.hostModels);assertEquals(2,reads.get());compose.onNodeWithTag("host-models-list").performScrollToNode(hasText("vision-model"));compose.onNodeWithText("vision-model").assertExists();compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-models-provider-fixture"));compose.onNodeWithTag("host-models-provider-fixture").performClick();compose.onNodeWithTag("host-models-catalog").performScrollToNode(hasText("second-model"));compose.onNodeWithText("second-model").assertExists();assertEquals(2,reads.get());compose.onNodeWithTag("host-models-catalog-close").performClick()
                compose.onNodeWithTag("host-models-list").performScrollToNode(hasTestTag("host-models-refresh"));compose.onNodeWithTag("host-models-refresh").performClick();compose.onNodeWithTag("host-models-read-review").performScrollToNode(hasText(Words.HostModelsRefreshEffect.en));compose.onNodeWithText(Words.HostModelsRefreshEffect.en).assertExists();val shot=File(compose.activity.getExternalFilesDir(null),"qa/host-models-refresh-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)};compose.onNodeWithTag("host-models-read-cancel").performClick();assertEquals(0,refreshes.get());compose.onNodeWithTag("host-models-refresh").performClick();compose.onNodeWithTag("host-models-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.hostModels?.refreshed==true};assertEquals(4,reads.get());assertEquals(1,refreshes.get());assertEquals(0,mutations.get());assertEquals(0,other.get());assertFalse(management.state.value.hostModels.toString().contains("private"));compose.onNodeWithTag("host-models-close").performClick();assertNull(management.state.value.hostModels)
            } finally {release.countDown();compose.runOnUiThread {management.cancel()}}
        }
    }
    @Test fun separatelyReviewedReadAndRefreshBrowseStoredAndInheritedRoutesWithoutMutation()=exercise()
    @Test fun heldCatalogCannotRepopulateAnotherConnection()=exercise("held")
    @Test fun malformedCatalogIsUnavailableInsteadOfEmptySuccess()=exercise("invalid")
    @Test fun legacyProfileAliasesCannotReadModelConfiguration()=exercise("alias")
}
