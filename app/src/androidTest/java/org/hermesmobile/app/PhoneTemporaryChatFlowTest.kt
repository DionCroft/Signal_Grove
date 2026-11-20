package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class PhoneTemporaryChatFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun endingTemporaryChatPreservesTheSavedChatDraftAndReopensEmpty() = withPhone {
        compose.onNodeWithTag("phone-draft").assertIsDisplayed().performClick().performTextInput("Original saved-chat draft")
        openTemporary()
        compose.onNodeWithTag("phone-temporary-draft").assertIsDisplayed().performClick().performTextInput("Unsent private draft")
        compose.onNodeWithTag("phone-temporary-close").performClick()
        compose.onNodeWithTag("phone-temporary-end-review").assertIsDisplayed()
        compose.onNodeWithTag("phone-temporary-keep").performClick()
        compose.onNodeWithTag("phone-temporary-draft").assertTextContains("Unsent private draft")
        compose.onNodeWithTag("phone-temporary-close").performClick()
        compose.onNodeWithTag("phone-temporary-end-confirm").performClick()
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
            compose.onAllNodes(hasTestTag("phone-temporary-open") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("phone-temporary-open").assertIsDisplayed().assertIsEnabled().performClick()
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
