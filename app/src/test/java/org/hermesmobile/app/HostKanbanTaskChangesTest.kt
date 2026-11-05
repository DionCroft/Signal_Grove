package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostKanbanTaskChangesTest {
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private val owner=ManagementTarget("a".repeat(64),"https://dashboard.example/prefix/","worker","password · actor")
    private val home="b".repeat(64)
    private fun meta(slug:String="garden")=obj("""{"slug":"$slug","name":"Private board","description":null,"icon":null,"color":null,"db_path":"/shared/$slug.db","default_workdir":null,"project_id":null,"archived":false,"created_at":1,"counts":{}}""")
    private fun inventory(board:JsonObject=meta())=HostKanbanProtocol.inventory(buildJsonObject {put("boards",JsonArray(listOf(meta("default"),board)));put("current","garden")},owner,home,1)
    private fun card(id:String="t_child",title:String="Private title",status:String="ready")=obj("""{"id":"$id","title":"$title","body":"Private body","status":"$status","priority":0,"assignee":null,"tenant":null,"workspace_kind":"scratch","goal_mode":false}""")
    private fun board(inventory:HostKanbanInventory=inventory(),cards:List<JsonObject> = listOf(card(),card("t_parent",status="done")))=HostKanbanProtocol.snapshot(buildJsonObject {put("columns",buildJsonArray {add(buildJsonObject {put("name","ready");put("tasks",JsonArray(cards))})});put("tenants",JsonArray(emptyList()));put("assignees",JsonArray(emptyList()));put("latest_event_id",0);put("now",1)},owner,home,inventory.boards.single {it.slug=="garden"},1)
    private fun detail(task:JsonObject=card(),parents:List<String> = emptyList(),snapshot:HostKanbanSnapshot=board())=HostKanbanProtocol.detail(buildJsonObject {put("task",task);for(k in listOf("comments","runs","events","attachments","link_tasks","child_results")) put(k,JsonArray(emptyList()));put("links",buildJsonObject {put("parents",JsonArray(parents.map(::JsonPrimitive)));put("children",JsonArray(emptyList()))})},owner,home,snapshot.board,task.getValue("id").jsonPrimitive.content,1)
    private fun create(d:HostKanbanTaskDraft=HostKanbanTaskDraft("Private new task"),inv:HostKanbanInventory=inventory(),projects:HostProjectSnapshot?=null)=HostKanbanTaskChange.create(inv,board(inv),d,projects)
    private fun patch(task:JsonObject=card(),title:String?="New title",body:String?=null)=HostKanbanTaskChange.metadata(inventory(),board(),detail(task),title,body)
    private fun ack(task:JsonObject=card())=buildJsonObject {put("task",task)}
    private fun fields(j:JsonObject,vararg fields:Pair<String,JsonElement>)=JsonObject(j+mapOf(*fields))

    @Test fun fullCreateUsesOnlyVerifiedSchemaAndFrozenPrivateNonce() {
        val parents=mutableListOf("t_parent");val skills=mutableListOf(" review ","review")
        val request=create(HostKanbanTaskDraft(" New task ",body="",assignee=" WORKER ",tenant="tenant",priority=-3,workspaceKind="dir",workspacePath="/srv/work",parents=parents,triage=true,maxRuntimeSeconds=60,skills=skills,goalMode=true,goalMaxTurns=20,model=" model ",provider=" provider ",reasoning=" HIGH ",projectId=""))
        val first=request.body().toString();parents.clear();skills.clear();assertEquals(first,request.body().toString());assertEquals(18,request.values.size)
        val literal=obj("""{"title":"New task","body":"","assignee":"worker","tenant":"tenant","priority":-3,"workspace_kind":"dir","workspace_path":"/srv/work","parents":["t_parent"],"triage":true,"max_runtime_seconds":60,"skills":["review"],"goal_mode":true,"goal_max_turns":20,"model_override":"model","provider_override":"provider","reasoning_effort":"high","project_id":""}""")
        assertEquals(literal,JsonObject(request.values.filterKeys {it!="idempotency_key"}))
        assertEquals(JsonPrimitive("New task"),request.values["title"]);assertEquals(JsonPrimitive("worker"),request.values["assignee"]);assertEquals(JsonPrimitive("high"),request.values["reasoning_effort"]);assertEquals(JsonArray(listOf(JsonPrimitive("review"))),request.values["skills"])
        assertFalse(request.values.containsKey("completion_contract"));assertFalse(request.values.containsKey("initial_status"));assertNotEquals(request.values["idempotency_key"],create().values["idempotency_key"])
    }
    @Test fun createAcknowledgementDoesNotConfirmIgnoredTriage() {
        val held=create(HostKanbanTaskDraft("Private new task",triage=true))
        val ready=card("t_new","Private new task",status="ready")
        val ignored=held.acknowledge(ack(ready))
        assertFalse("Ready is not a reported triage hold",ignored.effectReported)
        assertFalse(held.observed(ignored,detail(ready),null))
        val parked=card("t_new","Private new task",status="triage")
        val matching=held.acknowledge(ack(parked));assertTrue(matching.effectReported)
        assertTrue(held.observed(matching,detail(parked),null))
        assertEquals(JsonPrimitive(false),held.acknowledgedSummary(matching)["created_identity_verified"])
        assertFalse("A returned triage state also differs from the default non-triage request",create().acknowledge(ack(parked)).effectReported)
    }
    @Test fun minimalCreatePreservesOmittedFieldsAndNeverPromisesCreationIdentity() {
        val r=create();assertEquals(setOf("title","priority","parents","triage","idempotency_key","goal_mode"),r.values.keys)
        val a=r.acknowledge(ack(fields(card("t_new","Private new task"),"body" to JsonNull)));assertTrue(a.effectReported)
        assertEquals(JsonPrimitive(false),r.acknowledgedSummary(a)["created_identity_verified"])
    }
    @Test fun invalidTextAndSurrogatesAreRejectedBeforeAnyRequest() {
        for(title in listOf(" ","a\u0000", "a".repeat(4097),"\uD800")) assertThrows(HostKanbanTaskInvalid::class.java) {create(HostKanbanTaskDraft(title))}
        assertThrows(HostKanbanTaskInvalid::class.java) {create(HostKanbanTaskDraft("title",body="b".repeat(262145)))}
        assertThrows(HostKanbanTaskInvalid::class.java) {create(HostKanbanTaskDraft("title",tenant="bad\nline"))}
    }
    @Test fun workspaceAndPositiveCapsHaveExplicitBounds() {
        for(d in listOf(HostKanbanTaskDraft("title",workspaceKind="unknown"),HostKanbanTaskDraft("title",workspacePath=""),HostKanbanTaskDraft("title",maxRuntimeSeconds=0),HostKanbanTaskDraft("title",goalMaxTurns=-1))) assertThrows(HostKanbanTaskInvalid::class.java) {create(d)}
    }
    @Test fun providerRequiresModelAndReasoningAndSkillsAreTyped() {
        for(d in listOf(HostKanbanTaskDraft("title",provider="provider"),HostKanbanTaskDraft("title",reasoning="future"),HostKanbanTaskDraft("title",skills=listOf("one,two")),HostKanbanTaskDraft("title",skills=listOf(" ")))) assertThrows(HostKanbanTaskInvalid::class.java) {create(d)}
        assertEquals(JsonPrimitive("none"),create(HostKanbanTaskDraft("title",reasoning="none")).values["reasoning_effort"])
    }
    @Test fun parentsMustBeDistinctCanonicalAndPresentInReviewedBoard() {
        for(parents in listOf(listOf("missing"),listOf("t_parent","t_parent"),listOf("../parent"))) assertThrows(HostKanbanTaskInvalid::class.java) {create(HostKanbanTaskDraft("title",parents=parents))}
        val r=create(HostKanbanTaskDraft("title",parents=listOf("t_parent")));assertEquals("t_parent",r.parents.single().id)
    }
    @Test fun projectInheritanceRequiresExactCanonicalUnarchivedHomeInventory() {
        val i=inventory(fields(meta(),"project_id" to JsonPrimitive("p_bind")))
        assertThrows(HostKanbanTaskInvalid::class.java) {create(inv=i)}
        val projects=HostProjectProtocol.inventory(obj("""{"projects":[{"id":"p_bind","slug":"binding","name":"Private project","primary_path":"/srv/repo","created_at":1,"archived":false}],"active_id":null}"""),owner,home,1)
        val request=create(inv=i,projects=projects);assertEquals("p_bind",request.project!!.id);assertFalse(request.values.containsKey("project_id"))
        assertFalse(request.projectCurrent(projects.copy(homeId="c".repeat(64))))
        assertThrows(HostKanbanTaskInvalid::class.java) {create(HostKanbanTaskDraft("title",projectId="binding"),projects=projects)}
    }
    @Test fun explicitScratchOrEmptyProjectCanOptOutOfInheritedProject() {
        val i=inventory(fields(meta(),"project_id" to JsonPrimitive("p_bind")))
        assertNull(create(HostKanbanTaskDraft("title",workspaceKind="scratch"),i).project)
        assertNull(create(HostKanbanTaskDraft("title",projectId=""),i).project)
    }
    @Test fun metadataNeverCombinesPriorityStatusOrAssignmentPhases() {
        val r=patch(title=" New title ",body="");assertEquals(setOf("title","body"),r.values.keys);assertEquals(JsonPrimitive("New title"),r.values["title"]);assertEquals(JsonPrimitive(""),r.values["body"])
        assertThrows(HostKanbanTaskInvalid::class.java) {patch(title=null)};assertThrows(HostKanbanTaskInvalid::class.java) {patch(title=" ")}
    }
    @Test fun priorityRetainsFullSupportedSignedIntegerRange() {
        for(n in listOf(Int.MIN_VALUE,0,Int.MAX_VALUE)) {val r=HostKanbanTaskChange.priority(inventory(),board(),detail(),n);assertEquals(setOf("priority"),r.values.keys);assertTrue(r.acknowledge(ack(fields(card(),"priority" to JsonPrimitive(n)))).effectReported)}
    }
    @Test fun assignmentCanonicalizesAndUsesEmptyStringForUnassign() {
        val r=HostKanbanTaskChange.assignee(inventory(),board(),detail()," WORKER ");assertEquals(JsonPrimitive("worker"),r.values["assignee"])
        val clear=HostKanbanTaskChange.assignee(inventory(),board(),detail(),"");assertEquals(JsonPrimitive(""),clear.values["assignee"]);assertTrue(clear.acknowledge(ack()).effectReported)
        // Upstream normalizes labels; it does not impose a filesystem-name grammar here.
        assertEquals(JsonPrimitive("../worker"),HostKanbanTaskChange.assignee(inventory(),board(),detail(),"../worker").values["assignee"])
        assertThrows(HostKanbanTaskInvalid::class.java) {HostKanbanTaskChange.assignee(inventory(),board(),detail(),"bad\nworker")}
    }
    @Test fun commentAckNeverProvesSpecificCommentIdentity() {
        val r=HostKanbanTaskChange.comment(inventory(),board(),detail()," Private comment "," Author ");assertEquals(JsonPrimitive("Private comment"),r.values["body"]);assertEquals(JsonPrimitive("Author"),r.values["author"])
        val a=r.acknowledge(obj("""{"ok":true}"""));assertTrue(a.effectReported);assertFalse(r.observed(a,detail(),null));assertEquals(JsonPrimitive(false),r.acknowledgedSummary(a)["comment_identity_verified"])
    }
    @Test fun emptyCommentsAndAuthorsCannotCreateRequests() {
        for(pair in listOf(" " to "author","body" to " ")) assertThrows(HostKanbanTaskInvalid::class.java) {HostKanbanTaskChange.comment(inventory(),board(),detail(),pair.first,pair.second)}
    }
    @Test fun dependencyDirectionsAndCurrentPresenceAreExact() {
        val parent=detail(card("t_parent",status="done"));val child=detail();val r=HostKanbanTaskChange.dependency(inventory(),board(),child,parent,false)
        assertEquals(JsonPrimitive("t_parent"),r.values["parent_id"]);assertEquals(JsonPrimitive("t_child"),r.values["child_id"])
        assertTrue(r.acknowledge(obj("""{"ok":true,"gated":false}""" )).effectReported)
        assertThrows(HostKanbanTaskInvalid::class.java) {HostKanbanTaskChange.dependency(inventory(),board(),child,parent,true)}
        assertThrows(HostKanbanTaskInvalid::class.java) {HostKanbanTaskChange.dependency(inventory(),board(),child,child,false)}
        assertThrows(HostKanbanTaskInvalid::class.java) {HostKanbanTaskChange.dependency(inventory(),board(),detail(card(status="running")),parent,false)}
    }
    @Test fun absentUnlinkIsAcknowledgedWithoutAnEffectOrConfirmation() {
        val child=detail(parents=listOf("t_parent"));val r=HostKanbanTaskChange.dependency(inventory(),board(),child,detail(card("t_parent")),true)
        val a=r.acknowledge(obj("""{"ok":false}"""));assertFalse(a.effectReported);assertFalse(r.observed(a,detail(),null))
    }
    @Test fun malformedAcksAndForeignIdsAreNeverAccepted() {
        val r=patch();for(j in listOf(obj("{}"),ack(card("foreign")),ack(fields(card(),"priority" to JsonPrimitive("0"))))) assertThrows(HostKanbanUnsupported::class.java) {r.acknowledge(j)}
        val d=HostKanbanTaskChange.delete(inventory(),board(),detail());for(j in listOf(obj("""{"deleted":"true","task_id":"t_child"}"""),obj("""{"deleted":true,"task_id":"foreign"}"""))) assertThrows(HostKanbanUnsupported::class.java) {d.acknowledge(j)}
    }
    @Test fun ackWithDifferentRequestedValuesRemainsValidButUnconfirmed() {
        val r=patch();val a=r.acknowledge(ack());assertFalse(a.effectReported);assertFalse(r.observed(a,detail(),null))
    }
    @Test fun observationRequiresExactIdentityScopeMappingAndAcknowledgedTask() {
        val r=patch();val edited=fields(card(),"title" to JsonPrimitive("New title"));val a=r.acknowledge(ack(edited));val d=detail(edited)
        assertTrue(r.observed(a,d,null));assertFalse(r.observed(a,d.copy(owner=owner.copy(profile="other")),null));assertFalse(r.observed(a,detail(),null))
        val deleted=HostKanbanTaskChange.delete(inventory(),board(),detail());val da=deleted.acknowledge(obj("""{"deleted":true,"task_id":"t_child"}"""));assertFalse(deleted.observed(da,null,board()));assertTrue(deleted.observed(da,null,board(cards=listOf(card("t_parent")))))
    }
    @Test fun durableSummariesContainHashesAndFieldNamesWithoutPrivateValuesOrNonce() {
        val r=create(HostKanbanTaskDraft("Private title",body="Private body",assignee="worker",tenant="private-tenant",workspaceKind="dir",workspacePath="/srv/private-path",skills=listOf("private-skill")))
        val s=r.receiptTarget+r.summary().toString()+r.toString();for(v in listOf("Private title","Private body","worker","private-tenant","/srv/","private-skill",r.values.getValue("idempotency_key").jsonPrimitive.content)) assertFalse(v,s.contains(v))
        assertEquals(home,HostKanbanTaskChange.accessHome(r.summary().toString()))
    }
    @Test fun preflightRejectsBoardAnchorTaskHistoryAndForeignDetails() {
        val r=patch();val i=inventory();assertTrue(r.sameInventory(i));assertFalse(r.sameInventory(i.copy(boards=i.boards.map {if(it.slug=="default") it.copy(dbPath="/other.db") else it})))
        val d=detail();assertTrue(r.sameDetail(d,d.copy(observedAt=999)));assertFalse(r.sameDetail(d,d.copy(comments=listOf(HostKanbanComment(1,"a","b",1)))))
        assertThrows(HostKanbanTaskInvalid::class.java) {HostKanbanTaskChange.metadata(i,board(),d.copy(profileHomeId="c".repeat(64)),"title")}
    }
}
