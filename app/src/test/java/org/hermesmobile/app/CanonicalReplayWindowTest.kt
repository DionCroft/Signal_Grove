package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CanonicalReplayWindowTest {
    private val owner = CanonicalFeedOwner(
        ManagementTarget("a".repeat(64), "https://fixture.invalid/dashboard/", "default", "fixture-user"),
        "b".repeat(64), "/fixture/home", "fixture-instance", "s", 7
    )
    private val start = CanonicalReplayCursor("fixture-replay", 1)
    private fun event(n: Long, text: String = "event-$n", type: String = "status.update") = buildJsonObject {
        put("type", type); put("session_id", "s"); put("replay_epoch", start.epoch); put("seq", n)
        put("payload", buildJsonObject { put("kind", "status"); put("text", text) })
    }
    private fun live(row: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0"); put("method", "event"); put("params", row)
    }
    private fun replay(rows: List<JsonObject>, latest: Long = rows.lastOrNull()?.get("seq")?.jsonPrimitive?.long ?: start.sequence) = buildJsonObject {
        put("events", JsonArray(rows)); put("latest_seq", latest); put("last_sequence", latest)
        put("epoch", start.epoch); put("replay_epoch", start.epoch)
        put("truncated", false); put("snapshot_required", false); put("count", rows.size)
    }
    private fun changed(j: JsonObject, key: String, value: JsonElement) = JsonObject(j + (key to value))
    private fun held(w: CanonicalReplayWindow, result: CanonicalFeedDelivery, reason: CanonicalFeedFailure, sequence: Long = 1) {
        assertEquals(CanonicalFeedPhase.SnapshotRequired, result.status.phase)
        assertEquals(reason, result.status.failure); assertTrue(result.events.isEmpty())
        assertEquals(sequence, result.status.cursor.sequence); assertEquals(0, w.status().bufferedEvents)
    }

    @Test fun attachFirstReplayAndRacingLiveMergeOnce() {
        val w = CanonicalReplayWindow(owner, start)
        assertTrue(w.offerLive(owner, live(event(3))).events.isEmpty())
        w.offerLive(owner, live(event(4)))
        val delivery = w.reconcile(owner, replay(listOf(event(2), event(3))))
        assertEquals(listOf(2L, 3L, 4L), delivery.events.map { it.sequence })
        assertEquals(CanonicalFeedPhase.Following, delivery.status.phase)
        assertEquals(4L, delivery.status.cursor.sequence)
        assertTrue(w.offerLive(owner, live(event(3))).events.isEmpty())
        assertEquals(listOf(5L), w.offerLive(owner, live(event(5))).events.map { it.sequence })
    }

    @Test fun outOfOrderBufferedFramesAreOrderedBeforeAtomicDelivery() {
        val w = CanonicalReplayWindow(owner, start)
        for (n in 80L downTo 3L) w.offerLive(owner, live(event(n)))
        val result = w.reconcile(owner, replay((2L..50L).map { event(it) }))
        assertEquals((2L..80L).toList(), result.events.map { it.sequence })
        assertEquals(80L, result.status.cursor.sequence)
    }

    @Test fun concurrentBufferedDuplicatesDoNotLoseOrRepeatEvents() {
        val w = CanonicalReplayWindow(owner, start)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (3L..40L).flatMap { n -> List(3) { pool.submit { w.offerLive(owner, live(event(n))) } } }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
        val result = w.reconcile(owner, replay((2L..30L).map { event(it) }))
        assertEquals((2L..40L).toList(), result.events.map { it.sequence })
        assertEquals(40L, result.status.cursor.sequence)
    }

    @Test fun objectKeyOrderDoesNotCreateAConflictingDuplicate() {
        val w = CanonicalReplayWindow(owner, start)
        val row = event(2)
        w.offerLive(owner, live(JsonObject(row.entries.reversed().associate { it.toPair() })))
        assertEquals(listOf(2L), w.reconcile(owner, replay(listOf(row))).events.map { it.sequence })
        assertEquals(CanonicalFeedPhase.Following, w.offerLive(owner, live(row)).status.phase)
    }

    @Test fun bufferedPayloadIsFrozenBeforeItsCallerChangesJson() {
        val payload = mutableMapOf<String, JsonElement>("kind" to JsonPrimitive("status"), "text" to JsonPrimitive("before"))
        val row = changed(event(2), "payload", JsonObject(payload))
        val w = CanonicalReplayWindow(owner, start)
        w.offerLive(owner, live(row)); payload["text"] = JsonPrimitive("after")
        val result = w.reconcile(owner, replay(listOf(event(2, "before"))))
        assertEquals(JsonPrimitive("before"), result.events.single().payload["text"])
    }

    @Test fun conflictingBufferedDuplicateQuarantinesAllOutput() {
        val w = CanonicalReplayWindow(owner, start)
        w.offerLive(owner, live(event(3)))
        held(w, w.offerLive(owner, live(event(3, "conflict"))), CanonicalFeedFailure.ConflictingDuplicate)
        assertTrue(w.reconcile(owner, replay(listOf(event(2), event(3)))).events.isEmpty())
    }

    @Test fun conflictingReplayAndLiveBatchNeverPartiallyAdvances() {
        val w = CanonicalReplayWindow(owner, start)
        w.offerLive(owner, live(event(3, "conflict")))
        held(w, w.reconcile(owner, replay(listOf(event(2), event(3)))), CanonicalFeedFailure.ConflictingDuplicate)
    }

    @Test fun aPreviouslyDeliveredConflictPreservesTheLastSafeCursor() {
        val w = CanonicalReplayWindow(owner, start)
        w.reconcile(owner, replay(listOf(event(2))))
        held(w, w.offerLive(owner, live(event(2, "conflict"))), CanonicalFeedFailure.ConflictingDuplicate, 2)
    }

    @Test fun missingReplayOrLiveSequencesRequireSnapshotWithoutPartialDelivery() {
        for (packet in listOf(replay(listOf(event(3))), replay(listOf(event(2)), 3))) {
            val w = CanonicalReplayWindow(owner, start)
            held(w, w.reconcile(owner, packet), CanonicalFeedFailure.SequenceGap)
        }
        val w = CanonicalReplayWindow(owner, start)
        w.reconcile(owner, replay(listOf(event(2))))
        held(w, w.offerLive(owner, live(event(4))), CanonicalFeedFailure.SequenceGap, 2)
    }

    @Test fun replayOrderAndDuplicateRowsAreNotSilentlyRepaired() {
        for (rows in listOf(listOf(event(3), event(2)), listOf(event(2), event(2)))) {
            val w = CanonicalReplayWindow(owner, start)
            held(w, w.reconcile(owner, replay(rows)), CanonicalFeedFailure.SequenceGap)
        }
    }

    @Test fun actorProfileHostHomeInstanceAndAuthorityCannotShareAWindow() {
        val foreign = listOf(
            owner.copy(target = owner.target.copy(actor = "other-user")),
            owner.copy(target = owner.target.copy(profile = "worker")),
            owner.copy(target = owner.target.copy(url = "https://other.invalid/dashboard/")),
            owner.copy(target = owner.target.copy(apiScope = "c".repeat(64))),
            owner.copy(homeId = "c".repeat(64)), owner.copy(profileId = "/other/home"),
            owner.copy(instanceId = "other-instance"), owner.copy(sessionId = "other-session"),
            owner.copy(authorityEpoch = 8)
        )
        for (source in foreign) {
            val w = CanonicalReplayWindow(owner, start)
            held(w, w.offerLive(source, live(event(2))), CanonicalFeedFailure.OwnerChanged)
            val other = CanonicalReplayWindow(owner, start)
            held(other, other.reconcile(source, replay(listOf(event(2)))), CanonicalFeedFailure.OwnerChanged)
        }
    }

    @Test fun foreignSessionAndEpochCannotAdvanceThisCursor() {
        var w = CanonicalReplayWindow(owner, start)
        held(w, w.offerLive(owner, live(changed(event(2), "session_id", JsonPrimitive("foreign")))), CanonicalFeedFailure.InvalidWire)
        w = CanonicalReplayWindow(owner, start)
        held(w, w.offerLive(owner, live(changed(event(2), "replay_epoch", JsonPrimitive("other")))), CanonicalFeedFailure.EpochChanged)
        w = CanonicalReplayWindow(owner, start)
        held(w, w.reconcile(owner, changed(replay(listOf(event(2))), "epoch", JsonPrimitive("other"))), CanonicalFeedFailure.EpochChanged)
    }

    @Test fun optionalExecutionStampsStayTypedAndAuthorityBound() {
        val row = changed(changed(event(2), "authority_epoch", JsonPrimitive(7)), "execution_generation", JsonPrimitive(9))
        val w = CanonicalReplayWindow(owner, start)
        assertEquals(2L, w.reconcile(owner, replay(listOf(row))).status.cursor.sequence)
        var bad = CanonicalReplayWindow(owner, start)
        held(bad, bad.offerLive(owner, live(changed(row, "authority_epoch", JsonPrimitive(8)))), CanonicalFeedFailure.OwnerChanged)
        bad = CanonicalReplayWindow(owner, start)
        held(bad, bad.offerLive(owner, live(changed(row, "execution_generation", JsonPrimitive(true)))), CanonicalFeedFailure.InvalidWire)
    }

    @Test fun sequenceNumbersRejectBooleansStringsDecimalsAndOverflow() {
        for (value in listOf(JsonPrimitive(true), JsonPrimitive("2"), JsonPrimitive(2.5), JsonPrimitive(-1), JsonPrimitive(0), Json.parseToJsonElement("9223372036854775808"), JsonNull)) {
            val w = CanonicalReplayWindow(owner, start)
            held(w, w.offerLive(owner, live(changed(event(2), "seq", value))), CanonicalFeedFailure.InvalidWire)
        }
    }

    @Test fun malformedReplayAliasesCountsAndFlagsFailClosed() {
        val good = replay(listOf(event(2)))
        val bad = listOf(
            changed(good, "count", JsonPrimitive(0)), changed(good, "count", JsonPrimitive("1")),
            changed(good, "last_sequence", JsonPrimitive(3)), changed(good, "latest_seq", JsonPrimitive(true)),
            changed(good, "truncated", JsonPrimitive("false")), changed(good, "snapshot_required", JsonNull),
            changed(good, "events", JsonObject(emptyMap()))
        )
        for (packet in bad) {
            val w = CanonicalReplayWindow(owner, start)
            held(w, w.reconcile(owner, packet), CanonicalFeedFailure.InvalidWire)
        }
    }

    @Test fun truncatedReplayAndUnsequencedGapSignalsRequireFreshSnapshot() {
        for (key in listOf("truncated", "snapshot_required")) {
            val w = CanonicalReplayWindow(owner, start)
            held(w, w.reconcile(owner, changed(replay(emptyList()), key, JsonPrimitive(true))), CanonicalFeedFailure.ReplayTruncated)
        }
        val w = CanonicalReplayWindow(owner, start)
        held(w, w.offerLive(owner, live(buildJsonObject {
            put("type", "session.replay_gap"); put("session_id", "s")
            put("payload", buildJsonObject { put("replay_epoch", start.epoch); put("latest_seq", 12) })
        })), CanonicalFeedFailure.ReplayTruncated)
    }

    @Test fun bufferAndCombinedReplayLimitsReleasePendingPayloads() {
        var w = CanonicalReplayWindow(owner, start, maxBufferedEvents = 2)
        w.offerLive(owner, live(event(3))); w.offerLive(owner, live(event(4)))
        held(w, w.offerLive(owner, live(event(5))), CanonicalFeedFailure.BufferLimit)
        w = CanonicalReplayWindow(owner, start, maxBufferedEvents = 3)
        w.offerLive(owner, live(event(4))); w.offerLive(owner, live(event(5)))
        held(w, w.reconcile(owner, replay(listOf(event(2), event(3)))), CanonicalFeedFailure.BufferLimit)
    }

    @Test fun eventAndPendingByteBudgetsAreEnforced() {
        var w = CanonicalReplayWindow(owner, start, maxEventBytes = 512, maxBufferedBytes = 1024)
        held(w, w.offerLive(owner, live(event(2, "x".repeat(600)))), CanonicalFeedFailure.InvalidWire)
        w = CanonicalReplayWindow(owner, start, maxEventBytes = 512, maxBufferedBytes = 1024)
        w.offerLive(owner, live(event(3, "x".repeat(350))))
        w.offerLive(owner, live(event(4, "x".repeat(350))))
        held(w, w.offerLive(owner, live(event(5, "x".repeat(350)))), CanonicalFeedFailure.BufferLimit)
    }

    @Test fun forgottenOldDuplicateIsHeldInsteadOfSilentlyDiscarded() {
        val w = CanonicalReplayWindow(owner, start, rememberedEvents = 1)
        w.reconcile(owner, replay(listOf(event(2), event(3))))
        held(w, w.offerLive(owner, live(event(2))), CanonicalFeedFailure.HistoryUnavailable, 3)
    }

    @Test fun unrecognisedAndAssistantFinalEventsRemainObservations() {
        val final = changed(event(2, type = "assistant.final"), "payload", buildJsonObject { put("finish_reason", "stop") })
        val w = CanonicalReplayWindow(owner, start)
        val result = w.reconcile(owner, replay(listOf(final, event(3, type = "future.event"))))
        assertEquals(listOf("assistant.final", "future.event"), result.events.map { it.type })
        assertEquals(CanonicalFeedPhase.Following, result.status.phase)
    }

    @Test fun malformedEnvelopesPayloadsAndDeepObjectsDoNotEscape() {
        var nested: JsonElement = JsonPrimitive("private")
        repeat(40) { nested = buildJsonObject { put("nested", nested) } }
        val bad = listOf(
            changed(live(event(2)), "jsonrpc", JsonPrimitive("1.0")),
            changed(live(event(2)), "id", JsonPrimitive("request")),
            live(changed(event(2), "payload", JsonPrimitive("wrong"))),
            live(changed(event(2), "payload", buildJsonObject { put("deep", nested) }))
        )
        for (packet in bad) {
            val w = CanonicalReplayWindow(owner, start)
            held(w, w.offerLive(owner, packet), CanonicalFeedFailure.InvalidWire)
        }
    }

    @Test fun cursorOverflowAndSecondReplayNeverInventProgress() {
        var w = CanonicalReplayWindow(owner, CanonicalReplayCursor(start.epoch, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, w.reconcile(owner, replay(emptyList(), Long.MAX_VALUE)).status.cursor.sequence)
        w = CanonicalReplayWindow(owner, start)
        w.reconcile(owner, replay(emptyList()))
        held(w, w.reconcile(owner, replay(listOf(event(2)))), CanonicalFeedFailure.ProtocolOrder)
    }

    @Test fun closeDropsBuffersAndNeverAcceptsLaterFrames() {
        val w = CanonicalReplayWindow(owner, start)
        w.offerLive(owner, live(event(3))); w.close()
        val result = w.reconcile(owner, replay(listOf(event(2), event(3))))
        assertEquals(CanonicalFeedPhase.Closed, result.status.phase); assertTrue(result.events.isEmpty())
        assertEquals(0, result.status.bufferedEvents); assertEquals(1L, result.status.cursor.sequence)
        assertTrue(w.offerLive(owner, live(event(2))).events.isEmpty())
    }

    @Test fun debugStringsDoNotRevealOwnerEpochOrPayload() {
        val w = CanonicalReplayWindow(owner, start)
        val result = w.reconcile(owner, replay(listOf(event(2, "private transcript"))))
        for (value in listOf(owner, start, w, result, result.status, result.events.single())) {
            val text = value.toString()
            for (private in listOf("fixture-user", "fixture.invalid", "fixture-replay", "fixture-instance", "private transcript"))
                assertFalse(text, text.contains(private))
        }
    }
    private fun actualDispatcherVector(name: String, commit: String) {
        val vector = checkNotNull(javaClass.classLoader!!.getResourceAsStream(name)).use {
            Json.parseToJsonElement(it.bufferedReader().readText()).jsonObject
        }
        assertEquals(JsonPrimitive(commit), vector["sourceCommit"])
        assertEquals(JsonPrimitive(true), vector["syntheticOnly"])
        assertEquals(JsonPrimitive(false), vector["authenticatedNetworkClaim"])
        val observed = vector["owner"]!!.jsonObject
        val source = owner.copy(
            profileId = observed["profile_id"]!!.jsonPrimitive.content,
            instanceId = observed["instance_id"]!!.jsonPrimitive.content,
            sessionId = observed["session_id"]!!.jsonPrimitive.content,
            authorityEpoch = observed["authority_epoch"]!!.jsonPrimitive.long
        )
        val seen = vector["initialCursor"]!!.jsonObject
        val cursor = CanonicalReplayCursor(seen["replay_epoch"]!!.jsonPrimitive.content, seen["last_sequence"]!!.jsonPrimitive.long)
        val window = CanonicalReplayWindow(source, cursor)
        val frames = vector["live"]!!.jsonArray
        frames.forEach { assertTrue(window.offerLive(source, it.jsonObject).events.isEmpty()) }
        val result = window.reconcile(source, vector["replay"]!!.jsonObject)
        assertEquals(vector["expectedSequences"]!!.jsonArray.map { it.jsonPrimitive.long }, result.events.map { it.sequence })
        assertEquals(CanonicalFeedPhase.Following, result.status.phase)
        frames.forEach { assertTrue(window.offerLive(source, it.jsonObject).events.isEmpty()) }
        assertEquals(4L, window.status().cursor.sequence)
    }

    @Test fun stableActualDispatcherReplayAndLiveFramesMergeOnce() =
        actualDispatcherVector("canonical-replay-stable.json", "2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19")

    @Test fun priorActualDispatcherReplayAndLiveFramesMergeOnce() =
        actualDispatcherVector("canonical-replay-prior.json", "bf0203a794a07aac3ff764972ab9087baa4e4f4e")

    @Test fun extremeEventNestingIsRejectedBeforeSerialization() {
        var nested: JsonElement = JsonPrimitive("private")
        repeat(5000) { nested = JsonObject(mapOf("next" to nested)) }
        val w = CanonicalReplayWindow(owner, start)
        val row = changed(event(2), "payload", JsonObject(mapOf("deep" to nested)))
        held(w, w.offerLive(owner, live(row)), CanonicalFeedFailure.InvalidWire)
    }

    @Test fun extremeReplayNestingIsRejectedBeforeSerialization() {
        var nested: JsonElement = JsonPrimitive("private")
        repeat(5000) { nested = JsonArray(listOf(nested)) }
        val w = CanonicalReplayWindow(owner, start)
        val row = changed(event(2), "payload", JsonObject(mapOf("deep" to nested)))
        held(w, w.reconcile(owner, replay(listOf(row))), CanonicalFeedFailure.InvalidWire)
    }

    @Test fun utf8AndEscapedCharactersUseTheirWireByteBudget() {
        for (text in listOf("漢".repeat(200), "\n".repeat(250))) {
            val w = CanonicalReplayWindow(owner, start, maxEventBytes = 512, maxBufferedBytes = 1024)
            held(w, w.offerLive(owner, live(event(2, text))), CanonicalFeedFailure.InvalidWire)
        }
        val w = CanonicalReplayWindow(owner, start, maxEventBytes = 512, maxBufferedBytes = 1024)
        assertEquals(2L, w.reconcile(owner, replay(listOf(event(2, "🌳".repeat(10))))).status.cursor.sequence)
    }

    @Test fun malformedUnicodeAndNonJsonPrimitiveValuesAreRefused() {
        for (bad in listOf(JsonPrimitive(0xD800.toChar().toString()), JsonPrimitive(Double.NaN))) {
            val w = CanonicalReplayWindow(owner, start)
            val row = changed(event(2), "payload", buildJsonObject { put("private", bad) })
            held(w, w.offerLive(owner, live(row)), CanonicalFeedFailure.InvalidWire)
        }
    }

}
