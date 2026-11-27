package org.hermesmobile.app.local

import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.hermesmobile.app.data.HermesDatabase
import org.hermesmobile.app.data.LocalTask
import org.hermesmobile.app.data.NotebookItem
import org.hermesmobile.app.data.NotebookItemKind
import org.hermesmobile.app.data.NotebookSource
import org.hermesmobile.app.data.PhoneCapture
import org.hermesmobile.app.data.PhoneNotebook
import org.hermesmobile.app.data.validNotebookTitle

internal data class NotebookWorkspaceState(
    val open: Boolean = false,
    val notebookId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val pendingAnswer: PhoneDocumentAnswer? = null
)

internal class PhoneNotebookWorkspace(
    database: HermesDatabase,
    private val model: LocalAiController,
    private val scope: CoroutineScope
) {
    private val storage = database.phoneNotebooks()
    private val tasks = database.dao()
    private val captures = database.phoneCaptures()
    private val mutable = MutableStateFlow(NotebookWorkspaceState())
    val state = mutable.asStateFlow()
    val notebooks = storage.notebooks()

    fun open(answer: PhoneDocumentAnswer? = null) {
        if (!mutable.value.busy) mutable.value = NotebookWorkspaceState(open = true, pendingAnswer = answer)
    }

    fun close() {
        if (!mutable.value.busy) mutable.value = NotebookWorkspaceState()
    }

    fun select(id: String) = change {
        requireNotNull(storage.notebook(id))
        storage.openNotebook(id, System.currentTimeMillis())
        mutable.update { it.copy(open = true, notebookId = id) }
    }

    fun create(name: String) = change {
        val title = name.trim()
        check(validNotebookTitle(title))
        val now = System.currentTimeMillis()
        val notebook = PhoneNotebook(UUID.randomUUID().toString(), title, now, now)
        storage.create(notebook)
        mutable.update { it.copy(open = true, notebookId = notebook.id) }
    }

    fun entries(id: String, query: String): Flow<List<NotebookItem>> {
        val term = query.trim().lowercase(Locale.ROOT)
        if (term.isEmpty()) return storage.items(id)
        return combine(storage.items(id), captures.search(query), tasks.tasks()) { items, notes, localTasks ->
            val matchedNotes = notes.mapTo(mutableSetOf()) { it.id }
            val matchedTasks = localTasks.filter { hasTerm(it.title, term) || hasTerm(it.notes, term) }
                .mapTo(mutableSetOf()) { it.id }
            items.filter { item ->
                hasTerm(item.title, term) || when (NotebookItemKind.valueOf(item.kind)) {
                    NotebookItemKind.CAPTURE -> item.sourceId in matchedNotes
                    NotebookItemKind.TASK -> item.sourceId in matchedTasks
                    NotebookItemKind.ANSWER -> hasTerm(item.answer, term)
                    NotebookItemKind.DOCUMENT -> matchingDocument(item, term)
                }
            }
        }
    }

    fun sources(notebookId: String, itemId: String) = storage.sources(notebookId, itemId)

    suspend fun capture(item: NotebookItem): PhoneCapture? =
        captures.observe(checkedSource(item, NotebookItemKind.CAPTURE)).first()

    suspend fun task(item: NotebookItem): LocalTask? =
        tasks.tasks().first().singleOrNull { it.id == checkedSource(item, NotebookItemKind.TASK) }

    suspend fun document(item: NotebookItem): PhoneDocumentText {
        val id = checkedSource(item, NotebookItemKind.DOCUMENT)
        return model.readNotebookDocument(id, item.fingerprint)
    }

    suspend fun source(notebookId: String, source: NotebookSource): PhoneDocumentText {
        val answer = requireNotNull(storage.item(notebookId, source.itemId))
        check(answer.kind == NotebookItemKind.ANSWER.name)
        check(source in storage.sources(notebookId, source.itemId).first())
        return model.readNotebookDocument(source.documentId, source.fingerprint)
    }

    fun availableCaptures(query: String) = captures.search(query)
    fun availableTasks() = tasks.tasks()

    fun attachCapture(notebookId: String, captureId: String) = change {
        val capture = requireNotNull(captures.observe(captureId).first())
        val title = capture.content.lineSequence().map(String::trim)
            .first { it.isNotEmpty() }.replace('\t', ' ').take(100)
        attach(notebookId, NotebookItemKind.CAPTURE, capture.id, title)
    }

    fun attachTask(notebookId: String, taskId: String) = change {
        val task = tasks.tasks().first().single { it.id == taskId }
        attach(notebookId, NotebookItemKind.TASK, task.id, task.title.trim().take(100))
    }

    fun attachDocument(notebookId: String, selected: PhoneDocument) = change {
        val checked = model.readNotebookDocument(selected.id, selected.fingerprint).info
        attach(notebookId, NotebookItemKind.DOCUMENT, checked.id, checked.name.trim().take(100), checked.fingerprint)
    }

    fun saveAnswer(notebookId: String, answer: PhoneDocumentAnswer) = change {
        check(answer.state == LocalReplyState.COMPLETE)
        val body = requireNotNull(answer.text)
        checkNotNull(PhoneDocumentQuestions.checkedReply(body, answer.review.sources.size))
        val id = UUID.randomUUID().toString()
        val refs = answer.review.sources.mapIndexed { index, source ->
            NotebookSource(id, index, source.document.id, source.document.fingerprint, source.document.name,
                source.page, source.firstLine, source.lastLine, source.text)
        }
        val contentId = withContext(Dispatchers.Default) {
            val digest = MessageDigest.getInstance("SHA-256")
            val fields = listOf(answer.review.question, body) + refs.flatMap {
                listOf(it.documentId, it.documentName, it.fingerprint, it.page.toString(), it.firstLine.toString(),
                    it.lastLine.toString(), it.excerpt)
            }
            fields.forEach {
                val bytes = it.toByteArray(Charsets.UTF_8)
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val title = answer.review.question.lineSequence().map(String::trim)
            .first { it.isNotEmpty() }.replace('\t', ' ').take(100)
        storage.attach(NotebookItem(id, notebookId, NotebookItemKind.ANSWER.name, contentId, "", title, body,
            System.currentTimeMillis()), refs)
        storage.openNotebook(notebookId, System.currentTimeMillis())
        mutable.update { it.copy(open = true, notebookId = notebookId, pendingAnswer = null) }
    }

    fun detach(id: String, entry: NotebookItem) = change {
        check(entry.notebookId == id)
        storage.detach(id, entry.id)
    }

    private suspend fun attach(notebookId: String, kind: NotebookItemKind, sourceId: String, title: String,
        fingerprint: String = "") {
        storage.attach(NotebookItem(UUID.randomUUID().toString(), notebookId, kind.name, sourceId, fingerprint,
            title, "", System.currentTimeMillis()), emptyList())
    }

    private suspend fun checkedSource(item: NotebookItem, kind: NotebookItemKind): String {
        check(item.kind == kind.name)
        check(storage.item(item.notebookId, item.id) == item)
        return item.sourceId
    }

    private fun change(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutable.update { it.copy(error = "notebook_operation_failed") }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun matchingDocument(item: NotebookItem, term: String): Boolean = try {
        model.readNotebookDocument(item.sourceId, item.fingerprint).sections.any { hasTerm(it, term) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: LocalModelStorageException) {
        false
    }

    private fun hasTerm(text: String, term: String) = text.lowercase(Locale.ROOT).contains(term)
}
