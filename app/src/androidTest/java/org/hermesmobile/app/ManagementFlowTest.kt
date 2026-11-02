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
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
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
        MockWebServer().use {server ->
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
                        path=="/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[{"name":"Scoped MCP fixture","transport":"http","auth":"oauth","enabled":true,"source":"config","url":"https://secret.example/?token=never-display","env":{"PRIVATE":"never-display"}}]}""")
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
}
