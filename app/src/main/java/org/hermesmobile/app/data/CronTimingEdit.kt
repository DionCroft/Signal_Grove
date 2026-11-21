package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.OffsetDateTime

internal class CronTimingInvalid : Exception()
internal class CronTimingRefused(val code: String, val submitted: Boolean) : Exception()

internal data class CronTimingRepeat(val times: Long?, val completed: Long)

internal data class CronTimingEffect(
    val schedule: JsonObject, val display: String, val timezone: String?,
    val catchUp: Boolean?, val grace: Int?, val repeat: CronTimingRepeat?
) {
    fun fingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(buildJsonObject {
            put("schedule", JsonObject(schedule.toSortedMap()))
            put("timezone", timezone); put("catch_up", catchUp); put("grace_minutes", grace)
            put("repeat", repeat?.let { buildJsonObject { put("times", it.times); put("completed", it.completed) } } ?: JsonNull)
        }.toString().encodeToByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }

    override fun toString() = "CronTimingEffect(redacted)"
}

internal class CronTimingDraft private constructor(val changes: JsonObject) {
    fun matches(before: CronTimingEffect, after: CronTimingEffect): Boolean {
        val schedule = (changes["schedule"] as? JsonPrimitive)?.content?.let(::parseHermesCronSchedule)
        return (schedule?.matchesStoredSchedule(after.schedule, null) ?: (before.schedule == after.schedule)) &&
            (changes["timezone"] ?: JsonPrimitive(before.timezone)) == JsonPrimitive(after.timezone) &&
            (changes["catch_up"] ?: JsonPrimitive(before.catchUp)) == JsonPrimitive(after.catchUp) &&
            (changes["grace_minutes"] ?: JsonPrimitive(before.grace)) == JsonPrimitive(after.grace)
    }

    override fun toString() = "CronTimingDraft(redacted)"

    companion object {
        fun create(before: CronTimingEffect, schedule: String, zone: String, catchUp: Boolean?, grace: String): CronTimingDraft? {
            val time = schedule.trim()
            val timezone = zone.trim().ifEmpty { null }
            val minutes = grace.trim().let { if (it.isEmpty()) null else it.toIntOrNull() ?: return null }
            if ((timezone != null && !CronTimingProtocol.zone(timezone)) || (minutes != null && minutes !in 0..1440)) return null
            if (time != before.display) {
                if (time.isEmpty() || time.length > 512 || time.any { it.code !in 32..126 }) return null
                when (val value = parseHermesCronSchedule(time)) {
                    is HermesCronSchedule.Absolute, is HermesCronSchedule.Calendar -> Unit
                    is HermesCronSchedule.Elapsed -> if (!value.recurring || value.minutes <= 0) return null
                    else -> return null
                }
            }
            val changes = buildJsonObject {
                if (before.display != time) put("schedule", time)
                if (before.timezone != timezone) put("timezone", timezone)
                if (before.catchUp != catchUp) put("catch_up", catchUp)
                if (before.grace != minutes) put("grace_minutes", minutes)
            }
            return changes.takeIf { it.isNotEmpty() }?.let(::CronTimingDraft)
        }
    }
}

internal class CronTimingBaseline(
    val owner: ManagementTarget, val homeId: String, val jobId: String, val version: String, val effect: CronTimingEffect
)

internal class CronTimingReview(
    val baseline: CronTimingBaseline, val draft: CronTimingDraft, val digest: String,
    val profileTimezone: String?, val after: CronTimingEffect
) {
    fun sameReview(other: CronTimingReview): Boolean =
        digest == other.digest && profileTimezone == other.profileTimezone && after == other.after
}

internal data class CronTimingReceiptProof(val version: String, val review: String, val effect: String) {
    fun wire() = buildJsonObject { put("version", version); put("review", review); put("effect", effect) }.toString()
}

internal object CronTimingProtocol {
    const val Action = "CronTimingEdit"
    const val Prepare = "signal_grove.cron.edit_prepare_v1"
    const val Review = "signal_grove.cron.edit_review_v1"
    const val Apply = "signal_grove.cron.edit_apply_v1"
    const val Observe = "signal_grove.cron.edit_observe_v1"
    private val digest = Regex("[a-f0-9]{64}")
    private val requestId = Regex("[a-f0-9]{32}")
    private val jobId = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")
    private val timezone = Regex("[A-Za-z0-9_+.-]+(?:/[A-Za-z0-9_+.-]+)*")
    private val errors = setOf("INVALID","DENIED","CHANGED","EXPIRED","NOT_FOUND","BUSY","UNAVAILABLE","UNSUPPORTED",
        "TOO_LARGE","DUPLICATE","IDENTITY_FAILURE")

    fun zone(value: String) = value.length in 1..128 && timezone.matches(value) &&
        value.split('/').none { it == ".." || it == "." }

    private fun selector(owner: ManagementTarget, job: String, more: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        require(CronExecutionHistory.supportsProfile(owner.profile) && jobId.matches(job) && job != "unknown")
        put("expected_profile", owner.profile); put("job_id", job); more()
    }

    fun prepare(owner: ManagementTarget, job: String) = selector(owner, job)

    fun review(base: CronTimingBaseline, draft: CronTimingDraft) = selector(base.owner, base.jobId) {
        put("expected_version", base.version); put("changes", draft.changes)
    }

    fun apply(reviewed: CronTimingReview, id: String) = JsonObject(review(reviewed.baseline, reviewed.draft) +
        mapOf("request_id" to JsonPrimitive(id.also { require(requestId.matches(it)) }),
            "review_digest" to JsonPrimitive(reviewed.digest)))

    fun observe(owner: ManagementTarget, job: String, id: String) = selector(owner, job) {
        require(requestId.matches(id)); put("request_id", id)
    }

    fun parseBaseline(wire: JsonObject, owner: ManagementTarget, home: String, job: String): CronTimingBaseline = checked {
        val body = payload(wire, owner, job, setOf("profile","job_id","version","timing","repeat"))
        CronTimingBaseline(owner, home, job, body.hash("version"), effect(body))
    }

    fun parseReview(wire: JsonObject, base: CronTimingBaseline, draft: CronTimingDraft): CronTimingReview = checked {
        val body = payload(wire, base.owner, base.jobId,
            setOf("profile","job_id","version","profile_timezone","before","after","review_digest"))
        require(body.hash("version") == base.version)
        require(effect(body.objectAt("before", setOf("timing","repeat"))) == base.effect)
        val after = effect(body.objectAt("after", setOf("timing","repeat")))
        require(draft.matches(base.effect, after))
        val profileZone = body.optional("profile_timezone")?.let { it.string().also { value -> require(zone(value)) } }
        CronTimingReview(base, draft, body.hash("review_digest"), profileZone, after)
    }

    fun parseReceipt(wire: JsonObject, owner: ManagementTarget, job: String, id: String, proof: CronTimingReceiptProof) = checked {
        val body = payload(wire, owner, job,
            setOf("profile","job_id","request_id","reviewed_version","review_digest","timing","repeat"))
        require(body["request_id"] == JsonPrimitive(id) && body.hash("reviewed_version") == proof.version &&
            body.hash("review_digest") == proof.review && effect(body).fingerprint() == proof.effect)
    }

    fun proof(summary: String): CronTimingReceiptProof = checked {
        require(summary.length in 1..256)
        val saved = Json.parseToJsonElement(summary).jsonObject
        require(saved.keys == setOf("version","review","effect"))
        CronTimingReceiptProof(saved.hash("version"),saved.hash("review"),saved.hash("effect"))
    }

    private fun payload(wire: JsonObject, target: ManagementTarget, job: String, keys: Set<String>): JsonObject {
        require(wire["schema"] == JsonPrimitive(1) && wire.toString().length <= 16384)
        if (wire["state"] == JsonPrimitive("error")) {
            require(wire.keys == setOf("schema","state","code","request_submitted"))
            val code = wire.getValue("code").string()
            require(code in errors)
            throw CronTimingRefused(code, wire.getValue("request_submitted").boolean())
        }
        require(wire.keys == setOf("schema","state","payload") && wire["state"] == JsonPrimitive("ok"))
        return wire.objectAt("payload", keys).also {
            require(it["profile"] == JsonPrimitive(target.profile) && it["job_id"] == JsonPrimitive(job))
        }
    }

    private fun effect(body: JsonObject): CronTimingEffect {
        val timing = body.objectAt("timing",setOf("schedule","timezone","catch_up","grace_minutes"))
        val schedule = timing.getValue("schedule").jsonObject
        val display = when (schedule["kind"]) {
            JsonPrimitive("cron") -> {
                require(schedule.keys == setOf("kind","expr"))
                schedule.getValue("expr").string().also {
                    require(it.length in 1..512 && it.all { char -> char.code in 32..126 })
                }
            }
            JsonPrimitive("interval") -> {
                require(schedule.keys == setOf("kind","minutes"))
                "every " + schedule.getValue("minutes").number().also { require(it > 0) } + "m"
            }
            JsonPrimitive("once") -> {
                require(schedule.keys == setOf("kind","run_at"))
                schedule.getValue("run_at").string().also { require(it.length in 1..64 && OffsetDateTime.parse(it).year in 1..9999) }
            }
            else -> error("Unknown cadence.")
        }
        val tz = timing.optional("timezone")?.string()?.also { require(zone(it)) }
        val catchUp = timing.optional("catch_up")?.boolean()
        val grace = timing.optional("grace_minutes")?.number()?.also { require(it in 0..1440) }?.toInt()
        val repeat = body.optional("repeat")?.jsonObject?.let {
            require(it.keys == setOf("times","completed"))
            CronTimingRepeat(it.optional("times")?.number()?.also { times -> require(times > 0) },
                it.getValue("completed").number().also { count -> require(count >= 0) })
        }
        return CronTimingEffect(schedule, display, tz, catchUp, grace, repeat)
    }

    private fun JsonObject.objectAt(key: String, keys: Set<String>) =
        getValue(key).jsonObject.also { require(it.keys == keys) }
    private fun JsonObject.optional(key: String) = getValue(key).takeUnless { it == JsonNull }
    private fun JsonObject.hash(key: String) = getValue(key).string().also { require(digest.matches(it)) }
    private fun JsonElement.string() = jsonPrimitive.also { require(it.isString) }.content
    private fun JsonElement.number() = jsonPrimitive.also { require(!it.isString) }.long
    private fun JsonElement.boolean() = jsonPrimitive.also { require(!it.isString) }.boolean
    private inline fun <T> checked(parse: () -> T): T = try { parse() }
    catch (refused: CronTimingRefused) { throw refused }
    catch (_: Exception) { throw CronTimingInvalid() }
}
