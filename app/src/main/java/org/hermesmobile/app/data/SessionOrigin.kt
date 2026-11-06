package org.hermesmobile.app.data

import kotlinx.serialization.json.*

class ReadOnlySessionException(message:String):IllegalArgumentException(message)

/** Inventory origins are retained in existing private metadata, without a database migration.
 * Older app versions imported only API-owned sessions; their absent origin keeps that legacy behavior.
 * Newly observed missing origins are explicitly unknown and cannot acquire write authority. */
object SessionOrigin {
    const val Api="api_server"
    const val Unknown="unknown"
    fun wire(row:JsonObject):String {
        val value=row["source"] ?: return Unknown
        if(value==JsonNull) return Unknown
        require(value is JsonPrimitive && value.isString) {"Invalid conversation source metadata."}
        return value.content.ifBlank {Unknown}.also {require(it.length<=128 && it.none(Char::isISOControl)) {"Invalid conversation source metadata."}}
    }
    fun metadata(raw:String):JsonObject=if(raw.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(raw).jsonObject
    fun source(c:Conversation):String=runCatching {metadata(c.detailsJson).let {if(it.containsKey("source")) wire(it) else ""}}.getOrDefault(Unknown)
    fun readOnly(c:Conversation):Boolean=source(c).let {it.isNotBlank() && it!=Api}
    fun requireWritable(c:Conversation,message:String="This conversation belongs to another Hermes interface. Its history is read-only in this API client.") {
        if(readOnly(c)) throw ReadOnlySessionException(message)
    }
    fun retain(raw:String,source:String):String=JsonObject(metadata(raw)+("source" to JsonPrimitive(wire(buildJsonObject {put("source",source)})))).toString()
    fun mergeDetails(previous:String,fresh:JsonObject):String {
        if(fresh.containsKey("source")) return retain(fresh.toString(),wire(fresh))
        val origin=metadata(previous)["source"] ?: return fresh.toString()
        return retain(fresh.toString(),wire(buildJsonObject {put("source",origin)}))
    }
}
