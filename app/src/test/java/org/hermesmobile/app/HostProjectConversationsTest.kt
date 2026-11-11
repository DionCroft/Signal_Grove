package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostProjectConversationsTest {
    private val owner=ManagementTarget("b".repeat(64),"https://hermes.test/dashboard/","field","fixture · owner")
    private val home="c".repeat(64)
    private val project=HostProjectInfo("p_fixture","fixture","Fixture project",null,null,null,null,"/fixture/root",false,1,emptyList())
    private val session=buildJsonObject {
        put("id","chat-fixture");put("profile","field");put("title","Fixture conversation");put("source","cli");put("model","fixture-model")
        put("cwd","/fixture/root");put("message_count",2);put("last_active",17.0);put("archived",false)
        put("preview","PRIVATE_TRANSCRIPT_MUST_NOT_BE_PROJECTED")
    }
    private fun node(rows:List<JsonObject> = listOf(session),ids:List<String> = rows.map {it["id"]!!.jsonPrimitive.content})=buildJsonObject {
        put("id",project.id);put("label",project.name);put("path",project.primaryPath);put("isAuto",false);put("isNoProject",false)
        put("sessionCount",ids.size);put("sessionIds",JsonArray(ids.map(::JsonPrimitive)))
        put("repos",buildJsonArray {add(buildJsonObject {
            put("id","repo-fixture");put("path","/fixture/root");put("sessionCount",rows.size)
            put("groups",buildJsonArray {add(buildJsonObject {put("id","lane-fixture");put("label","Main");put("sessions",JsonArray(rows))})})
        })})
    }
    private fun parse(value:JsonElement=node())=HostProjectConversationProtocol.parse(buildJsonObject {put("project",value)},owner,home,project,100)
    private fun changed(value:JsonObject,key:String,next:JsonElement)=JsonObject(value+mapOf(key to next))
    private fun refused(value:JsonElement) {try {parse(value);fail("Unverified metadata accepted")} catch(e:HostProjectConversationsUnsupported) {assertEquals("Unsupported project conversation metadata.",e.message)}}
    @Test fun groupedNavigationKeepsScopeAndMetadataWithoutTranscriptOrRawJson() {
        val result=parse();assertEquals(owner,result.owner);assertEquals(home,result.homeId);assertEquals(100L,result.observedAt)
        assertEquals("chat-fixture",result.rows.single().id);assertEquals("Main",result.rows.single().lane);assertEquals("/fixture/root",result.rows.single().repository)
        assertEquals(2L,result.rows.single().messageCount);assertFalse(result.toString().contains("Fixture"));assertFalse(result.rows.single().toString().contains("Fixture"))
        assertEquals("HostProjectConversation(redacted)",result.rows.single().toString())
    }
    @Test fun nullProjectAndEmptyProjectAreDistinctWithoutDeletingStoredMetadata() {
        val missing=parse(JsonNull);assertFalse(missing.listed);assertEquals(project,missing.project);assertTrue(missing.rows.isEmpty())
        val empty=parse(node(emptyList()));assertTrue(empty.listed);assertEquals(0,empty.reportedCount)
    }
    @Test fun unplacedMatchingIdIsReportedWithoutInventingANavigableRow() {
        val result=parse(node(listOf(session),listOf("chat-fixture","unplaced-fixture")));assertEquals(2,result.reportedCount);assertEquals(1,result.rows.size)
    }
    @Test fun projectIdentityNameDirectoryAndKindsMustMatchReviewedStoredProject() {
        for((key,value) in listOf("id" to JsonPrimitive("other"),"label" to JsonPrimitive("Other"),"path" to JsonPrimitive("/other"),"isAuto" to JsonPrimitive(true),"isNoProject" to JsonPrimitive(true))) refused(changed(node(),key,value))
    }
    @Test fun wrongProfileArchivedSessionAndInvalidCountsAreRefused() {
        for((key,value) in listOf("profile" to JsonPrimitive("default"),"archived" to JsonPrimitive(true),"message_count" to JsonPrimitive(-1),"last_active" to JsonPrimitive(-1),"last_active" to JsonPrimitive("NaN"))) refused(node(listOf(changed(session,key,value))))
    }
    @Test fun missingOrMalformedProfileStampCannotInheritSocketLaunchProfile() {
        refused(node(listOf(JsonObject(session-"profile"))));refused(node(listOf(changed(session,"profile",JsonNull))))
    }
    @Test fun duplicateOrUnreportedSessionIdsCannotBecomeOpeningTargets() {
        refused(node(listOf(session,session)));refused(node(listOf(session),listOf("different")))
        refused(changed(node(),"sessionIds",JsonArray(listOf(JsonPrimitive("chat-fixture"),JsonPrimitive("chat-fixture")))))
    }
    @Test fun reportedCountsAndBoundedWindowAreEnforced() {
        refused(changed(node(),"sessionCount",JsonPrimitive(0)));refused(changed(node(),"sessionCount",JsonPrimitive(501)))
        refused(node((0..500).map {changed(session,"id",JsonPrimitive("chat-$it"))}))
    }
    @Test fun unsafeIdentifiersAndUnboundedLabelsAreNotExposed() {
        refused(node(listOf(changed(session,"id",JsonPrimitive("bad\nidentifier")))))
        refused(node(listOf(changed(session,"title",JsonPrimitive("x".repeat(513))))))
        refused(node(listOf(changed(session,"cwd",JsonPrimitive("/fixture/\u0000bad")))))
    }
    @Test fun nonObjectOrMissingResultCannotBeMistakenForEmptyProject() {
        refused(JsonArray(emptyList()));try {HostProjectConversationProtocol.parse(JsonObject(emptyMap()),owner,home,project);fail()} catch(_:HostProjectConversationsUnsupported) {}
    }
}
