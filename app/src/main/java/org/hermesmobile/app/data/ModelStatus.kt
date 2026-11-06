package org.hermesmobile.app.data

import kotlinx.serialization.json.*

/** Optional, explicitly advertised read-only extension; catalog entries never imply loaded weights. */
data class ObservedModel(val provider:String,val model:String,val state:String,val verified:Boolean)
data class ModelRuntimeStatus(val status:String,val observedAt:Long,val models:List<ObservedModel>) {
    val verifiedModels get()=models.filter {it.verified}
    companion object {
        private val states=setOf("ready","offline","unready","unsafe","unknown")
        private fun id(row:JsonObject,key:String,limit:Int):String {
            val p=row[key] as? JsonPrimitive
            require(p?.isString==true)
            val value=p.content
            require(value.isNotBlank() && value==value.trim() && value.length<=limit && value.none {it.isISOControl() || Character.getType(it)==Character.FORMAT.toInt()})
            return value
        }
        fun parse(payload:JsonObject):ModelRuntimeStatus {
            require(payload.text("object")=="signalgrove.model_runtime")
            val version=payload["schema_version"] as? JsonPrimitive
            require(version?.isString==false && version.intOrNull==1)
            val observed=payload["observed_at"] as? JsonPrimitive
            require(observed?.isString==false && (observed.longOrNull ?: 0)>0)
            val array=payload["models"] as? JsonArray ?: error("Missing runtime model observations.")
            require(array.size<=8)
            val rows=array.map {value->
                val row=value as? JsonObject ?: error("Invalid runtime model observation.")
                val state=row.text("state");require(state in states)
                val verified=(row["verified"] as? JsonPrimitive)?.takeUnless {it.isString}?.booleanOrNull ?: error("Missing identity verification.")
                require(verified==(state=="ready"))
                ObservedModel(id(row,"provider",128),id(row,"model",512),state,verified)
            }
            require(rows.map {it.provider}.distinct().size==rows.size)
            val status=payload.text("status")
            val expected=when {
                rows.isEmpty()->{require(status in setOf("unverified","not_configured"));status}
                rows.any {it.state in setOf("unsafe","unknown")}->"unverified"
                rows.count {it.verified}>1->"ambiguous"
                rows.any {it.verified}->"ready"
                else->"unavailable"
            }
            require(status==expected)
            return ModelRuntimeStatus(status,observed.long,rows)
        }
    }
}

fun HermesCapabilities.supportsDiscovery(resource:DiscoveryResource):Boolean {
    if(!endpoints.containsKey(resource.endpoint)) return false
    if(resource!=DiscoveryResource.RuntimeModels) return true
    val endpoint=endpoints[resource.endpoint] as? JsonObject ?: return false
    val flag=(features["model_runtime_status_v1"] as? JsonPrimitive)?.takeUnless {it.isString}?.booleanOrNull
    return flag==true && endpoint.text("method")=="GET" && endpoint.text("path")=="/"+resource.path
}

const val MODEL_STATUS_REFRESH_MS=60_000L
fun modelStatusDue(fetchedAt:Long?,now:Long)=fetchedAt==null || now<fetchedAt || now-fetchedAt>=MODEL_STATUS_REFRESH_MS
fun modelStatusCached(fetchedAt:Long?,now:Long,failed:Boolean)=failed || fetchedAt==null || now<fetchedAt || now-fetchedAt>=MODEL_STATUS_REFRESH_MS*2

/** A fresh HTTP receipt cannot refresh an old host observation; tolerate 30 seconds of clock skew. */
fun modelObservationCached(observedAt:Long?,fetchedAt:Long?,now:Long,failed:Boolean):Boolean {
    if(modelStatusCached(fetchedAt,now,failed) || observedAt==null) return true
    return observedAt-now>30_000L || now-observedAt>=MODEL_STATUS_REFRESH_MS*2
}

data class ConversationModelStatus(val requested:RuntimeChoice,val reported:RuntimeChoice?,val reportedAt:Long?,val lockReported:Boolean)
fun conversationModelStatus(choice:RuntimeChoice,conversation:Conversation?,messages:List<ChatMessage>,scope:String):ConversationModelStatus {
    val current=conversation?.takeIf {it.scope==scope}
    val message=current?.let {c->messages.filter {it.sessionKey==c.key && it.role=="assistant"}.maxByOrNull {it.timestamp}}
    val runtime=message?.let {RunTelemetry.parse(it.telemetryJson)["runtime"] as? JsonObject}
    val reported=runtime?.takeIf {it.text("model").isNotBlank()}?.let {RuntimeChoice(it.text("model"),it.text("provider"))}
    return ConversationModelStatus(choice,reported,if(reported!=null) message?.timestamp else null,runtime?.text("model_lock")=="accepted")
}
