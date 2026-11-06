package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal fun statusHash(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

class HostKanbanStatusImpactIncomplete : Exception("Task impact metadata is incomplete or unsupported.")
class HostKanbanStatusApprovalUsed : Exception("This status approval has already been consumed.")

/** Claims never leave this decoder as raw values. A claim witness is not verified process identity. */
class HostKanbanStatusObservation private constructor(val detail: HostKanbanDetail, val claimWitness: String, val sourceWitness: String, val reviewWitness: String, val responseBytes: Int) {
    override fun toString() = "HostKanbanStatusObservation(redacted)"

    fun same(other: HostKanbanStatusObservation): Boolean = claimWitness == other.claimWitness && reviewWitness == other.reviewWitness &&
        normalized(detail) == normalized(other.detail)

    companion object {
        private val required = setOf("current_run_id", "claim_lock", "worker_pid", "workspace_kind", "workspace_path", "result", "completion_contract")
        private fun canonical(value: JsonElement, depth: Int = 0): JsonElement {
            require(depth <= 64)
            return when (value) {
                is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value, depth + 1) })
                is JsonArray -> JsonArray(value.map { canonical(it, depth + 1) })
                else -> value
            }
        }
        internal fun witness(task: JsonObject): String {
            require(task.keys.containsAll(required))
            val claim = task.getValue("claim_lock")
            require(claim is JsonNull || claim is JsonPrimitive && claim.isString && claim.content.length <= 4096 && claim.content.none(Char::isISOControl))
            val pid = task.getValue("worker_pid")
            require(pid is JsonNull || pid is JsonPrimitive && !pid.isString && pid.longOrNull?.let { it > 0 } == true)
            return statusHash(JsonArray(listOf(task.getValue("current_run_id"), claim, pid)).toString())
        }
        fun parse(j: JsonObject, owner: ManagementTarget, home: String, board: HostKanbanBoard, id: String): HostKanbanStatusObservation = try {
            val detail = HostKanbanProtocol.detail(j, owner, home, board, id)
            require(detail.task.status in knownStatuses)
            val raw = j.toString()
            val bytes = raw.toByteArray(Charsets.UTF_8).size
            require(bytes <= 2_000_000)
            // Include undisplayed policy/claim fields in preflight. Only explicitly derived
            // diagnostics/age and advancing heartbeat/lease clocks are excluded.
            val stableTask = JsonObject(j.getValue("task").jsonObject.filterKeys { it !in setOf("age", "diagnostics", "warnings", "last_heartbeat_at", "claim_expires") })
            val stableRuns = JsonArray(j.getValue("runs").jsonArray.map { row -> JsonObject(row.jsonObject.filterKeys { it != "last_heartbeat_at" }) })
            val stable = JsonObject(j + mapOf("task" to stableTask, "runs" to stableRuns))
            HostKanbanStatusObservation(detail, witness(j.getValue("task").jsonObject), statusHash(raw), statusHash(canonical(stable).toString()), bytes)
        } catch (_: Exception) { throw HostKanbanStatusImpactIncomplete() }

        internal val knownStatuses = HostKanbanStatusTarget.entries.map { it.wire }.toSet() + "running"
        internal fun normalized(d: HostKanbanDetail) = d.copy(
            board = d.board.copy(counts = emptyMap(), total = null, current = false), observedAt = 0,
            task = d.task.copy(heartbeat = null), runs = d.runs.map { it.copy(heartbeat = null) }
        )
    }
}

/** Complete bounded connected task graph, not just the first page of direct children. */
class HostKanbanStatusChange private constructor(
    val inventory: HostKanbanInventory, val snapshot: HostKanbanSnapshot,
    val plan: HostKanbanStatusPlan, val observations: Map<String, HostKanbanStatusObservation>
) {
    val owner get() = inventory.owner
    val home get() = inventory.profileHomeId
    val board get() = snapshot.board
    val id get() = plan.subject.task.id
    val receiptTarget = "sha256:" + statusHash(board.slug + "\u0000" + id)
    fun body() = plan.body()
    override fun toString() = "HostKanbanStatusChange(redacted)"

    fun sameInventory(current: HostKanbanInventory) = current.owner == owner && current.profileHomeId == home &&
        current.boards.singleOrNull { it.slug == board.slug }?.definition() == board.definition() &&
        current.boards.singleOrNull { it.slug == "default" }?.dbPath == inventory.boards.single { it.slug == "default" }.dbPath

    fun sameReview(other: HostKanbanStatusChange) = sameInventory(other.inventory) && body() == other.body() &&
        observations.keys == other.observations.keys && observations.all { (id, observation) -> observation.same(other.observations.getValue(id)) }

    /** The user approves a task-scoped host operation, including its latest graph at write time. */
    fun approve(typedTaskId: String, acceptLatestTaskAndImpact: Boolean): HostKanbanStatusApproval {
        return HostKanbanStatusApproval.reviewed(this, typedTaskId, acceptLatestTaskAndImpact)
    }

    fun summary() = buildJsonObject {
        put("schema", 1); put("access_home_sha256", home)
        put("board_sha256", statusHash(board.slug)); put("db_sha256", statusHash(board.dbPath))
        put("anchor_db_sha256", statusHash(inventory.boards.single { it.slug == "default" }.dbPath))
        put("task_sha256", statusHash(id)); put("requested_sha256", statusHash(body().toString()))
        put("fields", JsonArray(body().keys.sorted().map(::JsonPrimitive)))
        put("requested_status", plan.target.wire); put("impact_count", observations.size)
        put("impact_sha256", statusHash(JsonArray(observations.toSortedMap().values.map { node ->
            buildJsonObject {
                val task = node.detail.task
                put("id", task.id); put("status", task.status); put("claim", node.claimWitness)
                put("observation_sha256", node.sourceWitness)
                put("title", task.title); put("body", task.body?.let(::JsonPrimitive) ?: JsonNull)
                put("result", task.result?.let(::JsonPrimitive) ?: JsonNull)
                put("fields", JsonObject(task.fields.toSortedMap().mapValues { JsonPrimitive(it.value) }))
                put("parents", JsonArray(node.detail.parents.sorted().map(::JsonPrimitive)))
                put("children", JsonArray(node.detail.children.sorted().map(::JsonPrimitive)))
            }
        }).toString()))
        put("latest_task_at_write", true); put("atomic_run_fence", false); put("worker_identity_verified", false)
        put("phase", "Status")
    }

    fun acknowledge(j: JsonObject): HostKanbanStatusAck = try {
        val task = HostKanbanProtocol.taskAcknowledgement(j)
        require(task.id == id && task.status in HostKanbanStatusObservation.knownStatuses)
        val witness = HostKanbanStatusObservation.witness(j.getValue("task").jsonObject)
        HostKanbanStatusAck(task, witness, task.status == plan.target.wire)
    } catch (_: Exception) { throw HostKanbanUnsupported() }

    fun acknowledgedSummary(ack: HostKanbanStatusAck) = JsonObject(summary() + buildJsonObject {
        put("host_acknowledged", true); put("reported_status", ack.task.status)
        put("observation_scope", "projected_task_and_claim_witness")
        put("requested_status_reported", ack.requestedStatusReported)
        put("worker_stop_verified", false); put("file_erasure_verified", false)
        put("runtime_adoption_verified", false); put("hook_execution_verified", false)
        put("descendant_effects_verified", false); put("engineering_completion_verified", false)
    })

    fun observed(ack: HostKanbanStatusAck, after: HostKanbanStatusObservation): Boolean {
        val d = after.detail
        if (d.owner != owner || d.profileHomeId != home || d.board.definition() != board.definition()) return false
        // PATCH deliberately omits derived latest_summary; heartbeat can advance during a read.
        return ack.claimWitness == after.claimWitness &&
            ack.task.copy(summary = null, heartbeat = null) == d.task.copy(summary = null, heartbeat = null)
    }

    companion object {
        const val MaxImpactTasks = 128
        const val MaxImpactBytes = 8 * 1024 * 1024
        const val ReceiptAction = "KanbanTaskStatus"
        fun receipt(action: String) = action == ReceiptAction

        fun review(inventory: HostKanbanInventory, snapshot: HostKanbanSnapshot, plan: HostKanbanStatusPlan, nodes: List<HostKanbanStatusObservation>): HostKanbanStatusChange {
            require(inventory.owner == snapshot.owner && inventory.profileHomeId == snapshot.profileHomeId)
            require(!snapshot.board.archived && inventory.boards.count { it.slug == "default" && !it.archived } == 1)
            require(inventory.boards.singleOrNull { it.slug == snapshot.board.slug }?.definition() == snapshot.board.definition())
            val root = plan.subject
            require(root.owner == inventory.owner && root.profileHomeId == inventory.profileHomeId && root.board.definition() == snapshot.board.definition())
            if (nodes.isEmpty() || nodes.size > MaxImpactTasks || nodes.sumOf { it.responseBytes.toLong() } > MaxImpactBytes || nodes.map { it.detail.task.id }.distinct().size != nodes.size) throw HostKanbanStatusImpactIncomplete()
            val graph = nodes.associateBy { it.detail.task.id }
            val actualRoot = graph[root.task.id]?.detail ?: throw HostKanbanStatusImpactIncomplete()
            if (HostKanbanStatusObservation.normalized(root) != HostKanbanStatusObservation.normalized(actualRoot)) throw HostKanbanTaskChanged()
            val cards = snapshot.columns.flatMap { it.tasks }.associateBy { it.id }
            for ((id, node) in graph) {
                val d = node.detail
                require(d.owner == inventory.owner && d.profileHomeId == inventory.profileHomeId && d.board.definition() == snapshot.board.definition())
                // The board carries a 200-character summary preview; the drawer is complete.
                if (cards[id]?.copy(heartbeat = null, summary = null) != d.task.copy(heartbeat = null, summary = null)) throw HostKanbanTaskChanged()
                if (d.parents.any { id !in (graph[it]?.detail?.children ?: throw HostKanbanStatusImpactIncomplete()) } ||
                    d.children.any { id !in (graph[it]?.detail?.parents ?: throw HostKanbanStatusImpactIncomplete()) }) throw HostKanbanStatusImpactIncomplete()
            }
            val seen = mutableSetOf<String>(); val queue = ArrayDeque(listOf(root.task.id))
            while (queue.isNotEmpty()) { val id = queue.removeFirst(); if (seen.add(id)) { val d = graph.getValue(id).detail; queue.addAll(d.parents); queue.addAll(d.children) } }
            if (seen != graph.keys) throw HostKanbanStatusImpactIncomplete()
            val degree = graph.mapValues { it.value.detail.parents.size }.toMutableMap()
            val ready = ArrayDeque(degree.filterValues { it == 0 }.keys); var visited = 0
            while (ready.isNotEmpty()) { val id = ready.removeFirst(); visited++; for (child in graph.getValue(id).detail.children) { degree[child] = degree.getValue(child) - 1; if (degree[child] == 0) ready.addLast(child) } }
            if (visited != graph.size) throw HostKanbanStatusImpactIncomplete()
            val statement = plan.body().entries.singleOrNull { it.key != "status" }?.value?.jsonPrimitive?.content
            val frozenPlan = HostKanbanStatusPlan.review(actualRoot, plan.target, statement)
            return HostKanbanStatusChange(inventory, snapshot, frozenPlan, Collections.unmodifiableMap(LinkedHashMap(graph)))
        }
    }
}

data class HostKanbanStatusAck(val task: HostKanbanTask, val claimWitness: String, val requestedStatusReported: Boolean) {
    override fun toString() = "HostKanbanStatusAck(redacted)"
}

class HostKanbanStatusApproval private constructor(val request: HostKanbanStatusChange) {
    internal val key: String = UUID.randomUUID().toString()
    private val consumed = AtomicBoolean(false)
    internal fun consume() { if (!consumed.compareAndSet(false, true)) throw HostKanbanStatusApprovalUsed() }
    override fun toString() = "HostKanbanStatusApproval(redacted)"
    companion object {
        internal fun reviewed(request: HostKanbanStatusChange, typedTaskId: String, acceptLatestTaskAndImpact: Boolean): HostKanbanStatusApproval {
            require(typedTaskId == request.id && acceptLatestTaskAndImpact)
            return HostKanbanStatusApproval(request)
        }
    }
}
