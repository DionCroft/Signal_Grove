package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.ManagementClient
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.network.HermesHttpException

data class HostKanbanStatusOutcome(val detail: HostKanbanDetail, val acknowledgement: HostKanbanStatusAck, val receipt: ManagementOperation) {
    override fun toString() = "HostKanbanStatusOutcome(redacted)"
}

/** Scoped graph preflight, explicit latest-task approval, one PATCH and durable ACK before readback. */
class HostKanbanStatusRepository(private val dao: HermesDao, private val ready: Deferred<Unit>) {
    private val reads = HostKanbanRepository()

    private fun context(owner: ManagementTarget, home: String, api: ManagementClient, tokens: ManagementTokens) {
        require(owner.url == api.base.toString() && owner.actor == tokens.provider + " · " + tokens.userId)
        require(home.matches(Regex("[a-f0-9]{64}")))
        GatewayCronInventory.requireProfile(owner.profile)
    }

    private suspend fun home(owner: ManagementTarget, expected: String, api: ManagementClient, tokens: ManagementTokens) {
        if (api.profiles(tokens).singleOrNull { it.name == owner.profile }?.pluginHome?.identity() != expected) throw HostKanbanTaskChanged()
        currentCoroutineContext().ensureActive()
    }

    /** Read-only preparation. The caller reviews this returned graph, not an earlier cached graph. */
    suspend fun prepare(inventory: HostKanbanInventory, snapshot: HostKanbanSnapshot, plan: HostKanbanStatusPlan, api: ManagementClient, tokens: ManagementTokens): HostKanbanStatusChange {
        val owner = inventory.owner; val expectedHome = inventory.profileHomeId
        context(owner, expectedHome, api, tokens)
        require(snapshot.owner == owner && snapshot.profileHomeId == expectedHome && !snapshot.board.archived)
        require(plan.subject.owner == owner && plan.subject.profileHomeId == expectedHome && plan.subject.board.definition() == snapshot.board.definition())
        require(inventory.boards.count { it.slug == "default" && !it.archived } == 1 && inventory.boards.singleOrNull { it.slug == snapshot.board.slug }?.definition() == snapshot.board.definition())
        val before = reads.inventory(owner, expectedHome, api, tokens)
        fun mapping(current: HostKanbanInventory): Boolean = current.owner == owner && current.profileHomeId == expectedHome &&
            current.boards.singleOrNull { it.slug == snapshot.board.slug }?.definition() == snapshot.board.definition() &&
            current.boards.singleOrNull { it.slug == "default" }?.dbPath == inventory.boards.singleOrNull { it.slug == "default" }?.dbPath
        if (!mapping(before)) throw HostKanbanTaskChanged()
        val board = reads.board(owner, expectedHome, snapshot.board, api, tokens)
        val graph = linkedMapOf<String, HostKanbanStatusObservation>(); val queue = ArrayDeque(listOf(plan.subject.task.id)); var bytes = 0L
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val id = queue.removeFirst()
            if (id in graph) continue
            if (graph.size >= HostKanbanStatusChange.MaxImpactTasks) throw HostKanbanStatusImpactIncomplete()
            val response = try { api.kanbanTask(tokens, owner.profile, board.board.slug, id) }
            catch (error: HermesHttpException) { if (error.code == 404) throw HostKanbanStatusImpactIncomplete() else throw error }
            val node = withContext(Dispatchers.Default) { HostKanbanStatusObservation.parse(response, owner, expectedHome, board.board, id) }
            bytes += node.responseBytes
            if (bytes > HostKanbanStatusChange.MaxImpactBytes) throw HostKanbanStatusImpactIncomplete()
            graph[id] = node; queue.addAll(node.detail.parents); queue.addAll(node.detail.children)
        }
        val after = reads.inventory(owner, expectedHome, api, tokens)
        if (!mapping(after)) throw HostKanbanTaskChanged()
        home(owner, expectedHome, api, tokens)
        return withContext(Dispatchers.Default) { HostKanbanStatusChange.review(after, board, plan, graph.values.toList()) }
    }

    suspend fun execute(owner: ManagementTarget, approval: HostKanbanStatusApproval, api: ManagementClient, tokens: ManagementTokens): HostKanbanStatusOutcome {
        ready.await()
        val request = approval.request
        require(owner == request.owner); context(owner, request.home, api, tokens)
        approval.consume()
        if (dao.unresolvedManagementOperations(owner.resourceScope, owner.url, request.home).isNotEmpty()) throw ManagementOutcomePending()
        val fresh = prepare(request.inventory, request.snapshot, request.plan, api, tokens)
        if (!request.sameReview(fresh)) throw HostKanbanTaskChanged()
        home(owner, request.home, api, tokens)
        val record = ManagementOperation(approval.key, owner.resourceScope, owner.apiScope, owner.url, owner.profile, owner.actor,
            HostKanbanStatusChange.ReceiptAction, request.receiptTarget, request.summary().toString(), "sending", "", System.currentTimeMillis())
        var attempted = false
        try {
            dao.claimManagementOperation(record); currentCoroutineContext().ensureActive(); attempted = true
            val ack = request.acknowledge(api.changeKanbanStatus(tokens, owner.profile, request))
            val summary = request.acknowledgedSummary(ack)
            withContext(NonCancellable) { dao.finishManagementOperation(record.key, "acknowledged", summary.toString()) }
            home(owner, request.home, api, tokens)
            if (!request.sameInventory(reads.inventory(owner, request.home, api, tokens))) throw HostKanbanTaskChanged()
            val response = api.kanbanTask(tokens, owner.profile, request.board.slug, request.id)
            val after = withContext(Dispatchers.Default) { HostKanbanStatusObservation.parse(response, owner, request.home, request.board, request.id) }
            home(owner, request.home, api, tokens)
            if (!request.sameInventory(reads.inventory(owner, request.home, api, tokens))) throw HostKanbanTaskChanged()
            if (request.observed(ack, after)) withContext(NonCancellable) {
                dao.confirmManagementOperation(record.key, JsonObject(summary + mapOf("reported_state_observed" to JsonPrimitive(true))).toString())
            }
            return HostKanbanStatusOutcome(after.detail, ack, dao.managementOperation(record.key)!!)
        } catch (error: Exception) {
            val retained = withContext(NonCancellable) {
                val row = dao.managementOperation(record.key)
                if (row?.state in setOf("sending", "unknown")) dao.finishManagementOperation(record.key,
                    if (attempted) "unknown" else "refused", if (attempted) "" else buildJsonObject { put("local_not_attempted", true) }.toString())
                dao.managementOperation(record.key)
            }
            if (error is CancellationException) throw error
            when (retained?.state) {
                "unknown" -> throw HostKanbanTaskUncertain(retained)
                "acknowledged", "confirmed" -> throw HostKanbanTaskReadbackPending(retained)
                "refused" -> throw HostKanbanTaskRefused(retained)
                else -> throw error
            }
        }
    }
}
