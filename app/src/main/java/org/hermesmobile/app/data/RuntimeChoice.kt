package org.hermesmobile.app.data

import kotlinx.serialization.json.*

data class RuntimeChoice(val model: String="",val provider: String="",val reasoning: Boolean?=null,val effort: String="",val serviceTier: String="",val followServing:Boolean=false) {
    val isDefault get()=!followServing && model.isBlank() && provider.isBlank() && reasoning==null && effort.isBlank() && serviceTier.isBlank()
    fun validate() {
        require(!followServing || (model.isBlank() && provider.isBlank())) {"Automatic selection cannot pin a provider/model."}
        fun safe(value:String,limit:Int)=value.length<=limit && value.none {it.isISOControl() || Character.getType(it)==Character.FORMAT.toInt()}
        require(safe(model,256) && safe(provider,80) && safe(effort,32) && safe(serviceTier,32)) {"Model settings contain an invalid or oversized ID."}
        require(provider.isBlank() || model.isNotBlank()) {"Choose a model for the requested provider."}
        require(reasoning!=false || effort.isBlank()) {"Clear the reasoning effort when reasoning is disabled."}
    }
    fun fields(): JsonObject {validate();require(!followServing) {"Resolve the serving model before freezing a request."};return buildJsonObject {
        if(model.isNotBlank()) put("model",model)
        if(provider.isNotBlank()) put("provider",provider)
        if(reasoning!=null || effort.isNotBlank() || serviceTier.isNotBlank()) put("model_options",buildJsonObject {
            if(reasoning!=null || effort.isNotBlank()) put("reasoning",buildJsonObject {reasoning?.let {put("enabled",it)};if(effort.isNotBlank()) put("effort",effort)})
            if(serviceTier.isNotBlank()) put("service_tier",serviceTier)
        })
    }}
    fun storedFields():JsonObject {
        validate()
        val fields=copy(followServing=false).fields()
        return if(followServing) JsonObject(fields+("_signal_grove_model_mode" to JsonPrimitive("serving"))) else fields
    }
    companion object {
        fun automatic()=RuntimeChoice(followServing=true)
        fun parse(fields: JsonObject): RuntimeChoice {
            val options=fields["model_options"] as? JsonObject
            val reasoning=options?.get("reasoning") as? JsonObject
            return RuntimeChoice(fields.text("model"),fields.text("provider"),(reasoning?.get("enabled") as? JsonPrimitive)?.booleanOrNull,reasoning?.text("effort").orEmpty(),options?.text("service_tier").orEmpty()).also {it.validate()}
        }
        fun saved(map: String,scope: String)=runCatching {
            val fields=Json.parseToJsonElement(map).jsonObject[scope] as? JsonObject ?: return@runCatching automatic()
            val mode=fields["_signal_grove_model_mode"]
            require(mode==null || mode==JsonPrimitive("serving"))
            parse(fields).copy(followServing=mode!=null).also {it.validate()}
        }.getOrDefault(automatic())
    }
}
