package org.hermesmobile.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Bundle
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.hermesmobile.app.local.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit large-model gate: no download, server, settings change or production history access. */
@RunWith(AndroidJUnit4::class)
class LocalModelPhysicalTest {
    // The external device harness brings the existing empty debug activity to
    // the foreground. Do not use ActivityScenario's cross-package bootstrap:
    // Xiaomi can hold it before any model check despite a successful ADB launch.
    private fun stage(name: String, elapsed: Long? = null) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("local_stage", name)
            if (elapsed != null) putLong("local_elapsed_ms", elapsed)
        })
    }
    private fun modelInput(): Pair<File, File> {
        assumeTrue("Local inference requires a 64-bit process", android.os.Process.is64Bit())
        val args = InstrumentationRegistry.getArguments()
        val directory = args.getString("localModelProbeDirectory").orEmpty()
        assumeTrue("Requires an explicitly staged app-private model", directory.isNotBlank())
        require(directory.matches(Regex("probe-[a-f0-9]{12}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.noBackupFilesDir, directory)
        val model = File(root, "model.gguf")
        stage("verify-model-hash")
        assertEquals(1049615680L, model.length())
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { stream -> val buffer = ByteArray(1024 * 1024); while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        assertEquals("43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452", digest.digest().joinToString("") { "%02x".format(it) })
        return root to model
    }
    private suspend fun metadata(model: File) = withContext(Dispatchers.IO) {
        val current = currentCoroutineContext()
        GgufModelReader.read(model) { current.ensureActive() }
    }
    @Test fun verifiedLfm3MetadataScanIsBoundedAndCancellable() = runBlocking {
        val (_, model) = modelInput()
        stage("metadata-scan-start")
        val start = SystemClock.elapsedRealtime()
        assertEquals("lfm3", withTimeout(30_000) { metadata(model) }.architecture)
        val elapsed = SystemClock.elapsedRealtime() - start
        stage("metadata-scan-complete", elapsed)
        assertTrue("Metadata scan exceeded its 20-second acceptance bound", elapsed < 20_000)
        var checkpoints = 0
        var cancelled = false
        try { GgufModelReader.read(model) { if (++checkpoints == 3) throw CancellationException("metadata probe cancelled") } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled); assertEquals(3, checkpoints)
        stage("metadata-cancellation-complete")
    }
    @Test fun verifiedLfm3SelectsReviewedChecksAndGeneratesChineseText() = runBlocking {
        val (root, model) = modelInput()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = NativeLocalEngine(root, context.applicationInfo.nativeLibraryDir)
        try {
            withTimeout(90_000) {engine.load(model, context = 2048, threads = 4)}
            for (topic in LocalHelpTopic.entries) {
                stage("helper-${topic.name}-start")
                var text = ""
                val end = withTimeout(45_000) {engine.generate(LocalSetupHelper.messages(topic, null), 384) {text = it}}
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("local_helper_topic", topic.name); putString("local_helper_answer", text); putString("local_helper_end", end.name)
                })
                assertEquals(LocalGenerationEnd.COMPLETE, end)
                val selected = LocalSetupHelper.selection(text, topic)
                assertNotNull("Invalid setup selection for $topic: $text", selected)
                assertTrue(selected!!.size in 1..3)
                selected.forEach {check ->
                    assertTrue(check.word.en.isNotBlank()); assertTrue(check.word.zh.isNotBlank()); assertTrue(check.word.pin.isNotBlank())
                    assertTrue(LocalSetupHelper.sources(topic).any {it.id == check.sourceId})
                }
                stage("helper-${topic.name}-complete")
            }
            var reply = ""
            assertEquals(LocalGenerationEnd.COMPLETE, withTimeout(30_000) {engine.generate(listOf(LocalChatMessage("user", "只回复这六个字：离线测试成功")), 384) {reply = it}})
            assertEquals("离线测试成功", reply.trim())
            stage("native-chinese-text-complete")
        } finally {withContext(NonCancellable) {engine.unload()}}
    }
    @Test fun verifiedLfm3StreamsOnlyAnswerTextForGardenPrompt() = runBlocking {
        val (root, model) = modelInput()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = NativeLocalEngine(root, context.applicationInfo.nativeLibraryDir)
        try {
            withTimeout(90_000) { engine.load(model, context = 2048, threads = 4) }
            var reply = ""
            val end = withTimeout(30_000) { engine.generate(listOf(
                LocalChatMessage("system", "You are an on-device text assistant in Signal Grove. You have no access to Hermes, servers, projects, credentials or tools. Never claim to have performed an action. Give advice as advice."),
                LocalChatMessage("user", "Write a short sentence about a peaceful garden")
            ), 384) {
                assertFalse(it.contains("<think>")); assertFalse(it.contains("</think>"))
                assertTrue(it.startsWith(reply)); reply = it
            } }
            assertEquals(LocalGenerationEnd.COMPLETE, end); assertTrue(reply.isNotBlank())
            assertTrue(reply.contains("garden", ignoreCase = true))
            stage("native-answer-only-garden-complete")
        } finally { withContext(NonCancellable) { engine.unload() } }
    }
    @Test fun verifiedLfm3GeneratesCancelsRecoversAndUnloads() = runBlocking {
        val (root, model) = modelInput()
        stage("preflight-start")
        assertEquals("lfm3", withTimeout(30_000) { metadata(model) }.architecture)
        stage("preflight-complete")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = NativeLocalEngine(root, context.applicationInfo.nativeLibraryDir)
        try {
            stage("model-load-start")
            withTimeout(90_000) { engine.load(model) }
            stage("model-load-complete")
            var reply = ""
            val end = withTimeout(30_000) { engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Signal Grove native OK")), 384) { reply = it } }
            assertEquals(LocalGenerationEnd.COMPLETE, end)
            assertEquals("Signal Grove native OK", reply.trim())
            stage("exact-native-reply-complete")
            reply = ""
            val second = withTimeout(30_000) { engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Second native OK")), 384) { reply = it } }
            assertEquals("Second request text: ${reply.take(512)}", LocalGenerationEnd.COMPLETE, second)
            assertEquals("Second native OK", reply.trim())
            stage("second-independent-native-reply-complete")
            var cancelledText = ""
            val interrupted = withTimeout(30_000) {
                engine.generate(listOf(LocalChatMessage("user", "Write a long numbered list of one thousand gardening tips.")), 512) {
                    cancelledText = it; if (it.length > 100) engine.cancel()
                }
            }
            assertEquals(LocalGenerationEnd.CANCELLED, interrupted); assertTrue(cancelledText.length > 100)
            stage("explicit-generation-cancellation-complete")
            reply = ""
            val recovered = withTimeout(30_000) { engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Recovered")), 384) { reply = it } }
            assertEquals("Recovery text: ${reply.take(512)}", LocalGenerationEnd.COMPLETE, recovered)
            assertEquals("Recovered", reply.trim())
            stage("generation-after-cancellation-complete")
            val emitted = CompletableDeferred<Unit>()
            val job = launch { engine.generate(listOf(LocalChatMessage("user", "Write one thousand gardening tips.")), 512) { if (it.length > 80) emitted.complete(Unit) } }
            withTimeout(30_000) { emitted.await() }
            withTimeout(10_000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            stage("coroutine-generation-cancellation-complete")
            var overflow: LocalEngineException? = null
            try { engine.generate(listOf(LocalChatMessage("user", "large context ".repeat(3000))), 384) { fail("Overflow must not emit output") } }
            catch (e: LocalEngineException) { overflow = e }
            assertEquals(2, overflow?.code)
            stage("context-overflow-refused")
            withTimeout(10_000) { engine.unload() }
            stage("unload-complete")
            withTimeout(90_000) { engine.load(model, context = 512, threads = 2) }
            stage("reload-complete")
            reply = ""
            assertEquals(LocalGenerationEnd.COMPLETE, withTimeout(30_000) { engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Reloaded")), 384) { reply = it } })
            assertEquals("Reloaded", reply.trim())
            stage("reply-after-reload-complete")
        } finally { withContext(NonCancellable) { engine.unload() } }
    }
}
