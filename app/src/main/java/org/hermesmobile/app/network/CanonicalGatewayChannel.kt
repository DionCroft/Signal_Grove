package org.hermesmobile.app.network

import android.os.SystemClock
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

internal open class CanonicalScopeChanged : Exception("Canonical profile identity changed.")
/** Only a complete, valid pre-session describe can request profile ticket negotiation. */
internal class CanonicalTicketProfileMismatch : CanonicalScopeChanged()
internal class CanonicalProfileTransportUnavailable : Exception("Profile-bound live transport is unavailable.")
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
            val actualHome = PluginHome.project(JsonPrimitive(profile))?.identity() ?: throw CanonicalScopeChanged()
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
            // Validate the complete descriptor before permitting a different ticket handshake.
            // No session/adoption/mutation has been sent on this unverified channel.
            if (actualHome != homeId) throw CanonicalTicketProfileMismatch()
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
    private data class Pending(val id: String, val waiter: CompletableDeferred<JsonObject>,val workspace:Boolean, val attachedRead: Boolean)
    private val requests = Mutex()
    private var nextId = 1L
    private var pending: Pending? = null
    private var runOnceReview: CronRunOnceReview? = null
    private var projectReview: CronProjectReview? = null
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
                if (!handshake && pending?.attachedRead == true && code == -32601) {
                    pending = null
                    waiter.completeExceptionally(CanonicalNotSupported())
                    return
                }
                val refusal = if(!handshake && pending?.workspace==true) CanonicalWorkspaceRefused.parse(code,error) ?: GatewayRpcException(code) else GatewayRpcException(code)
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
        require(method in setOf("session.list", "session.stats", "session.resume", "session.events.since", "gateway.ping", "session.mutate", "projects.list", "projects.get", "projects.project_sessions", CronExecutionHistory.Method, CronExecutionOutput.Method,
            CronTimingProtocol.Prepare, CronTimingProtocol.Review, CronTimingProtocol.Apply,
            CronTimingProtocol.Observe, AttachedWorkerProtocol.Describe, AttachedWorkerProtocol.Read) ||
            method in CronRunOnceProtocol.methods || method in CronProjectProtocol.methods)
        val request: Pending
        synchronized(this) {
            if (stopped.get()) throw firstFailure.get() ?: GatewayUnavailable()
            check(verified != null && pending == null && nextId < Long.MAX_VALUE)
            request = Pending((++nextId).toString(), CompletableDeferred(),
                method == "session.mutate" && params["operation"] == JsonPrimitive("workspace"),
                method == AttachedWorkerProtocol.Describe || method == AttachedWorkerProtocol.Read)
            pending = request
            val frame = buildJsonObject { put("jsonrpc","2.0"); put("id",request.id); put("method",method); put("params",params) }
            if (socket?.send(frame.toString()) != true) {
                fail(GatewayUnavailable()); throw GatewayUnavailable()
            }
        }
        try {
            withTimeoutOrNull(45000) { request.waiter.await() } ?: throw GatewayUnavailable()
        } catch (error: Exception) {
            if (!request.attachedRead || error !is CanonicalNotSupported) {
                fail(if (error is GatewayRpcException || error is GatewayProtocolException) error else GatewayUnavailable())
            }
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

    internal suspend fun cronExecutionHistory(jobId: String): CronExecutionHistory {
        val expected = binding
        val reply = cronCall(CronExecutionHistory.Method, CronExecutionHistory.request(expected.target, jobId))
        return CronExecutionHistory.parse(reply, expected.target, expected.homeId, jobId)
    }

    internal suspend fun cronExecutionOutput(jobId: String, executionId: String): CronExecutionOutput {
        val expected = binding
        val reply = cronCall(CronExecutionOutput.Method, CronExecutionHistory.receiptRequest(expected.target, jobId, executionId))
        return CronExecutionOutput.parse(reply, expected.target, jobId, executionId)
    }

    internal suspend fun prepareCronTiming(jobId: String): CronTimingBaseline {
        val expected = binding
        val reply = cronCall(CronTimingProtocol.Prepare, CronTimingProtocol.prepare(expected.target, jobId))
        return CronTimingProtocol.parseBaseline(reply, expected.target, expected.homeId, jobId)
    }

    internal suspend fun cronTimingReview(base: CronTimingBaseline, draft: CronTimingDraft): CronTimingReview {
        if (binding.target != base.owner || binding.homeId != base.homeId) throw CanonicalScopeChanged()
        return CronTimingProtocol.parseReview(
            cronCall(CronTimingProtocol.Review, CronTimingProtocol.review(base, draft)), base, draft)
    }

    internal suspend fun applyCronTiming(review: CronTimingReview, id: String, proof: CronTimingReceiptProof) {
        val base = review.baseline
        if (binding.target != base.owner || binding.homeId != base.homeId) throw CanonicalScopeChanged()
        val reply = cronCall(CronTimingProtocol.Apply, CronTimingProtocol.apply(review, id))
        CronTimingProtocol.parseReceipt(reply, base.owner, base.jobId, id, proof)
    }

    internal suspend fun observeCronTiming(jobId: String, id: String, proof: CronTimingReceiptProof) {
        val owner = binding.target
        val reply = cronCall(CronTimingProtocol.Observe, CronTimingProtocol.observe(owner, jobId, id))
        CronTimingProtocol.parseReceipt(reply, owner, jobId, id, proof)
    }

    internal suspend fun prepareCronRunOnce(jobId: String): CronRunOnceReview {
        val expected = binding
        val started = SystemClock.elapsedRealtime()
        val reply = cronCall(CronRunOnceProtocol.Prepare, CronRunOnceProtocol.prepare(expected.target, jobId))
        return CronRunOnceProtocol.review(reply, expected.target, expected.homeId, jobId, started)
            .also { runOnceReview = it }
    }

    internal fun requireRunOnceReview(review: CronRunOnceReview) {
        if (stopped.get() || runOnceReview !== review) throw CronRunOnceRefused("CHANGED")
        if (SystemClock.elapsedRealtime() >= review.expiresAt) throw CronRunOnceRefused("EXPIRED")
    }

    internal suspend fun confirmCronRunOnce(review: CronRunOnceReview): CronRunOnceObservation {
        requireRunOnceReview(review)
        runOnceReview = null
        val reply = cronCall(CronRunOnceProtocol.Confirm, CronRunOnceProtocol.decide(review, CronRunOnceDecision.Approve))
        return CronRunOnceProtocol.confirmed(reply, review)
    }

    internal suspend fun dismissCronRunOnce(review: CronRunOnceReview, decision: CronRunOnceDecision) {
        require(decision != CronRunOnceDecision.Approve)
        requireRunOnceReview(review)
        runOnceReview = null
        val reply = cronCall(CronRunOnceProtocol.Dismiss, CronRunOnceProtocol.decide(review, decision))
        CronRunOnceProtocol.dismissed(reply, review, decision)
    }

    internal suspend fun observeCronRunOnce(jobId: String, request: String): CronRunOnceObservation {
        val owner = binding.target
        val reply = cronCall(CronRunOnceProtocol.Observe, CronRunOnceProtocol.observe(owner, jobId, request))
        return CronRunOnceProtocol.observed(reply, owner, jobId, request)
    }

    internal suspend fun cronProjectInbox(): CronProjectInbox {
        val owner = binding
        return CronProjectProtocol.listed(
            cronCall(CronProjectProtocol.Inbox, CronProjectProtocol.inbox(owner.target)), owner.target, owner.homeId)
    }

    internal suspend fun prepareCronProject(inbox: CronProjectInbox, entry: CronProjectEntry): CronProjectReview {
        if (binding.target != inbox.owner || binding.homeId != inbox.home || inbox.entries.none { it === entry }) {
            throw CanonicalScopeChanged()
        }
        val started = SystemClock.elapsedRealtime()
        val reply = cronCall(CronProjectProtocol.Prepare, CronProjectProtocol.select(inbox.owner, entry.occurrence))
        return CronProjectProtocol.prepared(reply, inbox, entry, started).also { projectReview = it }
    }

    internal fun requireProjectReview(review: CronProjectReview) {
        if (stopped.get() || projectReview !== review) throw CronProjectRefused("CHANGED")
        if (SystemClock.elapsedRealtime() >= review.expiresAt) throw CronProjectRefused("EXPIRED")
    }

    internal suspend fun decideCronProject(review: CronProjectReview, choice: CronRunOnceDecision): CronProjectObservation? {
        requireProjectReview(review)
        projectReview = null
        val reply = cronCall(CronProjectProtocol.Decide, CronProjectProtocol.decide(review, choice))
        return CronProjectProtocol.decided(reply, review, choice)
    }

    internal suspend fun observeCronProject(occurrence: CronProjectOccurrence): CronProjectObservation {
        val owner = binding.target
        val reply = cronCall(CronProjectProtocol.Observe, CronProjectProtocol.select(owner, occurrence))
        return CronProjectProtocol.observed(reply, owner, occurrence)
    }

    private suspend fun cronCall(method: String, params: JsonObject): JsonObject {
        synchronized(this) { check(!resuming && !resumed) }
        val expected = binding
        val reply = try {
            call(method, params)
        } catch (error: GatewayRpcException) {
            if (error.code == -32601) {
                if (method == CronExecutionHistory.Method || method == CronExecutionOutput.Method) {
                    throw CronExecutionHistoryUnavailable()
                }
                if (method in CronProjectProtocol.methods) throw CronProjectRefused("UNSUPPORTED")
                if (method in CronRunOnceProtocol.methods) throw CronRunOnceRefused("UNSUPPORTED")
                throw CronTimingRefused("UNSUPPORTED", false)
            }
            throw error
        }
        if (binding !== expected) throw CanonicalScopeChanged()
        return reply
    }

    internal suspend fun projectMetadata(id:String?=null,conversations:Boolean=false):JsonObject {
        val expected=binding
        require(!conversations || id!=null)
        id?.let {require(HostProjectProtocol.validId(it))}
        val result=call(if(conversations) "projects.project_sessions" else if(id==null) "projects.list" else "projects.get",buildJsonObject {
            put("profile",expected.target.profile)
            if(conversations) {put("project_id",checkNotNull(id));put("session_limit",HostProjectConversationProtocol.SessionLimit)} else id?.let {put("id",it)}
        })
        if(binding!==expected) throw CanonicalScopeChanged()
        return result
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

    private fun requireUnattachedOwner(expected: CanonicalFeedOwner): CanonicalRuntimeBinding {
        synchronized(this) { check(!resuming && !resumed) }
        val current = binding
        if (current.target != expected.target || current.homeId != expected.homeId || current.profileId != expected.profileId ||
            current.instanceId != expected.instanceId || current.authorityEpoch != expected.authorityEpoch) throw CanonicalReviewChanged()
        return current
    }

    internal suspend fun readContext(expected: CanonicalFeedOwner): HermesContextStats {
        val current = requireUnattachedOwner(expected)
        val result = call("session.stats", buildJsonObject { put("session_id", expected.sessionId) })
        if (binding !== current) throw CanonicalScopeChanged()
        return HermesContextStats.parse(result)
    }

    private suspend fun attachedWorkerCall(
        expected: CanonicalSessionSnapshot, request: JsonObject? = null
    ): JsonObject {
        val current = binding
        synchronized(this) {
            if (resuming || snapshot !== expected || feed == null) throw CanonicalReviewChanged()
        }
        val result = call(
            if (request == null) AttachedWorkerProtocol.Describe else AttachedWorkerProtocol.Read,
            AttachedWorkerProtocol.params(expected, request)
        )
        if (binding !== current) throw CanonicalScopeChanged()
        return result
    }

    internal suspend fun describeWorker(expected: CanonicalSessionSnapshot): AttachedWorkerProject =
        AttachedWorkerProtocol.describe(attachedWorkerCall(expected))

    internal suspend fun prepareWorker(expected: CanonicalSessionSnapshot, project: AttachedWorkerProject): AttachedWorkerBinding {
        val request = AttachedWorkerProtocol.request(project, "prepare")
        return AttachedWorkerProtocol.prepare(attachedWorkerCall(expected, request), request, project)
    }

    internal suspend fun listWorker(expected: CanonicalSessionSnapshot, binding: AttachedWorkerBinding,
                                    entry: AttachedWorkerEntry?): AttachedWorkerListing {
        require(entry == null || entry.directory)
        val request = AttachedWorkerProtocol.request(binding.project, "list", binding, entry)
        return AttachedWorkerProtocol.list(attachedWorkerCall(expected, request), request, binding)
    }

    internal suspend fun previewWorker(expected: CanonicalSessionSnapshot, binding: AttachedWorkerBinding,
                                       entry: AttachedWorkerEntry): AttachedWorkerPreview {
        require(!entry.directory)
        val request = AttachedWorkerProtocol.request(binding.project, "preview", binding, entry)
        return AttachedWorkerProtocol.preview(attachedWorkerCall(expected, request), request, binding)
    }

    /** A separately authenticated socket; no attachment, prompt or work is replayed. */
    internal suspend fun changeWorkspaceReviewed(
        review:CanonicalWorkspaceReview,beforeSend:suspend ()->Unit,
        acknowledged:suspend (CanonicalWorkspaceAcknowledgement)->Unit
    ):CanonicalWorkspaceAcknowledgement {
        val current = requireUnattachedOwner(review.expected.owner)
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
