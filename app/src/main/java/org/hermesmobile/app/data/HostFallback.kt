package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Collections

class HostFallbackUnavailable:Exception()
class HostFallbackChanged:Exception()
class HostFallbackUncertain:Exception()
class HostFallbackReadbackPending:Exception()
enum class FallbackSection(val key:String) {Primary("fallback_providers"),Legacy("fallback_model")}
enum class FallbackAuth {Keep,Host,Environment,Fresh}
/** Only these two raw configuration fields survive projection, in memory, while this view is open. */
class FallbackRow private constructor(private val raw:JsonElement,val freshCredential:Boolean=false,private val lineage:Any=Any(),internal val newRow:Boolean=false) {
    private val obj get()=raw as? JsonObject
    private fun text(key:String)=((obj?.get(key) as? JsonPrimitive)?.takeIf {it.isString})?.content.orEmpty()
    val provider get()=text("provider").trim()
    val model get()=text("model").trim()
    val baseUrl get()=text("base_url")
    val apiMode get()=text("api_mode")
    val transport get()=text("transport")
    val hasInlineCredential get()=obj?.get("api_key")?.let {it!=JsonNull && it!=JsonPrimitive("")} ?: false
    val hasEnvironment get()=listOf("key_env","api_key_env").any {obj?.get(it)?.let {v->v!=JsonNull && v!=JsonPrimitive("")} ?: false}
    val extensionCount get()=obj?.keys?.count {it !in setOf("provider","model","base_url","api_mode","transport","api_key","key_env","api_key_env")} ?: 0
    val editable get()=obj!=null && listOf("provider","model","base_url","api_mode","transport").all {key->obj?.get(key).let {it==null || it is JsonPrimitive && it.isString}} && HostFallbackPolicy.route(provider,model,baseUrl,"") && listOf("api_mode","transport").all {text(it).length<=128 && text(it).none(Char::isISOControl)} && listOf("key_env","api_key_env").all {key->obj?.get(key).let {it==null || it==JsonNull || it is JsonPrimitive && it.isString && it.content.length<=256 && it.content.none(Char::isISOControl)}}
    internal fun wire()=raw
    internal fun derivesFrom(other:FallbackRow)=lineage===other.lineage
    fun edit(provider:String,model:String,baseUrl:String,apiMode:String?=null,auth:FallbackAuth=FallbackAuth.Keep,credential:String=""):FallbackRow {
        require(editable);return create(provider,model,baseUrl,apiMode,auth,credential,obj!!,freshCredential).let {FallbackRow(it.raw,it.freshCredential,lineage,newRow)}
    }
    /** Existing inline credentials are never rendered, including in the explicit private review. */
    fun reviewDetails():JsonObject=buildJsonObject {
        if(!editable) return@buildJsonObject
        put("base_url",baseUrl);put("api_mode",apiMode);put("transport",transport)
        for(key in listOf("key_env","api_key_env")) ((obj?.get(key) as? JsonPrimitive)?.takeIf {it.isString})?.let {put(key,it.content)}
        if(freshCredential) put("fresh_api_key",text("api_key"))
    }
    override fun toString()="FallbackRow(redacted)"
    companion object {
        internal fun retained(raw:JsonElement)=FallbackRow(Json.parseToJsonElement(raw.toString()))
        fun fresh(provider:String,model:String,baseUrl:String="",apiMode:String="",auth:FallbackAuth=FallbackAuth.Host,credential:String=""):FallbackRow {
            require(auth!=FallbackAuth.Keep);return create(provider,model,baseUrl,apiMode,auth,credential,JsonObject(emptyMap()),false).let {FallbackRow(it.raw,it.freshCredential,newRow=true)}
        }
        private fun create(provider:String,model:String,baseUrl:String,apiMode:String?,auth:FallbackAuth,credential:String,previous:JsonObject,hadFresh:Boolean):FallbackRow {
            val p=provider.trim();val m=model.trim();val url=baseUrl.trim();val secret=credential.trim()
            require(HostFallbackPolicy.route(p,m,url,apiMode ?: ""))
            if(auth==FallbackAuth.Fresh) require(HostEnvPolicy.value(secret))
            if(auth==FallbackAuth.Environment) require(HostFallbackPolicy.environment(secret))
            val values=previous.toMutableMap()
            fun assign(key:String,value:String,optional:Boolean=false) {
                val old=(previous[key] as? JsonPrimitive)?.takeIf {it.isString}?.content
                if((if(key=="api_mode") old else old?.trim())==value || old==null && optional && value.isEmpty()) return
                if(optional && value.isEmpty()) values.remove(key) else values[key]=JsonPrimitive(value)
            }
            assign("provider",p);assign("model",m);assign("base_url",url,true)
            if(apiMode!=null) {values.remove("api_mode");values.remove("transport");if(apiMode.isNotEmpty()) values["api_mode"]=JsonPrimitive(apiMode)}
            if(auth!=FallbackAuth.Keep) {values.remove("api_key");values.remove("key_env");values.remove("api_key_env")}
            when(auth) {FallbackAuth.Fresh->values["api_key"]=JsonPrimitive(secret);FallbackAuth.Environment->values["key_env"]=JsonPrimitive(secret);else->Unit}
            return FallbackRow(JsonObject(values.toMap()),auth==FallbackAuth.Fresh || auth==FallbackAuth.Keep && hadFresh)
        }
    }
}
class HostFallbackSnapshot private constructor(val owner:ManagementTarget,val homeId:String,private val fields:Map<String,JsonElement>) {
    fun rows(section:FallbackSection):List<FallbackRow> {
        val value=fields[section.key];val rows=when(value) {null,JsonNull->emptyList();is JsonArray->value.toList();else->listOf(value)}
        return Collections.unmodifiableList(rows.map(FallbackRow::retained))
    }
    fun sameConfig(other:HostFallbackSnapshot)=owner==other.owner && homeId==other.homeId && fields==other.fields
    internal fun sameField(section:FallbackSection,rows:List<FallbackRow>)=fields[section.key]==JsonArray(rows.map {it.wire()})
    override fun toString()="HostFallbackSnapshot(redacted)"
    companion object {
        fun parse(config:JsonObject,owner:ManagementTarget,homeId:String):HostFallbackSnapshot=try {
            val fields=FallbackSection.entries.mapNotNull {section->config[section.key]?.let {section.key to Json.parseToJsonElement(it.toString())}}.toMap()
            require(JsonObject(fields).toString().length<=500_000)
            fields.values.forEach {require(it !is JsonArray || it.size<=200)}
            HostFallbackSnapshot(owner,homeId,fields)
        } catch(e:Exception) {throw HostFallbackUnavailable()}
    }
}
class HostFallbackDraft private constructor(val expected:HostFallbackSnapshot,private val values:Map<FallbackSection,List<FallbackRow>>,private val edited:Set<FallbackSection>) {
    fun rows(section:FallbackSection)=values.getValue(section)
    val changed get()=edited.filter { !expected.sameField(it,rows(it)) }.toSet()
    private fun set(section:FallbackSection,rows:List<FallbackRow>):HostFallbackDraft {require(rows.size<=200);return HostFallbackDraft(expected,values+mapOf(section to Collections.unmodifiableList(rows.toList())),edited+section)}
    fun add(section:FallbackSection,row:FallbackRow):HostFallbackDraft {require(row.newRow);return set(section,rows(section)+row)}
    fun replace(section:FallbackSection,index:Int,row:FallbackRow):HostFallbackDraft {require(row.derivesFrom(rows(section)[index]));return set(section,rows(section).toMutableList().apply {this[index]=row})}
    fun remove(section:FallbackSection,index:Int)=set(section,rows(section).toMutableList().apply {removeAt(index)})
    fun move(section:FallbackSection,index:Int,offset:Int):HostFallbackDraft {require(offset==1 || offset== -1);val list=rows(section).toMutableList();require(index+offset in list.indices);Collections.swap(list,index,index+offset);return set(section,list)}
    fun clear()=set(FallbackSection.Primary,emptyList()).set(FallbackSection.Legacy,emptyList())
    override fun toString()="HostFallbackDraft(redacted)"
    companion object {fun from(snapshot:HostFallbackSnapshot)=HostFallbackDraft(snapshot,FallbackSection.entries.associateWith(snapshot::rows),emptySet())}
}
class HostFallbackRequest(val draft:HostFallbackDraft) {
    val expected get()=draft.expected
    val sections:Set<FallbackSection> = Collections.unmodifiableSet(draft.changed.toSet())
    init {require(sections.isNotEmpty());require(body().toString().length<=600_000)}
    fun body()=buildJsonObject {put("profile",expected.owner.profile);put("config",buildJsonObject {for(section in sections) put(section.key,JsonArray(draft.rows(section).map {it.wire()}))})}
    fun summary()=buildJsonObject {put("home_id",expected.homeId);put("sections",JsonArray(sections.map {JsonPrimitive(it.key)}));for(section in sections) {put("before_"+section.key,expected.rows(section).size);put("requested_"+section.key,draft.rows(section).size)}}
    fun acknowledge(j:JsonObject):JsonObject {val ok=j["ok"] as? JsonPrimitive;require(ok!=null && !ok.isString && ok.booleanOrNull==true);return summary()}
    fun matches(snapshot:HostFallbackSnapshot)=expected.owner==snapshot.owner && expected.homeId==snapshot.homeId && sections.all {snapshot.sameField(it,draft.rows(it))}
    override fun toString()="HostFallbackRequest(redacted)"
}
object HostFallbackPolicy {
    val apiModes=listOf("","chat_completions","codex_responses","anthropic_messages")
    fun environment(value:String)=value.length in 1..256 && value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))
    fun route(provider:String,model:String,baseUrl:String,apiMode:String)=
        provider.isNotBlank() && provider.length<=256 && provider.none(Char::isISOControl) && model.isNotBlank() && model.length<=2048 && model.none(Char::isISOControl) && apiMode in apiModes && baseUrl.length<=8192 && baseUrl.none(Char::isISOControl) && (baseUrl.isEmpty() || baseUrl.toHttpUrlOrNull()!=null)
}
