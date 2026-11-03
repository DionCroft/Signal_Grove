package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Collections

class HostMoaUnavailable:Exception()
class HostMoaChanged(val snapshot:HostMoaSnapshot?):Exception()
class HostMoaUncertain:Exception()
class HostMoaReadbackPending:Exception()
object HostMoaLimits {
    val efforts=setOf("none","minimal","low","medium","high","xhigh","max","ultra")
    fun id(v:String,max:Int=256)=v.isNotBlank() && v.length<=max && v==v.trim() && v.none(Char::isISOControl)
    fun cadence(v:String)=v in setOf("user_turn","per_iteration") || v.startsWith("every_n:") && v.removePrefix("every_n:").let {it.length in 1..128 && it.all {c->c in '0'..'9'} && it[0]!='0' && (it.length>1 || it[0]>='2')}
    fun number(v:String,positive:Boolean=false):Double?=v.toDoubleOrNull()?.takeIf {it.isFinite() && (!positive || it>0)}
}
data class HostMoaSlot(val route:HostModelRoute,val reasoning:String?=null,val enabled:Boolean=true) {
    init {require(HostMoaLimits.id(route.provider) && !route.provider.equals("moa",true) && HostMoaLimits.id(route.model,1024));require(reasoning==null || HostMoaLimits.id(reasoning,128))}
    fun json(reference:Boolean)=buildJsonObject {put("provider",route.provider);put("model",route.model);reasoning?.let {put("reasoning_effort",it)};if(reference) put("enabled",enabled)}
    override fun toString()="HostMoaSlot(redacted)"
}
class HostMoaPreset(references:List<HostMoaSlot>,val aggregator:HostMoaSlot,val enabled:Boolean=true,val referenceTemperature:Double?=null,val aggregatorTemperature:Double?=null,val referenceTimeout:Double?=null,val policy:String="loud",val fanout:String="user_turn") {
    val references:List<HostMoaSlot> = Collections.unmodifiableList(references.toList())
    init {require(references.size in 1..64 && aggregator.enabled);require(listOfNotNull(referenceTemperature,aggregatorTemperature,referenceTimeout).all {it.isFinite()});require(referenceTimeout==null || referenceTimeout>0);require(policy in setOf("loud","silent") && HostMoaLimits.cadence(fanout))}
    fun copy(references:List<HostMoaSlot> = this.references,aggregator:HostMoaSlot=this.aggregator,enabled:Boolean=this.enabled,referenceTemperature:Double?=this.referenceTemperature,aggregatorTemperature:Double?=this.aggregatorTemperature,referenceTimeout:Double?=this.referenceTimeout,policy:String=this.policy,fanout:String=this.fanout)=HostMoaPreset(references,aggregator,enabled,referenceTemperature,aggregatorTemperature,referenceTimeout,policy,fanout)
    fun json()=buildJsonObject {
        put("reference_models",buildJsonArray {references.forEach {add(it.json(true))}});put("aggregator",aggregator.json(false));put("enabled",enabled)
        put("reference_temperature",referenceTemperature?.let(::JsonPrimitive) ?: JsonNull);put("aggregator_temperature",aggregatorTemperature?.let(::JsonPrimitive) ?: JsonNull);put("reference_timeout",referenceTimeout?.let(::JsonPrimitive) ?: JsonNull);put("degraded_reference_policy",policy);put("fanout",fanout)
    }
    override fun equals(other:Any?)=other is HostMoaPreset && json()==other.json()
    override fun hashCode()=json().hashCode()
    override fun toString()="HostMoaPreset(redacted)"
}
class HostMoaConfig(presets:Map<String,HostMoaPreset>,val defaultPreset:String,val activePreset:String,val privacy:String) {
    val presets:Map<String,HostMoaPreset> = Collections.unmodifiableMap(LinkedHashMap(presets))
    val writable:Boolean get()=presets.values.all {p->(p.references+ p.aggregator).all {it.reasoning==null || it.reasoning in HostMoaLimits.efforts}}
    init {require(presets.size in 1..128 && presets.keys.all {HostMoaLimits.id(it)} && presets.values.sumOf {it.references.size}<=4096);require(defaultPreset in presets && (activePreset.isEmpty() || activePreset in presets));require(privacy in setOf("","display","full"))}
    fun copy(presets:Map<String,HostMoaPreset> = this.presets,defaultPreset:String=this.defaultPreset,activePreset:String=this.activePreset)=HostMoaConfig(presets,defaultPreset,activePreset,privacy)
    fun without(name:String):HostMoaConfig {require(presets.size>1 && name in presets);val next=presets-name;return copy(next,if(defaultPreset==name) next.keys.first() else defaultPreset,if(activePreset==name) "" else activePreset)}
    fun json(includePrivacy:Boolean=true)=buildJsonObject {put("default_preset",defaultPreset);put("active_preset",activePreset);put("presets",buildJsonObject {presets.toSortedMap().forEach {(key,value)->put(key,value.json())}});if(includePrivacy) put("privacy_filter",privacy)}
    fun fingerprint():String=MessageDigest.getInstance("SHA-256").digest(json().toString().toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
    fun routes():Set<HostModelRoute> = presets.values.flatMap {it.references+it.aggregator}.map {it.route}.toSet()
    override fun equals(other:Any?)=other is HostMoaConfig && defaultPreset==other.defaultPreset && activePreset==other.activePreset && privacy==other.privacy && presets==other.presets
    override fun hashCode()=json().hashCode()
    override fun toString()="HostMoaConfig(redacted)"
    companion object {
        private val flat=setOf("reference_models","aggregator","enabled","reference_temperature","aggregator_temperature","reference_timeout","degraded_reference_policy","fanout")
        fun parse(raw:JsonObject):HostMoaConfig=try {
            fun text(j:JsonObject,key:String,max:Int=256,blank:Boolean=false):String {val v=j[key];require(v is JsonPrimitive && v.isString);return v.content.also {require(it.length<=max && it.none(Char::isISOControl) && it==it.trim() && (blank || it.isNotBlank()))}}
            fun flag(j:JsonObject,key:String):Boolean {val v=j[key];require(v==JsonPrimitive(true) || v==JsonPrimitive(false));return v!!.jsonPrimitive.boolean}
            fun number(j:JsonObject,key:String,positive:Boolean=false):Double? {require(j.containsKey(key));val v=j[key];if(v is JsonNull) return null;require(v is JsonPrimitive && !v.isString);return v.double.also {require(it.isFinite() && (!positive || it>0))}}
            fun slot(j:JsonObject,reference:Boolean):HostMoaSlot {val effort=j["reasoning_effort"];require(effort==null || effort is JsonNull || effort is JsonPrimitive && effort.isString);return HostMoaSlot(HostModelRoute(text(j,"provider"),text(j,"model",1024)),(effort as? JsonPrimitive)?.contentOrNull,if(reference) flag(j,"enabled") else true)}
            fun preset(j:JsonObject):HostMoaPreset {val refs=j["reference_models"];require(refs is JsonArray && refs.size in 1..64);return HostMoaPreset(refs.map {slot(it.jsonObject,true)},slot(j["aggregator"]!!.jsonObject,false),flag(j,"enabled"),number(j,"reference_temperature"),number(j,"aggregator_temperature"),number(j,"reference_timeout",true),text(j,"degraded_reference_policy",32),text(j,"fanout",160))}
            val map=raw["presets"];require(map is JsonObject && map.size in 1..128)
            val config=HostMoaConfig(map.mapValues {preset(it.value.jsonObject)},text(raw,"default_preset"),text(raw,"active_preset",blank=true),text(raw,"privacy_filter",32,true))
            if(flat.any {raw.containsKey(it)}) {require(flat.all {raw.containsKey(it)});require(preset(raw)==config.presets[config.defaultPreset])}
            config
        } catch(_:Exception) {throw HostMoaUnavailable()}
    }
}
class HostMoaSnapshot(val catalog:HostModelInventory,val config:HostMoaConfig) {
    val owner get()=catalog.owner
    val homeId get()=catalog.homeId
    override fun toString()="HostMoaSnapshot(redacted)"
}
class HostMoaReply(val config:HostMoaConfig,val summary:JsonObject)
class HostMoaRequest(val expected:HostMoaSnapshot,val desired:HostMoaConfig) {
    private val selections:List<HostMainModelRequest>
    init {
        GatewayCronInventory.requireProfile(expected.owner.profile);require(expected.config.writable && desired.writable && desired.privacy==expected.config.privacy)
        // The pinned server deep-merges surviving aggregator dictionaries, so omitting effort cannot clear an existing value.
        require(desired.presets.all {(name,preset)->preset.aggregator.reasoning!=null || expected.config.presets[name]?.aggregator?.reasoning==null})
        selections=(desired.routes()-expected.config.routes()).map {HostMainModelRequest(expected.catalog,it.provider,it.model)}
        body(expected.owner.profile)
    }
    fun body(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);require(profile==expected.owner.profile);desired.json(false).forEach {(k,v)->put(k,v)};put("profile",profile)}.also {require(it.toString().toByteArray(Charsets.UTF_8).size<=2_000_000)}
    fun same(current:HostMoaSnapshot)=current.owner==expected.owner && current.homeId==expected.homeId && current.config==expected.config && current.catalog.main==expected.catalog.main && selections.all {it.same(current.catalog)}
    fun summary()=buildJsonObject {
        put("requested_sha256",desired.fingerprint());put("previous_sha256",expected.config.fingerprint());put("requested_default",desired.defaultPreset);put("requested_active",desired.activePreset);put("requested_presets",desired.presets.size)
        put("removed",buildJsonArray {(expected.config.presets.keys-desired.presets.keys).sorted().forEach {add(it)}});put("added",buildJsonArray {(desired.presets.keys-expected.config.presets.keys).sorted().forEach {add(it)}});put("changed",buildJsonArray {desired.presets.filter {(k,v)->expected.config.presets[k]!=v && k in expected.config.presets}.keys.sorted().forEach {add(it)}})
    }
    fun reply(raw:JsonObject):HostMoaReply {require(raw["ok"]==JsonPrimitive(true));val config=HostMoaConfig.parse(raw);return HostMoaReply(config,buildJsonObject {summary().forEach {(k,v)->put(k,v)};put("reported_sha256",config.fingerprint());put("reported_default",config.defaultPreset);put("reported_active",config.activePreset);put("reported_presets",config.presets.size);put("normalized",config!=desired)})}
    override fun toString()="HostMoaRequest(redacted)"
}
