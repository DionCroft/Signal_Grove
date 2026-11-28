package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.voice.KokoroSpeaker

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KokoroReadAloudControls(
    supported: Boolean,
    installed: Boolean,
    selected: Boolean,
    speaker: KokoroSpeaker,
    speaking: Boolean,
    changing: Boolean,
    progress: Long,
    error: Words?,
    onSelect: (Boolean) -> Unit,
    onVoice: (KokoroSpeaker) -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onCancelDownload: () -> Unit,
    onPreview: () -> Unit
) {
    if (!supported) return
    var review by remember { mutableStateOf<Words?>(null) }
    Column(Modifier.fillMaxWidth().testTag("kokoro-controls"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label(Words.KokoroTitle), style = MaterialTheme.typography.titleMedium)
        if (installed) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!selected, { onSelect(false) }, enabled = !speaking && !changing,
                    label = { Text(label(Words.KokoroAndroidVoice)) })
                FilterChip(selected, { onSelect(true) }, enabled = !speaking && !changing,
                    modifier = Modifier.testTag("kokoro-select"), label = { Text(label(Words.KokoroLocalVoice)) })
            }
            if (selected) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KokoroSpeaker.entries.filter { it.language == speaker.language }.forEach { option ->
                        FilterChip(option == speaker, { onVoice(option) }, enabled = !speaking && !changing,
                            label = { Text(label(when (option) {
                                KokoroSpeaker.MAPLE -> Words.KokoroMaple
                                KokoroSpeaker.SOL -> Words.KokoroSol
                                KokoroSpeaker.MANDARIN_FEMALE -> Words.KokoroMandarinFemale
                                KokoroSpeaker.MANDARIN_MALE -> Words.KokoroMandarinMale
                            })) })
                    }
                }
                TextButton(onPreview, Modifier.testTag("kokoro-preview"), enabled = !speaking && !changing) {
                    Text(label(Words.KokoroPreview))
                }
            }
            TextButton({ review = Words.KokoroRemove }, enabled = !speaking && !changing) {
                Text(label(Words.KokoroRemove))
            }
        } else if (changing) {
            Text(label(if (progress == 100L) Words.KokoroVerifying else Words.KokoroDownloading))
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            TextButton(onCancelDownload) { Text(label(Words.Cancel)) }
        } else {
            Text(label(Words.KokoroPackInfo))
            TextButton({ review = Words.KokoroDownload }, Modifier.testTag("kokoro-download"), enabled = !speaking) {
                Text(label(Words.KokoroDownload))
            }
        }
        error?.let { Text(label(it), color = MaterialTheme.colorScheme.error) }
    }
    review?.let { action ->
        AlertDialog(
            onDismissRequest = { review = null },
            title = { Text(label(action)) },
            text = { Text(label(if (action == Words.KokoroDownload) Words.KokoroDownloadReview else Words.KokoroRemoveReview)) },
            confirmButton = {
                TextButton({
                    review = null
                    if (action == Words.KokoroDownload) onDownload() else onRemove()
                }, Modifier.testTag("kokoro-confirm")) { Text(label(action)) }
            },
            dismissButton = { TextButton({ review = null }) { Text(label(Words.Cancel)) } }
        )
    }
}
