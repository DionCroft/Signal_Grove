package org.hermesmobile.app

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
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="demo" && vm.preferences.value.demo}
        compose.waitForIdle()
    }
    private fun go(w:Words)=compose.onNodeWithContentDescription(w.localized(AppPreferences()),useUnmergedTree=true).performClick()
    @Test fun conversationSearchPinRenameAndLocalDeleteWork() {
        compose.onNodeWithText("New conversation").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Session sample")
        compose.waitUntil(5000) {vm.draft.value=="Session sample" && !vm.connecting.value}
        compose.runOnUiThread {
            compose.activity.currentFocus?.clearFocus()
            androidx.core.view.WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        if(android.os.Build.VERSION.SDK_INT>=30) compose.waitUntil(5000) {(androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0)==0}
        compose.onNodeWithContentDescription(Words.Send.localized(AppPreferences())).assertIsEnabled().performClick()
        try {compose.waitUntil(15000) {!vm.busy.value && vm.runStatus.value=="completed"}}
        catch(e:androidx.compose.ui.test.ComposeTimeoutException) {fail("Demo session send: busy=${vm.busy.value}, status=${vm.runStatus.value}, connection=${vm.connectionStatus.value}, connecting=${vm.connecting.value}, draftLength=${vm.draft.value.length}, error=${vm.error.value}")}
        compose.onNodeWithContentDescription(Words.Completed.localized(AppPreferences())).assertExists()
        go(Words.History)
        compose.onNode(hasSetTextAction() and hasText(Words.Search.localized(AppPreferences()))).performTextInput("sample")
        go(Words.Pin);go(Words.Rename)
        compose.onNode(hasSetTextAction() and hasText(Words.ConversationTitle.localized(AppPreferences()))).performTextReplacement("Renamed sample")
        compose.onNodeWithText(Words.Save.localized(AppPreferences())).performClick()
        compose.waitUntil(5000) {runBlocking {app.database.dao().conversations("demo").first().singleOrNull()?.title=="Renamed sample"}}
        assertTrue(runBlocking {app.database.dao().conversations("demo").first().single().pinned})
        go(Words.Delete)
        compose.onNode(hasText(Words.Delete.localized(AppPreferences())) and hasClickAction()).performClick()
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
