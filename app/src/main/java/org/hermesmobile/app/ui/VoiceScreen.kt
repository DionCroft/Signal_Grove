package org.hermesmobile.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun VoiceScreen(vm: HermesViewModel, goChat: ()->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val available=remember {SpeechRecognizer.isRecognitionAvailable(context)}
    val recognizer=remember {if(available) SpeechRecognizer.createSpeechRecognizer(context) else null}
    var language by rememberSaveable {mutableStateOf("en-GB")}
    var transcript by rememberSaveable {mutableStateOf("")}
    var state by remember {mutableStateOf("idle")}
    var failed by remember {mutableStateOf(false)}
    val busy by vm.busy.collectAsStateWithLifecycle()
    fun start() {
        if(recognizer==null) return
        failed=false;state="listening"
        try {recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
        })} catch(e:Exception) {state="idle";failed=true}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted -> if(granted && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start() else {failed=true;state="idle"}}
    DisposableEffect(recognizer,owner) {
        recognizer?.setRecognitionListener(object: RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {state="listening"}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {state="processing"}
            override fun onError(error: Int) {state="idle";failed=true}
            override fun onResults(results: Bundle?) {transcript=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();state="idle"}
            override fun onPartialResults(results: Bundle?) {results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {transcript=it}}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) {recognizer?.cancel();state="idle"} }
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);recognizer?.cancel();recognizer?.destroy()}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Word(Words.Voice,Modifier.fillMaxWidth(),heading=true)
        Text(label(Words.VoiceInfo))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FilterChip(language=="en-GB",{language="en-GB"},enabled=state=="idle",label={Text("English")})
            FilterChip(language=="zh-CN",{language="zh-CN"},enabled=state=="idle",label={Text("中文 · Zhōngwén")})
        }
        FilledTonalIconButton(onClick={
            if(state=="listening") {recognizer?.stopListening();state="processing"}
            else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) start()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        },enabled=available && state!="processing",modifier=Modifier.size(112.dp)) {Icon(Icons.Outlined.MicNone,label(Words.TapSpeak),Modifier.size(48.dp))}
        Text(label(when(state) {"listening"->Words.Listening;"processing"->Words.Processing;else->Words.TapSpeak}))
        if(!available) Text(label(Words.VoiceUnavailable))
        if(failed) Text(label(Words.VoiceError),color=MaterialTheme.colorScheme.error)
        OutlinedTextField(transcript,{transcript=it},Modifier.fillMaxWidth(),label={Text(label(Words.Transcript))},minLines=3,maxLines=8)
        Button({recognizer?.cancel();state="idle";vm.draft.value=transcript;goChat()},enabled=transcript.isNotBlank() && state=="idle" && !busy) {Text(label(Words.Chat))}
        Text(label(Words.VoiceDisclosure),style=MaterialTheme.typography.bodySmall)
    }
}
