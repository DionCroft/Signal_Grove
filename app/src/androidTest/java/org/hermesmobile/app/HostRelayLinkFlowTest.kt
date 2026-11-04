package org.hermesmobile.app

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

class HostRelayLinkFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun tap(list:String,tag:String) {compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()}
    private fun shot(name:String) {val f=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use {requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun exercise(mode:String) {
        val sends=AtomicInteger();val reads=AtomicInteger();val states=AtomicInteger();val mcp=AtomicInteger();val changed=AtomicBoolean(false);val badReadback=AtomicBoolean(mode=="readback");val inactive=AtomicBoolean(false);val opened=mutableListOf<String>()
        var oldClip:ClipData?=null;lateinit var clipboard:ClipboardManager
        compose.runOnUiThread {clipboard=requireNotNull(app.getSystemService(ClipboardManager::class.java));oldClip=clipboard.primaryClip}
        try {credentials.managementServer().use {server->
            fun json(raw:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(raw.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val url=q.requestUrl!!;if(url.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer link-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles(mode=="home" && changed.get()))
                    url.encodedPath=="/dashboard/api/mcp/servers"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    url.encodedPath=="/dashboard/api/messaging/platforms"->{assertEquals("worker",url.queryParameter("profile"));json(HostRelayLinkFixture.channels())}
                    url.encodedPath=="/dashboard/api/messaging/relay"->{assertEquals("GET",q.method);assertNull(url.query);reads.incrementAndGet();if(badReadback.get() && sends.get()>0) MockResponse().setResponseCode(500) else json(HostRelayLinkFixture.report(if(mode=="portal" && sends.get()>0) "portal" else "connector"))}
                    url.encodedPath=="/dashboard/api/messaging/relay/telegram/link"->{assertEquals("POST",q.method);assertNull(url.query);assertEquals("{}",q.body.readUtf8());sends.incrementAndGet();when(mode) {"lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);"portal"->json(buildJsonObject {put("detail",buildJsonObject {put("code","managed_by_portal")})}).setResponseCode(409);"optional"->json(JsonObject(HostRelayLinkFixture.issued()+mapOf("expires_at" to buildJsonObject {put("bad",true)},"bot_url" to JsonPrimitive("https://evil.test/?code=AB12CD"))));else->json(HostRelayLinkFixture.issued())}}
                    url.encodedPath=="/dashboard/api/messaging/relay/link/"+HostRelayLinkFixture.code->{assertEquals("GET",q.method);assertNull(url.query);states.incrementAndGet();json(HostRelayLinkFixture.state(if(inactive.get()) "inactive" else "pending"))}
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val bilingual=mode=="bilingual";compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(server.url("inference/").toString(),credentials.uniqueToken("link-ui-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=bilingual,pinyin=bilingual))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==bilingual && api.preferences.value.pinyin==bilingual}
            val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",server.url("dashboard/").toString().trimEnd('/'));put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("link-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(5000) {vm.state.value.signedIn};compose.runOnUiThread {vm.refresh()};compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();val s by vm.state.collectAsStateWithLifecycle();HermesAppTheme(p) {if(mode=="browser") HostRelayDialog(vm,s,openCodeBot={opened.add(it)}) {vm.closeHostRelay()} else ManagementEntry(api,vm)}}}
            if(mode!="browser") {compose.onNodeWithTag("management-open").performClick();tap("management-list","host-relay-open")}
            fun read() {tap("relay-list","relay-read");compose.onNodeWithTag("relay-read-confirm").performClick();compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.relay!=null}}
            read();tap("relay-list","relay-reveal");tap("relay-list","relay-code-create-0");compose.onNodeWithTag("relay-code-create-confirm").assertIsNotEnabled()
            if(mode=="normal") {tap("relay-code-create-review","relay-code-create-approve");compose.onNodeWithTag("relay-code-create-cancel").performClick();assertEquals(0,sends.get());tap("relay-list","relay-code-create-0");compose.onNodeWithTag("relay-code-create-confirm").assertIsNotEnabled()}
            if(mode=="owner") {val inventory=vm.state.value.relay!!;val frozen=vm.prepareRelayLink(inventory,"telegram")!!;compose.runOnUiThread {vm.bind("unconfigured-link-fixture");vm.createRelayLink(frozen)};compose.waitUntil(5000) {vm.state.value.relay==null};compose.onNodeWithTag("relay-code-create-confirm").assertDoesNotExist();assertEquals(0,sends.get());return@use}
            tap("relay-code-create-review","relay-code-create-approve");if(bilingual) shot("relay-code-bilingual-approval");if(mode=="home") changed.set(true);compose.onNodeWithTag("relay-code-create-confirm").assertIsEnabled().performClick()
            compose.waitUntil(10000) {!vm.state.value.busy && (vm.state.value.relayLink!=null || vm.state.value.error!=null)}
            if(mode=="home") {assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,sends.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first()}.none {it.action=="RelayLinkStart"});return@use}
            compose.waitUntil(10000) {vm.receipts.value.any {it.action=="RelayLinkStart" && it.state==if(mode=="lost") "unknown" else "acknowledged"}}
            val receipt=vm.receipts.value.single {it.action=="RelayLinkStart"};assertEquals(1,sends.get());assertEquals(0,states.get())
            if(mode=="lost") {assertEquals("unknown",receipt.state);read();tap("relay-list","relay-reveal");compose.onNodeWithTag("relay-list").performScrollToNode(hasTestTag("relay-code-create-0"));compose.onNodeWithTag("relay-code-create-0").assertIsNotEnabled();assertEquals(1,sends.get());return@use}
            if(mode=="portal") {assertFalse(vm.state.value.relayLink!!.binding.issued);compose.onNodeWithTag("relay-code-command").assertDoesNotExist();compose.onNodeWithTag("relay-code-refresh").assertDoesNotExist();compose.onNodeWithTag("relay-code-close").performClick();read();tap("relay-list","relay-reveal");compose.onNodeWithTag("relay-code-create-0").assertDoesNotExist();return@use}
            if(mode=="readback") {assertEquals(Words.ManagementReadbackPending,vm.state.value.error);badReadback.set(false)}
            else {
                compose.onNodeWithTag("relay-code-command").assertDoesNotExist();tap("relay-code-flow","relay-code-reveal");compose.onNodeWithTag("relay-code-flow").performScrollToNode(hasTestTag("relay-code-command"));compose.onNodeWithTag("relay-code-command").assert(hasText("/link "+HostRelayLinkFixture.code));if(bilingual) shot("relay-code-bilingual-private")
                if(mode=="browser") {tap("relay-code-flow","relay-code-browser-review");compose.onNodeWithTag("relay-code-browser-confirm").assertIsNotEnabled();tap("relay-code-browser-review-list","relay-code-browser-approve");compose.onNodeWithTag("relay-code-browser-confirm").assertIsEnabled().performClick();compose.waitUntil(10000) {!vm.state.value.busy && opened.size==1};assertEquals(listOf(HostRelayLinkFixture.bot),opened);assertEquals(1,states.get());assertEquals(1,sends.get());return@use}
                if(mode=="foreground") {val flow=vm.state.value.relayLink!!;compose.runOnUiThread {vm.shareRelayCode(flow,false,{false}) {opened.add(it)}};compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.error==Words.RelayCodeForeground};assertTrue(opened.isEmpty());assertEquals(1,states.get());return@use}
                if(mode=="copy-inactive") {compose.runOnUiThread {clipboard.setPrimaryClip(ClipData.newPlainText("fixture","unchanged-clipboard-fixture"))};inactive.set(true)}
                if(mode=="optional") {assertNull(vm.state.value.relayLink!!.code!!.botUrl);compose.onNodeWithTag("relay-code-flow").performScrollToNode(hasTestTag("relay-code-browser-review"));compose.onNodeWithTag("relay-code-browser-review").assertIsNotEnabled()}
                tap("relay-code-flow","relay-code-copy");compose.waitUntil(10000) {!vm.state.value.busy && states.get()==1}
                if(mode=="copy-inactive") {assertEquals("inactive",vm.state.value.relayLink!!.binding.status);assertNull(vm.state.value.relayLink!!.code);compose.runOnUiThread {assertEquals("unchanged-clipboard-fixture",clipboard.primaryClip!!.getItemAt(0).text.toString())};assertEquals(1,sends.get());return@use}
                compose.runOnUiThread {assertEquals("/link "+HostRelayLinkFixture.code,clipboard.primaryClip!!.getItemAt(0).text.toString());assertTrue(clipboard.primaryClip!!.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))}
                compose.onNodeWithTag("relay-code-close").performClick()
            }
            if(mode=="clear") {tap("relay-list","relay-clear-codes");compose.onNodeWithTag("relay-codes-clear-confirm").assertIsNotEnabled();tap("relay-codes-clear-review","relay-codes-clear-approve");compose.onNodeWithTag("relay-codes-clear-confirm").assertIsEnabled().performClick();compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.notice==Words.RelayCodesCleared}}
            compose.onNodeWithTag("relay-close").performClick();val before=reads.get();val stateBefore=states.get();val mcpBefore=mcp.get();tap("management-list","management-observe-"+receipt.key);compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.relayLink!=null};assertEquals(before,reads.get());assertEquals(stateBefore,states.get());assertEquals(mcpBefore,mcp.get());assertNull(vm.state.value.relayLink!!.code);assertFalse(vm.state.value.relayLink!!.scopeVerified)
            if(mode=="clear") {assertFalse(vm.state.value.relayLink!!.available);compose.onNodeWithTag("relay-code-flow").performScrollToNode(hasTestTag("relay-code-refresh"));compose.onNodeWithTag("relay-code-refresh").assertIsNotEnabled();assertEquals("pending",HostRelayLinkBinding.parse(runBlocking {app.database.dao().managementOperation(receipt.key)}!!,vm.reviewTarget()!!).status)}
            else {tap("relay-code-flow","relay-code-refresh");compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.relayLink?.code!=null};assertEquals(stateBefore+1,states.get());compose.onNodeWithTag("relay-code-command").assertDoesNotExist();tap("relay-code-flow","relay-code-reveal");compose.onNodeWithTag("relay-code-flow").performScrollToNode(hasTestTag("relay-code-command"));compose.onNodeWithTag("relay-code-command").assert(hasText("/link "+HostRelayLinkFixture.code))}
            assertEquals(1,sends.get());val final=runBlocking {app.database.dao().managementOperation(receipt.key)}!!;for(secret in listOf(HostRelayLinkFixture.code,"/link",HostRelayLinkFixture.bot,HostRelayLinkFixture.reason)) {assertFalse(final.requestSummary.contains(secret));assertFalse(final.resultSummary.contains(secret))}
        }} finally {compose.runOnUiThread {if(oldClip!=null) clipboard.setPrimaryClip(oldClip!!) else if(Build.VERSION.SDK_INT>=28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("",""))}}
    }
    @Test fun creationNeedsFreshApprovalAfterCancelAndPrivateCopyResumesOnlyOriginalReceipt()=exercise("normal")
    @Test fun bilingualPinyinCodeApprovalAndPrivateCommandRemainReachable()=exercise("bilingual")
    @Test fun explicitBotDestinationReviewRechecksCodeBeforeOpeningOnlyReviewedUrl()=exercise("browser")
    @Test fun newlyInactiveCodeCannotReachClipboardOrBecomeConfirmedLink()=exercise("copy-inactive")
    @Test fun aBackgroundHandoffCannotShareEvenAfterPendingCheck()=exercise("foreground")
    @Test fun lostCreationBlocksNewCodesWhileReviewedReadsDoNotMintAgain()=exercise("lost")
    @Test fun typedPortalPreMintRefusalDoesNotClaimCodeIssuance()=exercise("portal")
    @Test fun invalidOptionalBotAndExpiryKeepCodeWithManualCopyOnly()=exercise("optional")
    @Test fun failedReadbackRetainsAckAndOriginalCodeCanBeVerifiedAfterExplicitResume()=exercise("readback")
    @Test fun changedHomeRefusesBeforeCreatingAReceiptOrCode()=exercise("home")
    @Test fun changedOwnerCannotReuseOldCreationReview()=exercise("owner")
    @Test fun clearLocalCodeCopiesKeepsIssuanceReceiptAndDoesNotCancelOrMint()=exercise("clear")
}
