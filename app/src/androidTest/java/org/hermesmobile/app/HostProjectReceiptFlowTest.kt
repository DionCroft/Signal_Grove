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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class HostProjectReceiptFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication

    private fun exercise(mode:String) {
        val own=mode in setOf("own","old-home","result")
        val bilingual=mode=="bilingual"
        val home=AtomicReference("/srv/project-worker")
        val requests=CopyOnWriteArrayList<Pair<String,String>>()
        credentials.managementServer().use {server->
            fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                requests.add(q.method.orEmpty() to q.path.orEmpty())
                return when {
                    q.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    q.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    q.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password"}""")
                    q.path=="/dashboard/api/profiles"->json(buildJsonObject {put("profiles",buildJsonArray {
                        add(buildJsonObject {put("name","worker");put("path",home.get())})
                        add(buildJsonObject {put("name","mirror");put("path",home.get())})
                    })}.toString())
                    q.path.orEmpty().startsWith("/dashboard/api/mcp/servers?profile=")->json("""{"servers":[]}""")
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val original=api.preferences.value
            compose.waitUntil(10000) {!api.connecting.value}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("project-receipt-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=bilingual,pinyin=bilingual,theme="STARHAVEN"))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==bilingual && api.preferences.value.pinyin==bilingual}
            val scope=api.profileScope.value
            val absent=ServerConfig(inference,credentials.uniqueToken("removed-project-owner"),true).scope
            assertNull(app.credentials.profile(absent))
            val owner=ManagementTarget(if(own || mode=="same") scope else absent,dashboard,if(own || mode=="old-home-foreign") "worker" else "private-original-profile",if(own) "password · fixture-user" else "password · private-original-actor")
            val row=ManagementOperation(UUID.randomUUID().toString(),owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,"ProjectUpdate","9".repeat(64),"private-original-request",if(mode=="sending") "sending" else if(mode=="result") "acknowledged" else "unknown",if(mode=="result") """{"host_acknowledged":true,"requested_effect_reported":false,"configuration_observed":false,"path_mapping_unverified":true}""" else "private-original-result",123456789,PluginHome.project(JsonPrimitive(if(mode in setOf("old-home","old-home-foreign")) "/srv/old-project-home" else home.get()))!!.identity())
            runBlocking {app.managementReady.await();app.database.dao().insertManagementOperation(row)}
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("project-receipt-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(5000) {vm.state.value.signedIn}
            compose.runOnUiThread {vm.refresh()};compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.loaded && vm.receipts.value.any {it.key==row.key}}
            try {
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();val s by vm.state.collectAsStateWithLifecycle();HermesAppTheme(p) {if(mode=="raw") ManagementReceiptCard(row,vm,s) else ManagementEntry(api,vm)}}}
                fun privateHidden() {
                    for(value in listOf("private-original-",row.target)) compose.onNodeWithText(value,substring=true,useUnmergedTree=true).assertDoesNotExist()
                }
                if(mode=="raw") {
                    compose.onNodeWithTag("management-shared-"+row.key).assertIsDisplayed();privateHidden()
                    home.set("/srv/different-project-store");compose.runOnUiThread {vm.refresh()}
                    compose.waitUntil(10000) {!vm.state.value.busy && vm.receipts.value.none {it.key==row.key}}
                    compose.onNodeWithTag("management-shared-"+row.key).assertDoesNotExist();privateHidden()
                    assertTrue(vm.visibleReceipts(vm.state.value,listOf(row)).isEmpty());return@use
                }
                compose.onNodeWithTag("management-open").performClick()
                fun tap(list:String,tag:String) {compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
                val reviewTag=if(own) "management-review-"+row.key else "management-shared-review-"+row.key
                if(mode=="result") {
                    tap("management-list","management-result-"+row.key)
                    compose.onNodeWithTag("management-result-list").performScrollToNode(hasText(Words.ProjectEffectUnreported.en,substring=true));compose.onNodeWithText(Words.ProjectEffectUnreported.en).assertIsDisplayed()
                    compose.onNodeWithTag("management-result-list").performScrollToNode(hasText(Words.ProjectConfigurationUnchecked.en,substring=true));compose.onNodeWithText(Words.ProjectConfigurationUnchecked.en).assertIsDisplayed()
                    compose.onNodeWithTag("management-result-close").performClick()
                    val before=requests.size
                    tap("management-list","management-observe-"+row.key)
                    compose.onNodeWithTag("host-projects-read").assertIsDisplayed()
                    assertEquals(before,requests.size);assertNull(vm.state.value.projects.inventory);privateHidden();return@use
                }
                if(mode in setOf("old-home","old-home-foreign")) {
                    compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag(reviewTag));compose.onNodeWithTag(reviewTag).assertIsNotEnabled()
                    assertNull(vm.prepareManagementUncertainty(row.key));val before=requests.size
                    compose.runOnUiThread {vm.reviewReceipt(row.key,vm.reviewTarget()!!,row.projectHome)}
                    assertEquals(before,requests.size);assertEquals(Words.ManagementReviewChanged,vm.state.value.error)
                    compose.onNodeWithTag("management-list").performScrollToNode(hasText(Words.ProjectReceiptLocationChanged.en,substring=true));compose.onNodeWithText(Words.ProjectReceiptLocationChanged.en).assertIsDisplayed();return@use
                }
                privateHidden()
                if(mode=="sending") {
                    compose.onNodeWithTag(reviewTag).assertDoesNotExist();assertNull(vm.prepareManagementUncertainty(row.key))
                    val before=requests.size
                    compose.runOnUiThread {vm.reviewReceipt(row.key,vm.reviewTarget()!!,row.projectHome)}
                    assertEquals(before,requests.size);assertEquals("sending",runBlocking {app.database.dao().managementOperation(row.key)!!.state});return@use
                }
                val list="management-shared-review"
                val confirm="management-shared-review-confirm"
                fun open() {tap("management-list",reviewTag);compose.onNodeWithTag(confirm).assertIsNotEnabled();tap(list,"management-project-approve");compose.onNodeWithTag(confirm).assertIsEnabled()}
                open();val beforeCancel=requests.size
                compose.onNodeWithText(Words.Cancel.localized(api.preferences.value)).performClick()
                assertEquals(beforeCancel,requests.size);assertEquals("unknown",runBlocking {app.database.dao().managementOperation(row.key)!!.state})
                tap("management-list",reviewTag);compose.onNodeWithTag(confirm).assertIsNotEnabled()
                compose.onNodeWithTag(list).performScrollToNode(hasTestTag("management-project-approve"));compose.onNodeWithTag("management-project-approve").assertIsOff()
                val frozen=vm.prepareManagementUncertainty(row.key)!!
                if(mode=="stale") {
                    home.set("/srv/different-project-store");compose.runOnUiThread {vm.refresh()}
                    compose.waitUntil(10000) {!vm.state.value.busy && vm.receipts.value.none {it.key==row.key}}
                    compose.onNodeWithTag(list).assertDoesNotExist();val before=requests.size
                    compose.runOnUiThread {vm.reviewReceipt(frozen.key,frozen.target,frozen.projectHome)}
                    assertEquals(before,requests.size);assertEquals(Words.ManagementReviewChanged,vm.state.value.error)
                    assertEquals("unknown",runBlocking {app.database.dao().managementOperation(row.key)!!.state});return@use
                }
                tap(list,"management-project-approve");compose.onNodeWithTag(confirm).assertIsEnabled()
                if(bilingual) {
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/project-shared-bilingual-review.png");shot.parentFile!!.mkdirs()
                    val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());shot.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
                }
                if(mode=="remote-home") home.set("/srv/different-project-store")
                val before=requests.size
                compose.onNodeWithTag(confirm).performClick()
                compose.waitUntil(10000) {!vm.state.value.busy && (vm.state.value.error!=null || runBlocking {app.database.dao().managementOperation(row.key)!!.state}=="reviewed")}
                assertEquals(listOf("GET" to "/dashboard/api/auth/me","GET" to "/dashboard/api/profiles"),requests.drop(before))
                assertEquals(row.copy(state=if(mode=="remote-home") "unknown" else "reviewed"),runBlocking {app.database.dao().managementOperation(row.key)!!})
                if(mode=="remote-home") assertEquals(Words.ManagementReviewChanged,vm.state.value.error)
                else assertNull(vm.state.value.error)
            } finally {
                compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(chinese=original.chinese,pinyin=original.pinyin,theme=original.theme))}
                compose.waitUntil(10000) {api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin && api.preferences.value.theme==original.theme}
                assertTrue(requests.none {it.first!="GET"});assertTrue(requests.none {it.second.contains("ws")})
            }
        }
    }
    @Test fun absentOriginalCredentialAndProfileHaveFreshPrivateHomeBoundReview()=exercise("foreign")
    @Test fun sameCredentialOtherProfileStillHasPrivateProjectDetails()=exercise("same")
    @Test fun ownProjectUnknownRequiresAFreshGrantAndVerifiedHome()=exercise("own")
    @Test fun sendingProjectReceiptCannotBeLocallyReleased()=exercise("sending")
    @Test fun homeChangeInvalidatesConfirmationAndRebindsReceiptFlow()=exercise("stale")
    @Test fun hostRemappingAtConfirmationPreservesUnknownWithoutMutation()=exercise("remote-home")
    @Test fun staleRawProjectRowIsReprojectedAcrossHomeChanges()=exercise("raw")
    @Test fun priorHomeForeignSameProfileReceiptCannotUseGenericLocalReview()=exercise("old-home-foreign")
    @Test fun priorHomeOwnReceiptCannotBeReviewedUnderTheNewHome()=exercise("old-home")
    @Test fun acknowledgedProjectReceiptDistinguishesEffectFromIndependentObservation()=exercise("result")
    @Test fun bilingualProjectReviewScrollsToFreshConsent()=exercise("bilingual")
}
