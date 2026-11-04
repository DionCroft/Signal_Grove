package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HostProjectsFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private val privateName="Private orbital garden"
    private val privatePath="/srv/private/orbital-garden"
    private fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
    private fun row(id:String="p_00000001",slug:String="orbital-garden")=buildJsonObject {
        put("id",id);put("slug",slug);put("name",privateName);put("created_at",1793820000L);put("archived",true)
        put("description","Private notes\n第二行");put("board_slug","fixture-board");put("icon","sprout");put("color","forest")
        put("folders",buildJsonArray {add(buildJsonObject {put("path",privatePath);put("label","Private folder");put("is_primary",true);put("added_at",1793820001L)})})
    }
    private fun capture(name:String) {
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("No actual screen capture")
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun exercise(mode:String) {
        val tickets=AtomicInteger();val lists=AtomicInteger();val details=AtomicInteger()
        val home=AtomicReference("/srv/project-worker")
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        credentials.managementServer().use {server->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(q:RecordedRequest):MockResponse=when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(buildJsonObject {put("profiles",buildJsonArray {
                        add(buildJsonObject {put("name","worker");put("path",home.get())})
                        add(buildJsonObject {put("name","other");put("path","/srv/project-other")})
                    })})
                    q.path.orEmpty().startsWith("/dashboard/api/mcp/servers?profile=")->json(buildJsonObject {put("servers",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/ws-ticket"->{assertEquals("Bearer projects-ui-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json(buildJsonObject {put("ticket","p".repeat(43));put("ttl_seconds",30)})}
                    q.path=="/dashboard/api/ws"->MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                        override fun onOpen(socket:WebSocket,response:Response) {socket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready"}}""")}
                        override fun onMessage(socket:WebSocket,text:String) {
                            val j=Json.parseToJsonElement(text).jsonObject;val params=j["params"]!!.jsonObject
                            assertEquals(JsonPrimitive("worker"),params["profile"])
                            val result=when(j["method"]!!.jsonPrimitive.content) {
                                "projects.list"->{
                                    lists.incrementAndGet();assertEquals(setOf("profile"),params.keys)
                                    if(mode=="held") {entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS))}
                                    if(mode=="after") home.set("/srv/project-remapped")
                                    buildJsonObject {put("projects",buildJsonArray {add(row())});put("active_id","p_deleted")}
                                }
                                "projects.get"->{details.incrementAndGet();assertEquals(setOf("profile","id"),params.keys);assertEquals(JsonPrimitive("p_00000001"),params["id"]);buildJsonObject {put("project",if(mode=="wrong-id") row("p_other","p_00000001") else row())}}
                                else->error("Unapproved project RPC")
                            }
                            socket.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                            if(mode=="held") returned.countDown()
                        }
                    }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                    else->MockResponse().setResponseCode(404)
                }
            }
            val original=api.preferences.value
            try {
                compose.waitUntil(10000) {!api.connecting.value}
                compose.runOnUiThread {api.configure(server.url("inference/").toString(),credentials.uniqueToken("projects-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(theme="STARHAVEN",chinese=mode=="normal",pinyin=mode=="normal"))}
                compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==(mode=="normal")}
                val scope=api.profileScope.value
                app.credentials.saveManagement(scope,buildJsonObject {put("url",server.url("dashboard/").toString());put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("projects-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
                compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
                compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val prefs by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(prefs) {ManagementEntry(api,management)}}}
                compose.onNodeWithTag("management-open").performClick()
                compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-projects-open"));compose.onNodeWithTag("host-projects-open").assertIsEnabled().performClick()
                assertEquals(0,tickets.get());assertEquals(0,lists.get())
                compose.onNodeWithTag("host-projects-list").performScrollToNode(hasTestTag("host-projects-read"))
                compose.onNodeWithTag("host-projects-read").assertHeightIsAtLeast(48.dp).performClick()
                compose.onNodeWithTag("host-projects-read-cancel").assertIsDisplayed().performClick()
                assertEquals(0,tickets.get());assertEquals(0,lists.get())
                compose.onNodeWithTag("host-projects-list").performScrollToNode(hasTestTag("host-projects-read"))
                compose.onNodeWithTag("host-projects-read").performClick()
                if(mode=="stale") {
                    val frozen=management.prepareHostProjectsRead()!!
                    compose.runOnUiThread {management.chooseProfile("other")};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.profile=="other"}
                    compose.runOnUiThread {management.loadHostProjects(frozen)}
                    assertEquals(Words.ManagementReviewChanged,management.state.value.error);assertNull(management.state.value.projects.inventory);assertEquals(0,tickets.get());assertEquals(0,lists.get());return@use
                }
                if(mode=="before") home.set("/srv/project-remapped")
                compose.onNodeWithTag("host-projects-read-confirm").assertIsDisplayed().assertIsEnabled().performClick()
                if(mode=="held") {
                    compose.waitUntil(10000) {entered.count==0L}
                    compose.onNodeWithTag("host-projects-list").performScrollToNode(hasTestTag("host-projects-cancel-work"))
                    compose.onNodeWithTag("host-projects-cancel-work").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick();release.countDown()
                    compose.waitUntil(10000) {returned.count==0L};compose.waitForIdle()
                    assertNull(management.state.value.projects.inventory);assertNull(management.state.value.projects.detail);compose.onNodeWithText(privateName).assertDoesNotExist();assertEquals(1,tickets.get());assertEquals(1,lists.get());return@use
                }
                compose.waitUntil(10000) {!management.state.value.busy}
                if(mode in setOf("before","after")) {
                    assertEquals(Words.HostProjectsScopeChanged,management.state.value.error);assertNull(management.state.value.projects.inventory)
                    compose.onNodeWithTag("host-projects-list").performScrollToNode(hasText(Words.HostProjectsScopeChanged.en,substring=true));compose.onNodeWithText(privateName).assertDoesNotExist()
                    assertEquals(if(mode=="before") 0 else 1,tickets.get());assertEquals(if(mode=="before") 0 else 1,lists.get());return@use
                }
                assertNotNull(management.state.value.projects.inventory);assertNull(management.state.value.projects.inventory!!.activeProject);assertTrue(management.state.value.projects.inventory!!.projects.single().archived)
                compose.onNodeWithTag("host-projects-list").performScrollToNode(hasText(Words.HostProjectMissingActive.en,substring=true))
                compose.onNodeWithTag("host-projects-list").performScrollToNode(hasTestTag("host-project-details-p_00000001"));compose.onNodeWithTag("host-project-details-p_00000001").assertIsDisplayed().performClick()
                compose.waitUntil(10000) {!management.state.value.busy}
                if(mode=="wrong-id") {
                    assertEquals(Words.HostProjectsUnsupported,management.state.value.error);assertNull(management.state.value.projects.detail);assertNull(management.state.value.projects.inventory);compose.onNodeWithText(privateName).assertDoesNotExist();assertEquals(2,tickets.get());return@use
                }
                assertNotNull(management.state.value.projects.detail)
                compose.onNodeWithTag("host-project-detail").performScrollToNode(hasText(privatePath));compose.onNodeWithText(privatePath).assertIsDisplayed();compose.onNodeWithTag("host-project-detail-close").assertIsDisplayed()
                if(mode=="closed") {
                    val raw=management.state.value
                    compose.runOnUiThread {management.closeHostProjects();compose.activity.setContent {HermesAppTheme(AppPreferences(chinese=false,pinyin=false)) {HostProjectsDialog(management,raw) {}}}}
                    compose.onNodeWithText(privateName,substring=true).assertDoesNotExist();compose.onNodeWithText(privatePath,substring=true).assertDoesNotExist();assertNull(management.visibleHostProjects(raw).inventory);assertNull(management.visibleHostProjects(raw).detail)
                } else capture("host-projects-bilingual-details")
                assertEquals(1,lists.get());assertEquals(1,details.get());assertEquals(2,tickets.get())
            } finally {
                release.countDown()
                compose.runOnUiThread {management.cancel();api.updatePreferences(api.preferences.value.copy(theme=original.theme,chinese=original.chinese,pinyin=original.pinyin))}
                compose.waitUntil(10000) {api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin && api.preferences.value.theme==original.theme}
            }
        }
    }
    @Test fun connectionReviewCancelAndBilingualCanonicalDetailsRemainReachable()=exercise("normal")
    @Test fun remappedProfileBeforeReadCannotOpenTheGateway()=exercise("before")
    @Test fun remappedProfileAfterReadCannotExposePrivateRows()=exercise("after")
    @Test fun cancellationDropsALateSocketResultWithoutAnotherConnection()=exercise("held")
    @Test fun staleProfileReviewCannotOpenAConnection()=exercise("stale")
    @Test fun closedPrivateViewCannotBeReconstitutedByAStaleRawState()=exercise("closed")
    @Test fun slugFallbackDetailReplyCannotRenderAnotherCanonicalProject()=exercise("wrong-id")
}
