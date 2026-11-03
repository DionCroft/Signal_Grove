package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostFallbackTest {
    private val owner=ManagementTarget("a".repeat(64),"https://host.test/prefix/","worker","password · fixture")
    private fun j(text:String)=Json.parseToJsonElement(text).jsonObject
    private fun snapshot(text:String)=HostFallbackSnapshot.parse(j(text),owner,"home-fixture")
    private val source="""{"fallback_providers":[{"provider":" custom:private ","model":" model-a ","base_url":"https://host.test/private?key=endpoint-fixture","api_key":"existing-inline-fixture","key_env":"PRIVATE_REF","api_mode":"","extension":{"private":"unknown-fixture"}},{"provider":"openrouter","model":"model-b"},17],"fallback_model":{"provider":"zen","model":"legacy","api_key_env":"LEGACY_REF"},"unrelated":{"api_key":"not-retained-fixture"}}"""
    @Test fun onlyChangedChainIsSentAndAllUntouchedRawFieldsArePreserved() {
        val before=snapshot(source);val draft=HostFallbackDraft.from(before);val row=draft.rows(FallbackSection.Primary).first()
        val changed=row.edit(row.provider,"edited-model",row.baseUrl,null)
        val request=HostFallbackRequest(draft.replace(FallbackSection.Primary,0,changed));val body=request.body();assertEquals("worker",body.text("profile"));val config=body.getValue("config").jsonObject
        assertEquals(setOf("fallback_providers"),config.keys);val raw=config.getValue("fallback_providers").jsonArray
        val original=j(source).getValue("fallback_providers").jsonArray
        assertEquals(JsonObject(original[0].jsonObject+mapOf("model" to JsonPrimitive("edited-model"))),raw[0]);assertEquals(original.drop(1),raw.drop(1));assertFalse(changed.reviewDetails().toString().contains("existing-inline-fixture"));assertTrue(changed.reviewDetails().toString().contains("PRIVATE_REF"))
        for(value in listOf(before,draft,row,request)) {assertFalse(value.toString().contains("fixture"));assertFalse(value.toString().contains("PRIVATE_REF"))}
        assertFalse(request.summary().toString().contains("private"));assertFalse(request.body().toString().contains("not-retained-fixture"))
    }
    @Test fun orderedMoveRetainsInvalidRowsAndUnknownFields() {val d=HostFallbackDraft.from(snapshot(source));val out=HostFallbackRequest(d.move(FallbackSection.Primary,2,-1)).body()["config"]!!.jsonObject["fallback_providers"]!!.jsonArray;val old=j(source)["fallback_providers"]!!.jsonArray;assertEquals(listOf(old[0],old[2],old[1]),out);assertFalse(d.rows(FallbackSection.Primary)[2].editable)}
    @Test fun legacyDictionaryIsOnlyConvertedWhenThatSectionIsEdited() {val d=HostFallbackDraft.from(snapshot(source));assertTrue(d.changed.isEmpty());val row=d.rows(FallbackSection.Legacy).single();val req=HostFallbackRequest(d.add(FallbackSection.Legacy,FallbackRow.fresh("plugin-provider","new-model")));assertEquals(listOf(row.wire(),FallbackRow.fresh("plugin-provider","new-model").wire()),req.body()["config"]!!.jsonObject["fallback_model"]!!.jsonArray)}
    @Test fun clearWritesBothEmptyArraysEvenWhenLegacyIsAbsent() {val req=HostFallbackRequest(HostFallbackDraft.from(snapshot("{}" )).clear());assertEquals(setOf("fallback_providers","fallback_model"),req.body()["config"]!!.jsonObject.keys);assertTrue(req.body()["config"]!!.jsonObject.values.all {it==JsonArray(emptyList())})}
    @Test fun removingLastPrimaryDoesNotEraseLegacyImplicitly() {val d=HostFallbackDraft.from(snapshot("""{"fallback_providers":[{"provider":"p","model":"m"}],"fallback_model":{"provider":"q","model":"n"}}"""));val req=HostFallbackRequest(d.remove(FallbackSection.Primary,0));assertEquals(setOf("fallback_providers"),req.body()["config"]!!.jsonObject.keys)}
    @Test fun authChoicesRemoveCompetingFieldsButPreserveExtensions() {val row=HostFallbackDraft.from(snapshot(source)).rows(FallbackSection.Primary).first();for(auth in listOf(FallbackAuth.Host,FallbackAuth.Environment,FallbackAuth.Fresh)) {val value=if(auth==FallbackAuth.Environment) "NEW_ENV" else "fresh-credential-fixture";val out=row.edit(row.provider,row.model,row.baseUrl,null,auth,value);val wire=out.wire().jsonObject;assertEquals(row.wire().jsonObject["extension"],wire["extension"]);assertFalse(wire.containsKey("api_key_env"));assertEquals(auth==FallbackAuth.Fresh,wire.containsKey("api_key"));assertEquals(auth==FallbackAuth.Environment,wire.containsKey("key_env"));assertEquals(auth==FallbackAuth.Fresh,out.reviewDetails().containsKey("fresh_api_key"))}}
    @Test fun unchangedEditPreservesWhitespaceAndExplicitEmptyOptionalFields() {val row=HostFallbackDraft.from(snapshot(source)).rows(FallbackSection.Primary).first();assertEquals(row.wire(),row.edit(row.provider,row.model,row.baseUrl,null).wire())}
    @Test fun privateCredentialAndExtensionChangesInvalidateFreshPreflight() {val old=snapshot(source);for(change in listOf(source.replace("existing-inline-fixture","rotated"),source.replace("unknown-fixture","other"),source.replace("LEGACY_REF","OTHER"))) assertFalse(old.sameConfig(snapshot(change)));assertTrue(old.sameConfig(snapshot(source.replace("not-retained-fixture","different-unrelated"))))}
    @Test fun missingAndNullSectionsAreDistinctAndUnsupportedScalarCanBeCleared() {assertFalse(snapshot("{}").sameConfig(snapshot("""{"fallback_model":null}""")));val d=HostFallbackDraft.from(snapshot("""{"fallback_providers":false}"""));assertFalse(d.rows(FallbackSection.Primary).single().editable);assertEquals(JsonArray(emptyList()),HostFallbackRequest(d.remove(FallbackSection.Primary,0)).body()["config"]!!.jsonObject["fallback_providers"])}
    @Test fun mandatoryAcknowledgmentIsTypedAndNeverCopiesHostDetails() {val req=HostFallbackRequest(HostFallbackDraft.from(snapshot(source)).clear());assertEquals(req.summary(),req.acknowledge(j("""{"ok":true,"error":"private-host-message"}""")));for(value in listOf("false","\"true\"","null","1")) assertTrue(runCatching {req.acknowledge(j("""{"ok":$value}"""))}.isFailure);assertTrue(runCatching {req.acknowledge(j("{}"))}.isFailure)}
    @Test fun boundsInvalidInputsAndMaskedFreshKeysAreRejected() {assertTrue(runCatching {snapshot("""{"fallback_providers":[${List(201) {"{}"}.joinToString(",") }]}""")}.isFailure);for(args in listOf(listOf("","m","",""),listOf("p","m","file:///tmp/key",""),listOf("p","m","https://host.test","unknown"))) assertTrue(runCatching {FallbackRow.fresh(args[0],args[1],args[2],args[3])}.isFailure);for(key in listOf("***","••••","bad\nkey")) assertTrue(runCatching {FallbackRow.fresh("p","m",auth=FallbackAuth.Fresh,credential=key)}.isFailure);assertTrue(runCatching {FallbackRow.fresh("p","m",auth=FallbackAuth.Environment,credential="${'$'}{KEY}")}.isFailure)}
    @Test fun retainedPrivateRowsCannotBeCopiedAcrossScopesOrSubstitutedForAnotherRow() {
        val draft=HostFallbackDraft.from(snapshot(source));val row=draft.rows(FallbackSection.Primary).first()
        val foreign=HostFallbackDraft.from(HostFallbackSnapshot.parse(j(source),owner.copy(profile="other"),"other-home")).rows(FallbackSection.Primary).first()
        assertTrue(runCatching {draft.add(FallbackSection.Legacy,row)}.isFailure)
        assertTrue(runCatching {draft.add(FallbackSection.Primary,foreign)}.isFailure)
        assertTrue(runCatching {draft.replace(FallbackSection.Primary,0,foreign.edit(foreign.provider,"changed",foreign.baseUrl,null))}.isFailure)
        assertTrue(runCatching {draft.replace(FallbackSection.Primary,1,row.edit(row.provider,"changed",row.baseUrl,null))}.isFailure)
        assertEquals("changed",draft.replace(FallbackSection.Primary,0,row.edit(row.provider,"changed",row.baseUrl,null)).rows(FallbackSection.Primary).first().model)
    }
    @Test fun legacyTransportAndUnknownModeCanBeKeptButAutomaticClearsBothPins() {
        val config="""{"fallback_providers":[{"provider":"p","model":"m","api_mode":"","transport":"anthropic","extension":"keep"}]}"""
        val row=HostFallbackDraft.from(snapshot(config)).rows(FallbackSection.Primary).single();assertTrue(row.editable)
        assertEquals(row.wire(),row.edit("p","m","").wire())
        val automatic=row.edit("p","m","",apiMode="").wire().jsonObject;assertFalse(automatic.containsKey("api_mode"));assertFalse(automatic.containsKey("transport"));assertEquals(JsonPrimitive("keep"),automatic["extension"])
        val explicit=row.edit("p","m","",apiMode="codex_responses").wire().jsonObject;assertEquals(JsonPrimitive("codex_responses"),explicit["api_mode"]);assertFalse(explicit.containsKey("transport"))
        val unknown=HostFallbackDraft.from(snapshot(config.replace("anthropic","future-wire"))).rows(FallbackSection.Primary).single();assertTrue(unknown.editable);assertEquals(unknown.wire(),unknown.edit("p","m","").wire())
    }
    @Test fun malformedPrivateFieldsStayOpaqueAndRetainTheirOriginalValues() {
        for((key,value) in listOf("provider" to JsonPrimitive("p".repeat(257)),"model" to JsonPrimitive("m".repeat(2049)),"key_env" to JsonPrimitive("E".repeat(257)),"api_key_env" to JsonPrimitive("bad\nreference"),"transport" to JsonPrimitive("t".repeat(129)),"api_mode" to JsonPrimitive("bad\nmode"))) {
            val raw=JsonObject(mapOf("provider" to JsonPrimitive("p"),"model" to JsonPrimitive("m"),key to value))
            val config=buildJsonObject {put("fallback_providers",JsonArray(listOf(raw)))};val snapshot=HostFallbackSnapshot.parse(config,owner,"home-fixture");val draft=HostFallbackDraft.from(snapshot);val row=draft.rows(FallbackSection.Primary).single()
            assertFalse(key,row.editable);assertTrue(key,row.reviewDetails().isEmpty());assertEquals(raw,row.wire());assertEquals(raw,HostFallbackRequest(draft.add(FallbackSection.Primary,FallbackRow.fresh("p","new"))).body()["config"]!!.jsonObject["fallback_providers"]!!.jsonArray.first())
        }
    }
    @Test fun unchangedDraftCannotCreateMutationAndSnapshotListsAreImmutable() {val d=HostFallbackDraft.from(snapshot(source));assertTrue(runCatching {HostFallbackRequest(d)}.isFailure);assertTrue(runCatching {(d.rows(FallbackSection.Primary) as MutableList).clear()}.isFailure);assertEquals(3,d.rows(FallbackSection.Primary).size)}
}
