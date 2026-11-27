package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.CaptureSource
import org.hermesmobile.app.data.PhoneCapture
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhoneCaptureLifecycleUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun cancelEditAndDeleteThenManageTheSameCaptureWithoutAModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as HermesApplication
        val captures = app.database.phoneCaptures()
        val note = PhoneCapture(UUID.randomUUID().toString(), "Original calibration note",
            CaptureSource.NOTE.name, System.currentTimeMillis())
        val editedText = "Calibrate the offline fixture before noon"
        runBlocking { captures.save(note) }
        instrumentation.runOnMainSync {
            app.startActivity(LocalAiActivity.captureIntent(app, note.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        try {
            await("capture-content")
            assertNull(app.localAi.state.value.loaded)
            clickDetail("capture-edit")
            compose.onNodeWithTag("capture-note").performTextReplacement("Discard this local draft")
            compose.onNodeWithTag("capture-cancel-edit").performClick()
            compose.onNodeWithTag("capture-content").assertTextEquals(note.content)

            clickDetail("capture-edit")
            compose.onNodeWithTag("capture-note").performTextReplacement(editedText)
            compose.onNodeWithTag("capture-save-note").performClick()
            await("capture-content")
            compose.onNodeWithTag("capture-content").assertTextEquals(editedText)
            assertEquals(editedText, runBlocking { captures.observe(note.id).first() }?.content)

            clickDetail("capture-archive-action")
            awaitText("capture-archive-action", "Move back to captures")
            compose.onNodeWithTag("capture-back-to-inbox").performClick()
            await("capture-list")
            compose.onNodeWithTag("capture-search").performTextInput(editedText)
            compose.onNodeWithTag("capture-search").performImeAction()
            assertTrue(runBlocking { captures.search(editedText).first().isEmpty() })
            val list = compose.onNodeWithTag("capture-list")
            list.performScrollToNode(hasText("No captures match your search.", substring = true))
            compose.onNodeWithText("No captures match your search.", substring = true).assertIsDisplayed()
            list.performScrollToIndex(0)
            compose.onNodeWithTag("capture-toggle-archive").performScrollTo().performClick()
            val rowTag = "capture-open-" + note.id
            compose.waitUntil(15_000) {
                val keyIndex = compose.onAllNodesWithTag("capture-list").fetchSemanticsNodes()
                    .singleOrNull()?.config?.getOrNull(SemanticsProperties.IndexForKey)
                (keyIndex?.invoke(note.id) ?: -1) >= 0
            }
            compose.onNodeWithTag("capture-list").performScrollToKey(note.id)
            compose.onNodeWithTag(rowTag).performClick()
            await("capture-content")
            compose.onNodeWithTag("capture-content").assertTextEquals(editedText)

            clickDetail("capture-archive-action")
            awaitText("capture-archive-action", "Archive capture")
            clickDetail("capture-delete")
            await("capture-delete-content")
            compose.onNodeWithTag("capture-delete-content").assertTextEquals(editedText)
            compose.onNodeWithTag("capture-cancel-delete").performClick()
            compose.onNodeWithTag("capture-content").assertTextEquals(editedText)
            assertNotNull(runBlocking { captures.observe(note.id).first() })

            clickDetail("capture-delete")
            compose.onNodeWithTag("capture-confirm-delete").performClick()
            await("capture-list")
            assertNull(runBlocking { captures.observe(note.id).first() })
            assertNull(app.localAi.state.value.loaded)
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<LocalAiActivity>().forEach { it.finish() }
            }
            runBlocking { captures.observe(note.id).first()?.let { captures.delete(it) } }
        }
    }

    private fun await(tag: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }

    private fun clickDetail(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    private fun awaitText(tag: String, text: String) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true)).fetchSemanticsNodes().size == 1
    }
}
