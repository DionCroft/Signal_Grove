package org.hermesmobile.app.network

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

internal class HistoryReadChanged:Exception("Saved history changed during the read.")
internal class HistoryReadInvalid:Exception("Saved history returned an inconsistent page.")

/** Read-side projection only. Hidden rows still count toward the wire pagination offset. */
internal object HistoryReadProjection {
    class Page(val sessionId:String,val rows:List<JsonObject>)
    private fun invalid():Nothing=throw HistoryReadInvalid()
    fun page(j:JsonObject,previous:String,offset:Int,limit:Int):Page {
        val resolved=when(val raw=j["session_id"]) {
            null->previous // Older endpoints did not echo the resolved identity.
            is JsonPrimitive->raw.takeIf {it.isString}?.content?.takeIf {it.length in 1..512 && it !in setOf(".","..") && it.matches(Regex("[A-Za-z0-9_.:-]+"))} ?: invalid()
            else->invalid()
        }
        if(offset>0 && resolved!=previous) throw HistoryReadChanged()
        val data=j["data"] as? JsonArray ?: invalid()
        if(data.size>limit) invalid()
        val rows=data.map {it as? JsonObject ?: invalid()}
        j["pagination"]?.let {value->
            val metadata=value as? JsonObject ?: invalid()
            fun number(key:String)=(metadata[key] as? JsonPrimitive)?.takeIf {!it.isString}?.intOrNull ?: invalid()
            if(number("limit")!=limit || number("offset")!=offset || number("returned")!=rows.size || metadata["order"]!=JsonPrimitive("oldest")) invalid()
        }
        return Page(resolved,rows)
    }
    fun message(row:JsonObject,fallbackId:String):RemoteMessage? {
        val kind=when(val value=row["display_kind"]) {
            null,JsonNull->null
            is JsonPrimitive->value.takeIf {it.isString}?.content?.takeIf {it.length<=128 && it.none(Char::isISOControl)} ?: invalid()
            else->invalid()
        }
        if(kind=="hidden" || row.text("role") !in setOf("user","assistant")) return null
        val timestamp=(row["timestamp"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val content=ImageContent.parse(row["content"])
        return RemoteMessage(row.text("role"),content.text,(timestamp*1000).toLong(),row.text("id").ifBlank {fallbackId},content.sources)
    }
}
