package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostKanbanStatusChangeTest {
    private val owner = ManagementTarget("a".repeat(64), "https://dashboard.example/prefix/", "worker", "password · actor")
    private val home = "b".repeat(64)
    private fun obj(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun meta(slug: String) = obj("""{"slug":"$slug","name":"Private board","description":null,"icon":null,"color":null,"db_path":"/private/$slug.db","default_workdir":null,"project_id":null,"archived":false,"created_at":1,"counts":{}}""")
    private val inventory get() = HostKanbanProtocol.inventory(buildJsonObject { put("boards", JsonArray(listOf(meta("default"), meta("garden")))); put("current", "garden") }, owner, home, 1)
    private val board get() = inventory.boards.single { it.slug == "garden" }
    private fun card(id: String = "t_root", status: String = "ready") = buildJsonObject {
        put("id", id); put("title", "Private title"); put("status", status); put("priority", 0); put("body", "Private body")
        put("assignee", JsonNull); put("tenant", JsonNull); put("workspace_kind", "scratch"); put("workspace_path", "/private/scratch")
        for (key in listOf("current_run_id", "claim_lock", "worker_pid", "result", "completion_contract", "model_override", "provider_override", "reasoning_effort")) put(key, JsonNull)
    }
    private fun detail(task: JsonObject, parents: List<String> = emptyList(), children: List<String> = emptyList()) = buildJsonObject {
        put("task", task); for (key in listOf("comments", "runs", "events", "attachments", "link_tasks", "child_results")) put(key, JsonArray(emptyList()))
        put("links", buildJsonObject { put("parents", JsonArray(parents.map(::JsonPrimitive))); put("children", JsonArray(children.map(::JsonPrimitive))) })
    }
    private fun decode(j: JsonObject) = HostKanbanStatusObservation.parse(j, owner, home, board, j.getValue("task").jsonObject.getValue("id").jsonPrimitive.content)
    private fun node(task: JsonObject = card(), parents: List<String> = emptyList(), children: List<String> = emptyList()) = decode(detail(task, parents, children))
    private fun snapshot(nodes: List<HostKanbanStatusObservation>) = HostKanbanSnapshot(owner, home, board, listOf(HostKanbanColumn("ready", nodes.map { it.detail.task })), emptyList(), emptyList(), 0, 1, 1)
    private fun change(nodes: List<HostKanbanStatusObservation> = listOf(node()), target: HostKanbanStatusTarget = HostKanbanStatusTarget.Triage, statement: String? = null): HostKanbanStatusChange {
        // Board cards use the same typed observations; no hidden wire fields are needed there.
        val s = snapshot(nodes).copy(columns = listOf(HostKanbanColumn("ready", nodes.map { it.detail.task })))
        val root = nodes.first { it.detail.task.id == "t_root" }.detail
        return HostKanbanStatusChange.review(inventory, s, HostKanbanStatusPlan.review(root, target, statement), nodes)
    }
    private fun taskField(j: JsonObject, key: String, value: JsonElement) = JsonObject(j + (key to value))
    private fun chain(size: Int, body: String? = null) = (0 until size).map { index ->
        val id = if (index == 0) "t_root" else "t_$index"
        val task = card(id).let { if (body == null) it else taskField(it, "body", JsonPrimitive(body)) }
        node(task, if (index == 0) emptyList() else listOf(if (index == 1) "t_root" else "t_${index - 1}"), if (index + 1 == size) emptyList() else listOf("t_${index + 1}"))
    }

    @Test fun fullConnectedGraphIncludesAncestorsAndTransitiveDescendants() {
        val nodes = listOf(node(card(), listOf("t_parent"), listOf("t_child")), node(card("t_parent", "done"), children = listOf("t_root")), node(card("t_child"), listOf("t_root"), listOf("t_grandchild")), node(card("t_grandchild"), listOf("t_child")))
        assertEquals(setOf("t_root", "t_parent", "t_child", "t_grandchild"), change(nodes).observations.keys)
    }
    @Test fun missingTransitiveTaskCannotBecomeACompleteImpactGraph() {
        assertThrows(HostKanbanStatusImpactIncomplete::class.java) { change(chain(3).dropLast(1)) }
    }
    @Test fun asymmetricAndSelfLinksAreRejected() {
        for (nodes in listOf(listOf(node(children = listOf("t_child")), node(card("t_child"))), listOf(node(parents = listOf("t_root"), children = listOf("t_root"))))) assertThrows(HostKanbanStatusImpactIncomplete::class.java) { change(nodes) }
    }
    @Test fun disconnectedNodesAndDirectedCyclesAreRejected() {
        assertThrows(HostKanbanStatusImpactIncomplete::class.java) { change(listOf(node(), node(card("t_elsewhere")))) }
        assertThrows(HostKanbanStatusImpactIncomplete::class.java) { change(listOf(node(parents = listOf("t_child"), children = listOf("t_child")), node(card("t_child"), listOf("t_root"), listOf("t_root")))) }
    }
    @Test fun duplicateObservationsNeverOverwriteAnImpactIdentity() {
        assertThrows(HostKanbanStatusImpactIncomplete::class.java) { change(listOf(node(), node())) }
    }
    @Test fun graphTaskLimitHasAnAcceptedBoundaryAndRefusesOverflow() {
        assertEquals(128, change(chain(128)).observations.size)
        assertThrows(HostKanbanStatusImpactIncomplete::class.java) { change(chain(129)) }
    }
    @Test fun aggregateResponseBudgetPreventsLargeGraphRetention() {
        assertThrows(HostKanbanStatusImpactIncomplete::class.java) { change(chain(34, "x".repeat(262144))) }
    }
    @Test fun explicitNullRunClaimAndWorkspaceMetadataDiffersFromOmission() {
        assertNotNull(change())
        for (field in listOf("current_run_id", "claim_lock", "worker_pid", "workspace_kind", "workspace_path", "result", "completion_contract")) assertThrows(HostKanbanStatusImpactIncomplete::class.java) { node(JsonObject(card().filterKeys { it != field })) }
    }
    @Test fun invalidClaimAndPidShapesAreNotSilentlyCoerced() {
        for ((field, value) in listOf("claim_lock" to JsonPrimitive(12), "claim_lock" to JsonPrimitive("bad\nclaim"), "worker_pid" to JsonPrimitive(-1), "worker_pid" to JsonPrimitive("12"), "current_run_id" to JsonPrimitive("7"))) assertThrows(HostKanbanStatusImpactIncomplete::class.java) { node(taskField(card(), field, value)) }
    }
    @Test fun claimAndRunChangesRemainVisibleWithoutRetainingRawClaimValues() {
        val original = node()
        for ((field, value) in listOf("claim_lock" to JsonPrimitive("private-claim"), "worker_pid" to JsonPrimitive(321), "current_run_id" to JsonPrimitive(7))) assertFalse(original.same(node(taskField(card(), field, value))))
        assertFalse(change(listOf(node(taskField(card(), "claim_lock", JsonPrimitive("private-claim"))))).summary().toString().contains("private-claim"))
    }
    @Test fun heartbeatProgressDoesNotInvalidateAnOtherwiseIdenticalReview() {
        val old = change(); val fresh = change(listOf(node(taskField(card(), "last_heartbeat_at", JsonPrimitive(9)))))
        assertTrue(old.sameReview(fresh)); assertNotEquals(old.summary()["impact_sha256"], fresh.summary()["impact_sha256"])
    }
    @Test fun materialTaskOrHistoryChangesInvalidateTheReview() {
        val old = node()
        assertFalse(old.same(node(taskField(card(), "body", JsonPrimitive("Changed")))))
        val comment = obj("""{"id":1,"task_id":"t_root","author":"owner","body":"New decision","created_at":1}""")
        assertFalse(old.same(decode(JsonObject(detail(card()) + ("comments" to JsonArray(listOf(comment)))))))
    }
    @Test fun approvalRequiresTypedIdentityAndExplicitLatestTaskConsentAndIsOneUse() {
        val c = change()
        assertThrows(IllegalArgumentException::class.java) { c.approve("t_other", true) }
        assertThrows(IllegalArgumentException::class.java) { c.approve("t_root", false) }
        val approval = c.approve("t_root", true); approval.consume()
        assertThrows(HostKanbanStatusApprovalUsed::class.java) { approval.consume() }
    }
    @Test fun summariesKeepExactValuesPrivateAndAllSideEffectGuaranteesFalse() {
        val c = change(target = HostKanbanStatusTarget.Done, statement = "Private replacement result")
        val summary = c.summary().toString()
        for (private in listOf("Private title", "Private body", "Private replacement result", "/private/", "t_root", "garden")) assertFalse(private, summary.contains(private))
        val ack = c.acknowledge(buildJsonObject { put("task", taskField(card(status = "done"), "result", JsonPrimitive("Private replacement result"))) })
        val after = c.acknowledgedSummary(ack)
        for (key in listOf("atomic_run_fence", "worker_identity_verified", "worker_stop_verified", "file_erasure_verified", "runtime_adoption_verified", "hook_execution_verified", "descendant_effects_verified", "engineering_completion_verified")) assertEquals(JsonPrimitive(false), after[key])
        for (value in listOf(c.toString(), ack.toString(), c.approve("t_root", true).toString(), c.observations.values.single().toString())) assertTrue(value.endsWith("(redacted)"))
    }
    @Test fun reportedDifferentStatusCanBeObservedWithoutPretendingTheRequestMatched() {
        val c = change(); val ack = c.acknowledge(buildJsonObject { put("task", card()) })
        assertFalse(ack.requestedStatusReported); assertTrue(c.observed(ack, node()))
        assertEquals(JsonPrimitive(false), c.acknowledgedSummary(ack)["requested_status_reported"])
    }
    @Test fun derivedSummaryAndHeartbeatAreNotConfusedWithAckDivergence() {
        val c = change(); val ack = c.acknowledge(buildJsonObject { put("task", card(status = "triage")) })
        val after = node(taskField(taskField(card(status = "triage"), "latest_summary", JsonPrimitive("Derived handoff")), "last_heartbeat_at", JsonPrimitive(5)))
        assertTrue(c.observed(ack, after))
        assertFalse(c.observed(ack, node(taskField(card(status = "triage"), "body", JsonPrimitive("Diverged")))))
    }
    @Test fun foreignUnknownOrIncompleteAcknowledgementsStayUnsupported() {
        val c = change()
        for (task in listOf(card("foreign"), card(status = "future_state"), JsonObject(card().filterKeys { it != "current_run_id" }))) assertThrows(HostKanbanUnsupported::class.java) { c.acknowledge(buildJsonObject { put("task", task) }) }
    }
    @Test fun changedBoardHomeOrRootSnapshotCannotBorrowAnotherReview() {
        val c = change(); assertFalse(c.sameInventory(inventory.copy(profileHomeId = "c".repeat(64))))
        assertFalse(c.sameInventory(inventory.copy(boards = inventory.boards.map { if (it.slug == "default") it.copy(dbPath = "/other/root.db") else it })))
        val n = node(); val s = snapshot(listOf(n)).copy(columns = listOf(HostKanbanColumn("ready", listOf(n.detail.task.copy(body = "Changed")))))
        assertThrows(HostKanbanTaskChanged::class.java) { HostKanbanStatusChange.review(inventory, s, c.plan, listOf(n)) }
    }
    @Test fun undisplayedPolicyAndUnknownWireFieldsStillInvalidateApproval() {
        val old = node()
        for (key in listOf("block_kind", "created_by", "future_policy")) assertFalse(key, old.same(node(taskField(card(), key, JsonPrimitive("Changed private policy")))))
    }
    @Test fun leaseExtensionAndDerivedAgeDoNotInvalidateStructuralIdentity() {
        val old = node(taskField(card(), "claim_expires", JsonPrimitive(1)))
        val fresh = node(taskField(taskField(card(), "claim_expires", JsonPrimitive(2)), "age", buildJsonObject { put("created_age_seconds", 5) }))
        assertTrue(old.same(fresh))
    }
    @Test fun boardSummaryPreviewDoesNotReplaceOrRejectTheFullHandoff() {
        val full = "Private handoff ".repeat(40)
        val n = node(taskField(card(), "latest_summary", JsonPrimitive(full)))
        val s = snapshot(listOf(n)).copy(columns = listOf(HostKanbanColumn("ready", listOf(n.detail.task.copy(summary = full.take(200))))))
        val c = HostKanbanStatusChange.review(inventory, s, HostKanbanStatusPlan.review(n.detail, HostKanbanStatusTarget.Triage), listOf(n))
        assertEquals(full, c.plan.subject.task.summary)
    }
    @Test fun wireObjectOrderingDoesNotChangeAReviewedObservation() {
        val original = detail(card())
        val reordered = JsonObject(original.entries.reversed().associate { (key, value) -> key to if (key == "task") JsonObject(value.jsonObject.entries.reversed().associate { it.toPair() }) else value })
        assertTrue(decode(original).same(decode(reordered)))
    }
    @Test fun deeplyNestedUnknownMetadataIsExplicitlyUnsupported() {
        var value: JsonElement = JsonPrimitive("private")
        repeat(80) { value = buildJsonObject { put("nested", value) } }
        val task = taskField(card(), "future_policy", value)
        assertThrows(HostKanbanStatusImpactIncomplete::class.java) { node(task) }
    }
}
