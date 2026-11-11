package org.hermesmobile.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.local.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual view models, Room receipts and private activity; no model load or remote write. */
class PhoneDraftRoutingTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private val rows=mutableListOf<String>()
    private val localChats=mutableListOf<String>()
    private var language:Pair<Boolean,Boolean>?=null
    private fun snapshot(name:String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100,3000)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        val file=File(app.getExternalFilesDir(null),"qa/phone-draft-$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun seedPhoneHistory() {
        val prior=LocalConversation(modelSha256="b".repeat(64),updatedAt=1,exchanges=listOf(LocalExchange("Earlier phone question","Earlier phone answer",LocalReplyState.COMPLETE)))
        localChats+=prior.id;LocalConversationStore(File(app.noBackupFilesDir,"local-ai/chats")).save(prior)
        compose.waitUntil(10000) {!app.localAi.state.value.busy}
        compose.runOnUiThread {app.localAi.openChat(prior.id)}
        compose.waitUntil(10000) {!app.localAi.state.value.busy && app.localAi.state.value.chat?.id==prior.id}
    }
    private var openedLocal:LocalAiActivity?=null
    private fun resumedLocal():LocalAiActivity? {
        val value=AtomicReference<LocalAiActivity?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            value.set(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<LocalAiActivity>().singleOrNull())
        }
        return value.get()
    }
    private fun ready() {
        compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo}
        compose.runOnUiThread {vm.newChat()}
    }
    private fun history():Map<String,String> {
        val root=File(app.noBackupFilesDir,"local-ai/chats")
        return root.listFiles().orEmpty().filter {it.isFile}.associate {it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") {b->"%02x".format(b)}}
    }
    private fun route(review:UnsentDraft.Review,text:String):String? {
        val result=AtomicReference<String?>();var job:Job?=null
        compose.runOnUiThread {job=compose.activity.lifecycleScope.launch {result.set(vm.routePhoneDraft(review,text))}}
        runBlocking {withTimeout(10000) {job!!.join()}}
        return result.get()
    }
    private fun seed(status:String="completed"):Conversation = runBlocking {
        val id="phone-route-${UUID.randomUUID()}";val c=Conversation("demo:$id","demo",id,"Synthetic routing fixture",status=status)
        rows+=c.key;app.database.dao().save(c);c
    }
    private fun select(c:Conversation) {
        compose.runOnUiThread {vm.select(c.key)}
        compose.waitUntil(10000) {!vm.historyLoading.value && vm.selectedConversation.value==c}
    }
    @After fun restore() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {openedLocal?.finish()}
        compose.runOnUiThread {
            vm.forgetServer()
            language?.let {(zh,pin)->vm.updatePreferences(vm.preferences.value.copy(chinese=zh,pinyin=pin))}
        }
        compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo && language?.let {(zh,pin)->vm.preferences.value.chinese==zh && vm.preferences.value.pinyin==pin}!=false}
        compose.waitUntil(10000) {!app.localAi.state.value.busy}
        compose.runOnUiThread {app.localAi.startDraft()}
        localChats.forEach {LocalConversationStore(File(app.noBackupFilesDir,"local-ai/chats")).delete(it)}
        runBlocking {rows.forEach {app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions WHERE [key]=?",arrayOf(it))}}
    }
    @Test fun cancelReviewKeepsTheRemoteDraftAndPhoneFilesUnchanged() {
        ready();val before=history();language=vm.preferences.value.let {it.chinese to it.pinyin}
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=true,pinyin=true))}
        compose.waitUntil(10000) {vm.preferences.value.chinese && vm.preferences.value.pinyin}
        compose.onNodeWithTag("nav-chat").performClick()
        compose.runOnUiThread {vm.changeDraft("Fresh unsent question")}
        compose.onNodeWithTag("phone-draft-open").performClick()
        snapshot("review-top")
        compose.onNodeWithTag("phone-draft-text").performScrollTo().performClick().assertTextEquals("Fresh unsent question")
        compose.waitUntil(5000) {focusedDialogKeyboardVisible()}
        var previous:FocusedDialogViewport?=null;var stableAt=0L
        compose.waitUntil(5000) {
            val current=focusedDialogViewport();val now=android.os.SystemClock.uptimeMillis()
            if(previous!=current) {previous=current;stableAt=now;false} else now-stableAt>=250
        }
        val viewport=checkNotNull(previous)
        for((tag,word) in listOf("phone-draft-confirm" to Words.PhoneDraftConfirm,"phone-draft-cancel" to Words.Cancel)) {
            val control=compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInWindow
            assertTrue("Entire control above native IME: $tag; $viewport",control.left+viewport.screenOffsetX>=viewport.visible.left && control.right+viewport.screenOffsetX<=viewport.visible.right && control.top+viewport.screenOffsetY>=viewport.visible.top && control.bottom+viewport.screenOffsetY<=viewport.visible.bottom)
            val caption=word.localized(vm.preferences.value);val native=focusedDialogCaptionScreenBounds(caption,caption)
            assertTrue("Complete native caption above IME: $tag; $native; $viewport",viewport.visible.contains(native))
        }
        snapshot("review-bottom")
        val cancel=compose.onNodeWithTag("phone-draft-cancel").fetchSemanticsNode().boundsInWindow.center
        tapFocusedDialogWindowPoint(cancel.x,cancel.y)
        compose.waitUntil(5000) {compose.onAllNodesWithTag("phone-draft-review").fetchSemanticsNodes().isEmpty()}
        assertEquals("Fresh unsent question",vm.draft.value);assertNotNull(vm.reviewPhoneDraft());assertEquals(before,history())
        assertTrue(vm.selected.value.isBlank());assertNull(app.localAi.state.value.loaded)
    }
    @Test fun editedReviewOpensOnlyANewPhoneDraftWithoutSubmittingOrLosingHistory() {
        ready();seedPhoneHistory();val before=history()
        compose.onNodeWithTag("nav-chat").performClick()
        compose.runOnUiThread {vm.changeDraft("Remote draft remains")}
        compose.onNodeWithTag("phone-draft-open").performClick()
        compose.onNodeWithTag("phone-draft-text").performTextReplacement("Reviewed phone question")
        compose.onNodeWithTag("phone-draft-confirm").assertIsDisplayed().performClick()
        compose.waitUntil(15000) {resumedLocal()!=null}
        openedLocal=resumedLocal()!!
        val token=openedLocal!!.intent.getStringExtra(LocalAiActivity.DRAFT_TOKEN)!!
        assertEquals(setOf(LocalAiActivity.DRAFT_TOKEN),openedLocal!!.intent.extras!!.keySet())
        compose.waitUntil(15000) {compose.onAllNodesWithTag("phone-draft-notice").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("phone-draft").assertTextEquals("Reviewed phone question")
        assertNull(app.phoneDrafts.take(token));assertEquals(before,history());assertNull(app.localAi.state.value.loaded)
        assertNull(app.localAi.state.value.chat);assertEquals("Remote draft remains",vm.draft.value);assertTrue(vm.selected.value.isBlank())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {openedLocal!!.recreate()}
        compose.waitUntil(15000) {resumedLocal()!=null && resumedLocal()!==openedLocal}
        openedLocal=resumedLocal()!!
        compose.onNodeWithTag("phone-draft").assertTextEquals("Reviewed phone question")
        assertEquals(before,history())
    }
    @Test fun staleReviewAndAuthoritativeUnknownReceiptCannotRoute() {
        ready();val c=seed();select(c);var review:UnsentDraft.Review?=null
        compose.runOnUiThread {vm.changeDraft("New question");review=vm.reviewPhoneDraft();vm.changeDraft("Changed question")}
        assertNull(route(review!!,"Old review"))
        compose.runOnUiThread {review=vm.reviewPhoneDraft()}
        val pending=c.copy(status="unknown",pendingInput="Uncertain server work",pendingKey="original-key",pendingRequestHash="a".repeat(64))
        runBlocking {app.database.dao().save(pending)}
        // Even if the view's collected status is stale, the final Room read must reject it.
        assertNull(route(review!!,"Fresh text"))
        assertEquals(pending,runBlocking {app.database.dao().conversation(c.key)})
        assertEquals("Changed question",vm.draft.value)
        compose.waitUntil(10000) {vm.selectedConversation.value?.status=="unknown"}
        assertNull(vm.reviewPhoneDraft())
    }
    @Test fun stagedFailedRetryAndResponseEndedAreNotNewUnsentWork() {
        ready();val failed=seed("failed").copy(pendingInput="Prior failed request",pendingKey="prior-request")
        runBlocking {app.database.dao().save(failed)};select(failed)
        compose.runOnUiThread {vm.prepareRetry()}
        compose.waitUntil(10000) {!vm.controlBusy.value}
        assertNull("Retry staging: ${vm.error.value}",vm.error.value)
        assertEquals("Prior failed request",vm.draft.value)
        assertNull(vm.reviewPhoneDraft());assertEquals(failed,runBlocking {app.database.dao().conversation(failed.key)})
        compose.runOnUiThread {vm.changeDraft("An explicitly edited new question")}
        assertNotNull(vm.reviewPhoneDraft())
        val ended=failed.copy(status="response_finished");runBlocking {app.database.dao().save(ended)}
        compose.waitUntil(10000) {vm.selectedConversation.value?.status=="response_finished"}
        assertNull(vm.reviewPhoneDraft())
    }
    @Test fun imageDraftAndSelectionChangeInvalidateRoutingReview() {
        ready();var review:UnsentDraft.Review?=null
        compose.runOnUiThread {vm.changeDraft("Text only");review=vm.reviewPhoneDraft();vm.imageDraft.value=ImageDraft("demo","","route-fixture",listOf(ImageRef("fixture",width=1,height=1)))}
        assertNull(vm.reviewPhoneDraft());assertNull(route(review!!,"Text only"))
        compose.runOnUiThread {vm.clearDraftImages();review=vm.reviewPhoneDraft()}
        val c=seed();select(c)
        assertNull(route(review!!,"Text only"));assertEquals("Text only",vm.draft.value)
    }
    @Test fun missingOrConsumedTokenCannotReplaceAPhoneDraftOrStartWork() {
        ready();val before=history();val intent=Intent(app,LocalAiActivity::class.java).putExtra(LocalAiActivity.DRAFT_TOKEN,UUID.randomUUID().toString())
        compose.runOnUiThread {compose.activity.startActivity(intent)}
        compose.waitUntil(15000) {resumedLocal()!=null};openedLocal=resumedLocal()!!
        compose.waitUntil(15000) {compose.onAllNodesWithTag("phone-draft-notice").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("phone-draft").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        compose.onNodeWithTag("phone-draft-notice").assertContentDescriptionEquals(Words.PhoneDraftNotReady.localized(vm.preferences.value)).performClick()
        compose.onNodeWithTag("phone-draft-info-text").assertTextEquals(Words.PhoneDraftExpired.localized(vm.preferences.value))
        compose.onNodeWithTag("phone-draft-info-close").performClick()
        assertEquals(before,history());assertNull(app.localAi.state.value.loaded);assertNull(app.localAi.state.value.chat)
    }
}
