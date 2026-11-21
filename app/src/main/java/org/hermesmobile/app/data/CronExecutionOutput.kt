package org.hermesmobile.app.data

import kotlinx.serialization.json.*

internal class InvalidCronExecutionOutput : Exception()

enum class CronArchiveState(val wire: String) {
    Available("available"), Unlinked("unlinked"), Missing("missing"), Changed("changed")
}

/** A display-only preview for a single receipt. Its text never enters a chat or a saved receipt. */
class CronExecutionOutput private constructor(
    val status: CronExecutionStatus,
    val archive: CronArchiveState,
    val text: String?,
    val limited: Boolean
) {
    override fun toString() = "CronExecutionOutput(redacted)"

    companion object {
        internal const val Method = "signal_grove.cron.output_v1"
        private const val PreviewBytes = 16 * 1024
        private val fields = setOf("profile", "job_id", "execution_id", "status", "archive_state", "text", "limited")

        internal fun parse(reply: JsonObject, owner: ManagementTarget, jobId: String, executionId: String): CronExecutionOutput = try {
            val payload = CronExecutionHistory.scopedPayload(reply, owner, jobId)
            require(payload.keys == fields && payload["execution_id"] == JsonPrimitive(executionId))
            val status = CronExecutionStatus.entries.single { payload["status"] == JsonPrimitive(it.wire) }
            val archive = CronArchiveState.entries.single { payload["archive_state"] == JsonPrimitive(it.wire) }
            val limited = when (payload["limited"]) {
                JsonPrimitive(true) -> true
                JsonPrimitive(false) -> false
                else -> error("Invalid preview limit.")
            }
            val text = if (archive == CronArchiveState.Available) {
                val value = payload["text"] as? JsonPrimitive ?: error("Invalid preview text.")
                require(value.isString && value.content.length in 1..PreviewBytes)
                require(value.content.codePoints().noneMatch { it in 0xD800..0xDFFF })
                require(value.content.toByteArray(Charsets.UTF_8).size <= PreviewBytes)
                value.content.map { char ->
                    if (char.isISOControl() && char !in "\n\r\t") '\uFFFD' else char
                }.joinToString("")
            } else {
                require(payload["text"] == JsonNull && !limited)
                null
            }
            CronExecutionOutput(status, archive, text, limited)
        } catch (error: CronExecutionHistoryUnavailable) {
            throw error
        } catch (_: Exception) {
            throw InvalidCronExecutionOutput()
        }
    }
}
