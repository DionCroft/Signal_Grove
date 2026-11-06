package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.OkHttpHermesApiClient
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Real local HTTP decoding through the client and the durable submission guards. */
class RunPollingProgressStorageTest {
    private suspend fun fixture(block: suspend (HermesDao, MockWebServer, OkHttpHermesApiClient) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HermesDatabase::class.java).build()
        try {
            MockWebServer().use { server ->
                server.start()
                block(db.dao(), server, OkHttpHermesApiClient(ServerConfig(server.url("/p/mobile/").toString(), "synthetic-poll-token", true), true))
            }
        } finally { db.close() }
    }
    private fun conversation() = Conversation("poll-progress", "scope", "api", runId = "r", pendingKey = "submission", pendingAt = 1, status = "running", executionGeneration = 7)
    private fun reply(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun request(server: MockWebServer) {
        val request = server.takeRequest(5, TimeUnit.SECONDS) ?: error("Missing poll")
        assertEquals("GET", request.method); assertEquals("/p/mobile/v1/runs/r", request.path)
        assertEquals("Bearer synthetic-poll-token", request.headers["Authorization"])
        assertNull(request.headers["Cookie"]); assertEquals(0L, request.body.size)
    }

    @Test fun progressAndToolNameCannotSettleARunningRun() = runBlocking {
        fixture { dao, server, api ->
            val current = conversation(); dao.save(current)
            dao.save(ChatMessage("submission_assistant", current.key, "assistant", "Working", status = "running"))
            server.enqueue(reply("""{"run_id":"r","status":"running","execution_generation":7,"progress_at":1793989000.5,"tool":"run.completed","pending_controls":[]}"""))
            val observed = dao.applyRunSnapshot(current, api.run("r"))!!
            assertEquals("running", observed.status); assertEquals("running", dao.message("submission_assistant")!!.status)
            assertEquals("Working", dao.message("submission_assistant")!!.content); assertFalse(observed.status in RunStates.terminal)
            request(server)
            server.enqueue(reply("""{"run_id":"r","status":"completed","execution_generation":7,"progress_at":1793989001,"tool":"shell","output":"Actual terminal result"}"""))
            assertEquals("completed", dao.applyRunSnapshot(observed, api.run("r"))!!.status)
            assertEquals("Actual terminal result", dao.message("submission_assistant")!!.content)
            assertEquals("completed", dao.message("submission_assistant")!!.status); request(server)
        }
    }

    @Test fun freshProgressCannotOverrideAnOlderGenerationOrSubmission() = runBlocking {
        fixture { dao, server, api ->
            val current = conversation(); dao.save(current)
            val answer = ChatMessage("submission_assistant", current.key, "assistant", "Current", status = "running"); dao.save(answer)
            server.enqueue(reply("""{"run_id":"r","status":"completed","execution_generation":6,"progress_at":9999999999,"tool":"done","output":"Stale generation"}"""))
            assertNull(dao.applyRunSnapshot(current, api.run("r"))); request(server)
            assertEquals(current, dao.conversation(current.key)); assertEquals(answer, dao.message(answer.id))
            val next = current.copy(runId = "next", pendingKey = "next-submission", pendingAt = 2, executionGeneration = 8)
            dao.save(next); val nextAnswer = answer.copy(id = "next-submission_assistant", content = "Next"); dao.save(nextAnswer)
            server.enqueue(reply("""{"run_id":"r","status":"completed","execution_generation":7,"progress_at":9999999999,"tool":"done","output":"Stale submission"}"""))
            assertNull(dao.applyRunSnapshot(current, api.run("r"))); request(server)
            assertEquals(next, dao.conversation(current.key)); assertEquals(nextAnswer, dao.message(nextAnswer.id))
        }
    }

    @Test fun pollingProgressCannotRemoveApprovalStateOrReopenATerminalRun() = runBlocking {
        fixture { dao, server, api ->
            val current = conversation(); dao.save(current)
            server.enqueue(reply("""{"run_id":"r","status":"waiting_for_approval","execution_generation":7,"progress_at":1793989000,"tool":"completed","pending_controls":[{"kind":"approval","prompt_id":"p"}]}"""))
            val waiting = dao.applyRunSnapshot(current, api.run("r"))!!
            assertEquals("waiting_for_approval", waiting.status); assertFalse(waiting.status in RunStates.terminal); request(server)
            val settled = waiting.copy(status = "completed"); dao.save(settled)
            server.enqueue(reply("""{"run_id":"r","status":"running","execution_generation":7,"progress_at":9999999999,"tool":"shell"}"""))
            assertNull(dao.applyRunSnapshot(settled, api.run("r"))); request(server)
            assertEquals(settled, dao.conversation(settled.key))
        }
    }

    @Test fun progressDoesNotReplaceMissingStatusOrBypassRunIdentityValidation() = runBlocking {
        fixture { dao, server, api ->
            val current = conversation(); dao.save(current)
            server.enqueue(reply("""{"run_id":"r","execution_generation":7,"progress_at":9999999999,"tool":"completed"}"""))
            val unknown = dao.applyRunSnapshot(current, api.run("r"))!!
            assertEquals("unknown", unknown.status); assertFalse(unknown.status in RunStates.terminal); request(server)
            server.enqueue(reply("""{"run_id":"another-run","status":"completed","execution_generation":7,"progress_at":9999999999,"tool":"done"}"""))
            assertTrue(runCatching { api.run("r") }.exceptionOrNull() is IllegalArgumentException); request(server)
            assertEquals(unknown, dao.conversation(unknown.key))
        }
    }
}
