package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CustomEndpointProbeAdoptionTest {
    private val owner=ManagementTarget("a".repeat(64),"https://host.test/prefix/","worker","basic · fixture")
    private fun inventory()=CustomEndpointSnapshot.parse(buildJsonObject {put("endpoints",JsonArray(emptyList()));put("current",buildJsonObject {put("provider","");put("model","");put("base_url","")})},JsonObject(emptyMap()),owner,"home-fixture")
    private fun draft(key:CustomEndpointKey=CustomEndpointKey.Keep,models:List<String> = emptyList(),metadata:List<CustomEndpointModelDetail> = emptyList())=CustomEndpointDraft("relay","Relay","https://relay.test/v1","alias",key,if(key==CustomEndpointKey.Fresh) "fresh-private-fixture" else "",apiMode="",models=models,modelDetails=metadata)
    private fun report(before:CustomEndpointDraft=draft(),models:List<String> = listOf("alias","other"),details:List<JsonObject> = listOf(buildJsonObject {put("id","alias");put("canonical_model","canonical");put("reasoning_effort","HIGH")}),url:String="https://resolved.test/v2",mode:String="anthropic_messages"):CustomEndpointProbeReport {
        val expected=inventory();val receipt=ManagementOperation("probe-fixture",owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,"CustomEndpointProbe","custom-endpoints","{}","acknowledged","{}",1)
        val data=CustomEndpointProbeDetails.parse(buildJsonObject {put("models",JsonArray(models.map(::JsonPrimitive)));put("model_details",JsonArray(details));put("resolved_base_url",url);put("transport_checked",mode)})!!
        return CustomEndpointProbeReport(CustomEndpointProbeRequest(expected,before),CustomEndpointProbeAck(false,false),data,receipt)
    }
    @Test fun catalogStagingAppendsWithoutChangingDestinationCredentialOrImportingAliases() {
        val before=draft(CustomEndpointKey.Fresh,listOf("typed"));val r=report(before);val plan=CustomEndpointProbeAdoption.create(r,before,CustomEndpointProbeAdoptionKind.Catalog)!!
        assertEquals(listOf("typed","alias","other"),plan.draft.models);assertEquals(before.baseUrl,plan.draft.baseUrl);assertEquals(before.secret,plan.draft.secret);assertTrue(plan.draft.modelDetails.isEmpty());assertTrue(r.request.same(plan.draft))
    }
    @Test fun returnedAddressStagingClearsFreshCredentialAndUnsubmittedMetadata() {
        val before=draft(CustomEndpointKey.Fresh,metadata=listOf(CustomEndpointModelDetail("alias","canonical","high")));val r=report(before)
        val after=CustomEndpointProbeAdoption.create(r,before,CustomEndpointProbeAdoptionKind.Url)!!.draft
        assertEquals("https://resolved.test/v2",after.baseUrl);assertEquals(CustomEndpointKey.Keep,after.key);assertEquals("",after.secret);assertTrue(after.modelDetails.isEmpty());assertFalse(r.request.same(after))
    }
    @Test fun oneSelectedAliasStagesTypedMetadataAndGlobalEffectEvenWithoutMakeDefault() {
        val before=draft();val after=CustomEndpointProbeAdoption.create(report(before),before,CustomEndpointProbeAdoptionKind.Model,"alias")!!.draft
        assertFalse(after.makeDefault);assertEquals("HIGH",after.modelDetails.single().reasoningEffort);assertEquals("canonical",after.aliasEffect.effectiveModel);assertEquals("high",after.aliasEffect.globalEffort)
    }
    @Test fun previouslyStagedMetadataRemainsRepresentedWhenAnotherModelIsSelected() {
        val before=draft(metadata=listOf(CustomEndpointModelDetail("alias","canonical","high")));val after=CustomEndpointProbeAdoption.create(report(before),before,CustomEndpointProbeAdoptionKind.Model,"other")!!.draft
        assertEquals("other",after.model);assertEquals(listOf("alias"),after.models);assertEquals("alias",after.modelDetails.single().id);assertNull(after.aliasEffect.globalEffort)
    }
    @Test fun stagingRejectsChangedFullDraftOwnerInventoryAndReportIdentity() {
        val before=draft();val r=report(before);val plan=CustomEndpointProbeAdoption.create(r,before,CustomEndpointProbeAdoptionKind.Catalog)!!
        assertTrue(plan.stillCurrent(r.request.expected,owner,r,before));assertFalse(plan.stillCurrent(inventory(),owner,r,before));assertFalse(plan.stillCurrent(r.request.expected,owner.copy(actor="basic · other"),r,before));assertFalse(plan.stillCurrent(r.request.expected,owner,report(before),before))
        val changed=draft(models=listOf("new-draft-model"));assertTrue(r.request.same(changed));assertFalse(plan.stillCurrent(r.request.expected,owner,r,changed))
    }
    @Test fun oversizedOrAmbiguousCatalogIsNotSilentlyTruncatedButOneModelCanBeChosen() {
        val before=draft();val large=report(before,models=(0..256).map {"m"+it})
        assertNull(CustomEndpointProbeAdoption.create(large,before,CustomEndpointProbeAdoptionKind.Catalog));assertEquals("m256",CustomEndpointProbeAdoption.create(large,before,CustomEndpointProbeAdoptionKind.Model,"m256")!!.draft.model)
        val duplicate=report(before,models=listOf("alias"," alias "));assertNull(CustomEndpointProbeAdoption.create(duplicate,before,CustomEndpointProbeAdoptionKind.Catalog))
        val repeated=report(before,details=listOf(buildJsonObject {put("id","alias")},buildJsonObject {put("id"," alias ")}));assertNull(CustomEndpointProbeAdoption.create(repeated,before,CustomEndpointProbeAdoptionKind.Model,"alias"))
    }
    @Test fun unsupportedAddressesModesAndUnacknowledgedOrForeignReportsCannotStage() {
        val before=draft();assertNull(CustomEndpointProbeAdoption.create(report(before,url="file:///private"),before,CustomEndpointProbeAdoptionKind.Url));assertNull(CustomEndpointProbeAdoption.create(report(before,mode="future-mode"),before,CustomEndpointProbeAdoptionKind.Mode))
        val r=report(before);for(receipt in listOf(r.receipt.copy(state="unknown"),r.receipt.copy(apiScope="b".repeat(64)))) assertNull(CustomEndpointProbeAdoption.create(CustomEndpointProbeReport(r.request,r.ack,r.details,receipt),before,CustomEndpointProbeAdoptionKind.Catalog))
    }
    @Test fun negativeProbeCanStageRecognizedModeWithoutAHealthClaimOrSecretDisclosure() {
        val before=draft(CustomEndpointKey.Fresh);val r=report(before);assertFalse(r.ack.ok);assertFalse(r.ack.reachable)
        val plan=CustomEndpointProbeAdoption.create(r,before,CustomEndpointProbeAdoptionKind.Mode)!!;assertEquals("anthropic_messages",plan.draft.apiMode)
        for(value in listOf("fresh-private-fixture","https://relay.test/v1","canonical","HIGH")) assertFalse(plan.toString().contains(value))
    }
    @Test fun stagingTheSameAddressDoesNotEraseANewKeyAndIsAnExplainedNoOp() {
        val before=draft(CustomEndpointKey.Fresh,metadata=listOf(CustomEndpointModelDetail("alias","canonical","high")))
        val plan=CustomEndpointProbeAdoption.create(report(before,url=before.baseUrl+"/"),before,CustomEndpointProbeAdoptionKind.Url)!!
        assertFalse(plan.changes);assertEquals(before.secret,plan.draft.secret);assertEquals(before.modelDetails,plan.draft.modelDetails)
    }
}
