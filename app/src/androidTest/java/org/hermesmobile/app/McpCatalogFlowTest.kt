package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
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
import java.util.concurrent.atomic.AtomicInteger

class McpCatalogFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    @Test fun catalogInstallRequiresPublicManifestReviewAndMasksSuppliedValues() {
        val writes=AtomicInteger();var configured:JsonObject?=null
        credentials.managementServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Creation fixture"}""")
                    request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker" && request.method=="GET"->json(buildJsonObject {put("servers",JsonArray(listOfNotNull(configured)))}.toString())
                    request.path=="/dashboard/api/mcp/catalog?profile=worker"->json("""{"entries":[{"name":"catalog-fixture","description":"Catalog fixture","source":"https://source.example/public","transport":"stdio","auth_type":"env","command":"uvx","args":["public-package"],"installed":false,"enabled":false,"needs_install":false,"required_env":[{"name":"FIXTURE_KEY","prompt":"Fixture credential","required":true}]}]}""")
                    request.path=="/dashboard/api/mcp/catalog/install?profile=worker"->{
                        assertEquals("Bearer management-create-fixture",request.getHeader("Authorization"))
                        val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertEquals("worker",body.text("profile"));assertEquals(false,body["enable"]!!.jsonPrimitive.boolean);assertEquals("catalog-secret-fixture",body["env"]!!.jsonObject.text("FIXTURE_KEY"));writes.incrementAndGet()
                        json("""{"ok":true,"name":"catalog-fixture","background":false}""")
                    }
                    else->json("{}").setResponseCode(404)
                }
            }
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("inference-catalog-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value
            val owner=ManagementTarget(scope,dashboard,"worker","password · fixture-user")
            val previousReceipt=ManagementOperation(java.util.UUID.randomUUID().toString(),owner.resourceScope,scope,dashboard,"worker",owner.actor,"CatalogInstall","previous-catalog-fixture","{}","acknowledged","",System.currentTimeMillis()-1)
            val beforeKeys=runBlocking {
                app.managementReady.await();app.database.dao().insertManagementOperation(previousReceipt)
                app.database.dao().managementOperations(scope).first().map {it.key}.toSet()
            }
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("management-create-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-catalog-open"));compose.onNodeWithTag("mcp-catalog-open").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.catalogLoaded}
            val pending=management.receipts.value.count {it.state in setOf("sending","unknown")}
            assertEquals("Catalog fixture encountered retained pending management work",0,pending)
            compose.onNodeWithTag("mcp-catalog-list").performScrollToNode(hasTestTag("mcp-catalog-select-catalog-fixture"))
            val before=File(compose.activity.getExternalFilesDir(null),"qa/mcp-catalog-before-select.png");before.parentFile!!.mkdirs()
            before.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("mcp-catalog-select-catalog-fixture").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            val after=File(compose.activity.getExternalFilesDir(null),"qa/mcp-catalog-after-select.png")
            after.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("mcp-catalog-form").assertExists()
            compose.onNodeWithTag("mcp-catalog-form").performScrollToNode(hasTestTag("mcp-catalog-env-FIXTURE_KEY"));compose.onNodeWithTag("mcp-catalog-env-FIXTURE_KEY").performTextReplacement("catalog-secret-fixture")
            closeFocusedKeyboard();compose.waitForIdle()
            compose.onNodeWithTag("mcp-catalog-review").performClick();compose.onNodeWithTag("mcp-catalog-confirmation").assertExists();assertEquals(0,writes.get())
            compose.onNode(hasText("catalog-secret-fixture",substring=true) and hasAnyAncestor(hasTestTag("mcp-catalog-confirmation"))).assertDoesNotExist()
            compose.onNodeWithTag("mcp-catalog-confirmation").performScrollToNode(hasText("https://source.example/public",substring=true))
            compose.onNode(hasText("public-package",substring=true) and hasAnyAncestor(hasTestTag("mcp-catalog-confirmation"))).assertExists()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/mcp-catalog-review.png");shot.parentFile!!.mkdirs()
            shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("mcp-catalog-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.key !in beforeKeys && it.action=="CatalogInstall" && it.state=="acknowledged"}}
            assertEquals(1,writes.get());val receipt=management.receipts.value.single {it.key !in beforeKeys && it.action=="CatalogInstall"};assertFalse(receipt.toString().contains("catalog-secret-fixture"));assertFalse(management.state.value.catalogLoaded)
            assertEquals(previousReceipt,runBlocking {app.database.dao().managementOperation(previousReceipt.key)})
        }
    }
}
