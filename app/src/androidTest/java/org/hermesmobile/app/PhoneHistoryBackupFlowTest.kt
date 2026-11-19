package org.hermesmobile.app

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.util.UUID
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.*
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhoneHistoryBackupFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val guard = UpdateWorkGuard()
    private val savedState = SavedStateHandle()
    private val picks = mutableListOf<Pair<Int, Intent>>()
    private lateinit var fixture: File
    private lateinit var output: File
    private lateinit var source: LocalConversationStore
    private lateinit var labels: LocalHistoryOrganization
    private lateinit var controller: LocalAiController
    private lateinit var backup: PhoneHistoryBackup
    private val preferences = LocalHistoryPreferences("Saved garden fixture", pinned = true, archived = true)
    private val seed = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 1000L,
        exchanges = listOf(LocalExchange("Garden fixture question", "Garden fixture answer", LocalReplyState.COMPLETE)))
    private val excluded = LocalConversation(modelSha256 = "b".repeat(64), updatedAt = 900L,
        exchanges = listOf(LocalExchange("Excluded fixture", "Excluded answer", LocalReplyState.COMPLETE)))

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

    private fun show(multilingual: Boolean = false) {
        val activity = compose.activity
        fixture = File(activity.cacheDir, "phone-backup-test-" + UUID.randomUUID()).apply { check(mkdir()) }
        output = File(activity.filesDir, "updates/apks/backup-test-" + UUID.randomUUID() + ".sgbackup")
        check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
        source = LocalConversationStore(File(fixture, "chats"))
        source.saveNew(seed)
        source.saveNew(excluded)
        labels = LocalHistoryOrganization(File(fixture, "chats-organization"), source)
        labels.update(seed.id, seed.modelSha256, null, preferences)
        compose.runOnUiThread {
            controller = LocalAiController(File(fixture, "models"), source.directory, activity.applicationInfo.nativeLibraryDir,
                supported = false, freeMemory = { Long.MAX_VALUE }, guard = guard)
            backup = PhoneHistoryBackup(activity.contentResolver, controller, guard, savedState, activity.lifecycleScope)
        }
        val actions = LocalAiActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            exportBackup = backup::selectExport, importBackup = backup::selectImport)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (multilingual) 1.3f else density.fontScale)) {
                    HermesAppTheme(AppPreferences(chinese = multilingual, pinyin = multilingual)) {
                        val state by controller.state.collectAsStateWithLifecycle()
                        LocalAiScreen(state, false, "", {}, false, "", actions, {})
                        PhoneHistoryBackupFlow(backup, state.busy)
                    }
                }
            }
        }
        compose.waitUntil(15_000) { !controller.state.value.busy && controller.state.value.history.size == 2 }
        compose.onNodeWithTag("phone-tab-2").performClick()
    }

    @After fun removeFixture() {
        if (::backup.isInitialized) compose.runOnUiThread { backup.clear() }
        if (::output.isInitialized) output.delete()
        if (::fixture.isInitialized) fixture.deleteRecursively()
    }

    private fun open(tag: String) {
        compose.onNodeWithTag("phone-history").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun deliverFile(pick: Pair<Int, Intent>) {
        val uri = FileProvider.getUriForFile(compose.activity, compose.activity.packageName + ".updates", output)
        compose.runOnIdle { picker.dispatchResult(pick.first, Activity.RESULT_OK, Intent().setData(uri)) }
    }

    private fun waitForState(predicate: (PhoneHistoryBackupState) -> Boolean) =
        compose.waitUntil(20_000) { predicate(backup.state.value) }

    @Test fun protectedBackupMakesAReviewedCopyAndPreservesBothOriginals() {
        show()
        val originals = listOf(seed, excluded).associate { it.id to source.file(it.id).readBytes() }
        val passphrase = "orchard-fixture-94"
        open("phone-history-backup-export")
        val review = compose.onNodeWithTag("phone-backup-prepare").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("phone-backup-protect").performScrollTo().assertIsOn()
        val selectedChat = compose.onNodeWithTag("phone-backup-select-" + seed.id)
        selectedChat.performScrollTo().assertIsDisplayed().assertIsOff().performClick()
        selectedChat.assertIsOn()
        compose.onNodeWithTag("phone-backup-select-" + excluded.id).performScrollTo().assertIsOff()
        val password = compose.onNodeWithTag("phone-backup-password").performScrollTo().assertIsDisplayed()
        password.performTextReplacement(passphrase)
        review.assertIsNotEnabled()
        val repeated = compose.onNodeWithTag("phone-backup-repeat").performScrollTo().assertIsDisplayed()
        repeated.performTextReplacement("mismatched-fixture-passphrase")
        review.assertIsNotEnabled()
        repeated.performTextReplacement(passphrase)
        review.assertIsDisplayed().assertIsEnabled().performClick()
        waitForState { it is PhoneHistoryBackupState.ExportReview }
        compose.onNodeWithTag("phone-backup-choose-destination").performClick()
        compose.waitForIdle()
        val save = picks.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, save.second.action)
        assertEquals("application/octet-stream", save.second.type)
        assertEquals("SignalGrove-phone-history.sgbackup", save.second.getStringExtra(Intent.EXTRA_TITLE))
        deliverFile(save)
        waitForState { it == PhoneHistoryBackupState.Finished(BackupResult.SAVED) }
        val exportedBytes = output.readBytes()
        assertTrue(LocalHistoryBackupProtection.recognizesHeader(exportedBytes.copyOf(8)))
        assertFalse(exportedBytes.toString(Charsets.ISO_8859_1).contains("Garden fixture answer"))
        compose.onNodeWithTag("phone-backup-close").performClick()

        open("phone-history-backup-import")
        compose.waitForIdle()
        val read = picks.last()
        assertEquals(2, picks.size)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, read.second.action)
        compose.runOnIdle {
            val active = backup.state.value as PhoneHistoryBackupState.Picking
            backup.picked("expired-picker", null)
            assertEquals(active, backup.state.value)
        }
        deliverFile(read)
        waitForState { it is PhoneHistoryBackupState.Unlock }
        compose.onNodeWithTag("phone-backup-unlock-password").assertIsDisplayed().performTextReplacement("wrong-fixture")
        compose.onNodeWithTag("phone-backup-unlock").performClick()
        waitForState { it == PhoneHistoryBackupState.Unlock(rejected = true) }
        compose.onNodeWithText(Words.PhoneBackupRejected.en).assertIsDisplayed()
        assertEquals(originals.keys, source.list().map { it.id }.toSet())
        compose.onNodeWithTag("phone-backup-unlock-password").assertIsDisplayed().performTextReplacement(passphrase)
        compose.onNodeWithTag("phone-backup-unlock").performClick()
        waitForState { it is PhoneHistoryBackupState.RestoreReview }
        val item = (backup.state.value as PhoneHistoryBackupState.RestoreReview).plan.items.single()
        assertEquals(seed.id, item.source.id)
        assertTrue(item.remapped)
        assertTrue(guard.active.value)
        assertEquals(originals.keys, source.list().map { it.id }.toSet())
        compose.onNodeWithTag("phone-backup-remapped-" + seed.id).assertExists()
        output.writeText("Provider changed after review")
        compose.onNodeWithTag("phone-backup-restore-confirm").assertIsDisplayed().performClick()
        waitForState { it == PhoneHistoryBackupState.Finished(BackupResult.RESTORED, 1) }
        compose.waitUntil(10_000) { !guard.active.value }
        originals.forEach { (id, bytes) -> assertArrayEquals(bytes, source.file(id).readBytes()) }
        assertEquals(seed.exchanges, source.load(item.targetId).exchanges)
        assertEquals(preferences, labels.entries().getValue(item.targetId).preferences)
        assertEquals(originals.keys + item.targetId, source.list().map { it.id }.toSet())
        assertTrue(File(fixture, "history-backup-stages").listFiles()!!.isEmpty())
    }

    @Test fun multilingualBackupSelectionKeepsFieldsAndActionsAboveTheKeyboard() {
        show(multilingual = true)
        open("phone-history-backup-export")
        compose.onNodeWithTag("phone-backup-select-" + seed.id).performScrollTo().assertIsToggleable().performClick()
        compose.onNodeWithTag("phone-backup-password").performScrollTo().performClick().performTextInput("garden-fixture")
        compose.waitUntil(5_000) { focusedDialogKeyboardVisible() }
        compose.onNodeWithTag("phone-backup-repeat").performScrollTo().assertIsDisplayed()
            .performClick().performTextInput("garden-fixture")
        val viewport = checkNotNull(focusedDialogViewport())
        for (tag in listOf("phone-backup-prepare", "phone-backup-cancel")) {
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.top + viewport.screenOffsetY >= viewport.visible.top)
            assertTrue(bounds.bottom + viewport.screenOffsetY <= viewport.visible.bottom)
        }
        compose.onNodeWithTag("phone-backup-cancel").performClick()
        assertTrue(picks.isEmpty())
        assertFalse(guard.active.value)
    }

    @Test fun plainBackupReviewCancellationAndInterruptedPickerNeverRestore() {
        show()
        val before = source.file(seed.id).readBytes()
        controller.historyBackupFiles.export(labels.history().filter { it.id == seed.id }, null).use { staged ->
            output.outputStream().use { staged.writeTo(it) }
        }
        open("phone-history-backup-import")
        compose.waitForIdle()
        compose.runOnIdle {
            val pending = backup.state.value as PhoneHistoryBackupState.Picking
            val restarted = PhoneHistoryBackup(compose.activity.contentResolver, controller, UpdateWorkGuard(),
                savedState, compose.activity.lifecycleScope)
            restarted.picked(pending.id, null)
            assertEquals(PhoneHistoryBackupState.Finished(BackupResult.INTERRUPTED), restarted.state.value)
        }
        deliverFile(picks.single())
        waitForState { it is PhoneHistoryBackupState.RestoreReview }
        assertEquals(LocalHistoryBackupFormat.PLAIN, (backup.state.value as PhoneHistoryBackupState.RestoreReview).format)
        compose.onNodeWithTag("phone-backup-cancel").performClick()
        assertArrayEquals(before, source.file(seed.id).readBytes())
        assertEquals(setOf(seed.id, excluded.id), source.list().map { it.id }.toSet())
        assertTrue(File(fixture, "history-backup-stages").listFiles()!!.isEmpty())
        assertFalse(guard.active.value)
        assertTrue(savedState.keys().isEmpty())
    }
}
