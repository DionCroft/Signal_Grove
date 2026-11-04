package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.net.URI
import java.util.Collections

class HostRelayUnavailable:Exception()
class HostRelayScopeUnavailable:Exception()
class HostRelayChanged:Exception()

/** Positive launch-home metadata is required before reading process-scoped account data. */
class HostRelayScope private constructor(val owner:ManagementTarget,val homeId:String,modes:Map<String,Pair<Boolean,Boolean>>) {
    val modes:Map<String,Pair<Boolean,Boolean>> = Collections.unmodifiableMap(modes.toMap())
    fun same(other:HostRelayScope)=owner==other.owner && homeId==other.homeId && modes==other.modes
    override fun toString()="HostRelayScope(redacted)"
    companion object {
        fun from(channels:HostChannelInventory):HostRelayScope {
            GatewayCronInventory.requireProfile(channels.owner.profile)
            val fronted=channels.rows.filter {it.relayFronted}
            if(fronted.isEmpty()) throw HostRelayScopeUnavailable()
            return HostRelayScope(channels.owner,channels.homeId,fronted.associate {it.id to (it.relayExclusive to it.relayDisabled)})
        }
    }
}
enum class HostRelayManager {Connector,Portal,Unknown}
class HostRelayAccount(val id:String,val name:String,val handle:String) {
    override fun toString()="HostRelayAccount(redacted)"
}
class HostRelayPlatform(val id:String,val botUrl:String,accounts:List<HostRelayAccount>) {
    val accounts:List<HostRelayAccount> = Collections.unmodifiableList(accounts.toList())
    override fun toString()="HostRelayPlatform(redacted)"
}
class HostRelayInventory(val scope:HostRelayScope,val manager:HostRelayManager,val portalUrl:String,platforms:List<HostRelayPlatform>) {
    val platforms:List<HostRelayPlatform> = Collections.unmodifiableList(platforms.toList())
    val owner get()=scope.owner
    val accountCount get()=platforms.sumOf {it.accounts.size}
    fun samePortal(other:HostRelayInventory)=scope.same(other.scope) && manager==HostRelayManager.Portal && other.manager==manager && portalUrl==other.portalUrl && HostRelayPolicy.portal(portalUrl)
    override fun toString()="HostRelayInventory(redacted)"
    companion object {
        fun parse(raw:JsonObject,scope:HostRelayScope):HostRelayInventory=try {
            fun text(row:JsonObject,key:String,max:Int,required:Boolean=false):String {
                val v=row[key]
                if(v==null || v is JsonNull) {require(!required);return ""}
                require(v is JsonPrimitive && v.isString)
                return v.content.also {require(it.length<=max && it.none(Char::isISOControl));if(required) require(it.isNotBlank() && it==it.trim())}
            }
            val rows=raw["platforms"] as? JsonArray ?: error("Missing platforms");require(rows.size<=128)
            var total=0;var privateSize=0
            val platforms=rows.map {value->
                val row=value.jsonObject;val id=text(row,"platform",128,true)
                val accounts=row["accounts"] as? JsonArray ?: error("Missing accounts");require(accounts.size<=512);total+=accounts.size;require(total<=4096)
                val parsed=accounts.map {v->val a=v.jsonObject;HostRelayAccount(text(a,"platformUserId",512,true),text(a,"displayName",512),text(a,"handle",512))}
                require(parsed.map {it.id}.distinct().size==parsed.size)
                val bot=text(row,"bot_url",8192);privateSize+=bot.length+parsed.sumOf {it.id.length+it.name.length+it.handle.length};require(privateSize<=500_000)
                HostRelayPlatform(id,bot,parsed)
            }
            require(platforms.map {it.id}.distinct().size==platforms.size && platforms.map {it.id}.toSet()==scope.modes.keys)
            val manager=when(text(raw,"managed_by",128)) {"connector"->HostRelayManager.Connector;"portal"->HostRelayManager.Portal;else->HostRelayManager.Unknown}
            HostRelayInventory(scope,manager,text(raw,"portal_url",8192),platforms)
        } catch(e:Exception) {throw HostRelayUnavailable()}
    }
}
object HostRelayPolicy {
    /** No credential-bearing query/fragment, user-info or non-HTTPS app/deep link handoff. */
    fun portal(value:String)=runCatching {val uri=URI(value);value.length<=8192 && value.none(Char::isISOControl) && uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.rawUserInfo==null && uri.rawQuery==null && uri.rawFragment==null && (uri.port==-1 || uri.port in 1..65535)}.getOrDefault(false)
}
