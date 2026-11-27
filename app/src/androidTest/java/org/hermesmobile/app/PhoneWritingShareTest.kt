package org.hermesmobile.app

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.CaptureSource
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class PhoneWritingShareTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun boundsAndRejectsAttachmentsBeforeCreatingAnyWritingDraft() {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Selected text")
        assertEquals("Selected text", parseWritingShare(share))
        val selection = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "你好")
        assertEquals("你好", parseWritingShare(selection))
        assertNull(parseWritingShare(Intent(share).putExtra(Intent.EXTRA_TEXT, "字".repeat(2049))))
        assertNull(parseWritingShare(Intent(share).putExtra(Intent.EXTRA_STREAM, Uri.parse("content://fixture/attachment"))))
        assertNull(parseWritingShare(Intent(share).apply { clipData = ClipData.newRawUri("fixture", Uri.parse("content://fixture/1")) }))
        assertNull(parseWritingShare(Intent(share).setType("text/html")))
    }

    @Test fun shareAndWidgetSaveCapturesWithoutLoadingAModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as HermesApplication
        val lifecycle = ActivityLifecycleMonitorRegistry.getInstance()
        val created = mutableListOf<Activity>()
        val tracking = ActivityLifecycleCallback { activity, stage ->
            if (activity is LocalAiActivity && stage == Stage.CREATED) created.add(activity)
        }
        val unique = UUID.randomUUID().toString()
        val link = "https://example.invalid/fixture/" + unique
        val widgetNote = "Rehearsal note " + unique
        val sharedText = "Shared rehearsal actions " + unique
        instrumentation.runOnMainSync { lifecycle.addLifecycleCallback(tracking) }
        try {
            for (content in listOf(link, sharedText)) {
                val share = Intent(app, PhoneTextReceiveActivity::class.java).setAction(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT, content).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                instrumentation.runOnMainSync { app.startActivity(share) }
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithTag("capture-save-shared").fetchSemanticsNodes().size == 1
                }
                compose.onNodeWithTag("phone-writing-incoming").assertTextEquals(content)
                compose.onNodeWithTag("capture-save-shared").performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("capture-content").fetchSemanticsNodes().size == 1 }
                compose.onNodeWithTag("capture-content").assertTextEquals(content)
                compose.onNodeWithTag("capture-suggest-tasks").assertIsNotEnabled()
                compose.onNodeWithTag("capture-close").performClick()
            }

            val widget = runBlocking { org.hermesmobile.app.background.TaskWidget.buildViews(app) }
            instrumentation.runOnMainSync {
                val view = widget.apply(app, android.widget.FrameLayout(app))
                assertTrue(view.findViewById<android.widget.TextView>(R.id.widget_capture).performClick())
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("capture-note").fetchSemanticsNodes().size == 1 }
            compose.onNodeWithTag("capture-note").performTextInput(widgetNote)
            compose.onNodeWithTag("capture-save-note").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("capture-content").fetchSemanticsNodes().size == 1 }
            compose.onNodeWithTag("capture-content").assertTextEquals(widgetNote)
            compose.onNodeWithTag("capture-back-to-inbox").performClick()
            compose.onNodeWithTag("capture-search").performTextInput(unique)
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("capture-list").fetchSemanticsNodes().size == 1 }
            val captures = runBlocking { app.database.phoneCaptures().search(unique).first() }
            assertEquals(setOf(CaptureSource.SHARED_TEXT.name, CaptureSource.SHARED_LINK.name, CaptureSource.WIDGET.name), captures.map { it.source }.toSet())
            assertEquals(setOf(link, sharedText, widgetNote), captures.map { it.preview }.toSet())
            assertNull(app.localAi.state.value.loaded)
            instrumentation.addResults(Bundle().apply { putString("capture_fixture", unique) })
        } finally {
            instrumentation.runOnMainSync {
                lifecycle.removeLifecycleCallback(tracking)
                created.asReversed().forEach { if (!it.isFinishing) it.finish() }
                app.phoneWriting.dismissIncoming()
            }
        }
    }

    @Test fun actualSharedTextReviewCancelsWithoutReplacingTheWritingDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as HermesApplication
        val lifecycle = ActivityLifecycleMonitorRegistry.getInstance()
        val created = mutableListOf<Activity>()
        val tracking = ActivityLifecycleCallback { activity, stage ->
            if (activity is LocalAiActivity && stage == Stage.CREATED) created.add(activity)
        }
        val before = app.phoneWriting.draft.value.source
        instrumentation.runOnMainSync {
            lifecycle.addLifecycleCallback(tracking)
            app.phoneWriting.editSource("Unsent writing draft")
        }
        try {
            val share = Intent(app, PhoneTextReceiveActivity::class.java).setAction(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Text shared from the system")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            instrumentation.runOnMainSync { app.startActivity(share) }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("phone-writing-incoming").fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodesWithTag("phone-writing-cancel-intake").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("phone-writing-incoming").assertTextEquals("Text shared from the system")
            compose.onNodeWithTag("phone-writing-send").assertDoesNotExist()
            compose.onNodeWithTag("phone-writing-cancel-intake").performClick()
            compose.onNodeWithTag("phone-writing-body").performScrollToNode(hasTestTag("phone-writing-source"))
            compose.onNodeWithTag("phone-writing-source").assertTextContains("Unsent writing draft")
            assertEquals("Unsent writing draft", app.phoneWriting.draft.value.source)
            assertNull(app.phoneWriting.draft.value.incoming)
        } finally {
            instrumentation.runOnMainSync {
                lifecycle.removeLifecycleCallback(tracking)
                created.asReversed().forEach { if (!it.isFinishing) it.finish() }
                app.phoneWriting.dismissIncoming()
                app.phoneWriting.editSource(before)
            }
        }
    }
}
