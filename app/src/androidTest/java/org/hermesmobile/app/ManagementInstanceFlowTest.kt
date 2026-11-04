package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ManagementInstanceFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String) {
        val writes=AtomicInteger();val sending=mode=="sending";val ordinary=mode=="ordinary";val same=mode in setOf("same","raw");val bilingual=mode=="bilingual"
        credentials.managementServer().use {server->
            fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                if(q.method!="GET") writes.incrementAndGet()
                return when {
                    q.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    q.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    q.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password"}""")
                    q.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"},{"name":"renamed"}]}""")
                    q.path.orEmpty().startsWith("/dashboard/api/mcp/servers?profile=")->json("""{"servers":[]}""")
                    else->MockResponse().setResponseCode(404)
                }
            }}
            compose.waitUntil(10000) {!api.connecting.value}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("instance-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=bilingual,pinyin=bilingual))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==bilingual && api.preferences.value.pinyin==bilingual}
            val scope=api.profileScope.value;val removed=ServerConfig(inference,credentials.uniqueToken("removed-instance-inference"),true).scope
            assertNull(app.credentials.profile(removed))
            val owner=ManagementTarget(if(same) scope else removed,dashboard,"private-deleted-profile","password · private-other-actor")
            val row=ManagementOperation(UUID.randomUUID().toString(),owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,if(ordinary) "Probe" else if(same) "RelayUnlink" else "RelayLinkStart","private-other-target","private-other-request",if(sending) "sending" else "unknown","private-other-result",123456789)
            runBlocking {app.managementReady.await();app.database.dao().insertManagementOperation(row)}
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard.trimEnd('/'));put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("instance-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(5000) {vm.state.value.signedIn}
            compose.runOnUiThread {vm.refresh()};compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.loaded && (ordinary || vm.receipts.value.any {it.key==row.key})}
            try {
                assertFalse(vm.receipts.value.toString().contains("private-other-"));assertFalse(vm.receipts.value.toString().contains(owner.profile))
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();val s by vm.state.collectAsStateWithLifecycle();HermesAppTheme(p) {if(mode=="raw") ManagementReceiptCard(row,vm,s) else ManagementEntry(api,vm)}}}
                fun privateHidden() {
                    for(value in listOf("private-other-",owner.profile)) compose.onNodeWithText(value,substring=true,useUnmergedTree=true).assertDoesNotExist()
                    compose.onNodeWithTag("management-result-"+row.key).assertDoesNotExist();compose.onNodeWithTag("management-observe-"+row.key).assertDoesNotExist()
                }
                if(mode=="raw") {compose.onNodeWithTag("management-shared-"+row.key).assertIsDisplayed();privateHidden();return@use}
                compose.onNodeWithTag("management-open").performClick()
                fun tap(list:String,tag:String) {compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
                compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-add"))
                if(ordinary) {compose.onNodeWithTag("mcp-add").assertIsEnabled();assertTrue(vm.visibleReceipts(vm.state.value,vm.receipts.value).none {it.key==row.key});assertEquals("unknown",runBlocking {app.database.dao().managementOperation(row.key)!!.state});return@use}
                compose.onNodeWithTag("mcp-add").assertIsNotEnabled();privateHidden()
                if(sending) {compose.onNodeWithTag("management-shared-review-"+row.key).assertDoesNotExist();assertEquals("sending",runBlocking {app.database.dao().managementOperation(row.key)!!.state});return@use}
                fun review() {tap("management-list","management-shared-review-"+row.key);compose.onNodeWithTag("management-shared-review-confirm").assertIsNotEnabled()}
                review()
                val before=server.requestCount
                tap("management-shared-review","management-instance-approve");compose.onNodeWithTag("management-shared-review-confirm").assertIsEnabled()
                compose.onNodeWithText(Words.Cancel.localized(api.preferences.value)).performClick()
                assertEquals("unknown",runBlocking {app.database.dao().managementOperation(row.key)!!.state});assertEquals(before,server.requestCount)
                review();compose.onNodeWithTag("management-shared-review").performScrollToNode(hasTestTag("management-instance-approve"));compose.onNodeWithTag("management-instance-approve").assertIsOff()
                if(mode=="stale") {
                    val expected=vm.reviewTarget()!!
                    compose.runOnUiThread {vm.chooseProfile("renamed")};compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.profile=="renamed"}
                    compose.onNodeWithTag("management-shared-review").assertDoesNotExist()
                    compose.runOnUiThread {vm.reviewReceipt(row.key,expected)}
                    assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals("unknown",runBlocking {app.database.dao().managementOperation(row.key)!!.state})
                    review()
                }
                tap("management-shared-review","management-instance-approve");compose.onNodeWithTag("management-shared-review-confirm").assertIsEnabled();compose.waitForIdle();privateHidden()
                if(bilingual) {
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/instance-relay-bilingual-review.png");shot.parentFile!!.mkdirs()
                    val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());shot.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                }
                val reviewRequests=server.requestCount
                compose.onNodeWithTag("management-shared-review-confirm").performClick()
                compose.waitUntil(10000) {!vm.state.value.busy && vm.receipts.value.none {it.key==row.key}}
                assertEquals(reviewRequests,server.requestCount);assertEquals(0,writes.get())
                assertEquals(row.copy(state="reviewed"),runBlocking {app.database.dao().managementOperation(row.key)!!})
                compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-add"));compose.onNodeWithTag("mcp-add").assertIsEnabled();privateHidden()
            } finally {compose.runOnUiThread {vm.cancel()};assertEquals(0,writes.get())}
        }
    }
    @Test fun removedCredentialAndDeletedProfilesRelayUnknownRequiresFreshLocalGrant()=exercise("foreign")
    @Test fun anotherProfileUnderSameCredentialKeepsPrivateRelayDetailsHidden()=exercise("same")
    @Test fun sendingOnAnotherProfileHasNoClearAction()=exercise("sending")
    @Test fun bilingualInstanceReviewScrollsToFreshGrantAndConfirmation()=exercise("bilingual")
    @Test fun switchingProfilesInvalidatesTheFrozenLocalReview()=exercise("stale")
    @Test fun ordinaryOtherProfilePendingDoesNotBlockTheCurrentProfile()=exercise("ordinary")
    @Test fun staleRawSameCredentialOtherProfileReceiptCannotExposeDetails()=exercise("raw")
}
