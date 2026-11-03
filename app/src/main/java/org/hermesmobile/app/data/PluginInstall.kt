package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class PluginInstallRequest(inspection:PluginInspection,val catalog:PluginCatalogEntry?,val enable:Boolean) {
    val inspection=PluginInspection(inspection.source,inspection.ok,inspection.agent,inspection.desktop,inspection.agentName,inspection.desktopName,inspection.sha,Collections.unmodifiableList(inspection.warnings.toList()),inspection.insecure)
    init {require(supported(this.inspection,catalog))}
    val name get()=inspection.agentName
    fun same(other:PluginInspection)=other.ok && inspection.source.kind==other.source.kind && inspection.source.value==other.source.value && inspection.agent==other.agent && inspection.desktop==other.desktop && inspection.agentName==other.agentName && inspection.desktopName==other.desktopName && inspection.sha==other.sha && inspection.warnings==other.warnings && inspection.insecure==other.insecure
    fun params(profile:String)=buildJsonObject {
        GatewayCronInventory.requireProfile(profile);put("profile",profile);put("action","install");put("force",false);put("enable",enable)
        put("inspected_sha",inspection.sha) // Passing ref would disable the backend's inspected_sha guard.
        put(if(inspection.source.kind==PluginSourceKind.Catalog) "catalog_name" else "identifier",inspection.source.value)
    }
    fun summary()=buildJsonObject {put("approved_commit",inspection.sha);put("requested_enable",enable);put("source_kind",inspection.source.kind.name)}
    fun acknowledge(j:JsonObject) {require(j["ok"]==JsonPrimitive(true) && j["plugin_name"]==JsonPrimitive(name) && j["enabled"]==JsonPrimitive(enable))}
    fun duplicate(inventory:GatewayPluginInventory)=inventory.plugins.any {it.name.equals(name,true) || it.key.equals(name,true) || it.key.endsWith("/$name",true)}
    override fun toString()="PluginInstallRequest(redacted)"
    companion object {
        fun supported(i:PluginInspection,c:PluginCatalogEntry?)=i.ok && i.agent==true && i.insecure==false && i.sha.matches(Regex("[a-f0-9]{40}")) && SkillProtocol.name(i.agentName) &&
            (if(i.source.kind==PluginSourceKind.Catalog) c!=null && c.name==i.source.value else c==null)
    }
}
class PluginLiveServerReport(val name:String,val connected:Boolean?,val tools:List<String>,val hasError:Boolean)
class PluginInstallReport(val name:String,val restartRequired:Boolean?,val gatewayReloaded:Boolean?,val dependencyCount:Int?,val missingEnv:List<String>,val activated:Map<String,List<String>>,val deferred:Map<String,List<String>>,val servers:List<PluginLiveServerReport>,val skills:List<String>,val missingDependencyCount:Int?,val missingIn:List<String>,val activationReported:Boolean,val liveReported:Boolean) {
    fun summary()=buildJsonObject {
        put("details_available",true);put("restart_required",restartRequired?.let(::JsonPrimitive) ?: JsonNull);put("gateway_reloaded",gatewayReloaded?.let(::JsonPrimitive) ?: JsonNull)
        put("python_dependency_count",dependencyCount?.let(::JsonPrimitive) ?: JsonNull);put("missing_env_keys",JsonArray(missingEnv.map(::JsonPrimitive)))
        put("activated_counts",buildJsonObject {activated.forEach {(k,v)->put(k,v.size)}});put("deferred_counts",buildJsonObject {deferred.forEach {(k,v)->put(k,v.size)}})
        put("activation_reported",activationReported);put("live_reported",liveReported);put("reported_live_servers",if(liveReported) JsonPrimitive(servers.size) else JsonNull);put("reported_connected_servers",if(liveReported) JsonPrimitive(servers.count {it.connected==true}) else JsonNull);put("reported_live_skills",if(liveReported) JsonPrimitive(skills.size) else JsonNull)
        put("missing_dependency_count",missingDependencyCount?.let(::JsonPrimitive) ?: JsonNull);put("missing_in",JsonArray(missingIn.map(::JsonPrimitive)))
    }
    override fun toString()="PluginInstallReport(redacted)"
    companion object {
        private fun flag(j:JsonObject,k:String):Boolean? {val v=j[k];require(v==null || v is JsonNull || v==JsonPrimitive(true) || v==JsonPrimitive(false));return (v as? JsonPrimitive)?.booleanOrNull}
        private fun names(j:JsonObject,k:String,max:Int=256):List<String> {
            val v=j[k];if(v==null || v is JsonNull) return emptyList();require(v is JsonArray && v.size<=max)
            return Collections.unmodifiableList(v.map {require(it is JsonPrimitive && it.isString);it.content.also {s->require(s.isNotBlank() && s.length<=512 && s.none(Char::isISOControl))}})
        }
        private fun opaqueCount(j:JsonObject,k:String):Int? {val v=j[k];if(v==null || v is JsonNull) return null;require(v is JsonArray && v.size<=256 && v.all {it is JsonPrimitive && it.isString && it.content.length<=8192});return v.size}
        private fun groups(j:JsonObject,k:String):Map<String,List<String>> {val v=j[k];if(v==null || v is JsonNull) return emptyMap();require(v is JsonObject && v.size<=32);return Collections.unmodifiableMap(v.mapValues {(key,_)->require(key.matches(Regex("[A-Za-z0-9_.-]{1,128}")));names(v,key)})}
        fun parse(j:JsonObject,name:String,requireInstallDetails:Boolean=true):PluginInstallReport {
            val activation=j["activation"]?.takeUnless {it is JsonNull}?.jsonObject ?: JsonObject(emptyMap())
            val live=activation["live_now"]?.takeUnless {it is JsonNull}?.jsonObject ?: JsonObject(emptyMap())
            if(requireInstallDetails) require(j["missing_env"] is JsonArray && j["python_dependencies"] is JsonArray)
            val env=names(j,"missing_env",128).also {require(it.all {key->key.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,127}"))})}
            require(live["mcp_servers"]==null || live["mcp_servers"] is JsonArray);require(live["skills"]==null || live["skills"] is JsonArray)
            val rows=live["mcp_servers"] as? JsonArray ?: JsonArray(emptyList());require(rows.size<=128)
            val servers=Collections.unmodifiableList(rows.map {v->val row=v.jsonObject;val n=row.text("name");require(n.isNotBlank() && n.length<=512 && n.none(Char::isISOControl));PluginLiveServerReport(n,flag(row,"connected"),names(row,"tools",1024),(row["error"] as? JsonPrimitive)?.contentOrNull?.isNotBlank()==true)})
            val skills=live["skills"] as? JsonArray ?: JsonArray(emptyList());require(skills.size<=256)
            val skillNames=Collections.unmodifiableList(skills.map {v->v.jsonObject.text("name").also {require(it.isNotBlank() && it.length<=512 && it.none(Char::isISOControl))}})
            val missingIn=names(activation,"missing_in",16).also {require(it.all {x->x in setOf("gateway","backend")})}
            return PluginInstallReport(name,flag(j,"restart_required"),flag(j,"gateway_reloaded"),opaqueCount(j,"python_dependencies"),env,groups(activation,"activated_now"),groups(activation,"deferred"),servers,skillNames,opaqueCount(activation,"missing_dependencies"),missingIn,j["activation"] is JsonObject,activation["live_now"] is JsonObject)
        }
    }
}
