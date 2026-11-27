package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.*

data class PhoneDocumentActions(
    val refresh: () -> Unit,
    val choose: (PhoneDocument?) -> Unit,
    val index: () -> Unit,
    val cancelIndex: () -> Unit,
    val remove: (PhoneDocument) -> Unit,
    val review: (List<String>, String) -> Unit,
    val answer: (PhoneDocumentReview) -> Unit,
    val clearReview: () -> Unit,
    val saveToNotebook: ((PhoneDocumentAnswer) -> Unit)? = null
)

@Composable
internal fun PhoneDocuments(
    state: LocalAiState, actions: PhoneDocumentActions, pending: Boolean,
    stop: () -> Unit, dismissError: () -> Unit, close: () -> Unit
) {
    var scope by remember { mutableStateOf(emptySet<String>()) }
    var question by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<PhoneDocument?>(null) }
    val bodyScroll = rememberScrollState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val idle = !state.busy
    val review = state.documentReview
    val answer = state.documentAnswer
    val selected = state.documents.filter { it.id in scope }
    val showLibrary = !pending && removing == null && review == null && answer == null
    val trimmedQuestion = question.trim()
    val canReview = idle && PhoneDocumentQuestions.validQuestion(trimmedQuestion)
    LaunchedEffect(pending, removing, review, answer?.review) { bodyScroll.scrollTo(0) }
    LaunchedEffect(state.documents) { scope = scope.intersect(state.documents.map { it.id }.toSet()) }
    Dialog(onDismissRequest = { if (idle) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(Words.PhoneDocuments), style = MaterialTheme.typography.titleLarge)
                if (!idle) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.takeIf { it.startsWith("document_") || it == "engine_2" || it == "operation_failed" }?.let { code ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label(phoneError(code)), Modifier.weight(1f).testTag("phone-document-error"),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(dismissError) { Text(label(Words.Close)) }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(bodyScroll).testTag("phone-document-body"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        pending -> {
                            Text(label(Words.PhoneDocumentIndex))
                            Text(label(Words.PhoneDocumentPrivacy))
                        }
                        removing != null -> Text(label(Words.PhoneDocumentRemoveReview)
                            .replace("{name}", checkNotNull(removing).name))
                        review != null -> {
                            Text(label(Words.PhoneDocumentReview))
                            Text(review.question)
                            DocumentSources(review)
                            if (state.loaded == null) Text(label(Words.PhoneNotLoaded))
                            else Text(label(Words.PhoneLoaded).replace("{model}", state.loaded.metadata.name))
                        }
                        answer != null -> {
                            Text(answer.review.question)
                            answer.text?.let {
                                SelectionContainer { Text(it, Modifier.testTag("phone-document-answer")) }
                                if (answer.anchoredVerbatim) Text(label(Words.PhoneDocumentVerbatimSource),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(label(Words.PhoneDocumentCheckAnswer), style = MaterialTheme.typography.bodySmall)
                            }
                            when (answer.state) {
                                LocalReplyState.GENERATING -> Text(label(Words.PhoneGenerating))
                                LocalReplyState.CANCELLED -> Text(label(Words.PhoneCancelled))
                                LocalReplyState.TOKEN_LIMIT -> Text(label(Words.PhoneTokenLimit))
                                else -> Unit
                            }
                            DocumentSources(answer.review)
                            if (answer.state == LocalReplyState.COMPLETE && actions.saveToNotebook != null) {
                                TextButton({
                                    actions.saveToNotebook.invoke(answer)
                                    close()
                                }, Modifier.testTag("phone-document-save-notebook")) { Text(label(Words.NotebookSaveAnswer)) }
                            }
                        }
                        showLibrary -> {
                            Text(label(Words.PhoneDocumentPrivacy), style = MaterialTheme.typography.bodySmall)
                            Text(label(if (android.os.Build.VERSION.SDK_INT >= 35) Words.PhonePdfFormats
                                else Words.PhoneTextFormats), style = MaterialTheme.typography.bodySmall)
                            Button({ actions.choose(null) }, enabled = idle && state.documents.size < PhoneDocumentLibrary.MAX_DOCUMENTS,
                                modifier = Modifier.testTag("phone-document-add")) { Text(label(Words.PhoneDocumentAdd)) }
                            if (state.documents.size >= PhoneDocumentLibrary.MAX_DOCUMENTS) Text(label(Words.PhoneDocumentLimit))
                            TextButton(actions.refresh, enabled = idle) { Text(label(Words.Refresh)) }
                            if (state.documents.isNotEmpty()) Text(label(Words.PhoneDocumentScope)
                                .replace("{count}", selected.size.toString()), Modifier.testTag("phone-document-scope"))
                            for (document in state.documents) {
                                val checked = document.id in scope
                                val selectable = idle && (checked || scope.size < PhoneDocumentLibrary.MAX_SCOPE)
                                HermesCard {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = selectable,
                                            role = Role.Checkbox, onValueChange = { scope = if (it) scope + document.id else scope - document.id })
                                            .testTag("phone-document-select-${document.id}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked, onCheckedChange = null, enabled = selectable)
                                        Text(document.name, Modifier.weight(1f).padding(start = 8.dp))
                                    }
                                    Row(Modifier.fillMaxWidth()) {
                                        TextButton({ actions.choose(document) }, Modifier.weight(1f), enabled = idle) {
                                            Text(label(Words.PhoneDocumentReindex))
                                        }
                                        TextButton({ removing = document }, Modifier.weight(1f), enabled = idle) {
                                            Text(label(Words.Delete))
                                        }
                                    }
                                }
                            }
                            if (selected.isNotEmpty()) {
                                OutlinedTextField(
                                    question, { question = it.take(PhoneDocumentQuestions.QUESTION_BYTES) },
                                    Modifier.fillMaxWidth().testTag("phone-document-question"),
                                    label = { Text(label(Words.PhoneDocumentQuestion)) }, maxLines = 4
                                )
                                if (trimmedQuestion.isNotEmpty() && !PhoneDocumentQuestions.validQuestion(trimmedQuestion)) {
                                    Text(label(Words.PhoneDocumentQuestionLimit), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                when {
                    !idle -> TextButton(stop, Modifier.fillMaxWidth().testTag("phone-document-stop")) { Text(label(Words.Stop)) }
                    pending -> {
                        Button(actions.index, Modifier.fillMaxWidth().testTag("phone-document-index")) { Text(label(Words.PhoneDocumentIndex)) }
                        TextButton(actions.cancelIndex, Modifier.fillMaxWidth()) { Text(label(Words.Cancel)) }
                    }
                    removing != null -> {
                        Button({
                            actions.remove(checkNotNull(removing))
                            removing = null
                        }, Modifier.fillMaxWidth()) { Text(label(Words.Delete)) }
                        TextButton({ removing = null }, Modifier.fillMaxWidth()) { Text(label(Words.Cancel)) }
                    }
                    review != null -> {
                        Button({ actions.answer(review) }, Modifier.fillMaxWidth().testTag("phone-document-answer-confirm"),
                            enabled = state.loaded != null) { Text(label(Words.PhoneDocumentAsk)) }
                        TextButton(actions.clearReview, Modifier.fillMaxWidth()) { Text(label(Words.Cancel)) }
                    }
                    answer != null -> TextButton(actions.clearReview, Modifier.fillMaxWidth()) { Text(label(Words.PhoneDocuments)) }
                    selected.isNotEmpty() -> Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                focus.clearFocus()
                                keyboard?.hide()
                                actions.review(selected.map { it.id }, trimmedQuestion)
                            },
                            enabled = canReview,
                            modifier = Modifier.weight(2f).testTag("phone-document-review")
                        ) { Text(label(Words.PhoneDocumentReview)) }
                        TextButton(close, Modifier.weight(1f).testTag("phone-documents-close")) {
                            Text(label(Words.Close))
                        }
                    }
                    else -> TextButton(close, Modifier.fillMaxWidth().testTag("phone-documents-close")) { Text(label(Words.Close)) }
                }
            }
        }
    }
}

@Composable
private fun DocumentSources(review: PhoneDocumentReview) {
    review.sources.forEachIndexed { index, source ->
        val range = source.page?.let { label(Words.PhoneDocumentPage).replace("{page}", it.toString()) }
            ?: label(Words.PhoneDocumentLines).replace("{first}", source.firstLine.toString())
                .replace("{last}", source.lastLine.toString())
        HermesCard {
            Text("[S${index + 1}] ${source.document.name} · $range",
                Modifier.testTag("phone-document-citation-$index"), style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(source.text, Modifier.testTag("phone-document-source-$index")) }
        }
    }
}
