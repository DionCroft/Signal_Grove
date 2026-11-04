package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Locale

/** Explicitly staged metadata, not an automatic adoption of a probe's untrusted response. */
class CustomEndpointModelDetail(id:String,canonicalModel:String?=null,reasoningEffort:String?=null) {
    val id=id.trim()
    val canonicalModel=canonicalModel?.trim()?.takeIf {it.isNotEmpty()}
    val reasoningEffort=reasoningEffort?.trim()?.takeIf {it.isNotEmpty()}
    init {
        require(CustomEndpointPolicy.field(this.id,2048,true))
        require(this.canonicalModel?.let {CustomEndpointPolicy.field(it,2048,true)}!=false)
        require(this.reasoningEffort?.let {CustomEndpointPolicy.field(it,128,true)}!=false)
    }
    fun body()=buildJsonObject {put("id",id);canonicalModel?.let {put("canonical_model",it)};reasoningEffort?.let {put("reasoning_effort",it)}}
    override fun toString()="CustomEndpointModelDetail(redacted)"
}

class CustomEndpointAliasEffect(val selectedModel:String,val effectiveModel:String,val globalEffort:String?) {
    val redirects get()=selectedModel!=effectiveModel
    override fun toString()="CustomEndpointAliasEffect(redacted)"
}

object CustomEndpointAliasPolicy {
    private val efforts=setOf("minimal","low","medium","high","xhigh","max","ultra","none","false","disabled")
    fun effect(model:String,details:List<CustomEndpointModelDetail>):CustomEndpointAliasEffect {
        val selected=details.singleOrNull {it.id==model}
        val canonical=selected?.canonicalModel?.takeIf {it!=model}
        val effort=if(canonical==null) null else selected?.reasoningEffort?.lowercase(Locale.ROOT)?.takeIf {it in efforts}
        return CustomEndpointAliasEffect(model,canonical ?: model,effort)
    }
}

/** Only reasoning overrides and shape flags are retained; unrelated agent settings are discarded. */
class CustomEndpointReasoning private constructor(private val agentShape:Int,private val overridesShape:Int,private val values:JsonObject) {
    val writable get()=agentShape!=2 && overridesShape!=2
    fun value(model:String):JsonElement?=values[model]
    fun same(other:CustomEndpointReasoning)=agentShape==other.agentShape && overridesShape==other.overridesShape && values==other.values
    override fun toString()="CustomEndpointReasoning(redacted)"
    companion object {
        fun parse(agent:JsonElement?):CustomEndpointReasoning {
            fun shape(value:JsonElement?)=if(value==null) 0 else if(value is JsonObject) 1 else 2
            val overrides=(agent as? JsonObject)?.get("reasoning_overrides")
            val values=(overrides as? JsonObject)?.let {Json.parseToJsonElement(it.toString()).jsonObject} ?: JsonObject(emptyMap())
            require(values.toString().length<=500_000)
            return CustomEndpointReasoning(shape(agent),shape(overrides),values)
        }
    }
}
