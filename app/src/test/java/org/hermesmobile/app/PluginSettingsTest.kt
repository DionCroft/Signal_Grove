package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class PluginSettingsTest {
    private val owner=ManagementTarget("a".repeat(64),"https://example.test/prefix/","worker","password · fixture")
    private val home=PluginHome.project(JsonPrimitive("/fixture-settings"))
    private fun field(type:String="string",key:String="endpoint",value:JsonElement=JsonPrimitive("private-original-value"),choices:JsonArray=JsonArray(listOf(JsonPrimitive("first"),JsonPrimitive("second"))))=buildJsonObject {put("key",key);put("type",type);put("label","Fixture field");put("description","Fixture schema");put("required",true);if(type=="secret") {put("env","FIXTURE_API_KEY");put("has_value",true)} else {put("value",value);if(type=="enum") put("choices",choices)}}
    private fun snapshot(fields:JsonArray=JsonArray(listOf(field())))=PluginSettingsSnapshot.parse(buildJsonObject {put("plugins",buildJsonArray {add(buildJsonObject {put("name","Fixture");put("key","tools/fixture");put("version","1");put("source","user");put("status","enabled");put("servers",JsonArray(emptyList()));put("settings_schema",fields)})});put("user_count",1);put("bundled_count",0)},owner,home,"tools/fixture")
    @Test fun exactSingleTypedSettingAndNoInventedGuardFields() {
        val s=snapshot();val request=PluginSettingsRequest(s,s.fields.single(),JsonPrimitive("private-new-value"));val params=request.params("worker")
        assertEquals(setOf("profile","action","key","values"),params.keys);assertEquals(JsonPrimitive("settings"),params["action"]);assertEquals(JsonPrimitive("tools/fixture"),params["key"]);assertEquals(buildJsonObject {put("endpoint","private-new-value")},params["values"])
        assertFalse(request.summary().toString().contains("private"));assertFalse(request.toString().contains("private"));assertTrue(runCatching {request.params("other")}.isFailure)
    }
    @Test fun declaredTypeValidationDistinguishesStringsNumbersBooleansJsonAndEnums() {
        val string=PluginSettingField.parse(field());assertEquals(JsonPrimitive(""),string.input(""));assertNull(string.input("x".repeat(8193)))
        val number=PluginSettingField.parse(field("number",value=JsonPrimitive(2)));assertEquals(JsonPrimitive(0),number.input("0"));assertNotNull(number.input("-1.5"));for(v in listOf("true","null","\"2\"","1e309","NaN")) assertNull(v,number.input(v))
        val bool=PluginSettingField.parse(field("boolean",value=JsonPrimitive(false)));assertEquals(JsonPrimitive(false),bool.input("false"));assertNull(bool.input("0"))
        val json=PluginSettingField.parse(field("json",value=JsonArray(emptyList())));assertNotNull(json.input("{\"active\":false}"));assertNotNull(json.input("[]"));assertNull(json.input("null"));assertNull(json.input("2"))
        val enum=PluginSettingField.parse(field("enum",value=JsonPrimitive("first")));assertNotNull(enum.input("second"));assertNull(enum.input("unknown"))
    }
    @Test fun secretProjectionHasPresenceOnlyAndCannotBecomeASettingRequest() {
        val s=snapshot(JsonArray(listOf(field("secret"))));val f=s.fields.single();assertEquals("FIXTURE_API_KEY",f.env);assertEquals(true,f.hasValue);assertEquals(JsonNull,f.value);assertNull(f.input("private-secret-value"));assertTrue(runCatching {PluginSettingsRequest(s,f,JsonPrimitive("secret"))}.isFailure)
        assertTrue(runCatching {PluginSettingField.parse(JsonObject(field("secret")+mapOf("value" to JsonPrimitive("must-not-import"))))}.isFailure)
    }
    @Test fun relativeKeyGrammarExcludesCoreRootsAndTraversalButSupportsDottedKeys() {
        for(k in listOf("","settings.foo","Plugins.foo","security","model.x","a..b","../value","a/b","a\\b","_bad","a.")) assertFalse(k,PluginSettingField.relative(k))
        for(k in listOf("endpoint","retry.policy","retry_2.max-attempts","0value")) assertTrue(k,PluginSettingField.relative(k))
    }
    @Test fun typedAcknowledgementRequiresCanonicalKeyAndExactlyTheRequestedWrittenKey() {
        val s=snapshot();val r=PluginSettingsRequest(s,s.fields.single(),JsonPrimitive("new"));val good=buildJsonObject {put("ok",true);put("name","tools/fixture");put("written",buildJsonArray {add("endpoint")});put("plugin","optional-malformed-private-projection")};r.acknowledge(good)
        for(j in listOf(JsonObject(good+mapOf("ok" to JsonPrimitive("true"))),JsonObject(good+mapOf("name" to JsonPrimitive("fixture"))),JsonObject(good+mapOf("written" to JsonArray(emptyList()))),JsonObject(good+mapOf("written" to JsonArray(listOf(JsonPrimitive("endpoint"),JsonPrimitive("extra"))))))) assertTrue(runCatching {r.acknowledge(j)}.isFailure)
    }
    @Test fun freshSchemaValuesAndOwnerArePartOfTheReviewIdentity() {
        val s=snapshot();assertTrue(s.same(snapshot()));assertFalse(s.same(snapshot(JsonArray(listOf(field(value=JsonPrimitive("changed")))))))
        val changed=PluginSettingsSnapshot(ManagementTarget(owner.apiScope,owner.url,"other",owner.actor),s.plugin,s.homeId,s.fields);assertFalse(s.same(changed));assertTrue(runCatching {PluginSettingsRequest(s,PluginSettingField.parse(field()),JsonPrimitive("new"))}.isFailure)
    }
    @Test fun missingSchemaUnknownTypeDuplicateKeysAndUnsafeKeysAreUnavailable() {
        for(fields in listOf(JsonArray(listOf(field("future"))),JsonArray(listOf(field(),field())),JsonArray(listOf(field(key="settings.global"))),JsonArray(List(129) {field(key="field$it")}))) assertTrue(runCatching {snapshot(fields)}.exceptionOrNull() is PluginSettingsUnavailable)
        assertTrue(snapshot(JsonArray(emptyList())).fields.isEmpty())
    }
    @Test fun valueBoundsCoverDepthNodeCountInvalidNumbersAndNulWithoutSavingHashes() {
        var deep:JsonElement=JsonPrimitive(1);repeat(18) {deep=JsonArray(listOf(deep))};assertFalse(PluginSettingField.bounded(deep));assertFalse(PluginSettingField.bounded(JsonArray(List(2049) {JsonPrimitive(1)})));assertFalse(PluginSettingField.bounded(JsonPrimitive("bad\u0000value")));assertFalse(PluginSettingField.bounded(JsonPrimitive(Double.POSITIVE_INFINITY)))
    }
    @Test fun readbackUsesTypedValueAndNeverConfirmsDottedProjectionEvenWhenItMatches() {
        val s=snapshot();val r=PluginSettingsRequest(s,s.fields.single(),JsonPrimitive("new"));assertFalse(r.observed(s));assertTrue(r.observed(snapshot(JsonArray(listOf(field(value=JsonPrimitive("new")))))))
        val dotted=snapshot(JsonArray(listOf(field(key="retry.policy"))));val d=PluginSettingsRequest(dotted,dotted.fields.single(),dotted.fields.single().value);assertFalse(d.observed(dotted));assertEquals(JsonPrimitive(true),d.summary()["nested_readback_unavailable"])
    }
    @Test fun projectedValuesAndRequestsAreDeepCopiedAndCollectionsCannotBeMutated() {
        val source=mutableMapOf<String,JsonElement>("endpoint" to JsonPrimitive("private-original-value"));val f=PluginSettingField.parse(field("json",value=JsonObject(source)));source["endpoint"]=JsonPrimitive("changed");assertEquals(JsonPrimitive("private-original-value"),f.value.jsonObject["endpoint"])
        val s=snapshot(JsonArray(listOf(field("json",value=JsonObject(emptyMap())))));val values=mutableMapOf<String,JsonElement>("endpoint" to JsonPrimitive("private-new-value"));val r=PluginSettingsRequest(s,s.fields.single(),JsonObject(values));values.clear();assertEquals(1,r.value.jsonObject.size);assertTrue(runCatching {(s.fields as MutableList).clear()}.isFailure);assertFalse(s.toString().contains("private"))
    }
}
