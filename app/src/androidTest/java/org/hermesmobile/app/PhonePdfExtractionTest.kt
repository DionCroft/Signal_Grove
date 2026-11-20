package org.hermesmobile.app

import android.content.ComponentName
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.local.LocalModelStorageException
import org.hermesmobile.app.local.PhonePdfReader
import org.hermesmobile.app.local.PhonePdfService
import org.junit.Assert.*
import org.junit.Test

class PhonePdfExtractionTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun pdfImportExtractsOriginalPagesOrExplainsUnsupportedAndroid() = runBlocking(Dispatchers.IO) {
        val marker = "The observatory codeword is MARIGOLD."
        val bytes = pdf(listOf("The observatory opens at dusk.", "", marker))
        if (Build.VERSION.SDK_INT < 35) {
            assertEquals("document_pdf_unsupported", failureReason(bytes))
        } else {
            @Suppress("DEPRECATION")
            val service = app.packageManager.getServiceInfo(ComponentName(app, PhonePdfService::class.java), 0)
            assertTrue(service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
            assertFalse(service.exported)
            assertEquals("${app.packageName}:phone_pdf", service.processName)
            val pages = PhonePdfReader.extract(app, bytes.inputStream()) {}
            assertEquals(3, pages.size)
            assertTrue(pages[0].contains("observatory opens at dusk"))
            assertTrue(pages[1].isBlank())
            assertTrue(pages[2].contains(marker))
        }
    }

    @Test fun pdfImportRejectsUnreadableScannedAndOversizedInputWithoutLeavingStagingFiles() = runBlocking(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 35) return@runBlocking
        val initial = stagingEntries()
        assertEquals("document_pdf_invalid", failureReason("not a PDF".toByteArray()))
        assertEquals("document_pdf_image_only", failureReason(pdf(listOf(""), imageOnly = true)))
        assertEquals("document_pdf_page_limit", failureReason(pdf(List(65) { "" })))
        assertEquals("document_pdf_size", failureReason(ByteArray(8 * 1024 * 1024 + 1)))
        val cancel = runCatching {
            PhonePdfReader.extract(app, pdf(listOf("Will be cancelled.")).inputStream()) {
                throw CancellationException()
            }
        }
        assertTrue(cancel.exceptionOrNull() is CancellationException)
        assertEquals(initial, stagingEntries())
    }

    private suspend fun failureReason(bytes: ByteArray): String? {
        return (runCatching { PhonePdfReader.extract(app, bytes.inputStream()) {} }
            .exceptionOrNull() as? LocalModelStorageException)?.reason
    }

    private fun stagingEntries(): Set<String> = app.cacheDir.list().orEmpty()
        .filter { it.startsWith("phone-pdf-") && it.endsWith(".pending") }.toSet()

    private fun pdf(lines: List<String>, imageOnly: Boolean = false): ByteArray {
        val document = PdfDocument()
        val image = if (imageOnly) Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        } else null
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f }
            lines.forEachIndexed { index, text ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(450, 150, index + 1).create())
                if (text.isNotEmpty()) page.canvas.drawText(text, 24f, 60f, paint)
                if (image != null) page.canvas.drawBitmap(image, 24f, 36f, paint)
                document.finishPage(page)
            }
            return ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
            image?.recycle()
        }
    }
}
