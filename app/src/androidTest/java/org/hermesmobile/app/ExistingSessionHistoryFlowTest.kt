package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class ExistingSessionHistoryFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private var previous:AppPreferences?=null
    @After fun reset() {
        compose.runOnUiThread {vm.forgetServer();previous?.let {old->vm.updatePreferences(vm.preferences.value.copy(theme=old.theme,chinese=old.chinese,pinyin=old.pinyin))}}
        compose.waitUntil(10000) {vm.preferences.value.demo && !vm.connecting.value}
    }
    private fun text(word:Words)=word.localized(vm.preferences.value)
    private fun shot(name:String) {
        compose.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(250,5000)
        val image=checkNotNull(automation.takeScreenshot());val f=File(compose.activity.getExternalFilesDir(null),"qa/origin-"+name+"-"+UUID.randomUUID()+".png");f.parentFile!!.mkdirs();f.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }
    private class Fixture(val token:String,val mode:String="normal"):AutoCloseable {
        val server=MockWebServer();val metadata=CopyOnWriteArrayList<String>();val writes=AtomicInteger();val pages=CopyOnWriteArrayList<Int>();val history=CopyOnWriteArrayList<String>();val unexpected=CopyOnWriteArrayList<String>();val turns=CopyOnWriteArrayList<JsonObject>()
        private fun row(id:String,title:String,source:String?,time:Int)=buildJsonObject {put("id",id);put("title",title);source?.let {put("source",it)};put("last_active",time)}
        private fun source(id:String)=if(mode=="second") "api_server" else when {id=="api_saved"->"api_server";id.startsWith("desk")->"desktop";id.startsWith("disc")->"discord";else->null}
        init {
            server.dispatcher=object:Dispatcher() {
                private fun json(value:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
                override fun dispatch(q:RecordedRequest):MockResponse=try {
                    assertEquals("Bearer "+token,q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));val u=q.requestUrl!!;assertTrue(u.encodedPath.startsWith("/p/work/"));val path=u.encodedPath.removePrefix("/p/work/")
                    when {
                        path=="v1/capabilities"->json(buildJsonObject {
                            put("model","stock-default");put("features",buildJsonObject {for(k in listOf("session_resources","session_chat","session_chat_streaming","run_submission","run_status","run_events_sse","session_fork","session_model_lock","run_steer","model_options")) put(k,true)})
                            put("endpoints",buildJsonObject {for((k,v) in mapOf("runs" to "/v1/runs","session" to "/api/sessions/{session_id}","session_create" to "/api/sessions","session_fork" to "/api/sessions/{session_id}/fork","session_model_lock" to "/api/sessions/{session_id}/model","session_update" to "/api/sessions/{session_id}","session_delete" to "/api/sessions/{session_id}","run_steer" to "/v1/runs/{run_id}/steer")) put(k,buildJsonObject {put("path",v)})})
                        })
                        path=="api/jobs"->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                        path=="api/sessions" && q.method=="GET"->{
                            assertEquals(setOf("limit","offset"),u.queryParameterNames);assertEquals("50",u.queryParameter("limit"));val offset=u.queryParameter("offset")!!.toInt();pages+=offset
                            val rows=when {
                                mode=="second"->listOf(row("desk_saved","Different profile API","api_server",100))
                                mode=="pagination" && offset==0->listOf(row("api_saved","API saved","api_server",100))+List(49) {row("desk_"+it,"Desktop "+it,"desktop",99-it)}
                                mode=="pagination"->listOf(row("disc_old","Older Discord","discord",1))
                                else->listOf(row("desk_saved","Desktop saved","desktop",100),row("disc_saved","Discord saved","discord",99),row("api_saved","API saved","api_server",98),row("unknown_saved","Unknown saved",null,97))
                            }
                            json(buildJsonObject {put("data",JsonArray(rows));put("has_more",mode=="pagination" && offset==0)})
                        }
                        path.startsWith("api/sessions/") && path.endsWith("/messages")->{
                            assertEquals("GET",q.method);val id=path.removePrefix("api/sessions/").removeSuffix("/messages");history+=id
                            json(buildJsonObject {put("session_id",id);put("data",buildJsonArray {add(buildJsonObject {put("id","u");put("role","user");put("content","Existing user "+id);put("timestamp",1)});add(buildJsonObject {put("id","a");put("role","assistant");put("content","Existing reply "+id);put("timestamp",2)})})})
                        }
                        path.startsWith("api/sessions/") && q.method=="GET"->{val id=path.removePrefix("api/sessions/");metadata+=id;json(buildJsonObject {put("session",row(id,"Metadata",source(id),1))})}
                        path=="v1/runs" && q.method=="POST"->{writes.incrementAndGet();turns+=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;json(buildJsonObject {put("run_id","origin-run")})}
                        path=="v1/runs/origin-run/events"->MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"event\":\"run.completed\",\"run_id\":\"origin-run\",\"seq\":0,\"output\":\"Verified existing API reply\"}\n\n")
                        else->{if(q.method!="GET") writes.incrementAndGet();unexpected+=q.method+" "+path;MockResponse().setResponseCode(500).setBody("unexpected origin fixture request")}
                    }
                } catch(error:Throwable) {unexpected+=error.javaClass.simpleName+":"+error.message;MockResponse().setResponseCode(500)}
            }
        }
        override fun close()=server.close()
    }
    private fun connect(f:Fixture,bilingual:Boolean=false) {
        val url=f.server.url("p/work/").toString()
        compose.waitUntil(10000) {!vm.connecting.value};if(previous==null) previous=vm.preferences.value
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(theme="STARHAVEN",chinese=bilingual,pinyin=bilingual));vm.configure(url,f.token,true)}
        compose.waitUntil(10000) {!vm.connecting.value && !vm.sessionsLoading.value && vm.connectionStatus.value=="connected" && vm.conversations.value.any {it.remoteId=="desk_saved" || it.remoteId=="desk_0"} && vm.preferences.value.chinese==bilingual}
        // Home also has Chat-labelled conversation actions; target the actual navigation destination.
        compose.onNodeWithTag("nav-chat").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("chat-model-status-open").fetchSemanticsNodes().size==1}
    }
    private fun browser()=compose.onNodeWithContentDescription(text(Words.History)).performClick()
    private fun reveal(id:String) {compose.onNodeWithTag("conversation-browser").performScrollToNode(hasTestTag("history-open-"+id));compose.onNodeWithTag("history-open-"+id).assertIsDisplayed()}
    private fun source(value:String) {compose.onNodeWithTag("conversation-browser").performScrollToNode(hasTestTag("history-source-filter"));compose.onNodeWithTag("history-source-filter").performClick();compose.onNodeWithTag("history-source-"+value).performClick()}
    private fun open(id:String) {browser();reveal(id);compose.onNodeWithTag("history-open-"+id).performClick();compose.waitUntil(10000) {vm.selectedConversation.value?.remoteId==id && vm.messages.value.any {it.role=="assistant"}}}
    private fun readableChat() {
        val minimum=48f*compose.activity.resources.displayMetrics.density
        compose.waitUntil(5000) {compose.onAllNodesWithTag("chat-messages").fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.height?.let {it>=minimum}==true}
        compose.onNodeWithTag("chat-messages").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
    private fun foreign(id:String,bilingual:Boolean=false) {
        Fixture(credentials.uniqueToken("origin")).use {f->
            connect(f,bilingual);open(id);compose.onNodeWithTag("chat-read-only").assertIsDisplayed();compose.onNodeWithContentDescription(text(Words.Send)).assertIsNotEnabled()
            val c=vm.selectedConversation.value!!;assertTrue(SessionOrigin.readOnly(c));assertEquals(listOf("Existing user "+id,"Existing reply "+id),vm.messages.value.map {it.content});readableChat()
            if(bilingual) shot("bilingual-readable-chat")
            compose.onNodeWithTag("chat-read-only").assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("chat-read-only-reason").assertIsDisplayed();compose.onNodeWithText(text(Words.ReadOnlySession)).assertIsDisplayed()
            if(bilingual) shot("bilingual-read-only-reason")
            compose.onNodeWithTag("chat-read-only-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick();readableChat()
            compose.runOnUiThread {vm.send("Do not admit this foreign turn")};compose.waitUntil(5000) {!vm.busy.value};assertEquals(text(Words.ReadOnlySession),vm.error.value)
            // Verify the expected rejection, then dismiss its banner before using another control.
            compose.onNodeWithContentDescription(text(Words.Dismiss)).assertIsDisplayed().assertIsEnabled().performClick();compose.waitUntil(5000) {vm.error.value==null};readableChat()
            compose.onNodeWithContentDescription(text(Words.SessionControls)).assertIsDisplayed().performClick()
            compose.waitUntil(5000) {compose.onAllNodesWithTag("session-control-list").fetchSemanticsNodes().size==1}
            compose.waitUntil(5000) {!vm.sessionDetailsLoading.value && id in f.metadata}
            compose.onNodeWithTag("session-control-list").performScrollToNode(hasTestTag("session-lock"));compose.onNodeWithTag("session-lock").assertIsNotEnabled()
            compose.onNodeWithTag("session-control-list").performScrollToNode(hasTestTag("session-fork"));compose.onNodeWithTag("session-fork").assertIsNotEnabled()
            assertTrue(SessionOrigin.readOnly(runBlocking {app.database.dao().conversation(c.key)}!!));assertEquals(0,f.writes.get());assertTrue(f.unexpected.toString(),f.unexpected.isEmpty())
            if(bilingual) shot("bilingual-read-only")
        }
    }
    @Test fun desktopHistoryIsReadableWhileSendLockAndForkStayUnavailable()=foreign("desk_saved")
    @Test fun discordHistoryKeepsItsOriginalSourceAndThread()=foreign("disc_saved")
    @Test fun unknownInventoryAndMetadataOriginsNeverGainWriteAuthority()=foreign("unknown_saved")
    @Test fun bilingualForeignHistoryExplainsUnavailableControls()=foreign("desk_saved",true)
    @Test fun existingApiConversationStillRepliesWithExplicitRuntimeAndNoNewSession() {
        Fixture(credentials.uniqueToken("origin-api")).use {f->
            connect(f);open("api_saved");assertFalse(SessionOrigin.readOnly(vm.selectedConversation.value!!));compose.onNodeWithTag("chat-read-only").assertDoesNotExist()
            val runtime=RuntimeChoice("rvn-loaded","spark-rvn");compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,runtime)};compose.waitUntil(5000) {!vm.controlBusy.value}
            compose.runOnUiThread {vm.send("New authorized API turn")};compose.waitUntil(15000) {!vm.busy.value && vm.runStatus.value=="completed"}
            assertEquals("api_saved",f.turns.single().text("session_id"));assertEquals(runtime.model,f.turns.single().text("model"));assertEquals(runtime.provider,f.turns.single().text("provider"));assertEquals(1,f.writes.get());assertTrue(f.unexpected.toString(),f.unexpected.isEmpty())
        }
    }
    @Test fun sourceFilteringIsLocalAndPaginationFooterIsReachableWithThreeLanguages() {
        Fixture(credentials.uniqueToken("origin-pages"),"pagination").use {f->
            connect(f,true);browser();compose.onNodeWithTag("history-load-more").assertIsDisplayed().assertHeightIsAtLeast(48.dp);shot("bilingual-pages")
            source("desktop");compose.onNodeWithTag("history-open-api_saved").assertDoesNotExist();assertEquals(listOf(0),f.pages.toList())
            compose.onNodeWithTag("history-load-more").performClick();compose.waitUntil(10000) {!vm.sessionsLoading.value && f.pages.size==2 && vm.conversations.value.any {it.remoteId=="disc_old"}};assertEquals(listOf(0,50),f.pages.toList())
            source("discord");reveal("disc_old");shot("bilingual-older-result");compose.onNodeWithTag("history-load-more").assertDoesNotExist();compose.onNodeWithTag("history-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("history-open-disc_old").performClick();compose.waitUntil(10000) {vm.selectedConversation.value?.remoteId=="disc_old" && vm.messages.value.any {it.content=="Existing reply disc_old"}};compose.onNodeWithTag("chat-read-only").assertIsDisplayed();assertEquals(listOf("disc_old"),f.history.toList())
            assertEquals(0,f.writes.get());assertTrue(f.unexpected.toString(),f.unexpected.isEmpty())
        }
    }
    @Test fun keyboardOpenStillAllowsNativePagination() {
        Fixture(credentials.uniqueToken("origin-keyboard"),"pagination").use {f->
            connect(f,true);browser();compose.waitUntil(5000) {compose.onAllNodesWithTag("history-close").fetchSemanticsNodes().singleOrNull()?.config?.getOrNull(SemanticsProperties.Focused)==true};compose.onNodeWithTag("history-close").assertIsFocused()
            compose.waitUntil(5000) {!focusedDialogKeyboardVisible()}
            source("desktop");compose.onNodeWithTag("conversation-browser").performScrollToNode(hasTestTag("history-search-input"))
            compose.onNodeWithTag("history-search-input").performClick().assertIsFocused();compose.waitUntil(5000) {focusedDialogKeyboardVisible()}
            shot("bilingual-keyboard-footer")
            val more=compose.onNodeWithTag("history-load-more").assertIsDisplayed().assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInWindow.center
            tapFocusedDialogWindowPoint(more.x,more.y)
            compose.waitUntil(10000) {!vm.sessionsLoading.value && f.pages.toList()==listOf(0,50) && vm.conversations.value.any {it.remoteId=="disc_old"}}
            val close=compose.onNodeWithTag("history-close").assertIsDisplayed().fetchSemanticsNode().boundsInWindow.center
            tapFocusedDialogWindowPoint(close.x,close.y);compose.waitUntil(5000) {compose.onAllNodesWithTag("conversation-browser").fetchSemanticsNodes().isEmpty()}
            assertEquals(0,f.writes.get());assertTrue(f.unexpected.toString(),f.unexpected.isEmpty())
        }
    }
    @Test fun switchingConnectionDropsTheOldSourceFilterAndPrivateRows() {
        Fixture(credentials.uniqueToken("origin-first")).use {first->Fixture(credentials.uniqueToken("origin-second"),"second").use {second->
            connect(first);browser();source("desktop");compose.onNodeWithTag("history-close").performClick();val oldScope=vm.profileScope.value
            connect(second);browser();assertNotEquals(oldScope,vm.profileScope.value);reveal("desk_saved");compose.onNodeWithText("Desktop saved",substring=true).assertDoesNotExist();assertFalse(SessionOrigin.readOnly(vm.conversations.value.single()))
            assertEquals(0,first.writes.get());assertEquals(0,second.writes.get());assertTrue(first.unexpected.toString(),first.unexpected.isEmpty());assertTrue(second.unexpected.toString(),second.unexpected.isEmpty())
        }}
    }
}
