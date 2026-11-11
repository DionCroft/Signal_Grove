package org.hermesmobile.app.network

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hermesmobile.app.data.*
import kotlinx.serialization.json.*
import okhttp3.*
import okio.ByteString
import org.hermesmobile.app.data.ManagementTarget
import org.hermesmobile.app.data.PluginHome

internal class CanonicalScopeChanged : Exception("Canonical profile identity changed.")
internal class CanonicalNotSupported : Exception("Canonical session transport is unavailable.")

/** Runtime identity only. Features are protocol support, never actor grants or execution state. */
internal class CanonicalRuntimeBinding(
    val target: ManagementTarget,
    val homeId: String,
    val profileId: String,
    val instanceId: String,
    val authorityEpoch: Long
) {
    override fun toString() = "CanonicalRuntimeBinding(redacted)"

    companion object {
        internal fun parse(result: JsonObject, target: ManagementTarget, homeId: String): CanonicalRuntimeBinding {
            fun text(key: String, limit: Int) = (result[key] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content
                ?.also { require(it.isNotBlank() && it.length <= limit && it.none(Char::isISOControl) && it.codePoints().noneMatch { c -> c in 0xD800..0xDFFF }) }
                ?: throw GatewayProtocolException()
            val profile = text("profile_id", 8192)
            if (PluginHome.project(JsonPrimitive(profile))?.identity() != homeId) throw CanonicalScopeChanged()
            val instance = text("instance_id", 512)
            val epoch = (result["authority_epoch"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
                ?.takeIf { it >= 0 } ?: throw GatewayProtocolException()
            fun strings(value: JsonElement?, limit: Int): Set<String> {
                val rows = value as? JsonArray ?: throw GatewayProtocolException()
                require(rows.size <= limit)
                val items = rows.map {
                    (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                        ?.also { s -> require(s.isNotBlank() && s.length <= 256 && s.none(Char::isISOControl)) }
                        ?: throw GatewayProtocolException()
                }
                require(items.size == items.toSet().size)
                return items.toSet()
            }
            val features = strings(result["capabilities"], 128)
            if (!features.containsAll(setOf("durable-admission-v1", "event-replay-v1"))) throw CanonicalNotSupported()
            val create = result["session_create"] as? JsonObject ?: throw GatewayProtocolException()
            strings(create["sources"], 128); strings(create["parameters"], 256)
            if (result["skin"] != null && result["skin"] != JsonNull && result["skin"] !is JsonObject) throw GatewayProtocolException()
            return CanonicalRuntimeBinding(target, homeId, profile, instance, epoch)
        }
    }
}

/**
 * Separate canonical socket. Explicit reviewed resume can restore/adopt history and attach
 * a viewer. No server-request capability, work admission, mutation replay or reconnect.
 */
internal class CanonicalGatewayChannel private constructor() : Closeable {
    private val opened = CompletableDeferred<Unit>()
    private val ready = CompletableDeferred<Unit>()
    private val runtime = CompletableDeferred<JsonObject>()
    private val stopped = AtomicBoolean(false)
    private val firstFailure = AtomicReference<Exception?>()
    private var heartbeatAdvertised = false
    private var readySeen = false
    private var describeSent = false
    private var describeAnswered = false
    private var notificationCount = 0
    private var receivedBytes = 0L
    private data class Pending(val id: String, val waiter: CompletableDeferred<JsonObject>)
    private val requests = Mutex()
    private var nextId = 1L
    private var pending: Pending? = null
    private var resuming = false
    private var resumed = false
    private val earlyFrames = ArrayList<JsonObject>()
    private var earlyBytes = 0
    private var feed: CanonicalReplayWindow? = null
    private var snapshot: CanonicalSessionSnapshot? = null
    private var baseline: CanonicalReplayCursor? = null
    private val baselineFingerprints = linkedMapOf<Long,String>()
    private val deliveries = Channel<CanonicalFeedDelivery>(64)
    private var queuedBytes = 0
    private val consumer = AtomicBoolean(false)
    private var hostWindowStarted = System.nanoTime()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var verified: CanonicalRuntimeBinding? = null

    val binding: CanonicalRuntimeBinding
        get() = verified?.takeUnless { stopped.get() } ?: throw GatewayUnavailable()

    @Synchronized private fun fail(error: Exception) {
        firstFailure.compareAndSet(null, error)
        stopped.set(true); verified = null
        val cause = firstFailure.get() ?: error
        opened.completeExceptionally(cause); ready.completeExceptionally(cause); runtime.completeExceptionally(cause)
        pending?.waiter?.completeExceptionally(cause); pending = null
        earlyFrames.clear(); earlyBytes = 0; baselineFingerprints.clear()
        feed?.close()
        while (deliveries.tryReceive().isSuccess) { /* Clear queued private observations. */ }
        queuedBytes = 0; deliveries.close(cause)
        socket?.cancel()
    }

    /** Bound depth before invoking the JSON parser, including escaped object keys. */
    private fun frame(text: String): JsonObject {
        val maximum = if (verified == null) 262144 else 2097152
        require(text.length <= maximum)
        val bytes = text.toByteArray(Charsets.UTF_8).size
        if (verified == null) receivedBytes += bytes
        require(bytes <= maximum && (verified != null || receivedBytes <= 2097152))
        val containers = ArrayList<MutableSet<String>?>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{' -> { require(containers.size < 32); containers.add(mutableSetOf()) }
                '[' -> { require(containers.size < 32); containers.add(null) }
                '}', ']' -> { require(containers.isNotEmpty()); containers.removeAt(containers.lastIndex) }
                '"' -> {
                    val start = index++
                    while (index < text.length && text[index] != '"') {
                        if (text[index] == '\\') index++
                        index++
                    }
                    require(index < text.length)
                    var next = index + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') {
                        val keys = containers.lastOrNull() ?: throw GatewayProtocolException()
                        val raw = text.substring(start, index + 1)
                        require(raw.length <= 4096)
                        require(keys.add(Json.decodeFromString<String>(raw)))
                    }
                }
            }
            index++
        }
        require(containers.isEmpty())
        return Json.parseToJsonElement(text) as? JsonObject ?: throw GatewayProtocolException()
    }

    @Synchronized private fun accept(text: String) {
        if (stopped.get()) return
        try {
            val j = frame(text)
            if (j["jsonrpc"] != JsonPrimitive("2.0")) throw GatewayProtocolException()
            if (!j.containsKey("id")) {
                if (j.keys != setOf("jsonrpc", "method", "params") || j["method"] != JsonPrimitive("event")) throw GatewayProtocolException()
                val params = j["params"] as? JsonObject ?: throw GatewayProtocolException()
                val type = (params["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw GatewayProtocolException()
                if (type == "gateway.ready") {
                    if (readySeen) throw GatewayProtocolException()
                    readySeen = true
                    val payload = params["payload"] as? JsonObject ?: throw GatewayProtocolException()
                    if (payload["session_authority"] != JsonPrimitive(true)) throw CanonicalNotSupported()
                    if (payload["skin"] !is JsonObject || payload["change_events"] !is JsonPrimitive ||
                        payload["change_events"]!!.jsonPrimitive.takeIf { !it.isString }?.booleanOrNull == null) throw GatewayProtocolException()
                    val epoch = (payload["replay_epoch"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw GatewayProtocolException()
                    require(epoch.isNotBlank() && epoch.length <= 256 && epoch.none(Char::isISOControl))
                    payload["heartbeat"]?.let { require(it is JsonPrimitive && !it.isString && it.booleanOrNull != null) }
                    heartbeatAdvertised = payload["heartbeat"] == JsonPrimitive(true)
                    ready.complete(Unit)
                } else {
                    // Host notifications are observations. A transcript event before attachment is unsolicited.
                    if (!readySeen || type.isBlank() || type.length > 256 || type.any(Char::isISOControl)) throw GatewayProtocolException()
                    if (text.toByteArray(Charsets.UTF_8).size > 262144) throw GatewayProtocolException()
                    // Registered surface-global notifications use an empty session ID and no
                    // session sequence. They never enter the reviewed transcript/feed or grant work.
                    val hostChange = type in setOf("sessions.changed","skin.changed","pet.changed","cron.changed",
                        "platforms.changed","projects.changed","pairing.changed","skills.changed","bot_relay.outbox.pending") &&
                        params["session_id"] == JsonPrimitive("") &&
                        params.keys.all {it in setOf("type","session_id","payload")} &&
                        (!params.containsKey("payload") || params["payload"] is JsonObject)
                    if ((params.containsKey("session_id") && !hostChange) || type == "session.replay_gap") {
                        acceptSessionFrame(j)
                    } else {
                        val now = System.nanoTime()
                        if (verified != null && now - hostWindowStarted >= 1_000_000_000L) {
                            hostWindowStarted = now; notificationCount = 0
                        }
                        if (++notificationCount > 64) throw GatewayProtocolException()
                    }
                }
                return
            }
            if (!readySeen || j.containsKey("method") || j.containsKey("result") == j.containsKey("error") ||
                j.keys.any { it !in setOf("jsonrpc", "id", "result", "error") }) throw GatewayProtocolException()
            val handshake = j["id"] == JsonPrimitive("1")
            val waiter = if (handshake) {
                if (!describeSent || describeAnswered) throw GatewayProtocolException()
                describeAnswered = true; runtime
            } else {
                val current = pending ?: throw GatewayProtocolException()
                if (j["id"] != JsonPrimitive(current.id)) throw GatewayProtocolException()
                current.waiter
            }
            if (j.containsKey("error")) {
                val error = j["error"] as? JsonObject ?: throw GatewayProtocolException()
                val code = (error["code"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: throw GatewayProtocolException()
                val refusal = GatewayRpcException(code)
                waiter.completeExceptionally(refusal)
                throw refusal
            }
            val result = j["result"] as? JsonObject ?: throw GatewayProtocolException()
            waiter.complete(result)
            if (!handshake) pending = null
        } catch (error: Exception) {
            fail(if (error is GatewayRpcException || error is CanonicalNotSupported) error else GatewayProtocolException())
        }
    }

    @Synchronized private fun describe() {
        if (stopped.get()) throw firstFailure.get() ?: GatewayUnavailable()
        check(readySeen && !describeSent)
        describeSent = true
        if (socket?.send("""{"jsonrpc":"2.0","id":"1","method":"runtime.describe","params":{}}""") != true) throw GatewayUnavailable()
    }

    private fun coveredByBaseline(frame: JsonObject, owner: CanonicalFeedOwner, cursor: CanonicalReplayCursor): Boolean {
        val params = frame["params"] as? JsonObject ?: throw GatewayProtocolException()
        val event = CanonicalFeedEvent.parse(params, owner, cursor, 262144)
        if (event.sequence > cursor.sequence) return false
        val known = baselineFingerprints[event.sequence]
        if (known != null && known != event.fingerprint || known == null && baselineFingerprints.size >= 256) throw GatewayProtocolException()
        baselineFingerprints[event.sequence] = event.fingerprint
        return true
    }

    @Synchronized private fun acceptSessionFrame(frame: JsonObject) {
        if (resuming) {
            val size = frame.toString().toByteArray(Charsets.UTF_8).size
            if (earlyFrames.size >= 256 || earlyBytes.toLong() + size > 2097152) throw GatewayProtocolException()
            earlyFrames.add(frame); earlyBytes += size
            return
        }
        val attached = snapshot ?: throw GatewayProtocolException()
        val window = feed ?: throw GatewayProtocolException()
        if (coveredByBaseline(frame,attached.owner,checkNotNull(baseline))) return
        val delivery = window.offerLive(attached.owner, frame)
        if (delivery.status.phase == CanonicalFeedPhase.SnapshotRequired) throw GatewayProtocolException()
        if (delivery.events.isNotEmpty()) {
            val bytes = delivery.events.sumOf { it.wireBytes }
            if (queuedBytes.toLong() + bytes > 2097152 || !deliveries.trySend(delivery).isSuccess) throw GatewayProtocolException()
            queuedBytes += bytes
        }
    }

    private suspend fun call(method: String, params: JsonObject): JsonObject = requests.withLock {
        require(method in setOf("session.list", "session.resume", "session.events.since", "gateway.ping", "session.mutate"))
        val request: Pending
        synchronized(this) {
            if (stopped.get()) throw firstFailure.get() ?: GatewayUnavailable()
            check(verified != null && pending == null && nextId < Long.MAX_VALUE)
            request = Pending((++nextId).toString(), CompletableDeferred())
            pending = request
            val frame = buildJsonObject { put("jsonrpc","2.0"); put("id",request.id); put("method",method); put("params",params) }
            if (socket?.send(frame.toString()) != true) {
                fail(GatewayUnavailable()); throw GatewayUnavailable()
            }
        }
        try {
            withTimeoutOrNull(45000) { request.waiter.await() } ?: throw GatewayUnavailable()
        } catch (error: Exception) {
            fail(if (error is GatewayRpcException || error is GatewayProtocolException) error else GatewayUnavailable())
            throw error
        } finally {
            synchronized(this) { if (pending === request) pending = null }
            request.waiter.cancel()
        }
    }

    internal suspend fun sessionInventory(scope: CanonicalSessionScope = CanonicalSessionScope.Stored): CanonicalSessionInventory {
        synchronized(this) { check(!resuming && !resumed) }
        val identity = binding
        val result = call("session.list", buildJsonObject { put("scope",scope.wire); put("limit",200) })
        if (binding !== identity) throw CanonicalScopeChanged()
        return CanonicalSessionProtocol.inventory(result, identity, scope)
    }

    /** WebSocket liveness only; the real WS layer returns ok, not the authority's pong. */
    internal suspend fun heartbeat() {
        val expected = binding
        if (!heartbeatAdvertised) throw CanonicalNotSupported()
        val result = call("gateway.ping", buildJsonObject {})
        if (binding !== expected || result.keys != setOf("ok") || result["ok"] != JsonPrimitive(true)) {
            fail(GatewayProtocolException())
            throw GatewayProtocolException()
        }
    }

    internal class Attachment(val snapshot: CanonicalSessionSnapshot, val initial: CanonicalFeedDelivery) {
        override fun toString() = "CanonicalAttachment(redacted)"
    }

    /** Caller must persist a durable sending receipt in beforeSend, after fresh review validation. */
    internal suspend fun resumeReviewed(
        review: CanonicalResumeReview,
        previous: CanonicalReplayCheckpoint? = null,
        beforeSend: suspend () -> Unit,
        acknowledged: suspend (CanonicalSessionSnapshot) -> Unit
    ): Attachment {
        val current = sessionInventory(review.expected.scope)
        if (previous != null) {
            val known = previous.owner; val owner = current.binding
            if (known.target != owner.target || known.homeId != owner.homeId || known.profileId != owner.profileId ||
                known.instanceId != owner.instanceId || known.authorityEpoch != owner.authorityEpoch) throw CanonicalReviewChanged()
        }
        review.consume(current)
        beforeSend()
        synchronized(this) {
            check(!resuming && !resumed)
            resuming = true; resumed = true
        }
        try {
            val result = call("session.resume", buildJsonObject {
                put("session_id",review.row.sessionId); put("omit_messages",true)
            })
            val accepted = CanonicalSessionProtocol.snapshot(result, current.binding, review.row.sessionId)
            acknowledged(accepted)
            val start = previous?.let {
                if (it.owner != accepted.owner || it.cursor.epoch != accepted.cursor.epoch || it.cursor.sequence > accepted.cursor.sequence) throw CanonicalReviewChanged()
                it.cursor
            } ?: accepted.cursor
            synchronized(this) {
                if (stopped.get()) throw firstFailure.get() ?: GatewayUnavailable()
                val window = CanonicalReplayWindow(accepted.owner, start)
                baseline = start
                // Snapshot watermark already covers earlier frames; validate every covered frame
                // before discarding it, and buffer only events strictly after that watermark.
                for (frame in earlyFrames) {
                    if (!coveredByBaseline(frame,accepted.owner,start) &&
                        window.offerLive(accepted.owner, frame).status.phase == CanonicalFeedPhase.SnapshotRequired) throw GatewayProtocolException()
                }
                earlyFrames.clear(); earlyBytes = 0; snapshot = accepted; feed = window; resuming = false
            }
            val replay = call("session.events.since", buildJsonObject {
                put("session_id",accepted.owner.sessionId); put("last_sequence",start.sequence); put("replay_epoch",start.epoch)
            })
            val delivery = synchronized(this) {
                val result = feed!!.reconcile(accepted.owner, replay)
                if (result.status.phase != CanonicalFeedPhase.Following || result.status.cursor.sequence < accepted.cursor.sequence) throw GatewayProtocolException()
                result
            }
            return Attachment(accepted, delivery)
        } catch (error: Exception) {
            fail(if (error is GatewayProtocolException || error is GatewayRpcException) error else GatewayUnavailable())
            throw error
        }
    }

    /** A separately authenticated socket; no attachment, prompt or work is replayed. */
    internal suspend fun changeWorkspaceReviewed(
        review:CanonicalWorkspaceReview,beforeSend:suspend ()->Unit,
        acknowledged:suspend (CanonicalWorkspaceAcknowledgement)->Unit
    ):CanonicalWorkspaceAcknowledgement {
        synchronized(this) {check(!resuming && !resumed)}
        val current=binding;val expected=review.expected.owner
        if(current.target!=expected.target || current.homeId!=expected.homeId || current.profileId!=expected.profileId ||
            current.instanceId!=expected.instanceId || current.authorityEpoch!=expected.authorityEpoch) throw CanonicalReviewChanged()
        val payload=review.consume(review.expected)
        beforeSend()
        currentCoroutineContext().ensureActive()
        if(binding!==current) throw CanonicalScopeChanged()
        val result=CanonicalWorkspaceAcknowledgement.parse(call("session.mutate",payload),review)
        acknowledged(result)
        return result
    }

    /** Exactly one consumer applies the initial batch before reading these queued live batches. */
    internal fun observations(): Flow<CanonicalFeedDelivery> {
        check(consumer.compareAndSet(false, true))
        return kotlinx.coroutines.flow.flow {
            for (delivery in deliveries) {
                synchronized(this@CanonicalGatewayChannel) {
                    queuedBytes -= delivery.events.sumOf { it.wireBytes }
                    if (stopped.get()) throw firstFailure.get() ?: GatewayUnavailable()
                }
                emit(delivery)
            }
        }
    }

    override fun close() = fail(GatewayUnavailable())
    override fun toString() = "CanonicalGatewayChannel(redacted)"

    companion object {
        internal suspend fun connect(
            http: OkHttpClient, base: HttpUrl, ticket: String, target: ManagementTarget, homeId: String
        ): CanonicalGatewayChannel {
            require(ticket.matches(Regex("[A-Za-z0-9_-]{16,8192}")))
            require(base.toString() == target.url && homeId.matches(Regex("[a-f0-9]{64}")))
            val connection = CanonicalGatewayChannel()
            try {
                val request = Request.Builder().url(base.newBuilder().addPathSegments("api/ws").build())
                    .header("Sec-WebSocket-Protocol", "hermes-gateway-v1, hermes-gateway-ticket.$ticket").build()
                connection.socket = http.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (response.header("Sec-WebSocket-Protocol") != "hermes-gateway-v1") connection.fail(GatewayProtocolException())
                        else connection.opened.complete(Unit)
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) = connection.accept(text)
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = connection.fail(GatewayProtocolException())
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = connection.fail(GatewayUnavailable())
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = connection.fail(GatewayUnavailable())
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = connection.fail(GatewayUnavailable())
                })
                val result = withTimeoutOrNull(30000) {
                    connection.opened.await(); connection.ready.await(); connection.describe(); connection.runtime.await()
                } ?: throw GatewayUnavailable()
                val binding = try { CanonicalRuntimeBinding.parse(result, target, homeId) }
                catch (error: IllegalArgumentException) { throw GatewayProtocolException() }
                synchronized(connection) {
                    if (connection.stopped.get()) throw connection.firstFailure.get() ?: GatewayUnavailable()
                    connection.verified = binding
                }
                return connection
            } catch (error: Exception) { connection.close(); throw error }
        }
    }
}
