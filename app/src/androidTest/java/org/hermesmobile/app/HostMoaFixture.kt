package org.hermesmobile.app

import kotlinx.serialization.json.*

object HostMoaFixture {
    fun preset(model:String=HostMainModelFixture.old,fanout:String="user_turn",bespoke:Boolean=false)=buildJsonObject {
        put("reference_models",buildJsonArray {add(buildJsonObject {put("provider",HostMainModelFixture.provider);put("model",model);put("enabled",true);put("reasoning_effort",if(bespoke) "fast" else "low");put("api_key","private-ref-key")})})
        put("aggregator",buildJsonObject {put("provider",HostMainModelFixture.provider);put("model",HostMainModelFixture.old);put("reasoning_effort","high");put("api_key","private-agg-key")});put("enabled",true);put("reference_temperature",JsonNull);put("aggregator_temperature",JsonNull);put("reference_timeout",JsonNull);put("degraded_reference_policy","loud");put("fanout",fanout);put("private_metadata","private-preset-extension")
    }
    fun normalized(presets:JsonObject,default:String="main",active:String="archive",privacy:String="full")=buildJsonObject {
        put("presets",presets);put("default_preset",default);put("active_preset",active);put("privacy_filter",privacy);presets[default]!!.jsonObject.filterKeys {it in setOf("reference_models","aggregator","enabled","reference_temperature","aggregator_temperature","reference_timeout","degraded_reference_policy","fanout")}.forEach {(k,v)->put(k,v)};put("api_key","private-config-key")
    }
    fun initial(changed:Boolean=false,bespoke:Boolean=false)=normalized(buildJsonObject {put("main",preset(fanout=if(changed) "per_iteration" else "user_turn",bespoke=bespoke));put("archive",preset())})
    fun after(body:JsonObject,different:Boolean=false)=normalized(body["presets"]!!.jsonObject,body["default_preset"]!!.jsonPrimitive.content,if(different) "archive" else body["active_preset"]!!.jsonPrimitive.content)
    fun catalog(changed:Boolean=false,blocked:Boolean=false):JsonObject {
        val base=HostMainModelFixture.catalog(changed=changed,blocked=blocked)
        return JsonObject(base+mapOf("providers" to JsonArray(base["providers"]!!.jsonArray+buildJsonObject {put("slug","moa");put("name","Fixture MoA");put("authenticated",true);put("models",buildJsonArray {add("main")})})))
    }
}
