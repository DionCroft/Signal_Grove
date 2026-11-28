package org.hermesmobile.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import kotlinx.coroutines.*
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.voice.*
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Composable fun VoiceScreen(vm:HermesViewModel,goChat:()->Unit) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    VoiceSessionPanel(preferences, busy,
        messages.lastOrNull { it.role == "assistant" && it.content.isNotBlank() }?.content.orEmpty(),
        onPreferences = { vm.updatePreferences(it, preferences) },
        onApply = { vm.changeDraft(it); goChat() })
}

@Composable
internal fun VoiceSessionPanel(
    preferences: AppPreferences,
    busy: Boolean,
    reply: String,
    onPreferences: (AppPreferences) -> Unit,
    onApply: (String) -> Unit,
    phoneDraftBlock: ((String) -> Words?)? = null
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val phoneOnly = phoneDraftBlock != null
    val p = if (phoneOnly) preferences.copy(onDeviceSpeechOnly = true, allowNetworkSpeech = false) else preferences
    fun availability()=Pair(
        runCatching {Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}.getOrDefault(false),
        !phoneOnly && runCatching {SpeechRecognizer.isRecognitionAvailable(context)}.getOrDefault(false)
    )
    var available by remember {mutableStateOf(availability())}
    var refreshGeneration by remember {mutableIntStateOf(0)}
    val mode = if (phoneOnly) SpeechPolicy.phoneRecognition(Build.VERSION.SDK_INT, available.first)
        else SpeechPolicy.recognition(p.onDeviceSpeechOnly, Build.VERSION.SDK_INT, available.first, available.second)
    var language by rememberSaveable {mutableStateOf("en-GB")}
    var transcript by if (phoneOnly) remember { mutableStateOf("") } else rememberSaveable {mutableStateOf("")}
    var readAloud by rememberSaveable {mutableStateOf(false)}
    var session by remember {mutableStateOf(DictationSession(mode,language))}
    var recognizer by remember {mutableStateOf<SpeechRecognizer?>(null)}
    var permissionRequest by remember {mutableStateOf<DictationSession?>(null)}
    var grantedPermission by remember { mutableStateOf<DictationSession?>(null) }
    var checkingLanguage by remember { mutableStateOf(false) }
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val main=remember {Handler(Looper.getMainLooper())}
    val scope = rememberCoroutineScope()
    val kokoroAvailable = remember { KokoroNative.supported }
    val pack = remember(context) { KokoroVoicePack(context.applicationContext) }
    var packInstalled by remember { mutableStateOf(false) }
    var kokoroJob by remember { mutableStateOf<Job?>(null) }
    var packJob by remember { mutableStateOf<Job?>(null) }
    var packMessage by remember { mutableStateOf<Words?>(null) }
    var packBytes by remember { mutableLongStateOf(0L) }
    val useKokoro = p.preferKokoro && packInstalled
    val speaker = KokoroSpeaker.forLanguage(language, if (language == "zh-CN") p.kokoroMandarin else p.kokoroEnglish)
    LaunchedEffect(kokoroAvailable, readAloud, foreground) {
        if (kokoroAvailable && readAloud && foreground) packInstalled = pack.isInstalled()
    }
    val engineNeeded = !phoneOnly || (foreground && readAloud)
    val alive=remember(refreshGeneration, engineNeeded) {AtomicBoolean(true)}
    var ttsStatus by remember(refreshGeneration, engineNeeded) {mutableIntStateOf(0)} // 0 initializing, 1 ready, -1 unavailable
    var speaking by remember {mutableStateOf(false)}
    var speechError by remember {mutableStateOf<Words?>(null)}
    var utterancePrefix by remember {mutableStateOf("")}
    var lastUtterance by remember {mutableStateOf("")}
    val tts = remember(refreshGeneration, engineNeeded) {
        if (!engineNeeded) null else TextToSpeech(context) { result ->
            main.post { if (alive.get()) ttsStatus = if (result == TextToSpeech.SUCCESS) 1 else -1 }
        }
    }
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val speechAttributes = remember {
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    }
    val kokoroPlayer = remember(pack, speechAttributes) { KokoroPlayback(pack, speechAttributes) }
    var audioFocus by remember { mutableStateOf<AudioFocusRequest?>(null) }
    var draftError by remember { mutableStateOf<Words?>(null) }
    val plainReply=remember(reply) {SpeechPolicy.plainText(reply)}
    val voice=remember(tts,ttsStatus,language,p.allowNetworkSpeech) {
        if(ttsStatus!=1) null else runCatching {
            val locale=Locale.forLanguageTag(language)
            tts?.voices.orEmpty().filter {
                it.locale.language==locale.language && (p.allowNetworkSpeech || !it.isNetworkConnectionRequired) &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
            }.sortedWith(compareBy({it.isNetworkConnectionRequired},{it.name})).firstOrNull()
        }.getOrNull()
    }
    fun release(target:SpeechRecognizer?) {
        if(recognizer===target) {
            recognizer=null
            checkingLanguage=false
        }
        target?.let {runCatching {it.cancel()};runCatching {it.destroy()}}
    }
    fun cancelListening(clearFailure:Boolean=false) {
        session=session.cancel(clearFailure);permissionRequest=null;grantedPermission=null;release(recognizer)
    }
    fun stopSpeaking() {
        kokoroJob?.cancel()
        kokoroJob = null
        kokoroPlayer.stop()
        utterancePrefix="";lastUtterance="";speaking=false
        runCatching { tts?.stop() }
        val previous = audioFocus
        audioFocus = null
        previous?.let { runCatching { audio?.abandonAudioFocusRequest(it) } }
    }
    fun requestFocus(): Boolean {
        val manager = audio ?: return false
        lateinit var request: AudioFocusRequest
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0 && audioFocus === request) {
                    stopSpeaking()
                    speechError = Words.PhoneSpeechInterrupted
                }
            }, main)
            .build()
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        audioFocus = request
        return true
    }
    fun playKokoro(message: String) {
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !useKokoro || speaking || packJob != null) return
        speechError = null
        if (!requestFocus()) {
            speechError = Words.SpeechPlaybackError
            return
        }
        speaking = true
        kokoroJob = scope.launch {
            try {
                kokoroPlayer.speak(message, speaker, p.speechRate, coroutineContext.job)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                speechError = Words.KokoroPlaybackFailed
                packInstalled = pack.isInstalled()
            } catch (_: LinkageError) {
                speechError = Words.KokoroPlaybackFailed
            } finally {
                if (kokoroJob === coroutineContext.job) stopSpeaking()
            }
        }
    }
    fun changePack(remove: Boolean) {
        if (packJob != null || speaking || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        packMessage = null
        packBytes = 0
        packJob = scope.launch {
            try {
                if (remove) pack.remove()
                else pack.install { bytes ->
                    // Publish percentage steps only, instead of a recomposition for every read.
                    val percent = bytes * 100 / KokoroVoicePack.DOWNLOAD_BYTES
                    if (percent != packBytes) packBytes = percent
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                packMessage = if (remove) Words.KokoroRemoveFailed else Words.KokoroDownloadFailed
            } finally {
                // An install may have finished immediately before cancellation.
                packInstalled = withContext(NonCancellable) { pack.isInstalled() }
                packJob = null
            }
        }
    }
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
                        else {
                            if (!phoneOnly || text.length <= 8192) {
                                transcript=text
                                draftError=null
                            } else draftError=Words.PhoneVoiceDraftLimit
                            session=session.finish(token)
                        }
                        release(created)
                    }
                }
                override fun onPartialResults(results:Bundle?) {
                    if (!phoneOnly && session.accepts(token)) results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { transcript=it }
                }
                override fun onEvent(eventType:Int,params:Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,!phoneOnly)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,p.onDeviceSpeechOnly)
            }
            if (phoneOnly && Build.VERSION.SDK_INT >= 33) {
                checkingLanguage = true
                fun stillChecking() = checkingLanguage && recognizer === created && session.accepts(token)
                fun unavailable(reason: RecognitionFailure) {
                    if (stillChecking()) {
                        session=session.fail(token,reason)
                        release(created)
                    }
                }
                created.checkRecognitionSupport(intent, ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
                    override fun onSupportResult(result: RecognitionSupport) {
                        if (!stillChecking()) return
                        val installed = SpeechPolicy.installedLanguage(language, result.installedOnDeviceLanguages)
                        if (installed == null || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            unavailable(RecognitionFailure.LANGUAGE_UNAVAILABLE)
                            return
                        }
                        checkingLanguage=false
                        try {
                            created.startListening(intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, installed))
                        } catch (error: Exception) {
                            session=session.fail(token, if (error is SecurityException) RecognitionFailure.PERMISSION else RecognitionFailure.CLIENT)
                            release(created)
                        }
                    }
                    override fun onError(error: Int) = unavailable(RecognitionFailure.LANGUAGE_UNAVAILABLE)
                })
                main.postDelayed({ unavailable(RecognitionFailure.LANGUAGE_UNAVAILABLE) }, 5000)
            } else created.startListening(intent)
        } catch(error:Exception) {
            val reason=when(error) {is SecurityException->RecognitionFailure.PERMISSION;is UnsupportedOperationException->RecognitionFailure.SERVICE;else->RecognitionFailure.CLIENT}
            session=session.fail(token,reason);release(recognizer)
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        val requested=permissionRequest;permissionRequest=null
        if(requested!=null && requested==session && requested.mode==mode && requested.language==language && !readAloud) {
            if (!granted) session=session.copy(failure=RecognitionFailure.PERMISSION)
            else if(phoneOnly) grantedPermission=requested
            else if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        }
    }
    LaunchedEffect(grantedPermission, foreground) {
        val granted=grantedPermission
        if(phoneOnly && foreground && granted!=null) {
            grantedPermission=null
            if(granted==session && !readAloud) start()
        }
    }
    LaunchedEffect(mode,language) {
        cancelListening(clearFailure=true);session=session.configure(mode,language);speechError=null
    }
    val resume by rememberUpdatedState({refresh(false)})
    val stop by rememberUpdatedState({packJob?.cancel();cancelListening();stopSpeaking()})
    val stopOnPause by rememberUpdatedState({
        packJob?.cancel()
        if (useKokoro || phoneOnly) stopSpeaking()
    })
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,event->when(event) {
            Lifecycle.Event.ON_RESUME -> { foreground=true;resume() }
            Lifecycle.Event.ON_PAUSE -> {
                foreground=false
                stopOnPause()
                if (phoneOnly && permissionRequest==null && grantedPermission==null) cancelListening()
            }
            Lifecycle.Event.ON_STOP -> stop()
            else -> Unit
        }}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);stop()}
    }
    DisposableEffect(tts) {
        tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String?) {main.post {if(alive.get() && utterancePrefix.isNotEmpty() && id?.startsWith("$utterancePrefix-")==true) speaking=true}}
            override fun onDone(id:String?) {main.post {if(alive.get() && id==lastUtterance) stopSpeaking()}}
            @Deprecated("Android callback") override fun onError(id:String?) {main.post {
                if(alive.get() && utterancePrefix.isNotEmpty() && id?.startsWith("$utterancePrefix-")==true) {stopSpeaking();speechError=Words.SpeechPlaybackError}
            }}
        })
        onDispose {alive.set(false);stopSpeaking();tts?.shutdown()}
    }
    LaunchedEffect(reply, busy) { if((phoneOnly || useKokoro) && speaking) stopSpeaking() }
    val noisy by rememberUpdatedState({ stopSpeaking(); speechError=Words.PhoneSpeechInterrupted })
    DisposableEffect(phoneOnly, useKokoro, speaking) {
        if ((!phoneOnly && !useKokoro) || !speaking) onDispose {}
        else {
            val receiver=object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && speaking) noisy()
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose { context.unregisterReceiver(receiver) }
        }
    }
    val readBlock=when {
        speaking->null
        reply.isBlank()->if(phoneOnly) Words.PhoneReadReplyRequired else Words.SpeechReplyRequired
        plainReply.isBlank()->Words.NoReadText
        busy->Words.SpeechChatBusy
        session.phase!=DictationPhase.IDLE->Words.SpeechListeningBusy
        packJob != null && useKokoro -> Words.KokoroPackBusy
        useKokoro->null
        ttsStatus==0->Words.SpeechStarting
        ttsStatus<0->Words.SpeechEngineUnavailable
        voice==null->Words.SpeechVoiceUnavailable
        else->null
    }
    VoiceControls(
        preferences=p,readAloud=readAloud,onSection={selected->if (!selected) packJob?.cancel();cancelListening(clearFailure=true);stopSpeaking();speechError=null;readAloud=selected},
        session=session,transcript=transcript,onTranscript={
            if (!phoneOnly || it.length <= 8192) { transcript=it;draftError=null }
            else draftError=Words.PhoneVoiceDraftLimit
        },recognitionAvailable=mode!=RecognitionMode.UNAVAILABLE,
        busy=busy,speaking=speaking,readBlock=readBlock,readError=speechError,
        onLanguage={cancelListening(clearFailure=true);language=it},onLocalOnly={cancelListening(clearFailure=true);onPreferences(p.copy(onDeviceSpeechOnly=it))},
        onNetworkVoices={speechError=null;onPreferences(p.copy(allowNetworkSpeech=it))},
        onRate={onPreferences(preferences.copy(speechRate=it))},
        onMicrophone={
            if(session.phase==DictationPhase.LISTENING) {session=session.processing(session.generation);runCatching {recognizer?.stopListening()}.onFailure {session=session.fail(session.generation,RecognitionFailure.CLIENT);release(recognizer)}}
            else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) start()
            else {session=session.configure(mode,language).cancel(clearFailure=true);permissionRequest=session;permission.launch(Manifest.permission.RECORD_AUDIO)}
        },
        onCancel={cancelListening()},onChat={
            if(phoneDraftBlock?.invoke(transcript)==null) { cancelListening();onApply(transcript) }
        },
        onRead={
            if(speaking) stopSpeaking()
            else if(readBlock==null && useKokoro) playKokoro(plainReply)
            else if(readBlock==null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) try {
                check(voice!=null);checkNotNull(tts)
                if (phoneOnly) {
                    check(tts.setAudioAttributes(speechAttributes)==TextToSpeech.SUCCESS)
                    check(requestFocus())
                }
                check(tts.setVoice(voice)==TextToSpeech.SUCCESS)
                if(phoneOnly) {
                    val active = checkNotNull(tts.voice)
                    check(active.name == voice.name && !active.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in active.features.orEmpty())
                }
                check(tts.setSpeechRate(p.speechRate)==TextToSpeech.SUCCESS)
                speechError=null
                val chunks=SpeechPolicy.chunks(plainReply,TextToSpeech.getMaxSpeechInputLength());require(chunks.isNotEmpty())
                utterancePrefix=UUID.randomUUID().toString();lastUtterance="$utterancePrefix-${chunks.lastIndex}";speaking=true
                chunks.forEachIndexed {index,text->check(tts.speak(text,if(index==0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,"$utterancePrefix-$index")==TextToSpeech.SUCCESS)}
            } catch(_:Exception) {stopSpeaking();speechError=Words.SpeechPlaybackError}
        },
        onRefresh={refresh(true)},onPermissions={runCatching {context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}"))) }},
        onSpeechSettings={
            stopSpeaking()
            // The dedicated AOSP action is optional on OEM devices. No voice data, default or consent is changed here.
            runCatching {context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))}.isSuccess ||
                runCatching {context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))}.isSuccess
        },
        phoneOnly=phoneOnly, checkingLanguage=checkingLanguage,
        recognitionBlock=if(phoneOnly && Build.VERSION.SDK_INT<33) Words.PhoneDictationNeedsLanguageCheck
            else if(phoneOnly) Words.PhoneDictationServiceMissing else null,
        draftBlock=draftError ?: phoneDraftBlock?.invoke(transcript),
        useKokoro = useKokoro,
        readExtras = {
            KokoroReadAloudControls(
                supported = kokoroAvailable, installed = packInstalled, selected = useKokoro, speaker = speaker,
                speaking = speaking, changing = packJob != null, progress = packBytes, error = packMessage,
                onSelect = { onPreferences(preferences.copy(preferKokoro = it));speechError = null },
                onVoice = {
                    onPreferences(if (language == "zh-CN") preferences.copy(kokoroMandarin = it.id)
                        else preferences.copy(kokoroEnglish = it.id))
                },
                onDownload = { changePack(remove = false) }, onRemove = { changePack(remove = true) },
                onCancelDownload = { packJob?.cancel() },
                onPreview = {
                    playKokoro(if (speaker.language == "zh-CN") "今天的笔记已经准备好了。我们下午可以再继续。"
                        else "Your notes are ready. We can continue this afternoon.")
                }
            )
        }
    )
}
