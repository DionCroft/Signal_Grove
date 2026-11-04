package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostProjectsTest {
    private val owner=ManagementTarget("a".repeat(64),"https://dashboard.example/prefix/","worker","provider · actor")
    private val home="b".repeat(64)
    private fun row(id:String="p_00000001",slug:String="garden",archived:Boolean=false)=buildJsonObject {
        put("id",id);put("slug",slug);put("name","Private garden");put("created_at",1793820000L);put("archived",archived)
        put("description","Private notes\n第二行");put("primary_path",JsonNull)
        put("folders",buildJsonArray {add(buildJsonObject {put("path","/srv/private/garden");put("is_primary",true);put("added_at",1793820001L)})})
    }
    private fun listing(rows:List<JsonObject> = listOf(row()),active:JsonElement=JsonNull)=buildJsonObject {put("projects",JsonArray(rows));put("active_id",active)}
    private fun parse(j:JsonObject)=HostProjectProtocol.inventory(j,owner,home,123L)
    @Test fun storedMetadataKeepsSecondsAndLegacyFolderFlagsWithoutInventingPrimaryPath() {
        val s=parse(listing(active=JsonPrimitive("p_00000001")));val p=s.projects.single()
        assertEquals(owner,s.owner);assertEquals(home,s.homeId);assertEquals(123L,s.observedAt)
        assertEquals(1793820000L,p.createdAtSeconds);assertEquals(1793820001L,p.folders.single().addedAtSeconds)
        assertNull(p.primaryPath);assertTrue(p.folders.single().primary);assertSame(p,s.activeProject)
        assertEquals("Private notes\n第二行",p.description)
    }
    @Test fun deletedAndArchivedActivePointersRemainObservedWithoutSelectingAnotherProject() {
        val missing=parse(listing(active=JsonPrimitive("p_deleted")))
        assertEquals("p_deleted",missing.activeId);assertNull(missing.activeProject);assertEquals(1,missing.projects.size)
        val archived=parse(listing(listOf(row(archived=true)),JsonPrimitive("p_00000001")))
        assertTrue(archived.activeProject!!.archived)
    }
    @Test fun exactCanonicalIdIsRequiredWhenServerLookupCouldResolveASlug() {
        val answer=buildJsonObject {put("project",row("p_replacement","p_00000001"))}
        assertThrows(HostProjectsUnsupported::class.java) {HostProjectProtocol.detail(answer,owner,home,"p_00000001")}
        assertEquals("p_replacement",HostProjectProtocol.detail(answer,owner,home,"p_replacement").project.id)
    }
    @Test fun wrongWireTypesAndOverflowCannotBecomeProjectMetadata() {
        for((key,value) in listOf("id" to JsonPrimitive(42),"archived" to JsonPrimitive("true"),"created_at" to JsonPrimitive("1793820000"),"created_at" to JsonPrimitive(Long.MAX_VALUE),"folders" to JsonNull,"slug" to JsonPrimitive("../escape"),"name" to JsonPrimitive("\u0000private"))) {
            val changed=JsonObject(row()+mapOf(key to value));assertThrows(HostProjectsUnsupported::class.java) {parse(listing(listOf(changed)))}
        }
        assertThrows(HostProjectsUnsupported::class.java) {parse(buildJsonObject {put("projects",JsonNull)})}
    }
    @Test fun duplicateIdentitiesFoldersAndOversizeRepliesAreRefusedAsAWhole() {
        assertThrows(HostProjectsUnsupported::class.java) {parse(listing(listOf(row(),row())))}
        assertThrows(HostProjectsUnsupported::class.java) {parse(listing(listOf(row(),row("p_00000002"))))}
        val f=row()["folders"]!!.jsonArray.single()
        assertThrows(HostProjectsUnsupported::class.java) {parse(listing(listOf(JsonObject(row()+mapOf("folders" to JsonArray(listOf(f,f)))))))}
        assertThrows(HostProjectsUnsupported::class.java) {parse(listing((0..500).map {row("id$it","slug$it")}))}
        assertThrows(HostProjectsUnsupported::class.java) {parse(listing(listOf(JsonObject(row()+mapOf("description" to JsonPrimitive("x".repeat(8193)))))))}
    }
    @Test fun reviewListsAreImmutableAndStringRenderingDoesNotRevealPrivateValues() {
        val s=parse(listing())
        assertThrows(UnsupportedOperationException::class.java) {(s.projects as MutableList<HostProjectInfo>).clear()}
        assertThrows(UnsupportedOperationException::class.java) {(s.projects.single().folders as MutableList<HostProjectFolder>).clear()}
        for(value in listOf(s,s.projects.single(),s.projects.single().folders.single())) {
            assertFalse(value.toString().contains("Private"));assertFalse(value.toString().contains("/srv/"))
        }
    }
    @Test fun optionalFieldsAndOpaqueFutureIdsWorkWithoutFakeCronScopeFields() {
        val minimal=buildJsonObject {put("id","future:project");put("slug","new");put("name","New");put("created_at",0);put("unexpected_secret","discard-me")}
        val p=parse(listing(listOf(minimal))).projects.single()
        assertEquals("future:project",p.id);assertFalse(p.archived);assertTrue(p.folders.isEmpty());assertNull(p.description)
    }
}
