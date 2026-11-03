package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostMoaTest {
    private val owner=ManagementTarget("a".repeat(64),"https://fixture.example/prefix/","worker","password · fixture")
    private val home=PluginHome.project(JsonPrimitive("/private-moa-home"))!!
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private fun preset(aggregatorEffort:String?="high",referenceEffort:String?="low")=buildJsonObject {
        put("reference_models",buildJsonArray {add(buildJsonObject {put("provider","fixture");put("model","old");put("enabled",true);referenceEffort?.let {put("reasoning_effort",it)}})})
        put("aggregator",buildJsonObject {put("provider","fixture");put("model","old");aggregatorEffort?.let {put("reasoning_effort",it)};put("api_key","private-key")});put("enabled",true);put("reference_temperature",JsonNull);put("aggregator_temperature",JsonNull);put("reference_timeout",JsonNull);put("degraded_reference_policy","loud");put("fanout","user_turn")
    }
    private fun raw(p:JsonObject=preset(),flat:Boolean=true)=buildJsonObject {put("default_preset","main");put("active_preset","archive");put("privacy_filter","full");put("presets",buildJsonObject {put("main",p);put("archive",p)});if(flat) p.forEach {(k,v)->put(k,v)};put("api_key","private-root-key")}
    private fun snapshot(config:HostMoaConfig=HostMoaConfig.parse(raw())):HostMoaSnapshot {
        val aux=obj("""{"main":{"provider":"fixture","model":"old"},"tasks":[]}""")
        val catalog=obj("""{"provider":"fixture","model":"old","providers":[{"slug":"fixture","name":"Fixture","authenticated":true,"models":["old","new","blocked"],"unavailable_models":["blocked"]},{"slug":"moa","authenticated":true,"models":["main"]}]}""")
        return HostMoaSnapshot(HostModelInventory.parse(aux,catalog,owner,home),config)
    }
    @Test fun normalizedNamedProjectionIsImmutableAndOmitsPrivateFields() {
        val config=HostMoaConfig.parse(raw());assertEquals("archive",config.activePreset);assertEquals("full",config.privacy);assertEquals("high",config.presets.getValue("main").aggregator.reasoning);assertFalse(config.json().toString().contains("private"));assertTrue(runCatching {(config.presets as MutableMap).clear()}.isFailure);assertTrue(runCatching {(config.presets.getValue("main").references as MutableList).clear()}.isFailure);assertFalse(config.toString().contains("archive"))
    }
    @Test fun partialOrConflictingCompatibilityAliasesAreRefusedRatherThanRepaired() {
        assertEquals(HostMoaConfig.parse(raw()),HostMoaConfig.parse(raw(flat=false)))
        for(value in listOf(JsonObject(raw()-"fanout"),JsonObject(raw()+mapOf("fanout" to JsonPrimitive("per_iteration"))))) assertTrue(runCatching {HostMoaConfig.parse(value)}.isFailure)
    }
    @Test fun requiredTypesNamesSelectorsAndSlotCompletenessCannotBecomeSuccessfulEmptyDefaults() {
        for(pair in listOf("presets" to JsonObject(emptyMap()),"presets" to JsonArray(emptyList()),"default_preset" to JsonPrimitive("missing"),"active_preset" to JsonPrimitive("missing"),"privacy_filter" to JsonPrimitive(true))) assertTrue(runCatching {HostMoaConfig.parse(JsonObject(raw()+pair))}.isFailure)
        for(p in listOf(JsonObject(preset()+mapOf("reference_models" to JsonArray(emptyList()))),JsonObject(preset()+mapOf("enabled" to JsonPrimitive("true"))),JsonObject(preset()+mapOf("aggregator" to obj("""{"provider":"moa","model":"main"}"""))))) assertTrue(runCatching {HostMoaConfig.parse(raw(p))}.isFailure)
    }
    @Test fun numericAndCadenceInputsRejectBooleanNonFiniteAndNonPositiveTimeoutsWithoutArtificialCaps() {
        for(v in listOf("NaN","Infinity","-Infinity","wrong")) assertNull(HostMoaLimits.number(v))
        assertNull(HostMoaLimits.number("0",true));assertNotNull(HostMoaLimits.number("1e100",true));assertNotNull(HostMoaLimits.number("-3"))
        for(v in listOf("every_n:1","every_n:0","every_n:02","every_n:2.0","every_n:-2","every_n","other")) assertFalse(HostMoaLimits.cadence(v))
        for(v in listOf("user_turn","per_iteration","every_n:2","every_n:"+"9".repeat(128))) assertTrue(HostMoaLimits.cadence(v))
        for(v in listOf(JsonPrimitive(true),JsonPrimitive("3"),JsonPrimitive(-1),JsonPrimitive(Double.POSITIVE_INFINITY))) assertTrue(runCatching {HostMoaConfig.parse(raw(JsonObject(preset()+mapOf("reference_timeout" to v))))}.isFailure)
    }
    @Test fun bespokeReasoningCanBeInspectedButCannotBeSilentlyLostDuringFullMapSave() {
        val config=HostMoaConfig.parse(raw(preset(referenceEffort="fast")));assertEquals("fast",config.presets.getValue("main").references.single().reasoning);assertFalse(config.writable);assertTrue(runCatching {HostMoaRequest(snapshot(config),config)}.isFailure)
    }
    @Test fun aggregatorClearIsExplicitlyUnsupportedWhileReferenceClearAndCanonicalOverrideWork() {
        val expected=snapshot();val main=expected.config.presets.getValue("main")
        val cleared=expected.config.copy(presets=expected.config.presets+("main" to main.copy(aggregator=main.aggregator.copy(reasoning=null))));assertTrue(runCatching {HostMoaRequest(expected,cleared)}.isFailure)
        val changed=expected.config.copy(presets=expected.config.presets+("main" to main.copy(references=main.references.map {it.copy(reasoning=null)},aggregator=main.aggregator.copy(reasoning="none"))));assertEquals(JsonPrimitive("none"),HostMoaRequest(expected,changed).body("worker")["presets"]!!.jsonObject["main"]!!.jsonObject["aggregator"]!!.jsonObject["reasoning_effort"])
        assertFalse(changed.presets.getValue("main").json()["reference_models"]!!.jsonArray.single().jsonObject.containsKey("reasoning_effort"))
    }
    @Test fun exactFullNamedBodyOmitsPrivacyLegacyFlatSecretsAndGuessedConcurrencyFields() {
        val expected=snapshot();val body=HostMoaRequest(expected,expected.config).body("worker");assertEquals(setOf("profile","default_preset","active_preset","presets"),body.keys);assertEquals(setOf("main","archive"),body["presets"]!!.jsonObject.keys);assertFalse(body.toString().contains("private"));assertFalse(body.toString().contains("confirm_expensive_model"));for(p in listOf("all","hermes",".hermes","other","")) assertTrue(runCatching {HostMoaRequest(expected,expected.config).body(p)}.isFailure)
    }
    @Test fun novelChoicesRequireEligibleCatalogRoutesAndRecursiveMoaCannotBeConstructed() {
        val expected=snapshot();val p=expected.config.presets.getValue("main")
        fun changed(model:String)=expected.config.copy(presets=expected.config.presets+("main" to p.copy(references=listOf(HostMoaSlot(HostModelRoute("fixture",model))))))
        assertNotNull(HostMoaRequest(expected,changed("new")));assertTrue(runCatching {HostMoaRequest(expected,changed("blocked"))}.isFailure);assertTrue(runCatching {HostMoaRequest(expected,changed("missing"))}.isFailure);assertTrue(runCatching {HostMoaSlot(HostModelRoute("MoA","main"))}.isFailure)
    }
    @Test fun deletionRemapsRemovedSelectorsAndNeverDropsTheLastPreset() {
        val expected=snapshot();val cfg=expected.config.without("archive");assertEquals("main",cfg.defaultPreset);assertEquals("",cfg.activePreset);assertEquals(setOf("main"),HostMoaRequest(expected,cfg).body("worker")["presets"]!!.jsonObject.keys);assertTrue(runCatching {cfg.without("main")}.isFailure)
        val other=expected.config.without("main");assertEquals("archive",other.defaultPreset);assertEquals("archive",other.activePreset)
    }
    @Test fun preflightRequiresFullVisibleMapAndPublicMetadataOfNovelChoices() {
        val expected=snapshot();val req=HostMoaRequest(expected,expected.config);assertTrue(req.same(snapshot()));assertFalse(req.same(snapshot(expected.config.copy(activePreset=""))));val p=expected.config.presets.getValue("main");assertFalse(req.same(snapshot(expected.config.copy(presets=expected.config.presets+("main" to p.copy(fanout="per_iteration"))))))
    }
    @Test fun normalizedSuccessIsCapturedWithCompactPublicFingerprintsAndWrongStatusCannotAck() {
        val expected=snapshot();val desired=expected.config.copy(activePreset="");val request=HostMoaRequest(expected,desired);val reply=request.reply(JsonObject(raw()+mapOf("ok" to JsonPrimitive(true))));assertEquals(expected.config,reply.config);assertEquals(JsonPrimitive(true),reply.summary["normalized"]);assertEquals(JsonPrimitive(2),reply.summary["reported_presets"]);assertFalse(reply.summary.toString().contains("private"));assertFalse(reply.summary.toString().contains("reference_models"));assertEquals(64,reply.summary["reported_sha256"]!!.jsonPrimitive.content.length)
        for(ok in listOf(JsonPrimitive(false),JsonPrimitive("true"),JsonNull)) assertTrue(runCatching {request.reply(JsonObject(raw()+mapOf("ok" to ok)))}.isFailure)
    }
    @Test fun expandedDraftCannotExceedEncodedWriteBoundBeforeAnyClaim() {
        val base=snapshot();val route=HostModelRoute("fixture","x".repeat(1024));val preset=HostMoaPreset(listOf(HostMoaSlot(route)),HostMoaSlot(route));val original=HostMoaConfig(mapOf("main" to preset),"main","","full");val expected=HostMoaSnapshot(base.catalog,original);val large=preset.copy(references=List(32) {HostMoaSlot(route)});val desired=HostMoaConfig((0 until 128).associate {"preset$it" to large},"preset0","","full");assertTrue(runCatching {HostMoaRequest(expected,desired)}.isFailure)
    }
    @Test fun fingerprintsAreStableAcrossMapOrderingAndDiscardedPrivateFields() {
        val config=HostMoaConfig.parse(raw());assertEquals(config.fingerprint(),HostMoaConfig(config.presets.toList().reversed().toMap(),config.defaultPreset,config.activePreset,config.privacy).fingerprint());val changed=JsonObject(raw()+mapOf("api_key" to JsonPrimitive("other-private")));assertEquals(config.fingerprint(),HostMoaConfig.parse(changed).fingerprint())
    }
}
