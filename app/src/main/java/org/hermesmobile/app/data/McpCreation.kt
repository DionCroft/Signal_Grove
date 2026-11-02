package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class McpTransport {Http,Stdio}
enum class McpAuth {None,Header,OAuth}
enum class McpSetupIssue {Name,Url,Command,Arguments,Environment,Authentication,Token}
class InvalidMcpSetup(val issue:McpSetupIssue):Exception()
class McpCreation(val name:String,val transport:McpTransport,val url:String="",val command:String="",args:List<String> = emptyList(),
    environment:Map<String,String> = emptyMap(),val auth:McpAuth=McpAuth.None,private val bearerToken:String="") {
    val args:List<String> = java.util.Collections.unmodifiableList(args.toList())
    private val environment=environment.toMap()
    init {
        fun check(value:Boolean,issue:McpSetupIssue) {if(!value) throw InvalidMcpSetup(issue)}
        check(McpProtocol.addressable(name) && name==name.trim(),McpSetupIssue.Name)
        check(this.args.size<=64 && this.args.all {it.length<=2048 && it.none(Char::isISOControl)},McpSetupIssue.Arguments)
        check(this.environment.size<=32 && this.environment.all {(key,value)->key.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")) && value.length<=8192 && '\u0000' !in value},McpSetupIssue.Environment)
        if(transport==McpTransport.Http) {
            check(command.isBlank() && this.args.isEmpty() && this.environment.isEmpty(),McpSetupIssue.Authentication)
            val endpoint=runCatching {url.toHttpUrl()}.getOrNull()
            check(endpoint!=null && url.none(Char::isISOControl) && '\\' !in url && url==url.trim() && url.length<=8192 && endpoint.username.isEmpty() && endpoint.password.isEmpty() && endpoint.fragment==null,McpSetupIssue.Url)
            if(auth==McpAuth.Header) check(bearerToken.isNotBlank() && bearerToken.length<=16384 && bearerToken.all {it.code in 32..126} && !bearerToken.trim().equals("Bearer",true),McpSetupIssue.Token)
            else check(bearerToken.isEmpty(),McpSetupIssue.Authentication)
        } else {
            check(url.isBlank() && auth==McpAuth.None && bearerToken.isEmpty(),McpSetupIssue.Authentication)
            check(command.isNotBlank() && command==command.trim() && command.length<=512 && command.none(Char::isISOControl),McpSetupIssue.Command)
        }
    }
    val environmentKeys get()=environment.keys.toList()
    fun reviewUrl():String=if(transport==McpTransport.Http) url.toHttpUrl().newBuilder().query(null).build().toString() else ""
    fun queryKeys():List<String> = if(transport==McpTransport.Http) url.toHttpUrl().queryParameterNames.toList() else emptyList()
    fun body(profile:String)=buildJsonObject {
        put("name",name);put("profile",profile)
        if(transport==McpTransport.Http) {
            put("url",url);put("auth",when(auth) {McpAuth.None->"none";McpAuth.Header->"header";McpAuth.OAuth->"oauth"})
            if(auth==McpAuth.Header) put("bearer_token",bearerToken)
        } else {
            put("command",command);put("args",JsonArray(args.map(::JsonPrimitive)));put("env",JsonObject(environment.mapValues {JsonPrimitive(it.value)}));put("auth","none")
        }
    }
    fun summary()=buildJsonObject {put("transport",transport.name);put("auth",auth.name);put("argument_count",args.size);put("environment_keys",JsonArray(environmentKeys.map(::JsonPrimitive)))}.toString()
    fun matches(row:JsonObject):Boolean {
        if(row.text("name")!=name || row.text("source")!="config" || row["enabled"]?.jsonPrimitive?.booleanOrNull!=true) return false
        val authValue=row.text("auth")
        return if(transport==McpTransport.Http) row.text("transport")=="http" && row.text("url")==url && when(auth) {McpAuth.None->authValue in setOf("","none");McpAuth.Header->authValue=="header";McpAuth.OAuth->authValue=="oauth"}
        else row.text("transport")=="stdio" && authValue in setOf("","none") && row.text("command")==command && (row["args"] as? JsonArray).orEmpty().map {(it as? JsonPrimitive)?.contentOrNull}==args && (row["env"] as? JsonObject).orEmpty().keys==environment.keys
    }
    override fun toString()="McpCreation(redacted)"
}
