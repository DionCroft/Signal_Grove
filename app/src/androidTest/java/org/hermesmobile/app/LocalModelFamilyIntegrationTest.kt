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

/** Opt-in large-model integration: real controller/store/JNI, isolated roots, no network or Hermes state. */
@RunWith(AndroidJUnit4::class)
class LocalModelFamilyIntegrationTest {
    private val qwenSha = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    private val lfmSha = "43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452"
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun stage(name: String, elapsed: Long? = null) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("local_family_stage", name)
            elapsed?.let {putLong("local_family_elapsed_ms", it)}
        })
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n)}
        }
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    private fun input(name: String, sha: String, bytes: Long): File {
        assumeTrue("Local inference needs a 64-bit process", android.os.Process.is64Bit())
        val directory = InstrumentationRegistry.getArguments().getString("localOtherModelProbeDirectory").orEmpty()
        assumeTrue("Requires explicitly staged official test models", directory.isNotBlank())
        require(directory.matches(Regex("other-model-probe-[a-f0-9]{12}")))
        val root = File(context.noBackupFilesDir, directory)
        val file = File(root, name)
        assertEquals(root.canonicalFile, file.canonicalFile.parentFile)
        assertEquals(bytes, file.length()); assertEquals(sha, hash(file))
        return file
    }
    private fun roots(source: File): Pair<File, File> {
        val root = File(source.parentFile, "run-${UUID.randomUUID()}")
        check(root.mkdir())
        return File(root, "models") to File(root, "chats")
    }
    private fun createController(models: File, chats: File, guard: UpdateWorkGuard): LocalAiController =
        LocalAiController(models, chats, context.applicationInfo.nativeLibraryDir, android.os.Process.is64Bit(), {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            info.availMem
        }, guard)
    private suspend fun idle(controller: LocalAiController, guard: UpdateWorkGuard, allowError: Boolean = false): LocalAiState {
        val state = withTimeout(120_000) {controller.state.first {!it.busy}}
        withTimeout(5_000) {guard.active.first {!it}}
        if(!allowError) assertNull("Local operation failed: ${state.error}", state.error)
        return state
    }
    private suspend fun stop(controller: LocalAiController, guard: UpdateWorkGuard) = withContext(NonCancellable) {
        controller.leave(); idle(controller, guard, allowError = true)
    }
    private suspend fun importVerified(controller: LocalAiController, guard: UpdateWorkGuard, source: File, sha: String): InstalledLocalModel {
        val start = SystemClock.elapsedRealtime()
        controller.importModel(true) {source.inputStream()}
        val value = idle(controller, guard).models.single {it.sha256 == sha}
        assertEquals(source.length(), value.metadata.bytes)
        stage("import-${value.metadata.architecture}-complete", SystemClock.elapsedRealtime() - start)
        return value
    }
    private suspend fun send(controller: LocalAiController, guard: UpdateWorkGuard, expected: String): LocalConversation {
        controller.send("Reply with exactly: $expected")
        val chat = idle(controller, guard).chat!!
        assertEquals(LocalReplyState.COMPLETE, chat.exchanges.last().state)
        assertEquals(expected, chat.exchanges.last().reply.trim())
        return chat
    }
    @Test fun verifiedQwenImportChatHistoryAndDelete() = runBlocking {
        val source = input("qwen.gguf", qwenSha, 491400032L)
        val (models, chats) = roots(source)
        val guard = UpdateWorkGuard(); val controller = createController(models, chats, guard)
        var reopened: LocalAiController? = null
        val reopenedGuard = UpdateWorkGuard()
        try {
            idle(controller, guard)
            var opens = 0
            controller.importModel(false) {opens++; source.inputStream()}
            assertNotNull(idle(controller, guard, allowError = true).error)
            assertEquals(0, opens); assertTrue(LocalModelStore(models).models().isEmpty())
            val model = importVerified(controller, guard, source, qwenSha)
            assertEquals("qwen2", model.metadata.architecture)
            assertEquals(32768, model.metadata.trainedContext)
            controller.load(model, 2048); assertEquals(qwenSha, idle(controller, guard).loaded!!.sha256)
            val chat = send(controller, guard, "Signal Grove Qwen OK")
            assertEquals(qwenSha, chat.modelSha256)
            val file = File(chats, "${chat.id}.json"); val before = hash(file)
            controller.unload(); assertNull(idle(controller, guard).loaded)
            assertTrue(LocalModelStore(models).verifiedFile(qwenSha).isFile)
            stop(controller, guard)
            val cold = createController(models, chats, reopenedGuard); reopened = cold
            val initial = idle(cold, reopenedGuard)
            assertNull(initial.loaded); assertNull(initial.chat)
            assertEquals(chat.id, initial.history.single().id)
            cold.openChat(chat.id)
            assertEquals(chat, idle(cold, reopenedGuard).chat)
            assertNull(cold.state.value.loaded); assertEquals(before, hash(file))
            cold.deleteModel(model)
            assertTrue(idle(cold, reopenedGuard).models.isEmpty())
            assertEquals(chat, LocalConversationStore(chats).load(chat.id))
            assertEquals(before, hash(file)); assertTrue(source.isFile)
            stage("qwen-real-import-generation-unload-history-delete-complete")
        } finally {
            reopened?.let {stop(it, reopenedGuard)}
            stop(controller, guard)
        }
    }
    @Test fun verifiedLfmQwenSwitchKeepsHistoryBoundToEachFile() = runBlocking {
        val qwenSource = input("qwen.gguf", qwenSha, 491400032L)
        val lfmSource = input("lfm3.gguf", lfmSha, 1049615680L)
        val (models, chats) = roots(qwenSource)
        val guard = UpdateWorkGuard(); val controller = createController(models, chats, guard)
        try {
            idle(controller, guard)
            val qwen = importVerified(controller, guard, qwenSource, qwenSha)
            val lfm = importVerified(controller, guard, lfmSource, lfmSha)
            controller.load(qwen, 2048); idle(controller, guard)
            val first = send(controller, guard, "First Qwen reply")
            val before = hash(File(chats, "${first.id}.json"))
            controller.load(lfm, 2048); assertEquals(lfmSha, idle(controller, guard).loaded!!.sha256)
            controller.send("This mismatched chat must not generate")
            val rejected = idle(controller, guard, allowError = true)
            assertNotNull(rejected.error); assertEquals(first, rejected.chat)
            assertEquals(before, hash(File(chats, "${first.id}.json")))
            assertEquals(1, LocalConversationStore(chats).list().size)
            controller.newChat()
            assertEquals(lfmSha, controller.state.value.chat!!.modelSha256)
            assertNotEquals(first.id, controller.state.value.chat!!.id)
            val other = send(controller, guard, "LFM local chat")
            assertEquals(lfmSha, other.modelSha256)
            controller.load(qwen, 2048); assertEquals(qwenSha, idle(controller, guard).loaded!!.sha256)
            controller.openChat(first.id); assertEquals(first, idle(controller, guard).chat)
            val continued = send(controller, guard, "Second Qwen reply")
            assertEquals(first.id, continued.id); assertEquals(qwenSha, continued.modelSha256)
            assertEquals(2, continued.exchanges.size)
            assertEquals(first.exchanges.first(), continued.exchanges.first())
            assertEquals(other, LocalConversationStore(chats).load(other.id))
            controller.deleteModel(lfm)
            assertEquals(qwenSha, idle(controller, guard).loaded!!.sha256)
            controller.deleteModel(qwen)
            val deleted = idle(controller, guard)
            assertNull(deleted.loaded); assertTrue(deleted.models.isEmpty())
            assertEquals(2, deleted.history.size)
            assertEquals(continued, LocalConversationStore(chats).load(first.id))
            assertEquals(other, LocalConversationStore(chats).load(other.id))
            assertTrue(qwenSource.isFile); assertTrue(lfmSource.isFile)
            stage("two-family-real-switch-mismatch-refusal-and-history-complete")
        } finally {stop(controller, guard)}
    }
}
