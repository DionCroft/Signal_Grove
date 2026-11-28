package org.hermesmobile.app.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import androidx.annotation.Keep
import kotlinx.coroutines.Job

internal enum class KokoroSpeaker(val id: Int, val language: String) {
    MAPLE(0, "en-GB"), SOL(1, "en-GB"), MANDARIN_FEMALE(3, "zh-CN"), MANDARIN_MALE(58, "zh-CN");

    companion object {
        fun forLanguage(language: String, selection: Int): KokoroSpeaker =
            entries.firstOrNull { it.id == selection && it.language == language }
                ?: entries.first { it.language == language }
    }
}

@Keep
internal object KokoroNative {
    val supported: Boolean get() = Build.SUPPORTED_64_BIT_ABIS.firstOrNull() == "arm64-v8a" && loaded
    private val loaded by lazy {
        try {
            System.loadLibrary("signal_grove_kokoro")
            true
        } catch (_: LinkageError) {
            false
        }
    }
    external fun open(path: ByteArray): Long
    external fun generate(handle: Long, text: ByteArray, speaker: Int, speed: Float, sink: PcmSink): Boolean
    external fun close(handle: Long)

    @Keep fun interface PcmSink { fun write(samples: FloatArray): Boolean }
}

/** PCM stays in memory. The worker checks cancellation while writing and draining AudioTrack. */
internal class KokoroPlayback(private val pack: KokoroVoicePack, private val attributes: AudioAttributes) {
    private val audioLock = Any()
    private var activeTrack: AudioTrack? = null

    fun stop() = synchronized(audioLock) {
        activeTrack?.let { runCatching { it.pause(); it.flush() } }
        Unit
    }

    suspend fun speak(text: String, speaker: KokoroSpeaker, speed: Float, job: Job) {
        pack.play { directory ->
            if (!job.isActive) return@play
            check(KokoroNative.supported)
            val handle = KokoroNative.open(directory.path.toByteArray())
            check(handle != 0L)
            try {
                if (job.isActive) playChunks(handle, speechChunks(text), speaker, speed, job)
            } finally {
                KokoroNative.close(handle)
            }
        }
    }

    private fun playChunks(handle: Long, chunks: List<String>, speaker: KokoroSpeaker, speed: Float, job: Job) {
        val rate = 24_000
        val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minimum > 0)
        val track = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setSampleRate(rate).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, rate * 4 / 4))
            .build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            synchronized(audioLock) { activeTrack = track }
            var sent = 0L
            var started = false
            val sink = KokoroNative.PcmSink { samples ->
                if (!job.isActive) return@PcmSink false
                if (!started) synchronized(audioLock) {
                    if (!job.isActive) return@PcmSink false
                    track.play()
                    started = true
                }
                var offset = 0
                var lastProgress = SystemClock.elapsedRealtime()
                while (offset < samples.size && job.isActive) {
                    val written = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    check(written >= 0)
                    if (written == 0) {
                        check(SystemClock.elapsedRealtime() - lastProgress < 3_000)
                        Thread.sleep(12)
                    } else {
                        lastProgress = SystemClock.elapsedRealtime()
                        offset += written
                        sent += written
                    }
                }
                job.isActive
            }
            var anySpoken = false
            for (chunk in chunks) {
                if (!job.isActive) return
                anySpoken = KokoroNative.generate(handle, chunk.toByteArray(), speaker.id, speed, sink) || anySpoken
            }
            if (!job.isActive) return
            check(anySpoken)
            val drainDeadline = SystemClock.elapsedRealtime() + 4_000
            while (job.isActive && track.playbackHeadPosition.toUInt().toLong() < sent) {
                check(SystemClock.elapsedRealtime() < drainDeadline)
                Thread.sleep(12)
            }
        } finally {
            synchronized(audioLock) {
                runCatching { track.pause(); track.flush() }
                activeTrack = null
                track.release()
            }
        }
    }

    companion object {
        // Chunking bounds JNI work, including unusually long sentences without spaces.
        internal fun speechChunks(text: String): List<String> =
            text.split(Regex("(?<=[。！？])|(?<=[.!?])\\s+|\\n+")).flatMap { SpeechPolicy.chunks(it.trim(), 180) }
                .map(String::trim).filter(String::isNotEmpty)
    }
}
