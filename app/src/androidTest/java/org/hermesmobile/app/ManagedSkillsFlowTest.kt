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

class ManagedSkillsFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    @Test fun profileSkillControlsRequireReviewShowTextAndObserveTheirOwnInventory() {
        val writes=AtomicInteger();val mcpReads=AtomicInteger();var enabled=true;var locked=false
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Skill fixture"}""")
                request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                request.path=="/dashboard/api/mcp/servers?profile=worker"->{mcpReads.incrementAndGet();json("""{"servers":[]}""")}
                request.path=="/dashboard/api/skills?profile=worker"->{assertEquals("Bearer management-skill-fixture",request.getHeader("Authorization"));json("""[{"name":"fixture","description":"Read this skill","enabled":$enabled,"locked":$locked,"provenance":"hub"},{"name":"unreported"}]""")}
                request.path.orEmpty().startsWith("/dashboard/api/skills/content?")->{assertEquals("fixture",request.requestUrl!!.queryParameter("name"));assertEquals("worker",request.requestUrl!!.queryParameter("profile"));json("""{"name":"fixture","content":"# Displayed instruction text","path":"/private-skill-home-fixture"}""")}
                request.method=="PUT" && request.path.orEmpty().startsWith("/dashboard/api/skills/")->{
                    writes.incrementAndGet();val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;assertEquals("worker",body.text("profile"));assertEquals("fixture",body.text("name"))
                    if(body.containsKey("enabled")) {enabled=body["enabled"]!!.jsonPrimitive.boolean;json("""{"ok":true,"name":"fixture","enabled":$enabled}""")}
                    else {locked=body["locked"]!!.jsonPrimitive.boolean;json("""{"ok":true,"name":"fixture","locked":$locked}""")}
                }
                else->json("{}").setResponseCode(404)
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(inference,"inference-skill-fixture",true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
            val scope=api.profileScope.value
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("management-skill-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-skills-open"));compose.onNodeWithTag("host-skills-open").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.skills.loaded}
            compose.onNodeWithTag("host-skills-list").performScrollToNode(hasTestTag("skill-lock-unreported"));compose.onNodeWithTag("skill-lock-unreported").assertIsNotEnabled()
            compose.onNodeWithTag("host-skills-list").performScrollToNode(hasTestTag("skill-content-fixture"));compose.onNodeWithTag("skill-content-fixture").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.skills.content!=null}
            compose.onNodeWithText("# Displayed instruction text").assertExists();compose.onNodeWithText("private-skill-home-fixture",substring=true).assertDoesNotExist();assertEquals(0,writes.get());compose.onNodeWithTag("skill-content-close").performClick()
            compose.onNodeWithTag("host-skills-list").performScrollToNode(hasTestTag("skill-toggle-fixture"));compose.onNodeWithTag("skill-toggle-fixture").performClick();compose.onNodeWithTag("skill-action-review").assertExists();assertEquals(0,writes.get())
            compose.onNodeWithTag("skill-action-review").performScrollToNode(hasText(Words.SkillToggleEffect.en));compose.onNodeWithText(Words.SkillToggleEffect.en).assertExists();compose.onNodeWithTag("skill-action-confirm").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.skills.rows.first().enabled==false};assertEquals(1,writes.get())
            compose.onNodeWithTag("host-skills-list").performScrollToNode(hasTestTag("skill-lock-fixture"));compose.onNodeWithTag("skill-lock-fixture").performClick()
            compose.onNodeWithTag("skill-action-review").performScrollToNode(hasText(Words.SkillLockEffect.en));compose.onNodeWithText(Words.SkillLockEffect.en).assertExists();assertEquals(1,writes.get())
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/skill-lock-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("skill-action-confirm").performClick();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.skills.rows.first().locked==true};assertEquals(2,writes.get())
            compose.onNodeWithTag("host-skills-close").performClick();compose.waitUntil(5000) {management.receipts.value.any {it.action=="SkillLock"}}
            val receipt=management.receipts.value.single {it.action=="SkillLock"};val priorMcpReads=mcpReads.get()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick()
            compose.waitUntil(10000) {!management.state.value.busy && management.state.value.notice==Words.ManagementObservationOnly};assertEquals(priorMcpReads,mcpReads.get());assertEquals(2,writes.get())
            compose.onNodeWithTag("host-skills-close").performClick();compose.runOnUiThread {management.bind("unconfigured")};assertTrue(management.state.value.skills.rows.isEmpty());assertNull(management.state.value.skills.content)
        }
    }
}
