package org.hermesmobile.app.local

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hermesmobile.app.data.PhoneWorkspaceBackupDao
import org.hermesmobile.app.data.PreferenceStore
import org.hermesmobile.app.data.WorkspaceBackupArchive
import org.hermesmobile.app.data.WorkspaceBackupKey
import org.hermesmobile.app.data.WorkspaceBackupRow
import org.hermesmobile.app.data.WorkspaceRestorePlan
import org.hermesmobile.app.updates.UpdateWorkGuard

internal enum class BackupCollection { CHATS, PROFILES, WORKSPACE }
internal enum class BackupStep { PREPARING, READING, REVIEWING, WRITING, RESTORING }
internal enum class BackupResult {
    SAVED, RESTORED, RESTORED_REFRESH_NEEDED, CANCELLED, STOPPED, WRITE_STOPPED,
    WRITE_UNCERTAIN, RESTORE_UNCERTAIN, INTERRUPTED, PICKER_UNAVAILABLE, FILE_UNAVAILABLE,
    BUSY, EMPTY, INVALID, CHANGED, CAPACITY, TOO_LARGE, INVALID_PASSPHRASE, CRYPTO_UNAVAILABLE, FAILED
}

internal sealed interface PhoneHistoryBackupState {
    data object Idle : PhoneHistoryBackupState
    data class Selection(val rows: List<LocalConversationSummary>) : PhoneHistoryBackupState
    data class ProfileSelection(val profiles: List<PhonePersonalProfile>) : PhoneHistoryBackupState
    data class WorkspaceSelection(val rows: List<WorkspaceBackupRow>, val incoming: WorkspaceBackupArchive? = null) :
        PhoneHistoryBackupState
    data class Working(val step: BackupStep, val stopping: Boolean = false) : PhoneHistoryBackupState
    data class ExportReview(val count: Int, val bytes: Long, val format: LocalHistoryBackupFormat,
        val collection: BackupCollection = BackupCollection.CHATS,
        val workspaceRows: List<WorkspaceBackupRow> = emptyList()) : PhoneHistoryBackupState
    data class Picking(val id: String, val filename: String?) : PhoneHistoryBackupState
    data class Unlock(val rejected: Boolean = false,
        val collection: BackupCollection = BackupCollection.CHATS) : PhoneHistoryBackupState
    data class RestoreReview(val plan: LocalHistoryBackupRestorePlan, val format: LocalHistoryBackupFormat) : PhoneHistoryBackupState
    data class ProfileRestoreReview(val plan: ProfileRestorePlan) : PhoneHistoryBackupState
    data class WorkspaceRestoreReview(val plan: WorkspaceRestorePlan) : PhoneHistoryBackupState
    data class Finished(val result: BackupResult, val restoredCount: Int = 0,
        val collection: BackupCollection = BackupCollection.CHATS) : PhoneHistoryBackupState
}

/** Retains only the active picker/review in memory. A dead process cannot resume file or restore work. */
@MainThread
internal class PhoneHistoryBackup(
    private val resolver: ContentResolver,
    private val controller: LocalAiController,
    private val guard: UpdateWorkGuard,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val preferences: PreferenceStore? = null,
    private val workspace: PhoneWorkspaceBackupDao? = null
) {
    private val mutable = MutableStateFlow<PhoneHistoryBackupState>(
        if (savedState.get<Boolean>(IN_FLIGHT) == true) PhoneHistoryBackupState.Finished(BackupResult.INTERRUPTED)
        else PhoneHistoryBackupState.Idle
    )
    val state = mutable.asStateFlow()
    private var document: LocalHistoryBackupDocument? = null
    private var collection = BackupCollection.CHATS
    private var smallCiphertext: ByteArray? = null
    private var review: LocalHistoryBackupReview? = null
    private var pickerLaunched = false
    private var operation: BackupOperation? = null
    private var disposed = false

    fun selectExport() {
        if (!canStart()) return
        val rows = controller.state.value.history.filter { it.canExport }
        if (rows.isNotEmpty()) {
            collection = BackupCollection.CHATS
            mutable.value = PhoneHistoryBackupState.Selection(rows)
        }
    }

    fun prepareExport(selection: PhoneHistoryBackupState.Selection, ids: Set<String>, passphrase: CharArray?) {
        val rows = selection.rows.filter { it.id in ids }
        if (disposed || mutable.value !== selection || rows.isEmpty() || rows.size != ids.size) {
            passphrase?.fill('\u0000')
            return
        }
        work(BackupStep.PREPARING) { task ->
            try {
                // Publish the owned stage on the main dispatcher before handling a stop.
                val staged = withContext(Dispatchers.IO) {
                    controller.historyBackupFiles.export(rows, passphrase, task::checkpoint)
                }
                document = staged
                task.checkpoint()
                mutable.value = PhoneHistoryBackupState.ExportReview(rows.size, staged.bytes, staged.format)
            } finally {
                passphrase?.fill('\u0000')
            }
        }
    }

    fun selectImport() {
        if (!canStart()) return
        collection = BackupCollection.CHATS
        pick(filename = null)
    }

    fun selectProfileExport(profiles: List<PhonePersonalProfile>): Boolean {
        if (!canStart() || preferences == null || profiles.isEmpty()) return false
        collection = BackupCollection.PROFILES
        mutable.value = PhoneHistoryBackupState.ProfileSelection(profiles.toList())
        return true
    }

    fun selectProfileImport(): Boolean {
        if (!canStart() || preferences == null) return false
        collection = BackupCollection.PROFILES
        pick(filename = null)
        return true
    }

    fun prepareProfileExport(selection: PhoneHistoryBackupState.ProfileSelection, ids: Set<String>, passphrase: CharArray) {
        val source = preferences
        val chosen = selection.profiles.filter { it.id in ids }
        if (disposed || mutable.value !== selection || source == null || chosen.isEmpty() || chosen.size != ids.size) {
            passphrase.fill('\u0000')
            return
        }
        work(BackupStep.PREPARING) { task ->
            try {
                val current = source.phonePersonalProfiles.first()
                if (!current.entries.containsAll(chosen)) throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
                val bytes = withContext(Dispatchers.IO) {
                    PhoneProfileBackup.encrypt(PhonePersonalProfiles(entries = chosen), passphrase, task::checkpoint)
                }
                smallCiphertext = bytes
                task.checkpoint()
                mutable.value = PhoneHistoryBackupState.ExportReview(chosen.size, bytes.size.toLong(),
                    LocalHistoryBackupFormat.PROTECTED, collection)
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun selectWorkspaceExport() {
        val storage = workspace ?: return
        if (!canStart()) return
        collection = BackupCollection.WORKSPACE
        work(BackupStep.PREPARING) { task ->
            val rows = storage.catalog()
            task.checkpoint()
            if (rows.isEmpty()) finish(BackupResult.EMPTY)
            else mutable.value = PhoneHistoryBackupState.WorkspaceSelection(rows)
        }
    }

    fun selectWorkspaceImport() {
        if (workspace == null || !canStart()) return
        collection = BackupCollection.WORKSPACE
        pick(filename = null)
    }

    fun prepareWorkspaceExport(selection: PhoneHistoryBackupState.WorkspaceSelection, ids: Set<WorkspaceBackupKey>,
        passphrase: CharArray) {
        val storage = workspace
        if (disposed || mutable.value !== selection || storage == null || selection.incoming != null ||
            ids.isEmpty() || !selection.rows.map { it.key }.containsAll(ids)) {
            passphrase.fill('\u0000')
            return
        }
        work(BackupStep.PREPARING) { task ->
            try {
                val archive = storage.snapshot(ids.toSet())
                task.checkpoint()
                val bytes = withContext(Dispatchers.IO) { PhoneWorkspaceBackup.encrypt(archive, passphrase, task::checkpoint) }
                smallCiphertext = bytes
                task.checkpoint()
                mutable.value = PhoneHistoryBackupState.ExportReview(archive.count, bytes.size.toLong(),
                    LocalHistoryBackupFormat.PROTECTED, collection, archive.rows())
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun chooseDestination(selection: PhoneHistoryBackupState.ExportReview) {
        if (disposed || mutable.value !== selection) return
        pick(when (selection.collection) {
            BackupCollection.CHATS -> checkNotNull(document).suggestedFilename
            BackupCollection.PROFILES -> PhoneProfileBackup.FILENAME
            BackupCollection.WORKSPACE -> PhoneWorkspaceBackup.FILENAME
        })
    }

    private fun pick(filename: String?) {
        begin()
        pickerLaunched = false
        mutable.value = PhoneHistoryBackupState.Picking(UUID.randomUUID().toString(), filename)
    }

    fun launchPicker(id: String, launch: () -> Unit) {
        val picker = mutable.value as? PhoneHistoryBackupState.Picking ?: return
        if (disposed || picker.id != id || pickerLaunched) return
        pickerLaunched = true
        try {
            launch()
        } catch (_: RuntimeException) {
            if (mutable.value === picker) finish(BackupResult.PICKER_UNAVAILABLE)
        }
    }

    fun picked(id: String, uri: Uri?) {
        val picker = mutable.value as? PhoneHistoryBackupState.Picking ?: return
        if (disposed || picker.id != id || !pickerLaunched) return
        pickerLaunched = false
        when {
            uri == null -> finish(BackupResult.CANCELLED)
            uri.scheme != ContentResolver.SCHEME_CONTENT -> finish(BackupResult.FILE_UNAVAILABLE)
            picker.filename != null -> write(uri)
            collection == BackupCollection.CHATS -> read(uri)
            else -> readSmallCollection(uri)
        }
    }

    private fun write(uri: Uri) {
        work(BackupStep.WRITING) { task ->
            val result = withContext(Dispatchers.IO) {
                task.writer.save(resolver, uri) { output, checkpoint ->
                    val ciphertext = smallCiphertext
                    if (collection == BackupCollection.CHATS) checkNotNull(document).writeTo(output, checkpoint)
                    else {
                        val bytes = checkNotNull(ciphertext)
                        var offset = 0
                        while (offset < bytes.size) {
                            checkpoint()
                            val size = minOf(8192, bytes.size - offset)
                            output.write(bytes, offset, size)
                            offset += size
                        }
                        checkpoint()
                    }
                }
            }
            finish(when (result) {
                DocumentWriteResult.SAVED -> BackupResult.SAVED
                DocumentWriteResult.STOPPED -> BackupResult.WRITE_STOPPED
                DocumentWriteResult.UNCERTAIN -> BackupResult.WRITE_UNCERTAIN
            })
        }
    }

    private fun read(uri: Uri) = work(BackupStep.READING) { task ->
        val staged = withContext(Dispatchers.IO) {
            task.read(resolver, uri) { controller.historyBackupFiles.stageImport(it, task::checkpoint) }
        }
        document = staged
        task.checkpoint()
        if (staged.format == LocalHistoryBackupFormat.PROTECTED) {
            mutable.value = PhoneHistoryBackupState.Unlock()
        } else {
            inspect(staged, null, task)
        }
    }

    private fun readSmallCollection(uri: Uri) = work(BackupStep.READING) { task ->
        smallCiphertext = withContext(Dispatchers.IO) {
            task.read(resolver, uri) {
                when (collection) {
                    BackupCollection.PROFILES -> PhoneProfileBackup.readFile(it, task::checkpoint)
                    BackupCollection.WORKSPACE -> PhoneWorkspaceBackup.readFile(it, task::checkpoint)
                    BackupCollection.CHATS -> error("Expected a selected-data backup.")
                }
            }
        }
        task.checkpoint()
        mutable.value = PhoneHistoryBackupState.Unlock(collection = collection)
    }

    fun unlock(passphrase: CharArray) {
        if (disposed || mutable.value !is PhoneHistoryBackupState.Unlock) {
            passphrase.fill('\u0000')
            return
        }
        work(BackupStep.REVIEWING) { task ->
            try {
                when (collection) {
                    BackupCollection.CHATS -> inspect(checkNotNull(document), passphrase, task)
                    BackupCollection.PROFILES -> {
                        val ciphertext = checkNotNull(smallCiphertext)
                        val profiles = withContext(Dispatchers.IO) {
                            PhoneProfileBackup.unlock(ciphertext, passphrase, task::checkpoint)
                        }
                        val current = checkNotNull(preferences).phonePersonalProfiles.first()
                        task.checkpoint()
                        mutable.value = PhoneHistoryBackupState.ProfileRestoreReview(ProfileRestorePlan(current, profiles))
                    }
                    BackupCollection.WORKSPACE -> {
                        val ciphertext = checkNotNull(smallCiphertext)
                        val incoming = withContext(Dispatchers.IO) {
                            PhoneWorkspaceBackup.unlock(ciphertext, passphrase, task::checkpoint)
                        }
                        task.checkpoint()
                        ciphertext.fill(0)
                        smallCiphertext = null
                        mutable.value = PhoneHistoryBackupState.WorkspaceSelection(incoming.rows(), incoming)
                    }
                }
            } catch (error: LocalHistoryBackupException) {
                if (error.problem != LocalHistoryBackupProblem.AUTHENTICATION) throw error
                task.checkpoint()
                mutable.value = PhoneHistoryBackupState.Unlock(rejected = true, collection = collection)
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    private suspend fun inspect(staged: LocalHistoryBackupDocument, passphrase: CharArray?, task: BackupOperation) {
        val inspected = withContext(Dispatchers.IO) { staged.inspect(passphrase, task::checkpoint) }
        review = inspected
        task.checkpoint()
        val plan = controller.prepareHistoryRestore(inspected.archive, task::checkpoint)
        task.checkpoint()
        mutable.value = PhoneHistoryBackupState.RestoreReview(plan, staged.format)
    }

    fun restore(selection: PhoneHistoryBackupState.RestoreReview) {
        if (disposed || mutable.value !== selection) return
        work(BackupStep.RESTORING) {
            val refreshed = controller.restoreHistory(selection.plan)
            finish(if (refreshed) BackupResult.RESTORED else BackupResult.RESTORED_REFRESH_NEEDED, selection.plan.items.size)
        }
    }

    fun restoreProfiles(selection: PhoneHistoryBackupState.ProfileRestoreReview, ids: Set<String>) {
        val store = preferences ?: return
        if (disposed || mutable.value !== selection || controller.state.value.busy) return
        val checked = ids.toSet()
        work(BackupStep.RESTORING) {
            store.editPhonePersonalProfiles { current -> selection.plan.restore(current, checked) }
            finish(BackupResult.RESTORED, checked.size)
        }
    }

    fun reviewWorkspaceRestore(selection: PhoneHistoryBackupState.WorkspaceSelection, ids: Set<WorkspaceBackupKey>) {
        val storage = workspace ?: return
        val incoming = selection.incoming ?: return
        if (disposed || mutable.value !== selection || ids.isEmpty()) return
        work(BackupStep.REVIEWING) { task ->
            val plan = storage.review(incoming.selected(ids.toSet()))
            task.checkpoint()
            mutable.value = PhoneHistoryBackupState.WorkspaceRestoreReview(plan)
        }
    }

    fun restoreWorkspace(selection: PhoneHistoryBackupState.WorkspaceRestoreReview) {
        val storage = workspace ?: return
        if (disposed || mutable.value !== selection || controller.state.value.busy) return
        work(BackupStep.RESTORING) {
            finish(BackupResult.RESTORED, storage.restore(selection.plan))
        }
    }

    fun dismiss() {
        if (disposed || mutable.value is PhoneHistoryBackupState.Working || mutable.value is PhoneHistoryBackupState.Picking) return
        closeStages()
        savedState.remove<Boolean>(IN_FLIGHT)
        mutable.value = PhoneHistoryBackupState.Idle
        guard.set(this, false)
    }

    fun stop() {
        val working = mutable.value as? PhoneHistoryBackupState.Working ?: return
        if (working.step == BackupStep.RESTORING || working.stopping) return
        mutable.value = working.copy(stopping = true)
        requestStop()
    }

    fun clear() {
        disposed = true
        if (operation == null) finish(BackupResult.CANCELLED)
        else if ((mutable.value as? PhoneHistoryBackupState.Working)?.step != BackupStep.RESTORING) requestStop()
    }

    private fun canStart() = !disposed && mutable.value == PhoneHistoryBackupState.Idle && !controller.state.value.busy

    private fun begin() {
        savedState[IN_FLIGHT] = true
        guard.set(this, true)
    }

    private fun work(step: BackupStep, block: suspend (BackupOperation) -> Unit) {
        check(operation == null)
        val task = BackupOperation()
        operation = task
        begin()
        mutable.value = PhoneHistoryBackupState.Working(step)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Keep owned stages and the update guard until all provider or transaction work has returned.
            withContext(NonCancellable) {
                try {
                    block(task)
                } catch (_: OperationCanceledException) {
                    finish(if (task.stopped) BackupResult.STOPPED else BackupResult.FILE_UNAVAILABLE)
                } catch (_: LocalHistoryBackupBusyException) {
                    finish(BackupResult.BUSY)
                } catch (failure: LocalHistoryBackupException) {
                    finish(if (task.stopped) BackupResult.STOPPED else backupProblem(failure.problem))
                } catch (_: LocalHistoryRestoreRecoveryException) {
                    finish(if (step == BackupStep.RESTORING) BackupResult.RESTORE_UNCERTAIN else BackupResult.FAILED)
                } catch (_: Exception) {
                    finish(when {
                        task.stopped -> BackupResult.STOPPED
                        step == BackupStep.READING -> BackupResult.FILE_UNAVAILABLE
                        step == BackupStep.RESTORING -> BackupResult.RESTORE_UNCERTAIN
                        else -> BackupResult.FAILED
                    })
                } finally {
                    operation = null
                    if (disposed) closeStages()
                    if (disposed || mutable.value is PhoneHistoryBackupState.Finished) guard.set(this@PhoneHistoryBackup, false)
                }
            }
        }
    }

    private fun requestStop() {
        val task = operation ?: return
        task.markStopped()
        scope.launch(Dispatchers.IO + NonCancellable) { task.cancelProvider() }
    }

    private fun finish(result: BackupResult, count: Int = 0) {
        closeStages()
        savedState.remove<Boolean>(IN_FLIGHT)
        mutable.value = PhoneHistoryBackupState.Finished(result, count, collection)
        if (operation == null) guard.set(this, false)
    }

    private fun closeStages() {
        smallCiphertext?.fill(0)
        smallCiphertext = null
        runCatching { review?.close() }
        review = null
        runCatching { document?.close() }
        document = null
    }

    private companion object { const val IN_FLIGHT = "phone_history_backup_in_flight" }
}

private class BackupOperation {
    private val stop = AtomicBoolean()
    private val signal = CancellationSignal()
    private val input = AtomicReference<InputStream?>()
    val writer = PhoneDocumentWrite()
    val stopped: Boolean get() = stop.get()

    fun markStopped() { stop.set(true); writer.markStopped() }

    fun cancelProvider() {
        writer.cancelProvider()
        runCatching { signal.cancel() }
        runCatching { input.getAndSet(null)?.close() }
    }

    fun checkpoint() {
        if (stopped) throw OperationCanceledException()
    }

    fun <T> read(resolver: ContentResolver, uri: Uri, consume: (InputStream) -> T): T {
        checkpoint()
        val descriptor = resolver.openAssetFileDescriptor(uri, "r", signal) ?: throw IOException("no_document")
        val stream = try {
            descriptor.createInputStream()
        } catch (failure: Exception) {
            descriptor.close()
            throw failure
        }
        input.set(stream)
        try {
            checkpoint()
            return consume(stream)
        } finally {
            runCatching { input.getAndSet(null)?.close() }
        }
    }
}

private fun backupProblem(problem: LocalHistoryBackupProblem) = when (problem) {
    LocalHistoryBackupProblem.INVALID, LocalHistoryBackupProblem.UNSUPPORTED_VERSION,
    LocalHistoryBackupProblem.AUTHENTICATION -> BackupResult.INVALID
    LocalHistoryBackupProblem.CHANGED -> BackupResult.CHANGED
    LocalHistoryBackupProblem.CAPACITY -> BackupResult.CAPACITY
    LocalHistoryBackupProblem.TOO_LARGE -> BackupResult.TOO_LARGE
    LocalHistoryBackupProblem.INVALID_PASSPHRASE -> BackupResult.INVALID_PASSPHRASE
    LocalHistoryBackupProblem.CRYPTO_UNAVAILABLE -> BackupResult.CRYPTO_UNAVAILABLE
    LocalHistoryBackupProblem.IDENTIFIER_UNAVAILABLE -> BackupResult.FAILED
}
