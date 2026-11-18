package org.hermesmobile.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Bundle
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
                val end = withTimeout(45_000) {engine.selectHelperChecks(LocalSetupHelper.messages(topic, null),
                    LocalCheckChoices.from(LocalSetupHelper.checks(topic).map { it.id }), 96) {text = it}}
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
            val shortChinese = withTimeout(30_000) {engine.generate(listOf(LocalChatMessage("user", "只回复这六个字：离线测试成功")), 48) {reply = it}}
            assertTrue(shortChinese in setOf(LocalGenerationEnd.COMPLETE, LocalGenerationEnd.TOKEN_LIMIT))
            assertFalse(reply.contains("<think>")); assertFalse(reply.contains("</think>"))
            if (shortChinese == LocalGenerationEnd.TOKEN_LIMIT) {
                // Total tokens include natural reasoning. Preserve the original
                // limit probe, then check a separate explicitly bounded request.
                assertTrue("离线测试成功".startsWith(reply.trim()))
                stage("native-chinese-short-budget-token-limit")
                reply = ""
                assertEquals(LocalGenerationEnd.COMPLETE, withTimeout(30_000) {
                    engine.generate(listOf(LocalChatMessage("user", "只回复这六个字：离线测试成功")), 96) {reply = it}
                })
            }
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
            val end = withTimeout(30_000) { engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Signal Grove native OK")), 96) { reply = it } }
            assertEquals(LocalGenerationEnd.COMPLETE, end)
            assertEquals("Signal Grove native OK", reply.trim())
            stage("exact-native-reply-complete")
            reply = ""
            val second = withTimeout(30_000) { engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Second native OK")), 96) { reply = it } }
            assertTrue("Unexpected second-request outcome: $second", second in setOf(LocalGenerationEnd.COMPLETE, LocalGenerationEnd.TOKEN_LIMIT))
            assertFalse(reply.contains("<think>")); assertFalse(reply.contains("</think>"))
            if (second == LocalGenerationEnd.TOKEN_LIMIT) {
                assertTrue("Second native OK".startsWith(reply.trim()))
                stage("second-native-short-budget-token-limit")
                reply = ""
                assertEquals(LocalGenerationEnd.COMPLETE, withTimeout(30_000) {
                    engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Second native OK")), 384) {reply = it}
                })
            }
            assertEquals("Second native OK", reply.trim())
            stage("second-independent-native-reply-complete")
            val thinkingText = java.util.concurrent.atomic.AtomicReference("")
            // Natural reasoning need not produce answer bytes before the limit.
            // UNDISPATCHED reserves the request before the first IO suspension;
            // Stop must also work while no visible answer has arrived.
            val thinking = async(start = CoroutineStart.UNDISPATCHED) {
                engine.generate(listOf(LocalChatMessage("user", "Write a long numbered list of one thousand gardening tips.")), 512) {thinkingText.set(it)}
            }
            delay(250)
            assertTrue("The thinking request must still be active before Stop", thinking.isActive)
            assertTrue("This fixture must exercise Stop before visible output", thinkingText.get().isEmpty())
            engine.cancel()
            assertEquals(LocalGenerationEnd.CANCELLED, withTimeout(10_000) {thinking.await()})
            assertFalse(thinkingText.get().contains("<think>")); assertFalse(thinkingText.get().contains("</think>"))
            stage("explicit-generation-cancellation-complete")

            // This measured literal reaches visible output within the existing
            // 512-token cap. Keep a separate actual-stream cancellation check.
            val streamingExpected = "Signal Grove streams this local test sentence, stops when asked, keeps local history, and can continue with a new request. This text is a bounded cancellation fixture."
            var cancelledText = ""
            val interrupted = withTimeout(30_000) {
                engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: $streamingExpected")), 512) {
                    assertTrue(it.startsWith(cancelledText)); cancelledText = it
                    assertTrue(streamingExpected.startsWith(it))
                    assertFalse(it.contains("<think>")); assertFalse(it.contains("</think>"))
                    if (it.length > 100) engine.cancel()
                }
            }
            assertEquals(LocalGenerationEnd.CANCELLED, interrupted); assertTrue(cancelledText.length > 100)
            assertTrue(streamingExpected.startsWith(cancelledText))
            stage("visible-generation-cancellation-complete")
            reply = ""
            val recovered = withTimeout(30_000) { engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Recovered")), 48) { reply = it } }
            assertEquals("Recovery text: ${reply.take(512)}", LocalGenerationEnd.COMPLETE, recovered)
            assertEquals("Recovered", reply.trim())
            stage("generation-after-cancellation-complete")
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                engine.generate(listOf(LocalChatMessage("user", "Write one thousand gardening tips.")), 512) {
                    assertFalse(it.contains("<think>")); assertFalse(it.contains("</think>"))
                }
            }
            delay(250)
            assertTrue("The coroutine request must still be active before cancellation", job.isActive)
            withTimeout(10_000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            stage("coroutine-generation-cancellation-complete")
            var overflow: LocalEngineException? = null
            try { engine.generate(listOf(LocalChatMessage("user", "large context ".repeat(3000))), 96) { fail("Overflow must not emit output") } }
            catch (e: LocalEngineException) { overflow = e }
            assertEquals(2, overflow?.code)
            stage("context-overflow-refused")
            withTimeout(10_000) { engine.unload() }
            stage("unload-complete")
            withTimeout(90_000) { engine.load(model, context = 512, threads = 2) }
            stage("reload-complete")
            reply = ""
            val shortReload = withTimeout(30_000) { engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Reloaded")), 48) { reply = it } }
            assertTrue(shortReload in setOf(LocalGenerationEnd.COMPLETE, LocalGenerationEnd.TOKEN_LIMIT))
            assertFalse(reply.contains("<think>")); assertFalse(reply.contains("</think>"))
            if (shortReload == LocalGenerationEnd.TOKEN_LIMIT) {
                // The limit includes natural reasoning. Keep the 48-token probe,
                // then verify a separate bounded request after a known limit.
                assertTrue("Reloaded".startsWith(reply.trim()))
                stage("reload-short-budget-token-limit")
                reply = ""
                assertEquals(LocalGenerationEnd.COMPLETE, withTimeout(30_000) {
                    engine.generate(listOf(LocalChatMessage("user", "Reply with exactly: Reloaded")), 96) { reply = it }
                })
            }
            assertEquals("Reloaded", reply.trim())
            stage("reply-after-reload-complete")
        } finally { withContext(NonCancellable) { engine.unload() } }
    }
    /** Actual native controller gate; uses a reviewed existing model and separate QA histories. */
    @Test fun verifiedLfm3BranchesRetainParentsSettingsAndNeverReplay() = runBlocking {
        val (probeRoot, input) = modelInput()
        require(InstrumentationRegistry.getArguments().getString("localModelLicenceReviewed") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HermesApplication
        val global = app.localAi
        withTimeout(30_000) {global.state.first { !it.busy }}
        assertNull("Close any existing debug model before this explicit gate", global.state.value.loaded)
        val globalHistory = File(app.noBackupFilesDir, "local-ai/chats")
        fun digest(file: File): String {
            val value = MessageDigest.getInstance("SHA-256")
            file.inputStream().use {stream -> val buffer = ByteArray(65536); while (true) {
                val n = stream.read(buffer); if (n < 0) break; value.update(buffer, 0, n)
            } }
            return value.digest().joinToString("") {"%02x".format(it)}
        }
        fun histories(root: File) = root.listFiles().orEmpty().filter {it.isFile}.associate {it.name to digest(it)}
        val globalBefore = histories(globalHistory)
        val modelRoot = File(app.noBackupFilesDir, "local-ai/models")
        val historyRoot = File(probeRoot, "branch-history-${java.util.UUID.randomUUID()}")
        val native = NativeLocalInference(modelRoot, app.applicationInfo.nativeLibraryDir)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val limits = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val loads = java.util.concurrent.ConcurrentLinkedQueue<Pair<Int, Int>>()
        val stopOnText = java.util.concurrent.atomic.AtomicReference<(() -> Unit)?>(null)
        val observed = object : LocalInference by native {
            override suspend fun load(file: File, context: Int, threads: Int) {
                native.load(file, context, threads); loads.add(context to threads)
            }
            override suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit): LocalGenerationEnd {
                calls.incrementAndGet(); limits.add(maxTokens)
                return native.generate(messages, maxTokens) {text -> onText(text); stopOnText.getAndSet(null)?.invoke()}
            }
        }
        val controller = LocalAiController(modelRoot, historyRoot, app.applicationInfo.nativeLibraryDir,
            android.os.Process.is64Bit(), {
                android.app.ActivityManager.MemoryInfo().also {app.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it)}.availMem
            }, app.updateWorkGuard, engineFactory = {observed})
        suspend fun idle(allowError: Boolean = false): LocalAiState {
            val value = withTimeout(120_000) {
                combine(controller.state, app.updateWorkGuard.active) {state, held -> state to held}
                    .first { !it.first.busy && !it.second }.first
            }
            if (!allowError) assertNull("Native controller operation failed", value.error)
            return value
        }
        fun terminal(value: LocalConversation) {
            assertTrue(value.exchanges.last().state in setOf(LocalReplyState.COMPLETE, LocalReplyState.TOKEN_LIMIT))
            assertTrue(value.exchanges.last().reply.isNotBlank())
            assertFalse(value.exchanges.last().reply.contains("<|"))
            assertFalse(value.exchanges.last().reply.contains("<think>"))
        }
        try {
            idle(); stage("of01-library-verify")
            if (controller.state.value.models.none {it.sha256 == RecommendedLocalModel.SHA256}) {
                stage("of01-import-existing-reviewed-model")
                controller.importModel(true) {input.inputStream()}; idle()
            }
            val model = controller.state.value.models.single {it.sha256 == RecommendedLocalModel.SHA256}
            stage("of01-load-context-2048"); controller.load(model, 2048); idle()
            stage("of01-explicit-original-turn"); controller.send("Write one short sentence about a peaceful garden.")
            val parent = idle().chat!!; terminal(parent)
            assertEquals(1, calls.get()); assertEquals(model.sha256, parent.modelSha256)
            val store = LocalConversationStore(historyRoot)
            val parentFile = File(historyRoot, "${parent.id}.json"); val parentHash = digest(parentFile)
            val parentSettings = parent.exchanges.single().generation!!
            assertEquals(2048, parentSettings.contextTokens)
            assertEquals(loads.first().second, parentSettings.threads)
            assertEquals(384, parentSettings.maxOutputTokens)
            assertEquals(BuildConfig.LOCAL_RUNTIME_SOURCE_SHA256, parentSettings.runtime!!.sourceSha256)
            stage("of01-reload-context-1024"); controller.load(model, 1024); idle()
            stage("of01-explicit-regeneration"); controller.branch(parent, 0, LocalBranchKind.REGENERATE)
            val child = idle().chat!!; terminal(child)
            assertNotEquals(parent.id, child.id); assertEquals(2, calls.get())
            assertEquals(LocalBranchKind.REGENERATE, child.origin!!.kind)
            assertEquals(1024, child.exchanges.single().generation!!.contextTokens)
            assertEquals(parentHash, digest(parentFile)); assertEquals(parent, store.load(parent.id))
            val childHash = digest(File(historyRoot, "${child.id}.json"))
            stage("of01-fork-without-generation"); controller.branch(child, 0, LocalBranchKind.FORK)
            val fork = idle().chat!!
            assertEquals(2, calls.get()); assertEquals(child.exchanges, fork.exchanges)
            assertEquals(LocalBranchKind.FORK, fork.origin!!.kind)
            stage("of01-return-to-parent-for-edit"); controller.openChat(parent.id); idle()
            stage("of01-explicit-edited-child")
            val editedQuestion = "Write one short sentence about a quiet forest."
            controller.branch(parent, 0, LocalBranchKind.EDIT, editedQuestion)
            val edited = idle().chat!!; terminal(edited)
            assertEquals(3, calls.get()); assertEquals(editedQuestion, edited.exchanges.single().question)
            assertEquals(LocalBranchKind.EDIT, edited.origin!!.kind)
            assertEquals(1024, edited.exchanges.single().generation!!.contextTokens)
            assertEquals(parentHash, digest(parentFile)); assertEquals(childHash, digest(File(historyRoot, "${child.id}.json")))
            controller.openChat(parent.id); idle()
            stage("of01-stop-on-first-visible-chunk")
            stopOnText.set {controller.cancel()}
            controller.branch(parent, 0, LocalBranchKind.REGENERATE)
            val stopped = idle().chat!!
            assertNotEquals(parent.id, stopped.id); assertEquals(4, calls.get())
            assertEquals(LocalReplyState.CANCELLED, stopped.exchanges.single().state)
            assertEquals(1024, stopped.exchanges.single().generation!!.contextTokens)
            assertEquals(parentHash, digest(parentFile)); assertEquals(childHash, digest(File(historyRoot, "${child.id}.json")))
            stage("of01-unload-and-read-without-replay"); controller.unload(); idle()
            assertNull(controller.state.value.loaded)
            controller.openChat(parent.id); assertEquals(parent, idle().chat)
            controller.openChat(child.id); assertEquals(child, idle().chat)
            controller.openChat(stopped.id); assertEquals(stopped, idle().chat)
            val beforeRecovery = histories(historyRoot)
            LocalConversationStore(historyRoot).recoverInterrupted()
            assertEquals(beforeRecovery, histories(historyRoot)); assertEquals(5, store.list().size)
            assertEquals(listOf(384, 384, 384, 384), limits.toList()); assertEquals(4, calls.get())
            assertEquals(globalBefore, histories(globalHistory))
            stage("of01-native-branches-parent-settings-cancellation-and-no-replay-complete")
        } finally {
            withContext(NonCancellable) {
                controller.cancel(); controller.leave(); idle(allowError = true)
                global.refreshLibrary(); withTimeout(30_000) {global.state.first {!it.busy}}
            }
            assertEquals(globalBefore, histories(globalHistory))
        }
    }}
