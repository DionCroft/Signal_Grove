package org.hermesmobile.app

import android.app.ActivityManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Bundle
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
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
    @Test fun verifiedLfm3PromptCountMatchesTheGenerationBoundary() = runBlocking {
        val (root, model) = modelInput()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = NativeLocalEngine(root, context.applicationInfo.nativeLibraryDir)
        try {
            val capacity = 512
            stage("prompt-boundary-load-start")
            withTimeout(120_000) { engine.load(model, context = capacity, threads = 4) }
            stage("prompt-boundary-load-complete")
            val short = localChatPrompt(emptyList(), "Reply with a dot.")
            val shortCount = engine.countPromptTokens(short)
            assertTrue(shortCount in 2 until capacity)
            val (request, promptTokens) = (350..500 step 4).firstNotNullOf { words ->
                val prompt = localChatPrompt(emptyList(), "garden ".repeat(words) + "Reply with a dot.")
                val measured = engine.countPromptTokens(prompt)
                (prompt to measured).takeIf { capacity - measured in 8..24 }
            }
            val room = capacity - promptTokens
            assertEquals(promptTokens, engine.countPromptTokens(request))
            stage("prompt-boundary-overflow-check")
            var streamed = false
            val refused = runCatching { engine.generate(request, room + 1) { streamed = true } }.exceptionOrNull()
            assertTrue(refused is LocalEngineException && refused.code == 2)
            assertFalse(streamed)
            stage("prompt-boundary-exact-fit-start")
            val end = withTimeout(120_000) { engine.generate(request, room) {} }
            assertTrue(end in setOf(LocalGenerationEnd.COMPLETE, LocalGenerationEnd.TOKEN_LIMIT))
            assertEquals(shortCount, engine.countPromptTokens(short))
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putInt("context_capacity", capacity)
                putInt("prompt_tokens", promptTokens)
                putInt("output_room", room)
                putInt("short_prompt_tokens", shortCount)
            })
            stage("native-prompt-count-boundary-complete")
        } finally { withContext(NonCancellable) { engine.unload() } }
    }
    @Test fun verifiedLfm3MeasuresExplicitContextProbe() = runBlocking {
        val requested = InstrumentationRegistry.getArguments().getString("localContextProbeCapacity")
        assumeTrue("Requires an explicit context capacity", requested != null)
        val capacity = requireNotNull(requested).toInt()
        require(capacity in setOf(4096, 8192, 16384))
        val workload = InstrumentationRegistry.getArguments().getString("localContextProbeWorkload")
        require(workload == null || (workload == "handoff" && capacity > 4096))
        val (root, model) = modelInput()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        probeMetric("available_memory_kib", memory.availMem / 1024)
        assertFalse("Android reports low memory before the context probe", memory.lowMemory)
        assertTrue("Available memory is below the existing model load floor",
            memory.availMem >= minimumModelLoadMemoryBytes(model.length()))
        probeMetric("context_capacity", capacity.toLong())
        probeMetric("threads", 4)
        val power = context.getSystemService(PowerManager::class.java)
        val maxPss = AtomicInteger(Debug.getPss().toInt())
        val maxThermal = AtomicInteger(-1)
        val sampler = launch(Dispatchers.Default) {
            while (isActive) {
                maxPss.accumulateAndGet(Debug.getPss().toInt(), ::maxOf)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    maxThermal.accumulateAndGet(power.currentThermalStatus, ::maxOf)
                }
                delay(1000)
            }
        }
        val engine = NativeLocalEngine(root, context.applicationInfo.nativeLibraryDir)
        try {
            stage("context-probe-load-start")
            val loadStarted = SystemClock.elapsedRealtime()
            withTimeout(120_000) {
                if (capacity == 4096) engine.load(model, capacity) else engine.loadContextProbe(model, capacity, 4)
            }
            stage("context-probe-load-complete", SystemClock.elapsedRealtime() - loadStarted)
            probeMetric("loaded_pss_kib", Debug.getPss())

            val common = localChatPrompt(emptyList(), LOCAL_BENCHMARK_PROMPT)
            val commonTokens = engine.countPromptTokens(common)
            probeGeneration("common", engine, common, commonTokens, LOCAL_BENCHMARK_OUTPUT_CAP)
            if (workload == "handoff") {
                verifyLongHandoff(engine, capacity)
                stage("context-probe-complete")
                return@runBlocking
            }

            stage("context-probe-near-window-count-start")
            val (near, nearTokens) = withTimeout(30_000) { nearWindowPrompt(engine, capacity) }
            val room = capacity - nearTokens
            probeMetric("near_window_output_room", room.toLong())
            val refused = runCatching {
                engine.generate(near, room + 1) { fail("Overflow must not emit output") }
            }.exceptionOrNull()
            assertTrue(refused is LocalEngineException && refused.code == 2)
            stage("context-probe-overflow-refused")

            val pending = async(start = CoroutineStart.UNDISPATCHED) { engine.generate(near, room) {} }
            delay(250)
            assertTrue("The near-window request must still be active for Stop", pending.isActive)
            val stopStarted = SystemClock.elapsedRealtime()
            engine.cancel()
            assertEquals(LocalGenerationEnd.CANCELLED, withTimeout(10_000) { pending.await() })
            stage("context-probe-stop-complete", SystemClock.elapsedRealtime() - stopStarted)

            // A successful near-window reply also verifies recovery after Stop.
            probeGeneration("near_window", engine, near, nearTokens, room)
            stage("context-probe-complete")
        } finally {
            withContext(NonCancellable) {
                sampler.cancelAndJoin()
                probeMetric("max_sampled_pss_kib", maxPss.get().toLong())
                if (maxThermal.get() >= 0) probeMetric("max_android_thermal_status", maxThermal.get().toLong())
                engine.unload()
            }
        }
    }

    private fun probeMetric(name: String, value: Long) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putLong(name, value)
        })
    }

    private suspend fun probeGeneration(
        name: String, engine: NativeLocalEngine, messages: List<LocalChatMessage>, tokens: Int, outputCap: Int,
        onText: (String) -> Unit = {}
    ) {
        probeMetric("${name}_prompt_tokens", tokens.toLong())
        probeMetric("${name}_output_cap", outputCap.toLong())
        stage("context-probe-${name}-generation-start")
        val started = SystemClock.elapsedRealtime()
        val result = withTimeout(180_000) { engine.generateMeasured(messages, outputCap, onText) }
        stage("context-probe-${name}-generation-complete", SystemClock.elapsedRealtime() - started)
        assertTrue(result.end in setOf(LocalGenerationEnd.COMPLETE, LocalGenerationEnd.TOKEN_LIMIT))
        val measurement = requireNotNull(result.measurement) { "Missing generation measurement for $name" }
        probeMetric("${name}_sampled_tokens", measurement.sampledTokens.toLong())
        probeMetric("${name}_sampling_ms", measurement.generationNanos / 1_000_000)
        val visible = requireNotNull(measurement.firstAnswerNanos) { "No visible answer within the $name output reserve" }
        probeMetric("${name}_first_answer_ms", visible / 1_000_000)
    }

    private suspend fun verifyLongHandoff(engine: NativeLocalEngine, capacity: Int) {
        val inputBand = if (capacity == 8192) 4608..5376 else 8448..9728
        var low = 1
        var high = 320
        var best: Pair<List<LocalChatMessage>, Int>? = null
        stage("context-probe-handoff-count-start")
        withTimeout(30_000) {
            while (low <= high) {
                val rows = (low + high) / 2
                val prompt = handoffPrompt(rows)
                val count = engine.countPromptTokens(prompt)
                if (count <= inputBand.last) {
                    best = prompt to count
                    low = rows + 1
                } else {
                    high = rows - 1
                }
            }
        }
        val (prompt, count) = requireNotNull(best)
        assertTrue("Handoff input did not reach the measured band",
            count in inputBand && count + LOCAL_BENCHMARK_OUTPUT_CAP <= capacity)

        val output = StringBuilder()
        probeGeneration("handoff", engine, prompt, count, LOCAL_BENCHMARK_OUTPUT_CAP) { output.append(it) }
        val words = Regex("[A-Za-z]+").findAll(output.toString()).map { it.value.lowercase() }.toSet()
        val matches = listOf("amethyst", "cedar", "thursday").count(words::contains)
        probeMetric("handoff_matched_facts", matches.toLong())
        assertEquals("Recall the intake label, storage shelf and current pickup day", 3, matches)
        assertFalse("The pickup day in the older handoff was replaced", "monday" in words)
    }

    private fun handoffPrompt(rowsPerShift: Int): List<LocalChatMessage> {
        val jobs = listOf("poster proofs", "spare cables", "lighting checks", "stool repairs", "label sheets", "plant trays")
        val outcomes = listOf("checked by both volunteers", "packed for the next shift", "ready for the workshop",
            "returned to the supply desk", "counted and recorded")
        val notes = listOf(
            "Intake for the Meadow exhibit: the label colour is Amethyst and the original pickup day is Monday.",
            "Storage for the Meadow exhibit: the reserved shelf is Cedar.",
            "Pickup update for the Meadow exhibit: the team moved pickup to Thursday. This replaces Monday."
        )
        val history = notes.mapIndexed { shift, note ->
            val log = buildString {
                appendLine("Volunteer workshop log for shift ${shift + 1}.")
                appendLine(note)
                repeat(rowsPerShift) { row ->
                    val ticket = shift * rowsPerShift + row + 1
                    appendLine("Ticket $ticket: ${jobs[row % jobs.size]} were ${outcomes[(row + shift) % outcomes.size]}.")
                }
            }
            LocalExchange(log, "I recorded the shift handoff.", LocalReplyState.COMPLETE)
        }
        return localChatPrompt(history, "For the Meadow exhibit, give the intake label colour, reserved shelf, " +
            "and latest pickup day, in that order. Answer with the three values only.")
    }

    private suspend fun nearWindowPrompt(engine: NativeLocalEngine, capacity: Int): Pair<List<LocalChatMessage>, Int> {
        // Synthetic two-byte filler keeps even the 16K input within the unchanged
        // request byte limit. The actual GGUF tokenizer sets the final boundary.
        val target = capacity - LOCAL_BENCHMARK_OUTPUT_CAP
        var low = 0
        var high = LOCAL_PROMPT_BYTE_LIMIT / 4
        var best: Pair<List<LocalChatMessage>, Int>? = null
        while (low <= high) {
            val size = (low + high) / 2
            val prompt = localChatPrompt(emptyList(), "Background test markers:\n" + " x".repeat(size) +
                "\nIgnore the test markers. " + LOCAL_BENCHMARK_PROMPT)
            val count = engine.countPromptTokens(prompt)
            if (count <= target) {
                best = prompt to count
                low = size + 1
            } else high = size - 1
        }
        return requireNotNull(best).also { (_, count) ->
            check(count in (target - 4)..target) { "The prompt did not approach the measured capacity" }
        }
    }

    @Test fun verifiedLfm3StreamsOnlyAnswerTextForGardenPrompt() = runBlocking {
        val (root, model) = modelInput()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = NativeLocalEngine(root, context.applicationInfo.nativeLibraryDir)
        try {
            withTimeout(90_000) { engine.load(model, context = 2048, threads = 4) }
            var reply = ""
            val result = withTimeout(30_000) { engine.generateMeasured(listOf(
                LocalChatMessage("system", "You are an on-device text assistant in Signal Grove. You have no access to Hermes, servers, projects, credentials or tools. Never claim to have performed an action. Give advice as advice."),
                LocalChatMessage("user", "Write a short sentence about a peaceful garden")
            ), 384) {
                assertFalse(it.contains("<think>")); assertFalse(it.contains("</think>"))
                assertTrue(it.startsWith(reply)); reply = it
            } }
            assertEquals(LocalGenerationEnd.COMPLETE, result.end); assertTrue(reply.isNotBlank())
            assertTrue(reply.contains("garden", ignoreCase = true))
            val sample = checkNotNull(result.measurement)
            assertTrue(sample.sampledTokens in 1..384)
            assertTrue(sample.sampledTokensPerSecond > 0)
            val firstAnswer = checkNotNull(sample.firstAnswerNanos)
            assertTrue(firstAnswer > 0)
            stage("native-measured-first-visible-answer", firstAnswer / 1_000_000)
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
            if (controller.state.value.models.none {it.sha256 == CuratedPhoneModel.LFM3.sha256}) {
                stage("of01-import-existing-reviewed-model")
                controller.importModel(true) {input.inputStream()}; idle()
            }
            val model = controller.state.value.models.single {it.sha256 == CuratedPhoneModel.LFM3.sha256}
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
