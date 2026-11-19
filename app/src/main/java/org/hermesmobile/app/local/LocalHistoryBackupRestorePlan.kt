package org.hermesmobile.app.local

import java.util.UUID

internal data class LocalHistoryRestoreItem(
    val source: LocalHistoryBackupItem,
    val targetId: String,
    val targetParentId: String?
) {
    val remapped: Boolean get() = source.id != targetId
    val parentOutsideArchive: Boolean get() = source.origin != null && targetParentId == null
}

/** Read-only review tied to one validated archive and one observed phone library.
 * Missing parents remain provenance. A matching phone UUID alone cannot link a branch. */
internal class LocalHistoryBackupRestorePlan private constructor(
    private val archive: LocalHistoryBackupArchive.Inspection,
    private val destinationHistory: Map<String, LocalConversationSummary>,
    private val destinationLabels: Map<String, LocalHistoryOrganizationEntry>,
    private val reviewed: Map<String, LocalHistoryRestoreItem>
) {
    val items: List<LocalHistoryRestoreItem> get() = reviewed.values.toList()

    fun requireCurrent(
        history: List<LocalConversationSummary>,
        labels: Map<String, LocalHistoryOrganizationEntry>
    ) {
        val current = index(history)
        if (current != destinationHistory || labels != destinationLabels) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
        }
    }

    fun readOriginal(sourceId: String, checkpoint: () -> Unit = {}): LocalHistoryBackupRecord {
        if (sourceId !in reviewed) throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
        return archive.readRecord(sourceId, checkpoint)
    }

    companion object {
        fun prepare(
            archive: LocalHistoryBackupArchive.Inspection,
            history: List<LocalConversationSummary>,
            labels: Map<String, LocalHistoryOrganizationEntry>,
            newId: () -> String = { UUID.randomUUID().toString() },
            checkpoint: () -> Unit = {}
        ): LocalHistoryBackupRestorePlan {
            checkpoint()
            val destinationHistory = index(history)
            // Orphan labels still reserve their ID. Import must never adopt or replace them.
            val destinationLabels = labels.toMap()
            destinationLabels.keys.forEach(::requireCanonicalChatId)
            val source = archive.items
            if (destinationHistory.size + source.size > LocalConversationStore.MAX_CHATS ||
                destinationLabels.size + source.count { it.labels != null } > LocalConversationStore.MAX_CHATS
            ) throw LocalHistoryBackupException(LocalHistoryBackupProblem.CAPACITY)

            val occupied = destinationHistory.keys + destinationLabels.keys
            val reserved = occupied.toMutableSet()
            source.forEach { row ->
                reserved.add(row.id)
                row.origin?.parentId?.let(reserved::add)
                row.origin?.linkedParentId?.let(reserved::add)
            }
            val targetIds = source.associate { row ->
                checkpoint()
                row.id to if (row.id in occupied) allocate(reserved, newId, checkpoint) else row.id
            }
            val items = source.associate { row ->
                row.id to LocalHistoryRestoreItem(row, targetIds.getValue(row.id), row.origin?.linkedParentId?.let { targetIds[it] })
            }
            checkpoint()
            return LocalHistoryBackupRestorePlan(archive, destinationHistory, destinationLabels, items)
        }

        private fun index(history: List<LocalConversationSummary>): Map<String, LocalConversationSummary> {
            val result = history.associateBy { it.id }
            require(result.size == history.size && result.size <= LocalConversationStore.MAX_CHATS)
            result.keys.forEach(::requireCanonicalChatId)
            return result
        }

        private fun allocate(reserved: MutableSet<String>, newId: () -> String, checkpoint: () -> Unit): String {
            repeat(64) {
                checkpoint()
                val candidate = newId()
                requireCanonicalChatId(candidate)
                if (reserved.add(candidate)) return candidate
            }
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.IDENTIFIER_UNAVAILABLE)
        }
    }
}
