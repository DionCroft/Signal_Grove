package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HostKanbanStatusStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun repository(db: HermesDatabase) = HostKanbanStatusRepository(db.dao(), CompletableDeferred(Unit))
    private suspend fun fixture(mode: String = "normal", block: suspend (HermesDatabase, HostKanbanStatusFixture) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, HermesDatabase::class.java).build()
        try { MockWebServer().use { server -> val f = HostKanbanStatusFixture(server, mode); f.start(); try { withTimeout(60000) { block(db, f) } } finally { f.release.countDown(); f.assertClean() } } } finally { db.close() }
    }
    private suspend fun prepare(db: HermesDatabase, f: HostKanbanStatusFixture, target: HostKanbanStatusTarget = HostKanbanStatusTarget.Triage, text: String? = null) = repository(db).prepare(f.inventory(), f.snapshot(), f.plan(target, text), f.api(), f.tokens)
    private suspend fun send(db: HermesDatabase, f: HostKanbanStatusFixture, request: HostKanbanStatusChange) = repository(db).execute(f.owner, request.approve("t_root", true), f.api(), f.tokens)
    private fun field(task: JsonObject, vararg values: Pair<String, JsonElement>) = HostKanbanStatusFixture.fields(task, *values)
    private fun card(id: String = "t_root", status: String = "ready") = HostKanbanStatusFixture.card(id, status)
    private fun detail(task: JsonObject, parents: List<String> = emptyList(), children: List<String> = emptyList()) = HostKanbanStatusFixture.detail(task, parents, children)
    private fun pending(owner: ManagementTarget, action: String, key: String = "foreign", projectHome: String = "") = ManagementOperation(key, owner.resourceScope, owner.apiScope, owner.url, owner.profile, owner.actor, action, "opaque-target", buildJsonObject { put("schema", 1); put("access_home_sha256", "b".repeat(64)) }.toString(), "unknown", "", 1, projectHome = projectHome)
    private suspend fun onlyReceipt(db: HermesDatabase, f: HostKanbanStatusFixture) = db.dao().managementOperations(f.owner.apiScope).first().single()

    @Test fun everySupportedVerbUsesOneLiteralScopedPatchAndObservesTheReportedRow() = runBlocking {
        val cases = listOf(
            Triple(HostKanbanStatusTarget.Todo, "ready", """{"status":"todo"}"""),
            Triple(HostKanbanStatusTarget.Ready, "todo", """{"status":"ready"}"""),
            Triple(HostKanbanStatusTarget.Triage, "ready", """{"status":"triage"}"""),
            Triple(HostKanbanStatusTarget.Blocked, "running", """{"status":"blocked","block_reason":"Private note"}"""),
            Triple(HostKanbanStatusTarget.Scheduled, "ready", """{"status":"scheduled","block_reason":"Private note"}"""),
            Triple(HostKanbanStatusTarget.Review, "running", """{"status":"review","summary":"Private note"}"""),
            Triple(HostKanbanStatusTarget.Done, "review", """{"status":"done","result":"Private note"}"""),
            Triple(HostKanbanStatusTarget.Archived, "running", """{"status":"archived"}""")
        )
        for ((target, before, literal) in cases) fixture { db, f ->
            f.before["t_root"] = detail(card(status = before))
            val expected = Json.parseToJsonElement(literal).jsonObject; f.expectedBody.set(expected)
            val after = card(status = target.wire).let { if (target == HostKanbanStatusTarget.Done) field(it, "result" to JsonPrimitive("Private note")) else it }
            f.acknowledgement.set(buildJsonObject { put("task", after) })
            f.after["t_root"] = detail(field(after, "latest_summary" to JsonPrimitive("Derived host summary")))
            val request = prepare(db, f, target, "Private note".takeIf { expected.size > 1 })
            val result = send(db, f, request)
            assertEquals(target.name, "confirmed", result.receipt.state); assertTrue(result.acknowledgement.requestedStatusReported)
            assertEquals(1, f.writes.get()); assertEquals(0, f.base.delegate.tickets.get()); assertEquals("", result.receipt.projectHome)
            assertEquals(f.home, HostKanbanBoardChange.accessHome(result.receipt.requestSummary))
            assertEquals(JsonPrimitive(true), Json.parseToJsonElement(result.receipt.resultSummary).jsonObject["reported_state_observed"])
        }
    }
    @Test fun preparationLoadsAncestorsAndTransitiveChildrenBeforeAnyWrite() = runBlocking {
        fixture { db, f ->
            f.before["t_root"] = detail(card(), listOf("t_parent"), listOf("t_child"))
            f.before["t_parent"] = detail(card("t_parent", "done"), children = listOf("t_root"))
            f.before["t_child"] = detail(card("t_child"), listOf("t_root"), listOf("t_grandchild"))
            f.before["t_grandchild"] = detail(card("t_grandchild"), listOf("t_child"))
            val request = prepare(db, f); assertEquals(setOf("t_root", "t_parent", "t_child", "t_grandchild"), request.observations.keys)
            assertEquals(0, f.writes.get()); assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())
        }
    }
    @Test fun changedRunClaimOrPidRefusesBeforeClaimAndWrite() = runBlocking {
        for ((key, value) in listOf("current_run_id" to JsonPrimitive(8), "claim_lock" to JsonPrimitive("changed-private-claim"), "worker_pid" to JsonPrimitive(321))) fixture { db, f ->
            val request = prepare(db, f); f.before["t_root"] = detail(field(card(), key to value))
            assertTrue(runCatching { send(db, f, request) }.exceptionOrNull() is HostKanbanTaskChanged)
            assertEquals(0, f.writes.get()); assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())
        }
    }
    @Test fun changedDescendantOrNewEdgeInvalidatesTheWholeReview() = runBlocking {
        for (edge in listOf(false, true)) fixture { db, f ->
            f.before["t_root"] = detail(card(), children = listOf("t_child")); f.before["t_child"] = detail(card("t_child"), listOf("t_root"))
            val request = prepare(db, f)
            if (edge) {
                f.before["t_child"] = detail(card("t_child"), listOf("t_root"), listOf("t_new")); f.before["t_new"] = detail(card("t_new"), listOf("t_child"))
            } else f.before["t_child"] = detail(card("t_child", "blocked"), listOf("t_root"))
            assertTrue(runCatching { send(db, f, request) }.exceptionOrNull() is HostKanbanTaskChanged); assertEquals(0, f.writes.get())
        }
    }
    @Test fun incompleteAndCyclicGraphsCannotReachStatusSubmission() = runBlocking {
        for (cycle in listOf(false, true)) fixture { db, f ->
            f.before["t_root"] = detail(card(), if (cycle) listOf("t_child") else emptyList(), listOf("t_child"))
            if (cycle) f.before["t_child"] = detail(card("t_child"), listOf("t_root"), listOf("t_root"))
            assertTrue(runCatching { prepare(db, f) }.exceptionOrNull() is HostKanbanStatusImpactIncomplete); assertEquals(0, f.writes.get())
            assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())
        }
    }
    @Test fun lostRepliesAndAllGenericHttpFailuresStayUnknownWithNoReplay() = runBlocking {
        for (mode in listOf("lost", "http400", "http409", "http500")) fixture(mode) { db, f ->
            val request = prepare(db, f); val approval = request.approve("t_root", true)
            assertTrue(runCatching { repository(db).execute(f.owner, approval, f.api(), f.tokens) }.exceptionOrNull() is HostKanbanTaskUncertain)
            assertEquals("unknown", onlyReceipt(db, f).state)
            assertTrue(runCatching { repository(db).execute(f.owner, approval, f.api(), f.tokens) }.exceptionOrNull() is HostKanbanStatusApprovalUsed)
            assertTrue(runCatching { send(db, f, request) }.exceptionOrNull() is ManagementOutcomePending)
            HostKanbanRepository().inventory(f.owner, f.home, f.api(), f.tokens)
            assertEquals("unknown", onlyReceipt(db, f).state); assertEquals(1, f.writes.get())
        }
    }
    @Test fun foreignUnknownAndMalformedAckKeepTheReceiptUnknown() = runBlocking {
        for (ack in listOf(buildJsonObject { put("task", card("foreign")) }, buildJsonObject { put("task", card(status = "future_state")) }, buildJsonObject { put("ok", true) })) fixture { db, f ->
            f.acknowledgement.set(ack); assertTrue(runCatching { send(db, f, prepare(db, f)) }.exceptionOrNull() is HostKanbanTaskUncertain)
            assertEquals("unknown", onlyReceipt(db, f).state); assertEquals(1, f.writes.get())
        }
    }
    @Test fun differentEffectiveStatusIsObservedWithoutClaimingRequestedStatus() = runBlocking {
        fixture { db, f ->
            f.acknowledgement.set(buildJsonObject { put("task", card()) }); f.after["t_root"] = detail(card())
            val result = send(db, f, prepare(db, f)); assertEquals("confirmed", result.receipt.state); assertFalse(result.acknowledgement.requestedStatusReported)
            val summary = Json.parseToJsonElement(result.receipt.resultSummary).jsonObject
            assertEquals(JsonPrimitive(false), summary["requested_status_reported"]); assertEquals(JsonPrimitive("ready"), summary["reported_status"])
            assertEquals(JsonPrimitive(true), summary["reported_state_observed"])
        }
    }
    @Test fun validAckWithDivergentReadbackStaysAcknowledged() = runBlocking {
        fixture { db, f ->
            f.after["t_root"] = detail(card(status = "blocked"))
            val result = send(db, f, prepare(db, f)); assertEquals("acknowledged", result.receipt.state); assertTrue(result.acknowledgement.requestedStatusReported)
            assertNull(Json.parseToJsonElement(result.receipt.resultSummary).jsonObject["reported_state_observed"])
        }
    }
    @Test fun failedReadbackOrChangedHomeRetainsDurableAcknowledgement() = runBlocking {
        for (mode in listOf("readback-fail", "home-after")) fixture(mode) { db, f ->
            assertTrue(runCatching { send(db, f, prepare(db, f)) }.exceptionOrNull() is HostKanbanTaskReadbackPending)
            assertEquals("acknowledged", onlyReceipt(db, f).state); assertEquals(1, f.writes.get())
        }
    }
    @Test fun boardAndDefaultAnchorRemappingAfterAckCannotConfirmState() = runBlocking {
        for (slug in listOf("default", "garden")) fixture { db, f ->
            f.base.catalogAfter.set(HostKanbanBoardFixture.catalog(listOf("default", "garden", "archive").map { name ->
                val meta = HostKanbanBoardFixture.metadata(name, name == "archive"); if (name == slug) field(meta, "db_path" to JsonPrimitive("/changed/root.db")) else meta
            }))
            assertTrue(runCatching { send(db, f, prepare(db, f)) }.exceptionOrNull() is HostKanbanTaskReadbackPending)
            assertEquals("acknowledged", onlyReceipt(db, f).state)
        }
    }
    @Test fun cancelledInFlightWriteRemainsUnknownAndNeverReplays() = runBlocking {
        fixture("held-write") { db, f ->
            val request = prepare(db, f); val job = async { send(db, f, request) }
            assertTrue(withContext(Dispatchers.IO) { f.entered.await(15, TimeUnit.SECONDS) }); job.cancelAndJoin(); f.release.countDown()
            assertEquals("unknown", onlyReceipt(db, f).state); assertEquals(1, f.writes.get())
            assertTrue(runCatching { send(db, f, request) }.exceptionOrNull() is ManagementOutcomePending)
        }
    }
    @Test fun cancelledPostAckObservationDoesNotEraseTheAcknowledgement() = runBlocking {
        fixture("held-readback") { db, f ->
            val request = prepare(db, f); val job = async { send(db, f, request) }
            assertTrue(withContext(Dispatchers.IO) { f.entered.await(15, TimeUnit.SECONDS) }); assertEquals("acknowledged", onlyReceipt(db, f).state)
            job.cancelAndJoin(); f.release.countDown(); assertEquals("acknowledged", onlyReceipt(db, f).state); assertEquals(1, f.writes.get())
        }
    }
    @Test fun concurrentApprovalsShareTheAtomicPendingBarrier() = runBlocking {
        fixture("held-write") { db, f ->
            val request = prepare(db, f); val job = async { runCatching { send(db, f, request) } }
            assertTrue(withContext(Dispatchers.IO) { f.entered.await(15, TimeUnit.SECONDS) })
            assertTrue(runCatching { send(db, f, request) }.exceptionOrNull() is ManagementOutcomePending)
            f.release.countDown(); assertTrue(job.await().isSuccess); assertEquals(1, f.writes.get())
        }
    }
    @Test fun consumedApprovalCannotSubmitAgainEvenAfterConfirmedReadback() = runBlocking {
        fixture { db, f ->
            val approval = prepare(db, f).approve("t_root", true); repository(db).execute(f.owner, approval, f.api(), f.tokens)
            assertTrue(runCatching { repository(db).execute(f.owner, approval, f.api(), f.tokens) }.exceptionOrNull() is HostKanbanStatusApprovalUsed)
            assertEquals(1, f.writes.get())
        }
    }
    @Test fun foreignProfileStatusReceiptBlocksExistingTaskOperationsAndIsVisible() = runBlocking {
        fixture { db, f ->
            val foreign = f.owner.copy(profile = "other")
            db.dao().insertManagementOperation(pending(foreign, HostKanbanStatusChange.ReceiptAction))
            assertTrue(runCatching { HostKanbanTaskRepository(db.dao(), CompletableDeferred(Unit)).execute(f.owner, f.base.request(), f.api(), f.tokens) }.exceptionOrNull() is ManagementOutcomePending)
            assertTrue(db.dao().managementVisibleResourceOperations(f.owner.resourceScope, f.owner.apiScope, f.owner.url, f.home).first().any { it.key == "foreign" })
            assertTrue(runCatching { send(db, f, prepare(db, f)) }.exceptionOrNull() is ManagementOutcomePending)
            db.dao().reviewManagementOperation("foreign", f.owner.resourceScope, f.owner.url, f.home)
            assertEquals("reviewed", db.dao().managementOperation("foreign")!!.state); assertEquals(0, f.writes.get())
        }
    }
    @Test fun existingTaskAndVerifiedProjectHomeReceiptsBlockStatusWrites() = runBlocking {
        for (project in listOf(false, true)) fixture { db, f ->
            val foreign = f.owner.copy(profile = "other")
            db.dao().insertManagementOperation(pending(if (project) foreign.copy(url = "https://alias.example/") else foreign, if (project) "ProjectUpdate" else "KanbanTaskMetadata", projectHome = if (project) f.home else ""))
            assertTrue(runCatching { send(db, f, prepare(db, f)) }.exceptionOrNull() is ManagementOutcomePending); assertEquals(0, f.writes.get())
        }
    }
    @Test fun ownerOrTokenActorSwapFailsBeforeAnyStatusWrite() = runBlocking {
        fixture { db, f ->
            val request = prepare(db, f)
            assertTrue(runCatching { repository(db).execute(f.owner.copy(profile = "other"), request.approve("t_root", true), f.api(), f.tokens) }.exceptionOrNull() is IllegalArgumentException)
            val tokens = ManagementTokens(f.tokens.access, f.tokens.refresh, f.tokens.provider, "other", f.tokens.expiresAt)
            assertTrue(runCatching { repository(db).execute(f.owner, request.approve("t_root", true), f.api(), tokens) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, f.writes.get()); assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())
        }
    }
    @Test fun heartbeatProgressCanBeObservedWithoutRetargetingTheApprovedTask() = runBlocking {
        fixture { db, f ->
            val request = prepare(db, f); f.before["t_root"] = detail(field(card(), "last_heartbeat_at" to JsonPrimitive(5)))
            val result = send(db, f, request); assertEquals("confirmed", result.receipt.state); assertEquals(1, f.writes.get())
        }
    }
    @Test fun durableSummariesContainNoTaskTextClaimOrWorkspaceAndNeverProveSideEffects() = runBlocking {
        fixture { db, f ->
            f.before["t_root"] = detail(field(card(), "claim_lock" to JsonPrimitive("synthetic-private-claim")))
            val result = send(db, f, prepare(db, f)); val durable = result.receipt.target + result.receipt.requestSummary + result.receipt.resultSummary
            for (value in listOf("Private status task", "Private task body", "synthetic-private-claim", "/private/", "t_root", "garden")) assertFalse(value, durable.contains(value))
            val summary = Json.parseToJsonElement(result.receipt.resultSummary).jsonObject
            for (key in listOf("atomic_run_fence", "worker_identity_verified", "worker_stop_verified", "file_erasure_verified", "runtime_adoption_verified", "hook_execution_verified", "descendant_effects_verified", "engineering_completion_verified")) assertEquals(JsonPrimitive(false), summary[key])
        }
    }
    @Test fun changedUndisplayedStatusPolicyRefusesBeforeWriting() = runBlocking {
        fixture { db, f ->
            val request = prepare(db, f)
            f.before["t_root"] = detail(field(card(), "block_kind" to JsonPrimitive("needs_input")))
            assertTrue(runCatching { send(db, f, request) }.exceptionOrNull() is HostKanbanTaskChanged)
            assertEquals(0, f.writes.get()); assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())
        }
    }
    @Test fun fullHandoffSummarySurvivesTheShortBoardPreview() = runBlocking {
        fixture { db, f ->
            val full = "Private handoff ".repeat(40)
            f.before["t_root"] = detail(field(card(), "latest_summary" to JsonPrimitive(full)))
            val request = prepare(db, f); assertEquals(full, request.plan.subject.task.summary)
            assertEquals("confirmed", send(db, f, request).receipt.state); assertEquals(1, f.writes.get())
        }
    }
}
