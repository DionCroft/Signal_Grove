package org.hermesmobile.app

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okio.ByteString.Companion.encodeUtf8
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalHeartbeatTest {
    private class Fixture(val mode:String="normal"):Closeable {
        val server=MockWebServer()
        val http=OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build()
        val calls=CopyOnWriteArrayList<JsonObject>()
        val profile="/fixture/home"
        private val ready=buildJsonObject {
            put("jsonrpc","2.0");put("method","event");put("params",buildJsonObject {
                put("type","gateway.ready");put("payload",buildJsonObject {
                    put("skin",buildJsonObject {});put("change_events",true);put("replay_epoch","fixture-ring");put("session_authority",true)
                    when(mode) {
                        "no-advertisement"->{}
                        "false-advertisement"->put("heartbeat",false)
                        "string-advertisement"->put("heartbeat","true")
                        "number-advertisement"->put("heartbeat",1)
                        "null-advertisement"->put("heartbeat",JsonNull)
                        else->put("heartbeat",true)
                    }
                })
            })
        }
        private val identity=buildJsonObject {
            put("instance_id","heartbeat-runtime");put("profile_id",profile);put("authority_epoch",7)
            put("capabilities",JsonArray(listOf(JsonPrimitive("durable-admission-v1"),JsonPrimitive("event-replay-v1"))))
            put("session_create",buildJsonObject {put("sources",JsonArray(listOf(JsonPrimitive("cli"))));put("parameters",JsonArray(emptyList()))})
        }
        init {
            server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(ws:WebSocket,response:Response) {ws.send(ready.toString())}
                override fun onMessage(ws:WebSocket,text:String) {
                    val request=Json.parseToJsonElement(text).jsonObject;calls+=request
                    val method=request["method"]!!.jsonPrimitive.content;val id=request.getValue("id")
                    if(method=="runtime.describe") {
                        ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("result",identity)}.toString());return
                    }
                    check(method=="gateway.ping")
                    if(mode=="silent") return
                    if(mode=="notice") ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"sessions.changed","payload":{}}}""")
                    val result=when(mode) {
                        "authority-pong"->buildJsonObject {put("pong",true)}
                        "false-result"->buildJsonObject {put("ok",false)}
                        "empty-result"->buildJsonObject {}
                        "extra-result"->buildJsonObject {put("ok",true);put("unreviewed",true)}
                        "string-result"->buildJsonObject {put("ok","true")}
                        "scalar-result"->JsonPrimitive(true)
                        else->buildJsonObject {put("ok",true)}
                    }
                    val frame=buildJsonObject {put("jsonrpc","2.0");put("id",if(mode=="foreign-id") JsonPrimitive("foreign-id") else id);put("result",result)}.toString()
                    if(mode=="binary") ws.send(frame.encodeUtf8()) else ws.send(frame)
                }
            }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1"))
        }
        val base get()=server.url("/dashboard/")
        val target get()=ManagementTarget("b".repeat(64),base.toString(),"default","fixture · owner")
        suspend fun connect()=withTimeout(3000) {CanonicalGatewayChannel.connect(http,base,"c".repeat(43),target,checkNotNull(PluginHome.project(JsonPrimitive(profile))).identity())}
        override fun close() {http.dispatcher.executorService.shutdown();http.connectionPool.evictAll();server.close()}
    }
    @Test fun verifiedHeartbeatSendsOnlyAnEmptyGatewayPingOnTheOriginalSocket()=runBlocking {
        Fixture().use {f->val channel=f.connect()
            try {
                val binding=channel.binding;channel.heartbeat();assertSame(binding,channel.binding)
                assertEquals(listOf("runtime.describe","gateway.ping"),f.calls.map {it["method"]!!.jsonPrimitive.content})
                assertEquals(JsonObject(emptyMap()),f.calls.last()["params"]);assertEquals(1,f.server.requestCount)
                val request=f.server.takeRequest();assertEquals("/dashboard/api/ws",request.requestUrl!!.encodedPath)
                assertNull(request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"))
            } finally {channel.close()}
        }
    }
    @Test fun repeatedHeartbeatUsesDistinctIdsWithoutOpeningAnotherSocket()=runBlocking {
        Fixture().use {f->val channel=f.connect();try {
            channel.heartbeat();channel.heartbeat()
            assertEquals(3,f.calls.size);assertEquals(3,f.calls.map {it["id"]}.toSet().size);assertEquals(1,f.server.requestCount)
            assertTrue(f.calls.drop(1).all {it["method"]==JsonPrimitive("gateway.ping") && it["params"]==JsonObject(emptyMap())})
        } finally {channel.close()}}
    }
    @Test fun missingOrFalseAdvertisementRefusesBeforeSendingAPing()=runBlocking {
        for(mode in listOf("no-advertisement","false-advertisement")) Fixture(mode).use {f->val channel=f.connect();try {
            assertTrue(runCatching {channel.heartbeat()}.exceptionOrNull() is CanonicalNotSupported)
            assertEquals(1,f.calls.size);assertEquals(JsonPrimitive("runtime.describe"),f.calls.single()["method"])
        } finally {channel.close()}}
    }
    @Test fun authorityPongFalseAndExtendedResultsAreNotAcceptedAsWsLiveness()=runBlocking {
        for(mode in listOf("authority-pong","false-result","empty-result","extra-result","string-result","scalar-result")) Fixture(mode).use {f->val channel=f.connect();try {
            assertTrue(mode,runCatching {withTimeout(3000) {channel.heartbeat()}}.exceptionOrNull() is GatewayProtocolException)
            assertTrue(runCatching {channel.heartbeat()}.isFailure);assertEquals(2,f.calls.size);assertEquals(1,f.server.requestCount)
        } finally {channel.close()}}
    }
    @Test fun foreignResponseIdentityCannotSettleThePendingHeartbeat()=runBlocking {
        Fixture("foreign-id").use {f->val channel=f.connect();try {
            assertTrue(runCatching {withTimeout(3000) {channel.heartbeat()}}.exceptionOrNull() is GatewayProtocolException)
            assertEquals(2,f.calls.size);assertEquals(1,f.server.requestCount)
        } finally {channel.close()}}
    }
    @Test fun cancelledHeartbeatStopsTheChannelWithoutRetryOrReconnect()=runBlocking {
        Fixture("silent").use {f->val channel=f.connect();try {
            assertTrue(runCatching {withTimeout(300) {channel.heartbeat()}}.exceptionOrNull() is TimeoutCancellationException)
            assertTrue(runCatching {channel.heartbeat()}.isFailure);assertEquals(2,f.calls.size);assertEquals(1,f.server.requestCount)
        } finally {channel.close()}}
    }
    @Test fun binaryHeartbeatResponseClosesInsteadOfReinterpretingBytes()=runBlocking {
        Fixture("binary").use {f->val channel=f.connect();try {
            assertTrue(runCatching {withTimeout(3000) {channel.heartbeat()}}.exceptionOrNull() is GatewayProtocolException)
            assertEquals(2,f.calls.size);assertEquals(1,f.server.requestCount)
        } finally {channel.close()}}
    }
    @Test fun hostInventoryNoticeDoesNotSubstituteForTheMatchingHeartbeatReply()=runBlocking {
        Fixture("notice").use {f->val channel=f.connect();try {
            withTimeout(3000) {channel.heartbeat()};assertEquals(2,f.calls.size);assertEquals(1,f.server.requestCount)
        } finally {channel.close()}}
    }
    @Test fun malformedHeartbeatAdvertisementFailsBeforeRuntimeCalls()=runBlocking {
        for(mode in listOf("string-advertisement","number-advertisement","null-advertisement")) Fixture(mode).use {f->
            assertTrue(mode,runCatching {f.connect()}.exceptionOrNull() is GatewayProtocolException)
            assertTrue(f.calls.isEmpty())
        }
    }
}
