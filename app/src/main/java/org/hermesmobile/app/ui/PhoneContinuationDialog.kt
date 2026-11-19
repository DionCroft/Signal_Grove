package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.hermesmobile.app.local.LocalAiState
import org.hermesmobile.app.local.LocalConversation
import org.hermesmobile.app.local.LocalConversationStore
import org.hermesmobile.app.local.LocalPromptPreview
import org.hermesmobile.app.local.retainedForPrompt

internal data class PhoneContinuationReview(val source: LocalConversation, val draft: String)

@Composable internal fun PhoneContinuationDialog(
    review: PhoneContinuationReview,
    state: LocalAiState,
    currentDraft: String,
    actions: LocalAiActions,
    close: () -> Unit
) {
    val validSource = state.chat == review.source && !state.busy
    val rows = remember(review) { review.source.exchanges.withIndex().filter { it.value.retainedForPrompt } }
    var selected by remember(review) { mutableStateOf(emptySet<Int>()) }
    var expanded by remember(review) { mutableStateOf<Int?>(null) }
    var copyDraft by remember(review) { mutableStateOf(review.draft.isNotBlank()) }
    val indices = remember(selected) { selected.sorted() }
    var summaryReview by remember(review) { mutableStateOf<List<Int>?>(null) }
    summaryReview?.let { chosen ->
        PhoneSummaryReview(review, chosen, copyDraft, currentDraft, state, actions,
            accept = { summary ->
                actions.continueWithSummary?.invoke(review.source, chosen, summary, review.draft, copyDraft)
                close()
            },
            close = { summaryReview = null })
        return
    }
    val preview = actions.previewSelectedPrompt
    val capacity = state.history.size < LocalConversationStore.MAX_CHATS
    val draftChanged = copyDraft && currentDraft != review.draft
    val question = review.draft.trim()
    var budget by remember(review, indices, copyDraft, state.loaded, validSource, draftChanged) {
        mutableStateOf<LocalPromptPreview?>(null)
    }
    if (preview != null && validSource && !draftChanged && copyDraft && indices.isNotEmpty() &&
        state.loaded?.sha256 == review.source.modelSha256 && question.isNotEmpty() && question.toByteArray().size <= 8192) {
        LaunchedEffect(preview, indices, question, state.loaded) {
            delay(300)
            budget = preview(review.source, indices, question)
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.weight(1f).testTag("phone-context-selection"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(label(Words.PhoneSelectContextTitle), style = MaterialTheme.typography.titleLarge)
                        Text(label(Words.PhoneSelectContextHelp))
                        Text(label(Words.PhoneContextSelectedCount).replace("{count}", indices.size.toString()),
                            Modifier.testTag("phone-context-selection-count"), style = MaterialTheme.typography.labelLarge)
                        if (!validSource || draftChanged) Text(label(Words.PhoneContextReviewChanged), color = MaterialTheme.colorScheme.error)
                        if (!capacity) Text(label(Words.PhoneBranchHistoryFull))
                        if (review.draft.isNotBlank()) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(copyDraft, role = Role.Checkbox,
                                    onValueChange = { copyDraft = it }).testTag("phone-context-copy-draft"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(copyDraft, onCheckedChange = null)
                                Text(label(Words.PhoneCopyUnsentDraft), Modifier.padding(start = 8.dp))
                            }
                        }
                        budget?.let { PhonePromptUse(it) }
                        if (actions.continueWithSummary != null) TextButton({ summaryReview = indices },
                            enabled = validSource && capacity && !draftChanged && indices.isNotEmpty(),
                            modifier = Modifier.testTag("phone-context-summary-review")) {
                            Text(label(Words.PhoneSummaryReview))
                        }
                    }
                    items(rows, key = { it.index }) { (index, row) ->
                        HermesCard {
                            val included = index in selected
                            val toggleEnabled = validSource && (included || selected.size < LocalConversationStore.MAX_TURNS - 1)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(included, enabled = toggleEnabled,
                                    role = Role.Checkbox, onValueChange = { checked ->
                                        selected = if (checked) selected + index else selected - index
                                    }).testTag("phone-context-turn-$index"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(included, onCheckedChange = null, enabled = toggleEnabled)
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(label(Words.PhoneContextSourceTurn).replace("{number}", (index + 1).toString()),
                                        style = MaterialTheme.typography.labelMedium)
                                    Text(row.question, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            TextButton({ expanded = if (expanded == index) null else index },
                                modifier = Modifier.testTag("phone-context-review-turn-$index")) {
                                Text(label(if (expanded == index) Words.PhoneContextHideTurn else Words.PhoneContextShowTurn))
                            }
                            if (expanded == index) {
                                SelectionContainer { Text(row.question) }
                                PhoneReply(row.reply, row.state, index)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({
                        actions.continueWithSelectedTurns?.invoke(review.source, indices, review.draft, copyDraft)
                        close()
                    }, enabled = validSource && capacity && !draftChanged && indices.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-context-confirm")) {
                        Text(label(Words.PhoneCreateContextBranch))
                    }
                    TextButton(close, Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-context-cancel")) {
                        Text(label(Words.Cancel))
                    }
                }
            }
        }
    }
}
