package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.*

@Composable
internal fun PhoneVoiceDialog(
    vm: LocalAiViewModel,
    target: PhoneVoiceDraft,
    state: LocalAiState,
    draft: String,
    preferences: AppPreferences,
    onPreferences: (AppPreferences) -> Unit
) {
    val current = target.current(state, draft)
    Dialog(onDismissRequest = vm::closePhoneVoice, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label(Words.PhoneVoice), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(vm::closePhoneVoice, Modifier.testTag("phone-voice-close")) { Text(label(Words.Close)) }
                }
                VoiceSessionPanel(preferences, state.busy || !current, target.reply.takeIf { current }.orEmpty(),
                    onPreferences, onApply = { vm.applyPhoneVoice(target, it) },
                    phoneDraftBlock = { transcript ->
                        when {
                            !current -> Words.PhoneVoiceDraftChanged
                            transcript.isNotBlank() && appendPhoneTranscript(draft, transcript) == null -> Words.PhoneVoiceDraftLimit
                            else -> null
                        }
                    })
            }
        }
    }
}
