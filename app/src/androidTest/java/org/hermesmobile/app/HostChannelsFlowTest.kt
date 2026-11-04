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

class HostChannelsFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun scroll(list:String,tag:String) {compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()}
    private fun tap(list:String,tag:String) {scroll(list,tag);compose.onNodeWithTag(tag).assertIsEnabled().performClick()}
    private fun shot(name:String) {val file=File(compose.activity.getExternalFilesDir(null),"qa/"+name+".png");file.parentFile!!.mkdirs();file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun exercise(mode:String) {
        val reads=AtomicInteger();val writes=AtomicInteger();val postures=AtomicInteger();val mcp=AtomicInteger();val tickets=AtomicInteger();val homeChanged=AtomicBoolean(false);val badReadback=AtomicBoolean(mode=="readback")
        credentials.managementServer().use {server->
            fun json(value:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val url=q.requestUrl!!
                if(url.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer channel-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles(homeChanged.get()))
                    url.encodedPath=="/dashboard/api/mcp/servers"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    url.encodedPath=="/dashboard/api/auth/ws-ticket"->{tickets.incrementAndGet();MockResponse().setResponseCode(500)}
                    url.encodedPath=="/dashboard/api/messaging/platforms"->{
                        assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));reads.incrementAndGet()
                        json(if(badReadback.get() && writes.get()>0) buildJsonObject {put("platforms",17)} else HostChannelsFixture.catalog(enabled=writes.get()>0,allowSet=writes.get()==0))
                    }
                    url.encodedPath=="/dashboard/api/messaging/platforms/"+HostChannelsFixture.id->{
                        assertEquals("PUT",q.method);assertEquals("worker",url.queryParameter("profile"));val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                        assertEquals(buildJsonObject {put("profile","worker");put("enabled",true);put("env",buildJsonObject {put(HostChannelsFixture.key,HostChannelsFixture.secret)});put("clear_env",buildJsonArray {add(HostChannelsFixture.listKey)})},body)
                        assertFalse(body.toString().contains(HostChannelsFixture.stored));writes.incrementAndGet()
                        if(mode=="lost") MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else json(HostChannelsFixture.ack(true))
                    }
                    url.encodedPath=="/dashboard/api/messaging/platforms/"+HostChannelsFixture.id+"/test"->{
                        assertEquals("POST",q.method);assertEquals("worker",url.queryParameter("profile"));assertEquals("{}",q.body.readUtf8());postures.incrementAndGet()
                        json(if(mode=="optional") JsonObject(HostChannelsFixture.posture()+mapOf("message" to buildJsonObject {put("invalid",true)})) else HostChannelsFixture.posture())
                    }
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString();val bilingual=mode=="bilingual"
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("channel-ui-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=bilingual,pinyin=bilingual))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==bilingual && api.preferences.value.pinyin==bilingual}
            val scope=api.profileScope.value
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("channel-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick();tap("management-list","host-channels-open");assertEquals(0,reads.get())
            fun read() {tap("host-channels-list","channels-read");compose.onNodeWithTag("channels-read-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.channels!=null}}
            read();assertEquals(1,reads.get());compose.onNodeWithText(HostChannelsFixture.users,substring=true).assertDoesNotExist();compose.onNodeWithText(HostChannelsFixture.stored,substring=true).assertDoesNotExist()
            if(mode in setOf("negative","optional")) {
                tap("host-channels-list","channels-posture-"+HostChannelsFixture.id);compose.onNodeWithTag("channels-confirm").assertIsNotEnabled()
                tap("channels-review","channels-review-reveal");tap("channels-review","channels-approve");compose.onNodeWithTag("channels-confirm").assertIsEnabled().performClick()
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.channelPosture!=null}
                assertEquals(1,postures.get());assertEquals(0,writes.get());assertFalse(management.state.value.channelPosture!!.ack.ok)
                tap("host-channels-list","channels-report-open");compose.onNodeWithText(HostChannelsFixture.message,substring=true).assertDoesNotExist();tap("channels-report","channels-report-reveal")
                if(mode=="optional") {assertNull(management.state.value.channelPosture!!.details);compose.onNodeWithTag("channels-report").performScrollToNode(hasText(Words.ChannelsDetailsUnavailable.en));compose.onNodeWithText(Words.ChannelsDetailsUnavailable.en).assertIsDisplayed()}
                else {compose.onNodeWithTag("channels-report").performScrollToNode(hasText(HostChannelsFixture.message));compose.onNodeWithText(HostChannelsFixture.message).assertIsDisplayed();shot("host-channels-posture-report")}
                compose.onNodeWithTag("channels-report-close").performClick();val receipt=management.receipts.value.single {it.action=="ChannelPosture"};assertEquals("acknowledged",receipt.state)
                compose.onNodeWithTag("channels-close").performClick();val before=reads.get();val mcpBefore=mcp.get();tap("management-list","management-observe-"+receipt.key);assertEquals(before,reads.get());read();assertEquals(before+1,reads.get());assertEquals(mcpBefore,mcp.get());assertEquals(1,postures.get());assertEquals(0,tickets.get());return@use
            }
            fun draft() {
                tap("host-channels-list","channels-edit-"+HostChannelsFixture.id);tap("channels-editor","channels-editor-reveal");tap("channels-editor","channels-enable-false");compose.onNodeWithTag("channels-review-open").assertIsNotEnabled()
                tap("channels-editor","channels-enable-true");tap("channels-editor","channels-field-"+HostChannelsFixture.key+"-Replace");scroll("channels-editor","channels-value-"+HostChannelsFixture.key)
                compose.onNodeWithTag("channels-value-"+HostChannelsFixture.key).assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText,AnnotatedString("")))
                compose.onNodeWithTag("channels-value-"+HostChannelsFixture.key).performTextReplacement(HostChannelsFixture.secret);closeDialogKeyboard()
                tap("channels-editor","channels-field-"+HostChannelsFixture.listKey+"-Clear");compose.onNodeWithTag("channels-review-open").assertIsEnabled().performClick();compose.onNodeWithTag("channels-confirm").assertIsNotEnabled()
            }
            draft()
            if(mode=="normal") {
                tap("channels-review","channels-approve");compose.onNodeWithTag("channels-confirm").assertIsNotEnabled();tap("channels-review","channels-review-reveal");compose.onNodeWithTag("channels-confirm").assertIsEnabled();compose.onNodeWithTag("channels-review-cancel").performClick();assertEquals(0,writes.get());draft()
            }
            tap("channels-review","channels-review-reveal");scroll("channels-review","channels-reviewed-value-"+HostChannelsFixture.key);compose.onNodeWithTag("channels-reviewed-value-"+HostChannelsFixture.key).assert(hasText(HostChannelsFixture.secret));shot(if(bilingual) "host-channels-bilingual-review" else "host-channels-save-review")
            tap("channels-review","channels-approve");compose.onNodeWithTag("channels-confirm").assertIsEnabled()
            if(mode=="owner") {compose.runOnUiThread {management.bind("unconfigured-channel-fixture")};compose.waitUntil(5000) {management.state.value.channels==null};compose.onNodeWithTag("channels-confirm").assertDoesNotExist();assertEquals(0,writes.get());return@use}
            if(mode=="home") homeChanged.set(true)
            compose.onNodeWithTag("channels-confirm").performClick()
            if(mode=="home") {compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertEquals(0,writes.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first()}.none {it.action=="ChannelSave"});return@use}
            if(mode=="lost") {
                compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="ChannelSave" && it.state=="unknown"}}
                assertEquals(1,writes.get());read();assertEquals(1,writes.get());scroll("host-channels-list","channels-edit-"+HostChannelsFixture.id);compose.onNodeWithTag("channels-edit-"+HostChannelsFixture.id).assertIsNotEnabled();compose.onNodeWithTag("channels-posture-"+HostChannelsFixture.id).assertIsNotEnabled();assertEquals(0,postures.get());return@use
            }
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="ChannelSave" && it.state=="acknowledged"}}
            assertEquals(1,writes.get());val receipt=management.receipts.value.single {it.action=="ChannelSave"}
            if(mode=="readback") {assertEquals(Words.ManagementReadbackPending,management.state.value.error);assertNull(management.state.value.channels);badReadback.set(false);read();assertEquals(1,writes.get())} else {assertTrue(management.state.value.channels!!.rows.single().enabled);assertFalse(management.state.value.channels!!.rows.single().fields.last().isSet);assertEquals(Words.ChannelsHotServed,management.state.value.notice)}
            for(secret in listOf(HostChannelsFixture.secret,HostChannelsFixture.stored,HostChannelsFixture.users,"private-channel-chat","private-channel-scope")) {assertFalse(receipt.requestSummary.contains(secret));assertFalse(receipt.resultSummary.contains(secret))}
            compose.onNodeWithTag("channels-close").performClick();val before=reads.get();val mcpBefore=mcp.get();tap("management-list","management-observe-"+receipt.key);assertEquals(before,reads.get());read();assertEquals(before+1,reads.get());assertEquals(1,writes.get());assertEquals(mcpBefore,mcp.get());assertEquals(0,postures.get());assertEquals(0,tickets.get())
            if(mode=="readback") {compose.onNodeWithTag("channels-close").performClick();tap("management-list","management-result-"+receipt.key);compose.onNodeWithText(Words.ChannelsReceiptAck.en).assertExists();compose.onNodeWithText(Words.ChannelsAcknowledged.en).assertDoesNotExist()}
        }
    }
    @Test fun openingIsLocalAndExactSaveNeedsFreshPrivateReviewAfterCancel()=exercise("normal")
    @Test fun bilingualPinyinReviewKeepsPrivateFieldsAndApprovalReachable()=exercise("bilingual")
    @Test fun negativePostureHasSeparateApprovalAndPrivateReportWithoutResubmission()=exercise("negative")
    @Test fun malformedOptionalDetailsRetainPostureAcknowledgement()=exercise("optional")
    @Test fun changedProfileHomeRefusesBeforeClaimAndSave()=exercise("home")
    @Test fun oldConnectionReviewCannotSendTheNewCredentialAfterOwnerSwitch()=exercise("owner")
    @Test fun lostSaveBlocksEditsWhileReviewedReadsNeverReplayIt()=exercise("lost")
    @Test fun failedReadbackKeepsAckAndReceiptDoesNotClaimObservedSettings()=exercise("readback")
}
