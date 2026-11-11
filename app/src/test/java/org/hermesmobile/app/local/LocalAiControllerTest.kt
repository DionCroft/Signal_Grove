package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAiControllerTest {
    @get:Rule val temp = TemporaryFolder()
    private class Engine : LocalInference {
        var loads = 0; var unloads = 0; var cancels = 0
        var blockGeneration = false
        var reply = "Phone reply"
        var failure: Exception? = null
        var unloadGate: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<List<LocalChatMessage>>()
        override suspend fun load(file: File, context: Int, threads: Int) { assertTrue(file.isFile); loads++ }
        override suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit): LocalGenerationEnd {
            requests.add(messages.toList()); failure?.let { throw it }; onText(reply)
            if (blockGeneration) awaitCancellation()
            return LocalGenerationEnd.COMPLETE
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
        engine.reply = "Review the intended profile and API_SERVER_KEY. [S1]"
        controller.help(LocalHelpTopic.AUTH, observations, false); advanceUntilIdle()
        val help = controller.state.value.helper!!
        assertEquals(LocalReplyState.COMPLETE, help.state); assertEquals(model.sha256, help.modelSha256)
        assertEquals(before, controller.state.value.chat); assertEquals(1, LocalConversationStore(history).list().size)
        val helperPrompt = engine.requests.last().joinToString {it.text}
        assertFalse(helperPrompt.contains("Only ordinary chat")); assertFalse(helperPrompt.contains("secret-pass")); assertTrue(helperPrompt.contains(LocalSetupHelper.VERSION))
        controller.send("Next chat turn"); advanceUntilIdle()
        val chatPrompt = engine.requests.last().joinToString {it.text}
        assertFalse(chatPrompt.contains(LocalSetupHelper.VERSION)); assertFalse(chatPrompt.contains("55 epoch"))
        controller.leave(); advanceUntilIdle(); assertNull(controller.state.value.helper)
    }
    @Test fun unsupportedHelperSettingIsWithheldAndNoChatIsWritten() = runTest {
        val root = temp.newFolder(); val model = model(root); val history = temp.newFolder(); val engine = Engine()
        val controller = controller(root, history, engine, UpdateWorkGuard(), StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        controller.load(model); advanceUntilIdle(); engine.reply = "Set API_ENABLED=true. [S1]"
        controller.help(LocalHelpTopic.AUTH, null, false); advanceUntilIdle()
        assertEquals("helper_unverified", controller.state.value.error)
        assertEquals("", controller.state.value.helper!!.text); assertEquals(LocalReplyState.FAILED, controller.state.value.helper!!.state)
        assertTrue(LocalConversationStore(history).list().isEmpty()); assertNull(controller.state.value.chat)
        controller.leave(); advanceUntilIdle()
    }
}
