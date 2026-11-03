package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

enum class PluginUpdateKind { Updated, Capabilities, Scan }
class PluginUpdateReply(val kind:PluginUpdateKind,val name:String,val unchanged:Boolean?,val pin:String) {
    fun summary()=buildJsonObject {put("outcome",kind.name);put("reported_name",name);put("unchanged",unchanged?.let(::JsonPrimitive) ?: JsonNull);if(kind!=PluginUpdateKind.Updated) put("published",false);if(pin.isNotBlank()) put("reported_pin",pin)}
    override fun toString()="PluginUpdateReply(redacted)"
}
class PluginUpdateFinding(val pattern:String,val severity:String,val category:String,val file:String,val line:Int?,val description:String) {
    override fun toString()="PluginUpdateFinding(redacted)"
}
class PluginUpdateDecision(val owner:ManagementTarget,val expected:ManagedPlugin,val receiptKey:String,val kind:PluginUpdateKind,val pin:String,val catalog:PluginCatalogEntry?,delta:Map<String,List<String>>,val verdict:String,findings:List<PluginUpdateFinding>,val createdAt:Long=System.currentTimeMillis()) {
    val delta:Map<String,List<String>> = Collections.unmodifiableMap(delta.mapValues {Collections.unmodifiableList(it.value.toList())})
    val findings:List<PluginUpdateFinding> = Collections.unmodifiableList(findings.toList())
    fun eligible(now:Long=System.currentTimeMillis())=now>=createdAt && now-createdAt<=600000 && when(kind) {
        PluginUpdateKind.Capabilities->expected.catalog.isNotBlank() && catalog!=null && catalog.name==expected.catalog && catalog.sha==pin && delta.isNotEmpty()
        PluginUpdateKind.Scan->expected.catalog.isBlank() && verdict=="caution" && findings.isNotEmpty()
        else->false
    }
    fun summary()=buildJsonObject {put("decision_available",true);put("delta_counts",buildJsonObject {delta.forEach {(k,v)->put(k,v.size)}});put("scan_finding_count",findings.size);if(verdict.isNotBlank()) put("scan_verdict",verdict)}
    override fun toString()="PluginUpdateDecision(redacted)"
    companion object {
        private val surfaces=setOf("capabilities","tools","hooks","python_dependencies","requires_auth","oauth","desktop")
        private fun string(j:JsonObject,k:String,max:Int,required:Boolean=false):String {
            val v=j[k];require(v==null || v is JsonNull || v is JsonPrimitive && v.isString)
            return (v as? JsonPrimitive)?.contentOrNull.orEmpty().also {require(it.length<=max && (!required || it.isNotBlank()) && it.none {c->c=='\u0000'})}
        }
        fun parse(j:JsonObject,reply:PluginUpdateReply,owner:ManagementTarget,row:ManagedPlugin,key:String,catalog:PluginCatalogEntry?):PluginUpdateDecision {
            require(reply.kind!=PluginUpdateKind.Updated)
            val delta=if(reply.kind==PluginUpdateKind.Capabilities) {
                val groups=j["delta"] as? JsonObject ?: error("Missing proposal");require(groups.isNotEmpty() && groups.size<=16 && groups.keys.all {it in surfaces})
                groups.mapValues {(_,value)->val list=value as? JsonArray ?: error("Invalid proposal");require(list.isNotEmpty() && list.size<=256);list.map {v->require(v is JsonPrimitive && v.isString && v.content.isNotBlank() && v.content.length<=8192 && v.content.none {it=='\u0000'});v.content}}
            } else emptyMap()
            val verdict=if(reply.kind==PluginUpdateKind.Scan) string(j,"scan_verdict",64,true).also {require(it in setOf("safe","caution","dangerous"))} else ""
            val findings=if(reply.kind==PluginUpdateKind.Scan) {
                val list=j["scan_findings"] as? JsonArray ?: error("Missing findings");require(list.size<=256)
                list.map {v->val f=v.jsonObject;val line=f["line"];require(line==null || line is JsonNull || line is JsonPrimitive && !line.isString && line.intOrNull?.let {it>=0}==true)
                    val pattern=string(f,"pattern_id",128,true);val severity=string(f,"severity",64,true);val category=string(f,"category",128,true);require(listOf(pattern,severity,category).all {it.matches(Regex("[A-Za-z0-9_.-]+"))})
                    PluginUpdateFinding(pattern,severity,category,string(f,"file",4096),line?.jsonPrimitive?.intOrNull,string(f,"description",8192))}
            } else emptyList()
            return PluginUpdateDecision(owner,row,key,reply.kind,reply.pin,catalog,delta,verdict,findings)
        }
    }
}
class PluginUpdateChanged(val inventory:GatewayPluginInventory?=null):Exception()
class PluginUpdateReadbackPending:Exception()
class PluginUpdateUncertain:Exception()
class PluginUpdateRequest(val expected:ManagedPlugin,val decision:PluginUpdateDecision?=null) {
    init {require(supported(expected));if(decision!=null) require(decision.expected===expected && decision.eligible())}
    val directory get()=expected.directory!!
    val acceptCapabilities get()=decision?.kind==PluginUpdateKind.Capabilities
    val force get()=decision?.kind==PluginUpdateKind.Scan
    fun same(row:ManagedPlugin)=PluginRemovalRequest(expected).same(row)
    fun params(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);put("profile",profile);put("action","update");put("name",directory.name);put("accept_capabilities",acceptCapabilities);put("force",force)}
    fun summary()=buildJsonObject {put("install_name",directory.name);put("plugin_key",expected.key);put("accept_capabilities",acceptCapabilities);put("force",force);if(expected.catalog.isNotBlank()) {put("catalog_name",expected.catalog);put("reviewed_catalog_pin",expected.catalogSha)};decision?.let {put("decision_origin",it.receiptKey);put("approved_delta_counts",buildJsonObject {it.delta.forEach {(k,v)->put(k,v.size)}});put("approved_finding_count",it.findings.size);if(it.verdict.isNotBlank()) put("approved_scan_verdict",it.verdict)}}
    fun acknowledge(j:JsonObject):PluginUpdateReply {
        fun flag(k:String):Boolean? {val v=j[k];require(v==null || v is JsonNull || v==JsonPrimitive(true) || v==JsonPrimitive(false));return (v as? JsonPrimitive)?.booleanOrNull}
        val ok=flag("ok") ?: error("Missing status");val consent=flag("consent_required")==true;val blocked=flag("scan_blocked")==true;require(!(consent && blocked))
        val n=j["name"] as? JsonPrimitive;require(n?.isString==true);val name=n.content
        val pin=(j["sha"] as? JsonPrimitive)?.takeIf {it.isString && PluginSourcesProtocol.sha(it.content)}?.content.orEmpty()
        if(ok) {
            require(!consent && !blocked);val unchanged=flag("unchanged") ?: error("Missing unchanged report")
            require(if(expected.catalog.isBlank()) name==directory.name else segment(name))
            return PluginUpdateReply(PluginUpdateKind.Updated,name,unchanged,pin)
        }
        require(consent || blocked)
        require(if(consent) expected.catalog.isNotBlank() && name==expected.catalog else name==directory.name)
        return PluginUpdateReply(if(consent) PluginUpdateKind.Capabilities else PluginUpdateKind.Scan,name,null,pin)
    }
    fun observedRow(reply:PluginUpdateReply,inventory:GatewayPluginInventory):ManagedPlugin? {
        val parent=directory.name.substringBeforeLast('/',"")
        val name=if(expected.catalog.isBlank()) directory.name else listOf(parent,reply.name).filter {it.isNotBlank()}.joinToString("/")
        return inventory.plugins.singleOrNull {it.source in setOf("user","git") && it.directory?.name==name && (expected.catalog.isBlank() || it.catalog==expected.catalog)}
    }
    fun reportedPinObserved(reply:PluginUpdateReply,row:ManagedPlugin)=reply.pin.isNotBlank() && (row.installedSha==reply.pin || row.catalogSha==reply.pin && row.updateAvailable==false && PluginSourcesProtocol.sha(row.installedSha))
    override fun toString()="PluginUpdateRequest(redacted)"
    companion object {
        private fun segment(name:String)=name.isNotBlank() && name==name.trim() && name.length<=256 && !name.contains("..") && name.none {it.isISOControl() || it in ":\\/"} && name.codePoints().noneMatch {it in 0xD800..0xDFFF}
        fun supported(row:ManagedPlugin)=PluginRemovalRequest.supported(row) && (if(row.catalog.isNotBlank()) PluginSourcesProtocol.sha(row.catalogSha) else row.pinnedSha.isBlank())
    }
}
