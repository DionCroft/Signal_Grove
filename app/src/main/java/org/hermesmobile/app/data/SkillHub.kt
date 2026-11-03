package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SkillHubInvalid:Exception()
class SkillHubChanged:Exception()
class SkillHubUnsupported:Exception()
data class HubSource(val id:String,val label:String,val available:Boolean?,val searchable:Boolean?,val rateLimited:Boolean?)
class HubSkill(val name:String,val description:String,val source:String,val identifier:String,val trust:String,val repo:String,tags:List<String>,val category:String,val installed:Boolean?) {
    val tags:List<String> = java.util.Collections.unmodifiableList(tags.toList())
    val canonicalId:String get()=if(source.equals("clawhub",true) && !identifier.startsWith("clawhub/")) "clawhub/$identifier" else identifier
    val addressable:Boolean get()=canonicalId.isNotBlank() && canonicalId.length<=8192 && canonicalId==canonicalId.trim() && canonicalId.none(Char::isISOControl) &&
        (!canonicalId.contains("://") || canonicalId.toHttpUrlOrNull()?.let {it.username.isBlank() && it.password.isBlank() && it.fragment==null}==true)
    fun displayIdentifier(showQuery:Boolean=false)=if(canonicalId.contains("://")) canonicalId.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.fragment(null)?.apply {if(!showQuery) query(null)}?.build()?.toString().orEmpty() else canonicalId
    fun displayRepo()=if(repo.contains("://")) repo.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.query(null)?.fragment(null)?.build()?.toString().orEmpty() else repo
    override fun toString()="HubSkill(redacted)"
}
data class HubInstalled(val name:String,val trust:String,val verdict:String)
class HubSources(val sources:List<HubSource>,val featured:List<HubSkill>,val installed:Map<String,HubInstalled>?) {override fun toString()="HubSources(redacted)"}
class HubSearch(val results:List<HubSkill>,val installed:Map<String,HubInstalled>?,val timedOut:List<String>) {override fun toString()="HubSearch(redacted)"}
class HubPreview(val skill:HubSkill,val content:String,val files:List<String>) {override fun toString()="HubPreview(redacted)"}
class HubFinding(val severity:String,val category:String,val file:String,val line:Int?,val description:String,val sensitive:Boolean) {
    fun displayFile():String {val value=file.replace('\\','/');return value.takeIf {it.isNotBlank() && !it.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(it) && ".." !in it.split('/')} ?: ""}
    override fun toString()="HubFinding(redacted)"
}
class HubScan(val identifier:String,val name:String,val source:String,val trust:String,val verdict:String,val policy:String,val reason:String,val findings:List<HubFinding>,val tier1Passed:Boolean?,val incomplete:List<String>,val tier1:List<HubFinding>) {
    override fun toString()="HubScan(redacted)"
}

/** Bounded host-reported metadata. Source text and scan evidence are transient; no instructions are executed by the phone. */
object SkillHubProtocol {
    private fun text(j:JsonObject,key:String,max:Int,required:Boolean=false):String {
        val v=j[key];require(v==null || v is JsonNull || v is JsonPrimitive && v.isString)
        return (v as? JsonPrimitive)?.contentOrNull.orEmpty().also {require(it.length<=max && (!required || it.isNotBlank()));if(key in setOf("name","identifier","source","trust_level","category")) require(it.none(Char::isISOControl))}
    }
    private fun flag(j:JsonObject,key:String):Boolean? {val v=j[key];require(v==null || v is JsonNull || v==JsonPrimitive(true) || v==JsonPrimitive(false));return (v as? JsonPrimitive)?.booleanOrNull}
    private fun list(j:JsonObject,key:String,count:Int,max:Int,required:Boolean=false):List<String> {
        val v=j[key];if(!required && (v==null || v is JsonNull)) return emptyList()
        require(v is JsonArray && v.size<=count)
        return java.util.Collections.unmodifiableList(v.map {require(it is JsonPrimitive && it.isString);it.content.also {s->require(s.length<=max && s.none(Char::isISOControl))}})
    }
    private fun skill(j:JsonObject)=HubSkill(text(j,"name",256,true),text(j,"description",8192),text(j,"source",128,true),text(j,"identifier",8192,true),text(j,"trust_level",64),text(j,"repo",8192),list(j,"tags",64,128),text(j,"category",256),flag(j,"installed"))
    private fun skills(j:JsonObject,key:String,max:Int):List<HubSkill> {
        val values=j[key] as? JsonArray ?: error("Missing skill catalog.");require(values.size<=max)
        return java.util.Collections.unmodifiableList(values.map {skill(it.jsonObject)}.also {require(it.map(HubSkill::canonicalId).distinct().size==it.size)})
    }
    private fun installed(j:JsonObject):Map<String,HubInstalled>? {
        val value=j["installed"];if(value==null || value is JsonNull) return null;require(value is JsonObject);val rows=value;require(rows.size<=5000)
        return java.util.Collections.unmodifiableMap(rows.mapValues {(key,v)->require(key.isNotBlank() && key.length<=8192 && key.none(Char::isISOControl));val row=v.jsonObject;HubInstalled(text(row,"name",256),text(row,"trust_level",64),text(row,"scan_verdict",64))})
    }
    private inline fun <T> parse(block:()->T):T=try {block()} catch(e:SkillHubChanged) {throw e} catch(e:Exception) {throw SkillHubInvalid()}
    fun official(j:JsonObject):List<HubSkill> =parse {skills(j,"skills",2000)}
    fun sources(j:JsonObject):HubSources=parse {
        val rows=j["sources"] as? JsonArray ?: error("Missing skill sources.");require(rows.size<=64)
        val sources=rows.map {v->val row=v.jsonObject;val id=text(row,"id",128,true);require(id.none(Char::isISOControl));HubSource(id,text(row,"label",256),flag(row,"available"),flag(row,"searchable"),flag(row,"rate_limited"))}.also {require(it.map(HubSource::id).distinct().size==it.size)}
        HubSources(java.util.Collections.unmodifiableList(sources),skills(j,"featured",100),installed(j))
    }
    fun search(j:JsonObject):HubSearch=parse {HubSearch(skills(j,"results",20),installed(j),list(j,"timed_out",64,128))}
    fun preview(j:JsonObject,expected:HubSkill):HubPreview=parse {
        val current=skill(j)
        if(current.canonicalId!=expected.canonicalId || !current.source.equals(expected.source,true)) throw SkillHubChanged()
        HubPreview(current,text(j,"skill_md",131072),list(j,"files",2048,1024,true))
    }
    private fun findings(j:JsonObject,key:String,tier1:Boolean):List<HubFinding> {
        val rows=j[key] as? JsonArray ?: error("Missing scan findings.");require(rows.size<=512)
        return java.util.Collections.unmodifiableList(rows.map {v ->val row=v.jsonObject
            val rawLine=row["line"];val line=if(rawLine==null || rawLine is JsonNull) null else {require(rawLine is JsonPrimitive && !rawLine.isString);val number=rawLine.intOrNull ?: error("Invalid finding location.");require(number in 0..10000000);number.takeIf {it>0}}
            val sensitive=tier1 && flag(row,"secrets_class")==true
            HubFinding(text(row,"severity",64),text(row,if(tier1) "check" else "category",128),text(row,"file",1024),line,if(sensitive) "" else text(row,if(tier1) "message" else "description",4096),sensitive)
        })
    }
    fun scan(j:JsonObject,expected:HubSkill):HubScan=parse {
        val identifier=text(j,"identifier",8192,true);require(identifier==expected.canonicalId)
        val tier1=j["tier1"]?.takeUnless {it is JsonNull}?.jsonObject
        HubScan(identifier,text(j,"name",256),text(j,"source",8192),text(j,"trust_level",64),text(j,"verdict",64),text(j,"policy",64),text(j,"policy_reason",4096),findings(j,"findings",false),tier1?.let {flag(it,"passed")},tier1?.let {list(it,"incomplete_checks",64,256)}.orEmpty(),tier1?.let {findings(it,"findings",true)}.orEmpty())
    }
}
