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

class McpCreationFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    @Test fun creatingAnHttpServerRequiresReviewAndDoesNotPersistProvisioningSecrets() {
        val writes=AtomicInteger();var configured:JsonObject?=null
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Creation fixture"}""")
                    request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker" && request.method=="GET"->json(buildJsonObject {put("servers",JsonArray(listOfNotNull(configured)))}.toString())
                    request.path=="/dashboard/api/mcp/servers?profile=worker" && request.method=="POST"->{
                        assertEquals("Bearer management-create-fixture",request.getHeader("Authorization"))
                        val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertEquals("worker",body.text("profile"));assertEquals("host-create-secret-fixture",body.text("bearer_token"));assertEquals("https://tool.example/mcp?token=hidden-query-fixture",body.text("url"));writes.incrementAndGet()
                        configured=buildJsonObject {put("name",body.text("name"));put("url",body.text("url"));put("auth","header");put("source","config");put("transport","http");put("enabled",true)}
                        json(configured.toString())
                    }
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
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-add"));compose.onNodeWithTag("mcp-add").performClick()
            compose.onNodeWithTag("mcp-create-name").performTextReplacement("created-fixture")
            compose.onNodeWithTag("mcp-create-form").performScrollToNode(hasTestTag("mcp-create-url"));compose.onNodeWithTag("mcp-create-url").performTextReplacement("https://tool.example/mcp?token=hidden-query-fixture")
            compose.onNodeWithTag("mcp-create-form").performScrollToNode(hasTestTag("mcp-auth-Header"));compose.onNodeWithTag("mcp-auth-Header").performClick()
            compose.onNodeWithTag("mcp-create-form").performScrollToNode(hasTestTag("mcp-create-token"));compose.onNodeWithTag("mcp-create-token").performTextReplacement("host-create-secret-fixture")
            androidx.test.espresso.Espresso.closeSoftKeyboard();compose.waitForIdle()
            compose.onNodeWithTag("mcp-create-review").performClick();compose.onNodeWithTag("mcp-create-confirmation").assertExists();assertEquals(0,writes.get())
            compose.onNode(hasText("https://tool.example/mcp") and hasAnyAncestor(hasTestTag("mcp-create-confirmation"))).assertExists()
            compose.onNode(hasText("hidden-query-fixture",substring=true) and hasAnyAncestor(hasTestTag("mcp-create-confirmation"))).assertDoesNotExist()
            compose.onNode(hasText("host-create-secret-fixture",substring=true) and hasAnyAncestor(hasTestTag("mcp-create-confirmation"))).assertDoesNotExist()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/mcp-create-review.png");shot.parentFile!!.mkdirs()
            shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("mcp-create-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="Create" && it.state=="confirmed"}}
            assertEquals(1,writes.get());assertEquals("created-fixture",management.state.value.servers.single().name)
            val receipt=management.receipts.value.single {it.action=="Create"}
            assertFalse(receipt.toString().contains("host-create-secret-fixture"));assertFalse(receipt.toString().contains("hidden-query-fixture"))
        }
    }
}
