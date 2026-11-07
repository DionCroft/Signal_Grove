package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.network.GatewayAuthorityAdvertisement
import org.hermesmobile.app.network.GatewayProtocolException
import org.hermesmobile.app.network.GatewayRpcSession
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GatewayAuthorityAdvertisementTest {
    private class Fixture(
        val payload:JsonElement?, val duplicate:JsonElement?=null, val corruptReply:Boolean=false
    ):Closeable {
        val server=MockWebServer()
        val http=OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        val ticket="a".repeat(43)
        val calls=CopyOnWriteArrayList<JsonObject>()
        val error=AtomicReference<Throwable?>()
        val expected=buildJsonObject {put("projects",JsonArray(emptyList()));put("active_id",JsonNull)}
        private fun frame(value:JsonElement?)=buildJsonObject {
            put("jsonrpc","2.0");put("method","event")
            put("params",buildJsonObject {put("type","gateway.ready");if(value!=null) put("payload",value)})
        }.toString()
        init {
            server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(socket:WebSocket,response:Response) {socket.send(frame(payload))}
                override fun onMessage(socket:WebSocket,text:String) {
                    try {
                        val request=Json.parseToJsonElement(text).jsonObject;calls.add(request)
                        check(request["method"]==JsonPrimitive("projects.list"))
                        check(request["params"]==buildJsonObject {put("profile","worker")})
                        if(duplicate!=null) socket.send(frame(duplicate))
                        socket.send(buildJsonObject {
                            put("jsonrpc","2.0");put("id",if(corruptReply) JsonPrimitive("foreign-fixture-id") else request["id"]!!)
                            put("result",expected)
                        }.toString())
                    } catch(problem:Throwable) {error.set(problem);socket.close(1011,"fixture-error")}
                }
            }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1"))
        }
        fun assertBoundary() {
            assertNull(error.get())
            val handshake=server.takeRequest(5,TimeUnit.SECONDS)!!
            assertEquals("/prefix/api/ws",handshake.path)
            assertEquals("hermes-gateway-v1, hermes-gateway-ticket.$ticket",handshake.getHeader("Sec-WebSocket-Protocol"))
            for(header in listOf("Authorization","Cookie","Origin")) assertNull(handshake.getHeader(header))
            assertEquals(1,calls.size)
            assertEquals(setOf("jsonrpc","id","method","params"),calls.single().keys)
            assertEquals(buildJsonObject {put("profile","worker")},calls.single()["params"])
        }
        override fun close() {server.close();http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()}
    }
    private fun payload(text:String)=Json.parseToJsonElement(text)
    private fun exercise(value:JsonElement?,expected:GatewayAuthorityAdvertisement)=runBlocking {
        Fixture(value).use {f->
            val socket=withTimeout(5000) {GatewayRpcSession.connect(f.http,f.server.url("prefix/"),f.ticket)}
            try {
                assertEquals(expected,socket.sessionAuthorityAdvertisement)
                assertEquals(f.expected,withTimeout(5000) {socket.projects("worker")})
                assertEquals(expected,socket.sessionAuthorityAdvertisement)
            } finally {socket.close()}
            assertEquals(GatewayAuthorityAdvertisement.Unknown,socket.sessionAuthorityAdvertisement)
            f.assertBoundary()
        }
    }
    @Test fun legacyAndExplicitFalseReadyKeepScopedMetadataAvailableWithoutAdvertisingAuthority() {
        for(value in listOf(null,payload("{}"),payload("""{"session_authority":false}""")))
            exercise(value,GatewayAuthorityAdvertisement.NotAdvertised)
    }
    @Test fun strictTrueReportsOnlyAnAdvertisementAndSendsNoGrantOrSubscription() {
        exercise(payload("""{"session_authority":true,"capabilities":["session:operator"],"profile_id":"spoofed-profile-fixture","instance_id":"private-instance-fixture","skin":"private-skin-fixture"}"""),GatewayAuthorityAdvertisement.Advertised)
        assertEquals("Advertised",GatewayAuthorityAdvertisement.Advertised.toString())
    }
    @Test fun malformedReadyIsUnknownWithoutReflectingPrivateFieldsOrBreakingMetadataReads() {
        for(value in listOf("null","[]",JsonPrimitive("private-payload-fixture").toString(),"""{"session_authority":"true"}""","""{"session_authority":1}""","""{"session_authority":null}""","""{"session_authority":[]}""","""{"session_authority":{}}"""))
            exercise(payload(value),GatewayAuthorityAdvertisement.Unknown)
    }
    @Test fun closeInvalidatesTheSnapshotAndNewConnectionsStartWithTheirOwnAdvertisement() {
        exercise(payload("""{"session_authority":true}"""),GatewayAuthorityAdvertisement.Advertised)
        exercise(null,GatewayAuthorityAdvertisement.NotAdvertised)
    }
    @Test fun laterReadyCannotPromoteTheFirstHandshakeSnapshot()=runBlocking {
        Fixture(null,payload("""{"session_authority":true}""")).use {f->
            GatewayRpcSession.connect(f.http,f.server.url("prefix/"),f.ticket).use {socket->
                assertEquals(f.expected,withTimeout(5000) {socket.projects("worker")})
                assertEquals(GatewayAuthorityAdvertisement.NotAdvertised,socket.sessionAuthorityAdvertisement)
            }
            f.assertBoundary()
        }
    }
    @Test fun protocolFailureInvalidatesAnAdvertisedSnapshotWithoutReplayingTheRead()=runBlocking {
        Fixture(payload("""{"session_authority":true}"""),corruptReply=true).use {f->
            GatewayRpcSession.connect(f.http,f.server.url("prefix/"),f.ticket).use {socket->
                assertEquals(GatewayAuthorityAdvertisement.Advertised,socket.sessionAuthorityAdvertisement)
                val failure=runCatching {withTimeout(5000) {socket.projects("worker")}}.exceptionOrNull()
                assertTrue(failure is GatewayProtocolException)
                assertEquals(GatewayAuthorityAdvertisement.Unknown,socket.sessionAuthorityAdvertisement)
                assertFalse(failure.toString().contains("private-instance-fixture"))
            }
            f.assertBoundary()
        }
    }
}
