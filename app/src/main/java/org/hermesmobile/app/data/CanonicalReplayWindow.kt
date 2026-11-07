package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.TreeMap

/** A verified connection's owner. No field is inferred from a ready flag or a project pointer. */
internal data class CanonicalFeedOwner(
    val target: ManagementTarget,
    val homeId: String,
    val profileId: String,
    val instanceId: String,
    val sessionId: String,
    val authorityEpoch: Long
) {
    init {
        require(homeId.matches(Regex("[a-f0-9]{64}")))
        require(listOf(profileId, instanceId, sessionId).all { it.isNotBlank() && it.length <= 8192 && it.none(Char::isISOControl) })
        require(authorityEpoch >= 0)
    }
    override fun toString() = "CanonicalFeedOwner(redacted)"
}

internal data class CanonicalReplayCursor(val epoch: String, val sequence: Long) {
    init { require(epoch.isNotBlank() && epoch.length <= 256 && epoch.none(Char::isISOControl) && sequence >= 0) }
    override fun toString() = "CanonicalReplayCursor(redacted)"
}

internal enum class CanonicalFeedPhase { Buffering, Following, SnapshotRequired, Closed }
internal enum class CanonicalFeedFailure {
    OwnerChanged, InvalidWire, EpochChanged, SequenceGap, ConflictingDuplicate,
    ReplayTruncated, BufferLimit, HistoryUnavailable, ProtocolOrder
}
internal data class CanonicalFeedStatus(
    val phase: CanonicalFeedPhase,
    val cursor: CanonicalReplayCursor,
    val failure: CanonicalFeedFailure?,
    val bufferedEvents: Int
) {
    override fun toString() = "CanonicalFeedStatus($phase)"
}

/** An observation only. Neither an event name nor its payload establishes terminal execution. */
internal class CanonicalFeedEvent private constructor(
    val type: String,
    val sequence: Long,
    val payload: JsonObject,
    internal val fingerprint: String,
    internal val wireBytes: Int
) {
    override fun toString() = "CanonicalFeedEvent(redacted)"

    companion object {
        private fun canonical(value: JsonElement, depth: Int = 0): JsonElement {
            require(depth <= 32)
            return when (value) {
                is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value, depth + 1) })
                is JsonArray -> JsonArray(value.map { canonical(it, depth + 1) })
                else -> value
            }
        }

        internal fun text(j: JsonObject, name: String, limit: Int): String =
            (j[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.also { require(it.isNotBlank() && it.length <= limit && it.none(Char::isISOControl)) }
                ?: throw IllegalArgumentException("Unsupported canonical event.")

        internal fun number(j: JsonObject, name: String): Long =
            (j[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
                ?.also { require(it >= 0) }
                ?: throw IllegalArgumentException("Unsupported canonical event.")

        internal fun parse(j: JsonObject, owner: CanonicalFeedOwner, cursor: CanonicalReplayCursor, maxBytes: Int): CanonicalFeedEvent {
            FeedWireBudget(maxBytes).check(j)
            val raw = j.toString().toByteArray(Charsets.UTF_8)
            require(raw.size <= maxBytes)
            require(text(j, "session_id", 8192) == owner.sessionId)
            if (text(j, "replay_epoch", 256) != cursor.epoch) throw FeedRefusal(CanonicalFeedFailure.EpochChanged)
            for (name in listOf("authority_epoch", "execution_generation")) {
                if (j[name] != null && j[name] != JsonNull) {
                    val value = number(j, name)
                    if (name == "authority_epoch" && value != owner.authorityEpoch) throw FeedRefusal(CanonicalFeedFailure.OwnerChanged)
                }
            }
            val type = text(j, "type", 256)
            val sequence = number(j, "seq").also { require(it > 0) }
            require(j["payload"] is JsonObject)
            // Freeze caller-owned JSON and fingerprint semantic object-key order, including stamps.
            val frozen = Json.parseToJsonElement(canonical(j).toString()).jsonObject
            val hash = MessageDigest.getInstance("SHA-256").digest(frozen.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return CanonicalFeedEvent(type, sequence, frozen["payload"]!!.jsonObject, hash, raw.size)
        }
    }
}

private class FeedWireBudget(private var remaining: Int) {
    private fun take(bytes: Int) { require(bytes >= 0 && bytes <= remaining); remaining -= bytes }
    private fun string(value: String) {
        take(2)
        var i = 0
        while (i < value.length) {
            val c = value[i].code
            when {
                c in 0xD800..0xDBFF -> {
                    require(i + 1 < value.length && value[i + 1].code in 0xDC00..0xDFFF)
                    take(4); i += 2; continue
                }
                c in 0xDC00..0xDFFF -> throw IllegalArgumentException()
                c == 34 || c == 92 || c in listOf(8, 9, 10, 12, 13) -> take(2)
                c < 32 -> take(6)
                c <= 0x7F -> take(1)
                c <= 0x7FF -> take(2)
                else -> take(3)
            }
            i++
        }
    }
    fun check(value: JsonElement, depth: Int = 0) {
        require(depth <= 32)
        when (value) {
            is JsonObject -> {
                take(2)
                value.entries.forEachIndexed { index, entry ->
                    if (index > 0) take(1)
                    string(entry.key); take(1); check(entry.value, depth + 1)
                }
            }
            is JsonArray -> {
                take(2)
                value.forEachIndexed { index, item -> if (index > 0) take(1); check(item, depth + 1) }
            }
            is JsonPrimitive -> if (value.isString) string(value.content) else {
                take(value.content.length)
                require(value.content in setOf("null", "true", "false") || number.matches(value.content))
            }
        }
    }
    companion object {
        private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    }
}

private class FeedRefusal(val failure: CanonicalFeedFailure) : Exception("Canonical stream requires a new snapshot.")

internal class CanonicalFeedDelivery(val status: CanonicalFeedStatus, val events: List<CanonicalFeedEvent>) {
    override fun toString() = "CanonicalFeedDelivery(${status.phase}, ${events.size} observations)"
}

/**
 * Attach first, buffer live frames, then reconcile replay from the last accepted cursor.
 * This class sends no RPC, changes no transcript and cannot replay an admission or a tool.
 * A discontinuity holds all output until a caller obtains a fresh reviewed snapshot/owner.
 */
internal class CanonicalReplayWindow(
    private val owner: CanonicalFeedOwner,
    initialCursor: CanonicalReplayCursor,
    private val maxBufferedEvents: Int = 256,
    private val maxBufferedBytes: Int = 2 * 1024 * 1024,
    private val maxEventBytes: Int = 256 * 1024,
    private val rememberedEvents: Int = 1024
) : AutoCloseable {
    init {
        require(maxBufferedEvents in 1..2048 && maxEventBytes in 64..1024 * 1024)
        require(maxBufferedBytes in maxEventBytes..8 * 1024 * 1024 && rememberedEvents in 1..8192)
    }
    private var phase = CanonicalFeedPhase.Buffering
    private var cursor = initialCursor
    private var failure: CanonicalFeedFailure? = null
    private val buffered = TreeMap<Long, CanonicalFeedEvent>()
    private var bufferedBytes = 0
    private val fingerprints = linkedMapOf<Long, String>()

    @Synchronized fun status() = CanonicalFeedStatus(phase, cursor, failure, buffered.size)
    private fun delivery(events: List<CanonicalFeedEvent> = emptyList()) = CanonicalFeedDelivery(status(), events.toList())
    private fun refuse(reason: CanonicalFeedFailure): CanonicalFeedDelivery {
        phase = CanonicalFeedPhase.SnapshotRequired; failure = reason
        buffered.clear(); bufferedBytes = 0; fingerprints.clear()
        return delivery()
    }
    private fun inactive() = phase == CanonicalFeedPhase.SnapshotRequired || phase == CanonicalFeedPhase.Closed
    private fun remember(event: CanonicalFeedEvent) {
        fingerprints[event.sequence] = event.fingerprint
        while (fingerprints.size > rememberedEvents) fingerprints.remove(fingerprints.keys.first())
    }
    private fun duplicate(event: CanonicalFeedEvent): CanonicalFeedDelivery =
        when (fingerprints[event.sequence]) {
            event.fingerprint -> delivery()
            null -> refuse(CanonicalFeedFailure.HistoryUnavailable)
            else -> refuse(CanonicalFeedFailure.ConflictingDuplicate)
        }
    private inline fun checked(block: () -> CanonicalFeedDelivery): CanonicalFeedDelivery =
        try { block() }
        catch (e: FeedRefusal) { refuse(e.failure) }
        catch (_: Exception) { refuse(CanonicalFeedFailure.InvalidWire) }

    @Synchronized fun offerLive(source: CanonicalFeedOwner, frame: JsonObject): CanonicalFeedDelivery {
        if (inactive()) return delivery()
        if (source != owner) return refuse(CanonicalFeedFailure.OwnerChanged)
        return checked {
            require(frame.keys == setOf("jsonrpc", "method", "params") && frame["jsonrpc"] == JsonPrimitive("2.0") && frame["method"] == JsonPrimitive("event"))
            FeedWireBudget(maxEventBytes + 128).check(frame)
            val params = frame["params"] as? JsonObject ?: throw IllegalArgumentException()
            if (params["type"] == JsonPrimitive("session.replay_gap")) return@checked refuse(CanonicalFeedFailure.ReplayTruncated)
            val event = CanonicalFeedEvent.parse(params, owner, cursor, maxEventBytes)
            if (event.sequence <= cursor.sequence) return@checked duplicate(event)
            if (phase == CanonicalFeedPhase.Buffering) {
                val old = buffered[event.sequence]
                if (old != null) {
                    if (old.fingerprint != event.fingerprint) return@checked refuse(CanonicalFeedFailure.ConflictingDuplicate)
                } else {
                    if (buffered.size >= maxBufferedEvents || bufferedBytes.toLong() + event.wireBytes > maxBufferedBytes)
                        return@checked refuse(CanonicalFeedFailure.BufferLimit)
                    buffered[event.sequence] = event; bufferedBytes += event.wireBytes
                }
                delivery()
            } else {
                if (cursor.sequence == Long.MAX_VALUE || event.sequence != cursor.sequence + 1) return@checked refuse(CanonicalFeedFailure.SequenceGap)
                cursor = CanonicalReplayCursor(cursor.epoch, event.sequence); remember(event); delivery(listOf(event))
            }
        }
    }

    @Synchronized fun reconcile(source: CanonicalFeedOwner, replay: JsonObject): CanonicalFeedDelivery {
        if (inactive()) return delivery()
        if (source != owner) return refuse(CanonicalFeedFailure.OwnerChanged)
        if (phase != CanonicalFeedPhase.Buffering) return refuse(CanonicalFeedFailure.ProtocolOrder)
        return checked {
            FeedWireBudget(maxBufferedBytes).check(replay)
            require(replay.toString().toByteArray(Charsets.UTF_8).size <= maxBufferedBytes)
            fun flag(name: String) = (replay[name] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
                ?: throw IllegalArgumentException()
            if (flag("truncated") || flag("snapshot_required")) return@checked refuse(CanonicalFeedFailure.ReplayTruncated)
            if (CanonicalFeedEvent.text(replay, "epoch", 256) != cursor.epoch ||
                CanonicalFeedEvent.text(replay, "replay_epoch", 256) != cursor.epoch)
                return@checked refuse(CanonicalFeedFailure.EpochChanged)
            val latest = CanonicalFeedEvent.number(replay, "latest_seq")
            require(latest == CanonicalFeedEvent.number(replay, "last_sequence") && latest >= cursor.sequence)
            val rows = replay["events"] as? JsonArray ?: throw IllegalArgumentException()
            require(rows.size <= maxBufferedEvents && CanonicalFeedEvent.number(replay, "count") == rows.size.toLong())
            val merged = TreeMap<Long, CanonicalFeedEvent>()
            var next = cursor.sequence
            for (row in rows) {
                val event = CanonicalFeedEvent.parse(row as? JsonObject ?: throw IllegalArgumentException(), owner, cursor, maxEventBytes)
                if (next == Long.MAX_VALUE || event.sequence != next + 1) return@checked refuse(CanonicalFeedFailure.SequenceGap)
                next = event.sequence; merged[next] = event
            }
            if (next != latest) return@checked refuse(CanonicalFeedFailure.SequenceGap)
            for ((seq, event) in buffered) {
                val old = merged[seq]
                if (old != null && old.fingerprint != event.fingerprint) return@checked refuse(CanonicalFeedFailure.ConflictingDuplicate)
                merged[seq] = event
            }
            if (merged.size > maxBufferedEvents || merged.values.sumOf { it.wireBytes.toLong() } > maxBufferedBytes)
                return@checked refuse(CanonicalFeedFailure.BufferLimit)
            next = cursor.sequence
            for (seq in merged.keys) {
                if (next == Long.MAX_VALUE || seq != next + 1) return@checked refuse(CanonicalFeedFailure.SequenceGap)
                next = seq
            }
            // Commit only after the entire replay/live batch passes; a later bad frame emits nothing.
            val events = merged.values.toList()
            events.forEach(::remember)
            cursor = CanonicalReplayCursor(cursor.epoch, next); phase = CanonicalFeedPhase.Following
            buffered.clear(); bufferedBytes = 0
            delivery(events)
        }
    }

    @Synchronized override fun close() {
        phase = CanonicalFeedPhase.Closed; failure = null
        buffered.clear(); bufferedBytes = 0; fingerprints.clear()
    }
    override fun toString() = "CanonicalReplayWindow(redacted)"
}
