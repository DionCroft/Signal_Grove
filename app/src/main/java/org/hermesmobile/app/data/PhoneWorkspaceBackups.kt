package org.hermesmobile.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import java.util.UUID
import org.hermesmobile.app.local.LocalHistoryBackupException
import org.hermesmobile.app.local.LocalHistoryBackupProblem
import org.hermesmobile.app.local.PhoneWorkspaceBackup

internal enum class WorkspaceBackupKind { CAPTURE, TASK, NOTEBOOK }
internal data class WorkspaceBackupKey(val kind: WorkspaceBackupKind, val id: String)
internal data class WorkspaceBackupRow(val key: WorkspaceBackupKey, val title: String)
internal data class WorkspaceBackupText(val id: String, val title: String)

internal data class WorkspaceBackupCapture(
    val id: String,
    val content: String,
    val source: String,
    val createdAt: Long,
    val processedAt: Long?,
    val archivedAt: Long?
) {
    constructor(capture: PhoneCapture) : this(capture.id, capture.content, capture.source,
        capture.createdAt, capture.processedAt, capture.archivedAt)

    fun toCapture(): PhoneCapture = PhoneCapture(id, content, source, createdAt, processedAt, archivedAt)
}

internal data class WorkspaceBackupEntry(val item: NotebookItem, val excerpts: List<NotebookSource>)

internal data class WorkspaceBackupNotebook(val notebook: PhoneNotebook, val entries: List<WorkspaceBackupEntry>)

internal data class WorkspaceBackupArchive(
    val version: Int,
    val captures: List<WorkspaceBackupCapture>,
    val tasks: List<LocalTask>,
    val taskLinks: List<PhoneCaptureTaskLink>,
    val notebooks: List<WorkspaceBackupNotebook>
) {
    val count: Int get() = captures.size + tasks.size + notebooks.size

    fun rows(): List<WorkspaceBackupRow> = buildList {
        captures.forEach { add(WorkspaceBackupRow(WorkspaceBackupKey(WorkspaceBackupKind.CAPTURE, it.id), it.content.take(180))) }
        tasks.forEach { add(WorkspaceBackupRow(WorkspaceBackupKey(WorkspaceBackupKind.TASK, it.id), it.title)) }
        notebooks.forEach { add(WorkspaceBackupRow(WorkspaceBackupKey(WorkspaceBackupKind.NOTEBOOK, it.notebook.id), it.notebook.name)) }
    }

    fun selected(chosen: Set<WorkspaceBackupKey>): WorkspaceBackupArchive {
        val captureIds = chosen.ids(WorkspaceBackupKind.CAPTURE)
        val taskIds = chosen.ids(WorkspaceBackupKind.TASK)
        val notebookIds = chosen.ids(WorkspaceBackupKind.NOTEBOOK)
        val subset = copy(
            captures = captures.filter { it.id in captureIds },
            tasks = tasks.filter { it.id in taskIds },
            taskLinks = taskLinks.filter { it.captureId in captureIds && it.taskId in taskIds },
            notebooks = notebooks.filter { it.notebook.id in notebookIds }
        )
        require(subset.count > 0 && subset.count == chosen.size)
        return subset
    }
}

internal data class WorkspaceRestorePlan(
    val rows: List<WorkspaceBackupRow>,
    val copied: Set<WorkspaceBackupKey>,
    internal val restored: WorkspaceBackupArchive
)

@Dao
internal abstract class PhoneWorkspaceBackupDao {
    @Query("SELECT id, substr(content, 1, 180) AS title FROM phone_captures ORDER BY createdAt DESC, id")
    protected abstract suspend fun catalogCaptures(): List<WorkspaceBackupText>

    @Query("SELECT id, title FROM tasks ORDER BY completed, priority DESC, dueDate, title, id")
    protected abstract suspend fun catalogTasks(): List<WorkspaceBackupText>

    @Query("SELECT id, name AS title FROM phone_notebooks ORDER BY openedAt DESC, id")
    protected abstract suspend fun catalogNotebooks(): List<WorkspaceBackupText>

    @Query("SELECT * FROM phone_captures WHERE id IN (:ids) ORDER BY createdAt DESC, id")
    protected abstract suspend fun captures(ids: List<String>): List<PhoneCapture>

    @Query("SELECT * FROM tasks WHERE id IN (:ids) ORDER BY id")
    protected abstract suspend fun tasks(ids: List<String>): List<LocalTask>

    @Query("SELECT * FROM phone_capture_tasks WHERE captureId IN (:ids) ORDER BY taskId")
    protected abstract suspend fun linkedTasks(ids: List<String>): List<PhoneCaptureTaskLink>

    @Query("SELECT * FROM phone_notebooks WHERE id IN (:ids) ORDER BY openedAt DESC, id")
    protected abstract suspend fun notebooks(ids: List<String>): List<PhoneNotebook>

    @Query("SELECT * FROM phone_notebook_items WHERE notebookId = :id ORDER BY addedAt DESC, id")
    protected abstract suspend fun entries(id: String): List<NotebookItem>

    @Query("""SELECT source.* FROM phone_notebook_sources source
        INNER JOIN phone_notebook_items item ON item.id = source.itemId
        WHERE item.notebookId = :id ORDER BY source.itemId, source.ordinal""")
    protected abstract suspend fun excerpts(id: String): List<NotebookSource>

    @Query("SELECT id FROM phone_captures")
    protected abstract suspend fun captureIds(): List<String>

    @Query("SELECT id FROM tasks")
    protected abstract suspend fun taskIds(): List<String>

    @Query("SELECT id FROM phone_notebooks")
    protected abstract suspend fun notebookIds(): List<String>

    @Query("SELECT id FROM phone_notebook_items")
    protected abstract suspend fun entryIds(): List<String>

    @Query("SELECT COUNT(*) FROM phone_notebooks")
    protected abstract suspend fun notebookCount(): Int

    @Insert protected abstract suspend fun insertCaptures(rows: List<PhoneCapture>)
    @Insert protected abstract suspend fun insertTasks(rows: List<LocalTask>)
    @Insert protected abstract suspend fun insertLinks(rows: List<PhoneCaptureTaskLink>)
    @Insert protected abstract suspend fun insertNotebooks(rows: List<PhoneNotebook>)
    @Insert protected abstract suspend fun insertEntries(rows: List<NotebookItem>)
    @Insert protected abstract suspend fun insertExcerpts(rows: List<NotebookSource>)

    @Transaction
    open suspend fun catalog(): List<WorkspaceBackupRow> = buildList {
        catalogCaptures().forEach { add(WorkspaceBackupRow(WorkspaceBackupKey(WorkspaceBackupKind.CAPTURE, it.id), it.title)) }
        catalogTasks().forEach { add(WorkspaceBackupRow(WorkspaceBackupKey(WorkspaceBackupKind.TASK, it.id), it.title)) }
        catalogNotebooks().forEach { add(WorkspaceBackupRow(WorkspaceBackupKey(WorkspaceBackupKind.NOTEBOOK, it.id), it.title)) }
    }

    /** All chosen values and links come from one local Room snapshot. */
    @Transaction
    open suspend fun snapshot(chosen: Set<WorkspaceBackupKey>): WorkspaceBackupArchive {
        require(chosen.isNotEmpty())
        val selectedNotes = chosen.ids(WorkspaceBackupKind.CAPTURE)
        val selectedTasks = chosen.ids(WorkspaceBackupKind.TASK)
        val selectedBooks = chosen.ids(WorkspaceBackupKind.NOTEBOOK)
        val books = selectedBooks.chunked(200).flatMap { ids ->
            notebooks(ids).map { notebook ->
                val sources = excerpts(notebook.id).groupBy(NotebookSource::itemId)
                val attached = entries(notebook.id).map { entry -> WorkspaceBackupEntry(entry, sources[entry.id].orEmpty()) }
                WorkspaceBackupNotebook(notebook, attached)
            }
        }
        val attached = books.flatMap { it.entries }.map { it.item }
        val notes = (selectedNotes + attached.sourceIds(NotebookItemKind.CAPTURE)).chunked(200).flatMap { captures(it) }
        val links = notes.map { it.id }.chunked(200).flatMap { linkedTasks(it) }
        val tasks = (selectedTasks + attached.sourceIds(NotebookItemKind.TASK) + links.map { it.taskId })
            .chunked(200).flatMap { tasks(it) }
        if (books.size != selectedBooks.size || !notes.map { it.id }.containsAll(selectedNotes) ||
            !tasks.map { it.id }.containsAll(selectedTasks)) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
        }
        return WorkspaceBackupArchive(1, notes.map(::WorkspaceBackupCapture), tasks, links, books)
            .also(PhoneWorkspaceBackup::requireValid)
    }

    @Transaction
    open suspend fun review(archive: WorkspaceBackupArchive): WorkspaceRestorePlan {
        PhoneWorkspaceBackup.requireValid(archive)
        if (notebookCount() + archive.notebooks.size > 100) capacity()

        val rows = archive.rows()
        val archivedEntries = archive.notebooks.flatMap { it.entries }.map { it.item }
        val captures = RestoreIds(captureIds(), archive.captures.map { it.id },
            archivedEntries.sourceIds(NotebookItemKind.CAPTURE))
        val tasks = RestoreIds(taskIds(), archive.tasks.map { it.id },
            archivedEntries.sourceIds(NotebookItemKind.TASK))
        val notebooks = RestoreIds(notebookIds(), archive.notebooks.map { it.notebook.id })
        val entries = RestoreIds(entryIds(), archivedEntries.map { it.id })
        val copied = rows.filter { row ->
            row.key.id != when (row.key.kind) {
                WorkspaceBackupKind.CAPTURE -> captures[row.key.id]
                WorkspaceBackupKind.TASK -> tasks[row.key.id]
                WorkspaceBackupKind.NOTEBOOK -> notebooks[row.key.id]
            }
        }.mapTo(mutableSetOf()) { it.key }
        val remappedBooks = archive.notebooks.map { book ->
            val id = notebooks[book.notebook.id]
            WorkspaceBackupNotebook(book.notebook.copy(id = id), book.entries.map { entry ->
                val item = entry.item
                val source = when (NotebookItemKind.valueOf(item.kind)) {
                    NotebookItemKind.CAPTURE -> captures[item.sourceId]
                    NotebookItemKind.TASK -> tasks[item.sourceId]
                    else -> item.sourceId
                }
                val entryId = entries[item.id]
                WorkspaceBackupEntry(
                    item.copy(id = entryId, notebookId = id, sourceId = source),
                    entry.excerpts.map { it.copy(itemId = entryId) }
                )
            })
        }
        val restored = archive.copy(
            captures = archive.captures.map { it.copy(id = captures[it.id]) },
            tasks = archive.tasks.map { it.copy(id = tasks[it.id]) },
            taskLinks = archive.taskLinks.map { PhoneCaptureTaskLink(tasks[it.taskId], captures[it.captureId]) },
            notebooks = remappedBooks
        )
        return WorkspaceRestorePlan(rows, copied, restored)
    }

    /** Uses only the IDs chosen during review. A late conflict rolls back every insertion. */
    @Transaction
    open suspend fun restore(plan: WorkspaceRestorePlan): Int {
        val archive = plan.restored
        PhoneWorkspaceBackup.requireValid(archive)
        if (notebookCount() + archive.notebooks.size > 100) capacity()
        val allEntries = archive.notebooks.flatMap { it.entries }
        val notebookItems = allEntries.map { it.item }
        val reservedCaptureIds = archive.captures.mapTo(mutableSetOf()) { it.id } + notebookItems.sourceIds(NotebookItemKind.CAPTURE)
        val reservedTaskIds = archive.tasks.mapTo(mutableSetOf()) { it.id } + notebookItems.sourceIds(NotebookItemKind.TASK)
        val reservedNotebookIds = archive.notebooks.mapTo(mutableSetOf()) { it.notebook.id }
        val reservedEntryIds = notebookItems.mapTo(mutableSetOf()) { it.id }
        if (captureIds().any { it in reservedCaptureIds } || taskIds().any { it in reservedTaskIds } ||
            notebookIds().any { it in reservedNotebookIds } || entryIds().any { it in reservedEntryIds }) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
        }
        insertCaptures(archive.captures.map { it.toCapture() })
        insertTasks(archive.tasks)
        insertLinks(archive.taskLinks)
        insertNotebooks(archive.notebooks.map { it.notebook })
        insertEntries(allEntries.map { it.item })
        insertExcerpts(allEntries.flatMap { it.excerpts })
        return archive.count
    }

    private fun capacity(): Nothing = throw LocalHistoryBackupException(LocalHistoryBackupProblem.CAPACITY)
}

private fun Set<WorkspaceBackupKey>.ids(kind: WorkspaceBackupKind): Set<String> =
    filter { it.kind == kind }.mapTo(linkedSetOf()) { it.id }

private fun List<NotebookItem>.sourceIds(kind: NotebookItemKind): Set<String> =
    filter { it.kind == kind.name }.mapTo(linkedSetOf()) { it.sourceId }

/** Missing sources get reserved IDs so a saved title can never bind to an unrelated local row. */
private class RestoreIds(existing: List<String>, imported: List<String>, referenced: Set<String> = emptySet()) {
    private val claimed = (existing + imported + referenced).toMutableSet()
    private val occupied = existing.toSet()
    private val importedIds = imported.toSet()
    private val mapping = (importedIds + referenced).associateWith { id ->
        if (id in importedIds && id !in occupied) id
        else generateSequence { UUID.randomUUID().toString() }.take(16).firstOrNull { claimed.add(it) }
            ?: throw LocalHistoryBackupException(LocalHistoryBackupProblem.IDENTIFIER_UNAVAILABLE)
    }

    operator fun get(id: String): String = mapping.getValue(id)
}
