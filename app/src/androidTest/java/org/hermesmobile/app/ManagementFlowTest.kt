package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.NativeLogin
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ManagementFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    @Test fun verifiedLoginExplicitProfileAndScopedMcpInventoryUseIndependentEncryptedCredentials() {
        val authorize=AtomicReference<String>();val exchanges=AtomicInteger();val wrongCredentials=AtomicInteger()
        val enabled=AtomicBoolean(true);val present=AtomicBoolean(true);val mutations=AtomicInteger()
        credentials.managementServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    val path=request.path.orEmpty()
                    if(path.startsWith("/dashboard/api/") && request.getHeader("Authorization")!="Bearer management-access-fixture") wrongCredentials.incrementAndGet()
                    return when {
                        path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                        path.contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                        path=="/dashboard/auth/native/token"->{
                            assertNull(request.getHeader("Authorization"));val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                            assertEquals(authorize.get().toHttpUrl().queryParameter("code_challenge"),NativeLogin.challenge(body.text("code_verifier")))
                            assertEquals("valid-"+"c".repeat(40),body.text("code"));exchanges.incrementAndGet()
                            json("""{"access_token":"management-access-fixture","refresh_token":"management-refresh-fixture","token_type":"Bearer","expires_at":4102444800,"provider":"password","user_id":"fixture-user"}""")
                        }
                        path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Management fixture"}""")
                        path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker","display_name":"Worker fixture"},{"name":"default"}]}""")
                        path=="/dashboard/api/mcp/servers?profile=worker"->if(present.get()) json("""{"servers":[{"name":"Scoped MCP fixture","transport":"http","auth":"oauth","enabled":${enabled.get()},"source":"config","url":"https://secret.example/?token=never-display","env":{"PRIVATE":"never-display"}}]}""") else json("""{"servers":[]}""")
                        path=="/dashboard/api/mcp/servers/Scoped%20MCP%20fixture/enabled?profile=worker"->{mutations.incrementAndGet();assertEquals("PUT",request.method);val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;assertEquals("worker",body.text("profile"));enabled.set(body["enabled"]!!.jsonPrimitive.boolean);json("""{"ok":true,"name":"Scoped MCP fixture","enabled":${enabled.get()}}""")}
                        path=="/dashboard/api/mcp/servers/Scoped%20MCP%20fixture/test?profile=worker"->{mutations.incrementAndGet();json("""{"ok":true,"tools":[{"name":"lookup","description":"never-store","inputSchema":{"secret":"never-store"}}]}""")}
                        path=="/dashboard/api/mcp/servers/Scoped%20MCP%20fixture?profile=worker" && request.method=="DELETE"->{mutations.incrementAndGet();present.set(false);json("""{"ok":true}""")}
                        else->json("{}").setResponseCode(404)
                    }
                }
            }
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(inference,"inference-access-fixture",true)}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected"}
            compose.runOnUiThread {management.bind(api.profileScope.value);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(5000) {!api.preferences.value.chinese}
            compose.runOnUiThread {management.signIn(dashboard,true) {authorize.set(it)}}
            compose.waitUntil(5000) {authorize.get()!=null}
            val auth=authorize.get().toHttpUrl();val target=auth.queryParameter("redirect_uri")!!.toHttpUrl()
            val callback=target.newBuilder().addQueryParameter("state",auth.queryParameter("state")).addQueryParameter("code","valid-"+"c".repeat(40)).build()
            OkHttpClient.Builder().callTimeout(5,TimeUnit.SECONDS).build().newCall(Request.Builder().url(callback).build()).execute().use {assertEquals(200,it.code)}
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.profiles.size==2}
            assertEquals("fixture-user",management.state.value.identity!!.userId)
            assertEquals("inference-access-fixture",app.credentials.readConfig()!!.token)
            val raw=app.getSharedPreferences("credentials",0).all.values.joinToString()
            listOf("management-access-fixture","management-refresh-fixture",dashboard).forEach {assertFalse(raw.contains(it))}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-profile"))
            compose.onNodeWithTag("management-profile").performClick()
            compose.onNodeWithText("Worker fixture\nworker").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.onNodeWithTag("management-list").performScrollToNode(hasText("Scoped MCP fixture"))
            compose.onNodeWithText("Scoped MCP fixture").assertIsDisplayed();compose.onNodeWithText("never-display",substring=true).assertDoesNotExist()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/management-profile.png");shot.parentFile!!.mkdirs()
            shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-toggle-Scoped MCP fixture"))
            compose.onNodeWithTag("mcp-toggle-Scoped MCP fixture").performClick()
            compose.onNodeWithTag("mcp-action-review").assertExists();assertEquals(0,mutations.get())
            compose.onNodeWithText(Words.Cancel.en).performClick();assertEquals(0,mutations.get())
            compose.onNodeWithTag("mcp-toggle-Scoped MCP fixture").performClick()
            val reviewShot=File(compose.activity.getExternalFilesDir(null),"qa/mcp-action-review.png")
            reviewShot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("mcp-action-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="Toggle" && it.state=="confirmed"}}
            assertFalse(enabled.get());assertEquals(1,mutations.get())
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-probe-Scoped MCP fixture"))
            compose.onNodeWithTag("mcp-probe-Scoped MCP fixture").performClick();compose.onNodeWithTag("mcp-action-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="Probe" && it.state=="confirmed"}}
            val probe=management.receipts.value.single {it.action=="Probe"};assertFalse(probe.resultSummary.contains("never-store"))
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-result-${probe.key}"))
            compose.onNodeWithTag("management-result-${probe.key}").performClick();compose.onNodeWithText("lookup").assertExists();compose.onNodeWithTag("management-result-close").performClick()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-delete-Scoped MCP fixture"))
            compose.onNodeWithTag("mcp-delete-Scoped MCP fixture").performClick();compose.onNodeWithTag("mcp-action-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.servers.isEmpty() && management.receipts.value.any {it.action=="Delete" && it.state=="confirmed"}}
            assertEquals(3,mutations.get())
            assertEquals("worker",runBlocking {app.credentials.management(api.profileScope.value)!!.text("profile")})
            assertEquals(0,wrongCredentials.get());assertEquals(1,exchanges.get())
            compose.runOnUiThread {management.disconnect()};compose.waitUntil(5000) {!management.state.value.busy && !management.state.value.signedIn}
            assertNull(app.credentials.management(api.profileScope.value));assertEquals("inference-access-fixture",app.credentials.readConfig()!!.token)
            authorize.set(null);compose.runOnUiThread {management.signIn(dashboard,true) {authorize.set(it)}}
            compose.waitUntil(5000) {authorize.get()!=null}
            val stale=authorize.get().toHttpUrl();val staleCallback=stale.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder().addQueryParameter("state",stale.queryParameter("state")).addQueryParameter("code","valid-"+"c".repeat(40)).build()
            compose.waitUntil(5000) {!api.jobBusy.value};compose.runOnUiThread {api.setDemo(true)}
            compose.waitUntil(10000) {!api.connecting.value && management.state.value.apiScope=="demo"}
            assertTrue(runCatching {OkHttpClient.Builder().callTimeout(1,TimeUnit.SECONDS).build().newCall(Request.Builder().url(staleCallback).build()).execute().close()}.isFailure)
            assertEquals(1,exchanges.get());assertFalse(management.state.value.signedIn)
        }
    }
    @Test fun uncertainControlsStayWithTheirProfileAndRequireAnExplicitLocalReview() {
        val writes=AtomicInteger();val enabled=AtomicBoolean(true)
        credentials.managementServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Uncertainty fixture"}""")
                    request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"},{"name":"default"}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[{"name":"uncertain-fixture","transport":"http","source":"config","enabled":${enabled.get()}}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=default"->json("""{"servers":[]}""")
                    request.path=="/dashboard/api/mcp/servers/uncertain-fixture/enabled?profile=worker"->{writes.incrementAndGet();enabled.set(false);MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)}
                    else->json("{}").setResponseCode(404)
                }
            }
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(inference,"uncertainty-api-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("management-access-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)}
            compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()}
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-toggle-uncertain-fixture"))
            compose.onNodeWithTag("mcp-toggle-uncertain-fixture").performClick();compose.onNodeWithTag("mcp-action-confirm").performClick()
            compose.waitUntil(15000) {!management.state.value.busy && management.receipts.value.any {it.state=="unknown"}}
            val receipt=management.receipts.value.single {it.state=="unknown"};assertEquals(1,writes.get())
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-toggle-uncertain-fixture"))
            compose.onNodeWithTag("mcp-toggle-uncertain-fixture").assertIsNotEnabled()
            compose.runOnUiThread {management.chooseProfile("default")}
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.profile=="default" && management.receipts.value.isEmpty()}
            assertEquals("unknown",runBlocking {app.database.dao().managementOperation(receipt.key)!!.state});assertEquals(1,writes.get())
            compose.runOnUiThread {management.chooseProfile("worker")}
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.key==receipt.key}}
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"))
            compose.onNodeWithTag("management-observe-${receipt.key}").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.observation?.key==receipt.key}
            assertEquals("unknown",runBlocking {app.database.dao().managementOperation(receipt.key)!!.state});assertEquals(false,management.state.value.observation!!.enabled)
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-review-${receipt.key}"))
            compose.onNodeWithTag("management-review-${receipt.key}").performClick();compose.onNodeWithTag("management-review-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.key==receipt.key && it.state=="reviewed"}}
            assertEquals(1,writes.get())
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("mcp-toggle-uncertain-fixture"))
            compose.onNodeWithTag("mcp-toggle-uncertain-fixture").assertIsEnabled()
        }
    }

}
