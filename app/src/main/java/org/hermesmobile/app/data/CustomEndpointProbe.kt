package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

/** One explicit outbound probe. Stored provider credentials are never copied into this body. */
class CustomEndpointProbeRequest(val expected:CustomEndpointSnapshot,draft:CustomEndpointDraft) {
    val name=draft.name;val baseUrl=draft.baseUrl;val model=draft.model;val apiMode=draft.apiMode.orEmpty()
    val fresh=draft.key==CustomEndpointKey.Fresh;val secret=if(fresh) draft.secret else ""
    fun body()=buildJsonObject {put("name",name);put("base_url",baseUrl);put("model",model);put("api_mode",apiMode);if(fresh) put("api_key",secret)}
    fun same(draft:CustomEndpointDraft)=name==draft.name && baseUrl==draft.baseUrl && model==draft.model && apiMode==draft.apiMode.orEmpty() && fresh==(draft.key==CustomEndpointKey.Fresh) && secret==if(draft.key==CustomEndpointKey.Fresh) draft.secret else ""
    fun summary()=buildJsonObject {put("home_id",expected.homeId);put("operation","Probe");put("fresh_credential_supplied",fresh);put("explicit_api_mode",apiMode.isNotEmpty());put("listener_network_scope",true)}
    fun acknowledge(j:JsonObject)=CustomEndpointProbeAck(CustomEndpointPolicy.flag(j,"ok"),CustomEndpointPolicy.flag(j,"reachable"))
    override fun toString()="CustomEndpointProbeRequest(redacted)"
}
class CustomEndpointProbeAck(val ok:Boolean,val reachable:Boolean) {
    fun summary(request:CustomEndpointProbeRequest)=buildJsonObject {request.summary().forEach {(k,v)->put(k,v)};put("reported_ok",ok);put("reported_reachable",reachable);put("acknowledged",true)}
}
class CustomEndpointProbeModel(val id:String,val canonicalModel:String?,val reasoningEffort:String?) {override fun toString()="CustomEndpointProbeModel(redacted)"}
class CustomEndpointProbeDetails private constructor(val message:String,val resolvedBaseUrl:String?,val transport:String?,models:List<String>,details:List<CustomEndpointProbeModel>) {
    val models:List<String> = Collections.unmodifiableList(models.toList())
    val details:List<CustomEndpointProbeModel> = Collections.unmodifiableList(details.toList())
    override fun toString()="CustomEndpointProbeDetails(redacted)"
    companion object {
        fun parse(j:JsonObject):CustomEndpointProbeDetails?=runCatching {
            fun optional(j:JsonObject,key:String,max:Int):String?=if(j[key]==null || j[key]==JsonNull) null else CustomEndpointPolicy.text(j,key,max)
            val message=optional(j,"message",16384).orEmpty();val url=optional(j,"resolved_base_url",8192);val mode=optional(j,"transport_checked",128)
            fun array(key:String):JsonArray {val v=j[key];if(v==null || v==JsonNull) return JsonArray(emptyList());require(v is JsonArray && v.size<=2048);return v}
            val models=array("models").map {require(it is JsonPrimitive && it.isString && CustomEndpointPolicy.field(it.content,2048,true));it.content}
            val details=array("model_details").map {val row=it.jsonObject;CustomEndpointProbeModel(CustomEndpointPolicy.text(row,"id",2048,true),optional(row,"canonical_model",2048),optional(row,"reasoning_effort",128))}
            require(models.distinct().size==models.size && details.map {it.id}.distinct().size==details.size)
            CustomEndpointProbeDetails(message,url,mode,models,details)
        }.getOrNull()
    }
}
class CustomEndpointProbeReport(val request:CustomEndpointProbeRequest,val ack:CustomEndpointProbeAck,val details:CustomEndpointProbeDetails?,val receipt:ManagementOperation) {override fun toString()="CustomEndpointProbeReport(redacted)"}
