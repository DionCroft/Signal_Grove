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
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class McpOAuthFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    @Test fun authorizationRequiresReviewAndRefreshesTheOriginalFlowWithoutAnotherStart() {
        val writes=AtomicInteger();val cancels=AtomicInteger();var reported="authorization_required"
        val flowId="unique_ui_flow_fixture_12345678"
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Creation fixture"}""")
                    request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[{"name":"OAuth fixture","transport":"http","auth":"oauth","source":"config","enabled":true}]}""")
                    request.path=="/dashboard/api/mcp/servers/OAuth%20fixture/auth?profile=worker"->{writes.incrementAndGet();json("""{"flow_id":"$flowId","server_name":"OAuth fixture","status":"authorization_required","authorization_url":"https://provider.example/authorize?state=private-state-fixture"}""")}
                    request.path=="/dashboard/api/mcp/oauth/flows/$flowId" && request.method=="GET"->json("""{"flow_id":"$flowId","server_name":"OAuth fixture","status":"$reported","authorization_url":"https://provider.example/authorize?state=private-state-fixture","tools":[{"name":"fixture_tool","description":"private-description-fixture"}]}""")
                    request.path=="/dashboard/api/mcp/oauth/flows/$flowId" && request.method=="DELETE"->{cancels.incrementAndGet();MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)}
                    else->json("{}").setResponseCode(404)
                }
            }
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(inference,"inference-create-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("management-create-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-oauth-start-OAuth fixture"));compose.onNodeWithTag("mcp-oauth-start-OAuth fixture").performClick()
            compose.onNodeWithTag("mcp-oauth-review").assertExists();assertEquals(0,writes.get());compose.onNodeWithTag("mcp-oauth-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="OAuthStart" && it.state=="acknowledged"}}
            val receipt=management.receipts.value.single {it.action=="OAuthStart"};val key=receipt.key;assertEquals(1,writes.get());assertFalse(receipt.toString().contains("private-state-fixture"))
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-oauth-open-$key"));compose.onNodeWithTag("mcp-oauth-open-$key").performClick()
            compose.onNodeWithText("https://provider.example/authorize").assertExists();compose.onNodeWithText("private-state-fixture",substring=true).assertDoesNotExist()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/mcp-oauth-browser-review.png");shot.parentFile!!.mkdirs()
            shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithText(Words.Cancel.en).performClick()
            reported="approved";compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-oauth-read-$key"));compose.onNodeWithTag("mcp-oauth-read-$key").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.oauth[key]?.status=="approved"}
            reported="authorization_required";compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-oauth-read-$key"));compose.onNodeWithTag("mcp-oauth-read-$key").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.oauth[key]?.status=="authorization_required"};assertEquals(1,writes.get())
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-oauth-cancel-$key"));compose.onNodeWithTag("mcp-oauth-cancel-$key").performClick();assertEquals(0,cancels.get())
            compose.onNodeWithTag("mcp-oauth-cancel-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="OAuthCancel" && it.state=="unknown"}}
            assertEquals(1,cancels.get());assertEquals(1,writes.get());assertNull(management.state.value.oauth[key])
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-oauth-cancel-$key"));compose.onNodeWithTag("mcp-oauth-cancel-$key").assertIsNotEnabled()

            assertTrue(management.receipts.value.none {it.resultSummary.contains("private-state-fixture") || it.resultSummary.contains("private-description-fixture")})
        }
    }
}
