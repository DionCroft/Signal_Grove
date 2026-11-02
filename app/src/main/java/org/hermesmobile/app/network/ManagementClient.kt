package org.hermesmobile.app.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.hermesmobile.app.data.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class ManagementTokens(val access:String,val refresh:String,val provider:String,val userId:String,val expiresAt:Long) {
    fun json()=buildJsonObject {put("access_token",access);put("refresh_token",refresh);put("provider",provider);put("user_id",userId);put("expires_at",expiresAt);put("token_type","Bearer")}
    override fun toString()="ManagementTokens(redacted)"
    companion object {
        fun parse(j:JsonObject):ManagementTokens {
            require(j.text("token_type").equals("Bearer",ignoreCase=true)) {"Unsupported management sign-in response."}
            fun field(name:String,max:Int)=j.text(name).also {require(it.isNotBlank() && it.length<=max && it.none(Char::isISOControl)) {"Invalid management sign-in response."}}
            val expires=(j["expires_at"] as? JsonPrimitive)?.longOrNull ?: error("Missing management expiry.")
            require(expires>0)
            val access=field("access_token",16384);require(access.all {it.code in 33..126}) {"Invalid management sign-in response."}
            return ManagementTokens(access,field("refresh_token",16384),field("provider",128),field("user_id",512),expires)
        }
    }
}
data class ManagementIdentity(val userId:String,val displayName:String,val provider:String)
data class ManagedProfile(val name:String,val displayName:String)

/** Dashboard listener and native session credentials are independent of the inference API. */
class ManagementClient(url:String,debugHttp:Boolean,debugBuild:Boolean=false,localNetworkAllowed:()->Boolean={true}) {
    val base=OkHttpHermesApiClient.validateBaseUrl(url,debugHttp,debugBuild)
    private val dns=PermissionAwareDns(localNetworkAllowed)
    private val http=OkHttpClient.Builder().dns(dns).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
        .writeTimeout(30,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private suspend fun json(path:String,token:String?=null,body:JsonObject?=null,profile:String?=null,method:String=if(body==null) "GET" else "POST",tail:List<String> = emptyList()):JsonObject=suspendCancellableCoroutine {continuation ->
        dns.checkHost(base.host)
        val endpoint=base.newBuilder().addPathSegments(path).apply {tail.forEach {addPathSegment(it)};profile?.let {require(it.isNotBlank() && it.length<=128);addQueryParameter("profile",it)}}.build()
        val request=Request.Builder().url(endpoint).header("Accept","application/json").apply {
            token?.let {header("Authorization","Bearer $it")}
            method(method,body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()))
        }.build()
        val call=http.newCall(request);continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {if(continuation.isActive) continuation.resumeWith(Result.failure(e))}
            override fun onResponse(call:Call,response:Response) {
                val result=runCatching {response.use {
                    if(!it.isSuccessful) throw HermesHttpException.from(it)
                    require(it.header("Content-Type").orEmpty().substringBefore(';').trim().equals("application/json",ignoreCase=true)) {"Management server returned a non-JSON response."}
                    if(it.body.contentLength()>2_000_000) throw HermesResponseTooLargeException()
                    val source=it.body.source();val buffer=okio.Buffer()
                    while(buffer.size<=2_000_000 && source.read(buffer,8192)!=-1L) {}
                    if(buffer.size>2_000_000) throw HermesResponseTooLargeException()
                    Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                }}
                if(continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
    suspend fun exchange(code:String,verifier:String)=ManagementTokens.parse(json("auth/native/token",body=buildJsonObject {put("code",code);put("code_verifier",verifier)}))
    suspend fun refresh(tokens:ManagementTokens)=ManagementTokens.parse(json("auth/native/refresh",body=buildJsonObject {put("refresh_token",tokens.refresh);put("provider",tokens.provider)})).also {
        require(it.userId==tokens.userId && it.provider==tokens.provider) {"Management identity changed. Sign in again."}
    }
    suspend fun identity(tokens:ManagementTokens):ManagementIdentity {
        val j=json("api/auth/me",tokens.access)
        require(j.text("user_id")==tokens.userId && j.text("provider")==tokens.provider) {"Management identity verification failed."}
        return ManagementIdentity(tokens.userId,j.text("display_name").take(160),tokens.provider)
    }
    suspend fun profiles(tokens:ManagementTokens):List<ManagedProfile> {
        val j=json("api/profiles",tokens.access);require(j["profiles"] is JsonArray)
        val rows=j.items("profiles");require(rows.size<=500)
        return rows.map {row ->val name=row.text("name");require(name.isNotBlank() && name.length<=128 && name.none(Char::isISOControl));ManagedProfile(name,row.text("display_name").take(160))}.distinctBy {it.name}
    }
    suspend fun mcpControl(tokens:ManagementTokens,profile:String,request:McpRequest):JsonObject {
        val tail=listOf(request.name)+when(request.action) {McpAction.Toggle->listOf("enabled");McpAction.Probe->listOf("test");McpAction.Delete->emptyList()}
        val method=when(request.action) {McpAction.Toggle->"PUT";McpAction.Probe->"POST";McpAction.Delete->"DELETE"}
        return json("api/mcp/servers",tokens.access,if(request.action==McpAction.Delete) null else request.body(profile),profile,method,tail)
    }
    suspend fun mcp(tokens:ManagementTokens,profile:String)=json("api/mcp/servers",tokens.access,profile=profile)
}
