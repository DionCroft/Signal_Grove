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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.data.PreferenceStore
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
    private var summaryChild: LocalConversation? = null
    private var markdownRequested: LocalConversationSummary? = null
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

    private fun show(multilingual: Boolean = false, includeSummary: Boolean = false, profileStore: PreferenceStore? = null) {
        val activity = compose.activity
        fixture = File(activity.cacheDir, "phone-backup-test-" + UUID.randomUUID()).apply { check(mkdir()) }
        output = File(activity.filesDir, "updates/apks/backup-test-" + UUID.randomUUID() + ".sgbackup")
        check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
        source = LocalConversationStore(File(fixture, "chats"))
        source.saveNew(seed)
        source.saveNew(excluded)
        if (includeSummary) summaryChild = source.createSummaryContinuation(seed, listOf(0), "Reviewed garden notes", 1100)
        labels = LocalHistoryOrganization(File(fixture, "chats-organization"), source)
        labels.update(seed.id, seed.modelSha256, null, preferences)
        compose.runOnUiThread {
            controller = LocalAiController(File(fixture, "models"), source.directory, activity.applicationInfo.nativeLibraryDir,
                supported = false, freeMemory = { Long.MAX_VALUE }, guard = guard)
            backup = PhoneHistoryBackup(activity.contentResolver, controller, guard, savedState, activity.lifecycleScope, profileStore)
        }
        val actions = LocalAiActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            exportBackup = backup::selectExport, importBackup = backup::selectImport,
            exportMarkdown = if (includeSummary) ({ selected: LocalConversationSummary -> markdownRequested = selected }) else null)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (multilingual) 1.3f else density.fontScale)) {
                    HermesAppTheme(AppPreferences(chinese = multilingual, pinyin = multilingual)) {
                        val state by controller.state.collectAsStateWithLifecycle()
                        LocalAiScreen(state, false, "", {}, false, actions, {})
                        PhoneHistoryBackupFlow(backup, state.busy)
                    }
                }
            }
        }
        val expected = if (includeSummary) 3 else 2
        compose.waitUntil(15_000) { !controller.state.value.busy && controller.state.value.history.size == expected }
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

    private fun waitForState(predicate: (PhoneHistoryBackupState) -> Boolean) {
        try {
            compose.waitUntil(20_000) { predicate(backup.state.value) }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val state = backup.state.value
            val phase = when (state) {
                is PhoneHistoryBackupState.Unlock -> "Unlock, rejected=" + state.rejected
                is PhoneHistoryBackupState.Working -> "Working, step=" + state.step
                is PhoneHistoryBackupState.Finished -> "Finished, result=" + state.result
                else -> state.javaClass.simpleName
            }
            throw AssertionError("Backup fixture timed out in " + phase, failure)
        }
    }

    private fun submitUnlockPassphrase(passphrase: String) {
        val password = compose.onNodeWithTag("phone-backup-unlock-password").performScrollTo().assertIsDisplayed()
        password.performTouchInput { click() }
        password.assertIsFocused().performTextReplacement(passphrase)
        closeDialogKeyboard()
        // Confirm only after the dialog owns the viewport and the IME has settled.
        compose.waitUntil(8_000) { focusedDialogViewport(requireKeyboard = false)?.keyboardVisible == false }
        compose.onNodeWithTag("phone-backup-unlock").assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }

    private fun selectBackupChat(id: String) {
        compose.onNodeWithTag("phone-backup-password").assertDoesNotExist()
        compose.waitUntil(5_000) { !focusedDialogKeyboardVisible() }
        val row = compose.onNodeWithTag("phone-backup-select-" + id)
        row.performScrollTo().assertIsDisplayed().assertIsOff().performTouchInput { click() }
        row.assertIsOn()
    }

    @Test fun protectedProfileBackupWritesOnlySelectedProfilesAndRestoresOneReviewedCopy() {
        val store = (compose.activity.application as HermesApplication).preferences
        val before = runBlocking { store.phonePersonalProfiles.first() }
        val sample = PhonePersonalProfile(UUID.randomUUID().toString(), "Garden fixture " + UUID.randomUUID().toString().take(8),
            "Use short paragraphs", listOf(PhonePersonalNote(UUID.randomUUID().toString(), "Private garden fixture 花")))
        val omitted = PhonePersonalProfile(UUID.randomUUID().toString(), "Omitted fixture " + UUID.randomUUID().toString().take(8),
            "Write in short sentences", emptyList())
        runBlocking { store.editPhonePersonalProfiles { it.save(null, sample).save(null, omitted) } }
        try {
            show(profileStore = store)
            val chatFiles = listOf(seed, excluded).associate { it.id to source.file(it.id).readBytes() }
            val password = "profile-fixture-unique"
            compose.runOnUiThread { assertTrue(backup.selectProfileExport(listOf(sample, omitted))) }
            compose.onNodeWithTag("phone-backup-continue").assertIsNotEnabled()
            val selected = compose.onNodeWithTag("phone-profile-backup-item-${sample.id}")
            selected.performScrollTo().assertIsDisplayed().assertIsOff().performClick().assertIsOn()
            compose.onNodeWithTag("phone-profile-backup-item-${omitted.id}").assertIsOff()
            compose.onNodeWithTag("phone-backup-continue").assertIsEnabled().performClick()
            compose.onNodeWithTag("phone-backup-passphrase").performScrollTo().performTextReplacement(password)
            compose.onNodeWithTag("phone-backup-passphrase-repeat").performScrollTo().performTextReplacement(password)
            compose.onNodeWithTag("phone-backup-prepare").assertIsDisplayed().assertIsEnabled().performClick()
            waitForState { it is PhoneHistoryBackupState.ExportReview }
            compose.onNodeWithTag("phone-backup-choose-destination").performClick()
            compose.waitUntil(10_000) { picks.size == 1 }
            val destination = picks.single()
            assertEquals(PhoneProfileBackup.FILENAME, destination.second.getStringExtra(Intent.EXTRA_TITLE))
            deliverFile(destination)
            waitForState { it is PhoneHistoryBackupState.Finished && it.result == BackupResult.SAVED }
            val bytes = output.readBytes()
            assertTrue(LocalHistoryBackupProtection.recognizesProfileEnvelope(bytes))
            assertEquals(listOf(sample), PhoneProfileBackup.unlock(bytes, password.toCharArray()).entries)
            compose.onNodeWithTag("phone-backup-close").performClick()

            val changed = sample.copy(style = "The local version still exists")
            val expected = before.save(null, changed).save(null, omitted)
            runBlocking { store.editPhonePersonalProfiles { it.save(sample, changed) } }
            compose.runOnUiThread { assertTrue(backup.selectProfileImport()) }
            compose.waitUntil(10_000) { picks.size == 2 }
            deliverFile(picks.last())
            waitForState { it is PhoneHistoryBackupState.Unlock }
            submitUnlockPassphrase(password)
            waitForState { it is PhoneHistoryBackupState.ProfileRestoreReview }
            assertEquals(expected, runBlocking { store.phonePersonalProfiles.first() })
            compose.onNodeWithTag("phone-profile-backup-restore-confirm").assertIsNotEnabled()
            compose.onNodeWithText(Words.PhoneProfileBackupCopy.en).assertExists()
            compose.onNodeWithTag("phone-profile-restore-item-${sample.id}").performScrollTo().assertIsOff().performClick()
            compose.onNodeWithTag("phone-profile-backup-restore-confirm").assertIsDisplayed().assertIsEnabled().performClick()
            waitForState { it is PhoneHistoryBackupState.Finished && it.result == BackupResult.RESTORED }
            val after = runBlocking { store.phonePersonalProfiles.first() }
            assertEquals(expected.entries, after.entries.dropLast(1))
            assertNotEquals(sample.id, after.entries.last().id)
            assertEquals(sample, after.entries.last().copy(id = sample.id))
            assertArrayEquals(bytes, output.readBytes())
            for ((id, raw) in chatFiles) assertArrayEquals(raw, source.file(id).readBytes())
            compose.waitUntil(10_000) { !guard.active.value }
        } finally {
            runBlocking {
                store.editPhonePersonalProfiles { current ->
                    current.copy(entries = current.entries.filterNot { it.name == sample.name || it.id == omitted.id })
                }
            }
        }
    }

    @Test fun protectedBackupMakesAReviewedCopyAndPreservesBothOriginals() {
        show()
        val originals = listOf(seed, excluded).associate { it.id to source.file(it.id).readBytes() }
        val passphrase = "orchard-fixture-94"
        open("phone-history-backup-export")
        val next = compose.onNodeWithTag("phone-backup-continue").assertIsDisplayed().assertIsNotEnabled()
        selectBackupChat(seed.id)
        compose.onNodeWithTag("phone-backup-select-" + excluded.id).performScrollTo().assertIsOff()
        next.assertIsEnabled().performTouchInput { click() }
        val review = compose.onNodeWithTag("phone-backup-prepare").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("phone-backup-protect").performScrollTo().assertIsOn()
        val password = compose.onNodeWithTag("phone-backup-password").performScrollTo().assertIsDisplayed()
        password.performClick().assertIsFocused()
        // Complete the real keyboard transition before calculating the repeat field's scroll.
        compose.waitUntil(5_000) { focusedDialogViewport(requireKeyboard = false)?.keyboardVisible == true }
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
        submitUnlockPassphrase("wrong-fixture")
        waitForState { it == PhoneHistoryBackupState.Unlock(rejected = true) }
        compose.onNodeWithText(Words.PhoneBackupRejected.en).assertIsDisplayed()
        assertEquals(originals.keys, source.list().map { it.id }.toSet())
        submitUnlockPassphrase(passphrase)
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

    @Test fun phoneHistoryOffersMarkdownAndBackupForSummaryBeforeItsFirstQuestion() {
        show(includeSummary = true)
        val child = checkNotNull(summaryChild)
        open("phone-history-export-" + child.id)
        assertEquals(child.id, markdownRequested?.id)
        assertTrue(markdownRequested?.hasReviewedSummary == true)
        assertNull(markdownRequested?.lastState)
        open("phone-history-backup-export")
        val selected = backup.state.value as PhoneHistoryBackupState.Selection
        val row = selected.rows.single { it.id == child.id }
        assertTrue(row.hasReviewedSummary)
        selectBackupChat(child.id)
        val next = compose.onNodeWithTag("phone-backup-continue")
        next.assertIsDisplayed().assertIsEnabled().performTouchInput {
            click()
            click()
        }
        compose.onNodeWithTag("phone-backup-select-" + child.id).assertDoesNotExist()
        val review = compose.onNodeWithTag("phone-backup-prepare").assertIsNotEnabled()
        compose.runOnIdle {
            assertSame(selected, backup.state.value)
            assertFalse(guard.active.value)
        }
        compose.onNodeWithTag("phone-backup-password").performScrollTo().performTextReplacement("discard-on-back")
        compose.onNodeWithTag("phone-backup-repeat").performScrollTo().performTextReplacement("discard-on-back")
        review.assertIsEnabled()
        compose.onNodeWithTag("phone-backup-change-selection").performScrollTo().assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithTag("phone-backup-password").assertDoesNotExist()
        compose.onNodeWithTag("phone-backup-select-" + child.id).performScrollTo().assertIsDisplayed().assertIsOn()

        next.assertIsEnabled().performTouchInput { click() }
        review.assertIsNotEnabled()
        val protect = compose.onNodeWithTag("phone-backup-protect").performScrollTo().assertIsOn()
        protect.assertIsDisplayed().performTouchInput { click() }
        protect.assertIsOff()
        compose.onNodeWithText(Words.PhoneBackupPlainWarning.en).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-backup-password").assertDoesNotExist()
        compose.onNodeWithTag("phone-backup-prepare").assertIsEnabled()
        compose.onNodeWithTag("phone-backup-cancel").performClick()
        assertTrue(picks.isEmpty())
        assertFalse(guard.active.value)
    }

    @Test fun multilingualBackupSelectionKeepsFieldsAndActionsAboveTheKeyboard() {
        show(multilingual = true)
        open("phone-history-backup-export")
        selectBackupChat(seed.id)
        compose.onNodeWithTag("phone-backup-continue").assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        compose.onNodeWithTag("phone-backup-select-" + seed.id).assertDoesNotExist()
        compose.onNodeWithTag("phone-backup-password").performScrollTo().assertIsDisplayed().performClick()
            .assertIsFocused().performTextReplacement("garden-fixture")
        compose.waitUntil(5_000) { focusedDialogKeyboardVisible() }
        compose.onNodeWithTag("phone-backup-repeat").performScrollTo().assertIsDisplayed().performClick()
            .assertIsFocused().performTextReplacement("garden-fixture")
        compose.waitUntil(5_000) { focusedDialogKeyboardVisible() }
        val viewport = checkNotNull(focusedDialogViewport(requireKeyboard = true))
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
