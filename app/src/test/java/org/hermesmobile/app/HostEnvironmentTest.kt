package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostEnvironmentTest {
    private val owner=ManagementTarget("a".repeat(64),"https://example.test/prefix/","worker","password · fixture")
    private val home=PluginHome.project(JsonPrimitive("/fixture-env"))
    private fun raw(present:Boolean=true)=buildJsonObject {put("FIXTURE_API_KEY",buildJsonObject {put("is_set",present);put("is_password",true);put("custom",false);put("description","Shared credential");put("category","provider");put("channel_managed",false);put("provider","first");put("provider_label","First provider");put("provider_profiles",buildJsonArray {for(id in listOf("first","second")) add(buildJsonObject {put("provider",id);put("provider_label",id);put("url","https://private.example/?secret=private-url")})});put("value","private-raw-value");put("redacted_value","private-mask-preview");put("url","private-host-url");put("tools",buildJsonArray {add("fixture_tool")})})}
    private fun inventory(present:Boolean=true)=HostEnvInventory.parse(raw(present),owner,home)
    @Test fun explicitEmptyIntentUsesALiteralPutValueAndDistinctReceiptWithoutRemovalFields() {
        val state=inventory();val request=HostEnvRequest(state,"FIXTURE_API_KEY",HostEnvAction.Empty);val body=request.body("worker")
        assertEquals(setOf("profile","key","value","provider_setup"),body.keys);assertEquals(JsonPrimitive(""),body["value"]);assertEquals(JsonPrimitive(false),body["provider_setup"]);assertEquals("EnvEmpty",request.receiptAction);assertEquals(JsonPrimitive("Empty"),request.summary()["operation"]);assertFalse(request.summary().containsKey("value"))
        request.acknowledge(buildJsonObject {put("ok",true);put("key",request.key);put("config_updates",JsonArray(emptyList()))})
        assertTrue(runCatching {HostEnvRequest(state,request.key,HostEnvAction.Empty,"nonempty")}.isFailure);assertTrue(runCatching {HostEnvRequest(state,request.key,HostEnvAction.Save,"")}.isFailure)
        assertTrue(runCatching {HostEnvRequest(state,"PATH",HostEnvAction.Empty)}.isFailure);assertNotNull(HostEnvRequest(state,"NEW_KEY",HostEnvAction.Empty))
    }
    @Test fun emptyObservationRequiresTheKeyAndReportedUnsetRatherThanTreatingDeletionAsEquivalent() {
        val request=HostEnvRequest(inventory(),"FIXTURE_API_KEY",HostEnvAction.Empty);assertTrue(request.presenceMatches(inventory(false)));assertFalse(request.presenceMatches(inventory()))
        val absent=HostEnvInventory(owner,home!!.identity(),emptyList());assertFalse(request.presenceMatches(absent));assertTrue(HostEnvRequest(inventory(),request.key,HostEnvAction.Remove).presenceMatches(absent))
    }
    @Test fun metadataOmitsAllValuesPreviewsAndUrlsButKeepsSharedProviderIdentity() {
        val row=inventory().rows.single();assertTrue(row.isSet);assertEquals(listOf("first","second"),row.providers.map {it.id});assertEquals(listOf("fixture_tool"),row.tools);assertFalse(row.toString().contains("private"));assertFalse(inventory().toString().contains("private"))
    }
    @Test fun saveBodyHasOnlyTheExactKeyValueAndMatchingExplicitProfile() {
        val request=HostEnvRequest(inventory(),"FIXTURE_API_KEY",HostEnvAction.Save,"private-new-value");val body=request.body("worker");assertEquals(setOf("profile","key","value","provider_setup"),body.keys);assertEquals(JsonPrimitive(false),body["provider_setup"]);assertEquals(JsonPrimitive("private-new-value"),body["value"]);assertEquals(JsonPrimitive("worker"),body["profile"])
        for(profile in listOf("all","hermes",".hermes","other")) assertTrue(runCatching {request.body(profile)}.isFailure)
        assertFalse(request.summary().toString().contains("private"));assertFalse(request.summary().toString().contains("hash"));assertFalse(request.toString().contains("private"))
    }
    @Test fun removeContainsNoValueAndCanAddressExistingUnsetOrProtectedNames() {
        val request=HostEnvRequest(inventory(false),"FIXTURE_API_KEY",HostEnvAction.Remove);assertEquals(setOf("profile","key"),request.body("worker").keys);assertFalse(request.previous!!.isSet)
        val row=HostEnvRow("PATH",true,"","",null,null,true,null,emptyList(),emptyList());val state=HostEnvInventory(owner,home!!.identity(),listOf(row));assertTrue(runCatching {HostEnvRequest(state,"PATH",HostEnvAction.Save,"new")}.isFailure);assertNotNull(HostEnvRequest(state,"PATH",HostEnvAction.Remove))
        assertTrue(runCatching {HostEnvRequest(inventory(),"ABSENT_KEY",HostEnvAction.Remove)}.isFailure)
    }
    @Test fun validNamesRespectStaticExecutionPolicyWithoutBlockingIntegrationPrefixes() {
        for(k in listOf("PATH","path","LD_NEW_SETTING","DYLD_ANYTHING","GIT_CONFIG_KEY_17","HERMES_HOME","PYTHONPATH","NODE_OPTIONS","BROWSER","HERMES_REDACT_SECRETS")) assertFalse(k,HostEnvPolicy.writable(k))
        for(k in listOf("FIXTURE_API_KEY","HERMES_SPOTIFY_CLIENT_ID","custom_key","_fixture")) assertTrue(k,HostEnvPolicy.writable(k))
        for(k in listOf("","1KEY","KEY=VALUE","KEY.NAME","A/B","a b","A\nB")) assertFalse(k,HostEnvPolicy.key(k))
    }
    @Test fun valuesAreFreshSingleLineAndCannotBeDisplayOnlyMasks() {
        for(v in listOf("","***","abcd...wxyz","«redacted:old»","«redacted-vault-secret»","x\ny","x\ry","bad\u0000value","x".repeat(16385),"口令","private-é-value")) assertFalse(v.take(16),HostEnvPolicy.value(v))
        for(v in listOf("private-new-value","a b","  significant spaces  ")) assertTrue(HostEnvPolicy.value(v))
    }
    @Test fun publicMetadataChangesRefuseWhilePresenceCannotDetectASecretRotation() {
        val req=HostEnvRequest(inventory(),"FIXTURE_API_KEY",HostEnvAction.Save,"private-new-value");assertFalse(req.same(inventory(false)));assertTrue(req.same(inventory()))
        val changed=JsonObject(raw()+mapOf("FIXTURE_API_KEY" to JsonObject(raw()["FIXTURE_API_KEY"]!!.jsonObject+mapOf("redacted_value" to JsonPrimitive("different-mask")))));assertTrue(req.same(HostEnvInventory.parse(changed,owner,home)))
        val other=HostEnvInventory(ManagementTarget(owner.apiScope,owner.url,"other",owner.actor),home!!.identity(),inventory().rows);assertFalse(req.same(other))
    }
    @Test fun unknownCustomKeyIsBoundToItsPriorAbsenceAndTypedIntentOnly() {
        val request=HostEnvRequest(inventory(),"NEW_KEY",HostEnvAction.Save,"private-new-value");assertNull(request.previous);assertTrue(request.same(inventory()));assertEquals(JsonNull,request.summary()["previous_presence"]);val after=HostEnvInventory(owner,home!!.identity(),inventory().rows+HostEnvRow("NEW_KEY",true,"","",true,false,true,null,emptyList(),emptyList()));assertFalse(request.same(after))
    }
    @Test fun acknowledgementsNeedExactKeyAndTypedStatusWhileOptionalPathsBecomeCountsOnly() {
        val req=HostEnvRequest(inventory(),"FIXTURE_API_KEY",HostEnvAction.Save,"private-new-value");val ack=buildJsonObject {put("ok",true);put("key","FIXTURE_API_KEY");put("config_updates",buildJsonArray {add("private-mirror-path")})};val summary=req.acknowledge(ack);assertEquals(JsonPrimitive(1),summary["config_updates_count"]);assertFalse(summary.toString().contains("private"))
        req.acknowledge(JsonObject(ack+mapOf("config_updates" to JsonPrimitive("malformed-private-path"))))
        for(j in listOf(JsonObject(ack+mapOf("ok" to JsonPrimitive("true"))),JsonObject(ack+mapOf("key" to JsonPrimitive("OTHER_KEY"))))) assertTrue(runCatching {req.acknowledge(j)}.isFailure)
        val remove=HostEnvRequest(inventory(),"FIXTURE_API_KEY",HostEnvAction.Remove);val removed=buildJsonObject {put("ok",true);put("key","FIXTURE_API_KEY");put("found",true);put("removed",false)};assertEquals(JsonPrimitive(false),remove.acknowledge(removed)["env_entry_removed"]);assertTrue(runCatching {remove.acknowledge(JsonObject(removed+mapOf("found" to JsonPrimitive(false))))}.isFailure)
    }
    @Test fun missingOrUntypedPresenceAndMalformedMetadataDoNotBecomeAnEmptyInventory() {
        for(row in listOf(buildJsonObject {},buildJsonObject {put("is_set","true")},buildJsonObject {put("is_set",true);put("tools",buildJsonArray {add(2)})})) assertTrue(runCatching {HostEnvInventory.parse(buildJsonObject {put("FIXTURE_KEY",row)},owner,home)}.exceptionOrNull() is HostEnvUnavailable)
        assertTrue(HostEnvInventory.parse(JsonObject(emptyMap()),owner,home).rows.isEmpty());assertTrue(runCatching {HostEnvInventory.parse(raw(),owner,null)}.isFailure)
    }
    @Test fun absenceAndPresenceRemainObservationsAndMetadataCollectionsAreImmutable() {
        val save=HostEnvRequest(inventory(false),"FIXTURE_API_KEY",HostEnvAction.Save,"private-new-value");assertTrue(save.presenceMatches(inventory()));val remove=HostEnvRequest(inventory(),"FIXTURE_API_KEY",HostEnvAction.Remove);assertTrue(remove.presenceMatches(inventory(false)));assertFalse(remove.presenceMatches(inventory()))
        assertTrue(runCatching {(inventory().rows as MutableList).clear()}.isFailure);assertTrue(runCatching {(inventory().rows.single().providers as MutableList).clear()}.isFailure)
    }
}
