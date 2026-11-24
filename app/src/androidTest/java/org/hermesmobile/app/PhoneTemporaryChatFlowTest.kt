package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneTemporaryChatFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun endingTemporaryChatPreservesTheSavedChatDraftAndReopensEmpty() = withPhone {
        compose.onNodeWithTag("phone-draft").assertIsDisplayed().performClick().performTextInput("Original saved-chat draft")
        openTemporary()
        val draft = compose.onNodeWithTag("phone-temporary-draft")
        draft.assertIsDisplayed().performClick().assertIsFocused().performTextInput("Unsent private draft")
        draft.assertTextContains("Unsent private draft")
        val review = compose.onNodeWithTag("phone-temporary-end-review")
        val close = compose.onNodeWithTag("phone-temporary-close")
        var keyboardViewport: FocusedDialogViewport? = null
        compose.waitUntil(5_000) {
            keyboardViewport = focusedDialogViewport(requireKeyboard = false)?.takeIf { it.keyboardVisible }
            keyboardViewport != null
        }
        val viewport = checkNotNull(keyboardViewport)
        val bounds = close.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Temporary chat must keep its End action above the keyboard",
            viewport.keyboardVisible && bounds.height > 0f &&
                bounds.top + viewport.screenOffsetY >= viewport.visible.top &&
                bounds.bottom + viewport.screenOffsetY <= viewport.visible.bottom
        )
        close.performTouchInput { click() }
        compose.waitUntil(5_000) { review.isDisplayed() }
        compose.onNodeWithTag("phone-temporary-keep").assertIsDisplayed().performTouchInput { click() }
        draft.assertTextContains("Unsent private draft")
        close.assertIsDisplayed().performTouchInput { click() }
        compose.waitUntil(5_000) { review.isDisplayed() }
        compose.onNodeWithTag("phone-temporary-end-confirm").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithTag("phone-temporary-title").assertDoesNotExist()
        compose.onNodeWithTag("phone-draft").assertTextContains("Original saved-chat draft")
        openTemporary()
        compose.onNodeWithTag("phone-temporary-draft").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun backgroundingClosesTheTemporaryComposerAndItCannotBeRestored() = withPhone { scenario ->
        openTemporary()
        compose.onNodeWithTag("phone-temporary-draft").performTextInput("Temporary background fixture")
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("phone-temporary-title").assertDoesNotExist()
        openTemporary()
        compose.onNodeWithTag("phone-temporary-draft").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("phone-temporary-reply").assertDoesNotExist()
    }

    private fun openTemporary() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("phone-chat-options") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("phone-chat-options").assertIsDisplayed().assertIsEnabled()
            .performTouchInput { click() }
        compose.onNodeWithTag("phone-temporary-open").assertIsDisplayed().assertIsEnabled()
            .performTouchInput { click() }
        val title = compose.onNodeWithTag("phone-temporary-title")
        compose.waitUntil(10_000) { title.isDisplayed() }
        title.assertIsDisplayed()
    }

    private fun withPhone(run: (ActivityScenario<LocalAiActivity>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<LocalAiActivity>(Intent(context, LocalAiActivity::class.java))
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasTestTag("phone-draft") and isEnabled()).fetchSemanticsNodes().size == 1
            }
            run(scenario)
        } finally {
            scenario.close()
        }
    }
}
