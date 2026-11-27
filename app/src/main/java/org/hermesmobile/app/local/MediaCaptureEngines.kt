package org.hermesmobile.app.local

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer

internal const val CAPTURE_SAMPLE_RATE = 16_000
internal const val CAPTURE_MAX_RECORDING_SECONDS = 20

internal class MediaCaptureEngines(private val context: Context) {
    fun screenshotText(image: InputStream, model: File, check: () -> Unit, cancelWith: ((() -> Unit)?) -> Unit): String {
        val bytes = boundedImage(image, check)
        val api = TessBaseAPI()
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val pixels = bounds.outWidth.toLong() * bounds.outHeight
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixels > 36_000_000) {
                throw LocalModelStorageException("media_image_invalid")
            }
            val options = BitmapFactory.Options().apply { inSampleSize = if (pixels > 9_000_000) 2 else 1 }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw LocalModelStorageException("media_image_invalid")
            try {
                check()
                if (!api.init(model.absolutePath, "eng", TessBaseAPI.OEM_LSTM_ONLY)) {
                    throw LocalModelStorageException("media_engine_failed")
                }
                api.setImage(bitmap)
                cancelWith { api.stop() }
                check()
                api.getHOCRText(0) // Recognize through Tesseract's cancellable entry point.
                check()
                return api.getUTF8Text().orEmpty().trim()
            } finally {
                bitmap.recycle()
            }
        } finally {
            cancelWith(null)
            api.recycle()
            bytes.fill(0)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun record(check: () -> Unit, keepRecording: () -> Boolean): ShortArray {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw LocalModelStorageException("media_microphone_denied")
        }
        val minBytes = AudioRecord.getMinBufferSize(CAPTURE_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) throw LocalModelStorageException("media_microphone_unavailable")
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, CAPTURE_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes, 3_200))
        val sample = ShortArray(CAPTURE_SAMPLE_RATE * CAPTURE_MAX_RECORDING_SECONDS)
        var written = 0
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw LocalModelStorageException("media_microphone_unavailable")
            }
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw LocalModelStorageException("media_microphone_unavailable")
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + (CAPTURE_MAX_RECORDING_SECONDS + 1) * 1_000
            while (written < sample.size && keepRecording() && android.os.SystemClock.elapsedRealtime() < deadline) {
                check()
                val count = recorder.read(sample, written, minOf(1_600, sample.size - written), AudioRecord.READ_NON_BLOCKING)
                if (count < 0) throw LocalModelStorageException("media_microphone_unavailable")
                if (count == 0) delay(15)
                else written += count
            }
            check()
            if (written == 0) throw LocalModelStorageException("media_audio_empty")
            return sample.copyOf(written)
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } finally {
                sample.fill(0)
                recorder.release()
            }
        }
    }

    fun transcript(samples: ShortArray, model: File, check: () -> Unit): String {
        val results = mutableListOf<String>()
        val block = ShortArray(CAPTURE_SAMPLE_RATE / 10)
        try {
            Model(model.absolutePath).use { speech ->
                Recognizer(speech, CAPTURE_SAMPLE_RATE.toFloat()).use { recognizer ->
                    var offset = 0
                    while (offset < samples.size) {
                        check()
                        val count = minOf(block.size, samples.size - offset)
                        samples.copyInto(block, 0, offset, offset + count)
                        if (recognizer.acceptWaveForm(block, count)) results += recognitionText(recognizer.result)
                        offset += count
                    }
                    check()
                    results += recognitionText(recognizer.finalResult)
                }
            }
        } finally {
            block.fill(0)
        }
        return results.filter(String::isNotBlank).joinToString(" ").trim()
    }

    private fun recognitionText(payload: String): String =
        Json.parseToJsonElement(payload).jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun boundedImage(stream: InputStream, check: () -> Unit): ByteArray {
        val maxBytes = 24 * 1024 * 1024
        val buffer = ByteArray(64 * 1024)
        val output = ByteArrayOutputStream()
        while (true) {
            check()
            val read = stream.read(buffer)
            if (read < 0) return output.toByteArray()
            if (read > maxBytes - output.size()) throw LocalModelStorageException("media_image_invalid")
            output.write(buffer, 0, read)
        }
    }
}
