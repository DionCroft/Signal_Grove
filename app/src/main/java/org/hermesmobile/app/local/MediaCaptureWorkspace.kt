package org.hermesmobile.app.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.hermesmobile.app.data.CaptureSource
import org.hermesmobile.app.data.MAX_CAPTURE_CHARS
import org.hermesmobile.app.data.validCaptureText

internal enum class MediaCaptureWork { INSTALL, DOWNLOAD, OCR, RECORDING, TRANSCRIPTION }

internal data class MediaCaptureState(
    val kind: MediaCaptureKind? = null,
    val installed: Boolean = false,
    val work: MediaCaptureWork? = null,
    val cancelling: Boolean = false,
    val recordingReady: Boolean = false,
    val result: String? = null,
    val error: String? = null
)

internal class MediaCaptureWorkspace(
    context: Context,
    modelRoot: File,
    private val inbox: PhoneCaptureWorkspace,
    private val scope: CoroutineScope,
    prepareDownloadRoute: suspend (OkHttpClient) -> ModelDownloadRoute
) {
    private val resolver = context.contentResolver
    private val models = MediaCaptureModels(modelRoot)
    private val packs = MediaPackDownload(models, prepareDownloadRoute)
    private val engines = MediaCaptureEngines(context)
    private val mutable = MutableStateFlow(MediaCaptureState())
    val state = mutable.asStateFlow()
    private val recording = AtomicBoolean(false)
    private val stopLock = Any()
    private var nativeStop: (() -> Unit)? = null
    private var job: Job? = null
    private var samples: ShortArray? = null

    fun open(kind: MediaCaptureKind) {
        if (job?.isCompleted == false) return
        clearRecording()
        mutable.value = MediaCaptureState(kind = kind, installed = models.installed(kind) != null)
    }

    fun close() {
        cancel()
        mutable.value = MediaCaptureState()
    }

    fun backgrounded() {
        if (job?.isCompleted == false || samples != null) close()
    }

    fun downloadPack() {
        val kind = mutable.value.kind ?: return
        launch(MediaCaptureWork.DOWNLOAD) {
            packs.install(kind)
            updateReview { it.copy(installed = true) }
        }
    }

    fun permissionDenied() {
        if (mutable.value.kind == MediaCaptureKind.RECORDING) mutable.update { it.copy(error = "media_microphone_denied") }
    }

    fun install(uri: Uri) {
        val kind = mutable.value.kind ?: return
        launch(MediaCaptureWork.INSTALL) {
            val operation = currentCoroutineContext()
            val input = resolver.openInputStream(uri) ?: throw LocalModelStorageException("media_model_checksum")
            input.use { models.install(kind, it) { operation.ensureActive() } }
            updateReview { it.copy(installed = true) }
        }
    }

    fun screenshot(uri: Uri) {
        val kind = MediaCaptureKind.SCREENSHOT
        if (mutable.value.kind != kind) return
        val model = models.installed(kind) ?: return
        launch(MediaCaptureWork.OCR) {
            val operation = currentCoroutineContext()
            val input = resolver.openInputStream(uri) ?: throw LocalModelStorageException("media_image_invalid")
            val result = input.use {
                engines.screenshotText(it, model, { operation.ensureActive() }) { stop ->
                    synchronized(stopLock) { nativeStop = stop }
                }
            }
            setResult(result)
        }
    }

    fun record() {
        val kind = MediaCaptureKind.RECORDING
        if (mutable.value.kind != kind || models.installed(kind) == null || job?.isCompleted == false) return
        clearRecording()
        recording.set(true)
        launch(MediaCaptureWork.RECORDING) {
            val operation = currentCoroutineContext()
            val recordingBytes = engines.record({ operation.ensureActive() }, recording::get)
            var retained = false
            try {
                withContext(Dispatchers.Main.immediate) {
                    samples = recordingBytes
                    retained = true
                    mutable.update { it.copy(recordingReady = true) }
                }
            } finally {
                recording.set(false)
                if (!retained) recordingBytes.fill(0)
            }
        }
    }

    fun stopRecording() { recording.set(false) }

    fun discardRecording() {
        if (job?.isCompleted == false) return
        clearRecording()
        mutable.update { it.copy(recordingReady = false, result = null) }
    }

    fun transcribe() {
        val kind = MediaCaptureKind.RECORDING
        if (mutable.value.kind != kind || job?.isCompleted == false) return
        val model = models.installed(kind) ?: return
        val audio = samples?.copyOf() ?: return
        launch(MediaCaptureWork.TRANSCRIPTION) {
            val operation = currentCoroutineContext()
            try {
                setResult(engines.transcript(audio, model) { operation.ensureActive() })
                withContext(Dispatchers.Main.immediate) {
                    clearRecording()
                    mutable.update { it.copy(recordingReady = false) }
                }
            } finally {
                audio.fill(0)
            }
        }
    }

    fun reviewAsCapture() {
        val current = mutable.value
        if (current.work != null) return
        val text = current.result?.takeIf(::validCaptureText) ?: return
        val source = when (current.kind) {
            MediaCaptureKind.SCREENSHOT -> CaptureSource.SCREENSHOT_TEXT
            MediaCaptureKind.RECORDING -> CaptureSource.VOICE_TRANSCRIPT
            null -> return
        }
        inbox.openExtractedDraft(source, text)
        close()
    }

    fun cancel() {
        if (job?.isCompleted == false) {
            mutable.update { it.copy(cancelling = true, recordingReady = false) }
            job?.cancel()
            synchronized(stopLock) { nativeStop?.invoke() }
        }
        recording.set(false)
        clearRecording()
    }

    private fun clearRecording() {
        samples?.fill(0)
        samples = null
    }

    private suspend fun setResult(text: String) {
        val answer = text.trim()
        if (answer.length > MAX_CAPTURE_CHARS || answer.toByteArray(Charsets.UTF_8).size > MAX_CAPTURE_CHARS) {
            throw LocalModelStorageException("media_text_limit")
        }
        if (!validCaptureText(answer)) throw LocalModelStorageException("media_no_text")
        updateReview { it.copy(result = answer) }
    }

    private suspend fun updateReview(action: (MediaCaptureState) -> MediaCaptureState) {
        withContext(Dispatchers.Main.immediate) { mutable.update(action) }
    }

    private fun launch(work: MediaCaptureWork, block: suspend () -> Unit) {
        if (job?.isCompleted == false) return
        mutable.update { it.copy(work = work, result = null, error = null) }
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocalModelStorageException) {
                mutable.update { if (it.kind != null) it.copy(error = e.reason) else it }
            } catch (_: LinkageError) {
                mutable.update { if (it.kind != null) it.copy(error = "media_engine_failed") else it }
            } catch (_: Exception) {
                mutable.update { if (it.kind != null) it.copy(error = "media_engine_failed") else it }
            } finally {
                mutable.update { it.copy(work = null, cancelling = false) }
            }
        }
    }
}
