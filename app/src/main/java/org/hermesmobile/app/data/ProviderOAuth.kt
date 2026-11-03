package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Collections

class ProviderOAuthChanged:Exception()
class ProviderOAuthUnavailable:Exception()
class ProviderOAuthUncertain:Exception()
class ProviderOAuthReadbackPending:Exception()

data class OAuthProvider(val id:String,val name:String,val flow:String,val loggedIn:Boolean,val disconnectable:Boolean) {
    val canStart get()=flow=="device_code"
}
class ProviderOAuthInventory(val owner:ManagementTarget,val homeId:String,rows:List<OAuthProvider>) {
    val rows:List<OAuthProvider> = Collections.unmodifiableList(rows.toList())
    companion object {
        fun parse(j:JsonObject,owner:ManagementTarget,homeId:String):ProviderOAuthInventory {
            val rows=j["providers"] as? JsonArray ?: throw ProviderOAuthUnavailable();require(rows.size<=500)
            val projected=rows.map {v->
                val row=v as? JsonObject ?: throw ProviderOAuthUnavailable()
                val id=ProviderOAuthProtocol.string(row,"id",128);require(ProviderOAuthProtocol.providerId(id))
                val status=row["status"] as? JsonObject ?: throw ProviderOAuthUnavailable()
                OAuthProvider(id,ProviderOAuthProtocol.string(row,"name",256),ProviderOAuthProtocol.string(row,"flow",64),ProviderOAuthProtocol.boolean(status,"logged_in"),ProviderOAuthProtocol.boolean(row,"disconnectable"))
            }
            require(projected.map {it.id}.distinct().size==projected.size)
            // Drop token previews, source paths, CLI commands, private errors and arbitrary extensions.
            return ProviderOAuthInventory(owner,homeId,projected)
        }
    }
}
enum class ProviderOAuthAction(val receiptAction:String) {Start("ProviderOAuthStart"),Cancel("ProviderOAuthCancel"),Disconnect("ProviderDisconnect")}
class ProviderOAuthRequest(val expected:ProviderOAuthInventory,val provider:OAuthProvider,val action:ProviderOAuthAction) {
    init {require(action!=ProviderOAuthAction.Cancel && expected.rows.singleOrNull {it.id==provider.id}==provider);require(if(action==ProviderOAuthAction.Start) provider.canStart else provider.disconnectable)}
    fun summary()=buildJsonObject {put("provider",provider.id);put("home_id",expected.homeId);put("flow",provider.flow);put("reported_logged_in",provider.loggedIn)}
}
class ProviderOAuthBinding(val receipt:ManagementOperation,val homeId:String,val sessionId:String) {
    val provider get()=receipt.target
    override fun toString()="ProviderOAuthBinding(redacted)"
    companion object {
        fun parse(receipt:ManagementOperation,target:ManagementTarget):ProviderOAuthBinding {
            require(receipt.action=="ProviderOAuthStart" && receipt.state=="acknowledged" && receipt.resourceScope==target.resourceScope && receipt.apiScope==target.apiScope && receipt.url==target.url && receipt.profile==target.profile && receipt.actor==target.actor)
            require(ProviderOAuthProtocol.providerId(receipt.target))
            val j=Json.parseToJsonElement(receipt.resultSummary).jsonObject
            val id=ProviderOAuthProtocol.string(j,"session_id",128);require(ProviderOAuthProtocol.sessionId(id) && j.text("flow")=="device_code")
            val home=ProviderOAuthProtocol.string(j,"home_id",64);require(home.matches(Regex("[a-f0-9]{64}")))
            return ProviderOAuthBinding(receipt,home,id)
        }
    }
}
/** One-time display secrets. Never write this object to receipts, logs or saved Compose state. */
class ProviderDeviceCode(val code:String,val url:String,val expiresAt:Long) {
    fun destination()=url.toHttpUrlOrNull()!!.newBuilder().query(null).build().toString()
    override fun toString()="ProviderDeviceCode(redacted)"
    companion object {
        fun parse(j:JsonObject,now:Long):ProviderDeviceCode {
            val code=ProviderOAuthProtocol.string(j,"user_code",256);require(code.all {it.code in 32..126})
            val value=ProviderOAuthProtocol.string(j,"verification_url",16384);require('\\' !in value)
            val url=value.toHttpUrlOrNull() ?: error("Invalid provider destination.")
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.fragment==null)
            val denied=setOf("token","access_token","refresh_token","id_token","api_key","apikey","authorization","password","client_secret")
            require(url.queryParameterNames.none {it.lowercase() in denied})
            val seconds=ProviderOAuthProtocol.integer(j,"expires_in");require(seconds>0)
            return ProviderDeviceCode(code,url.toString(),now+minOf(seconds,900)*1000)
        }
    }
}
interface ProviderCodeStore {
    fun save(binding:ProviderOAuthBinding,value:ProviderDeviceCode)
    fun read(binding:ProviderOAuthBinding,now:Long):ProviderDeviceCode?
    fun remove(binding:ProviderOAuthBinding)
}
class ProviderOAuthObservation(val binding:ProviderOAuthBinding,val status:String,val deviceCode:ProviderDeviceCode?) {
    override fun toString()="ProviderOAuthObservation($status)"
}
object ProviderOAuthProtocol {
    fun providerId(value:String)=value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) && value !in setOf("sessions",".","..")
    fun sessionId(value:String)=value.matches(Regex("[A-Za-z0-9_-]{16,128}"))
    fun string(j:JsonObject,key:String,max:Int):String {
        val value=j[key] as? JsonPrimitive ?: error("Invalid provider response.")
        require(value.isString && value.content.isNotBlank() && value.content.length<=max && value.content.none(Char::isISOControl))
        return value.content
    }
    fun boolean(j:JsonObject,key:String):Boolean {val v=j[key] as? JsonPrimitive ?: error("Invalid provider response.");require(!v.isString);return v.booleanOrNull ?: error("Invalid provider response.")}
    fun integer(j:JsonObject,key:String):Long {val v=j[key] as? JsonPrimitive ?: error("Invalid provider response.");require(!v.isString);return v.longOrNull ?: error("Invalid provider response.")}
    fun start(j:JsonObject,home:String):JsonObject {
        val id=string(j,"session_id",128);require(sessionId(id) && string(j,"flow",64)=="device_code")
        return buildJsonObject {put("session_id",id);put("flow","device_code");put("home_id",home);put("status","pending")}
    }
    fun observation(j:JsonObject,binding:ProviderOAuthBinding):JsonObject {
        require(string(j,"session_id",128)==binding.sessionId)
        val status=string(j,"status",32);require(status in setOf("pending","approved","denied","expired","error","cancelled"))
        return buildJsonObject {put("session_id",binding.sessionId);put("flow","device_code");put("home_id",binding.homeId);put("status",status)}
    }
    fun cancellation(j:JsonObject,binding:ProviderOAuthBinding):JsonObject {
        val ok=boolean(j,"ok");if(ok) require(string(j,"session_id",128)==binding.sessionId) else require(j.text("message")=="session not found" && !j.containsKey("session_id"))
        return buildJsonObject {put("session_id",binding.sessionId);put("cancel_acknowledged",ok);put("session_not_found",!ok)}
    }
    fun disconnect(j:JsonObject,provider:String):JsonObject {require(boolean(j,"ok") && string(j,"provider",128)==provider);return buildJsonObject {put("provider",provider);put("disconnect_acknowledged",true)}}
}
