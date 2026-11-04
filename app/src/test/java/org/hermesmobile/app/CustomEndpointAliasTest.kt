package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CustomEndpointAliasTest {
    private val owner=ManagementTarget("a".repeat(64),"https://host.test/prefix/","worker","basic · fixture")
    private fun obj(vararg fields:Pair<String,JsonElement>)=JsonObject(mapOf(*fields))
    private fun metadata(model:String="alias")=buildJsonObject {
        put("endpoints",buildJsonArray {add(buildJsonObject {put("id","relay");put("name","Relay");put("base_url","https://relay.test/v1");put("model",model);put("models",buildJsonArray {add("alias");add("canonical");add("other")});put("api_mode","");put("context_length",4096);put("discover_models",true);put("has_api_key",false);put("is_current",false);put("source","providers")})})
        put("current",buildJsonObject {put("provider","other");put("model","old");put("base_url","https://other.test/v1")})
    }
    private fun raw(agent:JsonElement?=null,models:JsonElement=obj("alias" to obj("unknown" to JsonPrimitive(7))))=buildJsonObject {
        put("providers",obj("relay" to obj("models" to models,"extra_headers" to obj("X-Fixture" to JsonPrimitive("retained-private")))))
        if(agent!=null) put("agent",agent)
    }
    private fun snapshot(config:JsonObject=raw(),model:String="alias")=CustomEndpointSnapshot.parse(metadata(model),config,owner,"home-fixture")
    private fun agent(value:JsonElement)=obj("reasoning_overrides" to obj("canonical" to value),"unrelated" to JsonPrimitive("not-retained-private"))
    private fun detail(canonical:String?="canonical",effort:String?="HIGH")=CustomEndpointModelDetail("alias",canonical,effort)
    private fun draft(details:List<CustomEndpointModelDetail> = listOf(detail()),context:Long?=null)=CustomEndpointDraft("relay","Relay","https://relay.test/v1","alias",contextLength=context,modelDetails=details)

    @Test fun legacyPayloadStillOmitsMetadataAndRemainsWritableWithMalformedAgent() {
        val d=draft(emptyList());assertFalse(d.body().containsKey("model_details"));assertEquals("alias",d.aliasEffect.effectiveModel)
        val s=snapshot(raw(JsonPrimitive("malformed")));assertTrue(s.canApplyMetadata(d));CustomEndpointRequest.save(s,d,s.rows.single())
    }
    @Test fun explicitMetadataTrimsValuesButKeepsProviderCaseAndLowercasesGlobalEffort() {
        val value=CustomEndpointModelDetail(" alias "," canonical "," HIGH ");val d=draft(listOf(value))
        assertEquals(obj("id" to JsonPrimitive("alias"),"canonical_model" to JsonPrimitive("canonical"),"reasoning_effort" to JsonPrimitive("HIGH")),d.body()["model_details"]!!.jsonArray.single())
        assertEquals("canonical",d.aliasEffect.effectiveModel);assertEquals("high",d.aliasEffect.globalEffort)
    }
    @Test fun canonicalRedirectSurvivesMissingOrUnknownEffortWithoutChangingGlobalReasoning() {
        for(effort in listOf(null,"","hgih","fast")) {val effect=draft(listOf(detail(effort=effort))).aliasEffect;assertTrue(effect.redirects);assertEquals("canonical",effect.effectiveModel);assertNull(effect.globalEffort)}
    }
    @Test fun onlyVerifiedBareLevelsAndDisabledSpellingsProduceGlobalWrites() {
        for(effort in listOf("minimal","low","medium","high","xhigh","max","ultra","none","false","disabled")) assertEquals(effort,draft(listOf(detail(effort=effort.uppercase()))).aliasEffect.globalEffort)
        assertNull(draft(listOf(detail(canonical="alias",effort="high"))).aliasEffect.globalEffort)
    }
    @Test fun blankMetadataMeansKeepAndDoesNotInventAHostClearOrRedirect() {
        val d=draft(listOf(detail(canonical=" ",effort=" ")))
        assertEquals(obj("id" to JsonPrimitive("alias")),d.body()["model_details"]!!.jsonArray.single());assertFalse(d.aliasEffect.redirects);assertNull(d.aliasEffect.globalEffort)
    }
    @Test fun metadataMembershipDuplicatesAndBoundsAreCheckedBeforeReview() {
        assertTrue(runCatching {draft(listOf(detail(),CustomEndpointModelDetail(" alias ","different")))}.isFailure)
        assertTrue(runCatching {draft(listOf(CustomEndpointModelDetail("not-selected")))}.isFailure)
        assertTrue(runCatching {CustomEndpointModelDetail("bad\nmodel")}.isFailure)
        assertTrue(runCatching {CustomEndpointModelDetail("alias",reasoningEffort="x".repeat(129))}.isFailure)
        val models=(0..256).map {"m"+it};assertTrue(runCatching {CustomEndpointDraft("relay","Relay","https://relay.test/v1","alias",models=models,modelDetails=models.map {CustomEndpointModelDetail(it)})}.isFailure)
    }
    @Test fun mutableCallerListsCannotChangeAFrozenMetadataBody() {
        val values=mutableListOf(detail());val d=draft(values);val body=d.body();values.clear();assertEquals(body,d.body())
        assertTrue(runCatching {(d.modelDetails as MutableList<CustomEndpointModelDetail>).clear()}.isFailure)
    }
    @Test fun onlyAReviewedGlobalWriteRequiresTheMinimalReasoningPreflightProjection() {
        val a=snapshot(raw(agent(JsonPrimitive("low"))));val b=snapshot(raw(agent(JsonPrimitive("medium"))))
        assertTrue(a.same(b));assertFalse(a.same(b,includeReasoning=true));assertEquals(JsonPrimitive("low"),a.priorReasoning(draft()))
        val changedUnrelated=raw(obj("reasoning_overrides" to obj("canonical" to JsonPrimitive("low")),"unrelated" to JsonPrimitive("changed")))
        assertTrue(a.same(snapshot(changedUnrelated),includeReasoning=true))
    }
    @Test fun malformedAgentOrOverridesCannotBeReplacedByAnImportedGlobalWrite() {
        for(agent in listOf(JsonPrimitive("bad"),JsonNull,obj("reasoning_overrides" to JsonPrimitive("bad")))) {
            val s=snapshot(raw(agent));assertFalse(s.canApplyMetadata(draft()));assertTrue(runCatching {CustomEndpointRequest.save(s,draft(),s.rows.single())}.isFailure)
            assertTrue(s.canApplyMetadata(draft(listOf(detail(effort="unknown")))))
        }
    }
    @Test fun importingMetadataCannotDiscardUnsupportedModelMapsOrCanonicalContextRows() {
        for(models in listOf<JsonElement>(JsonPrimitive("bad"),buildJsonArray {add("alias")},obj("alias" to JsonPrimitive("bad")))) assertFalse(snapshot(raw(models=models)).canApplyMetadata(draft()))
        val s=snapshot(raw(models=obj("alias" to obj(),"canonical" to JsonPrimitive("bad"))))
        assertTrue(s.canApplyMetadata(draft()));assertFalse(s.canApplyMetadata(draft(context=8192)))
    }
    @Test fun metadataAndGlobalObservationRemainSeparateFromTheTypedSaveAck() {
        val d=draft(context=8192);val before=snapshot(raw(agent(JsonPrimitive("low"))));val request=CustomEndpointRequest.save(before,d,before.rows.single())
        val ack=request.acknowledge(obj("ok" to JsonPrimitive(true),"id" to JsonPrimitive("relay")))
        val models=obj("alias" to obj("canonical_model" to JsonPrimitive("canonical"),"reasoning_effort" to JsonPrimitive("HIGH"),"unknown" to JsonPrimitive(7)),"canonical" to obj("context_length" to JsonPrimitive(8192)))
        assertTrue(snapshot(raw(agent(JsonPrimitive("high")),models),"canonical").metadataObserved(d))
        assertFalse(snapshot(raw(agent(JsonPrimitive("low")),models),"canonical").metadataObserved(d))
        assertEquals(JsonPrimitive(true),ack.summary["acknowledged"])
        assertFalse(snapshot(raw(agent(JsonPrimitive("high")),obj("canonical" to obj("context_length" to JsonPrimitive(8192)))),"canonical").metadataObserved(d))
    }
    @Test fun durableSummariesAndObjectStringsDoNotDiscloseImportedValuesOrPriorOverrides() {
        val d=draft();val s=snapshot(raw(agent(JsonPrimitive("prior-private"))));val request=CustomEndpointRequest.save(s,d,s.rows.single())
        assertEquals(JsonPrimitive(true),request.summary()["global_reasoning_write"]);assertEquals(JsonPrimitive(true),request.summary()["selected_alias_redirect"])
        for(value in listOf(detail(),d.aliasEffect,CustomEndpointReasoning.parse(agent(JsonPrimitive("prior-private"))),s,request)) for(secret in listOf("canonical","HIGH","prior-private","retained-private","not-retained-private")) assertFalse(value.toString().contains(secret))
        for(secret in listOf("canonical","HIGH","prior-private","retained-private")) assertFalse(request.summary().toString().contains(secret))
    }
    @Test fun legacyDictionaryAndLosslessListMetadataCanBeImportedWithoutDroppingUnknownFields() {
        val meta=Json.parseToJsonElement(metadata().toString().replace("\"providers\"","\"custom_providers\"")).jsonObject
        for(models in listOf<JsonElement>(obj("alias" to obj("unknown" to JsonPrimitive(7))),buildJsonArray {add("other");add(obj("id" to JsonPrimitive("alias"),"unknown" to JsonPrimitive(7)))})) {
            val config=buildJsonObject {put("custom_providers",buildJsonArray {add(obj("name" to JsonPrimitive("Relay"),"models" to models))})}
            val s=CustomEndpointSnapshot.parse(meta,config,owner,"home-fixture");assertTrue(s.canEdit(s.rows.single()));assertTrue(s.canApplyMetadata(draft()));CustomEndpointRequest.save(s,draft(),s.rows.single())
        }
    }
    @Test fun ambiguousLegacyRowsCannotBeSilentlyCollapsedByNewImports() {
        val config=buildJsonObject {put("custom_providers",buildJsonArray {add(obj("name" to JsonPrimitive("Relay"),"models" to buildJsonArray {add("alias");add(" alias ")}))})}
        val s=snapshot(config);assertFalse(s.canApplyMetadata(draft()));assertTrue(s.canApplyMetadata(draft(emptyList())))
    }
    @Test fun anOversizedPriorSharedValueCannotBeOverwrittenWithoutAnAccuratePrivateReview() {
        val s=snapshot(raw(agent(JsonPrimitive("x".repeat(9000)))));assertFalse(s.canApplyMetadata(draft()));assertTrue(s.canApplyMetadata(draft(listOf(detail(effort="unknown")))))
    }
}
