package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.HermesViewModel
import org.hermesmobile.app.ui.Words
import org.hermesmobile.app.ui.localized
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

    @Test fun olderCompletedReceiptOpensOnlyItsSavedActivity() {
        val id = UUID.randomUUID().toString().take(8)
        val older = "today-older-$id"
        val newer = "today-newer-$id"
        val remote = "today-session-$id"
        var sessionKey = ""
        val unexpected = CopyOnWriteArrayList<String>()
        val db = app.database
        try {
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path.orEmpty()
                        val response = MockResponse().setHeader("Content-Type", "application/json")
                        return when {
                            path == "/v1/capabilities" -> response.setBody(
                                """{"features":{"session_resources":true,"session_chat":true}}""")
                            path.startsWith("/api/jobs?") -> response.setBody("""{"jobs":[]}""")
                            path.startsWith("/api/sessions?") -> response.setBody("""{"data":[],"has_more":false}""")
                            else -> {
                                unexpected.add(path)
                                response.setResponseCode(404).setBody("{}")
                            }
                        }
                    }
                }
                compose.waitUntil(15_000) { !vm.connecting.value }
                val token = credentials.uniqueToken("today-receipt")
                val destination = server.url("/").toString()
                compose.runOnUiThread { vm.configure(destination, token, true) }
                compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "connected" }
                val target = vm.profileScope.value
                val now = System.currentTimeMillis()
                sessionKey = "$target:$remote"
                val trip = Conversation(sessionKey, target, remote, "Reused receipt conversation", now,
                    transport = "runs", runId = newer, status = "completed", executionGeneration = 8)
                val originalReport = RunReport("$sessionKey|$older", sessionKey, older, "", "runs",
                    """{"runtime":{"provider":"older-provider","model":"older-model"}}""", "completed", now - 5_000)
                val newerReport = RunReport("$sessionKey|$newer", sessionKey, newer, "", "runs",
                    """{"runtime":{"provider":"newer-provider","model":"newer-model"}}""", "completed", now)
                runBlocking {
                    db.dao().save(trip)
                    db.dao().saveReport(originalReport)
                    db.dao().saveReport(newerReport)
                    db.dao().save(ToolEvent("older-tool-$id", sessionKey, older, "older-tool", "completed", "", 5, now - 4_000))
                    db.dao().save(ToolEvent("newer-tool-$id", sessionKey, newer, "newer-tool", "completed", "", 7, now))
                    val oldRow = db.today().completedRuns(target).first().first { it.runId == older }
                    val exact = checkNotNull(db.today().completedRun(oldRow))
                    assertEquals(originalReport, exact.report)
                    assertEquals(listOf("older-tool"), exact.events.map { it.tool })
                    assertNull(db.today().completedRun(oldRow.copy(receiptKey = newerReport.key)))
                    assertNull(db.today().completedRun(oldRow.copy(scope = "foreign-$id")))
                }

                compose.onNodeWithTag("today-refresh").assertIsNotEnabled()
                compose.onNodeWithTag("today-refresh-reason").assertTextContains(
                    Words.TodayRefreshUnsupported.localized(vm.preferences.value)
                )
                val olderButton = compose.onNodeWithTag("today-receipt-$older")
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithTag("today-receipt-$older").fetchSemanticsNodes().isNotEmpty()
                }
                olderButton.performScrollTo().performClick()
                val receipt = compose.onNodeWithTag("saved-receipt-identity", useUnmergedTree = true)
                compose.waitUntil(15_000) { receipt.isDisplayed() }
                receipt.assertTextEquals(older)
                val activity = compose.onNodeWithTag("activity-timeline")
                activity.performScrollToNode(hasText("older-tool"))
                compose.onNodeWithText("older-tool").assertIsDisplayed()
                compose.onNodeWithText("newer-tool").assertDoesNotExist()
                compose.onNodeWithText("newer-model").assertDoesNotExist()
                assertTrue(vm.selected.value.isBlank())
                assertEquals(emptyList<String>(), unexpected.toList())

                compose.runOnUiThread { vm.setDemo(true) }
                compose.waitUntil(15_000) { vm.profileScope.value == "demo" && !vm.connecting.value }
                receipt.assertDoesNotExist()
                olderButton.assertDoesNotExist()
            }
        } finally {
            if (sessionKey.isNotEmpty()) {
                val sqlite = db.openHelper.writableDatabase
                sqlite.execSQL("DELETE FROM tool_events WHERE sessionKey = ?", arrayOf(sessionKey))
                sqlite.execSQL("DELETE FROM run_reports WHERE sessionKey = ?", arrayOf(sessionKey))
                sqlite.execSQL("DELETE FROM sessions WHERE key = ?", arrayOf(sessionKey))
            }
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
                    !vm.todayRefreshing.value && vm.todayRemoteSnapshot.value?.confirmed
                        ?.containsKey(TodayRunIdentity(complete.key, completedId)) == true
                }
                val confirmed = checkNotNull(vm.todayRemoteSnapshot.value)
                assertEquals(setOf(TodayRunIdentity(active.key, activeId), TodayRunIdentity(complete.key, completedId)),
                    confirmed.confirmed.keys)
                assertEquals(0, confirmed.failed)
                assertEquals(setOf(activeId, completedId), reads.toSet())
                assertEquals(completedId, runBlocking { db.today().completedRuns(target).first() }.single().runId)

                val checkedBeforeBusy = reads.toList()
                compose.runOnUiThread { vm.controlBusy.value = true }
                try {
                    compose.onNodeWithTag("today-refresh").assertIsNotEnabled()
                    compose.onNodeWithTag("today-refresh-reason").assertTextContains(
                        Words.TodayRefreshBusy.localized(vm.preferences.value)
                    )
                    compose.runOnUiThread { vm.refreshToday() }
                    assertEquals(checkedBeforeBusy, reads.toList())
                } finally {
                    compose.runOnUiThread { vm.controlBusy.value = false }
                }

                rest.set(false)
                compose.runOnUiThread { vm.refresh() }
                compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "offline" }
                assertEquals(confirmed, vm.todayRemoteSnapshot.value)
                compose.onNodeWithTag("today-refresh").assertIsNotEnabled()
                compose.onNodeWithTag("today-refresh-reason").assertTextContains(
                    Words.TodayRefreshOffline.localized(vm.preferences.value)
                )
                assertEquals(listOf("exact-request"), runBlocking {
                    db.today().pendingApprovals(target, terminals).first().map { it.requestId }
                })

                val checkedBeforeReconnect = reads.size
                rest.set(true)
                compose.runOnUiThread { vm.refresh() }
                compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "connected" }
                compose.waitUntil(15_000) {
                    val refreshed = vm.todayRemoteSnapshot.value
                    !vm.todayRefreshing.value && refreshed != null && refreshed.endedAt > confirmed.endedAt &&
                        refreshed.connectionGeneration == vm.todayConnectionGeneration.value
                }
                assertEquals(target, vm.todayRemoteSnapshot.value?.scope)
                assertEquals(setOf(activeId, completedId), reads.drop(checkedBeforeReconnect).toSet())
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
