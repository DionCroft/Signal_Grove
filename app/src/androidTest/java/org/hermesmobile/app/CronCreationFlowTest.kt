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

class CronCreationFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    @Test fun pausedCreationRequiresDeclaredSupportAndASeparateReviewThenObservesCronInsteadOfMcp() {
        val writes=AtomicInteger();val mcpReads=AtomicInteger();var configured:JsonObject?=null;var declared=false
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Creation fixture"}""")
                    request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker"->{mcpReads.incrementAndGet();json("""{"servers":[]}""")}
                    request.path=="/dashboard/openapi.json"->json(CronCreationFixture.schema(declared).toString())
                    request.path=="/dashboard/api/cron/delivery-targets?profile=worker"->json("""{"targets":[{"id":"local","name":"Save locally","home_target_set":true},{"id":"slack","name":"Slack fixture","home_target_set":false}]}""")
                    request.path=="/dashboard/api/cron/jobs?profile=worker" && request.method=="GET"->json(JsonArray(listOfNotNull(configured)).toString())
                    request.path=="/dashboard/api/cron/jobs?profile=worker" && request.method=="POST"->{
                        assertEquals("Bearer management-create-fixture",request.getHeader("Authorization"));val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertEquals(true,body["paused"]!!.jsonPrimitive.boolean);assertFalse(body.containsKey("profile"));assertEquals(JsonArray(emptyList()),body["enabled_toolsets"]);writes.incrementAndGet();configured=CronCreationFixture.job(body);json(configured.toString())
                    }
                    request.path=="/dashboard/api/cron/jobs/created-job-fixture?profile=worker"->json(configured.toString())
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
            compose.onNodeWithTag("host-cron-list").performScrollToNode(hasTestTag("cron-create-open"));compose.onNodeWithTag("cron-create-open").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.HostCronCreateUnsupported};compose.onNodeWithTag("cron-create-form").assertDoesNotExist();assertEquals(0,writes.get())
            declared=true;compose.onNodeWithTag("cron-create-open").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.contract!=null}
            compose.onNodeWithTag("cron-create-form").performScrollToNode(hasTestTag("cron-create-name"));compose.onNodeWithTag("cron-create-name").performTextReplacement("Created paused fixture")
            compose.onNodeWithTag("cron-create-form").performScrollToNode(hasTestTag("cron-create-schedule"));compose.onNodeWithTag("cron-create-schedule").performTextReplacement("0 9 * * *")
            compose.onNodeWithTag("cron-create-form").performScrollToNode(hasTestTag("cron-create-prompt"));compose.onNodeWithTag("cron-create-prompt").performTextReplacement("private-prompt-fixture")
            androidx.test.espresso.Espresso.closeSoftKeyboard();compose.waitForIdle()
            compose.onNodeWithTag("cron-create-form").performScrollToNode(hasTestTag("cron-create-delivery-slack"));compose.onNodeWithTag("cron-create-delivery-slack").assertIsNotEnabled()
            compose.onNodeWithTag("cron-create-form").performScrollToNode(hasTestTag("cron-create-advanced"));compose.onNodeWithTag("cron-create-advanced").performClick()
            compose.onNodeWithTag("cron-create-form").performScrollToNode(hasTestTag("cron-create-restrict-tools"));compose.onNodeWithTag("cron-create-restrict-tools").performClick()
            compose.onNodeWithTag("cron-create-review").performClick();compose.onNodeWithTag("cron-create-confirmation").assertExists();assertEquals(0,writes.get())
            compose.onNodeWithTag("cron-create-confirmation").performScrollToNode(hasText(Words.HostCronNoTools.en));compose.onNode(hasText(Words.HostCronNoTools.en) and hasAnyAncestor(hasTestTag("cron-create-confirmation"))).assertExists()
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/cron-create-review.png");shot.parentFile!!.mkdirs()
            shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("cron-create-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="CronCreate" && it.state=="confirmed"}}
            assertEquals(1,writes.get());assertEquals(false,management.state.value.cron.detail!!.enabled);assertTrue(management.state.value.cron.detail!!.noToolsAllowed)
            val receipt=management.receipts.value.single {it.action=="CronCreate"};assertFalse(receipt.toString().contains("private-prompt-fixture"))
            compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasText(Words.HostCronNoTools.en));compose.onNode(hasText(Words.HostCronNoTools.en) and hasAnyAncestor(hasTestTag("host-cron-detail"))).assertExists()
            compose.onNodeWithTag("host-cron-detail-close").performClick();compose.onNodeWithTag("host-cron-close").performClick()
            val originalMcpReads=mcpReads.get()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.jobs.any {it.id=="created-job-fixture"}}
            compose.onNodeWithTag("host-cron-list").assertExists();assertEquals(originalMcpReads,mcpReads.get());assertEquals(1,writes.get())
        }
    }
}
