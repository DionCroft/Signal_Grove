package org.hermesmobile.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.HermesApiClient

class AutomaticModelUnavailable:Exception("A fresh, unambiguous serving model could not be verified.")
class AutomaticModelConflict:Exception("The observed session lock differs from the serving model.")

data class RuntimePresets(val reasoning:Boolean,val canDisable:Boolean,val efforts:List<String>,val tiers:List<String>) {
    fun permits(choice:RuntimeChoice)=
        (choice.reasoning==null || choice.reasoning==true && reasoning || choice.reasoning==false && canDisable) &&
        (choice.effort.isBlank() || choice.effort in efforts) &&
        (choice.serviceTier.isBlank() || choice.serviceTier in tiers)
    companion object {
        // Hermes' API request decoder accepts this ladder, then clamps it for the provider.
        val effortLadder=listOf("minimal","low","medium","high","xhigh","max","ultra")
        fun forModel(inventory:ModelInventory?,model:String,provider:String):RuntimePresets {
            val p=inventory?.providers?.singleOrNull {it.slug==provider && it.authenticated!=false &&
                model in it.models && model !in it.unavailableModels}
            val caps=p?.capabilities?.get(model) as? JsonObject
            val reasoning=caps?.get("reasoning")==JsonPrimitive(true)
            val fast=caps?.get("fast")==JsonPrimitive(true)
            val tiers=buildList {
                if(fast) add("priority")
                if(fast && caps?.get("ultrafast")==JsonPrimitive(true)) add("ultrafast")
                if(caps?.get("daybreak")==JsonPrimitive(true)) add("daybreak")
            }
            return RuntimePresets(reasoning,(reasoning && caps?.get("can_disable_reasoning")!=JsonPrimitive(false)) || caps?.get("reasoning")==JsonPrimitive(false),
                if(reasoning) effortLadder else emptyList(),tiers)
        }
    }
}
data class AutomaticRuntimeResult(val choice:RuntimeChoice,val runtime:JsonObject?=null,val catalog:JsonObject?=null)

/** Resolve only a new request. Saved pending requests are never re-resolved or replayed here. */
object AutomaticRuntime {
    const val MAX_AGE_MS=30_000L
    fun resolve(choice:RuntimeChoice,runtime:JsonObject,catalog:JsonObject,now:Long,knownLock:RuntimeChoice?=null):RuntimeChoice {
        require(choice.followServing)
        val observed=try {ModelRuntimeStatus.parse(runtime)} catch(_:Exception) {throw AutomaticModelUnavailable()}
        if(now !in 1..Long.MAX_VALUE-30_000 || observed.status!="ready" || observed.observedAt>now+30_000 || now-observed.observedAt>=MAX_AGE_MS)
            throw AutomaticModelUnavailable()
        val serving=observed.verifiedModels.singleOrNull() ?: throw AutomaticModelUnavailable()
        val rawProviders=catalog["providers"] as? JsonArray ?: throw AutomaticModelUnavailable()
        if(rawProviders.size>500) throw AutomaticModelUnavailable()
        val target=rawProviders.mapNotNull {it as? JsonObject}.singleOrNull {it["slug"]==JsonPrimitive(serving.provider)} ?: throw AutomaticModelUnavailable()
        target["authenticated"]?.takeUnless {it==JsonNull}?.let {value->
            if(value !is JsonPrimitive || value.isString || value.booleanOrNull==null) throw AutomaticModelUnavailable()
        }
        val models=target["models"] as? JsonArray ?: throw AutomaticModelUnavailable()
        if(models.size>20000 || models.any {it !is JsonPrimitive || !it.isString}) throw AutomaticModelUnavailable()
        val inventory=ModelInventory.parse(catalog)
        val provider=inventory.providers.singleOrNull {it.slug==serving.provider} ?: throw AutomaticModelUnavailable()
        if(provider.authenticated==false || serving.model !in provider.models || serving.model in provider.unavailableModels)
            throw AutomaticModelUnavailable()
        if(knownLock!=null && (knownLock.model!=serving.model || knownLock.provider!=serving.provider))
            throw AutomaticModelConflict()
        val resolved=choice.copy(model=serving.model,provider=serving.provider,followServing=false)
        try {resolved.validate()} catch(_:IllegalArgumentException) {throw AutomaticModelUnavailable()}
        if(!RuntimePresets.forModel(inventory,serving.model,serving.provider).permits(resolved)) throw AutomaticModelUnavailable()
        return resolved
    }
    suspend fun beforeNewTurn(choice:RuntimeChoice,caps:HermesCapabilities?,api:HermesApiClient,
                              current:()->Boolean,now:()->Long=System::currentTimeMillis,
                              knownLock:RuntimeChoice?=null):AutomaticRuntimeResult {
        choice.validate()
        if(!choice.followServing) return AutomaticRuntimeResult(choice)
        fun scope() {if(!current()) throw CancellationException("Model selection scope changed.")}
        scope()
        // Standard Hermes remains server-managed when it advertises no serving-status extension.
        // The app supplies no preselected provider/model in this case.
        if(caps?.supportsDiscovery(DiscoveryResource.RuntimeModels)!=true)
            return AutomaticRuntimeResult(choice.copy(followServing=false))
        if(caps.has("model_options")!=true && !caps.endpoints.containsKey("model_options")) throw AutomaticModelUnavailable()
        val runtime=api.discovery(DiscoveryResource.RuntimeModels,false);scope()
        val catalog=api.discovery(DiscoveryResource.Models,false);scope()
        return AutomaticRuntimeResult(resolve(choice,runtime,catalog,now(),knownLock),runtime,catalog)
    }
}
