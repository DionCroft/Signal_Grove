package org.hermesmobile.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.CuratedPhoneModel
import org.hermesmobile.app.local.LocalAiState
import org.hermesmobile.app.local.LocalModelStore

@Composable
internal fun PhoneLicence(
    text: String,
    busy: Boolean,
    accept: () -> Unit,
    close: () -> Unit,
    model: CuratedPhoneModel? = null,
    catalogueState: LocalAiState? = null,
    queue: (() -> Unit)? = null
) {
    var approved by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("phone-licence-body"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(label(Words.PhoneLicence), style = MaterialTheme.typography.titleLarge)
                    if (model != null) {
                        Text(model.displayName, Modifier.testTag("phone-licence-model"), style = MaterialTheme.typography.titleMedium)
                        SelectionContainer {
                            Text(model.catalogueFileDetails(), Modifier.testTag("phone-licence-source"), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(label(Words.PhoneDownloadInfo), style = MaterialTheme.typography.bodySmall)
                        if (catalogueState != null) CatalogueUpdateReview(model, catalogueState)
                    }
                    SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(approved, enabled = !busy, role = Role.Checkbox, onValueChange = { approved = it })
                        .testTag("phone-licence-consent"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(approved, onCheckedChange = null, enabled = !busy)
                    Text(label(Words.PhoneLicenceAccept), Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (queue != null) {
                    OutlinedButton(queue, Modifier.fillMaxWidth().testTag("phone-licence-queue"), enabled = approved && !busy) {
                        Text(label(Words.PhoneDownloadQueueAdd))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(close, Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-licence-cancel")) {
                        Text(label(Words.Cancel))
                    }
                    Button(
                        accept,
                        Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-licence-confirm"),
                        enabled = approved && !busy
                    ) { Text(label(Words.Confirm)) }
                }
            }
        }
    }
}

@Composable
private fun CatalogueUpdateReview(model: CuratedPhoneModel, state: LocalAiState) {
    val previous = state.modelCatalog.olderVersions(model).filter { older ->
        state.models.any { it.sha256 == older.sha256 }
    }
    if (previous.isEmpty()) return

    val context = LocalContext.current
    val remaining = (model.bytes - state.partialBytes(model)).coerceAtLeast(0)
    val required = Formatter.formatFileSize(context, remaining + LocalModelStore.SPACE_RESERVE)
    Text(label(Words.PhoneCatalogueUpdateKeep), Modifier.testTag("phone-catalogue-update-retention"))
    for (older in previous) {
        SelectionContainer {
            Text(label(Words.PhoneReplacementPrevious) + "\n" + older.displayName + "\n" +
                label(Words.PhoneCatalogueRevision).replace("{revision}", older.revision) + "\nSHA-256 · ${older.sha256}",
                Modifier.testTag("phone-catalogue-update-previous-${older.name}"), style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(label(Words.PhoneCatalogueUpdateSpace).replace("{size}", required),
        Modifier.testTag("phone-catalogue-update-space"), style = MaterialTheme.typography.bodySmall)
    state.modelStorageAvailableBytes?.let { available ->
        Text(label(Words.PhoneCatalogueUpdateFree).replace("{available}", Formatter.formatFileSize(context, available)),
            Modifier.testTag("phone-catalogue-update-free"), style = MaterialTheme.typography.bodySmall)
    }
}
