package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

/** A status verb is a host operation, not a cosmetic column move. Running requires dispatch. */
enum class HostKanbanStatusTarget(val wire: String) {
    Todo("todo"), Ready("ready"), Triage("triage"), Blocked("blocked"),
    Scheduled("scheduled"), Review("review"), Done("done"), Archived("archived")
}

/** Material effects to explain in a future private review. None are completion guarantees. */
enum class HostKanbanStatusEffect {
    LatestTaskAtWrite, HostMayRefuseAfterAuditWrite, MayCloseAttempt, ForceCurrentClaim,
    MayRequestWorkerTermination, WorkerMayContinue, MayQueueWork, MayRestoreImplementer,
    MayResumeReview, MayInvalidateDescendants, MayCleanManagedAndParentWorkspaces,
    MayStageReferencedFiles, ReplacesStoredResult, ParkWithoutWakeTime, MayInvokeLifecycleHook
}

class HostKanbanStatusInvalid : Exception("Unsupported or incomplete task status change.")

/**
 * Typed, private review foundation only; no transport or execution entry point.
 * The reviewed public PATCH has no revision/run fence. A future executor must retain
 * preflight, home/board binding, exact approval and one-send uncertain-outcome receipts.
 */
class HostKanbanStatusPlan private constructor(
    val subject: HostKanbanDetail,
    val target: HostKanbanStatusTarget,
    private val payload: JsonObject,
    val effects: Set<HostKanbanStatusEffect>
) {
    fun body(): JsonObject = payload
    override fun toString() = "HostKanbanStatusPlan(redacted)"

    companion object {
        private val known = HostKanbanStatusTarget.entries.map { it.wire }.toSet() + "running"
        private val direct = setOf(HostKanbanStatusTarget.Todo, HostKanbanStatusTarget.Ready, HostKanbanStatusTarget.Triage)

        fun review(subject: HostKanbanDetail, target: HostKanbanStatusTarget, statement: String? = null): HostKanbanStatusPlan = try {
            GatewayCronInventory.requireProfile(subject.owner.profile)
            require(subject.profileHomeId.matches(Regex("[a-f0-9]{64}")))
            require(HostKanbanProtocol.slug(subject.board.slug) && !subject.board.archived && HostKanbanProtocol.id(subject.task.id))
            val before = subject.task.status
            require(before in known && before != target.wire)
            val allowed = when (target) {
                HostKanbanStatusTarget.Blocked, HostKanbanStatusTarget.Review -> before in setOf("running", "ready")
                HostKanbanStatusTarget.Scheduled -> before in setOf("todo", "ready", "running", "blocked")
                HostKanbanStatusTarget.Done -> before in setOf("running", "ready", "blocked", "review")
                else -> true
            }
            require(allowed)
            statement?.let { value ->
                require(value.isNotBlank() && value.length <= 262144)
                require(value.none { it.isISOControl() && it !in "\n\r\t" })
                require(!value.codePoints().anyMatch { it in 0xD800..0xDFFF })
            }
            // The completion handler always writes result, even when omitted. Require an
            // explicit replacement rather than accidentally clearing an existing handoff.
            if (target in setOf(HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Blocked)) require(statement != null)
            if (target in direct || target == HostKanbanStatusTarget.Archived) require(statement == null)
            val values = linkedMapOf<String, JsonElement>("status" to JsonPrimitive(target.wire))
            statement?.let {
                values[when (target) {
                    HostKanbanStatusTarget.Done -> "result"
                    HostKanbanStatusTarget.Review -> "summary"
                    HostKanbanStatusTarget.Blocked, HostKanbanStatusTarget.Scheduled -> "block_reason"
                    else -> error("Statement is not supported for this verb")
                }] = JsonPrimitive(it)
            }
            val effects = linkedSetOf(HostKanbanStatusEffect.LatestTaskAtWrite, HostKanbanStatusEffect.HostMayRefuseAfterAuditWrite)
            if (before == "running" || target in setOf(HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Review, HostKanbanStatusTarget.Blocked, HostKanbanStatusTarget.Scheduled, HostKanbanStatusTarget.Archived)) {
                effects += HostKanbanStatusEffect.MayCloseAttempt
            }
            if (before == "running") effects += HostKanbanStatusEffect.WorkerMayContinue
            if (target in setOf(HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Review)) effects += HostKanbanStatusEffect.ForceCurrentClaim
            if (before == "running" && (target in direct || target == HostKanbanStatusTarget.Archived)) effects += HostKanbanStatusEffect.MayRequestWorkerTermination
            if (target in setOf(HostKanbanStatusTarget.Todo, HostKanbanStatusTarget.Ready, HostKanbanStatusTarget.Review, HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Archived)) effects += HostKanbanStatusEffect.MayQueueWork
            if (before == "review" && target in setOf(HostKanbanStatusTarget.Ready, HostKanbanStatusTarget.Todo)) effects += HostKanbanStatusEffect.MayRestoreImplementer
            if (before in setOf("blocked", "scheduled", "running") && target == HostKanbanStatusTarget.Ready) effects += HostKanbanStatusEffect.MayResumeReview
            if (before in setOf("done", "archived") && target in direct) {
                effects += HostKanbanStatusEffect.MayInvalidateDescendants
                effects += HostKanbanStatusEffect.MayRequestWorkerTermination
                effects += HostKanbanStatusEffect.WorkerMayContinue
            }
            if (target in setOf(HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Archived)) effects += HostKanbanStatusEffect.MayCleanManagedAndParentWorkspaces
            if (target in setOf(HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Review)) effects += HostKanbanStatusEffect.MayStageReferencedFiles
            if (target == HostKanbanStatusTarget.Done) effects += HostKanbanStatusEffect.ReplacesStoredResult
            if (target == HostKanbanStatusTarget.Scheduled) effects += HostKanbanStatusEffect.ParkWithoutWakeTime
            if (target in setOf(HostKanbanStatusTarget.Done, HostKanbanStatusTarget.Blocked)) effects += HostKanbanStatusEffect.MayInvokeLifecycleHook
            HostKanbanStatusPlan(subject, target, JsonObject(Collections.unmodifiableMap(values)), Collections.unmodifiableSet(effects))
        } catch (_: Exception) { throw HostKanbanStatusInvalid() }
    }
}
