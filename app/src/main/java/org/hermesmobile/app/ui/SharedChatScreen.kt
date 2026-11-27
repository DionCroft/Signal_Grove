package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.hermesmobile.app.data.Conversation
import org.hermesmobile.app.network.SharedApproval
import org.hermesmobile.app.network.SharedChatProtocol

@Composable internal fun SharedChatEntry(
    chat: Conversation,
    management: ManagementViewModel = viewModel(),
    close: (refreshHistory: Boolean) -> Unit
) {
    val s by management.state.collectAsStateWithLifecycle()
    LaunchedEffect(chat.scope) { management.bind(chat.scope) }
    val selection = management.sharedChatSelection(chat.scope, chat.remoteId)
    if (selection != null) {
        CanonicalSessionsDialog(management.canonicalViewer, available = true, pending = false,
            selection = selection, continuing = true) { close(true) }
    } else {
        AlertDialog(onDismissRequest = { close(false) }, title = { Text(label(Words.SharedChatOpen)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.busy) QuietProgress()
                    Text(label(if (s.signedIn && s.profile.isNotBlank()) Words.ManagementVerify else Words.SharedChatSignIn))
                    s.error?.let { Text(label(it), color = MaterialTheme.colorScheme.error) }
                    if (s.signedIn && s.profile.isNotBlank()) {
                        TextButton({ management.refreshSharedChatAccess(chat.scope) }, enabled = !s.busy,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("shared-chat-verify")) { Text(label(Words.Refresh)) }
                    }
                }
            },
            confirmButton = { TextButton({ close(false) }) { Text(label(Words.Close)) } })
    }
}

@Composable internal fun SharedChatComposer(s: SharedChatState, controller: SharedChatController) {
    OutlinedTextField(s.draft, controller::edit, Modifier.fillMaxWidth().testTag("shared-chat-draft"),
        enabled = !s.busy && s.pending == null, maxLines = 3,
        placeholder = { Text(label(Words.SharedChatPrompt)) },
        trailingIcon = {
            IconButton(controller::send, enabled = !s.busy && s.pending == null && SharedChatProtocol.validPrompt(s.draft),
                modifier = Modifier.testTag("shared-chat-send")) {
                Icon(Icons.AutoMirrored.Outlined.Send, label(Words.Send))
            }
        })
}

@Composable internal fun SharedChatStatus(
    s: SharedChatState, controller: SharedChatController, showApprovalIdentity: Boolean = false
) {
    var review by remember { mutableStateOf<SharedApproval?>(null) }
    var unknownReview by remember(s.pending) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (s.busy) QuietProgress()
        s.status?.let { Text(label(it), Modifier.testTag("shared-chat-status")) }
        if (s.reply.isNotBlank()) SelectionContainer { Text(s.reply, Modifier.testTag("shared-chat-reply")) }
        if (s.replyTruncated) Text(label(Words.ViewerPreviewLimited))
        s.pending?.takeIf { s.available && it.admissionId == null }?.let { pending ->
            TextButton({ unknownReview = true }, enabled = !s.busy) { Text(label(Words.SharedReviewUnknown)) }
            if (unknownReview) AlertDialog(onDismissRequest = { unknownReview = false },
                title = { Text(label(Words.SharedPromptUnknown)) },
                confirmButton = {
                    TextButton({ unknownReview = false; controller.reviewedUnknown(pending) }, enabled = !s.busy) {
                        Text(label(Words.SharedReviewUnknown))
                    }
                }, dismissButton = { TextButton({ unknownReview = false }) { Text(label(Words.Cancel)) } })
        }
        for (approval in s.approvals) {
            TextButton({ review = approval }, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)
                .testTag("shared-chat-approval-" + approval.id)) {
                Text(label(Words.SharedApprovalReview) + " · " + approval.description.ifBlank { approval.command }.take(80),
                    maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
    review?.takeIf { it in s.approvals }?.let { approval ->
        AlertDialog(onDismissRequest = { review = null }, title = { Text(label(Words.Approval)) },
            text = {
                SelectionContainer {
                    Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showApprovalIdentity) {
                            Text(label(Words.TodayApprovalRequestId))
                            Text(approval.id, Modifier.testTag("shared-reviewed-request"))
                            Text(label(Words.TodayApprovalGeneration))
                            Text(approval.generation.toString(), Modifier.testTag("shared-reviewed-generation"))
                        }
                        if (approval.description.isNotBlank()) Text(approval.description)
                        if (approval.command.isNotBlank() && approval.command != approval.description) Text(approval.command)
                    }
                }
            },
            confirmButton = {
                if ("once" in approval.choices) TextButton({ review = null; controller.decide(approval, "once") },
                    enabled = !s.busy, modifier = Modifier.testTag("shared-approval-once")) { Text(label(Words.AllowOnce)) }
            },
            dismissButton = {
                Row {
                    TextButton({ review = null }) { Text(label(Words.Cancel)) }
                    if ("deny" in approval.choices) TextButton({ review = null; controller.decide(approval, "deny") },
                        enabled = !s.busy, modifier = Modifier.testTag("shared-approval-deny")) { Text(label(Words.Deny)) }
                }
            })
    }
}
