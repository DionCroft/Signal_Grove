package org.hermesmobile.app

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FilterInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.hermesmobile.app.local.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real atomic import with its return to Main deliberately held; isolated files, no server/network. */
@RunWith(AndroidJUnit4::class)
class LocalImportCommitIntegrationTest {
    @Test fun cancelledCommittedImportBecomesVisibleWithoutReopeningOrLoading() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = InstrumentationRegistry.getArguments().getString("localOtherModelProbeDirectory").orEmpty()
        assumeTrue("Requires explicitly staged publisher-verified LFM3", directory.isNotBlank())
        assumeTrue("Phone model management requires a 64-bit process", android.os.Process.is64Bit())
        require(directory.matches(Regex("other-model-probe-[a-f0-9]{12}")))
        val inputRoot = File(context.noBackupFilesDir, directory)
        val source = File(inputRoot, "lfm3.gguf")
        assertEquals(inputRoot.canonicalFile, source.canonicalFile.parentFile)
        assertEquals(RecommendedLocalModel.BYTES, source.length())
        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use {input ->
                val buffer = ByteArray(1024 * 1024)
                while(true) {val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n)}
            }
            return digest.digest().joinToString("") {"%02x".format(it)}
        }
        assertEquals(RecommendedLocalModel.SHA256, hash(source))
        val root = File(inputRoot, "commit-boundary-${UUID.randomUUID()}"); check(root.mkdir())
        val models = File(root, "models"); val chats = File(root, "chats")
        val guard = UpdateWorkGuard()
        val controller = LocalAiController(models, chats, context.applicationInfo.nativeLibraryDir,
            android.os.Process.is64Bit(), {
                val info = ActivityManager.MemoryInfo()
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
                info.availMem
            }, guard)
        val copied = CountDownLatch(1); val releaseCopy = CountDownLatch(1)
        val mainHeld = CountDownLatch(1); val releaseMain = CountDownLatch(1)
        var opens = 0
        suspend fun idle(): LocalAiState {
            val value = withTimeout(30_000) {controller.state.first {!it.busy}}
            withTimeout(5_000) {guard.active.first {!it}}
            return value
        }
        try {
            assertNull(idle().error)
            controller.importModel(true) {
                opens++
                object : FilterInputStream(source.inputStream()) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        val count = super.read(buffer, offset, length)
                        if(count < 0) {
                            copied.countDown()
                            check(releaseCopy.await(10, TimeUnit.SECONDS)) {"Copy gate timed out"}
                        }
                        return count
                    }
                }
            }
            assertTrue(withContext(Dispatchers.IO) {copied.await(90, TimeUnit.SECONDS)})
            // Main stays responsive during the large copy. Hold it only for the short
            // sync/metadata/publication window, with independent bounded release gates.
            assertTrue(Handler(Looper.getMainLooper()).post {
                mainHeld.countDown(); releaseMain.await(10, TimeUnit.SECONDS)
            })
            assertTrue(withContext(Dispatchers.IO) {mainHeld.await(5, TimeUnit.SECONDS)})
            releaseCopy.countDown()
            val published = File(models, "${RecommendedLocalModel.SHA256}.gguf")
            withTimeout(10_000) {while(!published.isFile) delay(2)}
            assertEquals(RecommendedLocalModel.BYTES, published.length())
            assertTrue(controller.state.value.busy); assertTrue(guard.active.value)
            assertTrue(controller.state.value.models.isEmpty())
            controller.cancel()
            assertTrue(controller.state.value.busy); assertTrue(guard.active.value)
            releaseMain.countDown()
            val stopped = idle()
            assertNull(stopped.error); assertNull(stopped.loaded)
            assertEquals(RecommendedLocalModel.SHA256, stopped.models.single().sha256)
            assertEquals(1, opens); assertTrue(stopped.history.isEmpty())
            assertEquals(0L, stopped.partialBytes)
            assertEquals(RecommendedLocalModel.SHA256, hash(LocalModelStore(models).verifiedFile(RecommendedLocalModel.SHA256)))
            assertFalse(models.listFiles()!!.any {it.name.endsWith(".part")})
            instrumentation.sendStatus(2, Bundle().apply {
                putString("local_import_stage", "committed-file-visible-after-stop-without-reopen-or-load")
                putLong("local_import_bytes", published.length())
            })
            controller.deleteModel(stopped.models.single())
            assertTrue(idle().models.isEmpty()); assertFalse(published.exists())
            assertEquals(RecommendedLocalModel.SHA256, hash(source))
        } finally {
            releaseCopy.countDown(); releaseMain.countDown()
            withContext(NonCancellable) {controller.leave(); idle()}
        }
    }
}
