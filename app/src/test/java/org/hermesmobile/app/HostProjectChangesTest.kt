package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostProjectChangesTest {
    private val owner=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · fixture")
    private val home="b".repeat(64)
    private fun folder(path:String="/srv/private/garden",primary:Boolean=true,label:String?=null)=buildJsonObject {put("path",path);put("is_primary",primary);put("added_at",100);label?.let {put("label",it)}}
    private fun row(id:String="p_00000001",slug:String="garden",archived:Boolean=false,folders:List<JsonObject> = listOf(folder()),primary:String?="/srv/private/garden")=buildJsonObject {
        put("id",id);put("slug",slug);put("name","Private garden");put("created_at",100);put("archived",archived)
        put("description","Private notes");put("icon","sprout");put("color","forest");put("board_slug","board")
        put("primary_path",primary?.let(::JsonPrimitive) ?: JsonNull);put("folders",JsonArray(folders))
    }
    private fun listing(rows:List<JsonObject> = listOf(row()),active:String?="p_00000001")=buildJsonObject {put("projects",JsonArray(rows));put("active_id",active?.let(::JsonPrimitive) ?: JsonNull)}
    private fun snapshot(rows:List<JsonObject> = listOf(row()),active:String?="p_00000001")=HostProjectProtocol.inventory(listing(rows,active),owner,home,123L)
    private fun result(row:JsonObject)=buildJsonObject {put("project",row)}
    private fun withFields(row:JsonObject,vararg fields:Pair<String,JsonElement>)=JsonObject(row+mapOf(*fields))

    @Test fun updateKeepsOmittedFieldsAndDistinguishesEmptyDescriptionFromClearedMetadata() {
        val request=HostProjectChange.update(snapshot(),"p_00000001",mapOf("name" to " New name ","description" to "","icon" to "","board_slug" to ""))
        val params=request.params("worker")
        assertEquals(setOf("profile","id","name","description","icon","board_slug"),params.keys)
        assertEquals(JsonPrimitive("New name"),params["name"]);assertEquals(JsonPrimitive(""),params["description"]);assertFalse(params.containsKey("color"))
        val saved=withFields(row(),"name" to JsonPrimitive("New name"),"description" to JsonPrimitive(""),"icon" to JsonNull,"board_slug" to JsonNull)
        val ack=request.acknowledge(result(saved));assertTrue(ack.effectReported);assertTrue(request.observed(ack,snapshot(listOf(saved))))
        assertFalse(request.observed(ack,snapshot()))
    }
    @Test fun createDoesNotActivateAndLeavesHostPathNormalizationToTheHost() {
        val request=HostProjectChange.create(snapshot(),HostProjectCreateDraft(" New project ","NEW_PROJECT",folders=listOf("../private/repo","../private/repo"),primaryPath="/srv/second"))
        val params=request.params("worker")
        assertEquals(JsonPrimitive(false),params["use"]);assertEquals(JsonPrimitive("New project"),params["name"]);assertEquals(JsonPrimitive("new_project"),params["slug"])
        assertEquals(JsonArray(listOf(JsonPrimitive("../private/repo"))),params["folders"]);assertEquals(JsonPrimitive("/srv/second"),params["primary_path"])
        assertFalse(params.containsKey("cwd"));assertFalse(params.containsKey("session_id"))
    }
    @Test fun normalizedCreateAcknowledgementRecordsReportedConfigurationWithoutInventingAnExactPathMapping() {
        val request=HostProjectChange.create(snapshot(),HostProjectCreateDraft("Private garden",folders=listOf("../private/repo")))
        val created=row("p_00000002","garden-2",folders=listOf(folder("/srv/private/repo")),primary="/srv/private/repo")
        val ack=request.acknowledge(result(created));assertTrue(ack.effectReported);assertTrue(ack.pathMappingUnverified)
        assertTrue(request.observed(ack,snapshot(listOf(row(),created))))
        assertFalse(request.observed(ack,snapshot()))
    }
    @Test fun explicitNullCreateResultRemainsAcknowledgedWithoutClaimingAProjectOrConfiguration() {
        val request=HostProjectChange.create(snapshot(),HostProjectCreateDraft("New"))
        val ack=request.acknowledge(buildJsonObject {put("project",JsonNull)})
        assertNull(ack.project);assertFalse(ack.effectReported);assertFalse(request.observed(ack,snapshot()))
    }
    @Test fun privateReviewRenderingAndDurableSummaryOmitInputValues() {
        val request=HostProjectChange.create(snapshot(),HostProjectCreateDraft("Secret name",description="Secret description",folders=listOf("/private/secret-folder")))
        val summary=request.summary().toString()
        for(secret in listOf("Secret name","Secret description","/private/secret-folder","Private garden")) {
            assertFalse(summary.contains(secret));assertFalse(request.toString().contains(secret));assertFalse(request.expected.toString().contains(secret))
        }
        assertEquals(JsonPrimitive(1),request.summary()["folder_count"])
        val selected=HostProjectChange.action(snapshot(),"p_00000001",HostProjectAction.Delete)
        assertFalse(selected.summary().toString().contains("p_00000001"));assertEquals(64,selected.receiptTarget.length)
    }
    @Test fun invalidOrOversizeInputsCannotBecomeAReview() {
        val s=snapshot()
        val bad=listOf<()->Unit>(
            {HostProjectChange.create(s,HostProjectCreateDraft(" "))},
            {HostProjectChange.create(s,HostProjectCreateDraft("New",slug="../bad"))},
            {HostProjectChange.addFolder(s,"p_00000001","  ")},
            {HostProjectChange.create(s,HostProjectCreateDraft("New",primaryPath=""))},
            {HostProjectChange.update(s,"p_00000001",mapOf("description" to "x".repeat(8193)))},
            {HostProjectChange.update(s,"p_00000001",mapOf("path" to "/private"))},
            {HostProjectChange.update(s,"p_00000001",emptyMap())},
            {HostProjectChange.update(s,"p_00000001",mapOf("name" to "\u0000bad"))},
            {HostProjectChange.create(s,HostProjectCreateDraft("New",folders=(0..128).map {"/srv/$it"}))}
        )
        for(call in bad) assertThrows(HostProjectChangeInvalid::class.java) {call()}
        assertThrows(IllegalArgumentException::class.java) {HostProjectChange.action(s,"p_00000001",HostProjectAction.Delete).params("other")}
    }
    @Test fun changedHomeOwnerPointerOrAnyStoredRowInvalidatesTheFrozenPreflight() {
        val s=snapshot();val r=HostProjectChange.action(s,"p_00000001",HostProjectAction.Delete)
        assertTrue(r.same(s.copy(observedAt=456L)))
        for(changed in listOf(s.copy(homeId="c".repeat(64)),s.copy(owner=owner.copy(actor="password · other")),s.copy(activeId=null),snapshot(listOf(row(),row("p_00000002","other"))))) assertFalse(r.same(changed))
    }
    @Test fun knownIdSlugCollisionAndMissingCanonicalTargetsAreRefusedBeforeAReview() {
        val collision=snapshot(listOf(row(),row("p_00000002","p_00000001")))
        assertTrue(HostProjectChange.collision(collision,"p_00000001"))
        assertThrows(HostProjectChangeInvalid::class.java) {HostProjectChange.action(collision,"p_00000001",HostProjectAction.Delete)}
        assertThrows(HostProjectChangeInvalid::class.java) {HostProjectChange.update(snapshot(),"garden",mapOf("name" to "New"))}
    }
    @Test fun aSuccessfulNoOpSetPrimaryReplyCannotBePresentedAsTheRequestedChange() {
        val alternate=folder("/srv/alternate",false)
        val s=snapshot(listOf(row(folders=listOf(folder(),alternate))))
        val r=HostProjectChange.folder(s,"p_00000001","/srv/alternate",true)
        assertEquals("projects.set_primary",r.action.rpc)
        val ack=r.acknowledge(result(row(folders=listOf(folder(),alternate))))
        assertFalse(ack.effectReported);assertFalse(r.observed(ack,s))
        val changed=row(folders=listOf(folder(primary=false),folder("/srv/alternate",true)),primary="/srv/alternate")
        val saved=r.acknowledge(result(changed));assertTrue(saved.effectReported);assertTrue(r.observed(saved,snapshot(listOf(changed))))
    }
    @Test fun addFolderSupportsFirstPrimaryAndKeepsNormalizedPathUncertaintyVisible() {
        val s=snapshot(listOf(row(folders=emptyList(),primary=null)))
        val r=HostProjectChange.addFolder(s,"p_00000001","relative/repo","Reviewed label",false)
        val changed=row(folders=listOf(folder("/srv/relative/repo",true,"Reviewed label")),primary="/srv/relative/repo")
        val ack=r.acknowledge(result(changed));assertTrue(ack.effectReported);assertTrue(ack.pathMappingUnverified)
        assertTrue(r.observed(ack,snapshot(listOf(changed))))
        assertEquals(JsonPrimitive(false),r.params("worker")["is_primary"])
    }
    @Test fun removeFolderObservesMetadataRemovalAndPrimaryReassignment() {
        val s=snapshot(listOf(row(folders=listOf(folder(),folder("/srv/next",false)))))
        val r=HostProjectChange.folder(s,"p_00000001","/srv/private/garden",false)
        val changed=row(folders=listOf(folder("/srv/next",true)),primary="/srv/next")
        val ack=r.acknowledge(result(changed));assertTrue(ack.effectReported);assertTrue(r.observed(ack,snapshot(listOf(changed))))
        assertFalse(r.acknowledge(result(row())).effectReported)
    }
    @Test fun archiveRestoreAndDeleteKeepDanglingActivePointersDistinct() {
        val s=snapshot();val archived=row(archived=true)
        val archive=HostProjectChange.action(s,"p_00000001",HostProjectAction.Archive)
        val ack=archive.acknowledge(listing(listOf(archived)))
        assertTrue(archive.observed(ack,snapshot(listOf(archived))));assertEquals("p_00000001",ack.inventory!!.activeId)
        val restore=HostProjectChange.action(snapshot(listOf(archived)),"p_00000001",HostProjectAction.Restore)
        assertEquals(JsonPrimitive(true),restore.params("worker")["restore"]);assertTrue(restore.acknowledge(listing()).effectReported)
        val delete=HostProjectChange.action(s,"p_00000001",HostProjectAction.Delete)
        val deleted=delete.acknowledge(listing(emptyList()))
        assertTrue(delete.observed(deleted,snapshot(emptyList())));assertEquals("p_00000001",deleted.inventory!!.activeId)
        assertNull(deleted.inventory!!.activeProject)
    }
    @Test fun activePointerChangesRequireExactAcknowledgementAndExistingTargetOnReadback() {
        val activate=HostProjectChange.action(snapshot(active=null),"p_00000001",HostProjectAction.SetActive)
        val ack=activate.acknowledge(buildJsonObject {put("active_id","p_00000001")})
        assertTrue(activate.observed(ack,snapshot()));assertFalse(activate.observed(ack,snapshot(emptyList())))
        assertFalse(activate.acknowledge(buildJsonObject {put("active_id","other")}).effectReported)
        val clear=HostProjectChange.action(snapshot(),null,HostProjectAction.ClearActive)
        assertTrue(clear.params("worker")["id"] is JsonNull)
        val cleared=clear.acknowledge(buildJsonObject {put("active_id",JsonNull)})
        assertTrue(clear.observed(cleared,snapshot(active=null)));assertFalse(clear.observed(cleared,snapshot()))
    }
    @Test fun anotherCanonicalProjectCannotBecomeAnUpdateOrCreationAcknowledgement() {
        val update=HostProjectChange.update(snapshot(),"p_00000001",mapOf("name" to "New"))
        assertThrows(HostProjectsUnsupported::class.java) {update.acknowledge(result(row("p_other","p_00000001")))}
        val create=HostProjectChange.create(snapshot(),HostProjectCreateDraft("New"))
        assertThrows(IllegalArgumentException::class.java) {create.acknowledge(result(row()))}
    }
}