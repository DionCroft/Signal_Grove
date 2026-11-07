package org.hermesmobile.app.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Protocol advertisement only. This never establishes actor grants, ownership or write authority. */
enum class GatewayAuthorityAdvertisement {
    Advertised, NotAdvertised, Unknown;

    internal companion object {
        fun fromReadyPayload(payload: JsonElement?): GatewayAuthorityAdvertisement {
            if (payload == null) return NotAdvertised
            if (payload !is JsonObject) return Unknown
            val flag = payload["session_authority"] ?: return NotAdvertised
            if (flag !is JsonPrimitive || flag.isString) return Unknown
            return when (flag.booleanOrNull) {
                true -> Advertised
                false -> NotAdvertised
                null -> Unknown
            }
        }
    }
}
