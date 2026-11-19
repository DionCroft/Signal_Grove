package org.hermesmobile.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.*
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercise actual history/review controls and the real resolver writer. Only the picker result is supplied by the test. */
class PhoneMarkdownExportFlowTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val guard = UpdateWorkGuard()
    private val savedState = SavedStateHandle()
    private lateinit var export: PhoneMarkdownExport
    private lateinit var document: LocalMarkdownExport
    private lateinit var selected: LocalConversationSummary
    private lateinit var source: File
    private lateinit var sourceBytes: ByteArray
    private val picks = mutableListOf<Pair<Int, Intent>>()
    private val reviewRequests = mutableListOf<String>()
    private val picker = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?
        ) {
            picks += requestCode to contract.createIntent(compose.activity, input)
        }
    }
    private val pickerOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = picker
    }

    private fun show() {
        val folder = File(compose.activity.cacheDir, "phone-markdown-test-" + UUID.randomUUID())
        val store = LocalConversationStore(folder)
        val chat = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 1000,
            exchanges = listOf(LocalExchange("Garden 花园 🌱", "Saved answer", LocalReplyState.COMPLETE)))
        store.saveNew(chat)
        source = File(folder, chat.id + ".json")
        sourceBytes = source.readBytes()
        selected = store.list().single()
        document = store.markdownExport(chat)
        compose.runOnUiThread {
            export = PhoneMarkdownExport(compose.activity.contentResolver, guard, savedState, compose.activity.lifecycleScope)
        }
        val history = LocalAiState(history = listOf(selected), loaded = null)
        val actions = LocalAiActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            exportMarkdown = {
                reviewRequests += it.id
                export.review(document, it.title)
            })
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    HermesAppTheme(AppPreferences(chinese = false, pinyin = false)) {
                        LocalAiScreen(history, true, "", {}, false, actions, {})
                        PhoneMarkdownExportFlow(export, busy = false)
                    }
                }
            }
        }
        compose.onNodeWithTag("phone-tab-2").performClick()
    }

    private fun review() {
        val tag = "phone-history-export-" + selected.id
        compose.onNodeWithTag("phone-history").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("phone-markdown-title").assertTextEquals(selected.title)
        compose.onNodeWithTag("phone-markdown-choose").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("phone-markdown-cancel").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun reviewCancelAndPickerCancelKeepTheSourceAndNeverWrite() {
        show()
        review()
        compose.onNodeWithTag("phone-markdown-cancel").performClick()
        compose.runOnIdle {
            assertTrue(picks.isEmpty())
            assertEquals(PhoneMarkdownExportState.Idle, export.state.value)
            assertFalse(guard.active.value)
        }
        review()
        compose.onNodeWithTag("phone-markdown-choose").performClick()
        compose.runOnIdle {
            val (requestCode, intent) = picks.single()
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
            assertEquals("text/markdown", intent.type)
            assertEquals(document.suggestedFilename, intent.getStringExtra(Intent.EXTRA_TITLE))
            assertTrue(guard.active.value)
            picker.dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
            assertFalse(guard.active.value)
            assertEquals(listOf(selected.id, selected.id), reviewRequests)
        }
        compose.onNodeWithTag("phone-markdown-result").assertTextEquals(Words.PhoneMarkdownCancelled.en)
        assertArrayEquals(sourceBytes, source.readBytes())
    }

    @Test fun oneExplicitDestinationWritesTheReviewedBytesAndIgnoresADuplicateResult() {
        show()
        val output = File(compose.activity.filesDir, "updates/apks/markdown-test-" + UUID.randomUUID() + ".md")
        assertTrue(output.parentFile!!.mkdirs() || output.parentFile!!.isDirectory)
        val uri = FileProvider.getUriForFile(compose.activity, compose.activity.packageName + ".updates", output)
        try {
            review()
            compose.onNodeWithTag("phone-markdown-choose").performClick()
            compose.runOnIdle {
                export.choose(document) { error("A second launch escaped the guard") }
                picker.dispatchResult(picks.single().first, Activity.RESULT_OK, Intent().setData(uri))
            }
            compose.waitUntil(10_000) { export.state.value == PhoneMarkdownExportState.Finished(MarkdownSaveResult.SAVED) }
            compose.onNodeWithTag("phone-markdown-result").assertTextEquals(Words.PhoneMarkdownSaved.en)
            val expected = ByteArrayOutputStream().also {document.writeTo(it)}.toByteArray()
            assertArrayEquals(expected, output.readBytes())
            assertFalse(guard.active.value)
            output.writeText("Destination changed after explicit save")
            compose.runOnIdle { export.destination(uri) }
            assertEquals("Destination changed after explicit save", output.readText())
            assertArrayEquals(sourceBytes, source.readBytes())
        } finally {
            output.delete()
        }
    }

    @Test fun invalidDestinationAndRestoredPickerMarkerNeverReplayAWritingOperation() {
        show()
        review()
        compose.onNodeWithTag("phone-markdown-choose").performClick()
        val path = File(compose.activity.cacheDir, "ungranted-markdown-" + UUID.randomUUID())
        compose.runOnIdle {
            val restored = PhoneMarkdownExport(compose.activity.contentResolver, UpdateWorkGuard(), savedState, compose.activity.lifecycleScope)
            assertEquals(PhoneMarkdownExportState.Finished(MarkdownSaveResult.INTERRUPTED), restored.state.value)
            restored.destination(Uri.fromFile(path))
            assertEquals(PhoneMarkdownExportState.Finished(MarkdownSaveResult.INTERRUPTED), restored.state.value)
            picker.dispatchResult(picks.single().first, Activity.RESULT_OK, Intent().setData(Uri.fromFile(path)))
        }
        compose.waitUntil(10_000) { export.state.value == PhoneMarkdownExportState.Finished(MarkdownSaveResult.UNCERTAIN) }
        compose.onNodeWithTag("phone-markdown-result").assertTextEquals(Words.PhoneMarkdownUncertain.en)
        assertFalse(path.exists())
        assertArrayEquals(sourceBytes, source.readBytes())
        assertFalse(guard.active.value)
    }
}
