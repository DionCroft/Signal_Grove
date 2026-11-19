package org.hermesmobile.app.local

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal enum class LocalHistoryRestoreOutcome { NONE, ROLLED_BACK, COMMITTED }
internal class LocalHistoryRestoreRecoveryException : IllegalStateException("local_history_restore_recovery")

/** The process holds the library locks from review verification through recovery.
 * After a crash, recover before exposing the chat or label stores. */
internal class LocalHistoryRestoreTransaction(
    private val conversations: LocalConversationStore,
    private val organization: LocalHistoryOrganization,
    private val syncDirectory: (File) -> Unit = ::syncAndroidDirectory,
    private val publishChat: (File, File) -> Unit = ::publishNewChat
) {
    private val root = conversations.directory
    private val journal: File get() = child(root, ".restore")
    private val pending: File get() = child(journal, "pending")
    private val committed: File get() = child(journal, "committed")

    fun prepare(
        archive: LocalHistoryBackupArchive.Inspection,
        checkpoint: () -> Unit = {}
    ): LocalHistoryBackupRestorePlan = locked {
        requireIdle()
        LocalHistoryBackupRestorePlan.prepare(archive, organization.history(checkpoint),
            organization.entries(checkpoint), checkpoint = checkpoint)
    }

    fun recover(): LocalHistoryRestoreOutcome = locked { recoverLocked() }

    fun restore(plan: LocalHistoryBackupRestorePlan, checkpoint: () -> Unit = {}) = locked {
        checkpoint()
        requireIdle()
        val (existingLabels, before) = organization.snapshot(checkpoint)
        val history = organization.history(checkpoint)
        plan.requireCurrent(history, existingLabels)
        val items = plan.items
        val installed = root.listFiles()?.count { it.name.endsWith(".json") } ?: throw LocalHistoryException()
        if (installed + items.size > LocalConversationStore.MAX_CHATS) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.CAPACITY)
        }
        try {
            if (!journal.exists()) Files.createDirectory(journal.toPath())
            syncDirectory(root)
            val manifest = stage(plan, items, existingLabels, before, checkpoint)
            val prepared = child(journal, "prepared")
            writeSynced(prepared) { it.write(manifest.encode()) }
            checkpoint()

            // From this point on, cancellation is reconciled through the journal.
            // The marker and every staged input reach stable storage before a live write.
            publishNewChat(prepared, pending)
            syncDirectory(journal)
            publish(manifest)
            publishNewChat(pending, committed)
            syncDirectory(journal)
        } catch (failure: Exception) {
            if (recoverLocked() != LocalHistoryRestoreOutcome.COMMITTED) throw failure
            return@locked
        }
        check(recoverLocked() == LocalHistoryRestoreOutcome.COMMITTED)
    }

    private fun requireIdle() {
        if (journal.exists() && (!journal.isDirectory || journal.list()?.isNotEmpty() != false)) {
            throw LocalHistoryRestoreRecoveryException()
        }
    }

    private fun stage(
        plan: LocalHistoryBackupRestorePlan,
        items: List<LocalHistoryRestoreItem>,
        existingLabels: Map<String, LocalHistoryOrganizationEntry>,
        before: ByteArray?,
        checkpoint: () -> Unit
    ): Manifest {
        val digests = linkedMapOf<String, String>()
        val labels = existingLabels.toMutableMap()
        for (item in items) {
            checkpoint()
            if (conversations.file(item.targetId).exists()) {
                throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
            }
            val record = plan.readOriginal(item.source.id, checkpoint)
            val original = record.conversation
            val chat = original.copy(id = item.targetId,
                origin = original.origin?.copy(linkedParentId = item.targetParentId))
            val file = stageChat(item.targetId)
            writeSynced(file) { output ->
                if (chat == original) record.writeTranscript(output, checkpoint)
                else output.write(conversations.encode(chat))
            }
            digests[item.targetId] = digest(file, LocalConversationStore.MAX_BYTES, checkpoint)
            record.labels?.let { labels[chat.id] = LocalHistoryOrganizationEntry(chat.modelSha256, it) }
        }
        if (labels == existingLabels) return Manifest(digests, before?.let(::hash), before?.let(::hash))
        val after = organization.encode(labels)
        before?.let { bytes -> writeSynced(child(journal, "labels-before")) { it.write(bytes) } }
        writeSynced(child(journal, "labels-after")) { it.write(after) }
        return Manifest(digests, before?.let(::hash), hash(after))
    }

    private fun publish(manifest: Manifest) {
        val index = organization.index()
        check(labelDigest(index) == manifest.labelsBefore)
        for ((id, sha) in manifest.chats) {
            val staged = stageChat(id)
            check(digest(staged, LocalConversationStore.MAX_BYTES) == sha)
            publishChat(staged, conversations.file(id))
        }
        if (manifest.labelsBefore != manifest.labelsAfter) {
            val after = child(journal, "labels-after")
            check(labelDigest(after) == manifest.labelsAfter)
            Files.move(after.toPath(), index.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }
        verifyCommitted(manifest)
        syncLibrary()
    }

    private fun recoverLocked(): LocalHistoryRestoreOutcome {
        try {
            if (!journal.exists()) return LocalHistoryRestoreOutcome.NONE
            check(journal.isDirectory && !(pending.exists() && committed.exists()))
            val outcome = when {
                committed.exists() -> {
                    verifyCommitted(Manifest.read(committed))
                    syncLibrary()
                    Files.delete(committed.toPath())
                    LocalHistoryRestoreOutcome.COMMITTED
                }
                pending.exists() -> {
                    rollback(Manifest.read(pending))
                    Files.delete(pending.toPath())
                    LocalHistoryRestoreOutcome.ROLLED_BACK
                }
                else -> LocalHistoryRestoreOutcome.NONE
            }
            // Persist retirement before removing the bytes that recovery may need.
            syncDirectory(journal)
            if (outcome == LocalHistoryRestoreOutcome.NONE) discardStaging()
            else try {
                discardStaging()
            } catch (_: Exception) {
                // The outcome is durable. The next recovery may finish staging cleanup.
            }
            return outcome
        } catch (_: Exception) {
            throw LocalHistoryRestoreRecoveryException()
        }
    }

    private fun verifyCommitted(manifest: Manifest) {
        check(labelDigest(organization.index()) == manifest.labelsAfter)
        manifest.chats.forEach { (id, sha) ->
            check(digest(conversations.file(id), LocalConversationStore.MAX_BYTES) == sha)
        }
    }

    private fun rollback(manifest: Manifest) {
        val index = organization.index()
        val current = labelDigest(index)
        check(current == manifest.labelsBefore || current == manifest.labelsAfter)
        val installed = manifest.chats.mapNotNull { (id, sha) ->
            val file = conversations.file(id)
            if (!file.exists()) null else file.also { check(digest(it, LocalConversationStore.MAX_BYTES) == sha) }
        }
        if (current != manifest.labelsBefore) {
            if (manifest.labelsBefore == null) {
                Files.delete(index.toPath())
            } else {
                val prior = readBounded(child(journal, "labels-before"), LocalHistoryOrganization.MAX_BYTES)
                check(hash(prior) == manifest.labelsBefore)
                val temp = child(journal, "labels-rollback")
                Files.deleteIfExists(temp.toPath())
                writeSynced(temp) { it.write(prior) }
                Files.move(temp.toPath(), index.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            }
        }
        installed.forEach { Files.delete(it.toPath()) }
        syncLibrary()
    }

    private fun syncLibrary() {
        syncDirectory(root)
        syncDirectory(requireNotNull(organization.index().parentFile))
        syncDirectory(requireNotNull(root.parentFile))
    }

    private fun discardStaging() {
        val files = journal.listFiles() ?: error("restore stage unavailable")
        val verified = files.map {
            check(it.name in STAGED_FILES || it.name.matches(CHAT_FILE))
            child(journal, it.name).also { file -> check(file.isFile) }
        }
        verified.forEach { Files.delete(it.toPath()) }
    }

    private fun stageChat(id: String): File {
        requireCanonicalChatId(id)
        return child(journal, "$id.json")
    }

    private fun <T> locked(action: () -> T): T =
        synchronized(organization) { synchronized(conversations) { action() } }

    private class Manifest(val chats: Map<String, String>, val labelsBefore: String?, val labelsAfter: String?) {
        fun encode() = buildString {
            appendLine(VERSION)
            appendLine(labelsBefore ?: "-")
            appendLine(labelsAfter ?: "-")
            chats.forEach { (id, sha) -> appendLine("$id:$sha") }
        }.toByteArray(Charsets.US_ASCII)

        companion object {
            private const val VERSION = "SG-RESTORE-1"

            fun read(file: File): Manifest {
                val rows = readBounded(file, 6_144).toString(Charsets.US_ASCII).split('\n')
                require(rows.size in 5..(LocalConversationStore.MAX_CHATS + 4) && rows[0] == VERSION && rows.last().isEmpty())
                fun labels(sha: String) = sha.takeUnless { it == "-" }?.also { require(it.matches(SHA)) }
                val chats = linkedMapOf<String, String>()
                for (row in rows.subList(3, rows.lastIndex)) {
                    require(row.length == 101 && row[36] == ':')
                    val id = row.substring(0, 36)
                    val sha = row.substring(37)
                    requireCanonicalChatId(id)
                    require(sha.matches(SHA) && chats.put(id, sha) == null)
                }
                return Manifest(chats, labels(rows[1]), labels(rows[2]))
            }
        }
    }

    companion object {
        private val SHA = Regex("[a-f0-9]{64}")
        private val CHAT_FILE = Regex("[a-f0-9-]{36}\\.json")
        private val STAGED_FILES = setOf("prepared", "labels-before", "labels-after", "labels-rollback")

        private fun child(directory: File, name: String) = File(directory, name).also {
            check(!Files.isSymbolicLink(it.toPath()) && it.canonicalFile == it.absoluteFile)
        }

        private fun writeSynced(file: File, write: (FileOutputStream) -> Unit) {
            Files.createFile(file.toPath())
            file.outputStream().use { output ->
                write(output)
                output.fd.sync()
            }
        }

        /** Both destination checks and renames run under the two library locks.
         * The app owns these private files and has one process that writes them. */
        internal fun publishNewChat(source: File, target: File) {
            check(Files.notExists(target.toPath(), NOFOLLOW_LINKS))
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE)
        }

        private fun readBounded(file: File, limit: Int): ByteArray {
            val size = file.length()
            require(file.isFile && size in 1L..limit.toLong())
            return file.inputStream().use { input ->
                ByteArray(size.toInt()).also {
                    java.io.DataInputStream(input).readFully(it)
                    require(input.read() == -1)
                }
            }
        }

        private fun digest(file: File, limit: Int, checkpoint: () -> Unit = {}): String {
            require(file.isFile && file.length() in 1L..limit.toLong())
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                var total = 0
                val buffer = ByteArray(8192)
                while (true) {
                    checkpoint()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= limit)
                    digest.update(buffer, 0, read)
                }
                require(total > 0)
            }
            return digest.digest().hex()
        }

        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        private fun labelDigest(file: File) =
            if (file.exists()) digest(file, LocalHistoryOrganization.MAX_BYTES) else null

        private fun syncAndroidDirectory(file: File) {
            val flags = OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW
            val fd = Os.open(file.path, flags, 0)
            try {
                check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode))
                Os.fsync(fd)
            } finally {
                Os.close(fd)
            }
        }
    }
}
