package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class HostModelsUnavailable:Exception()
class HostModelsChanged:Exception()
data class HostModelRoute(val provider:String,val model:String)
data class HostAuxModel(val task:String,val route:HostModelRoute,val endpointPresent:Boolean,val localEndpoint:Boolean?,val reasoning:String?,val label:String,val hint:String,val plugin:String,val inheritFrom:String?,val effective:HostModelRoute?,val effectiveEndpointPresent:Boolean?)
class HostModelProvider(val slug:String,val name:String,val source:String,val authenticated:Boolean?,val current:Boolean?,val userDefined:Boolean?,val totalModels:Int?,val warningPresent:Boolean,models:List<String>) {
    val models:List<String> = Collections.unmodifiableList(models.toList())
    override fun toString()="HostModelProvider(redacted)"
}
class HostModelInventory(val owner:ManagementTarget,val homeId:String,val main:HostModelRoute,auxiliary:List<HostAuxModel>,providers:List<HostModelProvider>,val refreshed:Boolean) {
    val auxiliary:List<HostAuxModel> = Collections.unmodifiableList(auxiliary.toList())
    val providers:List<HostModelProvider> = Collections.unmodifiableList(providers.toList())
    override fun toString()="HostModelInventory(redacted)"
    companion object {
        fun parse(aux:JsonObject,catalog:JsonObject,owner:ManagementTarget,home:PluginHome?,refreshed:Boolean=false):HostModelInventory=try {
            GatewayCronInventory.requireProfile(owner.profile)
            fun text(j:JsonObject,k:String,max:Int=1024,required:Boolean=false,lines:Boolean=false):String {
                val v=j[k];require(v==null || v is JsonNull || v is JsonPrimitive && v.isString)
                return (v as? JsonPrimitive)?.contentOrNull.orEmpty().also {require(it.length<=max && it.none {c->c.isISOControl() && (!lines || c !in "\r\n\t")} && (!required || it.isNotBlank()))}
            }
            fun flag(j:JsonObject,k:String):Boolean? {val v=j[k];require(v==null || v is JsonNull || v==JsonPrimitive(true) || v==JsonPrimitive(false));return (v as? JsonPrimitive)?.booleanOrNull}
            fun route(j:JsonObject):HostModelRoute {require(listOf("provider","model").all {j[it] is JsonPrimitive && j[it]!!.jsonPrimitive.isString});return HostModelRoute(text(j,"provider",256),text(j,"model"))}
            fun array(j:JsonObject,k:String,max:Int):JsonArray {val v=j[k];require(v is JsonArray && v.size<=max);return v}
            val main=route(aux["main"]!!.jsonObject);require(main==route(catalog))
            val tasks=array(aux,"tasks",512).map {v->val j=v.jsonObject
                val effective=j["effective"];require(effective==null || effective is JsonNull || effective is JsonObject)
                val inheritance=text(j,"inherit_from",256).ifBlank {null}
                require(effective !is JsonObject || inheritance!=null)
                HostAuxModel(text(j,"task",256,true),route(j),text(j,"base_url",8192).isNotBlank(),flag(j,"local_endpoint"),text(j,"reasoning_effort",128).ifBlank {null},text(j,"label",512),text(j,"hint",8192,lines=true),text(j,"plugin",256),inheritance,(effective as? JsonObject)?.let(::route),(effective as? JsonObject)?.let {text(it,"base_url",8192).isNotBlank()})
            };require(tasks.map {it.task}.distinct().size==tasks.size)
            var total=0
            val providers=array(catalog,"providers",500).map {v->val j=v.jsonObject
                val models=array(j,"models",20000).map {m->require(m is JsonPrimitive && m.isString && m.content.isNotBlank() && m.content.length<=1024 && m.content.none(Char::isISOControl));m.content}
                total+=models.size;require(total<=40000 && models.distinct().size==models.size)
                val count=j["total_models"];require(count==null || count is JsonNull || count is JsonPrimitive && !count.isString && count.intOrNull?.let {it>=models.size && it<=1000000}==true)
                val warning=j["warning"];require(warning==null || warning is JsonNull || warning is JsonPrimitive && warning.isString)
                HostModelProvider(text(j,"slug",256,true),text(j,"name",512),text(j,"source",128),flag(j,"authenticated"),flag(j,"is_current"),flag(j,"is_user_defined"),(count as? JsonPrimitive)?.intOrNull,(warning as? JsonPrimitive)?.contentOrNull?.isNotBlank()==true,models)
            };require(providers.map {it.slug}.distinct().size==providers.size)
            HostModelInventory(owner,home?.identity() ?: error("Profile home unavailable"),main,tasks,providers,refreshed)
        } catch(e:Exception) {throw HostModelsUnavailable()}
    }
}
