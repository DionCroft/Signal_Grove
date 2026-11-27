package org.hermesmobile.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.Settings
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import org.hermesmobile.app.data.*
import org.hermesmobile.app.local.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class MediaCaptureNativeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun modelPacksRecognizeSyntheticInputsAndSaveOnlyEditedTextOffline() = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("mediaProbeDirectory").orEmpty()
        assumeTrue("Requires explicitly staged publisher language packs", stage.isNotBlank())
        require(stage.matches(Regex("media-probe-[a-f0-9]{12}")))
        assertEquals(1, Settings.Global.getInt(context.contentResolver, "airplane_mode_on", 0))
        val root = File(context.noBackupFilesDir, stage).canonicalFile
        check(root.parentFile == context.noBackupFilesDir.canonicalFile)
        val installed = MediaCaptureModels(File(root, "installed-" + java.util.UUID.randomUUID()))
        val screenshotKind = MediaCaptureKind.SCREENSHOT
        val speechKind = MediaCaptureKind.RECORDING
        assertTrue(runCatching { installed.install(screenshotKind, byteArrayOf(11, 22).inputStream()) {} }.isFailure ||
            installed.installed(screenshotKind) != null)
        val ocr = File(root, screenshotKind.fileName).inputStream().use { installed.install(screenshotKind, it) {} }
        val speech = File(root, speechKind.fileName).inputStream().use { installed.install(speechKind, it) {} }
        assertEquals(speech, installed.installed(speechKind))

        val downloaded = MediaCaptureModels(File(root, "downloaded-" + java.util.UUID.randomUUID()))
        var fetched = 0
        val packFile = File(root, speechKind.fileName)
        val fixtureTransport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals(speechKind.download, request.url.toString())
            assertEquals("identity", request.header("Accept-Encoding"))
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
            fetched++
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("Prepared publisher pack")
                .body(packFile.source().buffer().asResponseBody(contentLength = packFile.length())).build()
        }.build()
        val transfer = MediaPackDownload(downloaded, { ModelDownloadRoute(it) }, fixtureTransport)
        val verifiedSpeech = transfer.install(speechKind)
        assertEquals(verifiedSpeech, downloaded.installed(speechKind))
        assertEquals(verifiedSpeech, transfer.install(speechKind))
        assertEquals(1, fetched)

        val engines = MediaCaptureEngines(context)
        val rendered = screenshot()
        var stopAfterInit = false
        var cleared = false
        try {
            val cancelled = runCatching {
                engines.screenshotText(rendered.inputStream(), ocr,
                    { if (stopAfterInit) throw CancellationException("synthetic stop") }) { callback ->
                    if (callback != null) stopAfterInit = true else cleared = true
                }
            }
            assertTrue(cancelled.exceptionOrNull() is CancellationException)
            assertTrue(cleared)
            val recognized = engines.screenshotText(rendered.inputStream(), ocr, {}, {})
            assertTrue(recognized.uppercase(Locale.ROOT).contains("GREENHOUSE"))
            assertTrue(recognized.uppercase(Locale.ROOT).contains("SEVEN"))

            val samples = pcm(File(root, "user.wav"))
            val transcript = try { engines.transcript(samples, verifiedSpeech) {} }
                finally { samples.fill(0) }
            assertTrue("Expected spoken keywords", transcript.contains("door", ignoreCase = true) &&
                transcript.contains("seven", ignoreCase = true))

            val database = Room.inMemoryDatabaseBuilder(context, HermesDatabase::class.java).build()
            val controller = LocalAiController(File(root, "models"), File(root, "history"),
                context.applicationInfo.nativeLibraryDir, android.os.Process.is64Bit(), { Long.MAX_VALUE }, UpdateWorkGuard())
            try {
                val inbox = PhoneCaptureWorkspace(database.phoneCaptures(), controller, this)
                for ((source, result, edited) in listOf(
                    Triple(CaptureSource.SCREENSHOT_TEXT, recognized, "Edited screenshot capture"),
                    Triple(CaptureSource.VOICE_TRANSCRIPT, transcript, "Edited phone transcript")
                )) {
                    inbox.openExtractedDraft(source, result)
                    assertEquals(result, inbox.state.value.draft?.content)
                    assertTrue(database.phoneCaptures().search("").first().none { it.source == source.name })
                    inbox.editNote(edited)
                    inbox.saveNote()
                    val saved = withTimeout(10_000) { inbox.state.first { !it.saving } }
                    val note = database.phoneCaptures().observe(requireNotNull(saved.selectedId)).first()
                    assertEquals(edited, note?.content)
                    assertEquals(source.name, note?.source)
                }
                assertNull(controller.state.value.loaded)
            } finally {
                controller.leave()
                database.close()
            }
        } finally { rendered.fill(0) }
    }
    private fun screenshot(): ByteArray {
        val bitmap = Bitmap.createBitmap(1100, 420, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 72f }
            canvas.drawText("GREENHOUSE GATE", 60f, 145f, pen)
            canvas.drawText("OPENS AT SEVEN", 60f, 260f, pen)
            return ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun pcm(wave: File): ShortArray {
        val bytes = wave.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(header.getInt(24) == CAPTURE_SAMPLE_RATE && header.getShort(22) == 1.toShort() &&
            header.getShort(34) == 16.toShort())
        val chunk = "data".toByteArray(Charsets.US_ASCII)
        val start = (36 until bytes.size - 8).first { offset -> chunk.indices.all { bytes[offset + it] == chunk[it] } }
        val length = header.getInt(start + 4)
        check(length in 2..(bytes.size - start - 8) && length % 2 == 0)
        return ShortArray(length / 2) { header.getShort(start + 8 + it * 2) }
    }
}
