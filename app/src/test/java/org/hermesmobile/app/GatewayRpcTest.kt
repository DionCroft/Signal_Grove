package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GatewayRpcTest {
    private val tokens=ManagementTokens("gateway-mgt-fixture","refresh-fixture","password","fixture",4102444800)
    private val ticket="g".repeat(43)
    private val ready="""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":"private-skin-fixture"}}}"""
    private fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
    private fun ticketResponse()=json("""{"ticket":"$ticket","ttl_seconds":30}""")
    private fun upgrade(listener:WebSocketListener,protocol:String="hermes-gateway-v1")=MockResponse().withWebSocketUpgrade(listener).setHeader("Sec-WebSocket-Protocol",protocol)
    @Test fun ticketAndSocketUseSeparateCredentialsAndExactScopedReadWithoutSubscriptions()=runBlocking {
        val calls=AtomicInteger()
        MockWebServer().use {server ->
            server.enqueue(ticketResponse());server.enqueue(upgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send(ready)}
                override fun onMessage(webSocket:WebSocket,text:String) {
                    val request=Json.parseToJsonElement(text).jsonObject;assertEquals("cron.manage",request.text("method"));assertEquals(buildJsonObject {put("profile","worker");put("action","list");put("include_disabled",true)},request["params"]);calls.incrementAndGet()
                    webSocket.send(buildJsonObject {put("jsonrpc","2.0");put("id",request["id"]!!);put("result",buildJsonObject {put("success",true);put("scoped","worker");put("count",0);put("jobs",JsonArray(emptyList()));put("gateway_running",false)})}.toString())
                }
            }))
            val api=ManagementClient(server.url("prefix/").toString(),true,true)
            api.gateway(tokens).use {session ->assertEquals(false,GatewayCronInventory.parse(session.cronList("worker"),"worker").gatewayRunning)}
            val mint=server.takeRequest();assertEquals("POST",mint.method);assertEquals("/prefix/api/auth/ws-ticket",mint.path);assertEquals("Bearer gateway-mgt-fixture",mint.getHeader("Authorization"));assertNull(mint.getHeader("Cookie"))
            val socket=server.takeRequest();assertEquals("/prefix/api/ws",socket.path);assertNull(socket.getHeader("Authorization"));assertNull(socket.getHeader("Cookie"));assertEquals("hermes-gateway-v1, hermes-gateway-ticket.$ticket",socket.getHeader("Sec-WebSocket-Protocol"));assertFalse(socket.requestUrl.toString().contains(ticket));assertEquals(1,calls.get())
        }
    }
    @Test fun incorrectProtocolOrMissingReadyCannotAdmitRequestsOrReuseTickets()=runBlocking {
        for(mode in listOf("protocol","closed")) MockWebServer().use {server ->
            val messages=AtomicInteger();server.enqueue(ticketResponse());server.enqueue(upgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {if(mode=="closed") webSocket.close(1000,"fixture") else webSocket.send(ready)}
                override fun onMessage(webSocket:WebSocket,text:String) {messages.incrementAndGet()}
            },if(mode=="protocol") "unsupported-fixture" else "hermes-gateway-v1"))
            val api=ManagementClient(server.url("/").toString(),true,true)
            val failure=withTimeout(5000) {runCatching {api.gateway(tokens).use {it.cronList("worker")}}.exceptionOrNull()}
            assertNotNull(failure);assertEquals(2,server.requestCount);assertEquals(0,messages.get());assertFalse(failure!!.toString().contains(ticket))
        }
    }
    @Test fun wrongIdsMalformedErrorsAndBinaryFramesFailPendingRequestsPromptly()=runBlocking {
        for(mode in listOf("id","error","binary","multibyte")) MockWebServer().use {server ->
            server.enqueue(ticketResponse());server.enqueue(upgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send(ready)}
                override fun onMessage(webSocket:WebSocket,text:String) {
                    val id=Json.parseToJsonElement(text).jsonObject["id"]!!
                    when(mode) {
                        "id"->webSocket.send("""{"jsonrpc":"2.0","id":"wrong","result":{}}""")
                        "error"->webSocket.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("error",buildJsonObject {put("code","not-an-int");put("message","private-error-fixture")})}.toString())
                        "multibyte"->webSocket.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("result",buildJsonObject {put("oversized","界".repeat(700000))})}.toString())
                        else->webSocket.send(okio.ByteString.of(1,2,3))
                    }
                }
            }))
            val api=ManagementClient(server.url("/").toString(),true,true)
            val failure=withTimeout(5000) {runCatching {api.gateway(tokens).use {it.cronList("worker")}}.exceptionOrNull()}
            assertTrue("$mode: ${failure?.javaClass?.simpleName}",failure is GatewayProtocolException);assertFalse(failure.toString().contains("private-error-fixture"));assertEquals(2,server.requestCount)
        }
    }
    @Test fun typedRpcErrorsRetainOnlyCodesAndNeverReflectRawServerMessages()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(ticketResponse());server.enqueue(upgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send(ready)}
                override fun onMessage(webSocket:WebSocket,text:String) {
                    val id=Json.parseToJsonElement(text).jsonObject["id"]!!
                    webSocket.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("error",buildJsonObject {put("code",4064);put("message","private-profile-home-fixture")})}.toString())
                }
            }))
            val api=ManagementClient(server.url("/").toString(),true,true)
            val failure=runCatching {api.gateway(tokens).use {it.cronList("worker")}}.exceptionOrNull()
            assertTrue(failure is GatewayRpcException);assertEquals(4064,(failure as GatewayRpcException).code);assertFalse(failure.toString().contains("private-profile-home-fixture"))
        }
    }
    @Test fun invalidTicketOrAggregateProfileNeverFallsBackToOtherCredentialsOrScope()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(json("""{"ticket":"has space","ttl_seconds":30}"""));val api=ManagementClient(server.url("/").toString(),true,true)
            assertTrue(runCatching {api.gateway(tokens)}.isFailure);assertEquals(1,server.requestCount)
        }
        MockWebServer().use {server ->
            val messages=AtomicInteger();server.enqueue(ticketResponse());server.enqueue(upgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send(ready)}
                override fun onMessage(webSocket:WebSocket,text:String) {messages.incrementAndGet()}
            }))
            val api=ManagementClient(server.url("/").toString(),true,true);api.gateway(tokens).use {session ->listOf("","all"," ALL ","current","hermes",".hermes","HERMES").forEach {assertTrue(runCatching {session.cronList(it)}.isFailure)}}
            assertEquals(0,messages.get());assertEquals(2,server.requestCount)
        }
    }
    @Test fun cancellationClosesTheSingleConnectionWithoutRepeatingTheRead()=runBlocking {
        val received=CompletableDeferred<Unit>();val calls=AtomicInteger()
        MockWebServer().use {server ->
            server.enqueue(ticketResponse());server.enqueue(upgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send(ready)}
                override fun onMessage(webSocket:WebSocket,text:String) {calls.incrementAndGet();received.complete(Unit)}
            }))
            val api=ManagementClient(server.url("/").toString(),true,true)
            val work=launch {api.gateway(tokens).use {it.cronList("worker")}};withTimeout(5000) {received.await()};work.cancelAndJoin();assertEquals(1,calls.get());assertEquals(2,server.requestCount)
        }
    }
    @Test fun scopeProofCountsDuplicateIdsAndTypedStatesAreMandatory() {
        fun parse(s:String)=GatewayCronInventory.parse(Json.parseToJsonElement(s).jsonObject,"worker")
        assertEquals(0,parse("""{"success":true,"scoped":"worker","count":0,"jobs":[]}""").jobs.size)
        listOf("""{"success":true,"count":0,"jobs":[]}""","""{"success":true,"scoped":"other","count":0,"jobs":[]}""","""{"success":true,"scoped":"worker","count":1,"jobs":[]}""","""{"success":true,"scoped":"worker","count":2,"jobs":[{"job_id":"same"},{"job_id":"same"}]}""","""{"success":true,"scoped":"worker","count":1,"jobs":[{"job_id":"fixture","enabled":"false"}]}""").forEach {assertTrue(runCatching {parse(it)}.isFailure)}
        assertNull(parse("""{"success":true,"scoped":"worker","count":1,"jobs":[{"job_id":"fixture"}]}""").jobs.single().enabled)
    }
}
