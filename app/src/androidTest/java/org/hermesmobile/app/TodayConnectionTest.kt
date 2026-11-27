package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import androidx.lifecycle.ViewModelProvider
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.HermesViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TodayConnectionTest {
    @get:Rule(order = 0) val credentials = CredentialFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HermesApplication
    private val vm get() = ViewModelProvider(compose.activity)[HermesViewModel::class.java]

    @After fun restoreDemo() {
        compose.waitUntil(15_000) { !vm.connecting.value && !vm.todayRefreshing.value }
        compose.runOnUiThread { vm.forgetServer() }
        compose.waitUntil(15_000) { vm.preferences.value.demo && !vm.connecting.value }
    }

    @Test fun localTodayIsAvailableInAirplaneMode() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("todayOffline") == "true")
        assertEquals(1, android.provider.Settings.Global.getInt(app.contentResolver, "airplane_mode_on", 0))
        val db = app.database
        val oldCount = runBlocking { db.today().unprocessedCaptures().first() }
        val stamp = System.currentTimeMillis()
        val task = LocalTask(UUID.randomUUID().toString(), "Today offline fixture", "2020-01-01")
        val capture = PhoneCapture(UUID.randomUUID().toString(), "Offline inbox fixture", CaptureSource.NOTE.name, stamp)
        val notebook = PhoneNotebook(UUID.randomUUID().toString(), "Today offline project", stamp, stamp)
        try {
            runBlocking {
                db.dao().save(task)
                db.phoneCaptures().save(capture)
                db.phoneNotebooks().create(notebook)
            }
            compose.waitUntil(15_000) { vm.todayUnprocessed.value == oldCount + 1 && vm.todayNotebook.value?.id == notebook.id }
            compose.onNodeWithTag("today-overview").assertExists()
            compose.onNodeWithText(task.title + " · " + task.dueDate).assertExists()
            compose.onNodeWithTag("home-capture-open").assertTextContains((oldCount + 1).toString(), substring = true)
            compose.onNodeWithTag("today-notebook").assertTextContains(notebook.name)
        } finally {
            runBlocking { db.dao().delete(task) }
            val sqlite = db.openHelper.writableDatabase
            sqlite.execSQL("DELETE FROM phone_captures WHERE id = ?", arrayOf(capture.id))
            sqlite.execSQL("DELETE FROM phone_notebooks WHERE id = ?", arrayOf(notebook.id))
        }
    }

    @Test fun refreshAndDisconnectRetainOnlyAttributableRunAndApprovalReceipts() {
        val id = UUID.randomUUID().toString().take(8)
        val activeId = "today-active-$id"
        val completedId = "today-completed-$id"
        val otherId = "today-other-$id"
        val rest = AtomicBoolean(true)
        val reads = CopyOnWriteArrayList<String>()
        fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        val cleanup = mutableListOf<String>()
        try {
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = when {
                        !rest.get() -> json("{}").setResponseCode(503)
                        request.path == "/v1/capabilities" -> json(
                            """{"features":{"session_resources":true,"session_chat":true,"run_events_sse":true},"endpoints":{"runs":{}}}""")
                        request.path.orEmpty().contains("/api/jobs?") -> json("""{"jobs":[]}""")
                        request.path.orEmpty().startsWith("/api/sessions?") -> json("""{"data":[],"has_more":false}""")
                        request.path == "/v1/runs/$activeId" -> {
                            reads.add(activeId)
                            json("""{"run_id":"$activeId","status":"running","execution_generation":4}""")
                        }
                        request.path == "/v1/runs/$completedId" -> {
                            reads.add(completedId)
                            json("""{"run_id":"$completedId","status":"completed","output":"Reviewed","execution_generation":2}""")
                        }
                        else -> json("{}").setResponseCode(404)
                    }
                }
                compose.waitUntil(15_000) { !vm.connecting.value }
                val destination = server.url("/").toString()
                compose.runOnUiThread { vm.configure(destination, credentials.uniqueToken("today"), true) }
                compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "connected" && !vm.todayRefreshing.value }
                val target = vm.profileScope.value
                val now = System.currentTimeMillis()
                val active = Conversation("$target:$activeId", target, activeId, "Active receipt", now,
                    transport = "runs", runId = activeId, status = "running", executionGeneration = 4)
                val complete = Conversation("$target:$completedId", target, completedId, "Completed receipt", now,
                    transport = "runs", runId = completedId, status = "completed", executionGeneration = 2)
                val unrelated = Conversation("foreign:$otherId", "foreign-$id", otherId, "Foreign realm", now,
                    transport = "runs", runId = otherId, status = "running", executionGeneration = 4)
                cleanup += listOf(active.key, complete.key, unrelated.key)
                val db = app.database
                runBlocking {
                    for (row in listOf(active, complete, unrelated)) db.dao().save(row)
                    db.dao().save(PendingControl("current-$id", active.key, activeId, "exact-request", 4,
                        "approval", "Approve fixture?", "[\"allow\"]", false, receivedAt = now - 2_000))
                    db.dao().save(PendingControl("stale-$id", active.key, activeId, "old-request", 3,
                        "approval", "Old generation", "[\"allow\"]", false, receivedAt = now - 3_000))
                    db.dao().save(PendingControl("foreign-$id", unrelated.key, otherId, "foreign-request", 4,
                        "approval", "Other connection", "[\"allow\"]", false, receivedAt = now))
                }
                val terminals = RunStates.terminal.toList()
                assertEquals(listOf("exact-request"), runBlocking {
                    db.today().pendingApprovals(target, terminals).first().map { it.requestId }
                })

                compose.runOnUiThread { vm.refreshToday() }
                compose.waitUntil(15_000) {
                    !vm.todayRefreshing.value && vm.todayRemoteSnapshot.value?.confirmed?.containsKey(completedId) == true
                }
                val confirmed = checkNotNull(vm.todayRemoteSnapshot.value)
                assertEquals(setOf(activeId, completedId), confirmed.confirmed.keys)
                assertEquals(0, confirmed.failed)
                assertEquals(setOf(activeId, completedId), reads.toSet())
                assertEquals(completedId, runBlocking { db.today().completedRuns(target).first() }.single().runId)

                rest.set(false)
                compose.runOnUiThread { vm.refresh() }
                compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "offline" }
                assertEquals(confirmed, vm.todayRemoteSnapshot.value)
                assertEquals(listOf("exact-request"), runBlocking {
                    db.today().pendingApprovals(target, terminals).first().map { it.requestId }
                })

                rest.set(true)
                compose.runOnUiThread { vm.refresh() }
                compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "connected" && !vm.todayRefreshing.value }
                compose.runOnUiThread { vm.refreshToday() }
                compose.waitUntil(15_000) { !vm.todayRefreshing.value && (vm.todayRemoteSnapshot.value?.endedAt ?: 0) > confirmed.endedAt }
                assertEquals(target, vm.todayRemoteSnapshot.value?.scope)
            }
        } finally {
            val db = app.database.openHelper.writableDatabase
            for (key in cleanup) {
                db.execSQL("DELETE FROM pending_controls WHERE sessionKey = ?", arrayOf(key))
                db.execSQL("DELETE FROM run_reports WHERE sessionKey = ?", arrayOf(key))
                db.execSQL("DELETE FROM sessions WHERE key = ?", arrayOf(key))
            }
        }
    }
}
