package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HistoryReadFeedbackTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val dao get()=(compose.activity.application as HermesApplication).database.dao()
    private var previous:AppPreferences?=null
    @After fun reset() {
        compose.runOnUiThread {vm.forgetServer();previous?.let {old->vm.updatePreferences(vm.preferences.value.copy(theme=old.theme,chinese=old.chinese,pinyin=old.pinyin))}}
        compose.waitUntil(10000) {vm.preferences.value.demo && !vm.connecting.value}
    }
    private data class Plan(val status:Int=200,val content:String="Existing reply",val gate:CountDownLatch?=null)
    private class Fixture(val token:String):AutoCloseable {
        val server=MockWebServer()
        val reads=CopyOnWriteArrayList<String>()
        val unexpected=CopyOnWriteArrayList<String>()
        val writes=AtomicInteger()
        private val plans=ConcurrentHashMap<String,ConcurrentLinkedQueue<Plan>>()
        private val allPlans=CopyOnWriteArrayList<Plan>()
        fun queue(id:String,vararg values:Plan) {plans.computeIfAbsent(id) {ConcurrentLinkedQueue()}.addAll(values.toList());allPlans.addAll(values.toList())}
        fun count(id:String)=reads.count {it==id}
        init {
            server.dispatcher=object:Dispatcher() {
                private fun json(data:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(data.toString())
                override fun dispatch(request:RecordedRequest):MockResponse=try {
                    assertEquals("Bearer "+token,request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"))
                    if(request.method!="GET") {writes.incrementAndGet();error("Unexpected write")}
                    val url=request.requestUrl!!;assertTrue(url.encodedPath.startsWith("/p/history/"));val path=url.encodedPath.removePrefix("/p/history/")
                    when {
                        path=="v1/capabilities"->json(buildJsonObject {
                            put("model","fixture-model");put("features",buildJsonObject {put("session_resources",true);put("session_chat",true)})
                            put("endpoints",buildJsonObject {put("session",buildJsonObject {put("path","/api/sessions/{session_id}")})})
                        })
                        path=="api/jobs"->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                        path=="api/sessions"->{
                            assertEquals(setOf("limit","offset"),url.queryParameterNames);assertEquals("0",url.queryParameter("offset"))
                            json(buildJsonObject {put("has_more",false);put("data",buildJsonArray {
                                for((id,time) in listOf("history_a" to 100,"history_b" to 99)) add(buildJsonObject {put("id",id);put("title","Saved "+id);put("source","desktop");put("last_active",time)})
                            })})
                        }
                        path.startsWith("api/sessions/") && path.endsWith("/messages")->{
                            val id=path.removePrefix("api/sessions/").removeSuffix("/messages")
                            assertTrue(id in setOf("history_a","history_b"))
                            assertEquals(setOf("inline_images","limit","offset","order"),url.queryParameterNames)
                            assertEquals("false",url.queryParameter("inline_images"));assertEquals("500",url.queryParameter("limit"));assertEquals("0",url.queryParameter("offset"));assertEquals("oldest",url.queryParameter("order"))
                            val plan=plans[id]?.poll() ?: Plan(content="Existing reply "+id)
                            reads+=id
                            plan.gate?.let {check(it.await(30,TimeUnit.SECONDS)) {"Fixture gate was not released"}}
                            if(plan.status!=200) json(buildJsonObject {put("error",buildJsonObject {put("code",if(plan.status==404) "session_not_found" else "fixture_refused");put("message","private fixture text")})}).setResponseCode(plan.status)
                            else json(buildJsonObject {put("session_id",id);put("data",buildJsonArray {
                                add(buildJsonObject {put("id","user");put("role","user");put("content","Existing user "+id);put("timestamp",1)})
                                add(buildJsonObject {put("id","assistant");put("role","assistant");put("content",plan.content);put("timestamp",2)})
                            })})
                        }
                        else->error("Unexpected path: "+path)
                    }
                } catch(error:Throwable) {unexpected+=error.javaClass.simpleName+":"+error.message;MockResponse().setResponseCode(500)}
            }
        }
        override fun close() {allPlans.forEach {it.gate?.countDown()};server.close()}
    }
    private fun text(word:Words)=word.localized(vm.preferences.value)
    private fun connect(f:Fixture,bilingual:Boolean=false) {
        compose.waitUntil(10000) {!vm.connecting.value}
        if(previous==null) previous=vm.preferences.value
        val scope=ServerConfig(f.server.url("p/history/").toString(),f.token,true).scope
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(theme="STARHAVEN",chinese=bilingual,pinyin=bilingual));vm.configure(f.server.url("p/history/").toString(),f.token,true)}
        compose.waitUntil(10000) {!vm.connecting.value && !vm.sessionsLoading.value && vm.connectionStatus.value=="connected" && vm.profileScope.value==scope && vm.conversations.value.any {it.remoteId=="history_a"} && vm.preferences.value.chinese==bilingual}
        compose.onNodeWithTag("nav-chat").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("chat-model-status-open").fetchSemanticsNodes().size==1}
    }
    private fun conversation(id:String)=vm.conversations.value.single {it.remoteId==id}
    private fun select(id:String):Job {
        val key=conversation(id).key;var result:Job?=null
        compose.runOnUiThread {result=vm.select(key)}
        return checkNotNull(result)
    }
    private fun await(job:Job) {compose.waitUntil(10000) {job.isCompleted};assertFalse("History read was unexpectedly cancelled",job.isCancelled);compose.waitForIdle()}
    private fun seed(id:String):ChatMessage {
        val c=conversation(id);val message=ChatMessage("cached-"+UUID.randomUUID(),c.key,"assistant","Retained cached reply",1)
        runBlocking {dao.save(message)};return message
    }
    private fun assertClean(f:Fixture) {assertEquals(0,f.writes.get());assertTrue(f.unexpected.toString(),f.unexpected.isEmpty())}
    private fun assertCompactBanner() {
        val bounds=compose.onNodeWithTag("error-banner").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("The error summary must stay compact",bounds.bottom-bounds.top<=96.dp)
    }
    private fun assertNativeReply(expected:String) {
        compose.waitUntil(5000) {vm.messages.value.any {it.content==expected}}
        val message=vm.messages.value.single {it.content==expected}
        val body="message-body-"+message.id
        compose.onNodeWithTag("chat-messages").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("chat-messages").performScrollToNode(hasTestTag(body))
        compose.onNodeWithTag(body).assertIsDisplayed()
        var found=0;var fullyVisible=false
        compose.waitUntil(5000) {
            compose.runOnUiThread {
                found=0;fullyVisible=false
                fun visit(view:android.view.View) {
                    if(view is android.widget.TextView && view.text.toString()==expected) {
                        found++
                        val rect=android.graphics.Rect()
                        fullyVisible=view.isShown && view.width>0 && view.height>0 && view.getLocalVisibleRect(rect) &&
                            rect.left==0 && rect.top==0 && rect.right==view.width && rect.bottom==view.height
                    }
                    if(view is android.view.ViewGroup) for(index in 0 until view.childCount) visit(view.getChildAt(index))
                }
                visit(compose.activity.window.decorView)
            }
            found==1 && fullyVisible
        }
        assertEquals("One native reply TextView is required",1,found)
        assertTrue("The complete native reply must be visible",fullyVisible)
    }
    private fun shot(name:String) {
        compose.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(250,5000)
        val image=checkNotNull(automation.takeScreenshot());val file=File(compose.activity.getExternalFilesDir(null),"qa/history-feedback-"+name+"-"+UUID.randomUUID()+".png")
        file.parentFile!!.mkdirs()
        val roots=compose.onAllNodes(isRoot(),useUnmergedTree=true)
        val tree=roots.fetchSemanticsNodes().indices.joinToString("\n\n") {index->roots[index].printToString(maxDepth=16)}
        File(file.path+".semantics.txt").writeText(tree.take(100000))
        File(file.path+".state.json").writeText(buildJsonObject {
            put("selected",vm.selectedConversation.value?.remoteId.orEmpty());put("historyLoading",vm.historyLoading.value)
            put("messages",buildJsonArray {vm.messages.value.forEach {add(buildJsonObject {put("id",it.id);put("role",it.role);put("content",it.content.take(200))})}})
        }.toString())
        file.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }
    @Test fun missingHistoryKeepsCachedMessagesAndShowsTranslatedExplanation() {
        Fixture(credentials.uniqueToken("history-missing")).use {f->
            f.queue("history_a",Plan(404));connect(f,true);val cached=seed("history_a")
            await(select("history_a"))
            assertEquals(text(Words.HistoryUnavailable),vm.error.value)
            assertEquals(listOf(cached),runBlocking {dao.history(cached.sessionKey)})
            compose.onNodeWithTag("error-banner-summary").assertIsDisplayed().assertTextEquals(text(Words.HistoryUnavailable))
            assertCompactBanner()
            compose.onNodeWithTag("error-details-open").assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("error-details-message").assertIsDisplayed().assertTextEquals(text(Words.HistoryUnavailable))
            compose.onNodeWithTag("error-details-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            shot("details")
            compose.onNodeWithTag("error-details-close").performClick()
            assertEquals(text(Words.HistoryUnavailable),vm.error.value)
            try {assertNativeReply(cached.content)} finally {shot("missing")}
            compose.onNodeWithTag("chat-messages").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("chat-read-only").assertIsDisplayed()
            assertFalse(vm.historyLoading.value);assertEquals(listOf("history_a"),f.reads.toList());assertClean(f)
        }
    }
    @Test fun readableSelectionClearsPreviousHistoryBanner() {
        Fixture(credentials.uniqueToken("history-next")).use {f->
            f.queue("history_a",Plan(404));connect(f,true);val cached=seed("history_a")
            await(select("history_a"));assertEquals(text(Words.HistoryUnavailable),vm.error.value)
            compose.onNodeWithTag("error-details-open").performClick()
            compose.onNodeWithTag("error-details-message").assertIsDisplayed()
            await(select("history_b"));compose.waitUntil(5000) {vm.messages.value.any {it.content=="Existing reply history_b"}}
            assertNull(vm.error.value);compose.onNodeWithTag("error-banner").assertDoesNotExist()
            compose.onNodeWithTag("error-details-message").assertDoesNotExist()
            try {assertNativeReply("Existing reply history_b")} finally {shot("recovered")}
            compose.onNodeWithTag("chat-read-only").assertIsDisplayed()
            assertEquals(listOf(cached),runBlocking {dao.history(cached.sessionKey)})
            assertEquals(listOf("history_a","history_b"),f.reads.toList());assertClean(f)
        }
    }
    @Test fun longErrorKeepsChatVisibleAndFullDetailsScrollable() {
        Fixture(credentials.uniqueToken("history-long-error")).use {f->
            connect(f,true);await(select("history_a"))
            val message=(1..80).joinToString("\n") {"Synthetic error line "+it}+"\nFinal diagnostic line"
            compose.runOnUiThread {vm.error.value=message}
            assertCompactBanner()
            compose.onNodeWithTag("error-banner-summary").assertIsDisplayed().assertTextEquals(message)
            assertNativeReply("Existing reply history_a")
            compose.onNodeWithTag("error-details-open").assertHeightIsAtLeast(48.dp).performClick()
            val details=compose.onNodeWithTag("error-details-message")
            details.assertIsDisplayed().assertTextEquals(message)
            val axis=details.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("Long error details must have a scrollable range",axis.maxValue()>0f)
            details.performSemanticsAction(SemanticsActions.ScrollBy) {scroll->assertTrue(scroll(0f,100000f))}
            compose.waitUntil(5000) {axis.value()>0f && axis.value()>=axis.maxValue()-1f}
            compose.onNodeWithTag("error-details-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            shot("long-details")
            compose.onNodeWithTag("error-details-close").performClick()
            assertEquals(message,vm.error.value)
            compose.onNodeWithTag("error-banner-dismiss").assertHeightIsAtLeast(48.dp).performClick()
            assertNull(vm.error.value);compose.onNodeWithTag("error-banner").assertDoesNotExist()
            assertNativeReply("Existing reply history_a")
            assertEquals(listOf("history_a"),f.reads.toList());assertClean(f)
        }
    }
    @Test fun oldFailureCannotReturnAfterLeavingAndReselectingSameChat() {
        val release=CountDownLatch(1)
        Fixture(credentials.uniqueToken("history-aba")).use {f->
            f.queue("history_a",Plan(404,gate=release),Plan(content="Fresh A reply"));connect(f)
            val old=select("history_a");compose.waitUntil(5000) {f.count("history_a")==1}
            await(select("history_b"));await(select("history_a"))
            release.countDown();await(old)
            assertNull(vm.error.value);assertEquals("history_a",vm.selectedConversation.value!!.remoteId)
            assertTrue(vm.messages.value.any {it.content=="Fresh A reply"});assertFalse(vm.historyLoading.value)
            assertEquals(2,f.count("history_a"));assertEquals(1,f.count("history_b"));assertClean(f)
        }
    }
    @Test fun oldSuccessCannotClearCurrentFailureOrLoading() {
        val first=CountDownLatch(1);val second=CountDownLatch(1)
        Fixture(credentials.uniqueToken("history-late-success")).use {f->
            f.queue("history_a",Plan(content="Late A reply",gate=first));f.queue("history_b",Plan(404,gate=second));connect(f)
            val old=select("history_a");compose.waitUntil(5000) {f.count("history_a")==1}
            val current=select("history_b");compose.waitUntil(5000) {f.count("history_b")==1}
            compose.runOnUiThread {vm.send("x".repeat(64001))};assertEquals(text(Words.MessageTooLong),vm.error.value)
            first.countDown();await(old)
            assertEquals(text(Words.MessageTooLong),vm.error.value);assertTrue(vm.historyLoading.value)
            assertEquals("history_b",vm.selectedConversation.value!!.remoteId)
            second.countDown();await(current);assertEquals(text(Words.HistoryUnavailable),vm.error.value)
            assertFalse(vm.historyLoading.value);assertEquals(1,f.count("history_a"));assertEquals(1,f.count("history_b"));assertClean(f)
        }
    }
    @Test fun explicitRefreshRecoversWithoutRecreatingSessionOrLosingCache() {
        val release=CountDownLatch(1)
        Fixture(credentials.uniqueToken("history-refresh")).use {f->
            f.queue("history_a",Plan(404),Plan(content="Refreshed A reply",gate=release));connect(f);val cached=seed("history_a")
            await(select("history_a"));assertEquals(text(Words.HistoryUnavailable),vm.error.value)
            var job:Job?=null;compose.runOnUiThread {job=vm.refreshHistory()}
            compose.waitUntil(5000) {f.count("history_a")==2}
            assertTrue(vm.historyLoading.value);assertNull(vm.error.value);assertEquals(listOf(cached),runBlocking {dao.history(cached.sessionKey)})
            release.countDown();await(checkNotNull(job));compose.waitUntil(5000) {vm.messages.value.any {it.content=="Refreshed A reply"}}
            assertNull(vm.error.value);assertFalse(vm.historyLoading.value);assertEquals(2,f.count("history_a"));assertClean(f)
        }
    }
    @Test fun newConversationDropsLateHistoryFailureAndLoading() {
        val release=CountDownLatch(1)
        Fixture(credentials.uniqueToken("history-new")).use {f->
            f.queue("history_a",Plan(404,gate=release));connect(f);val cached=seed("history_a")
            val old=select("history_a");compose.waitUntil(5000) {f.count("history_a")==1}
            compose.runOnUiThread {vm.newChat()};assertFalse(vm.historyLoading.value)
            release.countDown();await(old);compose.waitUntil(5000) {vm.selectedConversation.value==null}
            assertNull(vm.error.value);assertEquals("idle",vm.runStatus.value);assertEquals(listOf(cached),runBlocking {dao.history(cached.sessionKey)})
            assertEquals(listOf("history_a"),f.reads.toList());assertClean(f)
        }
    }
    @Test fun switchedConnectionRejectsOldFailureWithSameRemoteId() {
        val release=CountDownLatch(1)
        Fixture(credentials.uniqueToken("history-profile-first")).use {first->
            Fixture(credentials.uniqueToken("history-profile-second")).use {second->
                first.queue("history_a",Plan(404,gate=release));connect(first);val cached=seed("history_a")
                val oldScope=vm.profileScope.value;val old=select("history_a");compose.waitUntil(5000) {first.count("history_a")==1}
                connect(second);val nextScope=vm.profileScope.value;assertNotEquals(oldScope,nextScope)
                await(select("history_a"));release.countDown();await(old)
                assertNull(vm.error.value);assertEquals(nextScope,vm.selectedConversation.value!!.scope)
                assertTrue(vm.messages.value.any {it.content=="Existing reply history_a"})
                assertEquals(listOf(cached),runBlocking {dao.history(cached.sessionKey)})
                assertEquals(1,first.count("history_a"));assertEquals(1,second.count("history_a"));assertClean(first);assertClean(second)
            }
        }
    }
}
