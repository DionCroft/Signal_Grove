package org.hermesmobile.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.voice.*
import java.util.Locale
import java.util.UUID

@Composable fun VoiceScreen(vm: HermesViewModel,goChat: ()->Unit) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val p by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val systemAvailable=remember {SpeechRecognizer.isRecognitionAvailable(context)}
    val localAvailable=remember {Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}
    val mode=SpeechPolicy.recognition(p.onDeviceSpeechOnly,Build.VERSION.SDK_INT,localAvailable,systemAvailable)
    val recognizer=remember(mode) {
        runCatching {when(mode) {RecognitionMode.ON_DEVICE->if(Build.VERSION.SDK_INT>=31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else null
            RecognitionMode.SYSTEM->SpeechRecognizer.createSpeechRecognizer(context);else->null}}.getOrNull()
    }
    var language by rememberSaveable {mutableStateOf("en-GB")}
    var transcript by rememberSaveable {mutableStateOf("")}
    var state by remember {mutableStateOf("idle")}
    var failure by remember {mutableStateOf<Words?>(null)}
    val main=remember {Handler(Looper.getMainLooper())}
    var ttsReady by remember {mutableStateOf(false)}
    var speaking by remember {mutableStateOf(false)}
    var speechError by remember {mutableStateOf(false)}
    var utterancePrefix by remember {mutableStateOf("")}
    var lastUtterance by remember {mutableStateOf("")}
    val tts=remember {TextToSpeech(context) {result -> main.post {ttsReady=result==TextToSpeech.SUCCESS}}}
    val reply=messages.lastOrNull {it.role=="assistant" && it.content.isNotBlank()}?.content.orEmpty()
    val plainReply=remember(reply) {SpeechPolicy.plainText(reply)}
    fun cancelListening() {state="idle";recognizer?.cancel()}
    fun start() {
        if(recognizer==null || speaking || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        failure=null;state="listening"
        try {recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,p.onDeviceSpeechOnly)
        })} catch(_:Exception) {state="idle";failure=Words.VoiceError}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        if(!granted) {state="idle";failure=Words.SpeechPermission} else if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
    }
    DisposableEffect(recognizer,owner) {
        var active=true
        recognizer?.setRecognitionListener(object:RecognitionListener {
            override fun onReadyForSpeech(params:Bundle?) {if(active) state="listening"}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB:Float) {}
            override fun onBufferReceived(buffer:ByteArray?) {}
            override fun onEndOfSpeech() {if(active && state!="idle") state="processing"}
            override fun onError(error:Int) {if(active && state!="idle") {state="idle";failure=when(error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->Words.SpeechPermission
                SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT,SpeechRecognizer.ERROR_SERVER->Words.SpeechNetwork
                SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->Words.SpeechNoMatch
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY->Words.SpeechBusy
                else->Words.VoiceError
            }}}
            override fun onResults(results:Bundle?) {if(active && state!="idle") {transcript=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();state="idle"}}
            override fun onPartialResults(results:Bundle?) {if(active && state!="idle") results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {transcript=it}}
            override fun onEvent(eventType:Int,params:Bundle?) {}
        })
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) {cancelListening();utterancePrefix="stopped";tts.stop();speaking=false}}
        owner.lifecycle.addObserver(observer)
        onDispose {active=false;owner.lifecycle.removeObserver(observer);recognizer?.cancel();recognizer?.destroy()}
    }
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String?) {main.post {if(id?.startsWith(utterancePrefix)==true) speaking=true}}
            override fun onDone(id:String?) {main.post {if(id==lastUtterance) speaking=false}}
            @Deprecated("Android callback") override fun onError(id:String?) {main.post {if(id?.startsWith(utterancePrefix)==true) {tts.stop();speaking=false;speechError=true}}}
        })
        onDispose {utterancePrefix="disposed";tts.stop();tts.shutdown()}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Word(Words.Voice,Modifier.fillMaxWidth(),heading=true);Text(label(Words.VoiceInfo))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label(Words.LocalSpeech),Modifier.weight(1f));Switch(p.onDeviceSpeechOnly,{vm.updatePreferences(p.copy(onDeviceSpeechOnly=it))},enabled=state=="idle" && !speaking)
        }
        if(recognizer==null) Text(label(if(p.onDeviceSpeechOnly) Words.LocalSpeechUnavailable else Words.VoiceUnavailable))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FilterChip(language=="en-GB",{language="en-GB"},enabled=state=="idle" && !speaking,label={Text(label(Words.SpeechEnglish))})
            FilterChip(language=="zh-CN",{language="zh-CN"},enabled=state=="idle" && !speaking,label={Text("中文 · Zhōngwén")})
        }
        FilledTonalIconButton({if(state=="listening") {recognizer?.stopListening();state="processing"} else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) start() else permission.launch(Manifest.permission.RECORD_AUDIO)},
            enabled=recognizer!=null && state!="processing" && !speaking,modifier=Modifier.size(96.dp)) {Icon(Icons.Outlined.MicNone,label(Words.TapSpeak),Modifier.size(42.dp))}
        Text(label(when(state) {"listening"->Words.Listening;"processing"->Words.Processing;else->Words.TapSpeak}))
        if(state!="idle") TextButton(::cancelListening) {Text(label(Words.Cancel))}
        failure?.let {Text(label(it),color=MaterialTheme.colorScheme.error)}
        if(failure==Words.SpeechPermission) TextButton({context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}) {Text(label(Words.AppPermissions))}
        OutlinedTextField(transcript,{transcript=it},Modifier.fillMaxWidth(),label={Text(label(Words.Transcript))},minLines=2,maxLines=6)
        Button({cancelListening();vm.draft.value=transcript;goChat()},enabled=transcript.isNotBlank() && state=="idle" && !busy) {Text(label(Words.Chat))}
        if(!p.onDeviceSpeechOnly) Text(label(Words.VoiceDisclosure),style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label(Words.NetworkSpeech),Modifier.weight(1f));Switch(p.allowNetworkSpeech,{vm.updatePreferences(p.copy(allowNetworkSpeech=it))},enabled=!speaking)
        }
        Text(label(Words.SpeechRate)+" · "+"%.2f×".format(p.speechRate))
        Slider(p.speechRate,{vm.updatePreferences(p.copy(speechRate=it))},valueRange=.75f..1.25f,enabled=!speaking)
        Button({
            if(speaking) {utterancePrefix="stopped";tts.stop();speaking=false}
            else try {
                val locale=Locale.forLanguageTag(language)
                val voice=tts.voices.orEmpty().filter {it.locale.language==locale.language && (p.allowNetworkSpeech || !it.isNetworkConnectionRequired)}.sortedWith(compareBy({it.isNetworkConnectionRequired},{it.name})).firstOrNull()
                if(voice==null) {speechError=true} else {
                    speechError=false;tts.voice=voice;tts.setSpeechRate(p.speechRate)
                    val chunks=SpeechPolicy.chunks(plainReply,TextToSpeech.getMaxSpeechInputLength())
                    require(chunks.isNotEmpty());utterancePrefix=UUID.randomUUID().toString();lastUtterance="$utterancePrefix-${chunks.lastIndex}";speaking=true
                    chunks.forEachIndexed {index,text -> check(tts.speak(text,if(index==0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,"$utterancePrefix-$index")==TextToSpeech.SUCCESS)}
                }
            } catch(_:Exception) {tts.stop();speaking=false;speechError=true}
        },enabled=speaking || (ttsReady && plainReply.isNotBlank() && state=="idle" && !busy)) {Text(label(if(speaking) Words.StopSpeech else Words.ReadReply))}
        if(reply.isNotBlank() && plainReply.isBlank()) Text(label(Words.NoReadText),style=MaterialTheme.typography.bodySmall)
        if(speechError || !ttsReady) Text(label(Words.SpeechVoiceUnavailable),style=MaterialTheme.typography.bodySmall)
        Text(label(Words.SpeechDisclosure),style=MaterialTheme.typography.bodySmall)
    }
}
