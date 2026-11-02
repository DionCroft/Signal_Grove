package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ImageRef(val hash:String="",val mime:String="image/jpeg",val width:Int=0,val height:Int=0,val url:String="",val unavailable:Boolean=false) {
    fun json()=buildJsonObject {put("hash",hash);put("mime",mime);put("width",width);put("height",height);put("url",url);put("unavailable",unavailable)}
}
data class ImageParts(val text:String,val sources:List<String>)
object ImageContent {
    const val MAX_IMAGES=3
    const val MAX_HISTORY_IMAGES=8
    const val MAX_SOURCE_BYTES=8_000_000
    const val MAX_IMAGE_BYTES=1_500_000
    fun remoteUrl(value:String):String? {
        if(value.length>8192) return null
        val url=value.toHttpUrlOrNull() ?: return null
        return url.toString().takeIf {url.username.isBlank() && url.password.isBlank() && url.fragment==null}
    }
    fun parse(content:JsonElement?):ImageParts=when(content) {
        is JsonPrimitive -> ImageParts(content.contentOrNull.orEmpty(),emptyList())
        is JsonArray -> {
            val texts=mutableListOf<String>();val images=mutableListOf<String>()
            for(part in content) {
                val obj=part as? JsonObject ?: continue
                when(obj.text("type")) {
                    "text","input_text","output_text" -> texts+=obj.text("text")
                    "image_url","input_image" -> {
                        val raw=obj["image_url"];val value=if(raw is JsonObject) raw.text("url") else (raw as? JsonPrimitive)?.contentOrNull.orEmpty()
                        require(value.length<=11_000_000) {"An inline history image exceeds the mobile limit."}
                        images+=value
                        require(images.size<=MAX_HISTORY_IMAGES) {"Too many images in one history message."}
                    }
                }
            }
            ImageParts(texts.joinToString("\n"),images)
        }
        else -> ImageParts("",emptyList())
    }
    fun refs(value:String):List<ImageRef> {
        if(value.isBlank()) return emptyList()
        require(value.toByteArray(Charsets.UTF_8).size<=100_000)
        val rows=Json.parseToJsonElement(value).jsonArray;require(rows.size<=MAX_HISTORY_IMAGES)
        return rows.map {entry -> val j=entry.jsonObject
            val hash=j.text("hash");val url=j.text("url");val missing=(j["unavailable"] as? JsonPrimitive)?.booleanOrNull==true
            require(hash.isBlank() || hash.matches(Regex("[a-f0-9]{64}")))
            require(url.isBlank() || remoteUrl(url)!=null)
            require(hash.isNotBlank() || url.isNotBlank() || missing)
            val mime=j.text("mime");require(mime in setOf("image/jpeg","image/png"))
            ImageRef(hash,mime,(j["width"] as? JsonPrimitive)?.intOrNull ?: 0,(j["height"] as? JsonPrimitive)?.intOrNull ?: 0,url,missing)
        }
    }
    fun encode(refs:List<ImageRef>):String {
        val value=JsonArray(refs.map {it.json()}).toString();this.refs(value);return value
    }
    fun requestInput(request:TurnRequest):JsonElement {
        val raw=request.body["input"]
        return when(request.transport) {
            "runs" -> if(raw is JsonArray) raw.lastOrNull()?.jsonObject?.get("content") ?: JsonPrimitive("") else raw ?: JsonPrimitive("")
            "session","demo" -> raw ?: JsonPrimitive("")
            else -> request.body["messages"]?.jsonArray?.lastOrNull {it.jsonObject.text("role")=="user"}?.jsonObject?.get("content") ?: JsonPrimitive("")
        }
    }
    fun parts(text:String,refs:List<ImageRef>,bytes:(ImageRef)->ByteArray):JsonElement {
        if(refs.isEmpty()) return JsonPrimitive(text)
        require(refs.none {it.unavailable}) {"An image in this history is unavailable. Start a new conversation to avoid silently losing its context."}
        return buildJsonArray {
            if(text.isNotBlank()) add(buildJsonObject {put("type","text");put("text",text)})
            refs.forEach {ref ->
                val url=if(ref.hash.isNotBlank()) {
                    val image=bytes(ref);require(image.size in 1..MAX_IMAGE_BYTES)
                    "data:${ref.mime};base64,"+java.util.Base64.getEncoder().encodeToString(image)
                } else remoteUrl(ref.url) ?: error("Invalid image reference.")
                add(buildJsonObject {put("type","image_url");put("image_url",buildJsonObject {put("url",url)})})
            }
        }
    }
}
