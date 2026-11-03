package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class PluginSettingsUnavailable:Exception()
class PluginSettingsChanged:Exception()
class PluginSettingsUncertain:Exception()
class PluginSettingsReadbackPending:Exception()
class PluginSettingField private constructor(val key:String,val type:String,val label:String,val description:String,val required:Boolean,val value:JsonElement,val default:JsonElement,val choices:List<String>,val env:String,val hasValue:Boolean?,private val projection:JsonObject) {
    fun same(other:PluginSettingField)=projection==other.projection
    fun accepts(v:JsonElement)=bounded(v) && when(type) {
        "string"->v is JsonPrimitive && v.isString
        "enum"->v is JsonPrimitive && v.isString && v.content in choices
        "number"->v is JsonPrimitive && !v.isString && v.doubleOrNull?.isFinite()==true
        "boolean"->v==JsonPrimitive(true) || v==JsonPrimitive(false)
        "json"->v is JsonObject || v is JsonArray
        else->false
    }
    fun input(text:String):JsonElement?=runCatching {val v=if(type in setOf("string","enum")) JsonPrimitive(text) else Json.parseToJsonElement(text);v.takeIf(::accepts)}.getOrNull()
    override fun toString()="PluginSettingField(redacted)"
    companion object {
        fun relative(key:String)=key.length<=512 && key.substringBefore('.').lowercase() !in setOf("model","plugins","security","settings") && key.split('.').all {it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))}
        fun bounded(v:JsonElement):Boolean {
            var count=0
            fun walk(x:JsonElement,depth:Int):Boolean {
                if(depth>16 || ++count>2048) return false
                return when(x) {
                    is JsonObject->x.all {(k,value)->k.length<=8192 && '\u0000' !in k && walk(value,depth+1)}
                    is JsonArray->x.all {walk(it,depth+1)}
                    is JsonPrimitive->x.content.length<=8192 && '\u0000' !in x.content && (x.isString || x is JsonNull || x.booleanOrNull!=null || x.doubleOrNull?.isFinite()==true)
                }
            }
            return walk(v,0) && v.toString().length<=32768
        }
        fun parse(j:JsonObject):PluginSettingField {
            fun text(k:String,max:Int,required:Boolean=false):String {val v=j[k];require(v==null || v is JsonNull || v is JsonPrimitive && v.isString);return (v as? JsonPrimitive)?.contentOrNull.orEmpty().also {require(it.length<=max && '\u0000' !in it && (!required || it.isNotBlank()))}}
            val key=text("key",512,true);require(relative(key));val type=text("type",32,true);require(type in setOf("string","number","boolean","enum","secret","json"));val required=j["required"];require(required==JsonPrimitive(true) || required==JsonPrimitive(false))
            val label=text("label",512,true);val desc=text("description",8192);val secret=type=="secret"
            val value=if(secret) JsonNull else Json.parseToJsonElement((j["value"] ?: JsonNull).toString());val default=if(secret) JsonNull else Json.parseToJsonElement((j["default"] ?: JsonNull).toString())
            require(bounded(value) && bounded(default))
            if(secret) require((j["value"]==null || j["value"] is JsonNull) && (j["default"]==null || j["default"] is JsonNull))
            val choices=if(type=="enum") {val a=j["choices"] as? JsonArray ?: error("Missing choices");require(a.isNotEmpty() && a.size<=256);a.map {require(it is JsonPrimitive && it.isString && it.content.length<=8192 && '\u0000' !in it.content);it.content}.also {require(it.distinct().size==it.size)}} else emptyList()
            val env=if(secret) text("env",256,true).also {require(it.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,255}")))} else ""
            val has=if(secret) j["has_value"].also {require(it==JsonPrimitive(true) || it==JsonPrimitive(false))}?.jsonPrimitive?.booleanOrNull else null
            val projected=buildJsonObject {put("key",key);put("type",type);put("label",label);put("description",desc);put("required",required!!);put("value",value);put("default",default);put("choices",JsonArray(choices.map(::JsonPrimitive)));put("env",env);put("has_value",has?.let(::JsonPrimitive) ?: JsonNull)}
            return PluginSettingField(key,type,label,desc,required!!.jsonPrimitive.boolean,value,default,Collections.unmodifiableList(choices),env,has,projected)
        }
    }
}
class PluginSettingsSnapshot(val owner:ManagementTarget,val plugin:ManagedPlugin,val homeId:String,fields:List<PluginSettingField>) {
    val fields:List<PluginSettingField> = Collections.unmodifiableList(fields.toList())
    fun sameRow(row:ManagedPlugin)=PluginToggleRequest(plugin,plugin.status!="enabled").same(row)
    fun same(other:PluginSettingsSnapshot)=owner==other.owner && homeId==other.homeId && sameRow(other.plugin) && fields.size==other.fields.size && fields.all {f->other.fields.singleOrNull {it.key==f.key}?.let(f::same)==true}
    override fun toString()="PluginSettingsSnapshot(redacted)"
    companion object {
        fun parse(raw:JsonObject,target:ManagementTarget,home:PluginHome?,key:String):PluginSettingsSnapshot=try {
            val inventory=GatewayPluginInventory.parse(raw,target.profile,home);val row=inventory.plugins.single {it.key==key}
            require(PluginToggleRequest.supported(row) && !PluginToggleRequest.collision(row,inventory))
            val schema=raw["plugins"]!!.jsonArray.single {it.jsonObject["key"]==JsonPrimitive(key)}.jsonObject["settings_schema"] as? JsonArray ?: error("Settings unavailable")
            require(schema.size<=128);val fields=schema.map {PluginSettingField.parse(it.jsonObject)};require(fields.map {it.key}.distinct().size==fields.size)
            PluginSettingsSnapshot(target,row,home?.identity() ?: error("Profile home unavailable"),fields)
        } catch(e:Exception) {throw PluginSettingsUnavailable()}
    }
}
class PluginSettingsRequest(val expected:PluginSettingsSnapshot,val field:PluginSettingField,value:JsonElement) {
    val value:JsonElement=Json.parseToJsonElement(value.toString())
    init {require(expected.fields.any {it===field} && field.accepts(this.value))}
    fun params(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);require(profile==expected.owner.profile);put("profile",profile);put("action","settings");put("key",expected.plugin.key);put("values",buildJsonObject {put(field.key,value)})}
    fun summary()=buildJsonObject {put("setting_key",field.key);put("setting_type",field.type);put("requested_key_count",1);put("nested_readback_unavailable",field.key.contains('.'))}
    fun acknowledge(raw:JsonObject) {require(raw["ok"]==JsonPrimitive(true) && raw["name"]==JsonPrimitive(expected.plugin.key) && raw["written"]==JsonArray(listOf(JsonPrimitive(field.key))))}
    fun observed(after:PluginSettingsSnapshot)=!field.key.contains('.') && expected.owner==after.owner && expected.homeId==after.homeId && expected.sameRow(after.plugin) && after.fields.singleOrNull {it.key==field.key}?.let {it.type==field.type && it.value==value}==true
    override fun toString()="PluginSettingsRequest(redacted)"
}
