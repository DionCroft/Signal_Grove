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

class HostCronFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    @Test fun advancedScheduleInventoryUsesItsExplicitProfileAndLoadsTheSelectedJob() {
        val writes=AtomicInteger()
        credentials.managementServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Creation fixture"}""")
                    request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[]}""")
                    request.path=="/dashboard/api/cron/jobs?profile=worker"->{
                        assertEquals("Bearer management-create-fixture",request.getHeader("Authorization"))
                        json("""[{"id":"other-fixture","name":"Other schedule","profile":"worker","enabled":true,"state":"scheduled","schedule_display":"Every hour"},{"id":"advanced-fixture","name":"Advanced fixture","profile":"worker","enabled":false,"state":"paused","schedule_display":"0 9 * * *"}]""")
                    }
                    request.path=="/dashboard/api/cron/jobs/advanced-fixture?profile=worker"->json("""{"id":"advanced-fixture","name":"Advanced fixture","profile":"worker","enabled":false,"state":"paused","schedule_display":"0 9 * * *","script":"safe-fixture.py","no_agent":true,"timezone":"UTC","repeat":{"times":null,"completed":2},"last_error":"private-error-fixture","hermes_home":"/private-home-fixture"}""")
                    request.method!="GET"->{writes.incrementAndGet();json("{}").setResponseCode(405)}
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
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-cron-open"));compose.onNodeWithTag("host-cron-open").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.loaded}
            assertEquals(listOf("other-fixture","advanced-fixture"),management.state.value.cron.jobs.map {it.id})
            compose.onNodeWithTag("host-cron-list").performScrollToNode(hasTestTag("host-cron-detail-advanced-fixture"));compose.onNodeWithTag("host-cron-detail-advanced-fixture").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.detail?.id=="advanced-fixture"}
            compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasText("safe-fixture.py"));compose.onNodeWithText("safe-fixture.py").assertExists()
            compose.onNodeWithText("private-error-fixture",substring=true).assertDoesNotExist();compose.onNodeWithText("private-home-fixture",substring=true).assertDoesNotExist();assertEquals(0,writes.get())
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/host-cron-detail.png");shot.parentFile!!.mkdirs()
            shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("host-cron-detail-close").performClick()
            compose.runOnUiThread {management.bind("unconfigured")};compose.waitUntil(5000) {management.state.value.apiScope=="unconfigured"}
            assertTrue(management.state.value.cron.jobs.isEmpty());assertNull(management.state.value.cron.detail);assertEquals(0,writes.get())
        }
    }
}
