package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.CaptureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class PhoneCaptureRestartTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** The device driver invokes this after force-stopping the app following the share/widget test. */
    @Test fun findCapturedFixturesAfterProcessRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = InstrumentationRegistry.getArguments().getString("captureFixture").orEmpty()
        assumeTrue("Requires a previous share/widget fixture and process restart", fixture.isNotEmpty())
        UUID.fromString(fixture)
        val app = instrumentation.targetContext.applicationContext as HermesApplication
        val expected = mapOf(
            CaptureSource.SHARED_LINK.name to "https://example.invalid/fixture/$fixture",
            CaptureSource.SHARED_TEXT.name to "Shared rehearsal actions $fixture",
            CaptureSource.WIDGET.name to "Rehearsal note $fixture"
        )
        val rows = runBlocking { app.database.phoneCaptures().search(fixture).first() }
        assertEquals(expected, rows.associate { it.source to it.preview })
        instrumentation.runOnMainSync { app.startActivity(LocalAiActivity.captureIntent(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        for (row in rows) {
            compose.onNodeWithTag("capture-search").performTextReplacement(fixture)
            compose.onNodeWithTag("capture-search").performImeAction()
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("capture-open-" + rows.first().id)).fetchSemanticsNodes().size == 1 }
            compose.onNodeWithTag("capture-list").performScrollToNode(hasTestTag("capture-open-" + row.id))
            compose.onNodeWithTag("capture-open-" + row.id).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("capture-content").fetchSemanticsNodes().size == 1 }
            compose.onNodeWithTag("capture-content").assertTextEquals(expected.getValue(row.source))
            compose.onNodeWithTag("capture-back-to-inbox").performClick()
        }

        assertNull(app.localAi.state.value.loaded)
    }
}
