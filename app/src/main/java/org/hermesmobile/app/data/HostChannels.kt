package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.net.URI
import java.util.Collections

class HostChannelsUnavailable:Exception()
class HostChannelsChanged:Exception()
class HostChannelsUncertain:Exception()
class HostChannelsReadbackPending:Exception()

data class HostChannelField(val key:String,val required:Boolean,val isSet:Boolean,val password:Boolean,val list:Boolean,val advanced:Boolean,val prompt:String,val description:String,val help:String,val savedList:String?) {
    override fun toString()="HostChannelField(redacted)"
}
class HostChannelRow(val id:String,val name:String,val description:String,val docsUrl:String,val enabled:Boolean,val configured:Boolean,val gatewayRunning:Boolean,val state:String,val errorPresent:Boolean,val relayDisabled:Boolean,val relayFronted:Boolean,val relayExclusive:Boolean,val ingressUrl:String,home:JsonObject?,fields:List<HostChannelField>) {
    val home:JsonObject?=home?.let {Json.parseToJsonElement(it.toString()).jsonObject}
    val fields:List<HostChannelField> = Collections.unmodifiableList(fields.toList())
    fun sameReview(other:HostChannelRow)=id==other.id && name==other.name && description==other.description && docsUrl==other.docsUrl && enabled==other.enabled && configured==other.configured && relayDisabled==other.relayDisabled && relayFronted==other.relayFronted && relayExclusive==other.relayExclusive && ingressUrl==other.ingressUrl && home==other.home && fields==other.fields
    override fun toString()="HostChannelRow(redacted)"
}
class HostChannelInventory(val owner:ManagementTarget,val homeId:String,rows:List<HostChannelRow>) {
    val rows:List<HostChannelRow> = Collections.unmodifiableList(rows.toList())
    override fun toString()="HostChannelInventory(redacted)"
    companion object {
        fun parse(raw:JsonObject,owner:ManagementTarget,homeId:String):HostChannelInventory=try {
            require(homeId.isNotBlank());val values=raw["platforms"] as? JsonArray ?: error("Missing platforms")
            require(values.size<=128);var fieldsCount=0
            val rows=values.map {value->
                val row=value.jsonObject;val id=ChannelDecode.text(row,"id",128,true)
                val fields=row["env_vars"] as? JsonArray ?: error("Missing declared fields")
                require(fields.size<=128);fieldsCount+=fields.size;require(fieldsCount<=4096)
                val parsed=fields.map {v->
                    val f=v.jsonObject;val password=ChannelDecode.flag(f,"is_password",true)!!;val list=ChannelDecode.flag(f,"is_list",true)!!
                    require(!password || !list)
                    HostChannelField(ChannelDecode.text(f,"key",256,true),ChannelDecode.flag(f,"required",true)!!,ChannelDecode.flag(f,"is_set",true)!!,password,list,ChannelDecode.flag(f,"advanced") ?: false,ChannelDecode.text(f,"prompt",512),ChannelDecode.text(f,"description",8192),ChannelDecode.text(f,"help",8192),if(list) ChannelDecode.optionalText(f,"value",16384) else null)
                }
                require(parsed.map {it.key}.distinct().size==parsed.size)
                val home=row["home_channel"]?.takeUnless {it is JsonNull}?.let {h->
                    val obj=h.jsonObject;buildJsonObject {for(key in listOf("platform","chat_id","name","thread_id","user_id","scope_id")) ChannelDecode.optionalText(obj,key,2048)?.let {put(key,it)}}
                }
                HostChannelRow(id,ChannelDecode.text(row,"name",512,true),ChannelDecode.text(row,"description",8192),ChannelDecode.text(row,"docs_url",8192),ChannelDecode.flag(row,"enabled",true)!!,ChannelDecode.flag(row,"configured",true)!!,ChannelDecode.flag(row,"gateway_running",true)!!,ChannelDecode.text(row,"state",128,true),!ChannelDecode.text(row,"error_code",512).isBlank() || !ChannelDecode.text(row,"error_message",16384).isBlank(),ChannelDecode.flag(row,"disabled_by_relay") ?: false,ChannelDecode.flag(row,"relay_fronted") ?: false,ChannelDecode.flag(row,"relay_exclusive") ?: false,ChannelDecode.text(row,"ingress_url",8192),home,parsed)
            }
            require(rows.map {it.id}.distinct().size==rows.size)
            require(rows.sumOf {r->r.fields.sumOf {it.savedList?.length ?: 0}+(r.home?.toString()?.length ?: 0)}<=500_000)
            HostChannelInventory(owner,homeId,rows)
        } catch(e:Exception) {throw HostChannelsUnavailable()}
    }
}
private object ChannelDecode {
    fun optionalText(raw:JsonObject,key:String,max:Int):String? {val v=raw[key];if(v==null || v is JsonNull) return null;require(v is JsonPrimitive && v.isString);return v.content.also {require(it.length<=max && '\u0000' !in it)}}
    fun text(raw:JsonObject,key:String,max:Int,required:Boolean=false):String=(optionalText(raw,key,max) ?: "").also {if(required) require(it.isNotBlank() && it.none(Char::isISOControl))}
    fun flag(raw:JsonObject,key:String,required:Boolean=false):Boolean? {val v=raw[key];if(v==null || v is JsonNull) {require(!required);return null};require(v==JsonPrimitive(true) || v==JsonPrimitive(false));return v.jsonPrimitive.boolean}
}
object HostChannelPolicy {
    fun id(value:String)=value.isNotBlank() && value==value.trim() && value.length<=128 && value !in setOf(".","..") && value.none {it.isISOControl() || it in "/\\?#"}
    fun value(value:String,password:Boolean)=value.isNotEmpty() && value.length<=16384 && value==value.trim() && value.none(Char::isISOControl) && (!password || value.all {it.code in 32..126}) && !value.startsWith("«redacted") && value!="***" && !Regex(".{4}\\.\\.\\..{4}").matches(value)
    fun browser(value:String)=runCatching {val uri=URI(value);value.length<=8192 && value.none(Char::isISOControl) && uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.rawUserInfo==null}.getOrDefault(false)
}
class HostChannelDraft(val enabled:Boolean?=null,replacements:Map<String,String> = emptyMap(),clears:List<String> = emptyList()) {
    val replacements:Map<String,String> = Collections.unmodifiableMap(replacements.mapValues {it.value.trim()}.toMap())
    val clears:List<String> = Collections.unmodifiableList(clears.toList())
    init {require(this.replacements.size<=128 && this.clears.size<=128 && this.clears.distinct().size==this.clears.size && this.replacements.keys.intersect(this.clears.toSet()).isEmpty());require(this.replacements.values.sumOf {it.length}<=200_000)}
    override fun toString()="HostChannelDraft(redacted)"
}
class HostChannelRequest(val expected:HostChannelInventory,val id:String,val draft:HostChannelDraft?=null) {
    val row=expected.rows.single {it.id==id}
    val action get()=if(draft==null) "ChannelPosture" else "ChannelSave"
    init {
        GatewayCronInventory.requireProfile(expected.owner.profile);require(HostChannelPolicy.id(id))
        draft?.let {d->
            require(d.enabled!=null && d.enabled!=row.enabled || d.replacements.isNotEmpty() || d.clears.isNotEmpty())
            for(key in d.replacements.keys+d.clears) require(HostEnvPolicy.writable(key) && row.fields.any {it.key==key})
            for((key,value) in d.replacements) require(HostChannelPolicy.value(value,row.fields.single {it.key==key}.password))
            require(body(expected.owner.profile).toString().length<=250_000)
        }
    }
    fun body(profile:String)=buildJsonObject {
        GatewayCronInventory.requireProfile(profile);require(profile==expected.owner.profile);val d=draft ?: error("Posture has no configuration body")
        put("profile",profile);d.enabled?.let {put("enabled",it)}
        if(d.replacements.isNotEmpty()) put("env",JsonObject(d.replacements.mapValues {JsonPrimitive(it.value)}))
        if(d.clears.isNotEmpty()) put("clear_env",JsonArray(d.clears.map(::JsonPrimitive)))
    }
    fun same(current:HostChannelInventory)=expected.owner==current.owner && expected.homeId==current.homeId && current.rows.singleOrNull {it.id==id}?.let(row::sameReview)==true
    fun summary()=buildJsonObject {put("platform",id);put("configuration_write",draft!=null);put("replacement_count",draft?.replacements?.size ?: 0);put("clear_count",draft?.clears?.size ?: 0);put("enabled_choice",draft?.enabled?.let(::JsonPrimitive) ?: JsonNull)}
    fun acknowledge(raw:JsonObject):HostChannelAck {
        if(draft!=null) {require(raw["ok"]==JsonPrimitive(true) && raw["platform"]==JsonPrimitive(id));return HostChannelAck(true,ChannelDecode.flag(raw,"hot_served",true)!!,null)}
        return HostChannelAck(ChannelDecode.flag(raw,"ok",true)!!,null,ChannelDecode.text(raw,"state",128,true))
    }
    fun observed(after:HostChannelInventory):Boolean {
        val d=draft ?: return true;val now=after.rows.singleOrNull {it.id==id} ?: return false
        return (d.enabled==null || now.enabled==d.enabled) && d.replacements.keys.all {key->now.fields.singleOrNull {it.key==key}?.isSet==true} && d.clears.all {key->now.fields.singleOrNull {it.key==key}?.isSet==false}
    }
    override fun toString()="HostChannelRequest(redacted)"
}
class HostChannelAck(val ok:Boolean,val hotServed:Boolean?,val state:String?) {
    fun summary()=buildJsonObject {put("acknowledged",true);put("host_ok",ok);hotServed?.let {put("hot_served",it)}}
    override fun toString()="HostChannelAck(redacted)"
}
class HostChannelPostureDetails(val message:String,val posture:String) {
    override fun toString()="HostChannelPostureDetails(redacted)"
    companion object {fun parse(raw:JsonObject):HostChannelPostureDetails?=runCatching {HostChannelPostureDetails(ChannelDecode.text(raw,"message",16384),ChannelDecode.text(raw,"auth_posture",128))}.getOrNull()}
}
class HostChannelPosture(val inventory:HostChannelInventory,val id:String,val ack:HostChannelAck,val details:HostChannelPostureDetails?,val receipt:ManagementOperation) {
    override fun toString()="HostChannelPosture(redacted)"
}
