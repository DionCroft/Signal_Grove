package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostKanbanBoardChangesTest {
    private val owner=ManagementTarget("a".repeat(64),"https://dashboard.example/prefix/","worker","password · actor")
    private val home="b".repeat(64)
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private fun meta(slug:String="garden",archived:Boolean=false)=obj("""{"slug":"$slug","name":"Private name","description":"Private description","icon":"leaf","color":"green","db_path":"${if(slug=="default") "/shared/kanban.db" else "/shared/kanban/boards/$slug/kanban.db"}","default_workdir":"/srv/private/repository","project_id":null,"archived":$archived,"created_at":1}""")
    private fun listing(rows:List<JsonObject> = listOf(meta("default"),meta(),meta("archive",true)),current:String="garden")=buildJsonObject {put("boards",JsonArray(rows.map {JsonObject(it+mapOf("counts" to obj("""{"running":1}"""),"total" to JsonPrimitive(1)))}));put("current",current)}
    private fun inventory(j:JsonObject=listing())=HostKanbanProtocol.inventory(j,owner,home,1)
    private fun projects(id:String="p_bind",slug:String="binding",path:String="/srv/project-primary",archived:Boolean=false)=HostProjectProtocol.inventory(obj("""{"projects":[{"id":"$id","slug":"$slug","name":"Private project","primary_path":"$path","created_at":1,"archived":$archived}],"active_id":null}"""),owner,home,1)
    private fun create(fields:Map<String,String> = emptyMap(),slug:String="new-board",projects:HostProjectSnapshot?=null)=HostKanbanBoardChange.create(inventory(),HostKanbanBoardDraft(slug,fields),projects)
    private fun update(fields:Map<String,String>,projects:HostProjectSnapshot?=null)=HostKanbanBoardChange.update(inventory(),"garden",fields,projects)
    private fun ackBoard(meta:JsonObject,current:String="garden")=buildJsonObject {put("board",meta);put("current",current)}
    @Test fun createFreezesCanonicalSlugExplicitEmptyFieldsAndSwitchOff() {
        val r=create(mapOf("name" to "Private new board")," NEW-BOARD ")
        assertEquals("new-board",r.slug);assertEquals(JsonPrimitive(false),r.values["switch"])
        assertEquals(setOf("slug","switch","name","description","icon","color","default_workdir","project_id"),r.values.keys)
        assertEquals(JsonPrimitive(""),r.values["description"]);assertEquals("POST",r.action.method)
        assertEquals(home,HostKanbanBoardChange.accessHome(r.summary().toString()))
    }
    @Test fun everyKnownSlugIncludingDefaultAndArchivedEntryRejectsCreation() {
        for(slug in listOf("default","garden","archive")) assertThrows(HostKanbanBoardInvalid::class.java) {create(slug=slug)}
        val missing=inventory(listing(listOf(meta())))
        assertThrows(HostKanbanBoardInvalid::class.java) {HostKanbanBoardChange.create(missing,HostKanbanBoardDraft("new",emptyMap()))}
    }
    @Test fun editDistinguishesOmissionFromSavedEmptyValuesAndClearReferences() {
        val r=update(mapOf("description" to "","icon" to "","color" to "","project_id" to "","default_workdir" to ""))
        assertFalse(r.values.containsKey("name"));assertFalse(r.values.containsKey("slug"));assertFalse(r.values.containsKey("switch"))
        val changed=JsonObject(meta()+mapOf("description" to JsonPrimitive(""),"icon" to JsonPrimitive(""),"color" to JsonPrimitive(""),"project_id" to JsonNull,"default_workdir" to JsonNull))
        assertTrue(r.acknowledge(ackBoard(changed)).effectReported)
    }
    @Test fun emptyNameUsesTheHostDerivedNameRatherThanAnEmptyName() {
        val r=update(mapOf("name" to "  "));val changed=JsonObject(meta()+mapOf("name" to JsonPrimitive("Garden")))
        assertTrue(r.acknowledge(ackBoard(changed)).effectReported)
        assertFalse(r.acknowledge(ackBoard(JsonObject(meta()+mapOf("name" to JsonPrimitive(""))))).effectReported)
    }
    @Test fun canonicalBindingSeedsCreateBlankAndEditOmittedDirectory() {
        val p=projects();val created=create(mapOf("project_id" to "p_bind"),projects=p)
        val newMeta=JsonObject(meta("new-board")+mapOf("name" to JsonPrimitive("New Board"),"description" to JsonPrimitive(""),"icon" to JsonPrimitive(""),"color" to JsonPrimitive(""),"project_id" to JsonPrimitive("p_bind"),"default_workdir" to JsonPrimitive("/srv/project-primary")))
        assertTrue(created.acknowledge(ackBoard(newMeta)).effectReported)
        val edit=update(mapOf("project_id" to "p_bind"),p)
        assertTrue(edit.acknowledge(ackBoard(JsonObject(meta()+mapOf("project_id" to JsonPrimitive("p_bind"),"default_workdir" to JsonPrimitive("/srv/project-primary"))))).effectReported)
        assertFalse(edit.projectCurrent(projects(path="/srv/changed-primary")))
    }
    @Test fun explicitEmptyDirectoryPreventsEditSeedingAndClearBindingKeepsDirectory() {
        val r=update(mapOf("project_id" to "p_bind","default_workdir" to ""),projects())
        assertTrue(r.acknowledge(ackBoard(JsonObject(meta()+mapOf("project_id" to JsonPrimitive("p_bind"),"default_workdir" to JsonNull)))).effectReported)
        val clear=update(mapOf("project_id" to ""))
        assertTrue(clear.acknowledge(ackBoard(meta())).effectReported)
    }
    @Test fun bindingRequiresCanonicalLiveProjectMatchingAccessAndRejectsKnownIdSlugCollisions() {
        assertThrows(HostKanbanBoardInvalid::class.java) {create(mapOf("project_id" to "p_bind"))}
        assertThrows(HostKanbanBoardInvalid::class.java) {create(mapOf("project_id" to "binding"),projects=projects())}
        assertThrows(HostKanbanBoardInvalid::class.java) {create(mapOf("project_id" to "p_bind"),projects=projects(archived=true))}
        val p=projects();val other=p.projects.single().copy(id="p_other",slug="p_bind")
        assertThrows(HostKanbanBoardInvalid::class.java) {create(mapOf("project_id" to "p_bind"),projects=p.copy(projects=p.projects+other))}
        assertThrows(HostKanbanBoardInvalid::class.java) {create(mapOf("project_id" to "p_bind"),projects=p.copy(homeId="c".repeat(64)))}
    }
    @Test fun workerCountsCanAdvanceButMetadataCurrentPointerAndAnchorMustStayReviewed() {
        val r=update(mapOf("description" to "New"));val base=inventory()
        assertTrue(r.same(base.copy(observedAt=99,boards=base.boards.map {it.copy(total=900,counts=mapOf("running" to 900L))})))
        assertFalse(r.same(base.copy(current="default")))
        assertFalse(r.same(base.copy(boards=base.boards.map {if(it.slug=="garden") it.copy(description="Other") else it})))
        assertFalse(r.rootSame(base.copy(boards=base.boards.map {if(it.slug=="default") it.copy(dbPath="/other/kanban.db") else it})))
    }
    @Test fun metadataAcknowledgementNeedsNoInventedCountsButExactSlugAndRequiredShape() {
        val r=update(mapOf("description" to "Private description"));assertTrue(r.acknowledge(ackBoard(meta())).effectReported)
        assertThrows(HostKanbanUnsupported::class.java) {r.acknowledge(ackBoard(meta("other")))}
        assertThrows(HostKanbanUnsupported::class.java) {r.acknowledge(ackBoard(JsonObject(meta()-"archived")))}
        assertThrows(HostKanbanUnsupported::class.java) {r.acknowledge(ackBoard(JsonObject(meta()+mapOf("archived" to JsonPrimitive("false")))))}
    }
    @Test fun archiveRetainsOnlyStructuralReceiptAndSeparatelyObservesTombstoneAndCurrent() {
        val r=HostKanbanBoardChange.action(inventory(),"garden",HostKanbanBoardAction.Archive)
        val ack=r.acknowledge(obj("""{"result":{"slug":"garden","action":"archived","new_path":"/shared/private-archive/garden-123"},"current":"default"}"""))
        assertEquals("/shared/private-archive/garden-123",ack.archivePath)
        assertTrue(r.observed(ack,inventory(listing(listOf(meta("default"),meta(archived=true),meta("archive",true)),"default"))))
        val summary=r.acknowledgedSummary(ack).toString();assertFalse(summary.contains("/shared/"));assertFalse(summary.contains("Private"));assertFalse(summary.contains("garden"))
        assertEquals(JsonPrimitive(false),r.acknowledgedSummary(ack)["file_erasure_verified"])
    }
    @Test fun deleteLiveOrArchivedEntryConfirmsCatalogAbsenceWithoutClaimingErasure() {
        for(slug in listOf("garden","archive")) {
            val r=HostKanbanBoardChange.action(inventory(),slug,HostKanbanBoardAction.Delete)
            val ack=r.acknowledge(obj("""{"result":{"slug":"$slug","action":"deleted","new_path":""},"current":"default"}"""))
            assertTrue(r.observed(ack,inventory(listing(listOf(meta("default")),"default"))))
            assertEquals(JsonPrimitive(false),r.acknowledgedSummary(ack)["file_erasure_verified"])
            assertEquals(JsonPrimitive(false),r.acknowledgedSummary(ack)["runtime_stop_verified"])
        }
    }
    @Test fun switchAllowsAlreadyReportedCurrentButDoesNotInferEnvironmentAdoption() {
        val r=HostKanbanBoardChange.action(inventory(),"garden",HostKanbanBoardAction.Switch);assertTrue(r.values.isEmpty())
        val ack=r.acknowledge(obj("""{"current":"garden"}"""));assertTrue(r.observed(ack,inventory()))
        assertFalse(r.observed(ack,inventory(listing(current="default"))))
        assertThrows(HostKanbanUnsupported::class.java) {r.acknowledge(obj("""{"current":"other"}"""))}
    }
    @Test fun validReplyWithDifferentRequestedValueIsAcknowledgedButNotConfirmed() {
        val r=update(mapOf("description" to "Wanted"));val ack=r.acknowledge(ackBoard(meta()))
        assertFalse(ack.effectReported);assertFalse(r.observed(ack,inventory()))
        assertEquals(JsonPrimitive(true),r.acknowledgedSummary(ack)["host_acknowledged"])
    }
    @Test fun normalizedHostPathRemainsExplicitlyUnverifiedAndCannotBeReportedAsCleared() {
        val r=update(mapOf("default_workdir" to "~/private"));val ack=r.acknowledge(ackBoard(JsonObject(meta()+mapOf("default_workdir" to JsonPrimitive("/resolved/private")))))
        assertTrue(ack.effectReported);assertEquals(JsonPrimitive(false),r.acknowledgedSummary(ack)["path_mapping_verified"])
        assertFalse(r.acknowledge(ackBoard(JsonObject(meta()+mapOf("default_workdir" to JsonNull)))).effectReported)
    }
    @Test fun creationReadbackNeverClaimsNewBoardEvenWhenRequestedConfigurationMatches() {
        val r=create();val m=JsonObject(meta("new-board")+mapOf("name" to JsonPrimitive("New Board"),"description" to JsonPrimitive(""),"icon" to JsonPrimitive(""),"color" to JsonPrimitive(""),"default_workdir" to JsonNull))
        val ack=r.acknowledge(ackBoard(m));val after=inventory(listing(listOf(meta("default"),meta(),meta("archive",true),m)))
        assertTrue(r.observed(ack,after));assertEquals(JsonPrimitive(false),r.acknowledgedSummary(ack)["new_board_verified"])
    }
    @Test fun defaultRemovalArchivedSwitchAndDuplicateArchiveAreProtected() {
        for(action in listOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete)) assertThrows(HostKanbanBoardInvalid::class.java) {HostKanbanBoardChange.action(inventory(),"default",action)}
        for(action in listOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Switch)) assertThrows(HostKanbanBoardInvalid::class.java) {HostKanbanBoardChange.action(inventory(),"archive",action)}
        assertNotNull(HostKanbanBoardChange.update(inventory(),"archive",mapOf("description" to "Tombstone notes")))
    }
    @Test fun privateDraftMutationCannotChangeFrozenValuesOrAppearInRendering() {
        val fields=mutableMapOf("description" to "Private draft");val r=update(fields);fields["description"]="Changed later"
        assertEquals(JsonPrimitive("Private draft"),r.values["description"])
        assertTrue(runCatching {(r.values as MutableMap<String,JsonElement>)["description"]=JsonPrimitive("Other")}.isFailure)
        val ack=r.acknowledge(ackBoard(JsonObject(meta()+mapOf("description" to JsonPrimitive("Private draft")))))
        for(value in listOf(r,ack,HostKanbanBoardDraft("private",fields),ack.board!!)) assertFalse(value.toString().contains("Private"))
    }
    @Test fun invalidFieldsBoundsSelectorsAndMalformedReceiptHomeFailBeforeAWrite() {
        for(fields in listOf(mapOf("unknown" to "x"),mapOf("description" to "x".repeat(16385)),mapOf("default_workdir" to "/srv/\u0000private"))) assertThrows(HostKanbanBoardInvalid::class.java) {update(fields)}
        for(slug in listOf("../escape","x/y","","x".repeat(65))) assertThrows(HostKanbanBoardInvalid::class.java) {create(slug=slug)}
        assertThrows(HostKanbanBoardInvalid::class.java) {update(emptyMap())}
        for(summary in listOf("{}","""{"schema":"1","access_home_sha256":"${"b".repeat(64)}"}""","""{"schema":1,"access_home_sha256":"short"}""")) assertTrue(runCatching {HostKanbanBoardChange.accessHome(summary)}.isFailure)
    }
}
