package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhonePersonalProfilesFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun profileEditsPersistAndBackgroundingClosesThePrivateEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = (context.applicationContext as HermesApplication).preferences
        val before = runBlocking { store.phonePersonalProfiles.first() }
        val scenario = ActivityScenario.launch<LocalAiActivity>(Intent(context, LocalAiActivity::class.java))
        fun open() {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasTestTag("phone-profile-open") and isEnabled()).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("phone-profile-open").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("phone-profile-add") and isEnabled()).fetchSemanticsNodes().size == 1
            }
        }
        try {
            open()
            compose.onNodeWithTag("phone-profile-add").performClick()
            compose.onNodeWithTag("phone-profile-name").performTextReplacement("QA Grove profile")
            compose.onNodeWithTag("phone-profile-style").performScrollTo().performTextReplacement("Short replies")
            compose.onNodeWithTag("phone-profile-add-note").performScrollTo().performClick()
            compose.onNodeWithTag("phone-profile-note-0").performScrollTo().performTextReplacement("Example private garden note")
            compose.onNodeWithTag("phone-profile-save").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("QA Grove profile").fetchSemanticsNodes().size == 1 }
            val saved = runBlocking { store.phonePersonalProfiles.first() }.entries.single { it !in before.entries }
            assertEquals("Short replies", saved.style)
            assertEquals("Example private garden note", saved.notes.single().text)
            compose.onNodeWithTag("phone-profile-choose-${saved.id}").assertIsNotEnabled()
            compose.onNodeWithTag("phone-profile-edit-${saved.id}").performScrollTo().performClick()
            compose.onNodeWithTag("phone-profile-name").performTextReplacement("Uncommitted QA title")
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("phone-profile-name").assertDoesNotExist()
            open()
            compose.onNodeWithText("QA Grove profile").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("phone-profile-delete-${saved.id}").performScrollTo().performClick()
            compose.onNodeWithTag("phone-profile-delete-confirm").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("QA Grove profile").fetchSemanticsNodes().isEmpty() }
            assertEquals(before, runBlocking { store.phonePersonalProfiles.first() })
        } finally {
            scenario.close()
            runBlocking {
                store.editPhonePersonalProfiles { current ->
                    current.entries.singleOrNull { it.name == "QA Grove profile" && it !in before.entries }
                        ?.let(current::remove) ?: current
                }
            }
        }
    }
}
