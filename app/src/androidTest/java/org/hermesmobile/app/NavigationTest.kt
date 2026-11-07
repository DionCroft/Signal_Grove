package org.hermesmobile.app

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as HermesApplication
    private val vm get()=androidx.lifecycle.ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    @Before fun reset() {
        runBlocking {app.preferences.update(AppPreferences());app.database.dao().clearChats();app.database.dao().tasks().first().forEach {app.database.dao().delete(it)}}
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        val defaults=AppPreferences()
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="demo" && vm.preferences.value.demo && vm.tasks.value.isEmpty() && vm.preferences.value.chinese==defaults.chinese && vm.preferences.value.pinyin==defaults.pinyin && vm.preferences.value.theme==defaults.theme}
        compose.waitForIdle()
    }
    private fun go(word: Words) {
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription(word.localized(AppPreferences()),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription(word.localized(AppPreferences()),useUnmergedTree=true).performClick()
    }
    private fun screenshot(name: String,hideKeyboard:Boolean=true) {
        if(hideKeyboard) compose.runOnUiThread { androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime()) }
        compose.waitForIdle()
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private var taskCompletionGeometry="unobserved"
    private fun taskCompletionState()="editorClosed=${compose.onAllNodes(isDialog(),useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}, editedRow=${vm.tasks.value.any {it.title=="QA edited"}}, $taskCompletionGeometry"
    private fun awaitTaskCompletionCheckbox() {
        val checkbox=hasContentDescription(Words.CompleteTask.localized(AppPreferences()))
        var previous:String?=null;var stableSince=0L
        try {
            compose.waitUntil(5000) {compose.onAllNodes(isDialog(),useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
            closeFocusedKeyboard();compose.waitForIdle()
            // A leaving dialog/IME can resize the list after its descendant was located.
            // Establish the viewport first, then use the row's existing stable LazyColumn key.
            compose.waitUntil(5000) {
                val frame=runCatching {
                    val bounds=compose.onNodeWithTag("task-list").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
                    val visible=Rect();var focused=false
                    compose.runOnUiThread {val view=compose.activity.window.decorView;view.getWindowVisibleDisplayFrame(visible);focused=view.hasWindowFocus()}
                    taskCompletionGeometry="viewport="+visible.toShortString()+", list="+bounds+", focused="+focused
                    if(bounds.isEmpty || visible.isEmpty || !focused) null else taskCompletionGeometry
                }.getOrElse {taskCompletionGeometry="viewport lookup: "+it.javaClass.simpleName;null}
                val now=android.os.SystemClock.uptimeMillis()
                if(frame==null || frame!=previous) {previous=frame;stableSince=now;false} else now-stableSince>=500
            }
            val taskId=vm.tasks.value.single {it.title=="QA edited"}.id
            compose.onNodeWithTag("task-list").performScrollToKey(taskId)
            previous=null;stableSince=0L
            // One scroll, then observe the same visible/enabled checkbox before one real click.
            compose.waitUntil(5000) {
                val frame=runCatching {
                    val target=compose.onNode(checkbox)
                    val bounds=target.fetchSemanticsNode().boundsInWindow
                    val visible=Rect();val origin=IntArray(2)
                    compose.runOnUiThread {val view=compose.activity.window.decorView;view.getWindowVisibleDisplayFrame(visible);view.getLocationOnScreen(origin)}
                    val control=Rect(kotlin.math.floor(bounds.left).toInt()+origin[0],kotlin.math.floor(bounds.top).toInt()+origin[1],kotlin.math.ceil(bounds.right).toInt()+origin[0],kotlin.math.ceil(bounds.bottom).toInt()+origin[1])
                    taskCompletionGeometry="visible=${visible.toShortString()}, checkbox=${control.toShortString()}"
                    target.assertIsDisplayed().assertIsEnabled()
                    if(control.isEmpty || !visible.contains(control) || compose.onAllNodes(isDialog(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()) null else taskCompletionGeometry
                }.getOrNull()
                val now=android.os.SystemClock.uptimeMillis()
                if(frame==null || frame!=previous) {previous=frame;stableSince=now;false} else now-stableSince>=200
            }
        } catch(e:Throwable) {
            runCatching {screenshot("task-completion-transition-failure",hideKeyboard=false)}
            throw AssertionError("Task completion control was not visible/stable: ${taskCompletionState()}",e)
        }
    }
    private val demoSendRun=java.util.UUID.randomUUID().toString()
    private val demoSendTrace=mutableListOf<kotlinx.serialization.json.JsonObject>()
    private val demoSendViewportFrames=mutableListOf<String>()
    // Diagnostic reads only: no sleep, retry, forced send or changed success condition.
    private fun observeDemoSend(stage:String) {
        check(stage in setOf("before-ime-dismiss","before-pointer","after-pointer","timeout"))
        val native=Rect();val screen=IntArray(2);val window=IntArray(2)
        var focused=false;var imeBottom=-1;var width=0;var height=0
        compose.runOnUiThread {
            val view=compose.activity.window.decorView
            view.getWindowVisibleDisplayFrame(native);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
            focused=view.hasWindowFocus();width=view.width;height=view.height
            imeBottom=androidx.core.view.ViewCompat.getRootWindowInsets(view)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: -1
        }
        var bounds="unobserved";var lookup="ok"
        try {bounds=compose.onNodeWithContentDescription(Words.Send.localized(AppPreferences())).fetchSemanticsNode().boundsInWindow.toString()}
        catch(error:Exception) {lookup=error.javaClass.simpleName}
        val observation=kotlinx.serialization.json.buildJsonObject {
            put("stage",kotlinx.serialization.json.JsonPrimitive(stage));put("uptimeMs",kotlinx.serialization.json.JsonPrimitive(android.os.SystemClock.uptimeMillis()))
            put("visibleFrame",kotlinx.serialization.json.JsonPrimitive(native.toShortString()));put("sendBoundsInWindow",kotlinx.serialization.json.JsonPrimitive(bounds))
            put("screenOffsetX",kotlinx.serialization.json.JsonPrimitive(screen[0]-window[0]));put("screenOffsetY",kotlinx.serialization.json.JsonPrimitive(screen[1]-window[1]))
            put("decorWidth",kotlinx.serialization.json.JsonPrimitive(width));put("decorHeight",kotlinx.serialization.json.JsonPrimitive(height));put("imeBottom",kotlinx.serialization.json.JsonPrimitive(imeBottom))
            put("windowFocused",kotlinx.serialization.json.JsonPrimitive(focused));put("lookup",kotlinx.serialization.json.JsonPrimitive(lookup))
            put("busy",kotlinx.serialization.json.JsonPrimitive(vm.busy.value));put("controlBusy",kotlinx.serialization.json.JsonPrimitive(vm.controlBusy.value));put("connecting",kotlinx.serialization.json.JsonPrimitive(vm.connecting.value))
            put("scopeIsDemo",kotlinx.serialization.json.JsonPrimitive(vm.profileScope.value=="demo"));put("draftLength",kotlinx.serialization.json.JsonPrimitive(vm.draft.value.length))
            put("viewportFrames",kotlinx.serialization.json.JsonArray(demoSendViewportFrames.map {kotlinx.serialization.json.JsonPrimitive(it)}))
        }
        check(demoSendTrace.size<4);demoSendTrace+=observation
        val report=kotlinx.serialization.json.JsonArray(demoSendTrace).toString();check(report.toByteArray().size<=16384)
        val directory=File(compose.activity.getExternalFilesDir(null),"qa");check(directory.isDirectory || directory.mkdirs())
        File(directory,"navigation-demo-send-$demoSendRun.json").writeText(report)
    }
    private fun awaitDemoSendControl() {
        var previous:String?=null;var stableSince=0L
        try {
            compose.waitUntil(5000) {
                var ready=false
                val geometry=try {
                    val target=compose.onNodeWithContentDescription(Words.Send.localized(AppPreferences()))
                    val bounds=target.assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInWindow
                    val visible=Rect();val screen=IntArray(2);val window=IntArray(2)
                    var focused=false;var expectedBottom=0;var imeBottom=-1
                    compose.runOnUiThread {
                        val view=compose.activity.window.decorView
                        view.getWindowVisibleDisplayFrame(visible);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
                        focused=view.hasWindowFocus()
                        val insets=androidx.core.view.ViewCompat.getRootWindowInsets(view)
                        val metrics=android.util.DisplayMetrics()
                        @Suppress("DEPRECATION")
                        view.display.getRealMetrics(metrics)
                        expectedBottom=metrics.heightPixels-(insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0)
                        imeBottom=insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: -1
                    }
                    val control=Rect(kotlin.math.floor(bounds.left).toInt()+screen[0]-window[0],kotlin.math.floor(bounds.top).toInt()+screen[1]-window[1],kotlin.math.ceil(bounds.right).toInt()+screen[0]-window[0],kotlin.math.ceil(bounds.bottom).toInt()+screen[1]-window[1])
                    // Pre-30 inferred IME insets can remain stale; the real visible frame must
                    // return to the system-bar boundary, and contain the entire stable control.
                    val imeSettled=android.os.Build.VERSION.SDK_INT<30 || imeBottom==0
                    ready=focused && imeSettled && !control.isEmpty && visible.contains(control) && expectedBottom>0 && visible.bottom>=expectedBottom && !vm.busy.value && !vm.controlBusy.value && !vm.connecting.value && vm.draft.value=="A test question"
                    "visible=${visible.toShortString()}, expectedBottom=$expectedBottom, control=${control.toShortString()}, focused=$focused, imeBottom=$imeBottom, ready=$ready"
                } catch(error:Throwable) {"lookup="+error.javaClass.simpleName}
                if(demoSendViewportFrames.lastOrNull()!=geometry) {
                    if(demoSendViewportFrames.size==8) demoSendViewportFrames.removeAt(0)
                    demoSendViewportFrames+=geometry
                }
                val now=android.os.SystemClock.uptimeMillis()
                if(!ready || geometry!=previous) {previous=geometry;stableSince=now;false} else now-stableSince>=200
            }
        } catch(error:Throwable) {
            runCatching {captureDemoSendFailure()}
            throw AssertionError("Demo send control was not visible/stable: $demoSendViewportFrames",error)
        }
    }
    private fun captureDemoSendFailure() {
        val bitmap=checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val directory=File(compose.activity.getExternalFilesDir(null),"qa");check(directory.isDirectory || directory.mkdirs())
            File(directory,"navigation-demo-send-failure-$demoSendRun.png").outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        } finally {bitmap.recycle()}
    }
    @Test fun demoConversationStreamsAndEveryDestinationOpens() {
        compose.onNodeWithText("New conversation",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("A test question")
        compose.waitUntil(5000) {vm.draft.value=="A test question" && !vm.connecting.value}
        observeDemoSend("before-ime-dismiss")
        compose.runOnUiThread {
            compose.activity.currentFocus?.clearFocus()
            androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        // Pre-30 IME insets are inferred and can retain a stale height after dismissal.
        if(android.os.Build.VERSION.SDK_INT>=30) compose.waitUntil(5000) {(androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0)==0}
        compose.waitForIdle()
        closeFocusedKeyboard();awaitDemoSendControl()
        observeDemoSend("before-pointer")
        compose.onNodeWithContentDescription(Words.Send.localized(AppPreferences())).assertIsEnabled().performClick()
        observeDemoSend("after-pointer")
        try {compose.waitUntil(15000) {!vm.busy.value && vm.runStatus.value=="completed"}}
        catch(e:androidx.compose.ui.test.ComposeTimeoutException) {
            runCatching {observeDemoSend("timeout")};runCatching {captureDemoSendFailure()}
            fail("Demo send: busy=${vm.busy.value}, status=${vm.runStatus.value}, connection=${vm.connectionStatus.value}, connecting=${vm.connecting.value}, controlBusy=${vm.controlBusy.value}, draftLength=${vm.draft.value.length}, error=${vm.error.value}; geometry=$demoSendTrace")
        }
        compose.onNodeWithContentDescription(Words.Completed.localized(AppPreferences())).assertExists()
        screenshot("chat-demo")
        go(Words.Tasks);compose.onNodeWithText("Personal tasks stay on this device.",substring=true).assertExists()
        go(Words.Automation);compose.onNodeWithText("Changes affect the selected Hermes server.",substring=true).assertExists()
        go(Words.Voice);compose.onNodeWithText("Tap to speak. Review",substring=true).assertExists()
        go(Words.Settings);compose.onNodeWithText("Hermes connection").assertExists()
        go(Words.Home);compose.onNodeWithText("A little space to think.").assertExists()
    }
    @Test fun taskCreateEditCompleteAndDeletePersists() {
        go(Words.Tasks)
        compose.onNodeWithText(Words.AddTask.localized(AppPreferences())).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(Words.TaskTitle.localized(AppPreferences()))).performTextInput("QA task")
        compose.onNode(hasSetTextAction() and hasText(Words.DueDate.localized(AppPreferences()))).performScrollTo().performTextInput("2026-11-30")
        compose.onNodeWithText(Words.Save.localized(AppPreferences())).performClick()
        compose.waitUntil(5000) {vm.tasks.value.any {it.title=="QA task"}}
        compose.runOnUiThread {androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
        compose.waitForIdle()
        compose.onNodeWithTag("task-list").performScrollToNode(hasText("QA task"))
        compose.onNodeWithText("QA task").assertExists()
        compose.onNodeWithContentDescription(Words.Edit.localized(AppPreferences())).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(Words.TaskTitle.localized(AppPreferences()))).performTextReplacement("QA edited")
        compose.onNodeWithText(Words.Save.localized(AppPreferences())).performClick()
        compose.waitUntil(5000) {vm.tasks.value.any {it.title=="QA edited"}}
        // The edited title can reach Room while the IME/dialog is still leaving the screen.
        // Wait for the actual checkbox to be reachable before the one real pointer click.
        compose.runOnUiThread {
            compose.activity.currentFocus?.clearFocus()
            androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        if(android.os.Build.VERSION.SDK_INT>=30) compose.waitUntil(5000) {(androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0)==0}
        compose.waitForIdle()
        awaitTaskCompletionCheckbox()
        try {compose.onNodeWithContentDescription(Words.CompleteTask.localized(AppPreferences())).assertIsDisplayed().assertIsEnabled().assertIsOff().performClick()}
        catch(e:Throwable) {runCatching {screenshot("task-completion-pointer-failure")};throw AssertionError("Task completion pointer step: ${taskCompletionState()}",e)}
        compose.waitUntil(10000) {vm.tasks.value.singleOrNull {it.title=="QA edited"}?.completed==true}
        compose.onNodeWithContentDescription(Words.CompleteTask.localized(AppPreferences())).assertIsOn()
        assertTrue(runBlocking {app.database.dao().tasks().first().first {it.title=="QA edited"}.completed})
        screenshot("tasks")
        compose.onNodeWithContentDescription(Words.Delete.localized(AppPreferences())).performScrollTo().performClick()
        compose.onNode(hasText(Words.Delete.localized(AppPreferences())) and hasClickAction()).performClick()
        compose.waitUntil(5000) {vm.tasks.value.none {it.title=="QA edited"}}
        compose.onNodeWithText("QA edited").assertDoesNotExist()
    }
    @Test fun allFourThemesPersistAndRender() {
        HermesTheme.entries.forEach {theme ->
            go(Words.Settings)
            compose.onNodeWithText(theme.title).performScrollTo().performClick()
            compose.waitUntil(5000) {runBlocking {app.preferences.flow.first().theme==theme.name}}
            go(Words.Home)
            compose.onNodeWithText("A little space to think.").assertExists()
            screenshot("home-${theme.name.lowercase()}")
        }
    }
    @Test fun languageSettingsPersistEachDisplayMode() {
        go(Words.Settings)
        compose.onNodeWithText(Words.Pinyin.localized(AppPreferences())).performScrollTo()
        // Preference updates use the same persistent layer as the switches.
        listOf(AppPreferences(chinese=false,pinyin=false),AppPreferences(chinese=true,pinyin=false),AppPreferences(chinese=true,pinyin=true)).forEach {p ->
            val retained=runBlocking {app.preferences.flow.first().runtimeChoices}
            runBlocking {app.preferences.update(p)};compose.waitForIdle()
            if(p.chinese) compose.onNodeWithText(Words.Settings.zh).assertExists() else compose.onNodeWithText(Words.Settings.zh).assertDoesNotExist()
            if(p.chinese && p.pinyin) compose.onNodeWithText(Words.Settings.pin).assertExists() else compose.onNodeWithText(Words.Settings.pin).assertDoesNotExist()
            val actual=runBlocking {app.preferences.flow.first()}
            assertEquals(p,actual.copy(runtimeChoices=p.runtimeChoices));assertEquals(retained,actual.runtimeChoices)
        }
    }
}
