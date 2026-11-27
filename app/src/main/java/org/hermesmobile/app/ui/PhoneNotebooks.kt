package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.hermesmobile.app.data.*
import org.hermesmobile.app.local.*

@Composable
internal fun PhoneNotebookScreen(workspace: PhoneNotebookWorkspace, model: LocalAiState, state: NotebookWorkspaceState,
                                 handoff: (NotebookHandoffDraft) -> Unit) {
    val notebooks by workspace.notebooks.collectAsStateWithLifecycle(initialValue = emptyList())
    val selected = notebooks.firstOrNull { it.id == state.notebookId }
    val answer = state.pendingAnswer
    val rename = state.rename
    val deletion = state.deleteReview
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Dialog(workspace::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (selected != null && answer == null) Text(selected.name, style = MaterialTheme.typography.titleLarge)
                        else Word(Words.PhoneNotebooks)
                    }
                    IconButton(workspace::close, enabled = !state.busy, modifier = Modifier.testTag("notebook-close")) {
                        Icon(Icons.Outlined.Close, label(Words.Close))
                    }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.error != null) Text(label(Words.NotebookFailed), color = MaterialTheme.colorScheme.error)
                when {
                    rename != null -> NotebookRenameForm(workspace, rename, state.busy)
                    deletion != null -> NotebookDeleteReview(workspace, deletion, state.busy)
                    selected != null && answer == null ->
                        NotebookItems(Modifier.weight(1f), workspace, model, selected, state.busy, handoff)
                    else -> LazyColumn(Modifier.fillMaxSize().testTag("notebook-index"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (answer != null) item {
                            Word(Words.NotebookSaveAnswer)
                            Text(answer.review.question)
                            SelectionContainer { Text(answer.text.orEmpty()) }
                            Text(label(Words.NotebookChoose))
                        }
                        if (creating) item {
                            Word(Words.NotebookName)
                            OutlinedTextField(name, { if (it.length <= 160) name = it }, Modifier.fillMaxWidth().testTag("notebook-name"), singleLine = true)
                            Button({
                                focus.clearFocus()
                                workspace.create(name)
                                creating = false
                            }, Modifier.testTag("notebook-create-confirm"), enabled = !state.busy && validNotebookTitle(name.trim())) {
                                Text(label(Words.NotebookCreate))
                            }
                        } else item {
                            TextButton({ creating = true; name = "" }, Modifier.testTag("notebook-create"), enabled = !state.busy) {
                                Text(label(Words.NotebookCreate))
                            }
                        }
                        items(notebooks, key = PhoneNotebook::id) { notebook ->
                            TextButton({
                                if (answer == null) workspace.select(notebook.id)
                                else workspace.saveAnswer(notebook.id, answer)
                            }, Modifier.testTag("notebook-open-" + notebook.id), enabled = !state.busy) { Text(notebook.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NotebookRenameForm(workspace: PhoneNotebookWorkspace, review: NotebookRename, busy: Boolean) {
    val focus = LocalFocusManager.current
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Word(Words.NotebookRenameAction)
        OutlinedTextField(review.name, workspace::editName, Modifier.fillMaxWidth().testTag("notebook-rename-field"),
            enabled = !busy, singleLine = true, label = { Text(label(Words.NotebookName)) })
    }
    Button({ focus.clearFocus(); workspace.confirmRename() }, Modifier.fillMaxWidth().testTag("notebook-rename-confirm"),
        enabled = !busy && validNotebookTitle(review.name.trim())) { Text(label(Words.Save)) }
    TextButton({ focus.clearFocus(); workspace.cancelManagement() }, Modifier.fillMaxWidth().testTag("notebook-manage-cancel"),
        enabled = !busy) { Text(label(Words.Cancel)) }
}

@Composable
private fun ColumnScope.NotebookDeleteReview(workspace: PhoneNotebookWorkspace, notebook: PhoneNotebook, busy: Boolean) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Word(Words.NotebookDeleteAction)
        Text(label(Words.NotebookDeleteImpact), Modifier.testTag("notebook-delete-impact"))
        Text(notebook.name, Modifier.testTag("notebook-delete-name"))
    }
    Button(workspace::confirmDeletion, Modifier.fillMaxWidth().testTag("notebook-delete-confirm"), enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError)) { Text(label(Words.NotebookDeleteAction)) }
    TextButton(workspace::cancelManagement, Modifier.fillMaxWidth().testTag("notebook-manage-cancel"),
        enabled = !busy) { Text(label(Words.Cancel)) }
}

@Composable
private fun NotebookItems(modifier: Modifier, workspace: PhoneNotebookWorkspace, model: LocalAiState,
                          notebook: PhoneNotebook, busy: Boolean, handoff: (NotebookHandoffDraft) -> Unit) {
    var query by rememberSaveable(notebook.id) { mutableStateOf("") }
    var picker by remember(notebook.id) { mutableStateOf<NotebookItemKind?>(null) }
    var entry by remember(notebook.id) { mutableStateOf<NotebookItem?>(null) }
    val matches = notebookRead(notebook.id to query) { workspace.entries(notebook.id, query) }
    val focus = LocalFocusManager.current
    val current = entry
    val handoffs = notebookRead(notebook.id) { workspace.handoffs(notebook.id) }?.getOrNull().orEmpty()
    val kind = picker
    Column(modifier.fillMaxWidth()) {
        TextButton({
            when {
                current != null -> entry = null
                kind != null -> picker = null
                else -> workspace.open()
            }
        }, enabled = !busy, modifier = Modifier.testTag("notebook-back")) {
            Text(label(if (current == null && kind == null) Words.PhoneNotebooks else Words.NotebookItems))
        }
        when {
            current != null -> NotebookEntry(Modifier.weight(1f), workspace, notebook, current, busy) {
                workspace.prepareHandoff(notebook.id, current, handoff)
            }
            kind != null -> NotebookPicker(Modifier.weight(1f), workspace, model, notebook.id, kind, busy) { picker = null }
            else -> LazyColumn(Modifier.fillMaxSize().testTag("notebook-entries"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(label(Words.NotebookSearch), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(query, { if (it.length <= 160) query = it }, Modifier.fillMaxWidth().testTag("notebook-search"),
                        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
                }
                items(listOf(NotebookItemKind.CAPTURE, NotebookItemKind.DOCUMENT, NotebookItemKind.TASK)) { type ->
                    TextButton({ focus.clearFocus(); picker = type }, Modifier.testTag("notebook-add-" + type.name),
                        enabled = !busy) { Text(label(notebookAddLabel(type))) }
                }
                when {
                    matches == null -> item { QuietProgress() }
                    matches.isFailure -> item { Text(label(Words.NotebookFailed)) }
                    matches.getOrThrow().isEmpty() -> item { Text(label(Words.NotebookEmpty)) }
                    else -> items(matches.getOrThrow(), key = NotebookItem::id) { row ->
                        HermesCard {
                            Text(label(notebookKindLabel(NotebookItemKind.valueOf(row.kind))), style = MaterialTheme.typography.labelMedium)
                            TextButton({ focus.clearFocus(); entry = row },
                                Modifier.testTag("notebook-item-" + row.id), enabled = !busy) { Text(row.title) }
                        }
                    }
                }
                item {
                    TextButton({ focus.clearFocus(); workspace.reviewRename(notebook) },
                        modifier = Modifier.testTag("notebook-rename"), enabled = !busy) {
                        Text(label(Words.NotebookRenameAction))
                    }
                    TextButton({ focus.clearFocus(); workspace.reviewDeletion(notebook) },
                        modifier = Modifier.testTag("notebook-delete"), enabled = !busy) {
                        Text(label(Words.NotebookDeleteAction),
                            color = if (busy) LocalContentColor.current else MaterialTheme.colorScheme.error)
                    }
                }
                if (query.isBlank() && handoffs.isNotEmpty()) {
                    item { Word(Words.NotebookHandoffResults) }
                    items(handoffs, key = { "handoff/" + it.requestKey }) { result ->
                        NotebookResultCard(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookPicker(modifier: Modifier, workspace: PhoneNotebookWorkspace, model: LocalAiState,
                           notebookId: String, kind: NotebookItemKind, busy: Boolean, picked: () -> Unit) {
    var query by rememberSaveable(kind) { mutableStateOf("") }
    val notes = if (kind == NotebookItemKind.CAPTURE) notebookRead(query) { workspace.availableCaptures(query) } else null
    val tasks = if (kind == NotebookItemKind.TASK) notebookRead(kind) { workspace.availableTasks() } else null
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(label(Words.Search))
            OutlinedTextField(query, { if (it.length <= 160) query = it }, Modifier.fillMaxWidth(), singleLine = true)
        }
        when (kind) {
            NotebookItemKind.CAPTURE -> items(notes?.getOrNull().orEmpty(), key = PhoneCapturePreview::id) { note ->
                TextButton({ workspace.attachCapture(notebookId, note.id); picked() }, enabled = !busy,
                    modifier = Modifier.testTag("notebook-pick-" + note.id)) { Text(note.preview) }
            }
            NotebookItemKind.DOCUMENT -> items(model.documents.filter { it.name.contains(query.trim(), ignoreCase = true) },
                key = PhoneDocument::id) { document ->
                TextButton({ workspace.attachDocument(notebookId, document); picked() }, enabled = !busy,
                    modifier = Modifier.testTag("notebook-pick-" + document.id)) { Text(document.name) }
            }
            NotebookItemKind.TASK -> items(tasks?.getOrNull().orEmpty().filter { it.title.contains(query.trim(), ignoreCase = true) },
                key = LocalTask::id) { task ->
                TextButton({ workspace.attachTask(notebookId, task.id); picked() }, enabled = !busy,
                    modifier = Modifier.testTag("notebook-pick-" + task.id)) { Text(task.title) }
            }
            NotebookItemKind.ANSWER -> Unit
        }
    }
}

@Composable
private fun NotebookEntry(modifier: Modifier, workspace: PhoneNotebookWorkspace, notebook: PhoneNotebook, entry: NotebookItem,
                          busy: Boolean, reviewHandoff: () -> Unit) {
    var source by remember(entry.id) { mutableStateOf<NotebookSource?>(null) }
    val citation = source
    val citations = if (entry.kind == NotebookItemKind.ANSWER.name) notebookRead(entry.id) {
        workspace.sources(notebook.id, entry.id)
    } else null
    var contents by remember(entry, citation) { mutableStateOf<Result<String>?>(null) }
    LaunchedEffect(entry, citation) {
        contents = try {
            val text = if (citation != null) {
                val document = workspace.source(notebook.id, citation)
                document.sections[(citation.page ?: 1) - 1].take(12_000)
            } else when (NotebookItemKind.valueOf(entry.kind)) {
                NotebookItemKind.CAPTURE -> requireNotNull(workspace.capture(entry)).content
                NotebookItemKind.TASK -> requireNotNull(workspace.task(entry)).let { task ->
                    listOf(task.title, task.dueDate, task.notes).filter(String::isNotBlank).joinToString("\n")
                }
                NotebookItemKind.DOCUMENT -> workspace.document(entry).sections.joinToString("\n\n").take(12_000)
                NotebookItemKind.ANSWER -> entry.answer
            }
            Result.success(text)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(error) }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(citation?.documentName ?: entry.title, style = MaterialTheme.typography.titleMedium)
        if (citation != null) {
            TextButton({ source = null }) { Text(label(Words.NotebookAnswer)) }
            SelectionContainer { Text(citation.excerpt) }
        }
        val result = contents
        when {
            result == null -> QuietProgress()
            result.isFailure -> Text(label(Words.NotebookSourceUnavailable))
            else -> SelectionContainer { Text(result.getOrThrow(), Modifier.testTag("notebook-source-content")) }
        }
        if (citation == null && result?.isSuccess == true) {
            TextButton(reviewHandoff, enabled = !busy, modifier = Modifier.testTag("notebook-handoff-open")) {
                Text(label(Words.NotebookHandoff))
            }
        }
        if (citation == null) citations?.getOrNull().orEmpty().forEach { reference ->
            HermesCard {
                val location = reference.page?.let { label(Words.PhoneDocumentPage).replace("{page}", it.toString()) }
                    ?: label(Words.PhoneDocumentLines).replace("{first}", reference.firstLine.toString())
                        .replace("{last}", reference.lastLine.toString())
                Text("[S" + (reference.ordinal + 1) + "] " + reference.documentName + " · " + location)
                SelectionContainer { Text(reference.excerpt) }
                TextButton({ source = reference }, Modifier.testTag("notebook-source-" + reference.ordinal)) {
                    Text(label(Words.NotebookOpenSource))
                }
            }
        }
    }
}

@Composable
private fun NotebookResultCard(result: NotebookHandoffResult) {
    var expanded by remember(result.requestKey) { mutableStateOf(false) }
    HermesCard {
        TextButton({ expanded = !expanded }, Modifier.testTag("notebook-result-" + result.requestKey)) {
            Text(result.sessionTitle ?: label(Words.NotebookHandoff))
        }
        Text(result.connectionName, style = MaterialTheme.typography.labelMedium)
        Text(result.endpoint, style = MaterialTheme.typography.bodySmall)
        Text(label(result.replyStatus?.let(::statusWord) ?: Words.NotebookSourceUnavailable))
        if (expanded) {
            val reply = result.replyContent
            if (!reply.isNullOrBlank()) SelectionContainer { Text(reply, Modifier.testTag("notebook-result-content")) }
        }
    }
}

@Composable
private fun <T> notebookRead(key: Any, source: () -> Flow<T>): Result<T>? = key(key) {
    val flow = remember { source().map { Result.success(it) }.catch { emit(Result.failure(it)) } }
    val result by flow.collectAsStateWithLifecycle<Result<T>?>(initialValue = null)
    result
}

private fun notebookKindLabel(kind: NotebookItemKind) = when (kind) {
    NotebookItemKind.CAPTURE -> Words.CaptureInbox
    NotebookItemKind.DOCUMENT -> Words.PhoneDocuments
    NotebookItemKind.TASK -> Words.Tasks
    NotebookItemKind.ANSWER -> Words.NotebookAnswer
}

private fun notebookAddLabel(kind: NotebookItemKind) = when (kind) {
    NotebookItemKind.CAPTURE -> Words.NotebookAttachCapture
    NotebookItemKind.DOCUMENT -> Words.NotebookAttachDocument
    NotebookItemKind.TASK -> Words.NotebookAttachTask
    NotebookItemKind.ANSWER -> Words.NotebookSaveAnswer
}
