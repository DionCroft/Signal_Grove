package org.hermesmobile.app

import kotlinx.serialization.json.*

object PluginSettingsFixture {
    const val privateValue="private-setting-value"
    const val privateDefault="private-setting-default"
    fun value(mode:String):JsonElement=when(mode) {"number"->JsonPrimitive(0);"boolean"->JsonPrimitive(false);"enum"->JsonPrimitive("second");"json"->buildJsonObject {put("route",privateValue);put("enabled",false)};else->JsonPrimitive(privateValue)}
    fun type(mode:String)=if(mode in setOf("number","boolean","enum","json")) mode else "string"
    fun key(mode:String)=if(mode=="nested") "retry.policy" else "endpoint"
    fun inventory(mode:String="",after:Boolean=false):JsonObject {
        val base=PluginUpdateFixture.inventory();val row=base["plugins"]!!.jsonArray.single().jsonObject
        val field=buildJsonObject {put("key",key(mode));put("type",if(mode=="schema-changed") "number" else type(mode));put("label","Fixture endpoint");put("description","Private host setting");put("required",true);put("default",privateDefault);put("value",if(mode=="changed" || after && mode !in setOf("readback","nested","cancel-readback")) value(mode) else JsonNull);if(mode=="enum") put("choices",buildJsonArray {add("first");add("second")})}
        val secret=buildJsonObject {put("key","api_key");put("type","secret");put("label","Credential presence");put("description","Fixture secret");put("required",false);put("env","FIXTURE_PLUGIN_SECRET");put("has_value",true)}
        return JsonObject(base+mapOf("plugins" to JsonArray(listOf(JsonObject(row+mapOf("settings_schema" to JsonArray(listOf(field,secret))))))))
    }
}
