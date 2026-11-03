package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class PluginUpdateTest {
    private val pin="b".repeat(40)
    private val owner=ManagementTarget("a".repeat(64),"https://dashboard.example/","worker","password · fixture")
    private fun row(catalog:Boolean=false,pinned:Boolean=false,install:String="install-name",name:String="Manifest",key:String="manifest-key",sha:String="a".repeat(40),catalogPin:String=pin,available:Boolean=true):ManagedPlugin {
        val j=buildJsonObject {put("plugins",buildJsonArray {add(buildJsonObject {put("name",name);put("key",key);put("version","1");put("source","git");put("status","enabled");put("install_dir","/private-update-home/plugins/$install");put("servers",JsonArray(emptyList()));if(catalog) {put("catalog_name","fixture-plugin");put("catalog_sha",catalogPin);put("installed_sha",sha);put("update_available",available)};if(pinned) put("pinned_sha","c".repeat(40))})});put("user_count",1);put("bundled_count",0)}
        return GatewayPluginInventory.parse(j,"worker",PluginHome.project(JsonPrimitive("/private-update-home"))).plugins.single()
    }
    private fun cap()=buildJsonObject {put("ok",false);put("name","fixture-plugin");put("consent_required",true);put("sha",pin);put("delta",buildJsonObject {put("tools",buildJsonArray {add("fixture_tool")});put("python_dependencies",buildJsonArray {add("private-package @ https://user:pass@example/?secret=private-update-reference")})})}
    private fun scan(verdict:String="caution")=buildJsonObject {put("ok",false);put("name","install-name");put("scan_blocked",true);put("scan_verdict",verdict);put("scan_findings",buildJsonArray {add(buildJsonObject {put("pattern_id","fixture-rule");put("severity","high");put("category","execution");put("file","plugin.py");put("line",3);put("description","Fixture scanner finding")})})}
    private fun decision(row:ManagedPlugin,raw:JsonObject,catalog:PluginCatalogEntry?=null)=PluginUpdateDecision.parse(raw,PluginUpdateRequest(row).acknowledge(raw),owner,row,"receipt-fixture",catalog)
    @Test fun firstUpdateHasOnlyExplicitFalseGrantFlagsAndExactDirectoryProfile() {
        val q=PluginUpdateRequest(row());assertEquals(buildJsonObject {put("profile","worker");put("action","update");put("name","install-name");put("accept_capabilities",false);put("force",false)},q.params("worker"));assertFalse(q.params("worker").containsKey("inspected_sha"));assertTrue(runCatching {q.params("all")}.isFailure);assertFalse(q.summary().toString().contains("private-update-home"))
    }
    @Test fun eligibleReviewedDecisionSetsOnlyItsOwnFlagAndIsImmutable() {
        val catRow=row(true);val entry=PluginCatalogEntry("fixture-plugin","https://github.com/example/plugin",pin,"plugin","community");val d=decision(catRow,cap(),entry);val q=PluginUpdateRequest(catRow,d);assertEquals(JsonPrimitive(true),q.params("worker")["accept_capabilities"]);assertEquals(JsonPrimitive(false),q.params("worker")["force"])
        val git=row();val caution=decision(git,scan());val forced=PluginUpdateRequest(git,caution);assertEquals(JsonPrimitive(false),forced.params("worker")["accept_capabilities"]);assertEquals(JsonPrimitive(true),forced.params("worker")["force"])
        assertTrue(runCatching {(d.delta as MutableMap)["tools"]=emptyList()}.isFailure);assertTrue(runCatching {(d.delta.getValue("tools") as MutableList).add("extra")}.isFailure);assertFalse(d.summary().toString().contains("private-update-reference"));assertEquals("PluginUpdateDecision(redacted)",d.toString())
    }
    @Test fun dangerousCatalogCautionExpiredAndUnprovenPinsCannotBeGranted() {
        val git=row();assertFalse(decision(git,scan("dangerous")).eligible());val cat=row(true);assertFalse(decision(cat,scan()).eligible());assertFalse(PluginUpdateRequest.supported(row(pinned=true)));assertFalse(PluginUpdateRequest.supported(row(catalog=true,catalogPin="")))
        val d=decision(git,scan());assertFalse(d.eligible(d.createdAt-1));assertFalse(d.eligible(d.createdAt+600001));assertTrue(d.eligible(d.createdAt+600000));assertTrue(runCatching {PluginUpdateRequest(row(),d)}.isFailure)
    }
    @Test fun typedRefusalsAreDistinctFromUnknownRepliesAndRawErrorsAreNotProjected() {
        val q=PluginUpdateRequest(row(true));val reply=q.acknowledge(cap());assertEquals(PluginUpdateKind.Capabilities,reply.kind);assertEquals(JsonPrimitive(false),reply.summary()["published"])
        for(j in listOf(JsonObject(cap()+mapOf("ok" to JsonPrimitive("false"))),JsonObject(cap()+mapOf("scan_blocked" to JsonPrimitive(true))),JsonObject(cap()+mapOf("name" to JsonPrimitive("other"))))) assertTrue(runCatching {q.acknowledge(j)}.isFailure)
        assertEquals(PluginUpdateKind.Scan,PluginUpdateRequest(row()).acknowledge(scan()).kind)
    }
    @Test fun malformedDecisionDetailsDoNotChangeKnownRefusalButCannotCreateAGrant() {
        val row=row(true);val q=PluginUpdateRequest(row);val j=JsonObject(cap()+mapOf("delta" to JsonObject(mapOf("unknown_surface" to JsonArray(listOf(JsonPrimitive("value")))))));val reply=q.acknowledge(j);assertEquals(PluginUpdateKind.Capabilities,reply.kind);assertTrue(runCatching {PluginUpdateDecision.parse(j,reply,owner,row,"fixture",null)}.isFailure)
        val invalid=JsonObject(scan()+mapOf("scan_findings" to JsonArray(List(257) {JsonObject(emptyMap())})));val git=row();assertTrue(runCatching {decision(git,invalid)}.isFailure)
    }
    @Test fun positiveGitAckIsExactButCatalogMayReportARenamedDirectory() {
        val ack=buildJsonObject {put("ok",true);put("name","install-name");put("unchanged",false)};assertEquals(PluginUpdateKind.Updated,PluginUpdateRequest(row()).acknowledge(ack).kind)
        assertTrue(runCatching {PluginUpdateRequest(row()).acknowledge(JsonObject(ack+mapOf("name" to JsonPrimitive("other"))))}.isFailure)
        val q=PluginUpdateRequest(row(true));val renamed=q.acknowledge(JsonObject(ack+mapOf("name" to JsonPrimitive("renamed"),"sha" to JsonPrimitive(pin))));assertEquals("renamed",renamed.name)
        for(name in listOf("../other","a/b"," bad","a..b")) assertTrue(runCatching {q.acknowledge(JsonObject(ack+mapOf("name" to JsonPrimitive(name))))}.isFailure)
    }
    @Test fun catalogTagPinReadbackRemainsDistinctFromTheCheckedOutCommit() {
        val q=PluginUpdateRequest(row(true));val reply=PluginUpdateReply(PluginUpdateKind.Updated,"install-name",false,pin)
        assertTrue(q.reportedPinObserved(reply,row(true,sha=pin)));assertTrue(q.reportedPinObserved(reply,row(true,sha="c".repeat(40),available=false)))
        assertFalse(q.reportedPinObserved(reply,row(true,sha="c".repeat(40),available=true)));assertFalse(q.reportedPinObserved(reply,row(true,sha="",available=false)));assertFalse(q.reportedPinObserved(reply,row(true,sha="c".repeat(40),catalogPin="d".repeat(40),available=false)))
    }
    @Test fun completeProposalDisclosureIsTransientWhileReceiptsKeepOnlyCountsAndIntent() {
        val row=row(true);val d=decision(row,cap(),PluginCatalogEntry("fixture-plugin","https://github.com/example/plugin",pin,"","community"));assertTrue(d.delta.getValue("python_dependencies").single().contains("private-update-reference"));assertFalse(d.summary().toString().contains("private-update-reference"));assertFalse(PluginUpdateRequest(row,d).summary().toString().contains("private-update-reference"));assertEquals("PluginUpdateRequest(redacted)",PluginUpdateRequest(row,d).toString())
    }
}
