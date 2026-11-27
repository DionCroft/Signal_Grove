package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.hermesmobile.app.data.*
import org.hermesmobile.app.local.*

@Composable
internal fun PhoneCaptureInbox(vm: LocalAiViewModel, model: LocalAiState, state: CaptureInboxState) {
    val workspace = vm.captures
    val focus = LocalFocusManager.current
    val draft = state.draft
    val review = state.review
    val deleteReview = state.deleteReview
    val id = state.selectedId
    val generating = model.busy && model.status == "generating_capture_tasks"
    Dialog(workspace::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Word(Words.CaptureInbox) }
                    IconButton(workspace::close, Modifier.testTag("capture-close"), enabled = !state.saving) {
                        Icon(Icons.Outlined.Close, label(Words.Close))
                    }
                }
                state.notice?.let { CaptureStatus(it) }
                if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                when {
                    deleteReview != null -> CaptureDeleteReview(deleteReview, state.saving, workspace)
                    draft != null -> {
                        val valid = validCaptureText(draft.content)
                        if (draft.original != null) Word(Words.CaptureEdit)
                        OutlinedTextField(draft.content, workspace::editNote,
                            Modifier.weight(1f, fill = false).fillMaxWidth().testTag("capture-note"),
                            label = { Text(label(Words.Notes)) }, minLines = 3, maxLines = 10,
                            enabled = !state.saving,
                            supportingText = if (draft.content.isNotEmpty() && !valid) {
                                { Text(label(Words.CaptureNoteLimit)) }
                            } else null)
                        Button({ focus.clearFocus(); workspace.saveNote() },
                            Modifier.fillMaxWidth().testTag("capture-save-note"),
                            enabled = !state.saving && valid) { Text(label(Words.Save)) }
                        if (draft.original != null) {
                            TextButton({ focus.clearFocus(); workspace.discardEdit() },
                                Modifier.fillMaxWidth().testTag("capture-cancel-edit"),
                                enabled = !state.saving) { Text(label(Words.Cancel)) }
                        }
                    }
                    review != null -> CaptureTaskReviewScreen(workspace, state, review)
                    id != null -> {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("capture-detail"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CaptureDetail(vm, id, model, generating, state.saving)
                        }
                        TextButton({ workspace.showCaptures() }, enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth().testTag("capture-back-to-inbox")) {
                            Text(label(Words.CaptureBack))
                        }
                    }
                    else -> CaptureSearch(Modifier.weight(1f), workspace, state.showArchived, vm.mediaCapture::open)
                }
            }
        }
    }
}

@Composable
private fun CaptureSearch(
    modifier: Modifier, workspace: PhoneCaptureWorkspace, archived: Boolean, openMedia: (MediaCaptureKind) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchLabel = label(Words.CaptureSearch)
    val focus = LocalFocusManager.current
    val results = captureRead("$archived:$query") { workspace.search(query, archived) }
    val rows = results?.getOrNull()
    LazyColumn(modifier.fillMaxWidth().testTag("capture-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(searchLabel, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(query, { if (it.length <= 128) query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel }.testTag("capture-search"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
            }
        }
        item {
            TextButton({
                focus.clearFocus()
                workspace.showCaptures(!archived)
            }, Modifier.testTag("capture-toggle-archive")) {
                Text(label(if (archived) Words.CaptureBack else Words.CaptureArchivedList))
            }
        }
        if (!archived) item {
            TextButton({
                focus.clearFocus()
                workspace.newNote()
            }, Modifier.testTag("capture-new-note")) { Text(label(Words.CaptureNewNote)) }
            TextButton({
                focus.clearFocus()
                openMedia(MediaCaptureKind.SCREENSHOT)
            }, Modifier.testTag("capture-screenshot")) { Text(label(Words.MediaScreenshot)) }
            TextButton({
                focus.clearFocus()
                openMedia(MediaCaptureKind.RECORDING)
            }, Modifier.testTag("capture-recording")) { Text(label(Words.MediaRecording)) }
        }
        when {
            results == null -> item { QuietProgress() }
            rows == null -> item { CaptureStatus(CaptureNotice.STORAGE_ERROR) }
            rows.isEmpty() -> item {
                Text(label(when {
                    query.isNotBlank() -> Words.CaptureNoMatches
                    archived -> Words.CaptureArchiveEmpty
                    else -> Words.CaptureEmpty
                }))
            }
            else -> items(rows, key = { it.id }) { row ->
                HermesCard {
                    Text(label(captureSourceWord(row.source)), style = MaterialTheme.typography.labelMedium)
                    TextButton({
                        focus.clearFocus()
                        workspace.viewCapture(row.id)
                    }, Modifier.testTag("capture-open-" + row.id)) {
                        Text(row.preview, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureDetail(vm: LocalAiViewModel, id: String, model: LocalAiState, generating: Boolean, saving: Boolean) {
    val workspace = vm.captures
    val result = captureRead(id) { workspace.observe(id) }
    val capture = result?.getOrNull()
    if (capture == null) {
        when {
            result == null -> QuietProgress()
            result.isFailure -> CaptureStatus(CaptureNotice.STORAGE_ERROR)
            else -> Text(label(Words.CaptureUnavailable))
        }
        return
    }
    Text(label(captureSourceWord(capture.source)), style = MaterialTheme.typography.labelMedium)
    SelectionContainer { Text(capture.content, Modifier.testTag("capture-content")) }
    HorizontalDivider()
    val archived = capture.archivedAt != null
    if (archived) {
        Text(label(Words.CaptureArchived))
    } else if (generating) {
        Text(label(Words.CapturePreparingTasks))
        TextButton(vm.controller::cancel, Modifier.testTag("capture-stop")) { Text(label(Words.Stop)) }
    } else {
        model.error?.let { Text(label(phoneError(it)), color = MaterialTheme.colorScheme.error) }
        if (model.loaded == null) {
            Text(label(Words.CaptureLoadModel))
            PhoneInlineModelPicker(model, !model.busy) { vm.controller.load(it) }
        }
        Button({ workspace.suggestTasks(capture) }, Modifier.testTag("capture-suggest-tasks"),
            enabled = model.loaded != null && !model.busy && !saving) { Text(label(Words.CaptureSuggestTasks)) }
    }
    val linked = captureRead(id) { workspace.linkedTasks(id) }
    if (linked?.isFailure == true) CaptureStatus(CaptureNotice.STORAGE_ERROR)
    val tasks = linked?.getOrNull().orEmpty()
    if (tasks.isNotEmpty()) {
        Word(Words.CaptureLinkedTasks)
        tasks.forEach { task -> Text(task.title, Modifier.testTag("capture-linked-task"), style = MaterialTheme.typography.bodyMedium) }
    }
    HorizontalDivider()
    val canManage = !generating && !saving
    TextButton({ workspace.editCapture(capture) }, Modifier.testTag("capture-edit"), enabled = canManage) {
        Text(label(Words.CaptureEdit))
    }
    TextButton({ workspace.setArchived(capture) }, Modifier.testTag("capture-archive-action"), enabled = canManage) {
        Text(label(if (archived) Words.CaptureRestore else Words.CaptureArchive))
    }
    TextButton({ workspace.reviewDeletion(capture) }, Modifier.testTag("capture-delete"), enabled = canManage) {
        Text(label(Words.CaptureDelete), color = if (canManage) MaterialTheme.colorScheme.error else LocalContentColor.current)
    }
}

@Composable
private fun ColumnScope.CaptureDeleteReview(capture: PhoneCapture, saving: Boolean, workspace: PhoneCaptureWorkspace) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Word(Words.CaptureDelete)
        Text(label(Words.CaptureDeleteImpact))
        Text(capture.content, Modifier.testTag("capture-delete-content"))
    }
    Button(workspace::confirmDeletion, Modifier.fillMaxWidth().testTag("capture-confirm-delete"),
        enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError)) { Text(label(Words.CaptureDelete)) }
    TextButton(workspace::cancelDeletion, Modifier.fillMaxWidth().testTag("capture-cancel-delete"),
        enabled = !saving) { Text(label(Words.Cancel)) }
}

@Composable
private fun ColumnScope.CaptureTaskReviewScreen(workspace: PhoneCaptureWorkspace, state: CaptureInboxState, review: CaptureTaskReview) {
    val focus = LocalFocusManager.current
    Text(label(Words.CaptureReviewTasks))
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("capture-review"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        review.choices.forEachIndexed { index, choice ->
            Column {
                Row(Modifier.fillMaxWidth().toggleable(choice.selected, enabled = !state.saving, role = Role.Checkbox,
                    onValueChange = { workspace.updateChoice(index, choice.copy(selected = it)) }).testTag("capture-task-select-$index"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(choice.selected, onCheckedChange = null, enabled = !state.saving)
                    Spacer(Modifier.width(12.dp))
                    Text(label(Words.CaptureIncludeTask))
                }
                OutlinedTextField(choice.title, { workspace.updateChoice(index, choice.copy(title = it)) },
                    Modifier.fillMaxWidth().testTag("capture-task-title-$index"), singleLine = true,
                    enabled = !state.saving, isError = choice.selected && !validCaptureTaskTitle(choice.title.trim()),
                    label = { Text(label(Words.TaskTitle)) })
            }
        }
    }
    val selected = review.choices.filter { it.selected }
    Button({ focus.clearFocus(); workspace.saveReview() }, Modifier.fillMaxWidth().testTag("capture-save-tasks"),
        enabled = !state.saving && selected.isNotEmpty() && selected.all { validCaptureTaskTitle(it.title.trim()) }) {
        Text(label(Words.CaptureSaveTasks))
    }
    TextButton(workspace::discardReview, Modifier.fillMaxWidth().testTag("capture-cancel-review"),
        enabled = !state.saving) { Text(label(Words.Cancel)) }
}

@Composable
private fun <T> captureRead(key: String, source: () -> Flow<T>): Result<T>? {
    return key(key) {
        val flow = remember { source().map { Result.success(it) }.catch { emit(Result.failure(it)) } }
        val result by flow.collectAsStateWithLifecycle<Result<T>?>(initialValue = null)
        result
    }
}

@Composable
private fun CaptureStatus(notice: CaptureNotice) {
    val word = when (notice) {
        CaptureNotice.SAVED -> Words.CaptureSaved
        CaptureNotice.RESTORED -> Words.CaptureRestored
        CaptureNotice.DELETED -> Words.CaptureDeleted
        CaptureNotice.TASKS_SAVED -> Words.CaptureTasksSaved
        CaptureNotice.NO_TASKS -> Words.CaptureNoTasks
        CaptureNotice.STORAGE_ERROR -> Words.CaptureStorageError
    }
    Text(label(word), style = MaterialTheme.typography.bodySmall,
        color = if (notice == CaptureNotice.STORAGE_ERROR) MaterialTheme.colorScheme.error else LocalContentColor.current)
}

private fun captureSourceWord(source: String) = when (source) {
    CaptureSource.SHARED_LINK.name -> Words.CaptureSharedLink
    CaptureSource.SHARED_TEXT.name -> Words.CaptureSharedText
    CaptureSource.WIDGET.name -> Words.CaptureWidgetNote
    CaptureSource.SCREENSHOT_TEXT.name -> Words.MediaScreenshotSource
    CaptureSource.VOICE_TRANSCRIPT.name -> Words.MediaVoiceSource
    else -> Words.Notes
}
