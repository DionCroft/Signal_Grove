package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Collections

class CredentialPoolUnavailable:Exception()
class CredentialPoolChanged:Exception()
class CredentialPoolUncertain:Exception()
class CredentialPoolReadbackPending(val report:PoolCleanupReport?=null):Exception()
enum class PoolSourceKind {Manual,Environment,Configuration,HermesOAuth,ExternalCLI,ProviderHook}
enum class PoolReportedStatus {Unreported,Ok,Exhausted,Dead,Other}
class PoolCredential(val index:Int,val id:String,val label:String,val authType:String,val priority:Long,val hasRefresh:Boolean,val status:PoolReportedStatus,val requestCount:Long,val sourceKind:PoolSourceKind,private val sourceIdentity:String) {
    fun sameStructure(other:PoolCredential)=index==other.index && id==other.id && label==other.label && authType==other.authType && priority==other.priority && hasRefresh==other.hasRefresh && sourceIdentity==other.sourceIdentity
    override fun toString()="PoolCredential(redacted)"
}
class CredentialPoolProvider(val provider:String,entries:List<PoolCredential>) {
    val entries:List<PoolCredential> = Collections.unmodifiableList(entries.toList())
    val addressable get()=CredentialPoolProtocol.provider(provider)
    override fun toString()="CredentialPoolProvider(redacted)"
}
class CredentialPoolInventory(val owner:ManagementTarget,val homeId:String,providers:List<CredentialPoolProvider>) {
    val providers:List<CredentialPoolProvider> = Collections.unmodifiableList(providers.toList())
    fun sameProvider(other:CredentialPoolInventory,provider:String):Boolean {
        if(owner!=other.owner || homeId!=other.homeId) return false
        val a=providers.singleOrNull {it.provider==provider}?.entries.orEmpty();val b=other.providers.singleOrNull {it.provider==provider}?.entries.orEmpty()
        return a.size==b.size && a.zip(b).all {(left,right)->left.sameStructure(right)}
    }
    override fun toString()="CredentialPoolInventory(redacted)"
    companion object {
        fun parse(j:JsonObject,owner:ManagementTarget,homeId:String):CredentialPoolInventory=try {
            val rows=j["providers"] as? JsonArray ?: error("Missing pool providers.");require(rows.size<=500)
            var count=0
            val providers=rows.map {v->
                val row=v.jsonObject;val name=CredentialPoolProtocol.text(row,"provider",256,true)
                val items=row["entries"] as? JsonArray ?: error("Missing pool entries.");require(items.size<=1000);count+=items.size;require(count<=5000)
                val entries=items.mapIndexed {i,value->
                    val e=value.jsonObject;val index=CredentialPoolProtocol.number(e,"index");require(index==i+1L)
                    val source=CredentialPoolProtocol.text(e,"source",8192)
                    val sourceId=MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
                    val status=when(CredentialPoolProtocol.text(e,"last_status",256)) {""->PoolReportedStatus.Unreported;"ok"->PoolReportedStatus.Ok;"exhausted"->PoolReportedStatus.Exhausted;"dead"->PoolReportedStatus.Dead;else->PoolReportedStatus.Other}
                    val requests=CredentialPoolProtocol.number(e,"request_count");require(requests>=0)
                    PoolCredential(i+1,CredentialPoolProtocol.text(e,"id",256,true),CredentialPoolProtocol.text(e,"label",1024),CredentialPoolProtocol.text(e,"auth_type",64,true),CredentialPoolProtocol.number(e,"priority"),CredentialPoolProtocol.flag(e,"has_refresh"),status,requests,CredentialPoolProtocol.sourceKind(name,source),sourceId)
                }
                require(entries.map {it.id}.distinct().size==entries.size)
                CredentialPoolProvider(name,entries)
            }
            require(providers.map {it.provider}.distinct().size==providers.size)
            CredentialPoolInventory(owner,homeId,providers)
        } catch(e:Exception) {throw CredentialPoolUnavailable()}
    }
}
/** Original host response details, scoped to this open view; never serialized into receipts. */
class PoolCleanupReport private constructor(val owner:ManagementTarget,val homeId:String,val receiptKey:String,val provider:String,cleaned:List<String>,hints:List<String>) {
    val cleaned:List<String> = Collections.unmodifiableList(cleaned.toList())
    val hints:List<String> = Collections.unmodifiableList(hints.toList())
    val hasMessages get()=cleaned.isNotEmpty() || hints.isNotEmpty()
    override fun toString()="PoolCleanupReport(redacted)"
    companion object {
        fun parse(j:JsonObject,owner:ManagementTarget,homeId:String,receiptKey:String,provider:String):PoolCleanupReport?=runCatching {
            require(CredentialPoolProtocol.flag(j,"ok") && CredentialPoolProtocol.text(j,"provider",256,true)==provider && CredentialPoolProtocol.number(j,"count")>=0)
            fun rows(key:String):List<String> {
                val list=j[key] as? JsonArray ?: error("No cleanup report.");require(list.size<=2048)
                return list.map {value->require(value is JsonPrimitive && value.isString && value.content.length<=16384 && value.content.none {it.isISOControl() && it !in "\r\n\t"});value.content}
            }
            PoolCleanupReport(owner,homeId,receiptKey,provider,rows("cleaned"),rows("hints"))
        }.getOrNull()
    }
}
enum class CredentialPoolAction(val receiptAction:String) {Add("CredentialPoolAdd"),Remove("CredentialPoolRemove")}
class CredentialPoolRequest private constructor(val expected:CredentialPoolInventory,val provider:String,val action:CredentialPoolAction,val entry:PoolCredential?,val freshValue:String,val freshLabel:String) {
    init {
        require(CredentialPoolProtocol.provider(provider))
        if(action==CredentialPoolAction.Add) require(entry==null && HostEnvPolicy.value(freshValue) && freshValue==freshValue.trim() && freshLabel.length<=256 && freshLabel.none(Char::isISOControl))
        else require(entry!=null && expected.providers.singleOrNull {it.provider==provider}?.entries?.singleOrNull {it.index==entry.index}===entry && freshValue.isEmpty() && freshLabel.isEmpty())
    }
    fun body():JsonObject {require(action==CredentialPoolAction.Add);return buildJsonObject {put("provider",provider);put("api_key",freshValue);if(freshLabel.isNotEmpty()) put("label",freshLabel)}}
    fun summary()=buildJsonObject {
        put("provider",provider);put("home_id",expected.homeId);put("operation",action.name)
        put("reviewed_count",expected.providers.singleOrNull {it.provider==provider}?.entries?.size ?: 0)
        if(action==CredentialPoolAction.Add) {put("fresh_credential_supplied",true);put("label_supplied",freshLabel.isNotEmpty())}
        entry?.let {put("requested_index",it.index);put("reviewed_id",it.id);put("source_kind",it.sourceKind.name)}
    }
    fun acknowledge(j:JsonObject):JsonObject {
        require(CredentialPoolProtocol.flag(j,"ok") && CredentialPoolProtocol.text(j,"provider",256,true)==provider)
        val count=CredentialPoolProtocol.number(j,"count");require(count>=0)
        // Optional cleanup strings can contain private paths, commands, emails or provider errors.
        // Only their typed message counts survive; malformed details cannot erase a known ACK.
        fun messages(key:String):Int? {val list=j[key] as? JsonArray ?: return null;return list.size.takeIf {it<=2048 && list.all {v->v is JsonPrimitive && v.isString && v.content.length<=16384 && v.content.none {it.isISOControl() && it !in "\r\n\t"}}}}
        val cleaned=if(action==CredentialPoolAction.Remove) messages("cleaned") else null
        val hints=if(action==CredentialPoolAction.Remove) messages("hints") else null
        return buildJsonObject {
            put("provider",provider);put("operation",action.name);put("reported_count",count)
            entry?.let {put("requested_index",it.index);put("reviewed_id",it.id)}
            if(action==CredentialPoolAction.Remove) {put("cleanup_report_available",cleaned!=null && hints!=null);cleaned?.let {put("cleanup_message_count",it)};hints?.let {put("hint_message_count",it)}}
        }
    }
    override fun toString()="CredentialPoolRequest(redacted)"
    companion object {
        fun add(expected:CredentialPoolInventory,provider:String,value:String,label:String="")=CredentialPoolRequest(expected,provider,CredentialPoolAction.Add,null,value.trim(),label.trim())
        fun remove(expected:CredentialPoolInventory,provider:String,entry:PoolCredential)=CredentialPoolRequest(expected,provider,CredentialPoolAction.Remove,entry,"","")
    }
}
object CredentialPoolProtocol {
    fun provider(value:String)=value.isNotBlank() && value.length<=256 && value==value.trim() && value==value.lowercase(java.util.Locale.ROOT) && value !in setOf(".","..","custom:") && value.none {it.isISOControl() || it.isWhitespace() || it in "/\\?#%"}
    fun text(j:JsonObject,key:String,max:Int,required:Boolean=false):String {
        val v=j[key];require(v==null || v is JsonNull || v is JsonPrimitive && v.isString)
        return (v as? JsonPrimitive)?.contentOrNull.orEmpty().also {require(it.length<=max && it.none(Char::isISOControl) && (!required || it.isNotBlank()))}
    }
    fun number(j:JsonObject,key:String):Long {val v=j[key] as? JsonPrimitive ?: error("Missing pool number.");require(!v.isString);return v.longOrNull ?: error("Invalid pool number.")}
    fun flag(j:JsonObject,key:String):Boolean {val v=j[key] as? JsonPrimitive ?: error("Missing pool flag.");require(!v.isString);return v.booleanOrNull ?: error("Invalid pool flag.")}
    fun sourceKind(provider:String,source:String)=when {
        source=="manual"->PoolSourceKind.Manual
        source.startsWith("env:")->PoolSourceKind.Environment
        source.startsWith("config:") || source=="model_config"->PoolSourceKind.Configuration
        provider=="anthropic" && source=="hermes_pkce" || provider=="openai-codex" && (source=="device_code" || source.endsWith(":device_code")) || provider in setOf("nous","xai-oauth") && source=="device_code" || provider=="minimax-oauth" && source=="oauth"->PoolSourceKind.HermesOAuth
        provider=="anthropic" && source=="claude_code" || provider=="qwen-oauth" && source=="qwen-cli"->PoolSourceKind.ExternalCLI
        else->PoolSourceKind.ProviderHook
    }
}
