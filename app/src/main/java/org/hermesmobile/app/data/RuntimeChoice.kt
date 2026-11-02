package org.hermesmobile.app.data

import kotlinx.serialization.json.*

data class RuntimeChoice(val model: String="",val provider: String="",val reasoning: Boolean?=null,val effort: String="",val serviceTier: String="") {
    val isDefault get()=model.isBlank() && provider.isBlank() && reasoning==null && effort.isBlank() && serviceTier.isBlank()
    fun validate() {
        fun safe(value:String,limit:Int)=value.length<=limit && value.none {it.isISOControl() || Character.getType(it)==Character.FORMAT.toInt()}
        require(safe(model,256) && safe(provider,80) && safe(effort,32) && safe(serviceTier,32)) {"Model settings contain an invalid or oversized ID."}
        require(provider.isBlank() || model.isNotBlank()) {"Choose a model for the requested provider."}
        require(reasoning!=false || effort.isBlank()) {"Clear the reasoning effort when reasoning is disabled."}
    }
    fun fields(): JsonObject {validate();return buildJsonObject {
        if(model.isNotBlank()) put("model",model)
        if(provider.isNotBlank()) put("provider",provider)
        if(reasoning!=null || effort.isNotBlank() || serviceTier.isNotBlank()) put("model_options",buildJsonObject {
            if(reasoning!=null || effort.isNotBlank()) put("reasoning",buildJsonObject {reasoning?.let {put("enabled",it)};if(effort.isNotBlank()) put("effort",effort)})
            if(serviceTier.isNotBlank()) put("service_tier",serviceTier)
        })
    }}
    companion object {
        fun parse(fields: JsonObject): RuntimeChoice {
            val options=fields["model_options"] as? JsonObject
            val reasoning=options?.get("reasoning") as? JsonObject
            return RuntimeChoice(fields.text("model"),fields.text("provider"),(reasoning?.get("enabled") as? JsonPrimitive)?.booleanOrNull,reasoning?.text("effort").orEmpty(),options?.text("service_tier").orEmpty()).also {it.validate()}
        }
        fun saved(map: String,scope: String)=runCatching {parse(Json.parseToJsonElement(map).jsonObject[scope] as? JsonObject ?: JsonObject(emptyMap()))}.getOrDefault(RuntimeChoice())
    }
}
