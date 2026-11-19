package org.hermesmobile.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import org.hermesmobile.app.local.MarkdownSaveResult
import org.hermesmobile.app.local.PhoneMarkdownExport
import org.hermesmobile.app.local.PhoneMarkdownExportState

@Composable
internal fun PhoneMarkdownExportFlow(export: PhoneMarkdownExport, busy: Boolean) {
    val state by export.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown"), export::destination)
    when (val current = state) {
        PhoneMarkdownExportState.Idle -> Unit
        is PhoneMarkdownExportState.Review -> PhoneMarkdownReview(current, busy, export::dismiss) {
            export.choose(current.document) { picker.launch(it) }
        }
        PhoneMarkdownExportState.ChoosingDestination -> AlertDialog(
            onDismissRequest = {},
            text = { Text(label(Words.PhoneMarkdownChoosing)) },
            confirmButton = {}
        )
        PhoneMarkdownExportState.Writing -> AlertDialog(
            onDismissRequest = {},
            text = { Text(label(Words.PhoneMarkdownWriting), Modifier.testTag("phone-markdown-writing")) },
            confirmButton = { TextButton(export::stopWrite) { Text(label(Words.Stop)) } }
        )
        is PhoneMarkdownExportState.Finished -> AlertDialog(
            onDismissRequest = export::dismiss,
            text = { Text(label(markdownResultWord(current.result)), Modifier.testTag("phone-markdown-result")) },
            confirmButton = { TextButton(export::dismiss) { Text(label(Words.Close)) } }
        )
    }
}

@Composable
private fun PhoneMarkdownReview(review: PhoneMarkdownExportState.Review, busy: Boolean, dismiss: () -> Unit, choose: () -> Unit) {
    val document = review.document
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label(Words.PhoneMarkdownReview), style = MaterialTheme.typography.titleLarge)
                    Text(review.displayTitle, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("phone-markdown-title"))
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(document.updatedAt)))
                    Text(label(Words.PhoneMarkdownSize).replace("{count}", document.exchanges.toString()).replace("{bytes}", document.byteCount.toString()))
                    Text(document.suggestedFilename)
                    Text(label(Words.PhoneMarkdownContents))
                }
                TextButton(choose, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-markdown-choose")) {
                    Text(label(Words.PhoneMarkdownChoose))
                }
                TextButton(dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-markdown-cancel")) {
                    Text(label(Words.Cancel))
                }
            }
        }
    }
}

private fun markdownResultWord(result: MarkdownSaveResult) = when (result) {
    MarkdownSaveResult.SAVED -> Words.PhoneMarkdownSaved
    MarkdownSaveResult.CANCELLED -> Words.PhoneMarkdownCancelled
    MarkdownSaveResult.PICKER_UNAVAILABLE -> Words.PhoneMarkdownPickerUnavailable
    MarkdownSaveResult.STOPPED -> Words.PhoneMarkdownStopped
    MarkdownSaveResult.UNCERTAIN -> Words.PhoneMarkdownUncertain
    MarkdownSaveResult.INTERRUPTED -> Words.PhoneMarkdownInterrupted
}
