package org.hermesmobile.app

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.hermesmobile.app.local.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit public-model network gate. Default production transport, isolated roots, no Hermes state. */
@RunWith(AndroidJUnit4::class)
class LocalModelTransferIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun stage(name: String, bytes: Long? = null, elapsed: Long? = null) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("local_transfer_stage", name)
            bytes?.let {putLong("local_transfer_bytes", it)}
            elapsed?.let {putLong("local_transfer_elapsed_ms", it)}
        })
    }
    private fun root(): File {
        assumeTrue("Requires explicit public-model network and licence acknowledgement",
            InstrumentationRegistry.getArguments().getString("allowLiveModelDownload") == "pinned-lfm3-licence-reviewed")
        assumeTrue("Phone model management requires a 64-bit process", android.os.Process.is64Bit())
        val directory = File(context.noBackupFilesDir, "live-transfer-${UUID.randomUUID()}")
        check(directory.mkdir())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("local_transfer_probe_directory", directory.name)
        })
        return directory
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input ->
            val buffer = ByteArray(256 * 1024)
            while(true) {val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n)}
        }
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    private fun createController(root: File, guard: UpdateWorkGuard): LocalAiController =
        LocalAiController(File(root, "models"), File(root, "chats"), context.applicationInfo.nativeLibraryDir,
            android.os.Process.is64Bit(), {
                val info = ActivityManager.MemoryInfo()
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
                info.availMem
            }, guard)
    private suspend fun idle(controller: LocalAiController, guard: UpdateWorkGuard, timeout: Long = 20_000, allowError: Boolean = false): LocalAiState {
        val state = withTimeout(timeout) {controller.state.first {!it.busy}}
        withTimeout(5_000) {guard.active.first {!it}}
        if(!allowError) assertNull("Local transfer failed: ${state.error}", state.error)
        return state
    }
    private suspend fun stop(controller: LocalAiController, guard: UpdateWorkGuard) = withContext(NonCancellable) {
        controller.leave(); idle(controller, guard, allowError = true)
    }
    private suspend fun cancelAfterRealProgress(controller: LocalAiController, guard: UpdateWorkGuard): Long {
        controller.download(true)
        val running = withTimeout(120_000) {controller.state.first {
            !it.busy || (it.transfer?.let {p -> p.phase == LocalTransferPhase.DOWNLOADING && p.bytes >= 8L * 1024 * 1024} == true)
        }}
        assertTrue("Transfer ended before cancellation: ${running.error}", running.busy)
        assertTrue(guard.active.value)
        val progress = requireNotNull(running.transfer)
        assertEquals(RecommendedLocalModel.BYTES, progress.total)
        assertTrue(progress.bytes in 1 until RecommendedLocalModel.BYTES)
        val started = SystemClock.elapsedRealtime()
        controller.cancel()
        val cancelled = idle(controller, guard, timeout = 10_000)
        assertTrue(cancelled.models.isEmpty()); assertNull(cancelled.loaded)
        assertTrue(cancelled.partialBytes in 1 until RecommendedLocalModel.BYTES)
        stage("real-https-transfer-cancelled", cancelled.partialBytes, SystemClock.elapsedRealtime() - started)
        return cancelled.partialBytes
    }
    @Test fun livePinnedDownloadCancelsAndExplicitlyResumes() = runBlocking {
        val root = root(); val guard = UpdateWorkGuard(); val controller = createController(root, guard)
        val resumedGuard = UpdateWorkGuard(); var resumed: LocalAiController? = null
        try {
            idle(controller, guard)
            controller.download(false)
            assertNotNull(idle(controller, guard, allowError = true).error)
            assertEquals(0L, controller.state.value.partialBytes)
            assertTrue(controller.state.value.models.isEmpty())
            val retained = cancelAfterRealProgress(controller, guard)
            val models = File(root, "models")
            val partial = File(models, "${RecommendedLocalModel.SHA256}.part")
            assertEquals(retained, partial.length())
            val prefixHash = hash(partial)
            stop(controller, guard)
            val cold = createController(root, resumedGuard); resumed = cold
            val initial = idle(cold, resumedGuard)
            assertEquals(retained, initial.partialBytes); assertTrue(initial.models.isEmpty())
            assertNull(initial.loaded); assertTrue(initial.history.isEmpty())
            delay(300)
            assertEquals(retained, partial.length()); assertEquals(prefixHash, hash(partial))
            stage("partial-retained-without-automatic-resume", retained)
            val started = SystemClock.elapsedRealtime()
            cold.download(true)
            val completed = idle(cold, resumedGuard, timeout = 300_000)
            val model = completed.models.single()
            assertEquals(RecommendedLocalModel.SHA256, model.sha256)
            assertEquals(RecommendedLocalModel.BYTES, model.metadata.bytes)
            assertEquals("lfm3", model.metadata.architecture)
            assertEquals(0L, completed.partialBytes); assertFalse(partial.exists())
            val file = LocalModelStore(models).verifiedFile(model.sha256)
            assertEquals(RecommendedLocalModel.SHA256, hash(file))
            assertTrue(completed.history.isEmpty()); assertNull(completed.loaded)
            stage("real-https-explicit-resume-verified-and-atomically-installed", file.length(), SystemClock.elapsedRealtime() - started)
            cold.deleteModel(model)
            assertTrue(idle(cold, resumedGuard).models.isEmpty())
            assertFalse(file.exists())
        } finally {
            resumed?.let {stop(it, resumedGuard)}
            stop(controller, guard)
        }
    }
    @Test fun livePinnedDownloadCancelsAndExplicitlyDiscards() = runBlocking {
        val root = root(); val guard = UpdateWorkGuard(); val controller = createController(root, guard)
        try {
            idle(controller, guard)
            val retained = cancelAfterRealProgress(controller, guard)
            val models = File(root, "models")
            val partial = File(models, "${RecommendedLocalModel.SHA256}.part")
            val sentinel = File(models, "retained-test-data.txt")
            sentinel.writeText("Explicit discard retains unrelated test data.")
            val before = hash(sentinel)
            assertEquals(retained, partial.length())
            controller.discardDownload()
            val discarded = idle(controller, guard)
            assertEquals(0L, discarded.partialBytes); assertFalse(partial.exists())
            assertTrue(discarded.models.isEmpty()); assertTrue(discarded.history.isEmpty())
            assertEquals(before, hash(sentinel))
            stage("real-https-partial-explicitly-discarded-only", retained)
        } finally {stop(controller, guard)}
    }
}
