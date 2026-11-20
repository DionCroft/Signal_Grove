package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class PhonePersonalProfilesFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun profileEditsPersistAndBackgroundingClosesThePrivateEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = (context.applicationContext as HermesApplication).preferences
        val before = runBlocking { store.phonePersonalProfiles.first() }
        val fixtureName = "QA Grove ${UUID.randomUUID()}"
        val scenario = ActivityScenario.launch<LocalAiActivity>(Intent(context, LocalAiActivity::class.java))
        fun open() {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasTestTag("phone-chat-options") and isEnabled()).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("phone-chat-options").assertIsDisplayed().assertIsEnabled()
                .performTouchInput { click() }
            compose.onNodeWithTag("phone-profile-open").assertIsDisplayed().assertIsEnabled()
                .performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("phone-profile-add") and isEnabled()).fetchSemanticsNodes().size == 1
            }
        }
        try {
            open()
            compose.onNodeWithTag("phone-profile-add").performClick()
            compose.onNodeWithTag("phone-profile-name").performTextReplacement(fixtureName)
            compose.onNodeWithTag("phone-profile-style").performScrollTo().performTextReplacement("Short replies")
            val addNote = compose.onNodeWithTag("phone-profile-add-note")
            addNote.performScrollTo().assertIsDisplayed().performTouchInput { click() }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("phone-profile-note-0").fetchSemanticsNodes().size == 1 }
            compose.onNodeWithTag("phone-profile-note-0").performScrollTo().performTextReplacement("Example private garden note")
            compose.onNodeWithTag("phone-profile-save").assertIsDisplayed().assertIsEnabled().performClick()
            val saved = runBlocking {
                withTimeout(10_000) {
                    store.phonePersonalProfiles.first { library -> library.entries.any { it.name == fixtureName } }
                        .entries.single { it.name == fixtureName }
                }
            }
            assertEquals("Short replies", saved.style)
            assertEquals("Example private garden note", saved.notes.single().text)
            compose.onNodeWithTag("phone-profile-choose-${saved.id}").assertIsNotEnabled()
            compose.onNodeWithTag("phone-profile-edit-${saved.id}").performScrollTo().performClick()
            compose.onNodeWithTag("phone-profile-name").performTextReplacement("Uncommitted QA title")
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("phone-profile-name").assertDoesNotExist()
            open()
            compose.onNodeWithText(fixtureName).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("phone-profile-delete-${saved.id}").performScrollTo().performClick()
            compose.onNodeWithTag("phone-profile-delete-confirm").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(fixtureName).fetchSemanticsNodes().isEmpty() }
            assertEquals(before, runBlocking { store.phonePersonalProfiles.first() })
        } finally {
            scenario.close()
            runBlocking {
                store.editPhonePersonalProfiles { current ->
                    current.entries.singleOrNull { it.name == fixtureName && it !in before.entries }
                        ?.let(current::remove) ?: current
                }
            }
        }
    }
}
