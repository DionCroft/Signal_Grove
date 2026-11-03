package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Collections

enum class PluginSourceKind { Catalog, Repository }
class PluginSource(val kind:PluginSourceKind,val value:String) {
    init {require(valid(kind,value))}
    fun display(query:Boolean=false)=if(value.contains("://")) value.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.fragment(null)?.apply {if(!query) query(null)}?.build()?.toString().orEmpty() else value
    fun params(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);put("profile",profile);put("action","inspect");put(if(kind==PluginSourceKind.Catalog) "catalog_name" else "identifier",value)}
    override fun toString()="PluginSource(redacted)"
    companion object {
        fun valid(kind:PluginSourceKind,value:String):Boolean {
            if(value.isBlank() || value!=value.trim() || value.length>(if(kind==PluginSourceKind.Catalog) 256 else 8192) || value.any(Char::isISOControl)) return false
            if(kind==PluginSourceKind.Catalog) return true // The server owns the catalog-name grammar.
            if(value.substringBefore('?').split('/').any {it=="." || it==".."}) return false
            if(value.contains("://")) return value.toHttpUrlOrNull()?.let {it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment==null}==true
            return value.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.@+-]+)*")) || value.matches(Regex("git@[A-Za-z0-9.-]+:[A-Za-z0-9_.-]+/[A-Za-z0-9_./+-]+"))
        }
    }
}
data class FeaturedPlugin(val name:String,val title:String,val description:String,val tier:String,val platforms:List<String>,val appState:String,val sentence:String)
class PluginCatalogEntry(val name:String,val repo:String,val sha:String,val subdir:String,val tier:String) {
    fun same(other:PluginCatalogEntry)=name==other.name && repo==other.repo && sha==other.sha && subdir==other.subdir && tier==other.tier
    fun displayRepo(query:Boolean=false)=HubSkill("","","",repo,"","",emptyList(),"",null).displayIdentifier(query)
    override fun toString()="PluginCatalogEntry(redacted)"
}
class PluginLookup(val name:String,val status:String,val entry:PluginCatalogEntry?) {override fun toString()="PluginLookup(redacted)"}
class PluginInspection(val source:PluginSource,val ok:Boolean,val agent:Boolean?,val desktop:Boolean?,val agentName:String,val desktopName:String,val sha:String,val warnings:List<String>,val insecure:Boolean?) {override fun toString()="PluginInspection(redacted)"}
class PluginSourceChanged:Exception()
object PluginSourcesProtocol {
    fun sha(value:String)=value.matches(Regex("(?:[a-f0-9]{40}|[a-f0-9]{64})"))
    private fun text(j:JsonObject,k:String,max:Int,required:Boolean=false):String {
        val v=j[k];require(v==null || v is JsonNull || v is JsonPrimitive && v.isString)
        return (v as? JsonPrimitive)?.contentOrNull.orEmpty().also {require(it.length<=max && (!required || it.isNotBlank()));if(k !in setOf("description","sentence")) require(it.none(Char::isISOControl))}
    }
    private fun flag(j:JsonObject,k:String):Boolean? {val v=j[k];require(v==null || v is JsonNull || v==JsonPrimitive(true) || v==JsonPrimitive(false));return (v as? JsonPrimitive)?.booleanOrNull}
    private fun strings(j:JsonObject,k:String,n:Int,max:Int):List<String> {val a=j[k] as? JsonArray ?: error("Missing list");require(a.size<=n);return Collections.unmodifiableList(a.map {v->require(v is JsonPrimitive && v.isString);v.content.also {require(it.length<=max && '\u0000' !in it)}})}
    private inline fun <T> parse(block:()->T):T=try {block()} catch(e:PluginSourceChanged) {throw e} catch(e:Exception) {throw GatewayPluginInvalid()}
    fun featured(j:JsonObject):List<FeaturedPlugin> =parse {
        val rows=j["onboarding"] as? JsonArray ?: error("Missing selection");require(rows.size<=200)
        Collections.unmodifiableList(rows.map {v->val row=v.jsonObject;FeaturedPlugin(text(row,"name",256,true),text(row,"title",512),text(row,"description",8192),text(row,"tier",128),strings(row,"platforms",32,128),text(row,"app_state",128),text(row,"sentence",4096))}.also {require(it.map(FeaturedPlugin::name).distinct().size==it.size)})
    }
    fun lookup(j:JsonObject,name:String):PluginLookup=parse {
        require(PluginSource.valid(PluginSourceKind.Catalog,name));val status=text(j,"status",64,true);require(status in setOf("found","unknown","unavailable","invalid_name"))
        val entry=if(status=="found") {val row=j["entry"]?.jsonObject ?: error("Missing entry");val current=text(row,"name",256,true);if(current!=name) throw PluginSourceChanged();val sha=text(row,"sha",64);require(sha.isEmpty() || PluginSourcesProtocol.sha(sha));PluginCatalogEntry(current,text(row,"repo",8192,true),sha,text(row,"subdir",2048),text(row,"tier",128))} else {require(j["entry"]==null || j["entry"] is JsonNull);null}
        PluginLookup(name,status,entry)
    }
    fun requireCatalog(expected:PluginCatalogEntry,lookup:PluginLookup) {if(lookup.entry?.let(expected::same)!=true) throw PluginSourceChanged()}
    fun inspection(j:JsonObject,source:PluginSource,expected:PluginCatalogEntry?=null):PluginInspection=parse {
        val ok=flag(j,"ok") ?: error("Missing inspection status");val warnings=strings(j,"warnings",64,4096);val insecure=flag(j,"insecure")
        if(!ok) return@parse PluginInspection(source,false,null,null,"","","",warnings,insecure) // Host errors may contain paths or credentials.
        val agent=flag(j,"agent") ?: error("Missing agent component");val desktop=flag(j,"desktop") ?: error("Missing desktop component");require(agent || desktop)
        val sha=text(j,"sha",64,true);require(PluginSourcesProtocol.sha(sha))
        if(expected!=null && (source.kind!=PluginSourceKind.Catalog || expected.name!=source.value)) throw PluginSourceChanged()
        PluginInspection(source,true,agent,desktop,text(j,"agent_name",256,agent),text(j,"desktop_name",256,desktop),sha,warnings,insecure)
    }
}
enum class PluginSourceReadAction { Featured, Lookup, Inspect }
class PluginSourceRead(val action:PluginSourceReadAction,val source:PluginSource?=null,val expected:PluginCatalogEntry?=null) {
    init {require(if(action==PluginSourceReadAction.Featured) source==null && expected==null else source!=null);require(action!=PluginSourceReadAction.Lookup || source?.kind==PluginSourceKind.Catalog);require(expected==null || action==PluginSourceReadAction.Inspect && source?.kind==PluginSourceKind.Catalog && source.value==expected.name)}
    override fun toString()="PluginSourceRead(redacted)"
}
