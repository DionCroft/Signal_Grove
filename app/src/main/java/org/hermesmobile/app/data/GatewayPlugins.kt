package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class GatewayPluginInvalid:Exception()
data class PluginServerReport(val name:String,val state:String,val sentence:String)
class ManagedPlugin(val name:String,val key:String,val version:String,val description:String,val source:String,val status:String,val portable:Boolean?,val desktopHalf:Boolean?,val catalog:String,val tier:String,val installedSha:String,val catalogSha:String,val pinnedSha:String,val updateAvailable:Boolean?,servers:List<PluginServerReport>,tools:List<String>) {
    val servers:List<PluginServerReport> = Collections.unmodifiableList(servers.toList())
    val tools:List<String> = Collections.unmodifiableList(tools.toList())
    override fun toString()="ManagedPlugin(redacted)"
}
class GatewayPluginInventory private constructor(val requestedProfile:String,val plugins:List<ManagedPlugin>,val userCount:Int,val bundledCount:Int) {
    override fun toString()="GatewayPluginInventory(redacted)"
    companion object {
        private fun string(j:JsonObject,k:String,max:Int,required:Boolean=false):String {
            val v=j[k];require(v==null || v is JsonNull || v is JsonPrimitive && v.isString)
            return (v as? JsonPrimitive)?.contentOrNull.orEmpty().also {require(it.length<=max && (!required || it.isNotBlank()));if(k!="description" && k!="sentence") require(it.none(Char::isISOControl))}
        }
        private fun flag(j:JsonObject,k:String):Boolean? {val v=j[k];require(v==null || v is JsonNull || v==JsonPrimitive(true) || v==JsonPrimitive(false));return (v as? JsonPrimitive)?.booleanOrNull}
        private fun count(j:JsonObject,k:String):Int {val v=j[k] as? JsonPrimitive ?: error("Missing count");require(!v.isString);return (v.intOrNull ?: error("Invalid count")).also {require(it in 0..1000)}}
        fun parse(j:JsonObject,profile:String):GatewayPluginInventory=try {
            GatewayCronInventory.requireProfile(profile)
            val array=j["plugins"] as? JsonArray ?: error("Missing plugins");require(array.size<=1000)
            val rows=array.map {value ->val row=value.jsonObject;require(row.toString().length<=131072)
                val servers=(row["servers"] as? JsonArray ?: error("Missing server reports")).also {require(it.size<=128)}.map {v->val item=v.jsonObject;PluginServerReport(string(item,"name",512,true),string(item,"state",128,true),string(item,"sentence",4096))}.also {require(it.map(PluginServerReport::name).distinct().size==it.size)}
                val tools=(row["tools"] as? JsonArray ?: JsonArray(emptyList())).also {require(it.size<=2048)}.map {v->require(v is JsonPrimitive && v.isString);v.content.also {require(it.length<=512 && it.none(Char::isISOControl))}}.also {require(it.distinct().size==it.size)}
                ManagedPlugin(string(row,"name",256,true),string(row,"key",512,true),string(row,"version",256),string(row,"description",8192),string(row,"source",256,true),string(row,"status",256,true),flag(row,"portable"),flag(row,"has_desktop_half"),string(row,"catalog_name",256),string(row,"catalog_tier",128),string(row,"installed_sha",256),string(row,"catalog_sha",256),string(row,"pinned_sha",256),flag(row,"update_available"),servers,tools)
            }.also {require(it.map(ManagedPlugin::key).distinct().size==it.size)}
            val user=count(j,"user_count");val bundled=count(j,"bundled_count");require(user+bundled==rows.size && bundled==rows.count {it.source=="bundled"})
            GatewayPluginInventory(profile,Collections.unmodifiableList(rows),user,bundled)
        } catch(e:Exception) {throw GatewayPluginInvalid()}
    }
}
