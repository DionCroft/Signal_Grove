package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.util.Collections

class CronCreationUnsupported:Exception()
class CronCreationChanged:Exception()
class CronCreationReadbackPending:Exception()
class InvalidCronCreation:Exception()

class CronCreateContract private constructor(val signature:String,val fields:Set<String>,val version:String) {
    fun accepts(request:CronCreation)=fields.containsAll(request.body().keys)
    companion object {
        private fun canonical(value:JsonElement):JsonElement=when(value) {
            is JsonObject->JsonObject(value.toSortedMap().mapValues {canonical(it.value)})
            is JsonArray->JsonArray(value.map(::canonical))
            else->value
        }
        fun parse(document:JsonObject):CronCreateContract=try {
            val path=document["paths"]!!.jsonObject["/api/cron/jobs"]!!.jsonObject
            val post=path["post"]!!.jsonObject
            val parameters=((path["parameters"] as? JsonArray).orEmpty()+(post["parameters"] as? JsonArray).orEmpty()).map {it.jsonObject}
            val profile=parameters.single {it.text("name")=="profile" && it.text("in")=="query"}
            val profileSchema=profile["schema"]!!.jsonObject
            require(profileSchema.text("type")=="string" || (profileSchema["anyOf"] as? JsonArray).orEmpty().any {(it as? JsonObject)?.text("type")=="string"})
            val input=post["requestBody"]!!.jsonObject["content"]!!.jsonObject["application/json"]!!.jsonObject["schema"]!!.jsonObject
            val reference=input.text("\$ref");require(reference.matches(Regex("#/components/schemas/[A-Za-z0-9_.-]+")))
            val schema=document["components"]!!.jsonObject["schemas"]!!.jsonObject[reference.substringAfterLast('/')]!!.jsonObject
            val properties=schema["properties"]!!.jsonObject
            require(properties["paused"]!!.jsonObject.text("type")=="boolean" && properties["schedule"]!!.jsonObject.text("type")=="string")
            val paused=properties["paused"]!!.jsonObject
            require(!paused.containsKey("const") || paused["const"]==JsonPrimitive(true))
            require((paused["enum"] as? JsonArray)?.contains(JsonPrimitive(true))!=false)
            require((schema["required"] as? JsonArray).orEmpty().any {(it as? JsonPrimitive)?.contentOrNull=="schedule"})
            val version=(document["info"] as? JsonObject)?.text("version").orEmpty().take(128)
            val proof=buildJsonObject {put("schema",schema);put("profile",profile);put("version",version)}
            val signature=MessageDigest.getInstance("SHA-256").digest(canonical(proof).toString().toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
            CronCreateContract(signature,Collections.unmodifiableSet(properties.keys.toSet()),version)
        } catch(e:Exception) {throw CronCreationUnsupported()}
    }
}

data class CronDeliveryTarget(val id:String,val name:String,val configured:Boolean) {
    companion object {
        val local=CronDeliveryTarget("local","local",true)
        fun inventory(j:JsonObject):List<CronDeliveryTarget> {
            val rows=j["targets"] as? JsonArray ?: error("Missing delivery catalog.");require(rows.size<=100)
            return rows.map {value->val row=value.jsonObject;val id=row.text("id");val name=row.text("name");require(id.matches(Regex("[a-z0-9_-]{1,64}")) && name.length<=256 && name.none(Char::isISOControl));CronDeliveryTarget(id,name,row["home_target_set"]?.jsonPrimitive?.booleanOrNull ?: error("Missing delivery state."))}.also {require(it.map {target->target.id}.distinct().size==it.size)}
        }
    }
}

class CronCreation(val name:String,val schedule:String,val prompt:String="",val repeat:Int?=1,
    val delivery:CronDeliveryTarget=CronDeliveryTarget.local,skills:List<String> = emptyList(),
    val model:String="",val provider:String="",val baseUrl:String="",val script:String="",context:List<String> = emptyList(),
    toolsets:List<String>?=null,val workdir:String="",val noAgent:Boolean=false,val timezone:String="",val graceMinutes:Int?=null,
    val catchUp:Boolean?=null,val pausedReason:String="") {
    val skills:List<String> = Collections.unmodifiableList(skills.toList())
    val context:List<String> = Collections.unmodifiableList(context.toList())
    val toolsets:List<String>?=toolsets?.let {Collections.unmodifiableList(it.toList())}
    init {try {
        fun text(value:String,max:Int,empty:Boolean=true)=value.length<=max && (empty || value.isNotBlank()) && value.none(Char::isISOControl)
        require(text(name,160,false) && name==name.trim() && text(schedule,512,false))
        require(prompt.length<=5000 && '\u0000' !in prompt && prompt==prompt.trim())
        require(repeat==null || repeat in 1..1_000_000)
        require(delivery.configured && delivery.id.matches(Regex("[a-z0-9_-]{1,64}")))
        for(list in listOf(this.skills,this.context,this.toolsets.orEmpty())) {require(list.size<=64 && list.distinct().size==list.size);require(list.all {text(it,256,false) && it==it.trim()})}
        require(this.context.all {it=="self" || (it!="unknown" && it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")))})
        require(text(model,256) && text(provider,128) && text(script,1024) && text(workdir,2048) && text(timezone,128) && text(pausedReason,1024))
        if(script.isNotBlank()) require(!script.startsWith('/') && !script.startsWith('\\') && !Regex("^[A-Za-z]:").containsMatchIn(script) && script.replace('\\','/').split('/').none {it in setOf("",".","..")})
        if(baseUrl.isNotBlank()) {val url=baseUrl.toHttpUrlOrNull() ?: error("Invalid provider URL.");require(baseUrl.length<=8192 && baseUrl.none(Char::isISOControl) && '\\' !in baseUrl && url.username.isEmpty() && url.password.isEmpty() && url.fragment==null)}
        require(graceMinutes==null || graceMinutes in 0..1440)
        if(noAgent) require(script.isNotBlank() && prompt.isEmpty() && this.skills.isEmpty() && model.isEmpty() && provider.isEmpty() && baseUrl.isEmpty() && this.context.isEmpty() && this.toolsets==null)
        else require(prompt.isNotBlank() || this.skills.isNotEmpty() || script.isNotBlank())
    } catch(e:Exception) {throw InvalidCronCreation()}}
    fun body()=buildJsonObject {
        put("paused",true);put("name",name);put("schedule",schedule);put("prompt",prompt);put("repeat",repeat?.let(::JsonPrimitive) ?: JsonNull);put("deliver",delivery.id)
        if(skills.isNotEmpty()) put("skills",JsonArray(skills.map(::JsonPrimitive)))
        for((key,value) in listOf("model" to model,"provider" to provider,"base_url" to baseUrl,"script" to script,"workdir" to workdir,"timezone" to timezone,"paused_reason" to pausedReason)) if(value.isNotBlank()) put(key,value)
        if(context.isNotEmpty()) put("context_from",JsonArray(context.map(::JsonPrimitive)))
        toolsets?.let {put("enabled_toolsets",JsonArray(it.map(::JsonPrimitive)))}
        if(noAgent) put("no_agent",true)
        graceMinutes?.let {put("grace_minutes",it)};catchUp?.let {put("catch_up",it)}
    }
    fun summary()=buildJsonObject {put("fields",JsonArray(body().keys.sorted().map(::JsonPrimitive)));put("paused",true);put("no_agent",noAgent);put("skill_count",skills.size);put("context_count",context.size);toolsets?.let {put("toolset_count",it.size)}}.toString()
    fun acknowledge(value:JsonObject,profile:String):String {
        val job=ManagedCronJob.parse(value,profile)
        require(job.addressable && job.name==name && job.enabled==false && job.state=="paused" && value["next_run_at"] is JsonNull)
        require(value["repeat"]?.jsonObject?.get("completed")?.jsonPrimitive?.intOrNull==0)
        return buildJsonObject {put("accepted",true);put("paused",true);put("job_id",job.id)}.toString()
    }
    fun matches(value:JsonObject):Boolean=runCatching {
        val body=body()
        require(value.text("name")==name && value.text("prompt")==prompt && value.text("deliver")==delivery.id)
        for(key in listOf("model","provider","script","workdir","timezone","paused_reason")) if(body.containsKey(key)) {
            val actual=value.text(key);val expected=body.text(key)
            require(if(key=="script") actual.replace('\\','/')==expected.replace('\\','/') else actual==expected)
        }
        if(body.containsKey("base_url")) require(value.text("base_url")==baseUrl.trimEnd('/'))
        for(key in listOf("skills","context_from","enabled_toolsets","grace_minutes","catch_up")) if(body.containsKey(key)) require(value[key]==body[key])
        require(value["no_agent"]?.jsonPrimitive?.booleanOrNull==noAgent)
        val schedule=value["schedule"]!!.jsonObject;require(schedule.text("kind") in setOf("cron","interval","once"))
        val times=value["repeat"]!!.jsonObject["times"]
        val expected=if(repeat==null && schedule.text("kind")=="once") 1 else repeat
        require(if(expected==null) times is JsonNull else (times as? JsonPrimitive)?.intOrNull==expected)
        true
    }.getOrDefault(false)
    fun sameConfiguration(first:JsonObject,second:JsonObject):Boolean {
        val keys=body().keys-setOf("paused","schedule","repeat")+setOf("id","profile","enabled","state","schedule","no_agent")
        return keys.all {first[it]==second[it]} && first["repeat"]?.jsonObject?.get("times")==second["repeat"]?.jsonObject?.get("times") && (second["repeat"]?.jsonObject?.get("completed") as? JsonPrimitive)?.intOrNull==0 && second["next_run_at"] is JsonNull
    }
    fun reviewBaseUrl()=baseUrl.toHttpUrlOrNull()?.newBuilder()?.query(null)?.build()?.toString().orEmpty()
    override fun toString()="CronCreation(redacted)"
}
