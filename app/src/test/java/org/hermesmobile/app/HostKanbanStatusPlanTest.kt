package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostKanbanStatusPlanTest {
    private val owner = ManagementTarget("a".repeat(64), "https://dashboard.example/prefix/", "worker", "password · actor")
    private val board = HostKanbanBoard("garden", "Private board", null, null, null, "/private/garden.db", null, null, null, false, true, emptyMap(), null, null, 1)
    private fun detail(status: String = "ready"): HostKanbanDetail {
        val task = HostKanbanTask("t_private", "Private title", status, "implementer", null, 0, "Private body", "Old result", null, 1, null, null, 7, null, emptyMap())
        return HostKanbanDetail(owner, "b".repeat(64), board, task, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 1)
    }
    private fun plan(before: String, target: HostKanbanStatusTarget, statement: String? = null) = HostKanbanStatusPlan.review(detail(before), target, statement)
    private fun refuses(before: String, target: HostKanbanStatusTarget, statement: String? = null) = assertThrows(HostKanbanStatusInvalid::class.java) { plan(before, target, statement) }

    @Test fun runningIsNotAnOfferedStatusVerb() {
        assertEquals(setOf("todo", "ready", "triage", "blocked", "scheduled", "review", "done", "archived"), HostKanbanStatusTarget.entries.map { it.wire }.toSet())
    }
    @Test fun unknownAndNoOpStatesNeverBecomeTodo() {
        for (target in HostKanbanStatusTarget.entries) {
            refuses("future_state", target, if (target in setOf(HostKanbanStatusTarget.Blocked, HostKanbanStatusTarget.Done)) "Evidence" else null)
            refuses(target.wire, target, if (target in setOf(HostKanbanStatusTarget.Blocked, HostKanbanStatusTarget.Done)) "Evidence" else null)
        }
    }
    @Test fun completionRequiresExplicitReplacementEvenForReviewApproval() {
        for (before in listOf("running", "ready", "blocked", "review")) {
            refuses(before, HostKanbanStatusTarget.Done)
            val p = plan(before, HostKanbanStatusTarget.Done, "Checked result")
            assertEquals(Json.parseToJsonElement("""{"status":"done","result":"Checked result"}"""), p.body())
            assertTrue(p.effects.containsAll(setOf(HostKanbanStatusEffect.ReplacesStoredResult, HostKanbanStatusEffect.ForceCurrentClaim, HostKanbanStatusEffect.MayCleanManagedAndParentWorkspaces, HostKanbanStatusEffect.MayStageReferencedFiles)))
        }
    }
    @Test fun completionAndReviewDoNotClaimWorkerTermination() {
        for (target in listOf(HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Review)) {
            val p = plan("running", target, "Evidence")
            assertTrue(HostKanbanStatusEffect.WorkerMayContinue in p.effects)
            assertFalse(HostKanbanStatusEffect.MayRequestWorkerTermination in p.effects)
            assertTrue(HostKanbanStatusEffect.ForceCurrentClaim in p.effects)
        }
    }
    @Test fun reviewKeepsAssigneeAndMetadataOutOfItsPhase() {
        val p = plan("running", HostKanbanStatusTarget.Review, "See /workspace/report.txt")
        assertEquals(Json.parseToJsonElement("""{"status":"review","summary":"See /workspace/report.txt"}"""), p.body())
        assertTrue(HostKanbanStatusEffect.MayStageReferencedFiles in p.effects)
        assertEquals(Json.parseToJsonElement("""{"status":"review"}"""), plan("ready", HostKanbanStatusTarget.Review).body())
    }
    @Test fun blockedReasonIsRequiredAndNotSentAsCompletionEvidence() {
        refuses("ready", HostKanbanStatusTarget.Blocked)
        assertEquals(Json.parseToJsonElement("""{"status":"blocked","block_reason":"Needs a decision"}"""), plan("running", HostKanbanStatusTarget.Blocked, "Needs a decision").body())
        for (before in listOf("todo", "triage", "scheduled", "review", "done", "archived")) refuses(before, HostKanbanStatusTarget.Blocked, "Needs a decision")
    }
    @Test fun schedulingOnlyParksAndNeverInventsWakeTime() {
        for (before in listOf("todo", "ready", "running", "blocked")) {
            val p = plan(before, HostKanbanStatusTarget.Scheduled, "Waiting on time")
            assertEquals(Json.parseToJsonElement("""{"status":"scheduled","block_reason":"Waiting on time"}"""), p.body())
            assertTrue(HostKanbanStatusEffect.ParkWithoutWakeTime in p.effects)
            assertFalse(HostKanbanStatusEffect.MayQueueWork in p.effects)
        }
        assertEquals(setOf("status"), plan("todo", HostKanbanStatusTarget.Scheduled).body().keys)
        for (before in listOf("triage", "review", "done", "archived")) refuses(before, HostKanbanStatusTarget.Scheduled)
    }
    @Test fun unsupportedCompletionAndReviewOriginsAreRefusedLocally() {
        for (before in listOf("todo", "triage", "scheduled", "done", "archived")) refuses(before, HostKanbanStatusTarget.Done, "Evidence")
        for (before in listOf("todo", "triage", "blocked", "scheduled", "review", "done", "archived")) refuses(before, HostKanbanStatusTarget.Review)
    }
    @Test fun reopeningSatisfiedParentsIncludesTransitiveImpactAndPossibleWorkerSurvival() {
        for (before in listOf("done", "archived")) for (target in listOf(HostKanbanStatusTarget.Todo, HostKanbanStatusTarget.Ready, HostKanbanStatusTarget.Triage)) {
            val p = plan(before, target)
            assertTrue(p.effects.containsAll(setOf(HostKanbanStatusEffect.MayInvalidateDescendants, HostKanbanStatusEffect.MayRequestWorkerTermination, HostKanbanStatusEffect.WorkerMayContinue)))
        }
        assertFalse(HostKanbanStatusEffect.MayInvalidateDescendants in plan("done", HostKanbanStatusTarget.Archived).effects)
    }
    @Test fun reviewReturnDoesNotPromiseTheRequestedColumnOrAssignee() {
        for (target in listOf(HostKanbanStatusTarget.Todo, HostKanbanStatusTarget.Ready)) {
            val p = plan("review", target)
            assertTrue(HostKanbanStatusEffect.MayRestoreImplementer in p.effects)
            assertTrue(HostKanbanStatusEffect.MayQueueWork in p.effects)
            assertEquals(setOf("status"), p.body().keys)
        }
        assertFalse(HostKanbanStatusEffect.MayRestoreImplementer in plan("review", HostKanbanStatusTarget.Triage).effects)
    }
    @Test fun readyMayResumeReviewAndTodoCanBecomeDispatchable() {
        for (before in listOf("blocked", "scheduled", "running")) assertTrue(HostKanbanStatusEffect.MayResumeReview in plan(before, HostKanbanStatusTarget.Ready).effects)
        assertTrue(HostKanbanStatusEffect.MayQueueWork in plan("ready", HostKanbanStatusTarget.Todo).effects)
        assertFalse(HostKanbanStatusEffect.MayQueueWork in plan("ready", HostKanbanStatusTarget.Triage).effects)
    }
    @Test fun directRunningExitAndArchiveOnlyRequestTermination() {
        for (target in listOf(HostKanbanStatusTarget.Todo, HostKanbanStatusTarget.Ready, HostKanbanStatusTarget.Triage, HostKanbanStatusTarget.Archived)) {
            val p = plan("running", target)
            assertTrue(p.effects.containsAll(setOf(HostKanbanStatusEffect.MayRequestWorkerTermination, HostKanbanStatusEffect.WorkerMayContinue)))
        }
        assertTrue(HostKanbanStatusEffect.MayCleanManagedAndParentWorkspaces in plan("ready", HostKanbanStatusTarget.Archived).effects)
    }
    @Test fun exactMultilingualStatementsArePreservedAndUnsafeTextRefused() {
        val text = "  已检查 🌱\nVerified\tresult  "
        assertEquals(JsonPrimitive(text), plan("review", HostKanbanStatusTarget.Done, text).body()["result"])
        for (bad in listOf("", " \n ", "bad\u0000", "\uD800", "x".repeat(262145))) refuses("review", HostKanbanStatusTarget.Done, bad)
        assertEquals(262144, plan("review", HostKanbanStatusTarget.Done, "x".repeat(262144)).body().getValue("result").jsonPrimitive.content.length)
    }
    @Test fun directAndArchiveVerbsCannotSmuggleOtherWritePhases() {
        for (target in listOf(HostKanbanStatusTarget.Todo, HostKanbanStatusTarget.Ready, HostKanbanStatusTarget.Triage, HostKanbanStatusTarget.Archived)) {
            val before = if (target == HostKanbanStatusTarget.Ready) "todo" else "ready"
            refuses(before, target, "Extra text")
            val body = plan(before, target).body()
            assertEquals(setOf("status"), body.keys)
            assertFalse(body.containsKey("expected_run_id"))
        }
    }
    @Test fun everyPlanRetainsTheUnfencedAndUncertainOutcomeLimits() {
        for (target in HostKanbanStatusTarget.entries) {
            val p = plan(if (target == HostKanbanStatusTarget.Ready) "todo" else "ready", target, if (target in setOf(HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Blocked)) "Evidence" else null)
            assertTrue(p.effects.containsAll(setOf(HostKanbanStatusEffect.LatestTaskAtWrite, HostKanbanStatusEffect.HostMayRefuseAfterAuditWrite)))
            assertEquals("HostKanbanStatusPlan(redacted)", p.toString())
            assertFalse(p.toString().contains("Private"))
            assertThrows(UnsupportedOperationException::class.java) { (p.effects as MutableSet<*>).clear() }
            val mutation = runCatching { (p.body() as MutableMap<String, JsonElement>).clear() }.exceptionOrNull()
            assertTrue(mutation is UnsupportedOperationException || mutation is ClassCastException)
        }
    }
    @Test fun archivedBoardsAndInvalidIdentityCannotFormAPlan() {
        for (d in listOf(detail().copy(profileHomeId = "unknown"), detail().copy(board = board.copy(archived = true)), detail().copy(board = board.copy(slug = "../other")), detail().let { it.copy(task = it.task.copy(id = "../other")) })) {
            assertThrows(HostKanbanStatusInvalid::class.java) { HostKanbanStatusPlan.review(d, HostKanbanStatusTarget.Archived) }
        }
    }
}
