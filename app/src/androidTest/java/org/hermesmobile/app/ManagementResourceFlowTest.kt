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
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ManagementResourceFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(sending:Boolean) {
        val writes=AtomicInteger()
        credentials.managementServer().use {server ->
            fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method !in setOf("GET","HEAD")) writes.incrementAndGet()
                if(request.path.orEmpty().startsWith("/dashboard/")) assertEquals("Bearer shared-native-fixture",request.getHeader("Authorization"))
                return when {
                    request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password"}""")
                    request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[]}""")
                    else->MockResponse().setResponseCode(404)
                }
            }}
            compose.waitUntil(10000) {!api.connecting.value}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("shared-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese && !api.preferences.value.pinyin}
            val scope=api.profileScope.value;val removed=ServerConfig(inference,credentials.uniqueToken("removed-inference-fixture"),true).scope
            assertNull(app.credentials.profile(removed))
            val target=ManagementTarget(removed,dashboard,"worker","password · private-other-user")
            val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,removed,dashboard,"worker",target.actor,"CredentialPoolAdd","private-other-provider","private-other-request",if(sending) "sending" else "unknown","private-other-result",123456789)
            runBlocking {app.managementReady.await();app.database.dao().insertManagementOperation(record)}
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("shared-native-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded && management.receipts.value.any {it.key==record.key}}
            assertFalse(management.receipts.value.toString().contains("private-other-"))
            try {
                compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();val state by management.state.collectAsStateWithLifecycle();HermesAppTheme(preferences) {
                    if(sending) ManagementReceiptCard(record,management,state) else ManagementEntry(api,management)
                }}}
                if(sending) {
                    // Deliberately render an old private row before a collector catches up.
                    compose.onNodeWithTag("management-shared-"+record.key).assertIsDisplayed()
                    for(tag in listOf("management-shared-review-","management-result-","management-observe-")) compose.onNodeWithTag(tag+record.key).assertDoesNotExist()
                } else {
                    compose.onNodeWithTag("management-open").performClick()
                    compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-add"));compose.onNodeWithTag("mcp-add").assertIsNotEnabled()
                    fun review() {
                        compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-shared-review-"+record.key))
                        val before=File(compose.activity.getExternalFilesDir(null),"qa/shared-management-before-open.png");before.parentFile!!.mkdirs()
                        before.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                        compose.onNodeWithTag("management-shared-review-"+record.key).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
                        val after=File(compose.activity.getExternalFilesDir(null),"qa/shared-management-after-open.png")
                        after.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                        compose.onNodeWithTag("management-shared-review").assertExists()
                    }
                    review();compose.onNodeWithText(Words.Cancel.en).performClick()
                    assertEquals("unknown",runBlocking {app.database.dao().managementOperation(record.key)!!.state});review()
                    val shot=File(compose.activity.getExternalFilesDir(null),"qa/shared-management-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                    compose.onNodeWithTag("management-shared-review").performScrollToNode(hasText(Words.ManagementSharedReviewEffect.en));compose.onNodeWithTag("management-shared-review-confirm").performClick()
                    compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.none {it.key==record.key}}
                    assertEquals("reviewed",runBlocking {app.database.dao().managementOperation(record.key)!!.state})
                    compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-add"));compose.onNodeWithTag("mcp-add").assertIsEnabled()
                }
                compose.onNodeWithText("private-other-",substring=true,useUnmergedTree=true).assertDoesNotExist()
                assertEquals(0,writes.get());assertNull(app.credentials.profile(removed))
                val retained=runBlocking {app.database.dao().managementOperation(record.key)!!}
                assertEquals(record.requestSummary,retained.requestSummary);assertEquals(record.resultSummary,retained.resultSummary);assertEquals(if(sending) "sending" else "reviewed",retained.state)
            } finally {compose.runOnUiThread {management.cancel()}}
        }
    }
    @Test fun removedConnectionsUnknownOutcomeHasPrivateExplicitCancelableReview()=exercise(false)
    @Test fun staleSendingReceiptCannotRevealDetailsOrOfferLocalClear()=exercise(true)
}
