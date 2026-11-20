package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.window.SecureFlagPolicy
import org.hermesmobile.app.local.LocalAiState
import org.hermesmobile.app.local.LocalReplyContent
import org.hermesmobile.app.local.LocalReplyState
import org.hermesmobile.app.local.LocalAiViewModel
import org.hermesmobile.app.local.TemporaryPhoneChatState

@Composable
internal fun PhoneTemporaryChatDialog(vm: LocalAiViewModel, chat: TemporaryPhoneChatState, state: LocalAiState) {
    var confirmEnd by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val loaded = state.loaded
    val close = {
        if (confirmEnd) confirmEnd = false
        else if (chat.hasContent || chat.pending) confirmEnd = true
        else vm.closeTemporary()
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(confirmEnd) {
            if (confirmEnd) {
                focus.clearFocus()
                keyboard?.hide()
            }
        }
        LaunchedEffect(chat.exchanges.size) {
            if (chat.exchanges.isNotEmpty()) list.scrollToItem(chat.exchanges.size)
        }
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(Words.PhoneTemporaryChat), Modifier.testTag("phone-temporary-title"), style = MaterialTheme.typography.titleLarge)
                if (confirmEnd) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label(Words.PhoneTemporaryEndReview), Modifier.testTag("phone-temporary-end-review"))
                        Text(label(Words.PhoneTemporaryRetention))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ confirmEnd = false }, Modifier.weight(1f).testTag("phone-temporary-keep")) {
                            Text(label(Words.Cancel))
                        }
                        Button(vm::closeTemporary, Modifier.weight(1f).testTag("phone-temporary-end-confirm")) {
                            Text(label(Words.PhoneTemporaryEnd))
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("phone-temporary-messages"),
                        state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "temporary-info") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(label(Words.PhoneTemporaryRetention))
                                if (chat.modelSha256 == null) PhoneInlineModelPicker(state, !state.busy) { vm.controller.load(it) }
                                else if (loaded != null && loaded.sha256 == chat.modelSha256) {
                                    Text(label(Words.PhoneLoaded).replace("{model}", loaded.metadata.name))
                                } else Text(label(Words.PhoneTemporaryModelChanged))
                                if (loaded == null) Text(label(Words.PhoneTemporaryLoad))
                                state.error?.let {
                                    Text(label(phoneError(it)), color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.testTag("phone-temporary-error"))
                                }
                                if (chat.exchanges.isEmpty()) Text(label(Words.PhoneTemporaryEmpty))
                            }
                        }
                        items(chat.exchanges) { exchange ->
                            HermesCard {
                                Text(exchange.question, Modifier.testTag("phone-temporary-question"), style = MaterialTheme.typography.titleSmall)
                                LocalReplyContent.chunks(exchange.reply).forEach { section ->
                                    Text(section, Modifier.testTag("phone-temporary-reply"))
                                }
                                if (exchange.state == LocalReplyState.TOKEN_LIMIT) Text(label(Words.PhoneTokenLimit))
                            }
                        }
                    }
                    if (chat.atTurnLimit) Text(label(Words.PhoneTemporaryLimit))
                    else if (loaded != null && chat.draft.isNotBlank() && !chat.pending && !chat.canSend(state.loaded) &&
                        (chat.modelSha256 == null || chat.modelSha256 == state.loaded.sha256)) {
                        Text(label(Words.PhoneTemporaryDraftLimit))
                    }
                    OutlinedTextField(chat.draft, vm.temporary::edit, Modifier.fillMaxWidth().testTag("phone-temporary-draft"),
                        enabled = !state.busy && !chat.atTurnLimit, label = { Text(label(Words.PhoneTemporaryMessage)) }, maxLines = 4)
                    if (state.busy) Text(label(Words.PhoneWritingWorking))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({
                            focus.clearFocus()
                            keyboard?.hide()
                            close()
                        }, Modifier.weight(1f).testTag("phone-temporary-close")) {
                            Text(label(Words.PhoneTemporaryEnd))
                        }
                        if (chat.pending) TextButton(vm.controller::cancel, Modifier.weight(1f).testTag("phone-temporary-stop")) {
                            Text(label(Words.Stop))
                        } else Button({
                            focus.clearFocus()
                            keyboard?.hide()
                            vm.controller.sendTemporaryChat(vm.temporary)
                        }, Modifier.weight(1f).testTag("phone-temporary-send"),
                            enabled = !state.busy && chat.canSend(state.loaded)) { Text(label(Words.Send)) }
                    }
                }
            }
        }
    }
}
