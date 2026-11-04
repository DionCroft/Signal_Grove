package org.hermesmobile.app.data

import kotlinx.serialization.json.*

data class ManagedServer(val name:String,val transport:String,val auth:String,val enabled:Boolean,val source:String,val tools:List<String>)
enum class McpAction {Toggle,Delete,Probe}
data class ManagementTarget(val apiScope:String,val url:String,val profile:String,val actor:String) {
    init {require(apiScope.matches(Regex("[a-f0-9]{64}")));require(profile.isNotBlank() && profile.length<=128 && profile.none(Char::isISOControl));require(actor.isNotBlank() && actor.length<=1024)}
    val resourceScope:String get()=managementResourceScope(url,profile)
}
data class McpRequest(val action:McpAction,val name:String,val expectedEnabled:Boolean,val expectedSource:String,val enabled:Boolean?=null) {
    init {
        require(McpProtocol.addressable(name)) {"This MCP name cannot be addressed by the supported route."}
        require(action==McpAction.Probe || expectedSource=="config") {"Plugin-managed MCP configuration cannot be changed here."}
        require((action==McpAction.Toggle)==(enabled!=null))
    }
    fun body(profile:String)=buildJsonObject {if(action==McpAction.Toggle) {put("enabled",enabled!!);put("profile",profile)}}
    // No credentials, command arguments, URLs or environment values in the durable receipt.
    fun summary()=buildJsonObject {put("action",action.name);put("name",name);put("source",expectedSource);put("previous_enabled",expectedEnabled);enabled?.let {put("enabled",it)}}.toString()
}
object McpProtocol {
    fun addressable(name:String)=name.isNotBlank() && name.length<=256 && name !in setOf(".","..") && name.none {it.isISOControl() || it=='/' || it=='\\'}
    fun inventory(j:JsonObject):List<ManagedServer> {
        val rows=j["servers"] as? JsonArray ?: error("Missing MCP inventory.")
        require(rows.size<=500)
        return rows.map {value ->
            val row=value.jsonObject;val name=row.text("name")
            require(name.isNotBlank() && name.length<=256 && name.none(Char::isISOControl))
            ManagedServer(name,row.text("transport").take(32),row.text("auth").take(64),row["enabled"]?.jsonPrimitive?.booleanOrNull ?: error("Invalid MCP enabled state."),row.text("source").take(32),(row["tools"] as? JsonArray).orEmpty().take(100).mapNotNull {(it as? JsonPrimitive)?.contentOrNull?.take(256)})
        }.also {rows->require(rows.map {it.name}.distinct().size==rows.size) {"Duplicate MCP identity."}}
    }
    fun acknowledge(request:McpRequest,j:JsonObject):String {
        val ok=j["ok"]?.jsonPrimitive?.booleanOrNull ?: error("MCP acknowledgement is missing.")
        return when(request.action) {
            McpAction.Toggle->{require(ok && j.text("name")==request.name && j["enabled"]?.jsonPrimitive?.booleanOrNull==request.enabled);buildJsonObject {put("ok",true);put("enabled",request.enabled!!)}.toString()}
            McpAction.Delete->{require(ok && (!j.containsKey("name") || j.text("name")==request.name));"{\"ok\":true}"}
            McpAction.Probe->{
                val tools=j["tools"] as? JsonArray ?: error("MCP probe result is missing.");require(tools.size<=5000)
                val names=tools.take(100).map {it.jsonObject.text("name").also {name->require(name.isNotBlank() && name.length<=256 && name.none(Char::isISOControl))}}
                buildJsonObject {put("ok",ok);put("tool_count",tools.size);put("tools",JsonArray(names.map(::JsonPrimitive)))}.toString()
            }
        }
    }
    fun observed(request:McpRequest,servers:List<ManagedServer>):Boolean {
        val row=servers.singleOrNull {it.name==request.name}
        return when(request.action) {McpAction.Toggle->row?.enabled==request.enabled;McpAction.Delete->row==null;McpAction.Probe->false}
    }
}
