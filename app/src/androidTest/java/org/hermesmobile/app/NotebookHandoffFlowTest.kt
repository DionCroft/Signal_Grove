package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.local.NotebookExcerpt
import org.hermesmobile.app.local.NotebookHandoffDraft
import org.hermesmobile.app.ui.HermesViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotebookHandoffFlowTest {
    @get:Rule(order = 0) val credentials = CredentialFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HermesApplication
    private val vm get() = ViewModelProvider(compose.activity)[HermesViewModel::class.java]

    @After fun restoreDemo() {
        compose.waitUntil(10_000) { !vm.busy.value && !vm.connecting.value }
        compose.runOnUiThread { vm.forgetServer() }
        compose.waitUntil(10_000) { vm.preferences.value.demo && !vm.connecting.value }
    }

    @Test fun sendingTheExactReviewedRequestLinksOnlyTheOriginatingNotebook() {
        val source = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val note = UUID.randomUUID().toString()
        val item = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val excerpt = "The north greenhouse gate opens at seven."
        val instruction = "Summarize the evidence in one sentence."
        val proposed = NotebookHandoffDraft(source, item, "Greenhouse fixture", listOf(NotebookExcerpt("Opening hours", excerpt)))
        val exact = checkNotNull(proposed.body(instruction))
        val notebooks = app.database.phoneNotebooks()
        runBlocking {
            notebooks.create(PhoneNotebook(source, "Greenhouse fixture", now, now))
            notebooks.create(PhoneNotebook(other, "Workshop fixture", now, now))
            app.database.phoneCaptures().save(PhoneCapture(note, excerpt, CaptureSource.NOTE.name, now))
            notebooks.attach(NotebookItem(item, source, NotebookItemKind.CAPTURE.name, note, "", "Opening hours", "", now),
                emptyList())
        }

        val received = AtomicReference<JsonObject?>()
        val reply = "The reviewed gate opens at seven."
        fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        try {
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = when {
                        request.path == "/v1/capabilities" -> json(
                            """{"features":{"session_resources":true,"session_chat":true,"run_events_sse":true},"endpoints":{"runs":{}}}""")
                        request.path.orEmpty().contains("/api/jobs?") -> json("""{"jobs":[]}""")
                        request.path.orEmpty().startsWith("/api/sessions?") -> json("""{"data":[],"has_more":false}""")
                        request.path == "/api/sessions" && request.method == "POST" -> {
                            val input = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                            json(buildJsonObject {
                                put("session", buildJsonObject { put("id", input.text("id")); put("title", "Notebook fixture") })
                            }.toString())
                        }
                        request.path == "/v1/runs" && request.method == "POST" -> {
                            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                            if (!received.compareAndSet(null, body)) json("{}").setResponseCode(409)
                            else json("""{"run_id":"notebook-fixture-run"}""")
                        }
                        request.path == "/v1/runs/notebook-fixture-run/events" -> MockResponse()
                            .setHeader("Content-Type", "text/event-stream")
                            .setBody("data: {\"event\":\"run.started\",\"run_id\":\"notebook-fixture-run\",\"seq\":0}\n\n" +
                                "data: {\"event\":\"run.completed\",\"run_id\":\"notebook-fixture-run\",\"output\":\"$reply\",\"seq\":1}\n\n")
                        else -> json("{}").setResponseCode(404)
                    }
                }
                val url = server.url("/").toString()
                compose.waitUntil(10_000) { !vm.connecting.value }
                compose.runOnUiThread { vm.configure(url, credentials.uniqueToken("notebook"), true) }
                compose.waitUntil(10_000) { !vm.connecting.value && vm.connectionStatus.value == "connected" }

                val token = checkNotNull(app.notebookHandoffs.offer(checkNotNull(proposed.encode())))
                compose.runOnUiThread {
                    app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.NOTEBOOK_HANDOFF_TOKEN, token))
                }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("notebook-instructions").fetchSemanticsNodes().size == 1 }
                compose.onNodeWithTag("notebook-instructions").performTextInput(instruction)
                compose.onNodeWithTag("notebook-handoff-preview").performClick()
                compose.onNodeWithTag("notebook-handoff-endpoint").assertTextEquals(url)
                compose.onNodeWithTag("notebook-handoff-body").assertTextEquals(exact)
                assertNull(received.get())
                assertTrue(runBlocking { notebooks.handoffs(source).first().isEmpty() })

                compose.onNodeWithTag("notebook-handoff-send").performClick()
                compose.waitUntil(15_000) { !vm.busy.value && vm.runStatus.value == "completed" }
                assertEquals(exact, checkNotNull(received.get()).text("input"))
                val attached = runBlocking { notebooks.handoffs(source).first() }.single()
                assertEquals(url, attached.endpoint)
                assertEquals(reply, attached.replyContent)
                assertTrue(runBlocking { notebooks.handoffs(other).first().isEmpty() })
            }
        } finally {
            val db = app.database.openHelper.writableDatabase
            db.execSQL("DELETE FROM phone_notebooks WHERE id IN (?,?)", arrayOf(source, other))
            db.execSQL("DELETE FROM phone_captures WHERE id = ?", arrayOf(note))
        }
    }
}
