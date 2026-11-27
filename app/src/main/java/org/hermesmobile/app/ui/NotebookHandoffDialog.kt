package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.local.NotebookHandoffDraft
import org.hermesmobile.app.local.NotebookHandoffReview

@Composable
internal fun NotebookHandoffDialog(vm: HermesViewModel, draft: NotebookHandoffDraft, openChat: () -> Unit) {
    var instruction by remember(draft) { mutableStateOf("") }
    var review by remember(draft) { mutableStateOf<NotebookHandoffReview?>(null) }
    var blocked by remember(draft) { mutableStateOf(false) }
    val connected by vm.connectionStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val accepted = review
    Dialog(vm::dismissNotebookHandoff,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val focus = LocalFocusManager.current
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Word(Words.NotebookHandoff)
                if (blocked) Text(label(Words.NotebookHandoffChanged), color = MaterialTheme.colorScheme.error)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(draft.notebookName, style = MaterialTheme.typography.titleMedium)
                    if (accepted == null) {
                        if (connected != "connected") Text(label(Words.NotebookHandoffConnect))
                        OutlinedTextField(instruction, {
                            if (it.length <= 1024) { instruction = it; blocked = false }
                        }, Modifier.fillMaxWidth().testTag("notebook-instructions"),
                            label = { Text(label(Words.NotebookHandoffInstruction)) }, maxLines = 5)
                        draft.excerpts.forEach { excerpt ->
                            HermesCard {
                                Text(excerpt.source, style = MaterialTheme.typography.titleSmall)
                                SelectionContainer { Text(excerpt.text) }
                            }
                        }
                    } else {
                        HermesCard {
                            Text(label(Words.NotebookHandoffDestination))
                            Text(accepted.connectionName)
                            SelectionContainer { Text(accepted.endpoint, Modifier.testTag("notebook-handoff-endpoint")) }
                            Text(label(Words.NewChat))
                        }
                        SelectionContainer { Text(accepted.body, Modifier.testTag("notebook-handoff-body")) }
                    }
                }
                if (accepted == null) {
                    Button({
                        focus.clearFocus()
                        val result = vm.previewNotebookHandoff(draft, instruction.trim())
                        review = result
                        blocked = result == null
                    }, Modifier.fillMaxWidth().testTag("notebook-handoff-preview"),
                        enabled = connected == "connected" && !busy && draft.body(instruction.trim()) != null) {
                        Text(label(Words.NotebookHandoffReview))
                    }
                } else Button({
                    if (vm.confirmNotebookHandoff(accepted)) openChat()
                    else { review = null; blocked = true }
                }, Modifier.fillMaxWidth().testTag("notebook-handoff-send"), enabled = connected == "connected" && !busy) {
                    Text(label(Words.NotebookHandoffSend))
                }
                TextButton(vm::dismissNotebookHandoff, Modifier.fillMaxWidth().testTag("notebook-handoff-cancel")) {
                    Text(label(Words.Cancel))
                }
            }
        }
    }
}
