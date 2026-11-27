package org.hermesmobile.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import org.hermesmobile.app.local.*

@Composable
internal fun MediaCaptureDialog(workspace: MediaCaptureWorkspace, state: MediaCaptureState) {
    val kind = state.kind ?: return
    val context = LocalContext.current
    val modelFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(workspace::install) }
    val screenshot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(workspace::screenshot) }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) workspace.record() else workspace.permissionDenied()
    }
    val title = label(if (kind == MediaCaptureKind.SCREENSHOT) Words.MediaScreenshot else Words.MediaRecording)
    val result = state.result
    Dialog(workspace::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(workspace::close, Modifier.testTag("media-close")) {
                        Icon(Icons.Outlined.Close, label(Words.Close))
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.error?.let { Text(label(mediaCaptureError(it)), color = MaterialTheme.colorScheme.error) }
                    if (state.error == "media_microphone_denied") {
                        TextButton({
                            val destination = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            runCatching { context.startActivity(Intent(destination, Uri.fromParts("package", context.packageName, null))) }
                        }) { Text(label(Words.Settings)) }
                    }
                    if (!state.installed) {
                        Text(label(Words.MediaEnglishPack))
                        SelectionContainer { Text(kind.fileName) }
                        Text(label(Words.MediaPackLicense))
                        val sizeMb = (kind.bytes + 500_000) / 1_000_000
                        Text(label(Words.MediaDownloadSource).replace("{size}", sizeMb.toString())
                            .replace("{publisher}", Uri.parse(kind.download).host.orEmpty()))
                        TextButton(workspace::downloadPack, enabled = state.work == null,
                            modifier = Modifier.testTag("media-download-model")) { Text(label(Words.MediaDownloadPack)) }
                        if (state.work == MediaCaptureWork.DOWNLOAD) Text(label(Words.MediaPackDownloading))
                        Button({ modelFile.launch(arrayOf("*/*")) }, enabled = state.work == null,
                            modifier = Modifier.testTag("media-import-model")) { Text(label(Words.MediaImportPack)) }
                    } else if (kind == MediaCaptureKind.SCREENSHOT) {
                        Text(label(Words.MediaImagePrivacy))
                        Button({ screenshot.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
                            enabled = state.work == null, modifier = Modifier.testTag("media-choose-screenshot")) {
                            Text(label(Words.MediaChooseImage))
                        }
                    } else {
                        Text(label(Words.MediaAudioPrivacy))
                        if (state.recordingReady) {
                            Text(label(Words.MediaRecordingReady))
                            Button(workspace::transcribe, enabled = state.work == null,
                                modifier = Modifier.testTag("media-transcribe")) { Text(label(Words.MediaTranscribe)) }
                            TextButton(workspace::discardRecording, enabled = state.work == null,
                                modifier = Modifier.testTag("media-discard")) { Text(label(Words.MediaDiscardRecording)) }
                        } else if (state.work == null) Button({
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED) workspace.record()
                            else microphone.launch(Manifest.permission.RECORD_AUDIO)
                        }, Modifier.testTag("media-record")) { Text(label(Words.MediaStartRecording)) }
                    }
                    if (result != null) {
                        Word(Words.MediaResult)
                        SelectionContainer { Text(result, Modifier.testTag("media-result")) }
                    }
                }
                if (state.work != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.work == MediaCaptureWork.RECORDING) {
                        Button(workspace::stopRecording, Modifier.fillMaxWidth().testTag("media-stop"), enabled = !state.cancelling) {
                            Text(label(Words.Stop))
                        }
                    }
                    TextButton(workspace::cancel, Modifier.fillMaxWidth().testTag("media-cancel"), enabled = !state.cancelling) {
                        Text(label(Words.Cancel))
                    }
                } else if (result != null) Button(workspace::reviewAsCapture, Modifier.fillMaxWidth().testTag("media-review-text")) {
                    Text(label(Words.MediaReviewText))
                }
            }
        }
    }
}

private fun mediaCaptureError(reason: String) = when (reason) {
    "media_model_checksum" -> Words.MediaPackInvalid
    "media_image_invalid" -> Words.MediaImageInvalid
    "media_microphone_denied" -> Words.MediaMicDenied
    "media_microphone_unavailable" -> Words.MediaMicUnavailable
    "media_audio_empty" -> Words.MediaRecordingEmpty
    "media_no_text" -> Words.MediaNoText
    "media_text_limit" -> Words.MediaTextLimit
    "media_download_http" -> Words.MediaPackDownloadFailed
    "download_unmetered_required", "download_network_changed",
    "download_network_proxy", "download_network_settings" -> phoneError(reason)
    else -> Words.MediaFailed
}
