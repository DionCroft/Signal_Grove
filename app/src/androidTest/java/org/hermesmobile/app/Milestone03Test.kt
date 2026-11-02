package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Milestone03Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as HermesApplication
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    @Before fun reset() {runBlocking {app.preferences.update(AppPreferences());app.database.dao().clearChats()};compose.waitForIdle()}
    private fun go(w:Words)=compose.onAllNodesWithContentDescription(w.localized(AppPreferences()),useUnmergedTree=true).onLast().performClick()
    @Test fun demoApprovalWaitsForAnExplicitDenyAndRecordsToolFailure() {
        compose.onNodeWithText("New conversation").performScrollTo().performClick()
        compose.onNodeWithText(Words.DemoApproval.localized(AppPreferences())).performScrollTo().performClick()
        compose.waitUntil(5000) {vm.controls.value.isNotEmpty()}
        assertTrue(vm.busy.value);assertEquals("pending",vm.controls.value.single().state)
        compose.onNodeWithText(Words.Deny.localized(AppPreferences())).performClick()
        compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
        assertTrue(runBlocking {app.database.dao().events(vm.selected.value).first().any {it.kind=="failed"}})
        assertTrue(vm.controls.value.isEmpty())
        compose.onNodeWithContentDescription(Words.ToolActivity.localized(AppPreferences()),substring=true).performClick()
        compose.onNodeWithTag("activity-timeline").performScrollToNode(hasText("The simulated action was denied."))
        compose.onNodeWithText("The simulated action was denied.").assertExists()
    }
    @Test fun demoClarificationChoiceIsReviewedBeforeSubmission() {
        compose.onNodeWithText("New conversation").performScrollTo().performClick()
        compose.onNodeWithText(Words.DemoClarification.localized(AppPreferences())).performScrollTo().performClick()
        compose.waitUntil(5000) {vm.controls.value.isNotEmpty()}
        compose.onNodeWithText("Brief").performClick();assertTrue(vm.busy.value)
        compose.onNodeWithText(Words.SubmitAnswer.localized(AppPreferences())).performClick()
        compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
        assertTrue(vm.messages.value.last().content.contains("Brief"))
    }
    @Test fun voicePrivacyPreferencesPersistAndTypedTranscriptionTransfersWithoutActivatingAudio() {
        go(Words.Voice)
        assertTrue(vm.preferences.value.onDeviceSpeechOnly);assertFalse(vm.preferences.value.allowNetworkSpeech)
        compose.onNode(hasSetTextAction() and hasText(Words.Transcript.localized(AppPreferences()))).performScrollTo().performTextInput("Typed transcription sample")
        compose.onNodeWithText(Words.Chat.localized(AppPreferences())).performScrollTo().performClick()
        assertEquals("Typed transcription sample",vm.draft.value)
        runBlocking {app.preferences.update(vm.preferences.value.copy(onDeviceSpeechOnly=false,allowNetworkSpeech=false,speechRate=1.1f))}
        val stored=runBlocking {app.preferences.flow.first()};assertFalse(stored.onDeviceSpeechOnly);assertEquals(1.1f,stored.speechRate,.001f)
    }
}
