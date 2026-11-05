package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostKanbanTest {
    private val owner=ManagementTarget("a".repeat(64),"https://dashboard.example/prefix/","worker","provider · actor")
    private val home="b".repeat(64)
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private fun board(slug:String="garden")=obj("""{"slug":"$slug","name":"Private board","description":"Private description","db_path":"/shared/kanban/boards/$slug/kanban.db","default_workdir":"/srv/private/repo","project_id":"p_123","project_name":"Private project","archived":false,"is_current":false,"counts":{"running":1,"archived":2},"total":1,"default_workspace_kind":"worktree","created_at":null}""")
    private fun listing(rows:List<JsonObject> = listOf(board()))=buildJsonObject {put("boards",JsonArray(rows));put("current","default")}
    private fun task(id:String="t_123",status:String="running")=obj("""{"id":"$id","title":"Private task","status":"$status","priority":-7,"assignee":"worker","tenant":"private-tenant","body":"Private body\n第二行","created_at":null,"started_at":1793820000,"completed_at":null,"latest_summary":"Private summary","result":null,"current_run_id":9,"last_heartbeat_at":1793820001,"workspace_kind":"worktree","workspace_path":"/srv/private/repo","goal_mode":false,"skills":[],"claim_lock":"secret-claim","idempotency_key":"secret-idempotency","unknown_secret":"discard-me"}""")
    private fun snapshot(tasks:List<JsonObject> = listOf(task()),column:String="running")=buildJsonObject {put("columns",buildJsonArray {add(buildJsonObject {put("name",column);put("tasks",JsonArray(tasks))})});put("tenants",JsonArray(listOf(JsonPrimitive("private-tenant"))));put("assignees",JsonArray(listOf(JsonPrimitive("worker"))));put("latest_event_id",12);put("now",1793820010)}
    private fun detail()=obj("""{"task":${task()},"comments":[{"id":1,"task_id":"t_123","author":"Private author","body":"Private comment","created_at":1793820000}],"runs":[{"id":9,"task_id":"t_123","status":"running","outcome":null,"profile":"worker","started_at":1793820000,"ended_at":null,"summary":"Private full summary","metadata":{"secret":"private-metadata"}}],"events":[{"id":12,"task_id":"t_123","kind":"claimed","created_at":1793820000,"run_id":9,"payload":{"secret":"private-event"}}],"attachments":[{"id":4,"task_id":"t_123","filename":"Private attachment.txt","size":12,"created_at":1793820000,"stored_path":"/srv/private/attachment.txt"}],"links":{"parents":["t_missing"],"children":["t_child"]},"link_tasks":[{"id":"t_child","title":"Private child","status":"done"}],"child_results":[{"id":"t_child","title":"Private child","status":"done","latest_summary":"Private handoff","result":null}]}""")
    private fun inventory(j:JsonObject=listing())=HostKanbanProtocol.inventory(j,owner,home,123)
    private fun selected()=inventory().boards.single()
    private fun read(j:JsonObject=detail())=HostKanbanProtocol.detail(j,owner,home,selected(),"t_123",456)
    @Test fun sharedBoardPathsAndSeparateCurrentPointerAreObservedWithoutInventingCounts() {
        val s=inventory();val b=s.boards.single()
        assertEquals(owner,s.owner);assertEquals(home,s.profileHomeId);assertEquals(123L,s.observedAt)
        assertEquals("default",s.current);assertFalse(b.current);assertEquals("/shared/kanban/boards/garden/kanban.db",b.dbPath)
        assertEquals(1L,b.total);assertEquals(2L,b.counts["archived"]);assertNull(b.counts["done"]);assertNull(b.createdAt)
    }
    @Test fun synthesizedDefaultBoardAllowsNullMetadataAndEmptyCounts() {
        val b=obj("""{"slug":"default","db_path":"/shared/kanban.db","counts":{},"created_at":null}""")
        val v=inventory(listing(listOf(b))).boards.single();assertEquals("default",v.name);assertTrue(v.counts.isEmpty());assertNull(v.total);assertNull(v.workdir)
    }
    @Test fun futureTaskStatusInFallbackColumnRemainsItsActualStatus() {
        val s=HostKanbanProtocol.snapshot(snapshot(listOf(task(status="future_state")),"todo"),owner,home,selected(),456)
        val t=s.columns.single().tasks.single();assertEquals("todo",s.columns.single().name);assertEquals("future_state",t.status);assertEquals(-7L,t.priority)
        assertNull(t.createdAt);assertNull(t.completedAt);assertEquals(9L,t.currentRunId);assertEquals("false",t.fields["goal_mode"]);assertEquals("",t.fields["skills"])
        assertFalse(t.fields.containsKey("claim_lock"));assertFalse(t.fields.containsKey("idempotency_key"));assertFalse(t.fields.containsKey("unknown_secret"))
    }
    @Test fun fullDetailPreservesMissingLinksAndIndependentRunFactsWithoutPayloadProjection() {
        val d=read();assertEquals("Private full summary",d.runs.single().summary);assertNull(d.runs.single().outcome);assertNull(d.runs.single().endedAt)
        assertEquals(listOf("t_missing"),d.parents);assertFalse(d.linked.any {it.id=="t_missing"});assertEquals("Private handoff",d.childResults.single().summary)
        assertEquals("Private comment",d.comments.single().body);assertEquals("claimed",d.events.single().kind);assertEquals("Private attachment.txt",d.attachments.single().filename)
        assertEquals(456L,d.observedAt);assertEquals(9L,d.events.single().runId)
    }
    @Test fun canonicalTaskAndEveryOwnedHistoryRowMustMatchRequestedId() {
        assertThrows(HostKanbanUnsupported::class.java) {read(JsonObject(detail()+mapOf("task" to task("t_other"))))}
        for(key in listOf("comments","runs","events","attachments")) {
            val row=detail()[key]!!.jsonArray.single().jsonObject
            assertThrows(HostKanbanUnsupported::class.java) {read(JsonObject(detail()+mapOf(key to JsonArray(listOf(JsonObject(row+mapOf("task_id" to JsonPrimitive("t_other"))))))))}
        }
        val child=detail()["child_results"]!!.jsonArray.single().jsonObject
        assertThrows(HostKanbanUnsupported::class.java) {read(JsonObject(detail()+mapOf("child_results" to JsonArray(listOf(JsonObject(child+mapOf("id" to JsonPrimitive("t_other"))))))))}
    }
    @Test fun duplicateBoardColumnTaskAndHistoryIdentitiesAreRefusedAsAWhole() {
        assertThrows(HostKanbanUnsupported::class.java) {inventory(listing(listOf(board(),board())))}
        assertThrows(HostKanbanUnsupported::class.java) {HostKanbanProtocol.snapshot(snapshot(listOf(task(),task())),owner,home,selected())}
        val c=snapshot()["columns"]!!.jsonArray.single()
        assertThrows(HostKanbanUnsupported::class.java) {HostKanbanProtocol.snapshot(JsonObject(snapshot()+mapOf("columns" to JsonArray(listOf(c,c)))),owner,home,selected())}
        for(key in listOf("comments","runs","events","attachments","link_tasks","child_results")) {val row=detail()[key]!!.jsonArray.single();assertThrows(HostKanbanUnsupported::class.java) {read(JsonObject(detail()+mapOf(key to JsonArray(listOf(row,row)))))}}
    }
    @Test fun malformedTypesPathsAndTimestampOverflowCannotBecomeReadableData() {
        for((key,value) in listOf("slug" to JsonPrimitive("../escape"),"archived" to JsonPrimitive("false"),"db_path" to JsonPrimitive(4),"created_at" to JsonPrimitive(Long.MAX_VALUE),"counts" to obj("""{"done":-1}"""))) assertThrows(HostKanbanUnsupported::class.java) {inventory(listing(listOf(JsonObject(board()+mapOf(key to value)))))}
        for((key,value) in listOf("id" to JsonPrimitive("../escape"),"priority" to JsonPrimitive("7"),"completed_at" to JsonPrimitive(Long.MAX_VALUE),"current_run_started_at" to JsonPrimitive(Long.MAX_VALUE),"status" to JsonPrimitive(7),"body" to JsonPrimitive("\u0000private"),"body" to JsonPrimitive("\uD800"))) assertThrows(HostKanbanUnsupported::class.java) {HostKanbanProtocol.snapshot(snapshot(listOf(JsonObject(task()+mapOf(key to value)))),owner,home,selected())}
    }
    @Test fun explicitEmptyHistoriesAndColumnsAreAcceptedWithoutSyntheticTasks() {
        val d=detail().toMutableMap();for(key in listOf("comments","runs","events","attachments","link_tasks","child_results")) d[key]=JsonArray(emptyList())
        d["links"]=obj("""{"parents":[],"children":[]}""")
        val value=read(JsonObject(d));assertTrue(value.comments.isEmpty());assertTrue(value.runs.isEmpty());assertTrue(value.attachments.isEmpty())
        val s=HostKanbanProtocol.snapshot(snapshot(emptyList()),owner,home,selected());assertTrue(s.columns.single().tasks.isEmpty())
    }
    @Test fun oversizeArraysAndTextRejectEntireResponseInsteadOfTruncatingSuccess() {
        assertThrows(HostKanbanUnsupported::class.java) {inventory(listing((0..500).map {board("b$it")}))}
        assertThrows(HostKanbanUnsupported::class.java) {HostKanbanProtocol.snapshot(snapshot((0..2000).map {task("t_$it")}),owner,home,selected())}
        assertThrows(HostKanbanUnsupported::class.java) {read(JsonObject(detail()+mapOf("task" to JsonObject(task()+mapOf("body" to JsonPrimitive("x".repeat(262145)))))))}
    }
    @Test fun privateProjectionListsAndMapsAreImmutableAndRenderingIsRedacted() {
        val i=inventory();val d=read();val s=HostKanbanProtocol.snapshot(snapshot(),owner,home,selected())
        assertThrows(UnsupportedOperationException::class.java) {(i.boards as MutableList<HostKanbanBoard>).clear()}
        assertThrows(UnsupportedOperationException::class.java) {(i.boards.single().counts as MutableMap<String,Long>).clear()}
        assertThrows(UnsupportedOperationException::class.java) {(d.comments as MutableList<HostKanbanComment>).clear()}
        assertThrows(UnsupportedOperationException::class.java) {(d.task.fields as MutableMap<String,String>).clear()}
        assertThrows(UnsupportedOperationException::class.java) {(s.columns.single().tasks as MutableList<HostKanbanTask>).clear()}
        for(v in listOf(i,i.boards.single(),d,d.task,d.comments.single(),d.runs.single(),d.events.single(),d.attachments.single(),d.linked.single(),s,s.columns.single())) {assertFalse(v.toString().contains("Private"));assertFalse(v.toString().contains("/shared/"));assertFalse(v.toString().contains("secret"))}
    }
    @Test fun mappingComparisonIgnoresLiveCountsButDetectsRootAndArchiveChanges() {
        val b=selected();assertTrue(b.sameMapping(b.copy(total=99,name="Renamed",current=true)))
        assertFalse(b.sameMapping(b.copy(dbPath="/other/kanban.db")));assertFalse(b.sameMapping(b.copy(archived=true)));assertFalse(b.sameMapping(b.copy(slug="another")))
    }
    @Test fun invalidAccessProfileAndUnsafeSelectorsFailClosed() {
        assertThrows(HostKanbanUnsupported::class.java) {HostKanbanProtocol.inventory(listing(),owner.copy(profile="all"),home)}
        assertThrows(HostKanbanUnsupported::class.java) {HostKanbanProtocol.inventory(listing(),owner,"not-a-home-hash")}
        for(value in listOf("../x","x/y","x?board=other","Upper","", "x".repeat(65))) assertFalse(HostKanbanProtocol.slug(value))
        assertTrue(HostKanbanProtocol.id("t_future:123"));assertFalse(HostKanbanProtocol.id("t/123"))
    }
}
