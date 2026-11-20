package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.CuratedPhoneModel
import org.hermesmobile.app.local.LocalAiState
import org.hermesmobile.app.local.LocalTransferPhase

@Composable
internal fun PhoneDownloads(
    state: LocalAiState,
    supported: Boolean,
    actions: PhoneDownloadActions,
    review: (CuratedPhoneModel) -> Unit,
    discard: (CuratedPhoneModel) -> Unit,
    refresh: () -> Unit,
    networkSettings: (@Composable (() -> Unit) -> Unit)? = null,
    close: () -> Unit
) {
    var showNetworks by rememberSaveable { mutableStateOf(false) }
    if (showNetworks && networkSettings != null) {
        networkSettings { showNetworks = false }
        return
    }
    val active = state.activeDownload
    val installed = CuratedPhoneModel.entries.filter { model -> state.models.any { it.sha256 == model.sha256 } }
    val models = (listOfNotNull(active) + state.queuedDownloads +
        CuratedPhoneModel.entries.filter { state.partialBytes(it) > 0 } + installed).distinct()
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label(Words.PhoneDownloads), style = MaterialTheme.typography.titleLarge)
                    if (networkSettings != null) {
                        TextButton({ showNetworks = true }, Modifier.testTag("phone-download-networks"), enabled = !state.busy) {
                            Text(label(Words.PhoneDownloadNetworks))
                        }
                    }
                    Text(label(Words.PhoneDownloadQueueHelp), style = MaterialTheme.typography.bodySmall)
                    if (!supported) Text(label(Words.PhoneUnsupported), style = MaterialTheme.typography.bodySmall)
                    if (state.downloadQueueUnavailable) {
                        Text(label(Words.PhoneDownloadQueueUnavailable), color = MaterialTheme.colorScheme.error)
                        TextButton(refresh, enabled = !state.busy) { Text(label(Words.Refresh)) }
                    } else if (models.isEmpty()) {
                        Text(label(Words.PhoneDownloadQueueEmpty), Modifier.testTag("phone-downloads-empty"))
                    }
                    state.error?.takeUnless { state.downloadQueueUnavailable && it == "download_queue_unreadable" }?.let { code ->
                        Text(label(phoneError(code)), color = MaterialTheme.colorScheme.error)
                    }
                    for (model in models) {
                        val reviewed = model in state.queuedDownloads
                        val transfer = state.transfer.takeIf { active == model }
                        val saved = transfer?.bytes ?: state.partialBytes(model)
                        HermesCard {
                            Text(model.displayName, Modifier.testTag("phone-transfer-${model.name}"),
                                style = MaterialTheme.typography.titleMedium)
                            val displayedBytes = if (model in installed && saved == 0L) model.bytes else saved
                            Text(label(Words.PhoneDownloadBytes)
                                .replace("{saved}", displayedBytes.toString()).replace("{total}", model.bytes.toString()),
                                Modifier.testTag("phone-transfer-bytes-${model.name}"))
                            if (active == model) {
                                val phase = transfer?.phase
                                Text(label(if (state.status == "unloading") Words.PhoneUnloading else phoneDownloadPhase(phase)))
                                transfer?.let {
                                    LinearProgressIndicator(progress = { (it.bytes.toDouble() / it.total).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth())
                                }
                                TextButton(actions.pause, Modifier.testTag("phone-transfer-pause"),
                                    enabled = state.status == "downloading" && phase != LocalTransferPhase.INSTALLING) {
                                    Text(label(Words.PhoneDownloadPause))
                                }
                            } else if (model in installed && saved == 0L) {
                                Text(label(Words.PhoneCatalogueInstalled))
                            } else {
                                val canStart = supported && !state.busy && !state.downloadQueueUnavailable && model !in installed
                                Button({ if (reviewed) actions.resume(model) else review(model) }, enabled = canStart,
                                    modifier = Modifier.testTag("phone-transfer-start-${model.name}")) {
                                    Text(label(when {
                                        !reviewed -> Words.PhoneDownloadNeedsReview
                                        saved == model.bytes -> Words.PhoneDownloadVerifySaved
                                        saved > 0 -> Words.PhoneDownloadResume
                                        else -> Words.PhoneDownloadStart
                                    }))
                                }
                                TextButton({ discard(model) }, enabled = !state.busy && !state.downloadQueueUnavailable,
                                    modifier = Modifier.testTag("phone-transfer-discard-${model.name}")) {
                                    Text(label(if (saved > 0) Words.PhoneDiscard else Words.PhoneDownloadQueueRemove))
                                }
                            }
                        }
                    }
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-downloads-close")) {
                    Text(label(Words.Close))
                }
            }
        }
    }
}

internal fun phoneDownloadPhase(phase: LocalTransferPhase?) = when (phase) {
    null -> Words.PhoneDownloadPreparing
    LocalTransferPhase.DOWNLOADING -> Words.PhoneDownloading
    LocalTransferPhase.VERIFYING -> Words.PhoneVerifying
    LocalTransferPhase.CHECKING -> Words.PhoneDownloadChecking
    LocalTransferPhase.INSTALLING -> Words.PhoneDownloadInstalling
}
