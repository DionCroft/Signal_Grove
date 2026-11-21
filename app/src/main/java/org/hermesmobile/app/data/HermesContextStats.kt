package org.hermesmobile.app.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.hermesmobile.app.network.GatewayProtocolException

/** Numeric projection of the authority's session.stats response. */
internal data class HermesContextStats(
    val effectiveWindow: Long?,
    val configuredWindow: Long?,
    val contextTokens: Long?,
    val lastPromptTokens: Long?,
    val compressionCount: Long
) {
    companion object {
        fun parse(result: JsonObject): HermesContextStats {
            fun count(key: String, optional: Boolean = false): Long? {
                val value = result[key]
                if (optional && (value == null || value == JsonNull)) return null
                return (value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
                    ?.takeIf { it >= 0 } ?: throw GatewayProtocolException()
            }

            val window = count("context_length", optional = true)?.takeIf { it > 0 }
            val configured = count("config_context_length", optional = true)?.takeIf { it > 0 }
            val used = count("context_tokens")
            if (window != null && used != null && used > window) throw GatewayProtocolException()
            return HermesContextStats(
                window, configured,
                used?.takeIf { it > 0 || window != null },
                count("last_prompt_tokens")?.takeIf { it > 0 },
                checkNotNull(count("compression_count"))
            )
        }
    }
}
