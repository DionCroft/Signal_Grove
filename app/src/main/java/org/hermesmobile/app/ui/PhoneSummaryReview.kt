package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.hermesmobile.app.local.LOCAL_SUMMARY_BYTE_LIMIT
import org.hermesmobile.app.local.LocalAiState
import org.hermesmobile.app.local.LocalConversationStore
import org.hermesmobile.app.local.LocalPromptPreview
import org.hermesmobile.app.local.LocalReviewedSummary
import org.hermesmobile.app.local.isValidReviewedSummary

@Composable internal fun PhoneSummaryReview(
    review: PhoneContinuationReview,
    indices: List<Int>,
    copyDraft: Boolean,
    currentDraft: String,
    state: LocalAiState,
    actions: LocalAiActions,
    accept: (String) -> Unit,
    close: () -> Unit
) {
    var text by remember(review, indices) { mutableStateOf("") }
    var rejectedEdit by remember(review, indices) { mutableStateOf(false) }
    var draftRequested by remember(review, indices) { mutableStateOf(false) }
    var limitedDraft by remember(review, indices) { mutableStateOf(false) }
    val sameSource = state.chat == review.source
    val sameDraft = !copyDraft || currentDraft == review.draft
    val capacity = state.history.size < LocalConversationStore.MAX_CHATS
    val canEdit = sameSource && !state.busy
    val canDraft = state.loaded?.sha256 == review.source.modelSha256
    val drafting = draftRequested && state.busy && state.status == "summarizing"
    val valid = !rejectedEdit && text.isValidReviewedSummary()
    val question = review.draft.trim()
    val preview = actions.previewSummaryPrompt
    var budget by remember(review, question, text, valid, sameSource, sameDraft, canDraft, state.busy) {
        mutableStateOf<LocalPromptPreview?>(null)
    }
    if (preview != null && copyDraft && canEdit && sameDraft && canDraft && valid &&
        question.isNotEmpty() && question.toByteArray().size <= 8192) {
        LaunchedEffect(preview, text, question) {
            delay(300)
            budget = preview(review.source, text, question)
        }
    }
    val cancel: () -> Unit = {
        if (drafting) actions.stop()
        close()
    }
    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("phone-summary-review"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(label(Words.PhoneSummaryReview), style = MaterialTheme.typography.titleLarge)
                    Text(label(Words.PhoneSummaryHelp))
                    SummarySources(indices)
                    if (copyDraft) Text(label(Words.PhoneCopyUnsentDraft))
                    if (!sameSource || !sameDraft) Text(label(Words.PhoneContextReviewChanged), color = MaterialTheme.colorScheme.error)
                    if (!capacity) Text(label(Words.PhoneBranchHistoryFull))
                    actions.draftSummary?.let { prepare ->
                        if (drafting) {
                            Text(label(Words.PhoneSummaryDrafting))
                            TextButton(actions.stop, modifier = Modifier.testTag("phone-summary-stop")) { Text(label(Words.Stop)) }
                        } else {
                            TextButton({
                                draftRequested = true
                                limitedDraft = false
                                prepare(review.source, indices) { result ->
                                    text = result.text
                                    rejectedEdit = false
                                    limitedDraft = result.reachedTokenLimit
                                }
                            }, enabled = canEdit && sameDraft && canDraft && capacity && text.isBlank(),
                                modifier = Modifier.testTag("phone-summary-draft")) {
                                Text(label(Words.PhoneSummaryDraft))
                            }
                            if (!canDraft) Text(label(Words.PhoneSummaryDraftHint))
                            else if (text.isNotBlank()) Text(label(Words.PhoneSummaryDraftKeep))
                        }
                    }
                    OutlinedTextField(text, { changed ->
                        rejectedEdit = changed.length > LOCAL_SUMMARY_BYTE_LIMIT
                        if (!rejectedEdit) text = changed
                    }, Modifier.fillMaxWidth().testTag("phone-summary-text"),
                        label = { Text(label(Words.PhoneSummaryText)) }, enabled = canEdit, minLines = 3, maxLines = 8,
                        isError = rejectedEdit || (text.isNotBlank() && !valid))
                    Text(label(Words.PhoneSummaryLength),
                        color = if (rejectedEdit || (text.isNotBlank() && !valid)) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    if (limitedDraft) Text(label(Words.PhoneSummaryDraftLimit))
                    if (draftRequested && !state.busy) state.error?.let { code ->
                        Text(label(phoneError(code)), color = MaterialTheme.colorScheme.error)
                    }
                    budget?.let { PhonePromptUse(it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ accept(text) }, enabled = canEdit && sameDraft && capacity && valid,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-summary-confirm")) {
                        Text(label(Words.PhoneCreateContextBranch))
                    }
                    TextButton(cancel, Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-summary-cancel")) {
                        Text(label(Words.Cancel))
                    }
                }
            }
        }
    }
}

@Composable internal fun PhoneReviewedSummary(summary: LocalReviewedSummary) {
    var expanded by remember(summary) { mutableStateOf(false) }
    HermesCard {
        SummarySources(summary.sourceTurns)
        TextButton({ expanded = !expanded }, modifier = Modifier.testTag("phone-summary-read")) {
            Text(label(if (expanded) Words.PhoneSummaryHide else Words.PhoneSummaryRead))
        }
        if (expanded) SelectionContainer { Text(summary.text, Modifier.testTag("phone-summary-saved")) }
    }
}

@Composable private fun SummarySources(indices: List<Int>) {
    val turns = indices.joinToString(", ") { (it + 1).toString() }
    Text(label(Words.PhoneSummarySource).replace("{turns}", turns),
        Modifier.testTag("phone-summary-source"), style = MaterialTheme.typography.labelMedium)
}
