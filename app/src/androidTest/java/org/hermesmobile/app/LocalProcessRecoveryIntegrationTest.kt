package org.hermesmobile.app

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.hermesmobile.app.local.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Host-orchestrated real JNI/process/reboot probe. Only explicitly selected, isolated test roots. */
@RunWith(AndroidJUnit4::class)
class LocalProcessRecoveryIntegrationTest {
    private val modelSha = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    private val oldQuestion = "Write one hundred numbered gardening tips. Keep going until the list is complete."
    private val newQuestion = "Reply with exactly: Signal Grove recovered"
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun stage(name: String, values: Map<String, String> = emptyMap()) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("local_process_stage", name)
            values.forEach { (key, value) -> putString(key, value) }
        })
    }
    private fun inputRoot(): File {
        val name = InstrumentationRegistry.getArguments().getString("localOtherModelProbeDirectory").orEmpty()
        assumeTrue("Requires explicitly staged official Qwen test input", name.isNotBlank())
        assumeTrue("Native inference requires a 64-bit process", android.os.Process.is64Bit())
        require(name.matches(Regex("other-model-probe-[a-f0-9]{12}")))
        return File(context.noBackupFilesDir, name)
    }
    private fun recoveryRoot(): File {
        val name = InstrumentationRegistry.getArguments().getString("localProcessRecoveryProbe").orEmpty()
        assumeTrue("Requires the exact externally interrupted probe", name.isNotBlank())
        require(name.matches(Regex("interruption-[a-f0-9-]{36}")))
        return File(inputRoot(), name).also {assertTrue(it.isDirectory)}
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input ->
            val buffer = ByteArray(1024 * 1024)
            while(true) {val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n)}
        }
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    private class ObservedNative(root: File, directory: String) : LocalInference {
        private val actual = NativeLocalInference(root, directory)
        val loads = AtomicInteger(0); val generations = AtomicInteger(0)
        val requests = java.util.Collections.synchronizedList(mutableListOf<List<LocalChatMessage>>())
        var afterText: ((String) -> Unit)? = null
        @Volatile var latestNativeText: String = ""
        @Volatile var latestNativeEnd: LocalGenerationEnd? = null
        override suspend fun load(file: File, context: Int, threads: Int) {loads.incrementAndGet(); actual.load(file, context, threads)}
        override suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit): LocalGenerationEnd {
            generations.incrementAndGet(); requests.add(messages.toList())
            latestNativeText = ""; latestNativeEnd = null
            return actual.generate(messages, maxTokens) {text ->
                latestNativeText = text
                onText(text); afterText?.invoke(text)
            }.also {latestNativeEnd = it}
        }
        override fun cancel() = actual.cancel()
        override suspend fun unload() = actual.unload()
    }
    private fun controller(root: File, guard: UpdateWorkGuard, observer: ObservedNative): LocalAiController =
        LocalAiController(File(root, "models"), File(root, "chats"), context.applicationInfo.nativeLibraryDir,
            android.os.Process.is64Bit(), {
                val info = ActivityManager.MemoryInfo()
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
                info.availMem
            }, guard, engineFactory = {observer})
    private suspend fun idle(controller: LocalAiController, guard: UpdateWorkGuard, allowError: Boolean = false): LocalAiState {
        val value = withTimeout(90_000) {controller.state.first {!it.busy}}
        withTimeout(5_000) {guard.active.first {!it}}
        if(!allowError) assertNull("Local probe failed: ${value.error}", value.error)
        return value
    }
    private fun marker(root: File): JsonObject = Json.parseToJsonElement(File(root, "ready.json").readText()).jsonObject
    private fun id(root: File): String = marker(root).getValue("chat").jsonPrimitive.content.also {require(UUID.fromString(it).toString() == it)}

    @Test fun prepareInterruptedNativeGeneration() = runBlocking<Unit> {
        val source = File(inputRoot(), "qwen.gguf")
        assertEquals(491400032L, source.length()); assertEquals(modelSha, hash(source))
        val root = File(inputRoot(), "interruption-${UUID.randomUUID()}"); check(root.mkdir())
        val models = File(root, "models"); val chats = File(root, "chats")
        val guard = UpdateWorkGuard(); val native = ObservedNative(models, context.applicationInfo.nativeLibraryDir)
        val controller = controller(root, guard, native)
        val release = CountDownLatch(1); val ready = CompletableDeferred<Unit>(); val first = AtomicBoolean(false)
        native.afterText = {text ->
            if(text.any {!it.isWhitespace()} && first.compareAndSet(false, true)) {
                val live = controller.state.value.chat!!
                assertEquals(LocalReplyState.GENERATING, live.exchanges.single().state)
                val stored = LocalConversationStore(chats).load(live.id)
                assertEquals(LocalReplyState.GENERATING, stored.exchanges.single().state)
                assertEquals(oldQuestion, stored.exchanges.single().question)
                val value = buildJsonObject {
                    put("schema", 1); put("chat", live.id); put("model", modelSha)
                    put("pid", android.os.Process.myPid()); put("streamed_chars", text.length)
                    put("chat_sha256", hash(File(chats, "${live.id}.json")))
                }.toString().toByteArray()
                val pending = File(root, "ready.part")
                pending.outputStream().use {it.write(value); it.fd.sync()}
                Files.move(pending.toPath(), File(root, "ready.json").toPath(), StandardCopyOption.ATOMIC_MOVE)
                stage("native-output-ready-for-external-force-stop", mapOf("local_process_probe_directory" to root.name,
                    "local_process_chat" to live.id, "local_process_pid" to android.os.Process.myPid().toString()))
                ready.complete(Unit)
                check(release.await(90, TimeUnit.SECONDS)) {"External force-stop was not delivered"}
            }
        }
        try {
            idle(controller, guard)
            controller.importModel(true) {source.inputStream()}
            val model = idle(controller, guard).models.single(); assertEquals(modelSha, model.sha256)
            controller.load(model, 2048); idle(controller, guard)
            controller.send(oldQuestion)
            withTimeout(90_000) {ready.await()}
            assertEquals(1, native.loads.get()); assertEquals(1, native.generations.get())
            // The host verifies persisted GENERATING + live PID, then kills this exact debug process.
            withTimeout(90_000) {awaitCancellation()}
        } finally {
            release.countDown()
            withContext(NonCancellable) {controller.leave(); idle(controller, guard, allowError = true)}
        }
    }
    @Test fun coldRecoveryMarksInterruptedAndOnlyAnExplicitFreshSendGenerates() = runBlocking<Unit> {
        val root = recoveryRoot(); val chatId = id(root); val chats = File(root, "chats")
        assertNotEquals(marker(root).getValue("pid").jsonPrimitive.int, android.os.Process.myPid())
        val before = LocalConversationStore(chats).load(chatId)
        assertEquals(LocalReplyState.GENERATING, before.exchanges.single().state)
        assertEquals(marker(root).getValue("chat_sha256").jsonPrimitive.content, hash(File(chats, "$chatId.json")))
        val guard = UpdateWorkGuard(); val native = ObservedNative(File(root, "models"), context.applicationInfo.nativeLibraryDir)
        val controller = controller(root, guard, native)
        try {
            val initial = idle(controller, guard)
            assertNull(initial.loaded); assertNull(initial.chat)
            assertEquals(LocalReplyState.INTERRUPTED, initial.history.single().lastState)
            val recovered = LocalConversationStore(chats).load(chatId)
            assertEquals(before.copy(exchanges = before.exchanges.map {it.copy(state = LocalReplyState.INTERRUPTED)}), recovered)
            controller.openChat(chatId); assertEquals(recovered, idle(controller, guard).chat)
            val stable = hash(File(chats, "$chatId.json")); delay(300)
            assertEquals(stable, hash(File(chats, "$chatId.json")))
            assertEquals(0, native.loads.get()); assertEquals(0, native.generations.get())
            stage("new-process-read-only-recovery-without-native-replay")
            controller.load(initial.models.single(), 2048); idle(controller, guard)
            controller.send(newQuestion)
            val complete = idle(controller, guard).chat!!
            assertEquals(2, complete.exchanges.size); assertEquals(recovered.exchanges.first(), complete.exchanges.first())
            assertEquals(LocalReplyState.COMPLETE, complete.exchanges.last().state)
            // Lifecycle acceptance checks actual native output, not stochastic instruction wording.
            assertEquals(LocalGenerationEnd.COMPLETE, native.latestNativeEnd)
            assertTrue(native.latestNativeText.isNotBlank())
            assertEquals(native.latestNativeText, complete.exchanges.last().reply)
            assertEquals(1, native.loads.get()); assertEquals(1, native.generations.get())
            assertEquals(listOf(newQuestion), native.requests.single().filter {it.role == "user"}.map {it.text})
            controller.unload(); assertNull(idle(controller, guard).loaded)
            assertTrue(LocalModelStore(File(root, "models")).verifiedFile(modelSha).isFile)
            stage("only-explicit-fresh-question-generated-and-persisted", mapOf("local_process_probe_directory" to root.name,
                "local_process_chat" to chatId, "local_process_pid" to android.os.Process.myPid().toString()))
        } finally {withContext(NonCancellable) {controller.leave(); idle(controller, guard, allowError = true)}}
    }
    @Test fun rebootKeepsCompletedAndInterruptedHistoryWithoutAutomaticLoad() = runBlocking<Unit> {
        val root = recoveryRoot(); val chatId = id(root); val file = File(root, "chats/$chatId.json")
        val expected = InstrumentationRegistry.getArguments().getString("localRecoveredChatSha256").orEmpty()
        require(expected.matches(Regex("[a-f0-9]{64}"))); assertEquals(expected, hash(file))
        val before = LocalConversationStore(File(root, "chats")).load(chatId)
        val guard = UpdateWorkGuard(); val native = ObservedNative(File(root, "models"), context.applicationInfo.nativeLibraryDir)
        val controller = controller(root, guard, native)
        try {
            val initial = idle(controller, guard)
            assertNull(initial.loaded); assertEquals(modelSha, initial.models.single().sha256)
            controller.openChat(chatId)
            val chat = idle(controller, guard).chat!!
            assertEquals(listOf(LocalReplyState.INTERRUPTED, LocalReplyState.COMPLETE), chat.exchanges.map {it.state})
            assertEquals(oldQuestion, chat.exchanges.first().question)
            assertEquals(newQuestion, chat.exchanges.last().question)
            assertEquals(before, chat)
            assertTrue(chat.exchanges.last().reply.isNotBlank())
            delay(300)
            assertEquals(expected, hash(file)); assertEquals(0, native.loads.get()); assertEquals(0, native.generations.get())
            stage("reboot-history-retained-without-model-load-or-replay")
            controller.deleteModel(initial.models.single())
            assertTrue(idle(controller, guard).models.isEmpty()); assertEquals(expected, hash(file))
        } finally {withContext(NonCancellable) {controller.leave(); idle(controller, guard, allowError = true)}}
    }
}
