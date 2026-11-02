package org.hermesmobile.app

import android.graphics.Bitmap
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
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="demo" && vm.preferences.value.demo && vm.tasks.value.isEmpty()}
        compose.waitForIdle()
    }
    private fun go(word: Words) {
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription(word.localized(AppPreferences()),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription(word.localized(AppPreferences()),useUnmergedTree=true).performClick()
    }
    private fun screenshot(name: String) {
        compose.runOnUiThread { androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime()) }
        compose.waitForIdle()
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun demoConversationStreamsAndEveryDestinationOpens() {
        compose.onNodeWithText("New conversation",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("A test question")
        compose.waitUntil(5000) {vm.draft.value=="A test question" && !vm.connecting.value}
        compose.runOnUiThread {
            compose.activity.currentFocus?.clearFocus()
            androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        // Pre-30 IME insets are inferred and can retain a stale height after dismissal.
        if(android.os.Build.VERSION.SDK_INT>=30) compose.waitUntil(5000) {(androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0)==0}
        compose.waitForIdle()
        compose.onNodeWithContentDescription(Words.Send.localized(AppPreferences())).assertIsEnabled().performClick()
        try {compose.waitUntil(15000) {!vm.busy.value && vm.runStatus.value=="completed"}}
        catch(e:androidx.compose.ui.test.ComposeTimeoutException) {fail("Demo send: busy=${vm.busy.value}, status=${vm.runStatus.value}, connection=${vm.connectionStatus.value}, connecting=${vm.connecting.value}, draftLength=${vm.draft.value.length}, error=${vm.error.value}")}
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
        compose.onNodeWithTag("task-list").performScrollToNode(hasText("QA edited"))
        compose.onNodeWithContentDescription(Words.CompleteTask.localized(AppPreferences())).performScrollTo().assertIsDisplayed().assertIsOff().performClick()
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
