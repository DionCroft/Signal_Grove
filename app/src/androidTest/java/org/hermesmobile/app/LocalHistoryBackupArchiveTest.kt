package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.CancellationException
import org.hermesmobile.app.local.*
import org.junit.Assert.*
import org.junit.Test

/** Real Android files with a generated fixture in a unique private cache directory. */
class LocalHistoryBackupArchiveTest {
    private fun root() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "history-archive-" + UUID.randomUUID()).also { check(it.mkdir()) }

    @Test fun androidArchivePreservesMultichunkUnicodeBranchAndLabelsOutsideLiveHistory() {
        val root = root()
        val history = File(root, "fixture")
        val store = LocalConversationStore(history)
        val parent = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 10, exchanges = listOf(
            LocalExchange("Quiet garden 花园", "Saved answer 🌱 " + "x".repeat(100_000), LocalReplyState.COMPLETE)
        ))
        store.saveNew(parent)
        val branch = store.createBranch(parent, 0, LocalBranchKind.FORK, 11)
        val originals = history.listFiles()!!.associate { it.name to it.readBytes().toList() }
        val rows = store.list().associateBy { it.id }
        val labels = LocalHistoryOrganizationEntry(branch.modelSha256,
            LocalHistoryPreferences("分支 🌱", pinned = true, archived = true))
        val file = File(root, "archive.sgbackup")
        file.outputStream().use { out ->
            LocalHistoryBackupArchive.write(listOf(branch.id, parent.id), out, { id ->
                val metadata = labels.takeIf { id == branch.id }
                store.backupRecord(rows.getValue(id).copy(organization = metadata), metadata)
            })
        }
        assertTrue(file.length() > 200_000)
        val inspection = LocalHistoryBackupArchive.inspect(file)
        assertEquals(listOf(branch.id, parent.id), inspection.items.map { it.id })
        assertEquals(labels.preferences, inspection.items.first().labels)
        assertEquals(branch, inspection.readRecord(branch.id).conversation)
        assertEquals(parent, inspection.readRecord(parent.id).conversation)
        assertEquals(originals, history.listFiles()!!.associate { it.name to it.readBytes().toList() })
    }

    @Test fun androidInterruptedInspectionAndStagedByteChangeCannotSupplyAReviewRecord() {
        val root = root()
        val history = File(root, "fixture")
        val store = LocalConversationStore(history)
        val parent = LocalConversation(modelSha256 = "b".repeat(64), updatedAt = 20, exchanges = listOf(
            LocalExchange("Saved garden", "x".repeat(100_000), LocalReplyState.INTERRUPTED)
        ))
        store.saveNew(parent)
        val row = store.list().single()
        val file = File(root, "archive.sgbackup")
        file.outputStream().use { out ->
            LocalHistoryBackupArchive.write(listOf(parent.id), out, { store.backupRecord(row, null) })
        }
        var checkpoints = 0
        try {
            LocalHistoryBackupArchive.inspect(file) {
                if (++checkpoints == 5) throw CancellationException("fixture")
            }
            fail("Expected controlled inspection stop")
        } catch (_: CancellationException) { }
        val reviewed = LocalHistoryBackupArchive.inspect(file)
        RandomAccessFile(file, "rw").use {
            it.seek(59)
            it.writeByte(0)
        }
        try {
            reviewed.readRecord(parent.id)
            fail("Expected stale staged record")
        } catch (e: LocalHistoryBackupException) {
            assertEquals(LocalHistoryBackupProblem.CHANGED, e.problem)
        }
        assertEquals(parent, store.load(parent.id))
    }
}
