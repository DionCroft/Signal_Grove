package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Collections
import java.util.Locale

class CustomEndpointUnavailable:Exception()
class CustomEndpointChanged:Exception()
class CustomEndpointDuplicate:Exception()
class CustomEndpointUncertain:Exception()
class CustomEndpointReadbackPending:Exception()
enum class CustomEndpointSource {Provider,Legacy,Direct,Other}
enum class CustomEndpointKey {Keep,Fresh,Clear}
enum class CustomEndpointAction(val receiptAction:String) {Save("CustomEndpointSave"),Activate("CustomEndpointActivate"),Remove("CustomEndpointRemove")}
class CustomEndpointRow internal constructor(val id:String,val name:String,val baseUrl:String,val model:String,models:List<String>,val apiMode:String,val contextLength:Long?,val discoverModels:Boolean,val hasKey:Boolean,val current:Boolean,val source:CustomEndpointSource,private val structure:JsonObject) {
    val models:List<String> = Collections.unmodifiableList(models.toList())
    fun same(other:CustomEndpointRow)=structure==other.structure
    override fun toString()="CustomEndpointRow(redacted)"
}
class CustomEndpointRoute(val provider:String,val model:String,val baseUrl:String) {
    fun same(other:CustomEndpointRoute)=provider==other.provider && model==other.model && baseUrl==other.baseUrl
    override fun toString()="CustomEndpointRoute(redacted)"
}
/** Raw provider/legacy fields are retained only in the open view, never in a receipt or request body. */
class CustomEndpointSnapshot private constructor(val owner:ManagementTarget,val homeId:String,rows:List<CustomEndpointRow>,val current:CustomEndpointRoute,private val fields:JsonObject,private val reasoning:CustomEndpointReasoning) {
    val rows:List<CustomEndpointRow> = Collections.unmodifiableList(rows.toList())
    private val providers get()=fields["providers"] as? JsonObject ?: JsonObject(emptyMap())
    private val legacy get()=(fields["custom_providers"] as? JsonArray).orEmpty()
    private val legacyNamesKnown get()=legacy.all {it !is JsonObject || it["name"]==null || it["name"]==JsonNull || it["name"] is JsonPrimitive && (it["name"] as JsonPrimitive).isString}
    private fun legacyIds()=legacy.filterIsInstance<JsonObject>().map {CustomEndpointPolicy.slug((it["name"] as? JsonPrimitive)?.contentOrNull.orEmpty())}
    val canCreate get()=legacyNamesKnown
    fun freshId(id:String):Boolean=canCreate && rows.none {it.id.trim()==id} && providers.keys.none {it.trim()==id} && id !in legacyIds()
    fun canEdit(row:CustomEndpointRow):Boolean {
        if(rows.none {it===row} || !CustomEndpointPolicy.storedId(row.id)) return false
        return when(row.source) {
            CustomEndpointSource.Provider->providers[row.id] is JsonObject
            CustomEndpointSource.Legacy->legacyNamesKnown && providers.keys.none {it.trim()==row.id} && legacyIds().count {it==row.id}==1 && CustomEndpointPolicy.slug(row.id)==row.id
            else->false
        }
    }
    fun canAddress(row:CustomEndpointRow)=canEdit(row) && CustomEndpointPolicy.existingId(row.id)
    fun sharesGeneratedKey(id:String):Boolean {
        val slot=CustomEndpointPolicy.keySlot(id)
        return (providers.keys+legacyIds()+rows.map {it.id}).any {it!=id && CustomEndpointPolicy.keySlot(it)==slot}
    }
    fun same(other:CustomEndpointSnapshot,includeReasoning:Boolean=false)=owner==other.owner && homeId==other.homeId && fields==other.fields && (!includeReasoning || reasoning.same(other.reasoning)) && current.same(other.current) && rows.size==other.rows.size && rows.zip(other.rows).all {(a,b)->a.same(b)}
    private fun storedModels(id:String):JsonElement? {
        (providers[id] as? JsonObject)?.let {return it["models"]}
        val entry=legacy.filterIsInstance<JsonObject>().singleOrNull {CustomEndpointPolicy.slug((it["name"] as? JsonPrimitive)?.contentOrNull.orEmpty())==id} ?: return null
        val raw=entry["models"]
        if(raw is JsonObject) return JsonObject(raw.filterKeys {it !in setOf("__discovered_model_catalog__","__explicit_model_allowlist__")})
        if(raw !is JsonArray) return raw
        val normalized=linkedMapOf<String,JsonElement>()
        for(value in raw) {
            fun identifier(value:JsonElement?)=(value as? JsonPrimitive)?.takeIf {it.isString && it.content.isNotBlank()}?.content?.trim()
            val model=if(value is JsonObject) identifier(value["id"]) ?: identifier(value["name"]) else identifier(value)
            if(model==null || normalized.containsKey(model)) return raw // The host would skip/drop an ambiguous row.
            normalized[model]=if(value is JsonObject) JsonObject(value.filterKeys {it !in setOf("id","name")}) else JsonObject(emptyMap())
        }
        return JsonObject(normalized)
    }
    fun canMergeModels(draft:CustomEndpointDraft):Boolean {
        val raw=storedModels(draft.id)
        if(raw!=null && raw !is JsonObject) return false
        val models=raw as? JsonObject ?: JsonObject(emptyMap())
        val posted=draft.models+draft.modelDetails.map {it.id}+draft.model
        return posted.none {models[it]?.let {value->value !is JsonObject}==true}
    }
    fun canApplyMetadata(draft:CustomEndpointDraft):Boolean {
        if(draft.modelDetails.isEmpty()) return true
        if(draft.aliasEffect.globalEffort!=null && (!reasoning.writable || priorReasoning(draft)?.toString()?.length?.let {it>8192}==true)) return false
        // New imports must not discard unsupported stored values through host normalization.
        if(!canMergeModels(draft)) return false
        val models=storedModels(draft.id) as? JsonObject ?: JsonObject(emptyMap())
        val canonical=draft.aliasEffect.effectiveModel
        return draft.contextLength==null || models[canonical].let {it==null || it is JsonObject}
    }
    fun priorReasoning(draft:CustomEndpointDraft):JsonElement?=reasoning.value(draft.aliasEffect.effectiveModel)
    fun metadataObserved(draft:CustomEndpointDraft):Boolean {
        if(draft.modelDetails.isEmpty()) return true
        val models=storedModels(draft.id) as? JsonObject ?: return false
        if(draft.modelDetails.any {detail->
            val row=models[detail.id] as? JsonObject
            row==null || detail.canonicalModel?.let {row["canonical_model"]!=JsonPrimitive(it)}==true || detail.reasoningEffort?.let {row["reasoning_effort"]!=JsonPrimitive(it)}==true
        }) return false
        if(draft.contextLength!=null && (models[draft.aliasEffect.effectiveModel] as? JsonObject)?.get("context_length")!=JsonPrimitive(draft.contextLength)) return false
        return draft.aliasEffect.globalEffort?.let {reasoning.value(draft.aliasEffect.effectiveModel)==JsonPrimitive(it)} ?: true
    }
    override fun toString()="CustomEndpointSnapshot(redacted)"
    companion object {
        fun parse(metadata:JsonObject,config:JsonObject,owner:ManagementTarget,homeId:String):CustomEndpointSnapshot=try {
            val fields=JsonObject(listOf("providers","custom_providers").mapNotNull {key->config[key]?.let {key to Json.parseToJsonElement(it.toString())}}.toMap())
            require(fields.toString().length<=500_000)
            val reasoning=CustomEndpointReasoning.parse(config["agent"])
            require(fields["providers"].let {it==null || it==JsonNull || it is JsonObject});require(fields["custom_providers"].let {it==null || it==JsonNull || it is JsonArray && it.size<=1000})
            val data=metadata["endpoints"] as? JsonArray ?: error("No endpoint inventory.");require(data.size<=200);var total=0
            val rows=data.map {value->
                val j=value.jsonObject
                val id=CustomEndpointPolicy.text(j,"id",256,true);val name=CustomEndpointPolicy.text(j,"name",512);val url=CustomEndpointPolicy.text(j,"base_url",8192);val model=CustomEndpointPolicy.text(j,"model",2048)
                val rawModels=j["models"] as? JsonArray ?: error("No endpoint models.");require(rawModels.size<=2048);total+=rawModels.size;require(total<=8192)
                val models=rawModels.map {require(it is JsonPrimitive && it.isString);it.content.also {v->require(CustomEndpointPolicy.field(v,2048,true))}}
                val mode=CustomEndpointPolicy.text(j,"api_mode",128)
                val context=j["context_length"].let {if(it==null || it==JsonNull) null else {require(it is JsonPrimitive && !it.isString);it.longOrNull?.also {n->require(n>=0)} ?: error("Invalid context limit.")}}
                val discover=CustomEndpointPolicy.flag(j,"discover_models");val key=CustomEndpointPolicy.flag(j,"has_api_key");val current=CustomEndpointPolicy.flag(j,"is_current")
                val source=when(CustomEndpointPolicy.text(j,"source",128,true)) {"providers"->CustomEndpointSource.Provider;"custom_providers"->CustomEndpointSource.Legacy;"direct-config"->CustomEndpointSource.Direct;else->CustomEndpointSource.Other}
                val shape=buildJsonObject {put("id",id);put("name",name);put("base_url",url);put("model",model);put("models",JsonArray(models.map(::JsonPrimitive)));put("api_mode",mode);put("context_length",context?.let(::JsonPrimitive) ?: JsonNull);put("discover_models",discover);put("has_api_key",key);put("is_current",current);put("source",CustomEndpointPolicy.text(j,"source",128,true))}
                CustomEndpointRow(id,name,url,model,models,mode,context,discover,key,current,source,shape)
            }
            require(rows.map {it.id}.distinct().size==rows.size)
            val current=metadata["current"] as? JsonObject ?: error("No current route.")
            CustomEndpointSnapshot(owner,homeId,rows,CustomEndpointRoute(CustomEndpointPolicy.text(current,"provider",256),CustomEndpointPolicy.text(current,"model",2048),CustomEndpointPolicy.text(current,"base_url",8192)),fields,reasoning)
        } catch(e:Exception) {throw CustomEndpointUnavailable()}
    }
}
class CustomEndpointDraft(id:String,name:String,baseUrl:String,model:String,val key:CustomEndpointKey=CustomEndpointKey.Keep,secret:String="",val apiMode:String?=null,val contextLength:Long?=null,val discoverModels:Boolean=true,val makeDefault:Boolean=false,models:List<String> = emptyList(),modelDetails:List<CustomEndpointModelDetail> = emptyList()) {
    val id=id.trim();val name=name.trim();val baseUrl=baseUrl.trim().trimEnd('/');val model=model.trim();val secret=secret.trim()
    val models:List<String> = Collections.unmodifiableList(models.map {it.trim()}.toList())
    val modelDetails:List<CustomEndpointModelDetail> = Collections.unmodifiableList(modelDetails.toList())
    val aliasEffect get()=CustomEndpointAliasPolicy.effect(model,this.modelDetails)
    init {
        require(CustomEndpointPolicy.storedId(this.id) && CustomEndpointPolicy.field(this.name,512,true) && CustomEndpointPolicy.route(this.baseUrl,this.model))
        require(apiMode==null || apiMode in HostFallbackPolicy.apiModes);require(contextLength==null || contextLength in 1..2_000_000_000L)
        require(this.models.size<=256 && this.models.all {CustomEndpointPolicy.field(it,2048,true)} && this.models.distinct().size==this.models.size)
        require(this.modelDetails.size<=256 && this.modelDetails.map {it.id}.distinct().size==this.modelDetails.size)
        require(this.modelDetails.all {it.id==this.model || it.id in this.models})
        require(if(key==CustomEndpointKey.Fresh) CustomEndpointPolicy.freshKey(this.secret) else this.secret.isEmpty())
    }
    fun body()=buildJsonObject {
        put("id",id);put("name",name);put("base_url",baseUrl);put("model",model);put("discover_models",discoverModels);put("make_default",makeDefault)
        when(key) {CustomEndpointKey.Fresh->put("api_key",secret);CustomEndpointKey.Clear->put("api_key","");else->Unit}
        apiMode?.let {put("api_mode",it)};contextLength?.let {put("context_length",it)};if(models.isNotEmpty()) put("models",JsonArray(models.map(::JsonPrimitive)));if(modelDetails.isNotEmpty()) put("model_details",JsonArray(modelDetails.map {it.body()}))
    }
    override fun toString()="CustomEndpointDraft(redacted)"
}
class CustomEndpointAck(val summary:JsonObject,val id:String?=null,val provider:String?=null,val model:String?=null) {override fun toString()="CustomEndpointAck(redacted)"}
class CustomEndpointRequest private constructor(val expected:CustomEndpointSnapshot,val action:CustomEndpointAction,val row:CustomEndpointRow?,val draft:CustomEndpointDraft?) {
    val id get()=draft?.id ?: row!!.id
    init {
        if(row==null) {require(action==CustomEndpointAction.Save && draft!=null && CustomEndpointPolicy.newId(draft.id));if(!expected.canCreate) throw CustomEndpointUnavailable();if(!expected.freshId(draft.id)) throw CustomEndpointDuplicate()}
        else {require(if(action==CustomEndpointAction.Save) expected.canEdit(row) else expected.canAddress(row));if(action==CustomEndpointAction.Save) require(draft!=null && draft.id==row.id) else require(draft==null);if(action==CustomEndpointAction.Activate) require(CustomEndpointPolicy.route(row.baseUrl,row.model))}
        require(draft==null || draft.body().toString().length<=600_000 && expected.canApplyMetadata(draft))
    }
    fun summary()=buildJsonObject {put("home_id",expected.homeId);put("operation",action.name);put("new_endpoint",row==null);put("known_shared_key_slot",expected.sharesGeneratedKey(id));draft?.let {put("credential_choice",it.key.name);put("mode_choice",if(it.apiMode==null) "keep" else if(it.apiMode.isEmpty()) "auto" else "explicit");put("context_limit_supplied",it.contextLength!=null);put("discover_models",it.discoverModels);put("make_default",it.makeDefault);put("additional_model_count",it.models.size);put("model_metadata_count",it.modelDetails.size);put("selected_alias_redirect",it.aliasEffect.redirects);put("global_reasoning_write",it.aliasEffect.globalEffort!=null)}}
    fun acknowledge(j:JsonObject):CustomEndpointAck {
        require(CustomEndpointPolicy.flag(j,"ok"))
        val id=if(action==CustomEndpointAction.Save) CustomEndpointPolicy.text(j,"id",256,true) else null
        val provider=if(action==CustomEndpointAction.Activate) CustomEndpointPolicy.text(j,"provider",256,true) else null
        val model=if(action==CustomEndpointAction.Activate) CustomEndpointPolicy.text(j,"model",2048,true) else null
        val summary=buildJsonObject {summary().forEach {(k,v)->put(k,v)};put("acknowledged",true);if(id!=null) put("reported_id_matches_requested",id==this@CustomEndpointRequest.id);if(provider!=null) put("reported_route_matches_requested",provider==this@CustomEndpointRequest.id && model==row?.model)}
        return CustomEndpointAck(summary,id,provider,model)
    }
    override fun toString()="CustomEndpointRequest(redacted)"
    companion object {
        fun save(expected:CustomEndpointSnapshot,draft:CustomEndpointDraft,row:CustomEndpointRow?=null)=CustomEndpointRequest(expected,CustomEndpointAction.Save,row,draft)
        fun activate(expected:CustomEndpointSnapshot,row:CustomEndpointRow)=CustomEndpointRequest(expected,CustomEndpointAction.Activate,row,null)
        fun remove(expected:CustomEndpointSnapshot,row:CustomEndpointRow)=CustomEndpointRequest(expected,CustomEndpointAction.Remove,row,null)
    }
}
object CustomEndpointPolicy {
    fun field(value:String,max:Int,required:Boolean=false)=value.length<=max && value.none(Char::isISOControl) && (!required || value.isNotBlank())
    fun storedId(value:String)=field(value,256,true) && value==value.trim()
    fun existingId(value:String)=storedId(value) && value !in setOf(".","..") && value.none {it in "/\\?#%"}
    fun newId(value:String)=value.matches(Regex("[a-z0-9][a-z0-9_-]{0,127}")) && value==slug(value)
    fun slug(value:String)=value.trim().replace(Regex("[^A-Za-z0-9_-]+"),"-").trim('-','_').lowercase(Locale.ROOT).ifEmpty {"custom"}
    fun keySlot(id:String)="HERMES_CUSTOM_"+id.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"),"_").trim('_').let {if(it.isEmpty()) "API_KEY" else it+"_API_KEY"}
    fun route(url:String,model:String)=field(url,8192,true) && url.toHttpUrlOrNull()!=null && field(model,2048,true)
    fun freshKey(value:String)=HostEnvPolicy.value(value) && !value.all {it=='*'} && !Regex("\\$\\{[^}]+\\}").containsMatchIn(value)
    fun text(j:JsonObject,key:String,max:Int,required:Boolean=false):String {val v=j[key];require(v is JsonPrimitive && v.isString);return v.content.also {require(field(it,max,required))}}
    fun flag(j:JsonObject,key:String):Boolean {val v=j[key];require(v is JsonPrimitive && !v.isString);return v.booleanOrNull ?: error("Invalid endpoint flag.")}
}
