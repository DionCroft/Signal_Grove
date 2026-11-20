package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.ui.Words
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhoneVoiceFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun phoneVoiceAddsOnlyAnEditableDraftAndKeepsBothNetworkControlsHidden() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val app = target.applicationContext as HermesApplication
        val before = runBlocking { app.preferences.flow.first() }
        val networkAllowed = before.copy(onDeviceSpeechOnly = false, allowNetworkSpeech = true)
        runBlocking { app.preferences.patchUserSettings(networkAllowed, before) }
        try {
            withPhone {
                val history = app.localAi.state.value.history
                compose.onNodeWithTag("phone-draft").performTextReplacement("Existing text")
                open()
                compose.onNodeWithTag("voice-local-only").assertDoesNotExist()
                selectVoiceTab("voice-read-tab")
                compose.onNodeWithTag("voice-network-voices").assertDoesNotExist()
                compose.onNodeWithTag("voice-read-reply").performScrollTo().assertIsNotEnabled()
                compose.onNodeWithTag("voice-read-reason").performScrollTo().assertIsDisplayed()
                    .assertTextContains(Words.PhoneReadReplyRequired.en, substring = true)
                selectVoiceTab("voice-dictation-tab")
                val apply = compose.onNodeWithTag("voice-use-transcript")
                apply.assertIsDisplayed().assertIsNotEnabled()
                compose.onNodeWithTag("voice-transcript").performScrollTo().performClick()
                    .assertIsFocused().performTextReplacement("Test words 你好")
                compose.waitUntil(5_000) { focusedDialogViewport(requireKeyboard = false)?.keyboardVisible == true }
                val viewport = checkNotNull(focusedDialogViewport(requireKeyboard = false))
                assertTrue("Expected the real keyboard during transcript editing", viewport.keyboardVisible)
                val bounds = apply.fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "Transcript action bounds $bounds should fit the visible window ${viewport.visible}",
                    bounds.top + viewport.screenOffsetY >= viewport.visible.top &&
                        bounds.bottom + viewport.screenOffsetY <= viewport.visible.bottom && bounds.height > 0f
                )
                apply.assertIsDisplayed().assertIsEnabled()
                apply.performTouchInput { click() }
                compose.onNodeWithTag("voice-screen").assertDoesNotExist()
                compose.onNodeWithTag("phone-draft").assertTextContains("Existing text\nTest words 你好")
                assertEquals(history, app.localAi.state.value.history)
                assertEquals(networkAllowed, runBlocking { app.preferences.flow.first() })
            }
        } finally {
            runBlocking { app.preferences.patchUserSettings(before, networkAllowed) }
        }
    }

    @Test fun leavingPhoneVoiceDiscardsAnUnappliedTranscript() = withPhone { scenario ->
        compose.onNodeWithTag("phone-draft").assertIsDisplayed().performClick().performTextInput("Keep my draft")
        open()
        compose.onNodeWithTag("voice-transcript").performScrollTo().performTextReplacement("Unapplied local speech")
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("voice-screen").assertDoesNotExist()
        compose.onNodeWithTag("phone-draft").assertTextContains("Keep my draft")
        open()
        compose.onNodeWithTag("voice-use-transcript").assertIsDisplayed().assertIsNotEnabled()
    }

    private fun selectVoiceTab(tag: String) {
        // The dialog can be attached before the chip is displayed, and its label can
        // merge into another semantics node while the IME and dialog settle.
        val tab = compose.onNodeWithTag(tag, useUnmergedTree = true)
        compose.waitUntil(10_000) { tab.isDisplayed() }
        tab.assertIsEnabled().performTouchInput { click() }
    }

    private fun open() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("phone-voice-open") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("phone-voice-open").assertIsDisplayed().assertIsEnabled().performClick()
        val screen = compose.onNodeWithTag("voice-screen")
        compose.waitUntil(10_000) { screen.isDisplayed() }
        screen.assertIsDisplayed()
    }

    private fun withPhone(run: (ActivityScenario<LocalAiActivity>) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<LocalAiActivity>(Intent(target, LocalAiActivity::class.java))
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasTestTag("phone-voice-open") and isEnabled()).fetchSemanticsNodes().size == 1
            }
            run(scenario)
        } finally {
            scenario.close()
        }
    }
}
