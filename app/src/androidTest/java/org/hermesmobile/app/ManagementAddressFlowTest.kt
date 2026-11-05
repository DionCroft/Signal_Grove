package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ManagementAddressFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exact(value:String)=SemanticsMatcher("Exact private address") {it.config.getOrNull(SemanticsProperties.EditableText)?.text==value}
    private fun scroll(tag:String)=compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag(tag))
    private fun capture(name:String) {
        compose.waitForIdle()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(250,5000)
        val screenshot=automation.takeScreenshot() ?: throw AssertionError("No settled display capture")
        val f=File(compose.activity.getExternalFilesDir(null),"qa/management-address-$name.png");f.parentFile!!.mkdirs()
        f.outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun exercise(block:(HostKanbanBoardFixture,AtomicInteger,String)->Unit) {
        credentials.managementServer().use {server->
            val catalog=HostKanbanBoardFixture.catalog();val f=HostKanbanBoardFixture(server,"normal","PATCH","/unused",null,catalog,JsonObject(emptyMap()),catalog);f.start();val downstream=server.dispatcher;val requests=AtomicInteger()
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {if(request.requestUrl!!.encodedPath.startsWith("/prefix/dashboard/")) requests.incrementAndGet();return downstream.dispatch(request)}}
            try {
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(server.url("prefix/inference/").toString(),credentials.uniqueToken("address-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese && !api.preferences.value.pinyin}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",f.base);put("profile","worker");put("debugHttp",true);put("tokens",f.tokens.json())})
                compose.runOnUiThread {management.bind(scope)};compose.waitUntil(10000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();block(f,requests,scope)
            } finally {f.assertClean()}
        }
    }
    @Test fun browsingUsesReadOnlyActiveAddressAndKeepsCredentialNavigationReachable()=exercise {f,requests,_->
        compose.onNodeWithTag("management-list").performScrollToKey(1003);compose.onNodeWithTag("management-address").assert(hasText(f.base)).assertIsDisplayed();compose.onNodeWithTag("management-url").assertDoesNotExist();compose.onNodeWithTag("management-sign-in").assertDoesNotExist();val count=requests.get()
        compose.onNodeWithTag("management-list").performScrollToKey(1023);compose.waitForIdle();compose.onNodeWithTag("credential-pool-open").assertIsDisplayed().assertIsEnabled().performClick();compose.onNodeWithTag("credential-pool-list").assertExists();assertEquals(count,requests.get());assertEquals(0,f.writes.get())
        compose.onNodeWithTag("credential-pool-close").performClick();compose.onNodeWithTag("management-list").performScrollToKey(1003);compose.onNodeWithTag("management-address").assert(hasText(f.base));compose.onNodeWithTag("management-url").assertDoesNotExist()
    }
    @Test fun explicitEditCancelRestoresAddressHttpAndActorWithoutRequestsInBothLanguages()=exercise {f,requests,_->
        val identity=management.state.value.identity;val count=requests.get()
        compose.onNodeWithTag("management-list").performScrollToKey(1003);compose.onNodeWithTag("management-address-edit").performClick();compose.onNodeWithTag("management-url").assert(exact(f.base)).performTextReplacement("https://draft.invalid/private/");compose.onNodeWithTag("management-http").assertIsOn().performClick();closeDialogKeyboard();compose.onNodeWithTag("management-address-cancel").assertIsDisplayed();compose.onNodeWithTag("management-profile").assertDoesNotExist();compose.onNodeWithTag("management-address-cancel").performClick()
        compose.onNodeWithTag("management-list").performScrollToKey(1003);compose.onNodeWithTag("management-address").assert(hasText(f.base));compose.onNodeWithTag("management-url").assertDoesNotExist();assertEquals(f.base,management.state.value.url);assertEquals(identity,management.state.value.identity);assertTrue(management.state.value.debugHttp);assertTrue(management.state.value.loaded);assertEquals(count,requests.get())
        compose.runOnUiThread {api.updatePreferences(api.preferences.value.copy(chinese=true,pinyin=true))};compose.waitUntil(10000) {api.preferences.value.chinese && api.preferences.value.pinyin};compose.onNodeWithTag("management-list").performScrollToKey(1003);compose.onNodeWithTag("management-address-edit").assert(hasText(Words.ManagementEditAddress.zh,substring=true));compose.onNodeWithTag("management-address-edit").assert(hasText(Words.ManagementEditAddress.pin,substring=true));capture("bilingual-readonly")
        compose.onNodeWithTag("management-address-edit").performClick();compose.onNodeWithTag("management-url").assert(exact(f.base));compose.onNodeWithTag("management-http").assertIsOn();closeDialogKeyboard();compose.waitForIdle();compose.onNodeWithTag("management-address-cancel").assertIsDisplayed();compose.onNodeWithTag("management-close").assertIsDisplayed();capture("bilingual-editor");compose.onNodeWithTag("management-address-cancel").performClick();compose.onNodeWithTag("management-list").performScrollToKey(1003);compose.onNodeWithTag("management-url").assertDoesNotExist();compose.onNodeWithTag("management-address").assert(hasText(f.base)).assertIsDisplayed();assertEquals(count,requests.get());assertEquals(0,f.writes.get())
    }
    @Test fun changedConnectionDiscardsPrivateDraftInsteadOfRestoringItOnReturn()=exercise {f,_,scope->
        val draft="https://private-draft.invalid/";compose.onNodeWithTag("management-list").performScrollToKey(1003);compose.onNodeWithTag("management-address-edit").performClick();compose.onNodeWithTag("management-url").performTextReplacement(draft);closeDialogKeyboard();compose.waitUntil(10000) {!api.jobBusy.value && !api.connecting.value}
        compose.runOnUiThread {api.setDemo(true)};compose.waitUntil(10000) {!api.connecting.value && api.profileScope.value=="demo" && management.state.value.apiScope=="demo"};compose.onNodeWithText(draft,substring=true).assertDoesNotExist();assertFalse(management.state.value.signedIn)
        compose.runOnUiThread {api.useSavedConnection(scope)};compose.waitUntil(10000) {!api.connecting.value && api.profileScope.value==scope && management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
        compose.onNodeWithTag("management-list").performScrollToKey(1003);compose.onNodeWithTag("management-address").assert(hasText(f.base));compose.onNodeWithTag("management-url").assertDoesNotExist();compose.onNodeWithText(draft,substring=true).assertDoesNotExist();assertEquals(0,f.writes.get())
    }
}
