package org.hermesmobile.app.network

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

enum class SessionAction(val endpoint: String) {Lock("session_model_lock"),Fork("session_fork"),Steer("run_steer")}
object SessionProtocol {
    fun validate(action: SessionAction,target: String,body: JsonObject) {
        OkHttpHermesApiClient.segment(target)
        when(action) {
            SessionAction.Lock -> {require(body.keys.all {it in setOf("model","provider","model_options")});require(RuntimeChoice.parse(body).model.isNotBlank()) {"Choose a model before locking this session."}}
            SessionAction.Fork -> {require(body.keys==setOf("id","title"));OkHttpHermesApiClient.segment(body.text("id"));require(body.text("id").length<=128 && body.text("title").isNotBlank() && body.text("title").length<=200)}
            SessionAction.Steer -> require(body.keys==setOf("input") && body.text("input").isNotBlank() && body.text("input").length<=16384)
        }
        require(body.toString().toByteArray(Charsets.UTF_8).size<=64000) {"The operation exceeds the mobile request limit."}
    }
    fun acknowledged(action: SessionAction,target: String,body: JsonObject,ack: JsonObject): JsonObject {
        validate(action,target,body)
        return when(action) {
            SessionAction.Lock -> {
                val runtime=ack["runtime"] as? JsonObject ?: error("Missing model-lock acknowledgement.")
                require(ack.text("object")=="hermes.session.model_lock" && ack.text("session_id")==target && runtime.text("model_lock")=="accepted") {"The server did not acknowledge this exact model lock."}
                buildJsonObject {put("session_id",target);put("runtime",JsonObject(runtime.filterKeys {it in setOf("provider","model","route_source","model_lock","requested")}))}
            }
            SessionAction.Fork -> {
                val session=ack["session"] as? JsonObject ?: error("Missing branch acknowledgement.")
                require(session.text("id")==body.text("id") && session.text("parent_session_id")==target) {"The server did not confirm the requested branch lineage."}
                buildJsonObject {put("session",JsonObject(session.filterKeys {it in setOf("id","parent_session_id","title","source","model")}))}
            }
            SessionAction.Steer -> {
                require(ack.text("object")=="hermes.run.steer" && ack.text("run_id")==target && (ack["accepted"] as? JsonPrimitive)?.booleanOrNull==true) {"The server did not accept guidance for this exact run."}
                buildJsonObject {put("run_id",target);put("accepted",true)}
            }
        }.also {require(it.toString().toByteArray(Charsets.UTF_8).size<=64000) {"The acknowledgement is too large to retain safely."}}
    }
    fun sessionSnapshot(session: JsonObject,id: String): JsonObject {
        require(session.text("id")==id) {"The server returned another session."}
        if(session.containsKey("source")) SessionOrigin.wire(session)
        val keys=setOf("id","source","model","title","parent_session_id","end_reason","message_count","tool_call_count","input_tokens","output_tokens","cache_read_tokens","cache_write_tokens","reasoning_tokens","estimated_cost_usd","actual_cost_usd","api_call_count","last_active")
        return JsonObject(session.filter {(key,value) -> key in keys && value is JsonPrimitive && value.content.length<=1000})
    }
    fun definiteRefusal(action: SessionAction,error: HermesHttpException)=when(action) {
        SessionAction.Lock -> (error.code==400 && error.serverCode=="missing_model") || (error.code==409 && error.serverCode=="model_lock_unavailable")
        SessionAction.Fork -> (error.code==400 && error.serverCode=="invalid_session_id") || (error.code==409 && error.serverCode=="session_exists")
        SessionAction.Steer -> (error.code==400 && error.serverCode=="invalid_steer_input") || (error.code==409 && error.serverCode=="run_not_accepting_steer")
    }
}
