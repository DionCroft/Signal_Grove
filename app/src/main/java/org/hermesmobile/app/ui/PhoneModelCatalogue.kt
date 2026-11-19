package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.CuratedPhoneModel
import org.hermesmobile.app.local.LocalAiState
import org.hermesmobile.app.local.minimumModelLoadMemoryBytes

@Composable
internal fun PhoneModelCatalogue(
    state: LocalAiState,
    supported: Boolean,
    reviewDownload: (CuratedPhoneModel) -> Unit,
    reviewDiscard: (CuratedPhoneModel) -> Unit,
    close: () -> Unit
) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(Words.PhoneCatalogue), style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!supported) Text(label(Words.PhoneUnsupported), style = MaterialTheme.typography.bodySmall)
                    CuratedPhoneModel.entries.forEach { model ->
                        val installed = state.models.any { it.sha256 == model.sha256 }
                        val partial = state.partialBytes(model)
                        val minimumMiB = (minimumModelLoadMemoryBytes(model.bytes) + 1048575) / 1048576
                        HermesCard {
                            Text(model.displayName, Modifier.testTag("phone-catalogue-${model.name}"),
                                style = MaterialTheme.typography.titleMedium)
                            Text(model.repository, style = MaterialTheme.typography.bodySmall)
                            Text(model.licence, style = MaterialTheme.typography.bodySmall)
                            Text(label(Words.PhoneCatalogueSize).replace("{bytes}", model.bytes.toString()),
                                style = MaterialTheme.typography.bodySmall)
                            Text(label(Words.PhoneModelFreeMemory).replace("{number}", minimumMiB.toString()),
                                style = MaterialTheme.typography.bodySmall)
                            Text(label(Words.PhoneCatalogueTested), style = MaterialTheme.typography.bodySmall)
                            if (partial > 0) {
                                Text(label(Words.PhonePartial).replace("{bytes}", (partial / 1048576).toString()))
                                TextButton({ reviewDiscard(model) }, enabled = !state.busy,
                                    modifier = Modifier.testTag("phone-discard-${model.name}")) {
                                    Text(label(Words.PhoneDiscard))
                                }
                            }
                            if (installed) Text(label(Words.PhoneCatalogueInstalled))
                            else Button({ reviewDownload(model) }, enabled = supported && !state.busy,
                                modifier = Modifier.testTag("phone-download-review-${model.name}")) {
                                Text(label(Words.PhoneDownload))
                            }
                        }
                    }
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-catalogue-close")) {
                    Text(label(Words.Close))
                }
            }
        }
    }
}

@Composable
internal fun CuratedPhoneModel.catalogueFileDetails() = listOf(
    repository, filename, licence, label(Words.PhoneCatalogueSize).replace("{bytes}", bytes.toString()),
    label(Words.PhoneCatalogueRevision).replace("{revision}", revision), "SHA-256 · $sha256"
).joinToString("\n")
