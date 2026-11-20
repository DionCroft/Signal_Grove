package org.hermesmobile.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.PhoneModelReplacement

@Composable
internal fun PhoneModelReplacementReview(review: PhoneModelReplacement, busy: Boolean, install: () -> Unit, discard: () -> Unit) {
    val context = LocalContext.current
    val candidate = review.candidate
    val storage = label(Words.PhoneReplacementRemaining)
        .replace("{size}", Formatter.formatFileSize(context, candidate.metadata.bytes))
        .replace("{available}", Formatter.formatFileSize(context, review.availableBytes))
    Dialog(
        onDismissRequest = { if (!busy) discard() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("phone-replacement-details"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(label(Words.PhoneReplacementTitle), style = MaterialTheme.typography.titleLarge)
                    Text(label(Words.PhoneReplacementPrevious), style = MaterialTheme.typography.titleMedium)
                    Text(review.previous.metadata.name)
                    SelectionContainer { Text("SHA-256 · ${review.previous.sha256}", style = MaterialTheme.typography.bodySmall) }
                    Text(label(Words.PhoneReplacementCandidate), style = MaterialTheme.typography.titleMedium)
                    Text(candidate.metadata.name)
                    Text(candidate.metadata.architecture, style = MaterialTheme.typography.bodySmall)
                    Text(label(Words.PhoneReplacementContext).replace("{number}", candidate.metadata.trainedContext.toString()),
                        style = MaterialTheme.typography.bodySmall)
                    SelectionContainer {
                        Text("SHA-256 · ${candidate.sha256}", Modifier.testTag("phone-replacement-hash"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(storage)
                    Text(label(Words.PhoneReplacementKeep))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                HorizontalDivider()
                TextButton(discard, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-replacement-cancel"),
                    enabled = !busy) { Text(label(Words.Cancel)) }
                Button(install, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-replacement-install"),
                    enabled = !busy) { Text(label(Words.PhoneReplacementInstall)) }
            }
        }
    }
}
