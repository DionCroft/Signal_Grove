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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hermesmobile.app.updates.UpdateWorkGuard

internal enum class BackupStep { PREPARING, READING, REVIEWING, WRITING, RESTORING }
internal enum class BackupResult {
    SAVED, RESTORED, RESTORED_REFRESH_NEEDED, CANCELLED, STOPPED, WRITE_STOPPED,
    WRITE_UNCERTAIN, RESTORE_UNCERTAIN, INTERRUPTED, PICKER_UNAVAILABLE, FILE_UNAVAILABLE,
    BUSY, INVALID, CHANGED, CAPACITY, INVALID_PASSPHRASE, CRYPTO_UNAVAILABLE, FAILED
}

internal sealed interface PhoneHistoryBackupState {
    data object Idle : PhoneHistoryBackupState
    data class Selection(val rows: List<LocalConversationSummary>) : PhoneHistoryBackupState
    data class Working(val step: BackupStep, val stopping: Boolean = false) : PhoneHistoryBackupState
    data class ExportReview(val count: Int, val bytes: Long, val format: LocalHistoryBackupFormat) : PhoneHistoryBackupState
    data class Picking(val id: String, val filename: String?) : PhoneHistoryBackupState
    data class Unlock(val rejected: Boolean = false) : PhoneHistoryBackupState
    data class RestoreReview(val plan: LocalHistoryBackupRestorePlan, val format: LocalHistoryBackupFormat) : PhoneHistoryBackupState
    data class Finished(val result: BackupResult, val restoredCount: Int = 0) : PhoneHistoryBackupState
}

/** Retains only the active picker/review in memory. A dead process cannot resume file or restore work. */
@MainThread
internal class PhoneHistoryBackup(
    private val resolver: ContentResolver,
    private val controller: LocalAiController,
    private val guard: UpdateWorkGuard,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow<PhoneHistoryBackupState>(
        if (savedState.get<Boolean>(IN_FLIGHT) == true) PhoneHistoryBackupState.Finished(BackupResult.INTERRUPTED)
        else PhoneHistoryBackupState.Idle
    )
    val state = mutable.asStateFlow()
    private var document: LocalHistoryBackupDocument? = null
    private var review: LocalHistoryBackupReview? = null
    private var pickerLaunched = false
    private var operation: BackupOperation? = null
    private var disposed = false

    fun selectExport() {
        if (!canStart()) return
        val rows = controller.state.value.history.filter { it.canExport }
        if (rows.isNotEmpty()) mutable.value = PhoneHistoryBackupState.Selection(rows)
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
        if (canStart()) pick(filename = null)
    }

    fun chooseDestination(selection: PhoneHistoryBackupState.ExportReview) {
        if (disposed || mutable.value !== selection) return
        pick(checkNotNull(document).suggestedFilename)
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
            else -> read(uri)
        }
    }

    private fun write(uri: Uri) {
        val staged = checkNotNull(document)
        work(BackupStep.WRITING) { task ->
            val result = withContext(Dispatchers.IO) {
                task.writer.save(resolver, uri, staged::writeTo)
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

    fun unlock(passphrase: CharArray) {
        if (disposed || mutable.value !is PhoneHistoryBackupState.Unlock) {
            passphrase.fill('\u0000')
            return
        }
        val staged = checkNotNull(document)
        work(BackupStep.REVIEWING) { task ->
            try {
                inspect(staged, passphrase, task)
            } catch (error: LocalHistoryBackupException) {
                if (error.problem != LocalHistoryBackupProblem.AUTHENTICATION) throw error
                task.checkpoint()
                mutable.value = PhoneHistoryBackupState.Unlock(rejected = true)
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
        mutable.value = PhoneHistoryBackupState.Finished(result, count)
        if (operation == null) guard.set(this, false)
    }

    private fun closeStages() {
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
    LocalHistoryBackupProblem.INVALID_PASSPHRASE -> BackupResult.INVALID_PASSPHRASE
    LocalHistoryBackupProblem.CRYPTO_UNAVAILABLE -> BackupResult.CRYPTO_UNAVAILABLE
    LocalHistoryBackupProblem.IDENTIFIER_UNAVAILABLE -> BackupResult.FAILED
}
