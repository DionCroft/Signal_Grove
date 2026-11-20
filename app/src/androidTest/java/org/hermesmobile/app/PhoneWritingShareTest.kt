package org.hermesmobile.app

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
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
