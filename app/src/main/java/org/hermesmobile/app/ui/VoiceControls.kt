package org.hermesmobile.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.voice.*

internal fun RecognitionFailure.word()=when(this) {
    RecognitionFailure.PERMISSION->Words.SpeechPermission
    RecognitionFailure.NETWORK->Words.SpeechNetwork
    RecognitionFailure.NO_MATCH->Words.SpeechNoMatch
    RecognitionFailure.BUSY->Words.SpeechBusy
    RecognitionFailure.AUDIO->Words.SpeechAudioError
    RecognitionFailure.LANGUAGE_UNSUPPORTED->Words.SpeechLanguageUnsupported
    RecognitionFailure.LANGUAGE_UNAVAILABLE->Words.SpeechLanguageUnavailable
    RecognitionFailure.SERVICE->Words.SpeechServiceError
    RecognitionFailure.CLIENT->Words.VoiceError
}

/** Presentation only: reading text never requires the microphone or an available recognizer. */
@Composable internal fun VoiceControls(
    preferences:AppPreferences,readAloud:Boolean,onSection:(Boolean)->Unit,
    session:DictationSession,transcript:String,onTranscript:(String)->Unit,
    recognitionAvailable:Boolean,busy:Boolean,speaking:Boolean,readBlock:Words?,readError:Words?,
    onLanguage:(String)->Unit,onLocalOnly:(Boolean)->Unit,onNetworkVoices:(Boolean)->Unit,onRate:(Float)->Unit,
    onMicrophone:()->Unit,onCancel:()->Unit,onChat:()->Unit,onRead:()->Unit,onRefresh:()->Unit,onPermissions:()->Unit
) {
    val idle=session.phase==DictationPhase.IDLE
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Word(Words.Voice,Modifier.fillMaxWidth(),heading=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!readAloud,{onSection(false)},modifier=Modifier.weight(1f).testTag("voice-dictation-tab"),label={Text(label(Words.Dictation))})
            FilterChip(readAloud,{onSection(true)},modifier=Modifier.weight(1f).testTag("voice-read-tab"),label={Text(label(Words.ReadAloud))})
        }
        key(readAloud) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("voice-controls").padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(label(if(readAloud) Words.ReadAloudInfo else Words.VoiceInfo))
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FilterChip(session.language=="en-GB",{onLanguage("en-GB")},enabled=idle && !speaking,label={Text(label(Words.SpeechEnglish))})
                    FilterChip(session.language=="zh-CN",{onLanguage("zh-CN")},enabled=idle && !speaking,label={Text(label(Words.SpeechChinese))})
                }
                if(!readAloud) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(label(Words.LocalSpeech),Modifier.weight(1f))
                        Switch(preferences.onDeviceSpeechOnly,onLocalOnly,Modifier.testTag("voice-local-only"),enabled=idle && !speaking)
                    }
                    if(!recognitionAvailable) Text(label(if(preferences.onDeviceSpeechOnly) Words.LocalSpeechUnavailable else Words.VoiceUnavailable),Modifier.testTag("voice-recognition-unavailable"))
                    FilledTonalIconButton(onMicrophone,enabled=recognitionAvailable && session.phase!=DictationPhase.PROCESSING && !speaking,modifier=Modifier.size(96.dp).testTag("voice-microphone")) {
                        Icon(Icons.Outlined.MicNone,label(Words.TapSpeak),Modifier.size(42.dp))
                    }
                    Text(label(when(session.phase) {DictationPhase.LISTENING->Words.Listening;DictationPhase.PROCESSING->Words.Processing;else->Words.TapSpeak}))
                    if(!idle) TextButton(onCancel,Modifier.testTag("voice-cancel")) {Text(label(Words.Cancel))}
                    session.failure?.let {Text(label(it.word()),Modifier.testTag("voice-dictation-error"),color=MaterialTheme.colorScheme.error)}
                    if(session.failure==RecognitionFailure.PERMISSION) TextButton(onPermissions) {Text(label(Words.AppPermissions))}
                    OutlinedTextField(transcript,onTranscript,Modifier.fillMaxWidth(),label={Text(label(Words.Transcript))},minLines=2,maxLines=6)
                    Button(onChat,enabled=transcript.isNotBlank() && idle && !busy) {Text(label(Words.Chat))}
                    if(busy) Text(label(Words.SpeechChatBusy))
                    if(!preferences.onDeviceSpeechOnly) Text(label(Words.VoiceDisclosure),style=MaterialTheme.typography.bodySmall)
                } else {
                    Button(onRead,Modifier.testTag("voice-read-reply"),enabled=speaking || readBlock==null) {Text(label(if(speaking) Words.StopSpeech else Words.ReadReply))}
                    readBlock?.let {Text(label(it),Modifier.testTag("voice-read-reason"))}
                    readError?.let {Text(label(it),Modifier.testTag("voice-read-error"),color=MaterialTheme.colorScheme.error)}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(label(Words.NetworkSpeech),Modifier.weight(1f))
                        Switch(preferences.allowNetworkSpeech,onNetworkVoices,Modifier.testTag("voice-network-voices"),enabled=!speaking)
                    }
                    Text(label(Words.SpeechRate)+" · "+"%.2f×".format(preferences.speechRate))
                    Slider(preferences.speechRate,onRate,valueRange=.75f..1.25f,enabled=!speaking)
                    Text(label(Words.SpeechDisclosure),style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onRefresh,Modifier.testTag("voice-refresh"),enabled=idle && !speaking) {Text(label(Words.RefreshSpeech))}
            }
        }
    }
}
