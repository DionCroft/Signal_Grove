package org.hermesmobile.app.network

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okio.ByteString
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class GatewayProtocolException:Exception("Unsupported gateway response.")
class GatewayUnavailable:Exception("Gateway connection unavailable.")
class GatewayRpcException(val code:Int,val sourceMoved:Boolean=false):Exception("Gateway request refused ($code).")

/** One explicitly opened operator connection. No reconnect, mutation replay, server-request opt-in or transcript subscription. */
class GatewayRpcSession private constructor():Closeable {
    private val opened=CompletableDeferred<Unit>()
    private val ready=CompletableDeferred<Unit>()
    private val stopped=AtomicBoolean(false)
    private val ids=AtomicLong()
    private val received=AtomicLong()
    private val notifications=AtomicLong()
    private val pending=ConcurrentHashMap<String,CompletableDeferred<JsonObject>>()
    @Volatile private var socket:WebSocket?=null
    private fun fail(error:Exception) {
        stopped.set(true);opened.completeExceptionally(error);ready.completeExceptionally(error)
        pending.values.forEach {it.completeExceptionally(error)};pending.clear();socket?.cancel()
    }
    private fun accept(text:String) {
        if(stopped.get()) return
        try {
            if(text.length>2_000_000) throw GatewayProtocolException()
            val bytes=text.toByteArray(Charsets.UTF_8).size
            if(bytes>2_000_000 || received.addAndGet(bytes.toLong())>8_000_000) throw GatewayProtocolException()
            val j=Json.parseToJsonElement(text).jsonObject
            if(j["jsonrpc"]!=JsonPrimitive("2.0")) throw GatewayProtocolException()
            val id=j["id"]
            if(id==null) {
                // Never advertise server_requests: this client does not answer tools, sudo, approvals or clarification RPCs.
                if(j["method"]!=JsonPrimitive("event") || notifications.incrementAndGet()>256) throw GatewayProtocolException()
                val params=j["params"] as? JsonObject ?: throw GatewayProtocolException()
                if(params["type"]==JsonPrimitive("gateway.ready")) ready.complete(Unit)
                return
            }
            if(id !is JsonPrimitive || !id.isString) throw GatewayProtocolException()
            val waiter=pending.remove(id.content) ?: throw GatewayProtocolException()
            if(j.containsKey("method") || j.containsKey("result")==j.containsKey("error")) {waiter.completeExceptionally(GatewayProtocolException());throw GatewayProtocolException()}
            if(j.containsKey("error")) {
                val error=j["error"] as? JsonObject
                val code=(error?.get("code") as? JsonPrimitive)?.takeIf {!it.isString}?.intOrNull
                if(code==null) {waiter.completeExceptionally(GatewayProtocolException());throw GatewayProtocolException()}
                waiter.completeExceptionally(GatewayRpcException(code,(error?.get("data") as? JsonObject)?.get("reason")==JsonPrimitive("source_moved")))
            } else {
                val result=j["result"] as? JsonObject
                if(result==null) {waiter.completeExceptionally(GatewayProtocolException());throw GatewayProtocolException()}
                waiter.complete(result)
            }
        } catch(e:Exception) {fail(if(e is GatewayProtocolException) e else GatewayProtocolException())}
    }
    suspend fun installPlugin(profile:String,request:org.hermesmobile.app.data.PluginInstallRequest)=call("plugins.manage",request.params(profile),600000)
    suspend fun featuredPlugins(profile:String):JsonObject {
        org.hermesmobile.app.data.GatewayCronInventory.requireProfile(profile)
        return call("plugins.manage",buildJsonObject {put("profile",profile);put("action","onboarding")},90000)
    }
    suspend fun lookupPlugin(profile:String,name:String):JsonObject {
        org.hermesmobile.app.data.GatewayCronInventory.requireProfile(profile)
        require(org.hermesmobile.app.data.PluginSource.valid(org.hermesmobile.app.data.PluginSourceKind.Catalog,name))
        return call("plugins.catalog.lookup",buildJsonObject {put("profile",profile);put("name",name)})
    }
    suspend fun inspectPlugin(profile:String,source:org.hermesmobile.app.data.PluginSource)=call("plugins.manage",source.params(profile),180000)
    suspend fun plugins(profile:String):JsonObject {
        org.hermesmobile.app.data.GatewayCronInventory.requireProfile(profile)
        return call("plugins.manage",buildJsonObject {put("profile",profile);put("action","list")})
    }
    suspend fun cronList(profile:String):JsonObject {
        org.hermesmobile.app.data.GatewayCronInventory.requireProfile(profile)
        return call("cron.manage",buildJsonObject {put("profile",profile);put("action","list");put("include_disabled",true)})
    }
    suspend fun cronControl(profile:String,request:org.hermesmobile.app.data.GatewayCronRequest)=call("cron.manage",request.params(profile))
    private suspend fun call(method:String,params:JsonObject,timeoutMillis:Long=45000):JsonObject {
        check(!stopped.get());val id=ids.incrementAndGet().toString();val waiter=CompletableDeferred<JsonObject>()
        require(pending.size<8);pending[id]=waiter
        try {
            if(stopped.get() || socket?.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("method",method);put("params",params)}.toString())!=true) throw GatewayUnavailable()
            return withTimeoutOrNull(timeoutMillis) {waiter.await()} ?: throw GatewayUnavailable()
        } catch(e:Exception) {fail(if(e is GatewayRpcException || e is GatewayProtocolException) e else GatewayUnavailable());throw e}
        finally {pending.remove(id);waiter.cancel()}
    }
    override fun close() {fail(GatewayUnavailable())}
    override fun toString()="GatewayRpcSession(redacted)"
    companion object {
        suspend fun connect(http:OkHttpClient,base:HttpUrl,ticket:String):GatewayRpcSession {
            require(ticket.matches(Regex("[A-Za-z0-9_-]{16,8192}"))) {"Invalid gateway ticket."}
            val connection=GatewayRpcSession()
            try {
                val request=Request.Builder().url(base.newBuilder().addPathSegments("api/ws").build())
                    .header("Sec-WebSocket-Protocol","hermes-gateway-v1, hermes-gateway-ticket.$ticket").build()
                connection.socket=http.newWebSocket(request,object:WebSocketListener() {
                    override fun onOpen(webSocket:WebSocket,response:Response) {
                        if(response.header("Sec-WebSocket-Protocol")!="hermes-gateway-v1") connection.fail(GatewayProtocolException())
                        else connection.opened.complete(Unit)
                    }
                    override fun onMessage(webSocket:WebSocket,text:String)=connection.accept(text)
                    override fun onMessage(webSocket:WebSocket,bytes:ByteString)=connection.fail(GatewayProtocolException())
                    override fun onClosing(webSocket:WebSocket,code:Int,reason:String)=connection.fail(GatewayUnavailable())
                    override fun onClosed(webSocket:WebSocket,code:Int,reason:String)=connection.fail(GatewayUnavailable())
                    override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?)=connection.fail(GatewayUnavailable())
                })
                if(withTimeoutOrNull(30000) {connection.opened.await();connection.ready.await();true}!=true) throw GatewayUnavailable()
                if(connection.stopped.get()) throw GatewayUnavailable()
                return connection
            } catch(e:Exception) {connection.close();throw e}
        }
    }
}
