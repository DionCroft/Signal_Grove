package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HostRelayFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun tap(list:String,tag:String) {compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()}
    private fun shot(name:String) {val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun exercise(mode:String) {
        val relay=AtomicInteger();val channels=AtomicInteger();val writes=AtomicInteger();val changed=AtomicBoolean(false);val opened=mutableListOf<String>()
        credentials.managementServer().use {server->
            fun json(raw:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(raw.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val url=q.requestUrl!!
                if(url.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer relay-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));if(q.method!="GET") writes.incrementAndGet()}
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    url.encodedPath=="/dashboard/api/mcp/servers"->json(buildJsonObject {put("servers",JsonArray(emptyList()))})
                    url.encodedPath=="/dashboard/api/messaging/platforms"->{assertEquals("worker",url.queryParameter("profile"));channels.incrementAndGet();json(HostRelayFixture.channels(mode!="scope"))}
                    url.encodedPath=="/dashboard/api/messaging/relay"->{assertNull(url.query);relay.incrementAndGet();json(HostRelayFixture.report(if(mode=="unknown") "unknown" else "portal",if(changed.get()) "https://changed.example.test/agents" else HostRelayFixture.portal))}
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val bilingual=mode=="bilingual"
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(server.url("inference/").toString(),credentials.uniqueToken("relay-ui-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=bilingual,pinyin=bilingual))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==bilingual && api.preferences.value.pinyin==bilingual}
            val scope=api.profileScope.value
            app.credentials.saveManagement(scope,buildJsonObject {put("url",server.url("dashboard/").toString());put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("relay-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();val s by management.state.collectAsStateWithLifecycle();HermesAppTheme(p) {HostRelayDialog(management,s,openPortal={opened.add(it)}) {management.closeHostRelay()}}}}
            assertEquals(0,relay.get());assertEquals(0,channels.get());tap("relay-list","relay-read")
            if(mode=="cancel") {compose.onNodeWithTag("relay-read-cancel").performClick();assertEquals(0,relay.get());assertEquals(0,channels.get());tap("relay-list","relay-read")}
            compose.onNodeWithTag("relay-read-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && (management.state.value.relay!=null || management.state.value.error!=null)}
            if(mode=="scope") {assertEquals(Words.RelayScopeUnavailable,management.state.value.error);assertEquals(0,relay.get());assertNull(management.state.value.relay);return@use}
            assertEquals(1,relay.get());assertEquals(2,channels.get());compose.onNodeWithText(HostRelayFixture.account,substring=true).assertDoesNotExist();compose.onNodeWithText(HostRelayFixture.portal,substring=true).assertDoesNotExist()
            tap("relay-list","relay-reveal");compose.onNodeWithTag("relay-list").performScrollToNode(hasText(Words.RelayAccountId.localized(api.preferences.value)+": "+HostRelayFixture.account));compose.onNodeWithText(Words.RelayAccountId.localized(api.preferences.value)+": "+HostRelayFixture.account).assertIsDisplayed()
            if(mode=="unknown") {compose.onNodeWithTag("relay-portal-review").assertDoesNotExist();assertEquals(0,opened.size);return@use}
            tap("relay-list","relay-portal-review");compose.onNodeWithTag("relay-portal-confirm").assertIsNotEnabled()
            if(mode=="normal") {tap("relay-portal-review-list","relay-portal-approve");compose.onNodeWithTag("relay-portal-cancel").performClick();assertEquals(1,relay.get());tap("relay-list","relay-portal-review");compose.onNodeWithTag("relay-portal-confirm").assertIsNotEnabled()}
            compose.onNodeWithTag("relay-portal-review-list").performScrollToNode(hasTestTag("relay-reviewed-portal"));compose.onNodeWithTag("relay-reviewed-portal").assertIsDisplayed()
            if(bilingual) shot("host-relay-bilingual-review")
            if(mode=="owner") {compose.runOnUiThread {management.bind("unconfigured-relay-fixture")};compose.waitUntil(5000) {management.state.value.relay==null};compose.onNodeWithTag("relay-portal-confirm").assertDoesNotExist();assertEquals(1,relay.get());assertEquals(0,opened.size);return@use}
            tap("relay-portal-review-list","relay-portal-approve");compose.onNodeWithTag("relay-portal-confirm").assertIsEnabled()
            if(mode=="destination") changed.set(true)
            compose.onNodeWithTag("relay-portal-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && relay.get()==2}
            if(mode=="destination") {assertEquals(Words.ManagementReviewChanged,management.state.value.error);assertTrue(opened.isEmpty());assertNull(management.state.value.relay)} else {assertEquals(listOf(HostRelayFixture.portal),opened);compose.onNodeWithTag("relay-close").performClick();assertNull(management.state.value.relay)}
            assertEquals(0,writes.get())
        }
    }
    @Test fun localOpenPrivateRevealAndFreshPortalApprovalAfterCancel()=exercise("normal")
    @Test fun bilingualPinyinPortalReviewIsScrollableAtLargeFont()=exercise("bilingual")
    @Test fun unknownManagementAuthorityHasNoPortalHandoff()=exercise("unknown")
    @Test fun missingLaunchHomeProofStopsBeforePrivateAccounts()=exercise("scope")
    @Test fun changedOwnerDismissesTheOldPortalReview()=exercise("owner")
    @Test fun changedDestinationCannotOpenAfterApproval()=exercise("destination")
    @Test fun canceledReadReviewSendsNothingAndCanBeReviewedAgain()=exercise("cancel")
}
