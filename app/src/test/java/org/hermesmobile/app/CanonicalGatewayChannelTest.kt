package org.hermesmobile.app

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okio.ByteString.Companion.encodeUtf8
import org.hermesmobile.app.data.ManagementTarget
import org.hermesmobile.app.data.PluginHome
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalGatewayChannelTest {
    private class Fixture(val mode: String = "normal") : Closeable {
        val server = MockWebServer()
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build()
        val calls = CopyOnWriteArrayList<JsonObject>()
        val ticket = "c".repeat(43)
        val ready = """{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"replay_epoch":"fixture-replay","heartbeat":true,"session_authority":true}}}"""
        val identity = """{"instance_id":"fixture-instance","profile_id":"/fixture/default","authority_epoch":7,"capabilities":["durable-admission-v1","event-replay-v1"],"session_create":{"sources":["cli"],"parameters":["title"]}}"""
        init {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    when (mode) {
                        "before-ready" -> ws.send("""{"jsonrpc":"2.0","id":"1","result":{}}""")
                        "no-authority" -> ws.send(ready.replace(",\"session_authority\":true", ""))
                        "false-authority" -> ws.send(ready.replace("\"session_authority\":true", "\"session_authority\":false"))
                        "string-authority" -> ws.send(ready.replace("\"session_authority\":true", "\"session_authority\":\"true\""))
                        "duplicate-ready" -> { ws.send(ready); ws.send(ready) }
                        "duplicate-key" -> ws.send(ready.replace("\"session_authority\":true", "\"session_authority\":false,\"session_\\u0061uthority\":true"))
                        "deep-ready" -> ws.send("{\"nested\":".repeat(5000) + "0" + "}".repeat(5000))
                        "oversize" -> ws.send(" ".repeat(262145))
                        "binary" -> ws.send(ready.encodeUtf8())
                        else -> ws.send(ready)
                    }
                }
                override fun onMessage(ws: WebSocket, text: String) {
                    val request = Json.parseToJsonElement(text).jsonObject
                    calls.add(request)
                    when (mode) {
                        "server-request" -> ws.send("""{"jsonrpc":"2.0","id":"1","method":"approval.respond","params":{}}""")
                        "foreign-response" -> ws.send("""{"jsonrpc":"2.0","id":"srq-foreign","result":{}}""")
                        "session-event" -> ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"status.update","session_id":"unattached","payload":{}}}""")
                        "flood" -> repeat(65) { ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"sessions.changed","payload":{}}}""") }
                        "disconnect" -> ws.close(1000, "fixture")
                        else -> {
                            var result = identity
                            result = when (mode) {
                                "epoch-string" -> result.replace("\"authority_epoch\":7", "\"authority_epoch\":\"7\"")
                                "epoch-bool" -> result.replace("\"authority_epoch\":7", "\"authority_epoch\":true")
                                "epoch-fraction" -> result.replace("\"authority_epoch\":7", "\"authority_epoch\":7.5")
                                "epoch-negative" -> result.replace("\"authority_epoch\":7", "\"authority_epoch\":-1")
                                "features-missing" -> result.replace("\"event-replay-v1\"", "\"session:operator\"")
                                "features-duplicate" -> result.replace("\"event-replay-v1\"", "\"durable-admission-v1\"")
                                "descriptor-missing" -> result.replace("\"sources\":[\"cli\"]", "\"sources\":null")
                                "invalid-home" -> result.replace("/fixture/default", "not-an-absolute-home")
                                else -> result
                            }
                            if (mode == "host-notification") ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"sessions.changed","payload":{}}}""")
                            if(mode.startsWith("host-empty:")) ws.send(buildJsonObject {
                                put("jsonrpc","2.0");put("method","event");put("params",buildJsonObject {
                                    put("type",mode.substringAfter(':'));put("session_id","");put("payload",JsonObject(emptyMap()))
                                })
                            }.toString())
                            if(mode.startsWith("host-invalid:")) ws.send(buildJsonObject {
                                put("jsonrpc","2.0");put("method","event");put("params",buildJsonObject {
                                    put("type",if(mode=="host-invalid:unknown") "unreviewed.changed" else "projects.changed")
                                    put("session_id",if(mode=="host-invalid:session") "unreviewed" else "")
                                    put("payload",if(mode=="host-invalid:payload") JsonArray(emptyList()) else JsonObject(emptyMap()))
                                    if(mode=="host-invalid:sequence") put("seq",1)
                                    if(mode=="host-invalid:authority") put("authority_epoch",7)
                                })
                            }.toString())
                            ws.send("""{"jsonrpc":"2.0","id":"1","result":$result}""")
                        }
                    }
                }
            }).setHeader("Sec-WebSocket-Protocol", if (mode == "wrong-protocol") "unreviewed" else "hermes-gateway-v1"))
        }
        val base get() = server.url("/prefix/")
        val target get() = ManagementTarget("b".repeat(64), base.toString(), "default", "fixture · user")
        override fun close() {
            server.close(); http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll()
        }
    }
    private fun home(path: String = "/fixture/default") = checkNotNull(PluginHome.project(JsonPrimitive(path))).identity()
    private fun refused(mode: String, expected: Class<out Exception>, selectedHome: String = home()) = runBlocking {
        Fixture(mode).use { f ->
            val result = runCatching { withTimeout(4000) { CanonicalGatewayChannel.connect(f.http, f.base, f.ticket, f.target, selectedHome) } }
            result.getOrNull()?.close()
            assertTrue("$mode refused with the expected typed error; got ${result.exceptionOrNull()?.javaClass?.simpleName}", expected.isInstance(result.exceptionOrNull()))
            assertTrue(f.calls.all { it["method"] == JsonPrimitive("runtime.describe") && it["params"] == JsonObject(emptyMap()) })
            assertTrue(f.calls.size <= 1)
        }
    }
    private fun success(mode: String) = runBlocking {
        Fixture(mode).use { f ->
            val channel = withTimeout(4000) { CanonicalGatewayChannel.connect(f.http, f.base, f.ticket, f.target, home()) }
            assertEquals(f.target, channel.binding.target); assertEquals(home(), channel.binding.homeId)
            assertEquals("/fixture/default", channel.binding.profileId)
            assertEquals("fixture-instance", channel.binding.instanceId); assertEquals(7L, channel.binding.authorityEpoch)
            assertEquals("CanonicalRuntimeBinding(redacted)", channel.binding.toString())
            assertEquals("CanonicalGatewayChannel(redacted)", channel.toString())
            channel.close()
            assertTrue(runCatching { channel.binding }.exceptionOrNull() is GatewayUnavailable)
            val upgrade = f.server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/prefix/api/ws", upgrade.path)
            assertEquals("hermes-gateway-v1, hermes-gateway-ticket.${f.ticket}", upgrade.getHeader("Sec-WebSocket-Protocol"))
            for (header in listOf("Authorization", "Cookie", "Origin")) assertNull(upgrade.getHeader(header))
            assertEquals(1, f.calls.size)
            assertEquals(Json.parseToJsonElement("""{"jsonrpc":"2.0","id":"1","method":"runtime.describe","params":{}}"""), f.calls.single())
        }
    }
    @Test fun exactHomeIdentityAndPrefixAreBoundWithoutGrantOrSessionRequests() = success("normal")
    @Test fun boundedHostNotificationDoesNotSendOrInferAnApproval() = success("host-notification")
    @Test fun registeredEmptyScopeHostSignalsNeverBecomeReviewedTranscriptEvents() {
        listOf("sessions.changed","skin.changed","pet.changed","cron.changed","platforms.changed",
            "projects.changed","pairing.changed","skills.changed","bot_relay.outbox.pending").forEach {success("host-empty:"+it)}
    }
    @Test fun scopedHostNameCannotBypassTheUnattachedTranscriptGuard() = refused("host-invalid:session",GatewayProtocolException::class.java)
    @Test fun sequencedHostNameCannotBypassTheUnattachedTranscriptGuard() = refused("host-invalid:sequence",GatewayProtocolException::class.java)
    @Test fun authorityStampedHostNameCannotBypassTheUnattachedTranscriptGuard() = refused("host-invalid:authority",GatewayProtocolException::class.java)
    @Test fun unregisteredEmptyScopeHostNameIsRefused() = refused("host-invalid:unknown",GatewayProtocolException::class.java)
    @Test fun malformedEmptyScopeHostPayloadIsRefused() = refused("host-invalid:payload",GatewayProtocolException::class.java)
    @Test fun secondaryHomeCannotBorrowThePrimaryInstance() = refused("normal", CanonicalScopeChanged::class.java, home("/fixture/worker"))
    @Test fun invalidRuntimeHomeCannotPassTheAuthenticatedHomeFence() = refused("invalid-home", CanonicalScopeChanged::class.java)
    @Test fun missingAuthorityAdvertisementRefusesBeforeDescribe() = refused("no-authority", CanonicalNotSupported::class.java)
    @Test fun falseAuthorityAdvertisementRefusesBeforeDescribe() = refused("false-authority", CanonicalNotSupported::class.java)
    @Test fun stringAuthorityAdvertisementCannotOptIn() = refused("string-authority", CanonicalNotSupported::class.java)
    @Test fun missingReplayFeatureCannotBeReplacedByAnOperatorLabel() = refused("features-missing", CanonicalNotSupported::class.java)
    @Test fun duplicateFeaturesRefuseAmbiguousDescriptor() = refused("features-duplicate", GatewayProtocolException::class.java)
    @Test fun missingCreateDescriptorRefusesUnsupportedWire() = refused("descriptor-missing", GatewayProtocolException::class.java)
    @Test fun stringAuthorityEpochIsRefused() = refused("epoch-string", GatewayProtocolException::class.java)
    @Test fun booleanAuthorityEpochIsRefused() = refused("epoch-bool", GatewayProtocolException::class.java)
    @Test fun fractionalAuthorityEpochIsRefused() = refused("epoch-fraction", GatewayProtocolException::class.java)
    @Test fun negativeAuthorityEpochIsRefused() = refused("epoch-negative", GatewayProtocolException::class.java)
    @Test fun responseBeforeReadyCannotSettleNegotiation() = refused("before-ready", GatewayProtocolException::class.java)
    @Test fun duplicateReadyCannotReplaceTheOriginalAuthority() = refused("duplicate-ready", GatewayProtocolException::class.java)
    @Test fun escapedDuplicateAuthorityKeyCannotChangeTheReadyFlag() = refused("duplicate-key", GatewayProtocolException::class.java)
    @Test fun deepJsonIsRejectedBeforeRecursiveParsing() = refused("deep-ready", GatewayProtocolException::class.java)
    @Test fun oversizedFrameIsRefused() = refused("oversize", GatewayProtocolException::class.java)
    @Test fun binaryFramesCannotReplaceCanonicalJson() = refused("binary", GatewayProtocolException::class.java)
    @Test fun wrongSelectedSubprotocolRefusesNegotiation() = refused("wrong-protocol", GatewayProtocolException::class.java)
    @Test fun serverRequestCannotAcknowledgeOurDescribeId() = refused("server-request", GatewayProtocolException::class.java)
    @Test fun foreignResponseCannotAcknowledgeOurDescribeId() = refused("foreign-response", GatewayProtocolException::class.java)
    @Test fun unattachedTranscriptEventCannotEnterTheChannel() = refused("session-event", GatewayProtocolException::class.java)
    @Test fun hostNotificationFloodIsBounded() = refused("flood", GatewayProtocolException::class.java)
    @Test fun disconnectNeverEstablishesAnExecutionOutcome() = refused("disconnect", GatewayUnavailable::class.java)
    @Test fun invalidTicketAndUrlAreRefusedBeforeAnyUpgrade() = runBlocking {
        Fixture().use { f ->
            assertTrue(runCatching { CanonicalGatewayChannel.connect(f.http, f.base, "bad", f.target, home()) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { CanonicalGatewayChannel.connect(f.http, f.base, f.ticket, f.target.copy(url = f.server.url("/other/").toString()), home()) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, f.server.requestCount)
        }
    }
}
