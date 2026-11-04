package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.util.Collections

class HostRelayLinkUncertain:Exception()
class HostRelayLinkReadbackPending:Exception()
class HostRelayCodeUnavailable:Exception()
class HostRelayCodeFull:Exception()
class HostRelayLinkRefused(val portal:Boolean):Exception()
class HostRelayNotForeground:Exception()

object HostRelayLinkPolicy {
    fun code(value:String)=value.isNotEmpty() && value.length<=256 && value !in setOf(".","..") && value.none {it.isISOControl() || it.isWhitespace() || it in "/\\?#"} && value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==value
    fun text(raw:JsonObject,key:String,max:Int):String {val v=raw[key] as? JsonPrimitive ?: error("Missing relay field");require(v.isString && v.content.length<=max && v.content.none(Char::isISOControl));return v.content}
    fun modes(scope:HostRelayScope)=JsonObject(scope.modes.mapValues {(_,v)->buildJsonArray {add(v.first);add(v.second)}})
    fun modes(raw:JsonObject):Map<String,Pair<Boolean,Boolean>> {
        require(raw.isNotEmpty() && raw.size<=128)
        return Collections.unmodifiableMap(raw.mapValues {(id,value)->require(HostChannelPolicy.id(id));val a=value as? JsonArray ?: error("Invalid modes");require(a.size==2 && a.all {it==JsonPrimitive(true) || it==JsonPrimitive(false)});a[0].jsonPrimitive.boolean to a[1].jsonPrimitive.boolean})
    }
    fun bot(platform:String,code:String,value:String):Boolean=runCatching {
        val u=value.toHttpUrlOrNull() ?: return@runCatching false
        if(!u.isHttps || u.username.isNotEmpty() || u.password.isNotEmpty() || u.fragment!=null || u.port!=443 || value.length>8192 || value.none(Char::isISOControl).not()) return@runCatching false
        when(platform) {
            "telegram"->u.host=="t.me" && u.query==null && u.pathSegments.size==1 && u.pathSegments.single().matches(Regex("[A-Za-z0-9_]{1,128}"))
            "discord"->u.host=="discord.com" && u.query==null && u.pathSegments.size==2 && u.pathSegments[0]=="users" && u.pathSegments[1].matches(Regex("[0-9]{1,128}"))
            "whatsapp"->u.host=="wa.me" && u.pathSegments.size==1 && u.pathSegments[0].matches(Regex("[0-9]{1,128}")) && u.queryParameterNames==setOf("text") && u.queryParameterValues("text")==listOf("/link "+code)
            else->false
        }
    }.getOrDefault(false)
}
class HostRelayLinkRequest(val expected:HostRelayInventory,val platform:String) {
    val row=expected.platforms.single {it.id==platform}
    init {require(expected.manager==HostRelayManager.Connector && HostChannelPolicy.id(platform) && expected.scope.homeId.matches(Regex("[a-f0-9]{64}")))}
    fun same(current:HostRelayInventory)=expected.scope.same(current.scope) && current.manager==HostRelayManager.Connector && current.platforms.singleOrNull {it.id==platform}?.botUrl==row.botUrl
    fun summary()=buildJsonObject {put("platform",platform);put("home_id",expected.scope.homeId);put("relay_modes",HostRelayLinkPolicy.modes(expected.scope))}
    fun acknowledge(raw:JsonObject) {require(raw["platform"]==JsonPrimitive(platform));val c=HostRelayLinkPolicy.text(raw,"code",256);require(HostRelayLinkPolicy.code(c) && raw["command"]==JsonPrimitive("/link "+c))}
    override fun toString()="HostRelayLinkRequest(redacted)"
}
class HostRelayLinkBinding private constructor(val receipt:ManagementOperation,val homeId:String,val modes:Map<String,Pair<Boolean,Boolean>>,val issued:Boolean,val status:String) {
    fun owner()=buildJsonObject {put("key",receipt.key);put("api_scope",receipt.apiScope);put("url",receipt.url);put("profile",receipt.profile);put("actor",receipt.actor);put("platform",receipt.target);put("home_id",homeId);put("relay_modes",JsonObject(modes.mapValues {(_,v)->buildJsonArray {add(v.first);add(v.second)}}))}
    fun same(current:HostRelayInventory)=current.owner.apiScope==receipt.apiScope && current.owner.url==receipt.url && current.owner.profile==receipt.profile && current.owner.actor==receipt.actor && current.scope.homeId==homeId && current.scope.modes==modes && current.manager==HostRelayManager.Connector && current.platforms.any {it.id==receipt.target}
    override fun toString()="HostRelayLinkBinding(redacted)"
    companion object {
        fun parse(receipt:ManagementOperation,target:ManagementTarget):HostRelayLinkBinding {
            require(receipt.action=="RelayLinkStart" && receipt.state=="acknowledged" && receipt.resourceScope==target.resourceScope && receipt.apiScope==target.apiScope && receipt.url==target.url && receipt.profile==target.profile && receipt.actor==target.actor && HostChannelPolicy.id(receipt.target))
            val request=Json.parseToJsonElement(receipt.requestSummary).jsonObject;require(request.keys==setOf("platform","home_id","relay_modes") && request["platform"]==JsonPrimitive(receipt.target))
            val home=HostRelayLinkPolicy.text(request,"home_id",64);require(home.matches(Regex("[a-f0-9]{64}")));val modes=HostRelayLinkPolicy.modes(request["relay_modes"]!!.jsonObject);require(receipt.target in modes)
            val result=Json.parseToJsonElement(receipt.resultSummary).jsonObject;require(result["acknowledged"]==JsonPrimitive(true));val issued=result["code_issued"];require(issued==JsonPrimitive(true) || issued==JsonPrimitive(false));val status=HostRelayLinkPolicy.text(result,"status",32);require(if(issued==JsonPrimitive(true)) status in setOf("unverified","pending","inactive","refused") else status=="not_issued")
            return HostRelayLinkBinding(receipt,home,modes,issued==JsonPrimitive(true),status)
        }
    }
}
/** An original capability; never put its values or their hashes in Room/logs/saved UI state. */
class HostRelayCode(val code:String,val botUrl:String?,val originalBot:String,val expiresAt:Long) {
    val command get()="/link "+code
    override fun toString()="HostRelayCode(redacted)"
    fun same(other:HostRelayCode)=code==other.code && botUrl==other.botUrl && originalBot==other.originalBot && expiresAt==other.expiresAt
    companion object {
        fun from(raw:JsonObject,request:HostRelayLinkRequest,now:Long):HostRelayCode {
            request.acknowledge(raw);val code=raw["code"]!!.jsonPrimitive.content
            val server=(raw["expires_at"] as? JsonPrimitive)?.takeIf {it.isString}?.content?.let {runCatching {Instant.parse(it).toEpochMilli()}.getOrNull()}
            val expires=minOf(now+900_000,server ?: Long.MAX_VALUE)
            val url=(raw["bot_url"] as? JsonPrimitive)?.takeIf {it.isString}?.content?.takeIf {HostRelayLinkPolicy.bot(request.platform,code,it)}
            return HostRelayCode(code,url,request.row.botUrl,expires)
        }
    }
}
interface HostRelayCodeStore {
    fun beforeStart(target:ManagementTarget,now:Long)
    fun save(binding:HostRelayLinkBinding,code:HostRelayCode,now:Long)
    fun read(binding:HostRelayLinkBinding,now:Long):HostRelayCode?
    fun remove(binding:HostRelayLinkBinding)
    fun clear(target:ManagementTarget)
}
class HostRelayLinkObservation(val binding:HostRelayLinkBinding,val code:HostRelayCode?,val available:Boolean,val scopeVerified:Boolean,val reason:String?=null) {
    override fun toString()="HostRelayLinkObservation(redacted)"
}
