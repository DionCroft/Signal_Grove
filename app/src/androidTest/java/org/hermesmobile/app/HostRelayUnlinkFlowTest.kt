package org.hermesmobile.app

import android.graphics.Bitmap
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

class HostRelayUnlinkFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun tap(list:String,tag:String) {compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()}
    private fun shot(name:String) {val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun exercise(mode:String) {
        val reads=AtomicInteger();val sends=AtomicInteger();val mcp=AtomicInteger();val tickets=AtomicInteger();val changed=AtomicBoolean(false);val badReadback=AtomicBoolean(mode=="readback")
        credentials.managementServer().use {server->
            fun json(raw:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(raw.toString())
            fun report():JsonObject {
                val raw=HostRelayFixture.report(if(mode=="portal") "portal" else if(mode=="unknown") "unknown" else "connector");val row=raw["platforms"]!!.jsonArray.single().jsonObject;val account=row["accounts"]!!.jsonArray.single().jsonObject
                val accounts=if(sends.get()==0 || mode in setOf("lost","still")) buildJsonArray {add(if(mode=="target" && changed.get()) JsonObject(account+mapOf("displayName" to JsonPrimitive("Changed private target"))) else account)} else JsonArray(emptyList())
                return JsonObject(raw+mapOf("platforms" to buildJsonArray {add(JsonObject(row+mapOf("accounts" to accounts)))}))
            }
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val url=q.requestUrl!!;if(url.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer unlink-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles(mode=="home" && changed.get()))
                    url.encodedPath=="/dashboard/api/mcp/servers"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    url.encodedPath=="/dashboard/api/auth/ws-ticket"->{tickets.incrementAndGet();MockResponse().setResponseCode(500)}
                    url.encodedPath=="/dashboard/api/messaging/platforms"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));json(HostRelayFixture.channels())}
                    url.encodedPath=="/dashboard/api/messaging/relay"->{assertEquals("GET",q.method);assertNull(url.query);reads.incrementAndGet();if(badReadback.get() && sends.get()>0) MockResponse().setResponseCode(500) else json(report())}
                    url.encodedPath=="/dashboard/api/messaging/relay/"+HostChannelsFixture.id+"/accounts/"+HostRelayFixture.account->{assertEquals("DELETE",q.method);assertNull(url.query);assertEquals(0L,q.bodySize);sends.incrementAndGet();if(mode=="lost") MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else json(buildJsonObject {put("unlinked",true)})}
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val bilingual=mode=="bilingual";compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(server.url("inference/").toString(),credentials.uniqueToken("unlink-ui-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=bilingual,pinyin=bilingual))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==bilingual && api.preferences.value.pinyin==bilingual}
            val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",server.url("dashboard/").toString().trimEnd('/'));put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("unlink-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(5000) {vm.state.value.signedIn};compose.runOnUiThread {vm.refresh()};compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,vm)}}}
            compose.onNodeWithTag("management-open").performClick();tap("management-list","host-relay-open");assertEquals(0,reads.get())
            fun read() {tap("relay-list","relay-read");compose.onNodeWithTag("relay-read-confirm").performClick();compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.relay!=null}}
            read();compose.onNodeWithText(HostRelayFixture.account,substring=true).assertDoesNotExist();tap("relay-list","relay-reveal")
            if(mode in setOf("portal","unknown")) {compose.onNodeWithTag("relay-unlink-0-0").assertDoesNotExist();val inventory=vm.state.value.relay!!;compose.runOnUiThread {assertNull(vm.prepareRelayUnlink(inventory,HostChannelsFixture.id,inventory.platforms.single().accounts.single()))};assertEquals(0,sends.get());return@use}
            tap("relay-list","relay-unlink-0-0");compose.onNodeWithTag("relay-unlink-confirm").assertIsNotEnabled();compose.onNodeWithTag("relay-unlink-target").assertDoesNotExist()
            if(mode=="normal") {tap("relay-unlink-review","relay-unlink-reveal");tap("relay-unlink-review","relay-unlink-approve");compose.onNodeWithTag("relay-unlink-cancel").performClick();assertEquals(0,sends.get());tap("relay-list","relay-unlink-0-0");compose.onNodeWithTag("relay-unlink-confirm").assertIsNotEnabled();tap("relay-unlink-review","relay-unlink-approve");compose.onNodeWithTag("relay-unlink-confirm").assertIsNotEnabled()}
            tap("relay-unlink-review","relay-unlink-reveal");compose.onNodeWithTag("relay-unlink-review").performScrollToNode(hasTestTag("relay-unlink-target"));compose.onNodeWithTag("relay-unlink-target").assertIsDisplayed();shot(if(bilingual) "relay-unlink-bilingual-review" else "relay-unlink-review")
            if(mode=="owner") {val inventory=vm.state.value.relay!!;val frozen=vm.prepareRelayUnlink(inventory,HostChannelsFixture.id,inventory.platforms.single().accounts.single())!!;val n=reads.get();compose.runOnUiThread {vm.bind("unconfigured-unlink-fixture");vm.unlinkRelay(frozen)};compose.waitUntil(5000) {vm.state.value.relay==null};compose.onNodeWithTag("relay-unlink-confirm").assertDoesNotExist();assertEquals(n,reads.get());assertEquals(0,sends.get());return@use}
            if(mode!="normal") tap("relay-unlink-review","relay-unlink-approve");compose.onNodeWithTag("relay-unlink-confirm").assertIsEnabled()
            if(mode in setOf("home","target")) changed.set(true)
            compose.onNodeWithTag("relay-unlink-confirm").performClick();compose.waitUntil(10000) {!vm.state.value.busy && (if(mode in setOf("home","target")) vm.state.value.error!=null else vm.receipts.value.any {it.action=="RelayUnlink" && it.state==if(mode=="lost") "unknown" else "acknowledged"})}
            if(mode in setOf("home","target")) {assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,sends.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first()}.none {it.action=="RelayUnlink"});return@use}
            assertEquals(1,sends.get());val receipt=vm.receipts.value.single {it.action=="RelayUnlink"};assertEquals(if(mode=="lost") "unknown" else "acknowledged",receipt.state)
            if(mode=="readback") {assertEquals(Words.ManagementReadbackPending,vm.state.value.error);assertNull(vm.state.value.relay);badReadback.set(false)} else if(mode=="still") assertEquals(Words.RelayUnlinkStillListed,vm.state.value.notice) else if(mode!="lost") assertEquals(Words.RelayUnlinkAbsent,vm.state.value.notice)
            for(secret in listOf(HostRelayFixture.account,HostRelayFixture.name,HostRelayFixture.handle)) {assertFalse(receipt.requestSummary.contains(secret));assertFalse(receipt.resultSummary.contains(secret))}
            compose.onNodeWithTag("relay-close").performClick();val before=reads.get();val mcpBefore=mcp.get();tap("management-list","management-observe-"+receipt.key);compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.relayTarget!=null};assertEquals(before,reads.get());assertEquals(mcpBefore,mcp.get());compose.onNodeWithText(HostRelayFixture.account,substring=true).assertDoesNotExist();tap("relay-list","relay-original-reveal");compose.onNodeWithTag("relay-list").performScrollToNode(hasText(Words.RelayAccountId.localized(api.preferences.value)+": "+HostRelayFixture.account));compose.onNodeWithText(Words.RelayAccountId.localized(api.preferences.value)+": "+HostRelayFixture.account).assertIsDisplayed()
            read();assertEquals(before+1,reads.get());assertEquals(1,sends.get());assertEquals(0,tickets.get())
            if(mode=="lost") {
                tap("relay-list","relay-reveal");compose.onNodeWithTag("relay-list").performScrollToNode(hasTestTag("relay-unlink-0-0"));compose.onNodeWithTag("relay-unlink-0-0").assertIsNotEnabled();tap("relay-list","relay-clear-private");compose.onNodeWithTag("relay-clear-confirm").assertIsNotEnabled();tap("relay-clear-review","relay-clear-approve");compose.onNodeWithTag("relay-clear-confirm").assertIsEnabled().performClick();compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.notice==Words.RelayPrivateCleared};assertEquals("unknown",runBlocking {app.database.dao().managementOperation(receipt.key)}!!.state)
                compose.onNodeWithTag("relay-close").performClick();tap("management-list","management-observe-"+receipt.key);compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.relayTarget!=null};assertNull(vm.state.value.relayTarget!!.account);compose.onNodeWithTag("relay-list").performScrollToNode(hasText(Words.RelayOriginalUnavailable.en));compose.onNodeWithText(Words.RelayOriginalUnavailable.en).assertIsDisplayed();assertEquals(1,sends.get())
            } else {
                compose.onNodeWithTag("relay-close").performClick();tap("management-list","management-result-"+receipt.key);compose.onNodeWithText(Words.RelayUnlinkAcknowledged.localized(api.preferences.value)).assertExists();compose.onNodeWithText(Words.RelayUnlinkAbsent.localized(api.preferences.value)).assertDoesNotExist()
            }
        }
    }
    @Test fun exactPrivateUnlinkNeedsFreshRevealAndGrantAfterCancelAndReceiptObservationIsLocal()=exercise("normal")
    @Test fun bilingualPinyinPrivateTargetAndApprovalRemainReachable()=exercise("bilingual")
    @Test fun portalManagedAccountsNeverExposeConnectorUnlink()=exercise("portal")
    @Test fun unknownAuthorityAccountsNeverExposeConnectorUnlink()=exercise("unknown")
    @Test fun changedAccountMetadataRefusesBeforeClaim()=exercise("target")
    @Test fun changedHomeRefusesBeforeClaim()=exercise("home")
    @Test fun changedOwnerCannotUseAnOldPrivateReview()=exercise("owner")
    @Test fun lostReplyBlocksRepeatsAndPrivateClearDoesNotClearThePendingReceipt()=exercise("lost")
    @Test fun failedReadbackKeepsAckAndResultDoesNotClaimCurrentAbsence()=exercise("readback")
    @Test fun stillListedReadbackDoesNotAutomaticallyRepeatUnlink()=exercise("still")
}
