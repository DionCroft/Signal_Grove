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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.voice.*
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Composable fun VoiceScreen(vm:HermesViewModel,goChat:()->Unit) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val p by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    fun availability()=Pair(
        runCatching {Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}.getOrDefault(false),
        runCatching {SpeechRecognizer.isRecognitionAvailable(context)}.getOrDefault(false)
    )
    var available by remember {mutableStateOf(availability())}
    var refreshGeneration by remember {mutableIntStateOf(0)}
    val mode=SpeechPolicy.recognition(p.onDeviceSpeechOnly,Build.VERSION.SDK_INT,available.first,available.second)
    var language by rememberSaveable {mutableStateOf("en-GB")}
    var transcript by rememberSaveable {mutableStateOf("")}
    var readAloud by rememberSaveable {mutableStateOf(false)}
    var session by remember {mutableStateOf(DictationSession(mode,language))}
    var recognizer by remember {mutableStateOf<SpeechRecognizer?>(null)}
    var permissionRequest by remember {mutableStateOf<DictationSession?>(null)}
    val main=remember {Handler(Looper.getMainLooper())}
    val alive=remember(refreshGeneration) {AtomicBoolean(true)}
    var ttsStatus by remember(refreshGeneration) {mutableIntStateOf(0)} // 0 initializing, 1 ready, -1 unavailable
    var speaking by remember {mutableStateOf(false)}
    var speechError by remember {mutableStateOf<Words?>(null)}
    var utterancePrefix by remember {mutableStateOf("")}
    var lastUtterance by remember {mutableStateOf("")}
    val tts=remember(refreshGeneration) {TextToSpeech(context) {result->main.post {if(alive.get()) ttsStatus=if(result==TextToSpeech.SUCCESS) 1 else -1}}}
    val reply=messages.lastOrNull {it.role=="assistant" && it.content.isNotBlank()}?.content.orEmpty()
    val plainReply=remember(reply) {SpeechPolicy.plainText(reply)}
    val voice=remember(ttsStatus,refreshGeneration,language,p.allowNetworkSpeech) {
        if(ttsStatus!=1) null else runCatching {
            val locale=Locale.forLanguageTag(language)
            tts.voices.orEmpty().filter {
                it.locale.language==locale.language && (p.allowNetworkSpeech || !it.isNetworkConnectionRequired) &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
            }.sortedWith(compareBy({it.isNetworkConnectionRequired},{it.name})).firstOrNull()
        }.getOrNull()
    }
    fun release(target:SpeechRecognizer?) {
        if(recognizer===target) recognizer=null
        target?.let {runCatching {it.cancel()};runCatching {it.destroy()}}
    }
    fun cancelListening(clearFailure:Boolean=false) {
        session=session.cancel(clearFailure);permissionRequest=null;release(recognizer)
    }
    fun stopSpeaking() {utterancePrefix="";lastUtterance="";speaking=false;runCatching {tts.stop()}}
    fun refresh(clearFailure:Boolean) {
        val next=availability()
        if(clearFailure || next!=available) cancelListening(clearFailure=true)
        available=next;refreshGeneration++;speechError=null
    }
    fun start() {
        if(mode==RecognitionMode.UNAVAILABLE || speaking || readAloud || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        session=session.configure(mode,language)
        if(session.phase!=DictationPhase.IDLE) return
        release(recognizer)
        session=session.begin();val token=session.generation
        try {
            val created=when(mode) {
                RecognitionMode.ON_DEVICE->if(Build.VERSION.SDK_INT>=31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else error("On-device service unavailable")
                RecognitionMode.SYSTEM->SpeechRecognizer.createSpeechRecognizer(context)
                RecognitionMode.UNAVAILABLE->return
            }
            recognizer=created
            created.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB:Float) {}
                override fun onBufferReceived(buffer:ByteArray?) {}
                override fun onEndOfSpeech() {session=session.processing(token)}
                override fun onError(error:Int) {
                    if(session.accepts(token)) {session=session.fail(token,RecognitionFailure.fromAndroid(error));release(created)}
                }
                override fun onResults(results:Bundle?) {
                    if(session.accepts(token)) {
                        val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if(text.isBlank()) session=session.fail(token,RecognitionFailure.NO_MATCH)
                        else {transcript=text;session=session.finish(token)}
                        release(created)
                    }
                }
                override fun onPartialResults(results:Bundle?) {if(session.accepts(token)) results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {transcript=it}}
                override fun onEvent(eventType:Int,params:Bundle?) {}
            })
            created.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,p.onDeviceSpeechOnly)
            })
        } catch(error:Exception) {
            val reason=when(error) {is SecurityException->RecognitionFailure.PERMISSION;is UnsupportedOperationException->RecognitionFailure.SERVICE;else->RecognitionFailure.CLIENT}
            session=session.fail(token,reason);release(recognizer)
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        val requested=permissionRequest;permissionRequest=null
        if(requested!=null && requested==session && requested.mode==mode && requested.language==language && !readAloud && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if(granted) start() else session=session.copy(failure=RecognitionFailure.PERMISSION)
        }
    }
    LaunchedEffect(mode,language) {
        cancelListening(clearFailure=true);session=session.configure(mode,language);speechError=null
    }
    val resume by rememberUpdatedState({refresh(false)})
    val stop by rememberUpdatedState({cancelListening();stopSpeaking()})
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,event->when(event) {Lifecycle.Event.ON_RESUME->resume();Lifecycle.Event.ON_STOP->stop();else->Unit}}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);stop()}
    }
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String?) {main.post {if(alive.get() && utterancePrefix.isNotEmpty() && id?.startsWith("$utterancePrefix-")==true) speaking=true}}
            override fun onDone(id:String?) {main.post {if(alive.get() && id==lastUtterance) speaking=false}}
            @Deprecated("Android callback") override fun onError(id:String?) {main.post {
                if(alive.get() && utterancePrefix.isNotEmpty() && id?.startsWith("$utterancePrefix-")==true) {stopSpeaking();speechError=Words.SpeechPlaybackError}
            }}
        })
        onDispose {alive.set(false);stopSpeaking();tts.shutdown()}
    }
    val readBlock=when {
        speaking->null
        reply.isBlank()->Words.SpeechReplyRequired
        plainReply.isBlank()->Words.NoReadText
        busy->Words.SpeechChatBusy
        session.phase!=DictationPhase.IDLE->Words.SpeechListeningBusy
        ttsStatus==0->Words.SpeechStarting
        ttsStatus<0->Words.SpeechEngineUnavailable
        voice==null->Words.SpeechVoiceUnavailable
        else->null
    }
    VoiceControls(
        preferences=p,readAloud=readAloud,onSection={selected->cancelListening(clearFailure=true);stopSpeaking();speechError=null;readAloud=selected},
        session=session,transcript=transcript,onTranscript={transcript=it},recognitionAvailable=mode!=RecognitionMode.UNAVAILABLE,
        busy=busy,speaking=speaking,readBlock=readBlock,readError=speechError,
        onLanguage={cancelListening(clearFailure=true);language=it},onLocalOnly={cancelListening(clearFailure=true);vm.updatePreferences(p.copy(onDeviceSpeechOnly=it),p)},
        onNetworkVoices={speechError=null;vm.updatePreferences(p.copy(allowNetworkSpeech=it),p)},onRate={vm.updatePreferences(p.copy(speechRate=it),p)},
        onMicrophone={
            if(session.phase==DictationPhase.LISTENING) {session=session.processing(session.generation);runCatching {recognizer?.stopListening()}.onFailure {session=session.fail(session.generation,RecognitionFailure.CLIENT);release(recognizer)}}
            else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) start()
            else {session=session.configure(mode,language).cancel(clearFailure=true);permissionRequest=session;permission.launch(Manifest.permission.RECORD_AUDIO)}
        },
        onCancel={cancelListening()},onChat={cancelListening();vm.draft.value=transcript;goChat()},
        onRead={
            if(speaking) stopSpeaking()
            else if(readBlock==null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) try {
                check(voice!=null);check(tts.setVoice(voice)==TextToSpeech.SUCCESS);check(tts.setSpeechRate(p.speechRate)==TextToSpeech.SUCCESS)
                speechError=null
                val chunks=SpeechPolicy.chunks(plainReply,TextToSpeech.getMaxSpeechInputLength());require(chunks.isNotEmpty())
                utterancePrefix=UUID.randomUUID().toString();lastUtterance="$utterancePrefix-${chunks.lastIndex}";speaking=true
                chunks.forEachIndexed {index,text->check(tts.speak(text,if(index==0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,"$utterancePrefix-$index")==TextToSpeech.SUCCESS)}
            } catch(_:Exception) {stopSpeaking();speechError=Words.SpeechPlaybackError}
        },
        onRefresh={refresh(true)},onPermissions={runCatching {context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}}
    )
}
