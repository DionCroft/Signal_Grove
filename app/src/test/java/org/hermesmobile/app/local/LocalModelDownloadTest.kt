package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalModelDownloadTest {
    @get:Rule val temp = TemporaryFolder()
    private fun fixture(arch: String = "lfm3"): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun u32(n: Int) = out.writeInt(Integer.reverseBytes(n))
            fun u64(n: Long) = out.writeLong(java.lang.Long.reverseBytes(n))
            fun text(s: String) { val b = s.toByteArray(); u64(b.size.toLong()); out.write(b) }
            fun field(key: String, value: String) { text(key); u32(8); text(value) }
            u32(0x46554747); u32(3); u64(1); u64(4)
            field("general.name", "Fixture"); field("general.architecture", arch)
            text("$arch.context_length"); u32(4); u32(2048)
            field("tokenizer.chat_template", "{{ bos_token }}{{ messages }}")
            text("weight"); u32(1); u64(1); u32(0); u64(0)
            while (bytes.size() % 32 != 0) out.writeByte(0)
            out.write(ByteArray(1024 * 1024))
        }
        return bytes.toByteArray()
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun spec(bytes: ByteArray) = ModelDownloadSpec("https://model.invalid/pinned.gguf".toHttpUrl(), bytes.size.toLong(), hash(bytes))
    private fun client(reply: (Request) -> Response) = OkHttpClient.Builder().addInterceptor { reply(it.request()) }.build()
    private fun response(request: Request, bytes: ByteArray, code: Int = 200, range: String? = null) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(bytes.toResponseBody())
        .apply { if (range != null) header("Content-Range", range) }.build()
    private fun store(root: File) = LocalModelStore(root) { Long.MAX_VALUE }

    @Test fun downloadsChecksAndAtomicallyInstallsWithoutCredentials() = runBlocking {
        val bytes = fixture(); val root = temp.newFolder(); val store = store(root); var requests = 0
        val phases = mutableListOf<LocalTransferPhase>()
        val downloader = LocalModelDownload(store, client { request ->
            requests++; assertNull(request.header("Authorization")); assertNull(request.header("Cookie"))
            assertEquals("identity", request.header("Accept-Encoding")); assertNull(request.header("Range"))
            response(request, bytes)
        }, spec(bytes))
        val installed = downloader.download(true) { phases.add(it.phase) }
        assertEquals(hash(bytes), installed.sha256); assertEquals(1, requests)
        assertArrayEquals(bytes, store.verifiedFile(installed.sha256).readBytes())
        assertTrue(phases.containsAll(LocalTransferPhase.entries)); assertEquals(0L, downloader.partialBytes())
        assertEquals(installed, downloader.download(true)); assertEquals(1, requests)
    }
    @Test fun resumesExactOffsetAndRehashesTheEntireResult() = runBlocking {
        val bytes = fixture(); val root = temp.newFolder(); val spec = spec(bytes); val store = store(root); val offset = 512
        File(root, "${spec.sha256}.part").writeBytes(bytes.copyOfRange(0, offset))
        val downloader = LocalModelDownload(store, client { request ->
            assertEquals("bytes=$offset-", request.header("Range"))
            response(request, bytes.copyOfRange(offset, bytes.size), 206, "bytes $offset-${bytes.size - 1}/${bytes.size}")
        }, spec)
        assertEquals(spec.sha256, downloader.download(true).sha256)
        assertArrayEquals(bytes, store.verifiedFile(spec.sha256).readBytes())
    }
    @Test fun ignoredResumeNeverTruncatesTheExistingPartial() = runBlocking {
        val bytes = fixture(); val root = temp.newFolder(); val spec = spec(bytes); val store = store(root)
        val partial = File(root, "${spec.sha256}.part").also { it.writeBytes(bytes.copyOfRange(0, 512)) }
        val downloader = LocalModelDownload(store, client { response(it, bytes) }, spec)
        var reason: String? = null
        try { downloader.download(true) } catch (e: LocalModelStorageException) { reason = e.reason }
        assertEquals("download_resume_unsupported", reason); assertEquals(512L, partial.length()); assertTrue(store.models().isEmpty())
    }
    @Test fun badChecksumNeverBecomesInstalledAndRequiresExplicitDiscard() = runBlocking {
        val bytes = fixture(); val root = temp.newFolder(); val store = store(root); val wrong = spec(bytes).copy(sha256 = "1".repeat(64))
        val downloader = LocalModelDownload(store, client { response(it, bytes) }, wrong)
        var reason: String? = null
        try { downloader.download(true) } catch (e: LocalModelStorageException) { reason = e.reason }
        assertEquals("checksum_mismatch", reason); assertTrue(store.models().isEmpty()); assertEquals(bytes.size.toLong(), downloader.partialBytes())
        downloader.discardPartial(); assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun cancellationRetainsOnlyAResumablePartialThenCompletesOnExplicitResume() = runBlocking {
        val bytes = fixture(); val root = temp.newFolder(); val store = store(root); val spec = spec(bytes); var requests = 0
        val downloader = LocalModelDownload(store, client { request ->
            requests++
            val offset = request.header("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            response(request, bytes.copyOfRange(offset, bytes.size), if (offset == 0) 200 else 206,
                if (offset == 0) null else "bytes $offset-${bytes.size - 1}/${bytes.size}")
        }, spec)
        var cancelled = false
        try { downloader.download(true) { throw CancellationException("cancel transfer") } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled); assertTrue(store.models().isEmpty()); assertTrue(downloader.partialBytes() in 1 until bytes.size.toLong())
        assertEquals(spec.sha256, downloader.download(true).sha256); assertEquals(2, requests)
    }
    @Test fun licenceAndSpaceFailuresMakeNoRequest() = runBlocking {
        val bytes = fixture(); var requests = 0; val client = client { requests++; response(it, bytes) }
        val root = temp.newFolder(); val downloader = LocalModelDownload(LocalModelStore(root) { 0L }, client, spec(bytes))
        var licenceRejected = false
        try { downloader.download(false) } catch (_: IllegalArgumentException) { licenceRejected = true }
        assertTrue(licenceRejected)
        var reason: String? = null
        try { downloader.download(true) } catch (e: LocalModelStorageException) { reason = e.reason }
        assertEquals("insufficient_space", reason); assertEquals(0, requests); assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun rejectsInsecureOrCredentialBearingModelSources() {
        val bytes = fixture()
        assertThrows(IllegalArgumentException::class.java) { spec(bytes).copy(url = "http://model.invalid/file".toHttpUrl()) }
        assertThrows(IllegalArgumentException::class.java) { spec(bytes).copy(url = "https://user:secret@model.invalid/file".toHttpUrl()) }
    }
    @Test fun validatesRangesAndRejectsOverflowUnknownTotalsAndHttpFailures() {
        LocalModelDownload.validateResponse(206, "bytes 5-9/10", 5, 10)
        for ((code, range) in listOf(401 to null, 404 to null, 416 to null, 206 to null, 206 to "bytes 0-9/10", 206 to "bytes 5-8/10", 206 to "bytes 5-9/*", 206 to "bytes 9999999999999999999-9/10")) {
            assertThrows(LocalModelStorageException::class.java) { LocalModelDownload.validateResponse(code, range, 5, 10) }
        }
    }
    @Test fun corruptedResumePrefixFailsWholeFileHash() = runBlocking {
        val bytes = fixture(); val root = temp.newFolder(); val store = store(root); val spec = spec(bytes)
        File(root, "${spec.sha256}.part").writeBytes(ByteArray(512))
        val downloader = LocalModelDownload(store, client { response(it, bytes.copyOfRange(512, bytes.size), 206, "bytes 512-${bytes.size - 1}/${bytes.size}") }, spec)
        var reason: String? = null
        try { downloader.download(true) } catch (e: LocalModelStorageException) { reason = e.reason }
        assertEquals("checksum_mismatch", reason); assertTrue(store.models().isEmpty())
    }
    @Test fun verifiedBytesStillNeedCompatibleMetadata() = runBlocking {
        val bytes = fixture("unknown"); val store = store(temp.newFolder())
        val downloader = LocalModelDownload(store, client { response(it, bytes) }, spec(bytes))
        var reason: String? = null
        try { downloader.download(true) } catch (e: LocalModelFormatException) { reason = e.reason }
        assertEquals("unsupported_architecture", reason); assertTrue(store.models().isEmpty())
    }
    @Test fun rejectsWrongResponseLengthBeforeWriting() = runBlocking {
        val bytes = fixture(); val root = temp.newFolder(); val store = store(root)
        val downloader = LocalModelDownload(store, client { response(it, bytes.copyOf(bytes.size - 1)) }, spec(bytes))
        var reason: String? = null
        try { downloader.download(true) } catch (e: LocalModelStorageException) { reason = e.reason }
        assertEquals("download_size", reason); assertTrue(root.listFiles()!!.isEmpty())
    }
}
