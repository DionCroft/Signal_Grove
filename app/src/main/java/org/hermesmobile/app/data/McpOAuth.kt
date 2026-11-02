package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Collections

class McpOAuthUnavailable:Exception()

class McpOAuthSnapshot private constructor(val flowId:String,val server:String,val status:String,val authorizationUrl:String?,val tools:List<String>,val toolCount:Int?) {
    fun summary()=buildJsonObject {put("flow_id",flowId);put("status",status);toolCount?.let {put("tool_count",it)};put("tools",JsonArray(tools.map(::JsonPrimitive)))}.toString()
    fun destination()=authorizationUrl?.toHttpUrlOrNull()?.newBuilder()?.query(null)?.build()?.toString()
    override fun toString()="McpOAuthSnapshot($server, $status)"
    companion object {
        val statuses=setOf("starting","authorization_required","approved","error")
        fun validId(id:String)=id.matches(Regex("[A-Za-z0-9_-]{16,128}"))
        fun eligible(server:ManagedServer)=McpProtocol.addressable(server.name) && server.source=="config" && server.transport in setOf("http","sse") && server.auth in setOf("none","oauth")
        fun parse(j:JsonObject,server:String,expectedId:String?=null,allowHttp:Boolean=false):McpOAuthSnapshot {
            val id=j.text("flow_id");require(validId(id) && (expectedId==null || id==expectedId) && j.text("server_name")==server)
            val status=j.text("status");require(status in statuses)
            val link=if(status=="authorization_required") j.text("authorization_url").also {value ->
                require(value.isNotBlank() && value.length<=16384 && value.none(Char::isISOControl) && '\\' !in value)
                val url=value.toHttpUrlOrNull() ?: error("Invalid OAuth destination.")
                require((url.isHttps || allowHttp) && url.username.isEmpty() && url.password.isEmpty() && url.fragment==null)
            } else null
            val rows=j["tools"]?.let {if(it is JsonNull) null else it as? JsonArray ?: error("Invalid tools.")}
            require(rows==null || rows.size<=5000)
            val names=rows.orEmpty().take(100).map {it.jsonObject.text("name").also {name->require(name.isNotBlank() && name.length<=256 && name.none(Char::isISOControl))}}
            return McpOAuthSnapshot(id,server,status,link,Collections.unmodifiableList(names),rows?.size)
        }
        fun cancellation(j:JsonObject,flowId:String):String {
            require(validId(flowId) && j["ok"]?.jsonPrimitive?.booleanOrNull==true)
            val status=j.text("status");require(status in statuses || status=="expired")
            return buildJsonObject {put("flow_id",flowId);put("status",status);put("cancel_acknowledged",true)}.toString()
        }
    }
}
