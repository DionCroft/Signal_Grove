package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.local.*

private val writingPresets = listOf(
    Words.PhoneWriteSummarize to Words.PhoneWriteSummarizePrompt,
    Words.PhoneWriteRewrite to Words.PhoneWriteRewritePrompt,
    Words.PhoneWriteTranslate to Words.PhoneWriteTranslatePrompt,
    Words.PhoneWriteBrainstorm to Words.PhoneWriteBrainstormPrompt,
    Words.PhoneWriteExplainCode to Words.PhoneWriteExplainCodePrompt
)

@Composable
internal fun PhoneWritingDialog(vm: LocalAiViewModel, state: LocalAiState) {
    val draft by vm.writing.draft.collectAsStateWithLifecycle()
    val review by vm.writing.review.collectAsStateWithLifecycle()
    val result by vm.writing.result.collectAsStateWithLifecycle()
    val library by vm.writingLibrary.collectAsStateWithLifecycle()
    val storageError by vm.writingStorageError.collectAsStateWithLifecycle()
    val saving by vm.writingSaving.collectAsStateWithLifecycle()
    val capture by vm.captures.state.collectAsStateWithLifecycle()
    var notesOpen by remember { mutableStateOf(false) }
    var openedNote by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val incoming = draft.incoming
    val reviewed = review
    val output = result
    val idle = !state.busy && !saving && !capture.saving
    LaunchedEffect(incoming, reviewed, output, notesOpen) { scroll.scrollTo(0) }

    Dialog(onDismissRequest = vm::closeWriting,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(Words.PhoneWriting), style = MaterialTheme.typography.titleLarge)
                state.error?.let {
                    Text(label(phoneError(it)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("phone-writing-error"))
                }
                if (storageError) Text(label(Words.PhoneWritingStorage), color = MaterialTheme.colorScheme.error)
                if (incoming != null && capture.notice == CaptureNotice.STORAGE_ERROR) {
                    Text(label(Words.CaptureStorageError), color = MaterialTheme.colorScheme.error)
                }
                if (draft.intakeError) {
                    Text(label(Words.PhoneWritingIntakeError), color = MaterialTheme.colorScheme.error)
                    if (incoming == null) TextButton(vm.writing::dismissIncoming) { Text(label(Words.Close)) }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).testTag("phone-writing-body"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        incoming != null -> {
                            Text(label(Words.PhoneWritingIncoming))
                            SelectionContainer { Text(incoming, Modifier.testTag("phone-writing-incoming")) }
                        }
                        reviewed != null -> {
                            Text(label(Words.PhoneWritingReview), style = MaterialTheme.typography.titleMedium)
                            Text(label(Words.PhoneLoaded).replace("{model}", reviewed.modelName))
                            if (state.loaded?.sha256 != reviewed.modelSha256) Text(label(Words.PhoneWritingModelChanged))
                            Text(label(Words.PhoneWritingInstructions))
                            SelectionContainer { Text(reviewed.instructions, Modifier.testTag("phone-writing-reviewed-instructions")) }
                            Text(label(Words.PhoneWritingSource))
                            SelectionContainer { Text(reviewed.source, Modifier.testTag("phone-writing-reviewed-source")) }
                        }
                        notesOpen -> {
                            Text(label(Words.PhoneWritingNotes), style = MaterialTheme.typography.titleMedium)
                            if (library.notes.isEmpty()) Text(label(Words.PhoneWritingNoNotes))
                            library.notes.forEach { note ->
                                HermesCard {
                                    TextButton({ openedNote = note.id.takeUnless { openedNote == it } }) {
                                        Text(note.text.trim().lineSequence().first().take(90))
                                    }
                                    if (openedNote == note.id) {
                                        if (note.atLimit) Text(label(Words.PhoneTokenLimit))
                                        SelectionContainer { Text(note.text) }
                                        TextButton({ vm.removeWritingNote(note) }, enabled = idle) { Text(label(Words.Delete)) }
                                    }
                                }
                            }
                        }
                        output != null -> {
                            Text(label(Words.PhoneWritingOutput), style = MaterialTheme.typography.titleMedium)
                            if (output.atLimit) Text(label(Words.PhoneTokenLimit))
                            SelectionContainer { Text(output.text, Modifier.testTag("phone-writing-output")) }
                            TextButton({ notesOpen = true }) { Text(label(Words.PhoneWritingNotes)) }
                        }
                        else -> {
                            WritingDraftFields(draft, library, state, idle, vm)
                            TextButton({ notesOpen = true }) { Text(label(Words.PhoneWritingNotes)) }
                        }
                    }
                }
                HorizontalDivider()
                when {
                    state.busy -> {
                        Text(label(Words.PhoneWritingWorking))
                        if (state.status == "generating") TextButton(vm.controller::cancel) { Text(label(Words.Stop)) }
                    }
                    saving || capture.saving -> Text(label(Words.PhoneWritingWorking))
                    incoming != null -> {
                        TextButton(vm::saveSharedCapture, Modifier.fillMaxWidth().testTag("capture-save-shared")) {
                            Text(label(Words.CaptureSaveToInbox))
                        }
                        Button(vm.writing::useIncoming, Modifier.fillMaxWidth().testTag("phone-writing-use-incoming")) {
                            Text(label(if (draft.source.isEmpty()) Words.PhoneWritingUseShared else Words.PhoneWritingReplaceShared))
                        }
                        TextButton(vm.writing::dismissIncoming, Modifier.fillMaxWidth().testTag("phone-writing-cancel-intake")) { Text(label(Words.Cancel)) }
                    }
                    reviewed != null -> {
                        Button({ vm.sendWriting(reviewed) }, Modifier.fillMaxWidth().testTag("phone-writing-send"),
                            enabled = state.loaded?.sha256 == reviewed.modelSha256) { Text(label(Words.PhoneWritingSend)) }
                        TextButton(vm.writing::cancelReview, Modifier.fillMaxWidth().testTag("phone-writing-cancel-review")) { Text(label(Words.Cancel)) }
                    }
                    notesOpen -> TextButton({ notesOpen = false; openedNote = null }, Modifier.fillMaxWidth()) { Text(label(Words.PhoneWritingBack)) }
                    output != null -> {
                        val saved = output in library.notes
                        if (saved) Text(label(Words.PhoneWritingNoteSaved))
                        else if (library.notes.size == PhoneWritingLibrary.LIMIT) Text(label(Words.PhoneWritingNotesFull))
                        else Button({ vm.saveWritingNote(output) }, Modifier.fillMaxWidth().testTag("phone-writing-save-note")) {
                            Text(label(Words.PhoneWritingSaveNote))
                        }
                        TextButton(vm.writing::dismissResult, Modifier.fillMaxWidth().testTag("phone-writing-back-draft")) { Text(label(Words.PhoneWritingBack)) }
                    }
                    else -> {
                        if (state.loaded == null) Text(label(Words.PhoneWritingLoadToReview))
                        else if (!draft.canReview) Text(label(Words.PhoneWritingLimits))
                        Button({
                            focus.clearFocus()
                            keyboard?.hide()
                            vm.controller.dismissError()
                            vm.writing.prepare(state.loaded)
                        }, Modifier.fillMaxWidth().testTag("phone-writing-prepare"), enabled = state.loaded != null && draft.canReview) {
                            Text(label(Words.PhoneWritingReview))
                        }
                        TextButton(vm::closeWriting, Modifier.fillMaxWidth().testTag("phone-writing-close")) { Text(label(Words.Close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingDraftFields(draft: PhoneWritingDraft, library: PhoneWritingLibrary, state: LocalAiState,
    enabled: Boolean, vm: LocalAiViewModel) {
    var languageChinese by remember { mutableStateOf(false) }
    var chooseTemplate by remember { mutableStateOf(false) }
    Text(label(Words.PhoneWritingPrivate))
    PhoneInlineModelPicker(state, enabled) { vm.controller.load(it) }
    TextButton({ languageChinese = !languageChinese }, enabled = enabled) {
        Text(label(if (languageChinese) Words.PhoneWritingChinese else Words.PhoneWritingEnglish))
    }
    Box {
        TextButton({ chooseTemplate = true }, enabled = enabled, modifier = Modifier.testTag("phone-writing-templates")) {
            Text(label(Words.PhoneWritingTemplate))
        }
        DropdownMenu(chooseTemplate, { chooseTemplate = false }) {
            writingPresets.forEachIndexed { index, (name, prompt) ->
                DropdownMenuItem(text = { Text(label(name)) }, modifier = Modifier.testTag("phone-writing-preset-$index"),
                    onClick = { chooseTemplate = false; vm.writing.choosePreset(if (languageChinese) prompt.zh else prompt.en) })
            }
            library.templates.forEach { template ->
                DropdownMenuItem(text = { Text(template.title) }, onClick = { chooseTemplate = false; vm.writing.chooseTemplate(template) })
            }
        }
    }
    OutlinedTextField(draft.instructions, vm.writing::editInstructions, Modifier.fillMaxWidth().testTag("phone-writing-instructions"),
        label = { Text(label(Words.PhoneWritingInstructions)) }, minLines = 2, maxLines = 6, enabled = enabled)
    OutlinedTextField(draft.source, vm.writing::editSource, Modifier.fillMaxWidth().testTag("phone-writing-source"),
        label = { Text(label(Words.PhoneWritingSource)) }, minLines = 3, maxLines = 8, enabled = enabled)
    OutlinedTextField(draft.title, vm.writing::editTitle, Modifier.fillMaxWidth().testTag("phone-writing-template-title"),
        label = { Text(label(Words.PhoneWritingTemplateTitle)) }, singleLine = true, enabled = enabled)
    if (library.templates.size == PhoneWritingLibrary.LIMIT && draft.template == null) {
        Text(label(Words.PhoneWritingTemplatesFull))
    } else {
        TextButton(vm::saveWritingTemplate, enabled = enabled && validTitle(draft.title) && validInstructions(draft.instructions),
            modifier = Modifier.testTag("phone-writing-save-template")) { Text(label(Words.PhoneWritingSaveTemplate)) }
    }
    draft.template?.let { template ->
        TextButton({ vm.removeWritingTemplate(template) }, enabled = enabled) { Text(label(Words.PhoneWritingDeleteTemplate)) }
    }
}

@Composable
internal fun PhoneInlineModelPicker(state: LocalAiState, enabled: Boolean, onLoad: (InstalledLocalModel) -> Unit) {
    var choose by remember { mutableStateOf(false) }
    state.loaded?.let { Text(label(Words.PhoneLoaded).replace("{model}", it.metadata.name)) }
    if (state.models.isEmpty()) Text(label(Words.PhoneWritingNoInstalled))
    else Box {
        TextButton({ choose = true }, enabled = enabled) { Text(label(Words.PhoneWritingChooseModel)) }
        DropdownMenu(choose, { choose = false }) {
            state.models.forEach { model ->
                DropdownMenuItem(text = { Text(model.metadata.name) }, enabled = model.sha256 != state.loaded?.sha256,
                    onClick = { choose = false; onLoad(model) })
            }
        }
    }
}
