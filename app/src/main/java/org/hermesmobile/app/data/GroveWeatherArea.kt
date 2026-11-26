package org.hermesmobile.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.round

@ConsistentCopyVisibility
internal data class GroveWeatherArea private constructor(val name: String, val latitude: Double, val longitude: Double) {
    fun encode(): String = buildJsonObject {
        put("name", name)
        put("latitude", latitude)
        put("longitude", longitude)
    }.toString()

    companion object {
        fun create(name: String, latitude: Double, longitude: Double): GroveWeatherArea? {
            val title = name.trim().take(120)
            if (title.isBlank() || title.any { it.isISOControl() } || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return GroveWeatherArea(title, round(latitude * 4) / 4, round(longitude * 4) / 4)
        }

        fun decode(raw: String?): GroveWeatherArea? {
            if (raw == null || raw.length > 1024) return null
            val row = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
            val name = (row["name"] as? JsonPrimitive)?.contentOrNull ?: return null
            val latitude = (row["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
            val longitude = (row["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
            return create(name, latitude, longitude)
        }
    }
}
