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
import org.hermesmobile.app.data.CanonicalFeedOwner
import org.hermesmobile.app.data.CanonicalReviewChanged
import org.hermesmobile.app.data.CanonicalReplayCursor
import org.hermesmobile.app.data.CanonicalSessionSnapshot
import org.hermesmobile.app.data.HermesContextStats
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
                    if (mode.startsWith("approval")) {
                        val id = request.getValue("id")
                        when (request["method"]) {
                            JsonPrimitive("session.list") -> {
                                ws.send("""{"jsonrpc":"2.0","id":$id,"result":{"scope":"live","sessions":[
                                    {"id":"desktop","session_id":"desktop","title":"Desktop task","source":"cli","message_count":7,"running":false},
                                    {"id":"other","session_id":"other","title":"Second task","source":"cli","message_count":4,"running":true}
                                ]}}""".trimIndent())
                                return
                            }
                            JsonPrimitive(SharedChatProtocol.PendingApproval) -> {
                                val session = request["params"]?.jsonObject?.get("session_id")
                                val fields = when {
                                    mode == "approval-unsupported" ->
                                        """"error":{"code":-32601,"message":"Method not found"}"""
                                    mode == "approval-partial" && session == JsonPrimitive("other") ->
                                        """"error":{"code":4001,"message":"not_found","data":{"reason":"not_found"}}"""
                                    session == JsonPrimitive("desktop") ->
                                        """"result":{"approvals":[{"request_id":"desktop-request","command":"fixture read","description":"Read fixture","choices":["once","always","deny"],"allow_permanent":true}]}"""
                                    else -> """"result":{"approvals":[]}"""
                                }
                                ws.send("""{"jsonrpc":"2.0","id":$id,$fields}""")
                                return
                            }
                            else -> Unit
                        }
                    }
                    if (request["method"] == JsonPrimitive("session.stats")) {
                        val id = request.getValue("id")
                        if (mode == "stats-missing") {
                            ws.send("""{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"Private fixture refusal"}}""")
                        } else {
                            ws.send("""{"jsonrpc":"2.0","id":$id,"result":{"context_length":16384,"config_context_length":32768,"context_tokens":794,"last_prompt_tokens":820,"compression_count":2,"model":"private fixture text"}}""")
                        }
                        return
                    }
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
    @Test fun approvalLookupFindsAnotherClientWithoutOpeningOrAnsweringItsSession() = runBlocking {
        Fixture("approval-found").use { fixture ->
            withTimeout(4000) {
                CanonicalGatewayChannel.connect(fixture.http, fixture.base, fixture.ticket, fixture.target, home()).use { channel ->
                    val inbox = channel.pendingApprovalInbox()
                    assertSame(channel.binding, inbox.binding)
                    assertEquals(2, inbox.listedSessions)
                    assertEquals(2, inbox.checkedSessions)
                    assertFalse(inbox.incomplete)
                    val notice = inbox.notices.single()
                    assertEquals("desktop", notice.session.sessionId)
                    assertEquals("desktop-request", notice.request.id)
                    assertTrue(notice.request.reviewable)
                    assertFalse(notice.session.reportedRunning)
                    assertEquals("CanonicalApprovalNotice(redacted)", notice.toString())
                    assertEquals("SharedApprovalNotice(redacted)", notice.request.toString())

                    val owner = CanonicalFeedOwner(fixture.target, home(), "/fixture/default", "fixture-instance", "desktop", 7)
                    fun snapshot(scope: CanonicalFeedOwner = owner, requested: String = "desktop", generation: Long = 9) =
                        CanonicalSessionSnapshot(scope, requested, "stored-desktop", "sub-1", CanonicalReplayCursor("epoch", 1),
                            revision = 3, executionGeneration = generation, reportedRunning = true, messageCount = 0, messagesOmitted = false)
                    val approval = SharedApproval("desktop-request", 9, "fixture read", "Read fixture", setOf("deny", "once"))
                    assertTrue(notice.matches(snapshot(), approval))
                    for (wrongScope in listOf(
                        owner.copy(sessionId = "other"), owner.copy(homeId = "d".repeat(64)),
                        owner.copy(profileId = "/fixture/other"), owner.copy(instanceId = "different"),
                        owner.copy(authorityEpoch = 8), owner.copy(target = fixture.target.copy(actor = "fixture · other")),
                        owner.copy(target = fixture.target.copy(apiScope = "a".repeat(64)))
                    )) assertFalse(notice.matches(snapshot(scope = wrongScope), approval))
                    assertFalse(notice.matches(snapshot(requested = "other"), approval))
                    assertFalse(notice.matches(snapshot(generation = 10), approval))
                    assertFalse(notice.matches(snapshot(), approval.copy(id = "other-request")))
                    assertFalse(notice.matches(snapshot(), approval.copy(command = "changed")))
                    assertFalse(notice.matches(snapshot(), approval.copy(choices = setOf("once"))))
                }
            }
            assertEquals(listOf("runtime.describe", "session.list", "approval.pending", "approval.pending"),
                fixture.calls.map { it.getValue("method").jsonPrimitive.content })
            assertEquals(buildJsonObject { put("scope", "live"); put("limit", 200) }, fixture.calls[1]["params"])
            assertEquals(buildJsonObject { put("session_id", "desktop") }, fixture.calls[2]["params"])
            assertEquals(buildJsonObject { put("session_id", "other") }, fixture.calls[3]["params"])
            val upgrade = fixture.server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/prefix/api/ws", upgrade.path)
            for (header in listOf("Authorization", "Cookie", "Origin")) assertNull(upgrade.getHeader(header))
        }
    }

    @Test fun approvalLookupDistinguishesAnIncompleteScanAndAnUnsupportedMethod() = runBlocking {
        Fixture("approval-partial").use { fixture ->
            withTimeout(4000) {
                CanonicalGatewayChannel.connect(fixture.http, fixture.base, fixture.ticket, fixture.target, home()).use { channel ->
                    val inbox = channel.pendingApprovalInbox()
                    assertTrue(inbox.incomplete)
                    assertEquals(1, inbox.checkedSessions)
                    assertEquals(2, inbox.listedSessions)
                    assertEquals("desktop-request", inbox.notices.single().request.id)
                }
            }
            assertEquals(4, fixture.calls.size)
        }
        Fixture("approval-unsupported").use { fixture ->
            withTimeout(4000) {
                CanonicalGatewayChannel.connect(fixture.http, fixture.base, fixture.ticket, fixture.target, home()).use { channel ->
                    assertTrue(runCatching { channel.pendingApprovalInbox() }.exceptionOrNull() is CanonicalNotSupported)
                }
            }
            assertEquals(listOf("runtime.describe", "session.list", "approval.pending"),
                fixture.calls.map { it.getValue("method").jsonPrimitive.content })
        }
    }

    @Test fun pendingApprovalRejectsAmbiguousRequestsAndKeepsUnsupportedChoicesReadOnly() {
        val row = Json.parseToJsonElement("""{"request_id":"id","command":"read fixture","description":"","choices":["always"],"allow_permanent":true}""").jsonObject
        fun decode(vararg rows: JsonElement) = SharedChatProtocol.pendingApprovals(buildJsonObject {
            put("approvals", JsonArray(rows.toList()))
        })
        assertFalse(decode(row).single().reviewable)
        for (ambiguous in listOf(
            JsonObject(row + ("request_id" to JsonPrimitive(5))),
            JsonObject(row + ("command" to JsonPrimitive("a".repeat(16385)))),
            JsonObject(row + ("allow_permanent" to JsonPrimitive(false))),
            JsonObject(row + ("choices" to JsonArray(listOf(JsonPrimitive("always"), JsonPrimitive("always")))))
        )) assertNotNull(runCatching { decode(ambiguous) }.exceptionOrNull())
        assertNotNull(runCatching { decode(row, row) }.exceptionOrNull())
    }

    @Test fun contextReadUsesOnlyTheAlreadyReviewedSessionAndRejectsAnotherRuntime() = runBlocking {
        Fixture().use { fixture ->
            val channel = withTimeout(4000) {
                CanonicalGatewayChannel.connect(fixture.http, fixture.base, fixture.ticket, fixture.target, home())
            }
            channel.use {
                val owner = CanonicalFeedOwner(fixture.target, home(), "/fixture/default", "fixture-instance", "history", 7)
                assertTrue(runCatching { channel.readContext(owner.copy(authorityEpoch = 8)) }.exceptionOrNull() is CanonicalReviewChanged)
                assertEquals(1, fixture.calls.size)
                assertEquals(HermesContextStats(16384, 32768, 794, 820, 2), withTimeout(4000) { channel.readContext(owner) })
                assertEquals(listOf(JsonPrimitive("runtime.describe"), JsonPrimitive("session.stats")), fixture.calls.map { it["method"] })
                assertEquals(buildJsonObject { put("session_id", "history") }, fixture.calls.last()["params"])
            }
        }
    }

    @Test fun unsupportedContextMethodKeepsItsTypedRefusalOnTheReadSocket() = runBlocking {
        Fixture("stats-missing").use { fixture ->
            val channel = withTimeout(4000) {
                CanonicalGatewayChannel.connect(fixture.http, fixture.base, fixture.ticket, fixture.target, home())
            }
            channel.use {
                val owner = CanonicalFeedOwner(fixture.target, home(), "/fixture/default", "fixture-instance", "history", 7)
                val failure = runCatching { withTimeout(4000) { channel.readContext(owner) } }.exceptionOrNull()
                assertEquals(-32601, (failure as GatewayRpcException).code)
                assertEquals(listOf("runtime.describe", "session.stats"), fixture.calls.map { it.getValue("method").jsonPrimitive.content })
            }
        }
    }

    @Test fun contextNumericProjectionDistinguishesUnreportedWindowAndRejectsMalformedCounts() {
        val agentless = buildJsonObject { put("context_length", JsonNull); put("context_tokens", 0); put("last_prompt_tokens", 0); put("compression_count", 2) }
        assertEquals(HermesContextStats(null, null, null, null, 2), HermesContextStats.parse(agentless))
        val bounded = buildJsonObject { agentless.forEach { (name, value) -> put(name, value) }; put("context_length", 4096) }
        assertEquals(0L, HermesContextStats.parse(bounded).contextTokens)
        for ((field, value) in listOf(
            "context_tokens" to JsonPrimitive(4097),
            "compression_count" to JsonPrimitive(-1),
            "last_prompt_tokens" to JsonPrimitive("100"),
            "context_length" to JsonPrimitive(1.5),
            "config_context_length" to JsonPrimitive(true)
        )) {
            val malformed = JsonObject(bounded + (field to value))
            assertTrue(runCatching { HermesContextStats.parse(malformed) }.exceptionOrNull() is GatewayProtocolException)
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
