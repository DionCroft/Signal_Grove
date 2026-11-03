package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class PluginToggleTest {
    private fun row(status:String="disabled",key:String="tools/fixture",version:String="1",name:String="fixture")=ManagedPlugin(name,key,version,"private-description","user",status,true,false,"","","a".repeat(40),"","",false,emptyList(),emptyList())
    private fun inventory(vararg rows:ManagedPlugin)=GatewayPluginInventory.parse(buildJsonObject {put("plugins",buildJsonArray {rows.forEach {r->add(buildJsonObject {put("name",r.name);put("key",r.key);put("version",r.version);put("description",r.description);put("source",r.source);put("status",r.status);put("installed_sha",r.installedSha);put("servers",JsonArray(emptyList()))})}});put("user_count",rows.size);put("bundled_count",0)},"worker")
    @Test fun canonicalKeyAndTypedEnableAreTheOnlyToggleInputs() {
        val q=PluginToggleRequest(row(),true);assertEquals(buildJsonObject {put("profile","worker");put("action","toggle");put("key","tools/fixture");put("enable",true)},q.params("worker"));assertTrue(runCatching {q.params("all")}.isFailure);assertFalse(q.summary().toString().contains("private-description"));assertEquals("PluginToggleRequest(redacted)",q.toString())
    }
    @Test fun unknownStatesUnsafeKeysLegacyRedirectsAndNoOpIntentAreRefused() {
        for(key in listOf("../fixture","/fixture","tools//fixture","tools/..","observability/langfuse")) assertFalse(PluginToggleRequest.supported(row(key=key)))
        assertFalse(PluginToggleRequest.supported(row(status="running")));assertTrue(runCatching {PluginToggleRequest(row("enabled"),true)}.isFailure);assertTrue(PluginToggleRequest.supported(row(status="not enabled")))
    }
    @Test fun reviewIdentityAndKnownCanonicalNameCollisionAreChecked() {
        val q=PluginToggleRequest(row(),true);assertTrue(q.same(row()));assertFalse(q.same(row(version="2")));assertFalse(q.same(row(status="enabled")))
        assertTrue(PluginToggleRequest.collision(row(),inventory(row(),row(key="other",name="tools/fixture"))));assertFalse(PluginToggleRequest.collision(row(),inventory(row())))
    }
    @Test fun acknowledgementRequiresExactTypedSuccessAndCanonicalKey() {
        val q=PluginToggleRequest(row(),true);val ack=buildJsonObject {put("ok",true);put("name","tools/fixture")};q.acknowledge(ack)
        for(j in listOf(JsonObject(ack+mapOf("ok" to JsonPrimitive("true"))),JsonObject(ack+mapOf("name" to JsonPrimitive("fixture"))),JsonObject(ack-mapOf("name" to JsonNull).keys))) assertTrue(runCatching {q.acknowledge(j)}.isFailure)
    }
    @Test fun readbackIsExplicitConfigurationStateAndDoesNotUseLiveServerHealth() {
        assertTrue(PluginToggleRequest(row(),true).observed(inventory(row("enabled"))));assertFalse(PluginToggleRequest(row(),true).observed(inventory(row("disabled"))));assertTrue(PluginToggleRequest(row("enabled"),false).observed(inventory(row("disabled"))));assertFalse(PluginToggleRequest(row("enabled"),false).observed(inventory(row("not enabled"))))
    }
    @Test fun optionalToggleActivationDoesNotRequireInstallOnlyDependencyArrays() {
        val r=PluginInstallReport.parse(buildJsonObject {put("ok",true);put("name","tools/fixture");put("restart_required",true)},"tools/fixture",requireInstallDetails=false)
        assertEquals(true,r.restartRequired);assertNull(r.dependencyCount);assertFalse(r.activationReported);assertEquals(JsonNull,r.summary()["reported_live_servers"]);assertTrue(runCatching {PluginInstallReport.parse(buildJsonObject {put("ok",true)},"tools/fixture")}.isFailure)
    }
}
