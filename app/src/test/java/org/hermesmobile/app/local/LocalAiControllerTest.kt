package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAiControllerTest {
    @get:Rule val temp = TemporaryFolder()
    private class Engine : LocalInference {
        var runtime: LocalRuntimePolicy? = null
        override val runtimePolicy: LocalRuntimePolicy? get() = runtime
        val loadedSettings = mutableListOf<Pair<Int, Int>>()
        val outputCaps = mutableListOf<Int>()
        var loads = 0; var unloads = 0; var cancels = 0
        var blockGeneration = false
        var reply = "Phone reply"
        var measurement: LocalGenerationMeasurement? = null
        var failure: Exception? = null
        var unloadGate: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<List<LocalChatMessage>>()
        val measured = mutableListOf<List<LocalChatMessage>>()
        var promptTokens: Int? = null
        override suspend fun countPromptTokens(messages: List<LocalChatMessage>): Int? {
            measured.add(messages)
            return promptTokens
        }
        val helperChoices = mutableListOf<List<String>>()
        override suspend fun load(file: File, context: Int, threads: Int) { assertTrue(file.isFile); loadedSettings.add(context to threads); loads++ }
        override suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit): LocalGenerationEnd {
            outputCaps.add(maxTokens); requests.add(messages.toList()); failure?.let { throw it }; onText(reply)
            if (blockGeneration) awaitCancellation()
            return LocalGenerationEnd.COMPLETE
        }
        override suspend fun generateMeasured(
            messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit
        ) = LocalGenerationResult(generate(messages, maxTokens, onText), measurement)

        override suspend fun selectHelperChecks(messages: List<LocalChatMessage>, choices: LocalCheckChoices, maxTokens: Int,
            onText: (String) -> Unit): LocalGenerationEnd {
            helperChoices.add(choices.ids); return generate(messages, maxTokens, onText)
        }
        override fun cancel() { cancels++ }
        override suspend fun unload() { unloads++; unloadGate?.await() }
    }
    private fun model(root: File, name: String = "Fixture"): InstalledLocalModel {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun u32(n: Int) = out.writeInt(Integer.reverseBytes(n))
            fun u64(n: Long) = out.writeLong(java.lang.Long.reverseBytes(n))
            fun text(s: String) { val b = s.toByteArray(); u64(b.size.toLong()); out.write(b) }
            fun field(k: String, v: String) { text(k); u32(8); text(v) }
            u32(0x46554747); u32(3); u64(1); u64(4)
            field("general.name", name); field("general.architecture", "lfm3")
            text("lfm3.context_length"); u32(4); u32(2048)
            field("tokenizer.chat_template", "{{ bos_token }}{{ messages }}")
            text("weight"); u32(1); u64(1); u32(0); u64(0)
            while (bytes.size() % 32 != 0) out.writeByte(0)
            out.write(ByteArray(4))
        }
        return LocalModelStore(root) { Long.MAX_VALUE }.importModel(bytes.toByteArray().inputStream(), true)
    }
    private fun controller(models: File, history: File, engine: Engine, guard: UpdateWorkGuard, dispatcher: CoroutineDispatcher,
        supported: Boolean = true, freeMemory: Long = Long.MAX_VALUE) = LocalAiController(models, history, "unused-test-library-directory", supported,
        { freeMemory }, guard, dispatcher, dispatcher) { engine }

    @Test fun curatedDownloadsKeepSeparatePartialsAndDiscardOnlyTheSelectedFile() = runTest {
        val root = temp.newFolder()
        val existing = model(root)
        val driver = Engine()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val lfm = CuratedPhoneModel.LFM3
        val qwen = CuratedPhoneModel.QWEN
        val lfmPart = File(root, "${lfm.sha256}.part").apply { writeText("first fixture") }
        val qwenPart = File(root, "${qwen.sha256}.part").apply { writeText("second fixture") }
        val firstBytes = lfmPart.readBytes()
        val controller = controller(root, temp.newFolder(), driver, UpdateWorkGuard(), dispatcher)
        advanceUntilIdle()

        assertEquals(lfmPart.length(), controller.state.value.partialBytes(lfm))
        assertEquals(qwenPart.length(), controller.state.value.partialBytes(qwen))
        controller.discardDownload(qwen)
        advanceUntilIdle()

        assertFalse(qwenPart.exists())
        assertArrayEquals(firstBytes, lfmPart.readBytes())
        assertEquals(0L, controller.state.value.partialBytes(qwen))
        assertEquals(lfmPart.length(), controller.state.value.partialBytes(lfm))
        assertEquals(listOf(existing), controller.state.value.models)
        assertNull(controller.state.value.loaded)
        assertEquals(0, driver.loads)
        assertTrue(driver.requests.isEmpty())
    }

    @Test fun reviewedDownloadsPersistInOrderAndDiscardOnlyTheSelectedEntry() = runTest {
        val root = temp.newFolder()
        val history = temp.newFolder()
        val engine = Engine()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val qwen = CuratedPhoneModel.QWEN
        val lfm = CuratedPhoneModel.LFM3
        val firstPart = File(root, "${qwen.sha256}.part").apply { writeText("qwen partial") }
        val otherPart = File(root, "${lfm.sha256}.part").apply { writeText("lfm partial") }
        val otherBytes = otherPart.readBytes()
        val first = controller(root, history, engine, UpdateWorkGuard(), dispatcher)
        advanceUntilIdle()

        first.enqueueDownload(qwen, licenceReviewed = true)
        advanceUntilIdle()
        first.enqueueDownload(lfm, licenceReviewed = true)
        advanceUntilIdle()
        first.enqueueDownload(qwen, licenceReviewed = true)
        advanceUntilIdle()

        val reopened = controller(root, history, engine, UpdateWorkGuard(), dispatcher)
        advanceUntilIdle()
        assertEquals(listOf(qwen, lfm), reopened.state.value.queuedDownloads)
        assertNull(reopened.state.value.activeDownload)
        assertEquals(firstPart.length(), reopened.state.value.partialBytes(qwen))
        assertArrayEquals(otherBytes, otherPart.readBytes())
        reopened.discardDownload(qwen)
        advanceUntilIdle()
        assertEquals(listOf(lfm), reopened.state.value.queuedDownloads)
        assertFalse(firstPart.exists())
        assertArrayEquals(otherBytes, otherPart.readBytes())
        assertEquals(0, engine.loads)
        assertTrue(engine.requests.isEmpty())
    }

    @Test fun startingALegacyPartialRequiresCurrentLicenceReview() = runTest {
        val root = temp.newFolder()
        val qwen = CuratedPhoneModel.QWEN
        val partial = File(root, "${qwen.sha256}.part").apply { writeText("old reviewed bytes") }
        val snapshot = partial.readBytes()
        val controller = controller(root, temp.newFolder(), Engine(), UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.resumeDownload(qwen)
        advanceUntilIdle()
        assertEquals("download_review_required", controller.state.value.error)
        assertNull(controller.state.value.activeDownload)
        assertFalse(controller.state.value.busy)
        assertArrayEquals(snapshot, partial.readBytes())
    }

    @Test fun corruptReceiptCannotBeReplacedAndTheInstalledLibraryStaysReadable() = runTest {
        val root = temp.newFolder()
        val installed = model(root)
        val bad = File(root, "download-queue").apply { writeText("1\nincomplete receipt") }
        val original = bad.readBytes()
        val controller = controller(root, temp.newFolder(), Engine(), UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertEquals(listOf(installed), controller.state.value.models)
        assertTrue(controller.state.value.downloadQueueUnavailable)
        controller.enqueueDownload(CuratedPhoneModel.LFM3, licenceReviewed = true)
        advanceUntilIdle()
        assertEquals("download_queue_unreadable", controller.state.value.error)
        assertArrayEquals(original, bad.readBytes())
        assertEquals(listOf(installed), controller.state.value.models)
    }

    @Test fun publishedPinReceiptDoesNotReturnAfterTheInstalledModelDisappears() {
        val root = temp.newFolder()
        val store = LocalModelStore(root)
        val first = LocalDownloadQueue(store)
        first.add(CuratedPhoneModel.LFM3)
        first.add(CuratedPhoneModel.QWEN)
        val completed = CuratedPhoneModel.LFM3.sha256
        assertEquals(listOf(CuratedPhoneModel.QWEN), first.pending(setOf(completed)))
        assertEquals(listOf(CuratedPhoneModel.QWEN), LocalDownloadQueue(store).pending(emptySet()))
    }

    @Test fun benchmarkUsesTheLoadedLimitsAndLeavesChatHistoryUntouched() = runTest {
        val root = temp.newFolder()
        val first = model(root)
        val history = temp.newFolder()
        val engine = Engine().apply { measurement = LocalGenerationMeasurement(4, 500_000_000, 900_000_000) }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = LocalAiController(root, history, "unused-test-library-directory", true,
            { Long.MAX_VALUE }, UpdateWorkGuard(), dispatcher, dispatcher, readLoadPssKiB = { 262_144 }) { engine }
        advanceUntilIdle()
        controller.saveModelPerformance(first, LocalModelPerformance(1024, 1, 192))
        advanceUntilIdle()
        controller.load(first)
        advanceUntilIdle()
        assertEquals(262_144, controller.state.value.performance.loadPssKiB)
        controller.send("A private question")
        advanceUntilIdle()
        val chat = checkNotNull(controller.state.value.chat)
        val file = File(history, chat.id + ".json")
        val before = file.readBytes()
        val lastReply = checkNotNull(controller.state.value.performance.lastReply)
        assertEquals(8.0, checkNotNull(lastReply.measurement).sampledTokensPerSecond, 0.01)

        val benchmarkMeasurement = LocalGenerationMeasurement(6, 500_000_000, null)
        engine.measurement = benchmarkMeasurement
        controller.benchmark(first.sha256)
        advanceUntilIdle()
        assertEquals(localChatPrompt(emptyList(), LOCAL_BENCHMARK_PROMPT), engine.requests.last())
        assertEquals(listOf(192, 192), engine.outputCaps)
        assertEquals(lastReply, controller.state.value.performance.lastReply)
        assertEquals(LocalBenchmarkResult(LocalBenchmarkEnd.COMPLETE, benchmarkMeasurement),
            controller.state.value.performance.benchmark)
        assertEquals(chat, controller.state.value.chat)
        assertArrayEquals(before, file.readBytes())
        assertEquals(listOf(chat.id), LocalConversationStore(history).list().map { it.id })

        controller.unload()
        advanceUntilIdle()
        assertEquals(LocalPerformanceDiagnostics(), controller.state.value.performance)
    }

    @Test fun benchmarkTimeoutAndStopReleaseWorkGuardWithoutSavingAnExchange() = runTest {
        val root = temp.newFolder()
        val first = model(root)
        val history = temp.newFolder()
        val engine = Engine().apply { blockGeneration = true }
        val guard = UpdateWorkGuard()
        val controller = controller(root, history, engine, guard, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.load(first)
        advanceUntilIdle()
        controller.benchmark(first.sha256)
        runCurrent()
        assertTrue(guard.active.value)
        advanceTimeBy(LOCAL_BENCHMARK_TIMEOUT_MILLIS)
        runCurrent()
        assertEquals(LocalBenchmarkResult(LocalBenchmarkEnd.TIMED_OUT), controller.state.value.performance.benchmark)
        assertFalse(guard.active.value)

        controller.benchmark(first.sha256)
        runCurrent()
        assertTrue(guard.active.value)
        controller.cancel()
        runCurrent()
        assertEquals(LocalBenchmarkResult(LocalBenchmarkEnd.STOPPED), controller.state.value.performance.benchmark)
        assertFalse(guard.active.value)
        assertTrue(LocalConversationStore(history).list().isEmpty())
        assertEquals(2, engine.requests.size)
    }

    @Test fun savedPerformanceIsPerModelAndOnlyChangesGenerationAfterLoad() = runTest {
        val root = temp.newFolder()
        val first = model(root)
        val second = model(root, "Other")
        val history = temp.newFolder()
        val engine = Engine().apply { promptTokens = 40 }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(root, history, engine, UpdateWorkGuard(), dispatcher)
        advanceUntilIdle()
        controller.load(first)
        advanceUntilIdle()
        val original = checkNotNull(controller.state.value.loadedSettings)
        val battery = LocalPerformancePreset.BATTERY_SAVER.forModel(first.metadata.trainedContext).copy(outputTokens = 128)
        controller.saveModelPerformance(first, battery)
        advanceUntilIdle()
        assertEquals(original, controller.state.value.loadedSettings)
        assertEquals(mapOf(first.sha256 to battery), controller.state.value.modelPerformance)
        assertEquals(1, engine.loads)

        controller.send("Use the original request limits")
        advanceUntilIdle()
        assertEquals(384, engine.outputCaps.single())
        val chat = checkNotNull(controller.state.value.chat)
        controller.load(first)
        advanceUntilIdle()
        val preview = controller.previewPrompt(chat, first.sha256, "Use the saved limits") as LocalPromptPreview.Exact
        assertEquals(LocalPromptBudget(40, 1024, 128), preview.budget)
        controller.send("Use the saved limits")
        advanceUntilIdle()
        val completed = checkNotNull(controller.state.value.chat)
        val recorded = LocalConversationStore(history).load(completed.id).exchanges.map { it.generation }
        assertEquals(listOf(original, LocalGenerationSettings(1024, 1, 128)), recorded)
        assertEquals(listOf(384, 128), engine.outputCaps)
        assertEquals(1024 to 1, engine.loadedSettings.last())
        controller.leave()
        advanceUntilIdle()

        val reopenedEngine = Engine()
        val reopened = controller(root, history, reopenedEngine, UpdateWorkGuard(), dispatcher)
        advanceUntilIdle()
        assertEquals(mapOf(first.sha256 to battery), reopened.state.value.modelPerformance)
        assertNull(reopened.state.value.loadedSettings)
        assertEquals(0, reopenedEngine.loads)
        reopened.load(second)
        advanceUntilIdle()
        assertEquals(384, reopened.state.value.loadedSettings?.maxOutputTokens)
        reopened.load(first)
        advanceUntilIdle()
        assertEquals(1024 to 1, reopenedEngine.loadedSettings.last())
        reopened.leave()
        advanceUntilIdle()
    }

    @Test fun invalidPerformanceDoesNotReplaceSavedSettingsAndDeletingModelRemovesItsSettings() = runTest {
        val root = temp.newFolder()
        val first = model(root)
        val engine = Engine()
        val controller = controller(root, temp.newFolder(), engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        val store = LocalModelStore(root)
        val saved = LocalModelPerformance(1024, 2, 192)
        controller.saveModelPerformance(first, saved)
        advanceUntilIdle()
        for (invalid in listOf(saved.copy(contextTokens = 4096), saved.copy(threadCap = 5), LocalModelPerformance(512, 1, 512))) {
            controller.saveModelPerformance(first, invalid)
            advanceUntilIdle()
            assertNotNull(controller.state.value.error)
            assertEquals(saved, store.performance(first))
            assertEquals(saved, controller.state.value.modelPerformance[first.sha256])
        }
        assertEquals(0, engine.loads)
        controller.deleteModel(first)
        advanceUntilIdle()
        assertNull(store.performance(first))
        assertTrue(controller.state.value.modelPerformance.isEmpty())
        assertTrue(controller.state.value.models.isEmpty())
    }

    @Test fun presetLimitsRespectTheModelAndDeviceThreadCount() {
        val balanced = LocalPerformancePreset.BALANCED.forModel(768)
        assertTrue(balanced.allowedBy(768))
        assertFalse(balanced.allowedBy(511))
        assertEquals(LocalGenerationSettings(768, 2, 384), balanced.resolve(2))
        assertEquals(1, balanced.resolve(0).threads)
        assertEquals(4, balanced.resolve(32).threads)
        assertEquals(LocalModelPerformance(1024, 4, 384), LocalPerformancePreset.FAST.forModel(2048))
        assertEquals(LocalModelPerformance(1024, 1, 384), LocalPerformancePreset.BATTERY_SAVER.forModel(2048))
    }

    @Test fun cancellingBeforeDispatchReleasesBusyStateAndUpdateGuard() = runTest {
        val guard = UpdateWorkGuard(); val engine = Engine(); val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(temp.newFolder(), temp.newFolder(), engine, guard, dispatcher)
        assertTrue(controller.state.value.busy); controller.cancel(); advanceUntilIdle()
        assertFalse(controller.state.value.busy); assertFalse(guard.active.value); assertEquals(0, engine.loads)
    }
    @Test fun coldRecoveryNeverLoadsAModelOrReplaysTheInterruptedQuestion() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder()
        val value = LocalConversation(modelSha256 = model.sha256, updatedAt = 1,
            exchanges = listOf(LocalExchange("Old question", "partial", LocalReplyState.GENERATING)))
        LocalConversationStore(history).save(value)
        val engine = Engine(); val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertEquals(LocalReplyState.INTERRUPTED, controller.state.value.history.single().lastState)
        assertTrue(engine.requests.isEmpty()); assertEquals(0, engine.loads)
    }
    @Test fun cancellationIsSavedAndNextSendContainsOnlyTheNewQuestion() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder(); val engine = Engine(); val guard = UpdateWorkGuard()
        val controller = controller(root, history, engine, guard, StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(model); advanceUntilIdle(); engine.blockGeneration = true
        controller.send("Cancelled question"); advanceUntilIdle(); assertTrue(controller.state.value.busy)
        controller.cancel(); advanceUntilIdle()
        val stopped = controller.state.value.chat!!
        assertEquals(LocalReplyState.CANCELLED, stopped.exchanges.single().state)
        assertEquals(stopped, LocalConversationStore(history).load(stopped.id)); assertFalse(guard.active.value)
        engine.blockGeneration = false; controller.send("New question"); advanceUntilIdle()
        assertEquals(listOf("New question"), engine.requests.last().filter { it.role == "user" }.map { it.text })
        assertEquals(LocalReplyState.COMPLETE, controller.state.value.chat!!.exchanges.last().state)
        controller.leave(); advanceUntilIdle()
    }
    @Test fun aConversationKeepsItsModelUntilTheUserStartsANewOne() = runTest {
        val root = temp.newFolder(); val first = model(root); val second = model(root, "Other model"); val engine = Engine()
        val controller = controller(root, temp.newFolder(), engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(first); advanceUntilIdle(); controller.send("First chat"); advanceUntilIdle()
        val conversation = controller.state.value.chat!!
        controller.load(second); advanceUntilIdle(); controller.send("Wrong model"); advanceUntilIdle()
        assertEquals(conversation, controller.state.value.chat); assertEquals(1, engine.requests.size)
        controller.newChat(); controller.send("Explicit new chat"); advanceUntilIdle()
        assertEquals(second.sha256, controller.state.value.chat!!.modelSha256); assertEquals(2, engine.requests.size)
        controller.leave(); advanceUntilIdle()
    }
    @Test fun exactPromptBudgetReservesOutputAndRejectsOverflowBeforePublishing() = runTest {
        val root = temp.newFolder()
        val model = model(root)
        val history = temp.newFolder()
        val engine = Engine()
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.load(model)
        advanceUntilIdle()
        engine.promptTokens = 2048 - 384 + 1
        val rejected = controller.previewPrompt(null, model.sha256, "Preserve this draft")
        assertEquals(LocalPromptPreview.Exact(LocalPromptBudget(1665, 2048, 384)), rejected)
        assertFalse((rejected as LocalPromptPreview.Exact).budget.fits)
        var published = 0
        controller.send("Preserve this draft") { published++ }
        advanceUntilIdle()
        assertEquals("engine_2", controller.state.value.error)
        assertNull(controller.state.value.chat)
        assertTrue(LocalConversationStore(history).list().isEmpty())
        assertTrue(engine.requests.isEmpty())
        assertEquals(0, published)

        engine.promptTokens = 2048 - 384
        controller.send("Preserve this draft") { published++ }
        advanceUntilIdle()
        assertEquals(1, published)
        assertEquals(engine.measured.last(), engine.requests.single())
        assertEquals(384, engine.outputCaps.single())
        assertEquals("Preserve this draft", controller.state.value.chat!!.exchanges.single().question)
        controller.leave()
        advanceUntilIdle()
    }

    @Test fun branchPreflightUsesOnlyTheReviewedPrefixAndKeepsTheParentOnOverflow() = runTest {
        val root = temp.newFolder()
        val model = model(root)
        val history = temp.newFolder()
        val store = LocalConversationStore(history)
        val source = LocalConversation(modelSha256 = model.sha256, updatedAt = 1, exchanges = listOf(
            LocalExchange("Earlier question", "Earlier answer", LocalReplyState.COMPLETE),
            LocalExchange("Interrupted question", "Partial text", LocalReplyState.INTERRUPTED),
            LocalExchange("Selected question", "Selected answer", LocalReplyState.COMPLETE),
            LocalExchange("Later question", "Later answer", LocalReplyState.COMPLETE)))
        store.save(source)
        val sourceBytes = File(history, source.id + ".json").readBytes()
        val engine = Engine()
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.load(model)
        advanceUntilIdle()
        controller.openChat(source.id)
        advanceUntilIdle()
        engine.promptTokens = 2000
        val preview = controller.previewPrompt(source, model.sha256, "Edited question", beforeExchange = 2)
        assertEquals(LocalPromptPreview.Exact(LocalPromptBudget(2000, 2048, 384)), preview)
        controller.branch(source, 2, LocalBranchKind.EDIT, "Edited question") { error("Overflow published a child") }
        advanceUntilIdle()
        assertEquals("engine_2", controller.state.value.error)
        assertEquals(source, controller.state.value.chat)
        assertEquals(1, store.list().size)
        assertTrue(engine.requests.isEmpty())

        engine.promptTokens = 64
        controller.branch(source, 2, LocalBranchKind.EDIT, "Edited question")
        advanceUntilIdle()
        val prompt = engine.requests.single()
        assertEquals(engine.measured.last(), prompt)
        assertEquals(listOf("Earlier question", "Edited question"), prompt.filter { it.role == "user" }.map { it.text })
        assertEquals(listOf("Earlier answer"), prompt.filter { it.role == "assistant" }.map { it.text })
        assertArrayEquals(sourceBytes, File(history, source.id + ".json").readBytes())
        assertEquals(2, store.list().size)
        assertEquals(LocalPromptPreview.Unavailable, controller.previewPrompt(source, model.sha256, "Stale draft"))
        controller.leave()
        advanceUntilIdle()
    }

    @Test fun selectedContinuationPublishesOnlyReviewedTurnsAndSendsOnlyOnExplicitRequest() = runTest {
        val root = temp.newFolder()
        val model = model(root)
        val history = temp.newFolder()
        val store = LocalConversationStore(history)
        val source = LocalConversation(modelSha256 = model.sha256, updatedAt = 10, exchanges = listOf(
            LocalExchange("Keep first", "First answer", LocalReplyState.COMPLETE),
            LocalExchange("Interrupted", "Partial reply", LocalReplyState.INTERRUPTED),
            LocalExchange("Keep third", "Limited answer", LocalReplyState.TOKEN_LIMIT),
            LocalExchange("Omit fourth", "Fourth answer", LocalReplyState.COMPLETE)))
        store.save(source)
        val bytes = File(history, source.id + ".json").readBytes()
        val engine = Engine().apply { promptTokens = 80 }
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.load(model)
        advanceUntilIdle()
        controller.openChat(source.id)
        advanceUntilIdle()
        val selection = mutableListOf(0, 2)
        val preview = controller.previewSelectedPrompt(source, selection, "Unsent draft")
        assertEquals(LocalPromptPreview.Exact(LocalPromptBudget(80, 2048, 384)), preview)
        val reviewedMessages = engine.measured.single()
        assertEquals(LocalPromptPreview.Unavailable,
            controller.previewSelectedPrompt(source, listOf(1), "Unsent draft"))
        var child: LocalConversation? = null
        controller.continueWithSelectedTurns(source, selection) { child = it }
        selection.clear()
        advanceUntilIdle()
        val continued = checkNotNull(child)
        assertEquals(listOf(0, 2), continued.origin!!.selectedExchanges)
        assertEquals(listOf(source.exchanges[0], source.exchanges[2]), continued.exchanges)
        assertEquals(continued, LocalConversationStore(history).load(continued.id))
        assertTrue(engine.requests.isEmpty())
        assertArrayEquals(bytes, File(history, source.id + ".json").readBytes())

        controller.send("Unsent draft")
        advanceUntilIdle()
        assertEquals(reviewedMessages, engine.requests.single())
        assertEquals(listOf("Keep first", "Keep third", "Unsent draft"),
            engine.requests.single().filter { it.role == "user" }.map { it.text })
        assertEquals(continued.origin, store.load(continued.id).origin)
        assertArrayEquals(bytes, File(history, source.id + ".json").readBytes())
        controller.leave()
        advanceUntilIdle()
    }

    @Test fun reviewedSummaryUsesOnlySelectedSourceAndCommitsOnlyEditedText() = runTest {
        val root = temp.newFolder()
        val model = model(root)
        val history = temp.newFolder()
        val store = LocalConversationStore(history)
        val source = LocalConversation(modelSha256 = model.sha256, updatedAt = 10, exchanges = listOf(
            LocalExchange("First selected question", "First answer", LocalReplyState.COMPLETE),
            LocalExchange("Other private turn", "Another reply", LocalReplyState.COMPLETE),
            LocalExchange("Third selected question", "Third answer", LocalReplyState.TOKEN_LIMIT)))
        store.saveNew(source)
        val before = store.file(source.id).readBytes()
        val engine = Engine().apply { promptTokens = 80; reply = "Unreviewed suggestion" }
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.load(model)
        advanceUntilIdle()
        controller.openChat(source.id)
        advanceUntilIdle()

        val selected = mutableListOf(0, 2)
        var proposed: LocalSummaryDraft? = null
        controller.draftContinuationSummary(source, selected) { proposed = it }
        selected.clear()
        advanceUntilIdle()
        assertEquals(LocalSummaryDraft("Unreviewed suggestion", false), proposed)
        assertEquals(listOf(LOCAL_SUMMARY_OUTPUT_TOKENS), engine.outputCaps)
        val summaryInput = engine.requests.single().last().text
        assertTrue(summaryInput.contains("First selected question") && summaryInput.contains("Third answer"))
        assertFalse(summaryInput.contains("Other private turn"))
        assertEquals(listOf(source.id), store.list().map { it.id })
        assertArrayEquals(before, store.file(source.id).readBytes())

        val approved = "I checked the notes. Continue the garden work 🌱."
        val next = "What should we do next?"
        val preview = controller.previewSummaryPrompt(source, approved, next)
        assertEquals(LocalPromptPreview.Exact(LocalPromptBudget(80, 2048, 384)), preview)
        val reviewedPrompt = engine.measured.last()
        var child: LocalConversation? = null
        controller.continueWithReviewedSummary(source, listOf(0, 2), approved) { child = it }
        advanceUntilIdle()
        val created = checkNotNull(child)
        assertTrue(created.exchanges.isEmpty())
        assertEquals(LocalBranchKind.REVIEWED_SUMMARY, created.origin?.kind)
        assertEquals(listOf(0, 2), created.reviewedSummary?.sourceTurns)
        assertEquals(approved, created.reviewedSummary?.text)
        assertEquals(created, LocalConversationStore(history).load(created.id))
        assertEquals(1, engine.requests.size)

        controller.send(next)
        advanceUntilIdle()
        assertEquals(reviewedPrompt, engine.requests.last())
        assertEquals(listOf("system", "user"), engine.requests.last().map { it.role })
        assertTrue(engine.requests.last().last().text.contains(approved))
        assertFalse(engine.requests.last().last().text.contains("Unreviewed suggestion"))
        val completed = store.load(created.id)
        assertEquals(next, completed.exchanges.single().question)
        assertEquals(created.reviewedSummary, completed.reviewedSummary)
        assertArrayEquals(before, store.file(source.id).readBytes())

        controller.branch(completed, 0, LocalBranchKind.REGENERATE)
        advanceUntilIdle()
        assertEquals(reviewedPrompt, engine.requests.last())
        assertEquals(created.reviewedSummary, controller.state.value.chat?.reviewedSummary)
        controller.leave()
        advanceUntilIdle()
    }

    @Test fun cancelledOversizedAndInvalidSummariesNeverPublishAReviewOrChat() = runTest {
        val root = temp.newFolder()
        val model = model(root)
        val history = temp.newFolder()
        val store = LocalConversationStore(history)
        val source = LocalConversation(modelSha256 = model.sha256, updatedAt = 10,
            exchanges = listOf(LocalExchange("Question", "Reply", LocalReplyState.COMPLETE)))
        store.saveNew(source)
        val before = store.file(source.id).readBytes()
        val engine = Engine().apply { promptTokens = 2000 }
        val guard = UpdateWorkGuard()
        val controller = controller(root, history, engine, guard, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.load(model)
        advanceUntilIdle()
        controller.openChat(source.id)
        advanceUntilIdle()
        var reviews = 0
        controller.draftContinuationSummary(source, listOf(0)) { reviews++ }
        advanceUntilIdle()
        assertEquals("engine_2", controller.state.value.error)
        assertTrue(engine.requests.isEmpty())
        engine.promptTokens = 70
        engine.blockGeneration = true
        controller.draftContinuationSummary(source, listOf(0)) { reviews++ }
        runCurrent()
        assertTrue(guard.active.value)
        controller.cancel()
        advanceUntilIdle()
        assertFalse(guard.active.value)
        assertEquals(0, reviews)
        for (bad in listOf("", "é".repeat(2049), "Bad\u0000text", "\ud800")) {
            assertThrows(IllegalArgumentException::class.java) { store.createSummaryContinuation(source, listOf(0), bad, 12) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.createSummaryContinuation(source.copy(updatedAt = 9), listOf(0), "Valid notes", 12)
        }
        assertEquals(listOf(source.id), store.list().map { it.id })
        assertArrayEquals(before, store.file(source.id).readBytes())
        controller.leave()
        advanceUntilIdle()
    }

    @Test fun selectedContinuationRejectsInvalidAndStaleSelectionsBeforeWriting() {
        val history = temp.newFolder()
        val store = LocalConversationStore(history)
        val source = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 5, exchanges = listOf(
            LocalExchange("Keep", "Reply", LocalReplyState.COMPLETE),
            LocalExchange("Failed", "", LocalReplyState.FAILED),
            LocalExchange("Later", "Last", LocalReplyState.COMPLETE)))
        store.save(source)
        val original = File(history, source.id + ".json").readBytes()
        for (selection in listOf(emptyList(), listOf(2, 0), listOf(0, 0), listOf(1), listOf(3))) {
            assertThrows(IllegalArgumentException::class.java) { store.createSelectedContinuation(source, selection, 10) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.createSelectedContinuation(source.copy(updatedAt = 4), listOf(0), 10)
        }
        assertEquals(listOf(source.id), store.list().map { it.id })
        assertArrayEquals(original, File(history, source.id + ".json").readBytes())

        val child = store.createSelectedContinuation(source, listOf(0, 2), 10)
        val encoded = store.encode(child).toString(Charsets.UTF_8)
        assertTrue(encoded.contains("\"schema\":5") && encoded.contains("\"selected\":[0,2]"))
        for (replacement in listOf("[]", "[2,0]", "[0,0]", "[0,8]", "[\"0\",2]")) {
            val modified = encoded.replace("\"selected\":[0,2]", "\"selected\":$replacement")
            assertThrows(IllegalArgumentException::class.java) {
                LocalConversationStore.decodeBackupTranscript(child.id, modified.toByteArray()) {}
            }
        }
    }

    @Test fun leavingHoldsTheUpdateGuardUntilUnloadingActuallyFinishes() = runTest {
        val root = temp.newFolder(); val model = model(root); val engine = Engine(); val guard = UpdateWorkGuard()
        val controller = controller(root, temp.newFolder(), engine, guard, StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(model); advanceUntilIdle(); engine.unloadGate = CompletableDeferred()
        controller.leave(); controller.leave(); advanceUntilIdle()
        assertTrue(controller.state.value.busy); assertTrue(guard.active.value); assertEquals(1, engine.unloads)
        engine.unloadGate!!.complete(Unit); advanceUntilIdle()
        assertFalse(controller.state.value.busy); assertNull(controller.state.value.loaded); assertFalse(guard.active.value)
    }
    @Test fun deletingTheLoadedModelUnloadsBeforeRemovingItsBytes() = runTest {
        val root = temp.newFolder(); val model = model(root); val engine = Engine()
        val controller = controller(root, temp.newFolder(), engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(model); advanceUntilIdle(); engine.unloadGate = CompletableDeferred()
        controller.deleteModel(model); advanceUntilIdle()
        assertTrue(File(root, "${model.sha256}.gguf").exists()); assertTrue(controller.state.value.busy)
        engine.unloadGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, engine.unloads); assertFalse(File(root, "${model.sha256}.gguf").exists()); assertTrue(controller.state.value.models.isEmpty())
    }
    @Test fun incompatibleProcessAndMemoryShortageDoNotInvokeTheEngine() = runTest {
        for (supported in listOf(false, true)) {
            val root = temp.newFolder(); val model = model(root); val engine = Engine(); val guard = UpdateWorkGuard()
            val controller = controller(root, temp.newFolder(), engine, guard, StandardTestDispatcher(testScheduler), supported, 0); advanceUntilIdle()
            controller.load(model); advanceUntilIdle()
            assertEquals(0, engine.loads); assertNull(controller.state.value.loaded); assertFalse(guard.active.value)
            if (supported) assertEquals("insufficient_memory", controller.state.value.error)
        }
    }
    @Test fun anImportWithoutLicenceApprovalNeverOpensTheSource() = runTest {
        val root = temp.newFolder(); val controller = controller(root, temp.newFolder(), Engine(), UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        var opened = false; controller.importModel(false) { opened = true; byteArrayOf().inputStream() }; advanceUntilIdle()
        assertFalse(opened); assertTrue(root.listFiles()!!.isEmpty()); assertNotNull(controller.state.value.error)
    }
    @Test fun contextFailureIsSavedAndDoesNotReplayOnTheNextRequest() = runTest {
        val root = temp.newFolder(); val model = model(root); val engine = Engine(); val guard = UpdateWorkGuard()
        val controller = controller(root, temp.newFolder(), engine, guard, StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(model); advanceUntilIdle(); engine.failure = LocalEngineException(2)
        controller.send("Too much context"); advanceUntilIdle()
        assertEquals("engine_2", controller.state.value.error); assertEquals(LocalReplyState.FAILED, controller.state.value.chat!!.exchanges.single().state)
        assertFalse(guard.active.value); engine.failure = null; controller.send("Fresh message"); advanceUntilIdle()
        assertEquals(listOf("Fresh message"), engine.requests.last().filter { it.role == "user" }.map { it.text })
        controller.leave(); advanceUntilIdle()
    }
    @Test fun switchingReclaimsOldWeightsBeforeCheckingAvailableMemory() = runTest {
        val root = temp.newFolder(); val first = model(root); val second = model(root, "Switch"); val engine = Engine()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = LocalAiController(root, temp.newFolder(), "unused", true,
            {if(engine.loads == 0 || engine.unloads > 0) Long.MAX_VALUE else 0L}, UpdateWorkGuard(), dispatcher, dispatcher) {engine}
        advanceUntilIdle(); controller.load(first); advanceUntilIdle()
        assertEquals(first, controller.state.value.loaded)
        controller.load(second); advanceUntilIdle()
        assertEquals(2, engine.loads); assertEquals(second, controller.state.value.loaded)
        controller.leave(); advanceUntilIdle()
    }
    @Test fun cancellingAfterAtomicSaveBeforeMainResumesRecordsStoppedWithoutGenerating() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder(); val engine = Engine()
        val ioScheduler = TestCoroutineScheduler()
        val controller = LocalAiController(root, history, "unused", true, {Long.MAX_VALUE}, UpdateWorkGuard(),
            StandardTestDispatcher(testScheduler), StandardTestDispatcher(ioScheduler)) {engine}
        fun drain() {repeat(10) {testScheduler.runCurrent(); ioScheduler.runCurrent()}}
        drain(); controller.load(model); drain(); assertNotNull(controller.state.value.loaded)
        controller.send("Stopped after save"); testScheduler.runCurrent(); ioScheduler.runCurrent()
        assertEquals(LocalReplyState.GENERATING, LocalConversationStore(history).list().single().lastState)
        controller.cancel(); drain()
        assertEquals(LocalReplyState.CANCELLED, LocalConversationStore(history).list().single().lastState)
        assertTrue(engine.requests.isEmpty()); assertFalse(controller.state.value.busy)
        controller.leave(); drain()
    }
    @Test fun helperSourcesAndObservationsNeverEnterChatHistoryOrALaterChatRequest() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder(); val engine = Engine()
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(model); advanceUntilIdle(); controller.send("Only ordinary chat"); advanceUntilIdle()
        val before = controller.state.value.chat
        val observations = LocalHelpObservations.capture(false, "https://secret-user:secret-pass@host.invalid/profile?token=private", true, true, 55)
        engine.reply = "[\"host.api\",\"phone.api_key\"]"
        controller.help(LocalHelpTopic.AUTH, observations); advanceUntilIdle()
        val help = controller.state.value.helper!!
        assertEquals(LocalReplyState.COMPLETE, help.state); assertEquals(model.sha256, help.modelSha256)
        assertEquals(listOf("host.api", "phone.api_key"), help.checks.map {it.id})
        assertEquals(before, controller.state.value.chat); assertEquals(1, LocalConversationStore(history).list().size)
        assertEquals(listOf("host.api", "phone.api_key", "phone.auth_check", "phone.dashboard"), engine.helperChoices.single())
        val helperPrompt = engine.requests.last().joinToString {it.text}
        assertFalse(helperPrompt.contains("Only ordinary chat")); assertFalse(helperPrompt.contains("secret-pass")); assertTrue(helperPrompt.contains(LocalSetupHelper.VERSION))
        controller.send("Next chat turn"); advanceUntilIdle()
        val chatPrompt = engine.requests.last().joinToString {it.text}
        assertFalse(chatPrompt.contains(LocalSetupHelper.VERSION)); assertFalse(chatPrompt.contains("55 epoch"))
        controller.leave(); advanceUntilIdle(); assertNull(controller.state.value.helper)
    }
    @Test fun helperSchemaExcludesChecksFilteredBySharedPhonePreferences() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder(); val engine = Engine()
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(model); advanceUntilIdle()
        engine.reply = "[\"phone.demo\"]"
        controller.help(LocalHelpTopic.FIRST_SETUP, LocalHelpObservations.capture(false, "", true, true, 1)); advanceUntilIdle()
        assertFalse(engine.helperChoices.single().contains("phone.demo"))
        assertEquals(LocalReplyState.FAILED, controller.state.value.helper!!.state)
        assertEquals("helper_unverified", controller.state.value.error)
        assertTrue(LocalConversationStore(history).list().isEmpty()); assertNull(controller.state.value.chat)
        controller.leave(); advanceUntilIdle()
    }
    @Test fun unsupportedHelperSettingIsWithheldAndNoChatIsWritten() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder(); val engine = Engine()
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(model); advanceUntilIdle(); engine.reply = "Set API_ENABLED=true. [S1]"
        controller.help(LocalHelpTopic.AUTH, null); advanceUntilIdle()
        assertEquals("helper_unverified", controller.state.value.error)
        assertTrue(controller.state.value.helper!!.checks.isEmpty()); assertEquals(LocalReplyState.FAILED, controller.state.value.helper!!.state)
        assertTrue(LocalConversationStore(history).list().isEmpty()); assertNull(controller.state.value.chat)
        controller.leave(); advanceUntilIdle()
    }
    @Test fun cancellingAfterImportCommitReconcilesTheInstalledModelBeforeIdle() = runTest {
        val sourceRoot = temp.newFolder(); val expected = model(sourceRoot)
        val source = LocalModelStore(sourceRoot).verifiedFile(expected.sha256)
        val root = temp.newFolder(); val engine = Engine(); val guard = UpdateWorkGuard()
        val ioScheduler = TestCoroutineScheduler()
        val controller = LocalAiController(root, temp.newFolder(), "unused", true, {Long.MAX_VALUE}, guard,
            StandardTestDispatcher(testScheduler), StandardTestDispatcher(ioScheduler)) {engine}
        fun drain() {repeat(10) {testScheduler.runCurrent(); ioScheduler.runCurrent()}}
        drain(); controller.importModel(true) {source.inputStream()}
        testScheduler.runCurrent(); ioScheduler.runCurrent()
        assertEquals(listOf(expected), LocalModelStore(root).models())
        assertTrue(controller.state.value.models.isEmpty())
        controller.cancel(); testScheduler.runCurrent()
        val heldBeforeReconciliation = controller.state.value.busy && guard.active.value
        drain()
        assertEquals(listOf(expected), controller.state.value.models)
        assertTrue(heldBeforeReconciliation)
        assertFalse(controller.state.value.busy); assertFalse(guard.active.value)
        assertNull(controller.state.value.error); assertNull(controller.state.value.loaded)
        assertEquals(0, engine.loads); assertTrue(engine.requests.isEmpty())
        assertEquals(source.length(), LocalModelStore(root).verifiedFile(expected.sha256).length())
        controller.leave(); drain()
    }
    @Test fun failedStopReconciliationPreservesFilesReportsTheReasonAndReleasesTheGuard() = runTest {
        val sourceRoot = temp.newFolder(); val expected = model(sourceRoot)
        val source = LocalModelStore(sourceRoot).verifiedFile(expected.sha256)
        val root = temp.newFolder(); val guard = UpdateWorkGuard(); val engine = Engine()
        val ioScheduler = TestCoroutineScheduler()
        val controller = LocalAiController(root, temp.newFolder(), "unused", true, {Long.MAX_VALUE}, guard,
            StandardTestDispatcher(testScheduler), StandardTestDispatcher(ioScheduler)) {engine}
        fun drain() {repeat(10) {testScheduler.runCurrent(); ioScheduler.runCurrent()}}
        drain(); controller.importModel(true) {source.inputStream()}
        testScheduler.runCurrent(); ioScheduler.runCurrent()
        assertTrue(File(root, "${expected.sha256}.gguf").isFile)
        val corrupt = File(root, "${"b".repeat(64)}.gguf").apply {writeBytes(ByteArray(24))}
        controller.cancel(); testScheduler.runCurrent()
        val heldBeforeReconciliation = controller.state.value.busy && guard.active.value
        drain()
        assertEquals("gguf_version", controller.state.value.error)
        assertTrue(heldBeforeReconciliation)
        assertFalse(controller.state.value.busy); assertFalse(guard.active.value)
        assertTrue(File(root, "${expected.sha256}.gguf").isFile)
        assertArrayEquals(ByteArray(24), corrupt.readBytes())
        assertEquals(0, engine.loads); assertTrue(engine.requests.isEmpty())
        controller.leave(); drain()
    }
    @Test fun cancellingAClosingDocumentProviderRetainsNoImportAndReportsNoFalseError() = runBlocking {
        val root = temp.newFolder(); val guard = UpdateWorkGuard(); val engine = Engine()
        val controller = LocalAiController(root, temp.newFolder(), "unused", true, {Long.MAX_VALUE}, guard,
            Dispatchers.Default, Dispatchers.IO) {engine}
        val entered = CompletableDeferred<Unit>(); val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        var opens = 0
        val input = object : java.io.InputStream() {
            override fun read(): Int = read(ByteArray(1), 0, 1)
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                entered.complete(Unit)
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
                while(!closed.get() && System.nanoTime() < deadline) Thread.sleep(1)
                check(closed.get()) {"Provider was not closed"}
                throw java.io.IOException("Document provider closed after cancellation")
            }
            override fun close() {closed.set(true)}
        }
        try {
            withTimeout(5_000) {controller.state.first {!it.busy}; guard.active.first {!it}}
            controller.importModel(true) {opens++; input}
            withTimeout(5_000) {entered.await()}
            assertTrue(guard.active.value); controller.cancel()
            val stopped = withTimeout(5_000) {controller.state.first {!it.busy}}
            withTimeout(5_000) {guard.active.first {!it}}
            assertNull(stopped.error); assertTrue(closed.get()); assertEquals(1, opens)
            assertTrue(stopped.models.isEmpty()); assertTrue(root.listFiles()!!.isEmpty())
            assertEquals(0, engine.loads); assertTrue(engine.requests.isEmpty())
        } finally {
            input.close(); controller.leave()
            withTimeout(5_000) {controller.state.first {!it.busy}; guard.active.first {!it}}
        }
    }


    @Test fun explicitRegenerationPublishesOneChildAndKeepsTheOriginalBytes() = runTest {
        val root = temp.newFolder(); val item = model(root); val history = temp.newFolder(); val engine = Engine()
        val c = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        c.load(item); advanceUntilIdle(); c.send("First"); advanceUntilIdle(); c.send("Later"); advanceUntilIdle()
        val parent = c.state.value.chat!!; val file = File(history, "${parent.id}.json"); val before = file.readBytes()
        var selected = 0
        c.branch(parent, 0, LocalBranchKind.REGENERATE, onSelected = { child ->
            selected++; assertEquals(LocalReplyState.GENERATING, LocalConversationStore(history).load(child.id).exchanges.single().state)
        })
        // A second tap during the same action cannot create a second native request.
        c.branch(parent, 0, LocalBranchKind.REGENERATE); advanceUntilIdle()
        val child = c.state.value.chat!!
        assertNotEquals(parent.id, child.id); assertEquals(1, selected); assertEquals(3, engine.requests.size)
        assertEquals(listOf("First"), engine.requests.last().filter { it.role == "user" }.map { it.text })
        assertEquals(LocalReplyState.COMPLETE, child.exchanges.single().state)
        assertEquals(LocalBranchKind.REGENERATE, child.origin!!.kind); assertArrayEquals(before, file.readBytes())
        c.leave(); advanceUntilIdle()
    }
    @Test fun explicitEditUsesTheReviewedQuestionWithoutReplayingLaterTurns() = runTest {
        val root = temp.newFolder(); val item = model(root); val history = temp.newFolder(); val engine = Engine()
        val c = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        c.load(item); advanceUntilIdle(); c.send("First"); advanceUntilIdle(); c.send("Old second"); advanceUntilIdle()
        c.send("Unrelated later"); advanceUntilIdle()
        val parent = c.state.value.chat!!; val before = File(history, "${parent.id}.json").readBytes()
        c.branch(parent, 1, LocalBranchKind.EDIT, "Revised 你好 🌱"); advanceUntilIdle()
        val child = c.state.value.chat!!
        assertEquals(listOf("First", "Revised 你好 🌱"), engine.requests.last().filter {it.role == "user"}.map {it.text})
        assertEquals(LocalBranchKind.EDIT, child.origin!!.kind); assertEquals(2, child.exchanges.size)
        assertArrayEquals(before, File(history, "${parent.id}.json").readBytes())
        c.leave(); advanceUntilIdle()
    }
    @Test fun branchBrowsingNeedsNoLoadedWeightsAndNeverCallsNativeGeneration() = runTest {
        val root = temp.newFolder(); val item = model(root); val history = temp.newFolder(); val engine = Engine()
        val c = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        c.load(item); advanceUntilIdle(); c.send("First"); advanceUntilIdle(); c.send("Later"); advanceUntilIdle()
        val parent = c.state.value.chat!!; c.unload(); advanceUntilIdle()
        c.branch(parent, 0, LocalBranchKind.FORK); advanceUntilIdle()
        assertNull(c.state.value.loaded); assertEquals(1, c.state.value.chat!!.exchanges.size)
        assertEquals(2, engine.requests.size); assertEquals(1, engine.loads)
        val reopened = c.state.value.chat!!; c.openChat(parent.id); advanceUntilIdle(); c.openChat(reopened.id); advanceUntilIdle()
        assertEquals(reopened, c.state.value.chat); assertEquals(2, engine.requests.size)
    }
    @Test fun changedOrWrongModelBranchesAreRefusedBeforePublishingOrCallingTheEngine() = runTest {
        val root = temp.newFolder(); val first = model(root); val other = model(root, "Other"); val history = temp.newFolder(); val engine = Engine()
        val c = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        c.load(first); advanceUntilIdle(); c.send("Original"); advanceUntilIdle()
        val parent = c.state.value.chat!!; c.send("New turn"); advanceUntilIdle()
        c.branch(parent, 0, LocalBranchKind.REGENERATE); advanceUntilIdle()
        assertEquals(2, engine.requests.size); assertEquals(1, LocalConversationStore(history).list().size)
        val latest = c.state.value.chat!!; c.load(other); advanceUntilIdle()
        c.branch(latest, 0, LocalBranchKind.EDIT, "Wrong model"); advanceUntilIdle()
        assertEquals(latest, c.state.value.chat); assertEquals(2, engine.requests.size)
        assertEquals(1, LocalConversationStore(history).list().size)
        c.leave(); advanceUntilIdle()
    }
    @Test fun cancelledPublishedRegenerationIsDurableWithoutASecondNativeDispatch() = runTest {
        val root = temp.newFolder(); val item = model(root); val history = temp.newFolder(); val engine = Engine(); val guard = UpdateWorkGuard()
        val ioScheduler = TestCoroutineScheduler()
        val c = LocalAiController(root, history, "unused", true, {Long.MAX_VALUE}, guard,
            StandardTestDispatcher(testScheduler), StandardTestDispatcher(ioScheduler)) { engine }
        fun drain() { repeat(10) { testScheduler.runCurrent(); ioScheduler.runCurrent() } }
        drain(); c.load(item); drain(); c.send("Original"); drain()
        val parent = c.state.value.chat!!; val before = File(history, "${parent.id}.json").readBytes(); var selected = 0
        c.branch(parent, 0, LocalBranchKind.REGENERATE, onSelected = { selected++ })
        testScheduler.runCurrent(); ioScheduler.runCurrent()
        assertEquals(2, LocalConversationStore(history).list().size); assertEquals(1, engine.requests.size)
        c.cancel(); drain()
        val child = c.state.value.chat!!
        assertNotEquals(parent.id, child.id); assertEquals(1, selected)
        assertEquals(LocalReplyState.CANCELLED, child.exchanges.single().state)
        assertEquals(child, LocalConversationStore(history).load(child.id)); assertEquals(1, engine.requests.size)
        assertArrayEquals(before, File(history, "${parent.id}.json").readBytes()); assertFalse(guard.active.value)
        c.leave(); drain()
    }
    @Test fun failedRegenerationKeepsItsErrorAndLeavesTheParentReplyIntact() = runTest {
        val root = temp.newFolder(); val item = model(root); val history = temp.newFolder(); val engine = Engine()
        val c = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        c.load(item); advanceUntilIdle(); c.send("Original"); advanceUntilIdle()
        val parent = c.state.value.chat!!; val before = File(history, "${parent.id}.json").readBytes()
        engine.failure = LocalEngineException(2); c.branch(parent, 0, LocalBranchKind.REGENERATE); advanceUntilIdle()
        assertEquals("engine_2", c.state.value.error); assertEquals(LocalReplyState.FAILED, c.state.value.chat!!.exchanges.single().state)
        assertArrayEquals(before, File(history, "${parent.id}.json").readBytes()); assertEquals(2, engine.requests.size)
        c.refreshLibrary(); advanceUntilIdle(); assertEquals(2, engine.requests.size)
        c.leave(); advanceUntilIdle()
    }
    @Test fun unpublishedQuestionsKeepTheirDraftCallbackPendingUntilDurablePublication() = runTest {
        val root = temp.newFolder(); val item = model(root); val history = temp.newFolder(); val engine = Engine()
        val c = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        c.load(item); advanceUntilIdle(); var published = 0
        c.send("Never saved") { published++ }; c.cancel(); advanceUntilIdle()
        assertEquals(0, published); assertTrue(LocalConversationStore(history).list().isEmpty()); assertTrue(engine.requests.isEmpty())
        c.send("Saved") { value ->
            assertEquals(value, LocalConversationStore(history).load(value.id)); published++
        }; advanceUntilIdle()
        assertEquals(1, published); assertEquals(1, engine.requests.size)
        c.leave(); advanceUntilIdle()
    }

    @Test fun requestSettingsMatchTheLoadAndGenerationAndRegenerationRecordsTheNewLoad() = runTest {
        val root = temp.newFolder(); val item = model(root); val history = temp.newFolder(); val engine = Engine()
        engine.runtime = LocalRuntimePolicy("b".repeat(64), LocalSamplingPolicy.GREEDY_MODEL_METADATA_V1)
        val c = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        c.load(item, 512); advanceUntilIdle(); c.send("Original"); advanceUntilIdle()
        val parent = c.state.value.chat!!; val original = parent.exchanges.single().generation!!
        assertEquals(512, original.contextTokens); assertEquals(engine.loadedSettings.first().second, original.threads)
        assertEquals(engine.outputCaps.single(), original.maxOutputTokens); assertEquals(engine.runtime, original.runtime)
        c.load(item, 1024); advanceUntilIdle(); c.branch(parent, 0, LocalBranchKind.REGENERATE); advanceUntilIdle()
        val child = c.state.value.chat!!; val current = child.exchanges.single().generation!!
        assertEquals(1024, current.contextTokens); assertEquals(engine.loadedSettings.last().second, current.threads)
        assertEquals(engine.outputCaps.last(), current.maxOutputTokens); assertEquals(engine.runtime, current.runtime)
        assertEquals(original, LocalConversationStore(history).load(parent.id).exchanges.single().generation)
        assertEquals(current, LocalConversationStore(history).load(child.id).exchanges.single().generation)
        c.leave(); advanceUntilIdle()
    }
    @Test fun unidentifiedEngineDoesNotClaimTheNativeSamplingPolicy() = runTest {
        val root = temp.newFolder(); val item = model(root); val engine = Engine()
        val c = controller(root, temp.newFolder(), engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        c.load(item, 512); advanceUntilIdle(); c.send("Unknown runtime"); advanceUntilIdle()
        val record = c.state.value.chat!!.exchanges.single().generation!!
        assertEquals(512, record.contextTokens); assertNull(record.runtime)
        c.leave(); advanceUntilIdle()
    }

    @Test fun editingAnOlderParentAfterForkRequiresReopeningItBeforeAnyNewGeneration() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder()
        val engine = Engine(); val guard = UpdateWorkGuard()
        val controller = controller(root, history, engine, guard, StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); controller.load(model); advanceUntilIdle()
        controller.send("Original question"); advanceUntilIdle()
        val parent = controller.state.value.chat!!
        val parentFile = File(history, "${parent.id}.json"); val parentBytes = parentFile.readBytes()
        controller.branch(parent, 0, LocalBranchKind.FORK); advanceUntilIdle()
        val fork = controller.state.value.chat!!; val forkBytes = File(history, "${fork.id}.json").readBytes()
        assertNotEquals(parent.id, fork.id); assertEquals(1, engine.requests.size)
        controller.branch(parent, 0, LocalBranchKind.EDIT, "Reviewed edited question"); advanceUntilIdle()
        assertEquals("operation_failed", controller.state.value.error)
        assertEquals(fork, controller.state.value.chat); assertEquals(1, engine.requests.size)
        assertEquals(2, LocalConversationStore(history).list().size)
        assertArrayEquals(parentBytes, parentFile.readBytes())
        controller.openChat(parent.id); advanceUntilIdle(); assertNull(controller.state.value.error)
        controller.branch(parent, 0, LocalBranchKind.EDIT, "Reviewed edited question"); advanceUntilIdle()
        val edited = controller.state.value.chat!!
        assertNotEquals(parent.id, edited.id); assertEquals(LocalBranchKind.EDIT, edited.origin!!.kind)
        assertEquals("Reviewed edited question", edited.exchanges.single().question)
        assertEquals(2, engine.requests.size)
        assertEquals(listOf("Reviewed edited question"), engine.requests.last().filter {it.role == "user"}.map {it.text})
        assertArrayEquals(parentBytes, parentFile.readBytes())
        assertArrayEquals(forkBytes, File(history, "${fork.id}.json").readBytes())
        assertFalse(controller.state.value.busy); assertFalse(guard.active.value)
        controller.leave(); advanceUntilIdle()
    }

    private fun searchFixture(question: String, reply: String, updated: Long = 1) =
        LocalConversation(modelSha256 = "a".repeat(64), updatedAt = updated,
            exchanges = listOf(LocalExchange(question, reply, LocalReplyState.COMPLETE)))
    private fun searchFileSnapshot(root: File) = root.listFiles()!!.associate {it.name to it.readBytes().toList()}

    @Test fun historySearchKeepsTheUnfilteredInventoryAndNeverLoadsOrGenerates() = runTest {
        val history = temp.newFolder(); val store = LocalConversationStore(history)
        val a = searchFixture("First", "A peaceful garden"); val b = searchFixture("Other", "A forest", 2)
        store.saveNew(a); store.saveNew(b); val before = searchFileSnapshot(history)
        val engine = Engine(); val guard = UpdateWorkGuard()
        val controller = controller(temp.newFolder(), history, engine, guard, StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); val query = LocalHistoryQuery("garden", "a".repeat(64))
        controller.searchHistory(query); advanceUntilIdle()
        assertEquals(query, controller.state.value.historyQuery)
        assertEquals(listOf(a.id), controller.state.value.historyMatches.map {it.id})
        assertEquals(setOf(a.id, b.id), controller.state.value.history.map {it.id}.toSet())
        assertNull(controller.state.value.loaded); assertNull(controller.state.value.chat)
        controller.openChat(a.id); advanceUntilIdle(); assertEquals(a, controller.state.value.chat)
        assertEquals(0, engine.loads); assertTrue(engine.requests.isEmpty()); assertFalse(guard.active.value)
        assertEquals(before, searchFileSnapshot(history)); controller.leave(); advanceUntilIdle()
    }
    @Test fun clearingHistorySearchPreservesTheOpenConversationAndEveryFile() = runTest {
        val history = temp.newFolder(); val store = LocalConversationStore(history)
        val a = searchFixture("Garden", "reply"); val b = searchFixture("Forest", "reply", 2)
        store.saveNew(a); store.saveNew(b); val before = searchFileSnapshot(history)
        val engine = Engine(); val controller = controller(temp.newFolder(), history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); controller.openChat(a.id); advanceUntilIdle()
        controller.searchHistory(LocalHistoryQuery("Garden")); advanceUntilIdle(); controller.clearHistorySearch()
        assertNull(controller.state.value.historyQuery); assertTrue(controller.state.value.historyMatches.isEmpty())
        assertEquals(a, controller.state.value.chat); assertEquals(2, controller.state.value.history.size)
        assertEquals(before, searchFileSnapshot(history)); assertTrue(engine.requests.isEmpty())
        controller.leave(); advanceUntilIdle()
    }
    @Test fun cancelledQueuedSearchRetainsTheLastAppliedFilterWithoutAnyDeferredReplay() = runTest {
        val history = temp.newFolder(); val a = searchFixture("Garden", "reply")
        LocalConversationStore(history).saveNew(a); val before = searchFileSnapshot(history)
        val engine = Engine(); val guard = UpdateWorkGuard()
        val controller = controller(temp.newFolder(), history, engine, guard, StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); val applied = LocalHistoryQuery("Garden")
        controller.searchHistory(applied); advanceUntilIdle()
        controller.searchHistory(LocalHistoryQuery("Forest")); assertTrue(controller.state.value.busy); assertTrue(guard.active.value)
        controller.searchHistory(LocalHistoryQuery("Ignored while busy")); controller.cancel(); advanceUntilIdle()
        assertEquals(applied, controller.state.value.historyQuery)
        assertEquals(listOf(a.id), controller.state.value.historyMatches.map {it.id})
        assertFalse(controller.state.value.busy); assertFalse(guard.active.value)
        assertEquals(before, searchFileSnapshot(history)); assertEquals(0, engine.loads); assertTrue(engine.requests.isEmpty())
        controller.leave(); advanceUntilIdle()
    }
    @Test fun failedSearchPreservesThePreviousResultsAndMalformedSourceWithoutAPartialReplacement() = runTest {
        val history = temp.newFolder(); val store = LocalConversationStore(history)
        val a = searchFixture("Garden", "reply"); val b = searchFixture("Forest", "reply", 2)
        store.saveNew(a); store.saveNew(b); val engine = Engine()
        val controller = controller(temp.newFolder(), history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); val applied = LocalHistoryQuery("Garden")
        controller.searchHistory(applied); advanceUntilIdle()
        File(history, "${b.id}.json").writeText("{broken"); val damaged = searchFileSnapshot(history)
        controller.searchHistory(LocalHistoryQuery("Forest")); advanceUntilIdle()
        assertEquals("history_search", controller.state.value.error)
        assertEquals(applied, controller.state.value.historyQuery)
        assertEquals(listOf(a.id), controller.state.value.historyMatches.map {it.id})
        assertEquals(damaged, searchFileSnapshot(history)); assertTrue(engine.requests.isEmpty())
        controller.clearHistorySearch(); assertNull(controller.state.value.error)
        assertEquals(damaged, searchFileSnapshot(history)); controller.leave(); advanceUntilIdle()
    }
    @Test fun filteredHistoryRefreshesAfterExplicitDeletionAndLibraryRefresh() = runTest {
        val history = temp.newFolder(); val store = LocalConversationStore(history)
        val a = searchFixture("Garden", "reply"); val b = searchFixture("Forest", "reply", 2)
        store.saveNew(a); store.saveNew(b); val forest = File(history, "${b.id}.json").readBytes()
        val engine = Engine(); val controller = controller(temp.newFolder(), history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); val query = LocalHistoryQuery("Garden")
        controller.searchHistory(query); advanceUntilIdle(); controller.deleteChat(a.id); advanceUntilIdle()
        assertEquals(query, controller.state.value.historyQuery); assertTrue(controller.state.value.historyMatches.isEmpty())
        val newer = searchFixture("Another Garden", "new reply", 3); store.saveNew(newer)
        controller.refreshLibrary(); advanceUntilIdle()
        assertEquals(listOf(newer.id), controller.state.value.historyMatches.map {it.id})
        assertEquals(2, controller.state.value.history.size); assertArrayEquals(forest, File(history, "${b.id}.json").readBytes())
        assertTrue(engine.requests.isEmpty()); assertEquals(0, engine.loads); controller.leave(); advanceUntilIdle()
    }
    @Test fun aSearchFilterNeverChangesWhichGeneratedBranchIsOpenOrItsHistoryLimitInventory() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder(); val engine = Engine()
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); controller.load(model); advanceUntilIdle(); engine.reply = "Garden answer"
        controller.send("Garden question"); advanceUntilIdle(); val parent = controller.state.value.chat!!
        val original = File(history, "${parent.id}.json").readBytes()
        val query = LocalHistoryQuery("Garden", model.sha256); controller.searchHistory(query); advanceUntilIdle()
        engine.reply = "Forest answer"; controller.branch(parent, 0, LocalBranchKind.EDIT, "Forest question"); advanceUntilIdle()
        val child = controller.state.value.chat!!
        assertNotEquals(parent.id, child.id); assertEquals(LocalBranchKind.EDIT, child.origin!!.kind)
        assertEquals(query, controller.state.value.historyQuery)
        assertEquals(listOf(parent.id), controller.state.value.historyMatches.map {it.id})
        assertEquals(2, controller.state.value.history.size); assertEquals(2, engine.requests.size)
        assertEquals(model.sha256, controller.state.value.loaded!!.sha256)
        assertArrayEquals(original, File(history, "${parent.id}.json").readBytes())
        assertNull(controller.state.value.error); controller.leave(); advanceUntilIdle()
    }

    private fun labels(history:File)=LocalHistoryOrganization(File(history.canonicalFile.parentFile,history.name+"-organization"),LocalConversationStore(history))
    private fun labeledChat(hash:String="a".repeat(64))=LocalConversation(modelSha256=hash,updatedAt=1,
        exchanges=listOf(LocalExchange("Original garden question","Original reply",LocalReplyState.COMPLETE)))
    @Test fun organizingWithoutWeightsKeepsTheOpenChatAndFullInventoryWhileArchivedSearchRemainsExplicit()=runTest {
        val history=temp.newFolder();val chat=labeledChat();LocalConversationStore(history).saveNew(chat)
        val engine=Engine();val c=controller(temp.newFolder(),history,engine,UpdateWorkGuard(),StandardTestDispatcher(testScheduler));advanceUntilIdle()
        c.openChat(chat.id);advanceUntilIdle();val before=File(history,"${chat.id}.json").readBytes().toList()
        c.organizeChat(c.state.value.history.single(),LocalHistoryPreferences("Personal orchard",pinned=true,archived=true));advanceUntilIdle()
        assertEquals(chat,c.state.value.chat);assertEquals("Personal orchard",c.state.value.history.single().title)
        assertTrue(c.state.value.history.single().organization!!.preferences.archived);assertEquals(1,c.state.value.history.size)
        c.searchHistory(LocalHistoryQuery());advanceUntilIdle();assertTrue(c.state.value.historyMatches.isEmpty())
        c.searchHistory(LocalHistoryQuery("orchard",view=LocalHistoryView.ARCHIVED));advanceUntilIdle();assertEquals(listOf(chat.id),c.state.value.historyMatches.map {it.id})
        c.clearHistorySearch();assertNull(c.state.value.historyQuery);assertEquals(chat,c.state.value.chat)
        assertEquals(before,File(history,"${chat.id}.json").readBytes().toList());assertEquals(0,engine.loads);assertTrue(engine.requests.isEmpty())
    }
    @Test fun staleOrganizationReviewKeepsTheNewerLabelsAndDoesNotRetryTheProposedChange()=runTest {
        val history=temp.newFolder();val chat=labeledChat();LocalConversationStore(history).saveNew(chat);val engine=Engine()
        val c=controller(temp.newFolder(),history,engine,UpdateWorkGuard(),StandardTestDispatcher(testScheduler));advanceUntilIdle();val review=c.state.value.history.single()
        val newer=labels(history).update(chat.id,chat.modelSha256,null,LocalHistoryPreferences("Newer title",pinned=true))
        c.organizeChat(review,LocalHistoryPreferences("Stale title",archived=true));advanceUntilIdle()
        assertEquals("history_organization_changed",c.state.value.error);assertEquals(newer,labels(history).entries()[chat.id])
        c.refreshLibrary();advanceUntilIdle();assertEquals("Newer title",c.state.value.history.single().title)
        assertTrue(engine.requests.isEmpty());assertEquals(chat,LocalConversationStore(history).load(chat.id))
    }
    @Test fun cancellingQueuedOrganizationNeverPublishesOrReplaysIt()=runTest {
        val history=temp.newFolder();val chat=labeledChat();LocalConversationStore(history).saveNew(chat);val engine=Engine();val guard=UpdateWorkGuard()
        val c=controller(temp.newFolder(),history,engine,guard,StandardTestDispatcher(testScheduler));advanceUntilIdle()
        c.organizeChat(c.state.value.history.single(),LocalHistoryPreferences("Cancelled title"));c.cancel();advanceUntilIdle()
        assertTrue(labels(history).entries().isEmpty());assertEquals("Original garden question",c.state.value.history.single().title)
        c.refreshLibrary();advanceUntilIdle();assertTrue(labels(history).entries().isEmpty());assertFalse(guard.active.value);assertTrue(engine.requests.isEmpty())
    }
    @Test fun cancellationAfterOrganizationPublicationReconcilesFilesBeforeReturningToIdle()=runTest {
        val history=temp.newFolder();val chat=labeledChat();LocalConversationStore(history).saveNew(chat);val engine=Engine();val guard=UpdateWorkGuard()
        val main=StandardTestDispatcher(testScheduler);lateinit var c:LocalAiController;var armed=false;var fired=false
        val io=object:CoroutineDispatcher() {
            override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable) {
                main.dispatch(context,Runnable {block.run();if(armed&&!fired&&File(history.parentFile,history.name+"-organization/index.json").isFile){fired=true;c.cancel()}})
            }
        }
        c=LocalAiController(temp.newFolder(),history,"unused-test-library-directory",true,{Long.MAX_VALUE},guard,main,io){engine};advanceUntilIdle()
        armed=true;c.organizeChat(c.state.value.history.single(),LocalHistoryPreferences("Published title",pinned=true));advanceUntilIdle()
        assertTrue(fired);assertEquals("Published title",labels(history).entries().getValue(chat.id).preferences.title)
        assertEquals("Published title",c.state.value.history.single().title);assertFalse(c.state.value.busy);assertFalse(guard.active.value)
        assertTrue(engine.requests.isEmpty());assertEquals(chat,LocalConversationStore(history).load(chat.id))
    }
    @Test fun organizationLabelsNeverEnterGenerationContextOrReplaceStoredQuestions()=runTest {
        val models=temp.newFolder();val model=model(models);val history=temp.newFolder();val chat=labeledChat(model.sha256);LocalConversationStore(history).saveNew(chat);val engine=Engine()
        val c=controller(models,history,engine,UpdateWorkGuard(),StandardTestDispatcher(testScheduler));advanceUntilIdle();c.openChat(chat.id);advanceUntilIdle()
        c.organizeChat(c.state.value.history.single(),LocalHistoryPreferences("Display-only instruction",archived=true));advanceUntilIdle()
        c.load(model);advanceUntilIdle();c.send("A new real question");advanceUntilIdle()
        assertEquals(1,engine.requests.size);assertEquals(listOf("Original garden question","A new real question"),engine.requests.single().filter {it.role=="user"}.map {it.text})
        assertFalse(engine.requests.flatten().any {it.text.contains("Display-only instruction")});assertEquals("Original garden question",LocalConversationStore(history).load(chat.id).exchanges.first().question)
        c.leave();advanceUntilIdle()
    }
    @Test fun explicitDeletionRemovesOnlyThatChatsLabelsAndCallsItsAcknowledgementOnce()=runTest {
        val history=temp.newFolder();val store=LocalConversationStore(history);val parent=labeledChat();store.saveNew(parent);val child=store.createBranch(parent,0,LocalBranchKind.FORK,2)
        labels(history).update(parent.id,parent.modelSha256,null,LocalHistoryPreferences("Parent label",archived=true));val engine=Engine()
        val c=controller(temp.newFolder(),history,engine,UpdateWorkGuard(),StandardTestDispatcher(testScheduler));advanceUntilIdle();c.openChat(parent.id);advanceUntilIdle();var acknowledged=0
        c.deleteChat(parent.id){acknowledged++};advanceUntilIdle();assertEquals(1,acknowledged);assertNull(c.state.value.chat)
        assertEquals(listOf(child.id),c.state.value.history.map {it.id});assertTrue(labels(history).entries().isEmpty());assertEquals(child,store.load(child.id));assertTrue(engine.requests.isEmpty())
    }
    @Test fun corruptOrganizationIndexRefusesDeletionBeforeAnyTranscriptChange()=runTest {
        val history=temp.newFolder();val store=LocalConversationStore(history);val chat=labeledChat();store.saveNew(chat);val engine=Engine()
        val c=controller(temp.newFolder(),history,engine,UpdateWorkGuard(),StandardTestDispatcher(testScheduler));advanceUntilIdle();c.openChat(chat.id);advanceUntilIdle()
        val index=File(history.parentFile,history.name+"-organization/index.json").apply {writeText("{future")};var acknowledged=0
        c.deleteChat(chat.id){acknowledged++};advanceUntilIdle()
        assertEquals(0,acknowledged);assertEquals("history_organization",c.state.value.error);assertEquals(chat,store.load(chat.id));assertEquals(chat,c.state.value.chat);assertEquals("{future",index.readText())
    }
    @Test fun aNewBranchKeepsItsOwnDefaultLabelsAndPreservesTheOrganizedParent()=runTest {
        val history=temp.newFolder();val store=LocalConversationStore(history);val chat=labeledChat();store.saveNew(chat);val engine=Engine()
        val c=controller(temp.newFolder(),history,engine,UpdateWorkGuard(),StandardTestDispatcher(testScheduler));advanceUntilIdle();c.openChat(chat.id);advanceUntilIdle()
        c.organizeChat(c.state.value.history.single(),LocalHistoryPreferences("Parent only",pinned=true,archived=true));advanceUntilIdle()
        c.branch(chat,0,LocalBranchKind.FORK);advanceUntilIdle();val child=c.state.value.chat!!
        assertNull(c.state.value.history.single {it.id==child.id}.organization);assertEquals("Parent only",c.state.value.history.single {it.id==chat.id}.title)
        assertEquals(chat,store.load(chat.id));assertTrue(labels(history).entries().keys==setOf(chat.id));assertTrue(engine.requests.isEmpty())
    }
}
