package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.network.GatewayProtocolException
import org.hermesmobile.app.network.GatewayRpcSession
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GatewayRpcBoundaryTest {
    private class Fixture(val mode:String):Closeable {
        val server=MockWebServer()
        val http=OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        val calls=CopyOnWriteArrayList<JsonObject>()
        val error=AtomicReference<Throwable?>()
        val ticket="p".repeat(43)
        val expected=buildJsonObject {put("projects",JsonArray(emptyList()));put("active_id",JsonNull)}
        init {
            server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready"}}""")
                }
                override fun onMessage(webSocket:WebSocket,text:String) {
                    try {
                        val request=Json.parseToJsonElement(text).jsonObject
                        calls.add(request)
                        check(request["method"]==JsonPrimitive("projects.list"))
                        when(mode) {
                            "matching-request"->webSocket.send(buildJsonObject {
                                put("jsonrpc","2.0");put("id",request["id"]!!);put("method","preview.act")
                                put("params",buildJsonObject {put("action","vault");put("vault",buildJsonObject {put("operation","evaluate");put("expression","synthetic-expression-only")})})
                            }.toString())
                            "foreign-response"->webSocket.send("""{"jsonrpc":"2.0","id":"srq-foreign-fixture","result":{"value":"synthetic-answer"}}""")
                            else->{
                                if(mode=="approval-event") webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"approval.request","payload":{"request_id":"synthetic-decision"}}}""")
                                webSocket.send(buildJsonObject {put("jsonrpc","2.0");put("id",request["id"]!!);put("result",expected)}.toString())
                            }
                        }
                    } catch(problem:Throwable) {
                        error.set(problem);webSocket.close(1011,"fixture-error")
                    }
                }
            }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1"))
        }
        override fun close() {
            server.close();http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()
        }
    }
    private fun exercise(mode:String,refused:Boolean)=runBlocking {
        Fixture(mode).use {f->
            val socket=withTimeout(5000) {GatewayRpcSession.connect(f.http,f.server.url("prefix/"),f.ticket)}
            try {
                val result=runCatching {withTimeout(5000) {socket.projects("worker")}}
                if(refused) assertTrue("Unexpected server frame must fail the pending read",result.exceptionOrNull() is GatewayProtocolException)
                else assertEquals(f.expected,result.getOrThrow())
            } finally {socket.close()}
            assertNull(f.error.get())
            val request=f.server.takeRequest(5,TimeUnit.SECONDS)!!
            assertEquals("/prefix/api/ws",request.path)
            assertEquals("hermes-gateway-v1, hermes-gateway-ticket.${f.ticket}",request.getHeader("Sec-WebSocket-Protocol"))
            assertNull(request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"));assertNull(request.getHeader("Origin"))
            assertEquals(1,f.calls.size)
            val only=f.calls.single()
            assertEquals(setOf("jsonrpc","id","method","params"),only.keys)
            assertEquals(JsonPrimitive("2.0"),only["jsonrpc"]);assertEquals(JsonPrimitive("1"),only["id"])
            assertEquals(JsonPrimitive("projects.list"),only["method"])
            assertEquals(buildJsonObject {put("profile","worker")},only["params"])
        }
    }
    @Test fun serverRequestCannotAcknowledgeAMatchingClientRequest()=exercise("matching-request",true)
    @Test fun foreignSidecarResponseCannotSettleThePendingRead()=exercise("foreign-response",true)
    @Test fun ordinaryReplyPreservesProfilePrefixAndTicketWithoutOptingIntoServerRequests()=exercise("normal",false)
    @Test fun unsolicitedApprovalEventDoesNotSendAnApprovalOrAdvertiseCapabilities()=exercise("approval-event",false)
}
