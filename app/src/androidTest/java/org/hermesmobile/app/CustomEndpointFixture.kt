package org.hermesmobile.app

import kotlinx.serialization.json.*
object CustomEndpointFixture {
    const val id="Local.Example:8443"
    const val name="Private endpoint fixture"
    const val model="private-model-a"
    const val url="https://endpoint.test/private/v1?key=private-url-fixture"
    const val inline="existing-endpoint-inline-fixture"
    const val header="existing-endpoint-header-fixture"
    const val preview="endpoint-preview-fixture"
    const val fresh="fresh-endpoint-key-fixture"
    const val newId="new-endpoint"
    fun provider(name:String=this.name,url:String=this.url,model:String=this.model)=buildJsonObject {put("name",name);put("base_url",url);put("model",model);put("api_key",inline);put("api_key_env","PRIVATE_ENDPOINT_ALIAS");put("extra_headers",buildJsonObject {put("X-Private",header)});put("models",buildJsonObject {put(model,buildJsonObject {put("context_length",4096)})});put("api_mode","anthropic_messages");put("transport","messages");put("discover_models",true);put("context_length",4096)}
    fun config(kind:String="provider",change:String="")=buildJsonObject {
        put("providers",buildJsonObject {if(kind=="provider") put(id,if(change=="private") JsonObject(provider()+mapOf("extra_headers" to buildJsonObject {put("X-Private","rotated-header-fixture")})) else provider());put("hidden-provider",buildJsonObject {put("key_env","HIDDEN_ENDPOINT_REF")});if(change=="collision") put(newId,buildJsonObject {put("key_env","PRIVATE_COLLISION_REF")})})
        put("custom_providers",if(kind=="legacy") buildJsonArray {add(JsonObject(provider()+mapOf("name" to JsonPrimitive("Legacy Endpoint"))))} else JsonArray(emptyList()))
        put("unrelated","not-retained-endpoint-fixture")
    }
    fun row(id:String=this.id,source:String="providers",name:String=this.name,url:String=this.url,model:String=this.model,current:Boolean=false,hasKey:Boolean=true,mode:String="anthropic_messages",context:Long?=4096,discover:Boolean=true,models:List<String> = listOf(this.model,"catalog-model-b"))=buildJsonObject {put("id",id);put("name",name);put("base_url",url);put("model",model);put("models",JsonArray(models.map(::JsonPrimitive)));put("api_mode",mode);put("context_length",context?.let(::JsonPrimitive) ?: JsonNull);put("discover_models",discover);put("has_api_key",hasKey);put("api_key_preview",preview);put("is_current",current);put("source",source)}
    fun route(provider:String="openrouter",model:String="baseline-model",url:String="https://primary.test/v1")=buildJsonObject {put("provider",provider);put("model",model);put("base_url",url)}
    fun metadata(kind:String="provider")=buildJsonObject {put("endpoints",JsonArray(listOf(when(kind) {"legacy"->row("legacy-endpoint","custom_providers","Legacy Endpoint");"direct"->row("custom","direct-config",current=true);else->row()})));put("current",if(kind=="direct") route("custom",model,url) else route())}
    fun ack(id:String?=null,provider:String?=null,model:String?=null)=buildJsonObject {put("ok",true);id?.let {put("id",it)};provider?.let {put("provider",it)};model?.let {put("model",it)}}
    fun activated(before:JsonObject,id:String)=buildJsonObject {
        val rows=(before["endpoints"] as JsonArray).map {value->val j=value.jsonObject;JsonObject(j+mapOf("is_current" to JsonPrimitive(j["id"]==JsonPrimitive(id))))};val row=rows.single {it["id"]==JsonPrimitive(id)}
        put("endpoints",JsonArray(rows));put("current",route(id,row["model"]!!.jsonPrimitive.content,row["base_url"]!!.jsonPrimitive.content))
    }
    fun savedConfig(before:JsonObject,body:JsonObject):JsonObject {
        val id=body["id"]!!.jsonPrimitive.content;val providers=(before["providers"] as? JsonObject).orEmpty().toMutableMap();val legacy=(before["custom_providers"] as? JsonArray).orEmpty().toMutableList()
        val index=legacy.indexOfFirst {it is JsonObject && org.hermesmobile.app.data.CustomEndpointPolicy.slug(it["name"]?.jsonPrimitive?.content.orEmpty())==id}
        val old=(providers[id] as? JsonObject) ?: if(index>=0) legacy.removeAt(index).jsonObject else JsonObject(emptyMap());val entry=old.toMutableMap()
        for(key in listOf("name","base_url","model","discover_models")) entry[key]=body.getValue(key)
        if(body.containsKey("api_mode")) {entry.remove("api_mode");entry.remove("transport");if(body["api_mode"]!=JsonPrimitive("")) entry["api_mode"]=body.getValue("api_mode")}
        val credential=body["api_key"] as? JsonPrimitive
        if(credential!=null || entry.containsKey("api_key")) {entry.remove("api_key");if(credential?.content=="") entry.remove("key_env") else entry["key_env"]=JsonPrimitive(org.hermesmobile.app.data.CustomEndpointPolicy.keySlot(id))}
        body["context_length"]?.let {entry["context_length"]=it}
        providers[id]=JsonObject(entry)
        return JsonObject(before+mapOf("providers" to JsonObject(providers),"custom_providers" to JsonArray(legacy)))
    }
    fun saved(before:JsonObject,body:JsonObject)=buildJsonObject {
        val id=body["id"]!!.jsonPrimitive.content;val key=body["api_key"] as? JsonPrimitive;val mode=(body["api_mode"] as? JsonPrimitive)?.content ?: "anthropic_messages"
        val models=((body["models"] as? JsonArray).orEmpty().map {it.jsonPrimitive.content}+body["model"]!!.jsonPrimitive.content+listOf(this@CustomEndpointFixture.model,"catalog-model-b")).distinct()
        val replacement=row(id,"providers",body["name"]!!.jsonPrimitive.content,body["base_url"]!!.jsonPrimitive.content,body["model"]!!.jsonPrimitive.content,body["make_default"]==JsonPrimitive(true),key==null || key.content.isNotEmpty(),mode,(body["context_length"] as? JsonPrimitive)?.longOrNull ?: 4096,body["discover_models"]==JsonPrimitive(true),models)
        put("endpoints",JsonArray((before["endpoints"] as JsonArray).filter {it.jsonObject["id"]!=JsonPrimitive(id)}+replacement));put("current",if(body["make_default"]==JsonPrimitive(true)) route(id,replacement["model"]!!.jsonPrimitive.content,replacement["base_url"]!!.jsonPrimitive.content) else before["current"]!!)
    }
}
