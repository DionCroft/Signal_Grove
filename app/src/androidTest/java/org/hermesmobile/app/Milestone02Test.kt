package org.hermesmobile.app

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Milestone02Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as HermesApplication
    private val vm get()=androidx.lifecycle.ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    @Before fun reset() {runBlocking {app.preferences.update(AppPreferences());app.database.dao().clearChats();app.database.dao().tasks().first().forEach {app.database.dao().delete(it)}}
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="demo" && vm.preferences.value.demo && !vm.busy.value && !vm.controlBusy.value}
        compose.waitForIdle()
    }
    private fun go(w:Words)=compose.onNodeWithContentDescription(w.localized(AppPreferences()),useUnmergedTree=true).performClick()
    private fun historyAction(word:Words) {
        val description=word.localized(AppPreferences())
        compose.onNodeWithTag("conversation-browser").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description,useUnmergedTree=true).assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun historyScreenshot() {
        compose.waitForIdle();val ui=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation;ui.waitForIdle(250,5000)
        val image=checkNotNull(ui.takeScreenshot());val file=File(compose.activity.getExternalFilesDir(null),"qa/legacy-history-row-"+java.util.UUID.randomUUID()+".png");file.parentFile!!.mkdirs()
        file.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }
    private fun sendScreenshot(name:String) {
        val shot=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");shot.parentFile!!.mkdirs()
        shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun sendState()="busy=${vm.busy.value}, controlling=${vm.controlBusy.value}, status=${vm.runStatus.value}, connection=${vm.connectionStatus.value}, connecting=${vm.connecting.value}, draftLength=${vm.draft.value.length}, error=${vm.error.value}"
    private fun awaitVisibleStableSend() {
        closeFocusedKeyboard();compose.waitForIdle()
        var previous:String?=null;var stableSince=0L;var lastFrame="unobserved"
        try {
            compose.waitUntil(5000) {
                val frame=runCatching {
                    val target=compose.onNodeWithContentDescription(Words.Send.localized(AppPreferences())).assertIsDisplayed().assertIsEnabled()
                    val bounds=target.fetchSemanticsNode().boundsInWindow
                    val visible=Rect();val origin=IntArray(2)
                    compose.runOnUiThread {val view=compose.activity.window.decorView;view.getWindowVisibleDisplayFrame(visible);view.getLocationOnScreen(origin)}
                    val button=Rect(kotlin.math.floor(bounds.left).toInt()+origin[0],kotlin.math.floor(bounds.top).toInt()+origin[1],kotlin.math.ceil(bounds.right).toInt()+origin[0],kotlin.math.ceil(bounds.bottom).toInt()+origin[1])
                    lastFrame="visible=${visible.toShortString()}, send=${button.toShortString()}"
                    if(button.isEmpty || !visible.contains(button) || vm.busy.value || vm.controlBusy.value || vm.connecting.value) null else lastFrame
                }.getOrNull()
                val now=android.os.SystemClock.uptimeMillis()
                if(frame==null || frame!=previous) {previous=frame;stableSince=now;false} else now-stableSince>=200
            }
        } catch(e:Throwable) {
            runCatching {sendScreenshot("demo-send-transition-failure")}
            throw AssertionError("Demo send control was not visible/stable: $lastFrame, ${sendState()}",e)
        }
    }
    @Test fun conversationSearchPinRenameAndLocalDeleteWork() {
        compose.onNodeWithText("New conversation").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Session sample")
        compose.waitUntil(5000) {vm.draft.value=="Session sample" && !vm.connecting.value}
        compose.runOnUiThread {
            compose.activity.currentFocus?.clearFocus()
            androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        if(android.os.Build.VERSION.SDK_INT>=30) compose.waitUntil(5000) {(androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0)==0}
        awaitVisibleStableSend()
        assertEquals("Session sample",vm.draft.value)
        compose.onNodeWithContentDescription(Words.Send.localized(AppPreferences())).assertIsDisplayed().assertIsEnabled().performClick()
        try {compose.waitUntil(5000) {vm.draft.value.isEmpty()}}
        catch(e:androidx.compose.ui.test.ComposeTimeoutException) {runCatching {sendScreenshot("demo-send-not-admitted")};fail("Demo session send was not admitted: ${sendState()}")}
        try {compose.waitUntil(15000) {!vm.busy.value && vm.runStatus.value=="completed"}}
        catch(e:androidx.compose.ui.test.ComposeTimeoutException) {runCatching {sendScreenshot("demo-send-not-completed")};fail("Demo session send: ${sendState()}")}
        compose.onNodeWithContentDescription(Words.Completed.localized(AppPreferences())).assertExists()
        go(Words.History)
        compose.onNode(hasSetTextAction() and hasText(Words.Search.localized(AppPreferences()))).performTextInput("sample")
        closeDialogKeyboard()
        compose.onNodeWithTag("conversation-browser").performScrollToNode(hasContentDescription(Words.Pin.localized(AppPreferences())))
        compose.onNodeWithContentDescription(Words.Pin.localized(AppPreferences()),useUnmergedTree=true).assertIsDisplayed().assertIsEnabled()
        historyScreenshot()
        historyAction(Words.Pin);historyAction(Words.Rename)
        compose.onNode(hasSetTextAction() and hasText(Words.ConversationTitle.localized(AppPreferences()))).performTextReplacement("Renamed sample")
        closeDialogKeyboard()
        compose.onNodeWithText(Words.Save.localized(AppPreferences())).assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(5000) {runBlocking {app.database.dao().conversations("demo").first().singleOrNull()?.title=="Renamed sample"}}
        assertTrue(runBlocking {app.database.dao().conversations("demo").first().single().pinned})
        closeDialogKeyboard()
        historyAction(Words.Delete)
        compose.onNode(hasText(Words.Delete.localized(AppPreferences())) and hasClickAction()).assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(5000) {runBlocking {app.database.dao().conversations("demo").first().isEmpty()}}
    }
    @Test fun notesAreSavedAndCanBeSearched() {
        go(Words.Tasks)
        compose.onNodeWithText(Words.AddTask.localized(AppPreferences())).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(Words.TaskTitle.localized(AppPreferences()))).performTextInput("Notes task")
        compose.onNode(hasSetTextAction() and hasText(Words.Notes.localized(AppPreferences()))).performScrollTo().performTextInput("Searchable details")
        compose.onNodeWithText(Words.Save.localized(AppPreferences())).performClick()
        compose.onNodeWithTag("task-list").performScrollToNode(hasSetTextAction() and hasText(Words.Search.localized(AppPreferences())))
        compose.onNode(hasSetTextAction() and hasText(Words.Search.localized(AppPreferences()))).performTextInput("details")
        compose.onNodeWithTag("task-list").performScrollToNode(hasText("Notes task"))
        compose.onNodeWithText("Notes task").assertExists()
        assertEquals("Searchable details",runBlocking {app.database.dao().tasks().first().single().notes})
    }
    @Test fun scheduledJobDetailsOpenFromTheCachedList() {
        go(Words.Automation)
        compose.onNodeWithTag("job-list").performScrollToNode(hasText("Weekly reflection · Demo"))
        compose.onNodeWithText(Words.JobDetails.localized(AppPreferences())).performScrollTo().performClick()
        compose.onNodeWithText("Review completed local goals. This sample job never runs.").assertExists()
    }
}
