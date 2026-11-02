package org.hermesmobile.app.network

import org.hermesmobile.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Explicit local simulator. No server, model, tool or microphone is invoked. */
open class FakeHermesApiClient : HermesApiClient {
    private data class DemoRun(val id: String,var status: String="running",var output: String="",var control: ControlRequest?=null,val answer: CompletableDeferred<String> = CompletableDeferred())
    private val runs=ConcurrentHashMap<String,DemoRun>()
    private val sessionMetadata=ConcurrentHashMap<String,JsonObject>()
    override suspend fun capabilities() = HermesCapabilities("Demo",buildJsonObject {
        put("session_resources",true);put("session_chat",true);put("session_chat_streaming",true);put("run_stop",true);put("run_status",true);put("run_approval_response",true);put("session_model_lock",true);put("session_fork",true);put("run_steer",true)
    },buildJsonObject {
        SessionAction.entries.forEach {put(it.endpoint,buildJsonObject {put("method","POST");put("path",when(it) {SessionAction.Lock->"/api/sessions/{session_id}/model";SessionAction.Fork->"/api/sessions/{session_id}/fork";SessionAction.Steer->"/v1/runs/{run_id}/steer"})})}
        put("session",buildJsonObject {put("method","GET");put("path","/api/sessions/{session_id}")})
        DiscoveryResource.entries.forEach {put(it.endpoint,buildJsonObject {put("method","GET");put("path","/"+it.path)})}})
    override suspend fun discovery(resource: DiscoveryResource,refreshModels: Boolean): JsonObject = Json.parseToJsonElement(when(resource) {
        DiscoveryResource.Readiness -> """{"status":"ready","version":"Demo fixture","gateway_state":"simulated","active_agents":0,"readiness":{"status":"ready"}}"""
        DiscoveryResource.Models -> """{"model":"demo-model","provider":"demo","providers":[{"slug":"demo","name":"Demo · local simulation","authenticated":true,"models":["demo-model"],"capabilities":{"demo-model":{"reasoning":false}},"pricing":{}}]}"""
        DiscoveryResource.Skills -> """{"data":[{"name":"demo-reflection","description":"A sample skill entry. No skill executes in Demo.","category":"Demo","disabled":false}]}"""
        DiscoveryResource.Toolsets -> """{"data":[{"name":"demo","label":"Demo tools","description":"Simulated activity only; no server tools run.","enabled":false,"configured":false,"tools":["demo_note"]}]}"""
    }).jsonObject
    override suspend fun sessionDetails(id:String)=JsonObject(buildJsonObject {put("id",id);put("source","api_server");put("model","demo-model");put("message_count",0);put("preview","Demo metadata only")}+sessionMetadata[id].orEmpty())
    override suspend fun sessionAction(action:SessionAction,target:String,body:JsonObject):JsonObject {
        SessionProtocol.validate(action,target,body)
        return when(action) {
            SessionAction.Lock->buildJsonObject {put("object","hermes.session.model_lock");put("session_id",target);put("runtime",buildJsonObject {put("model",body.text("model"));put("provider",body.text("provider"));put("route_source","demo");put("model_lock","accepted")})}
            SessionAction.Fork->{
                val child=buildJsonObject {put("id",body.text("id"));put("parent_session_id",target);put("title",body.text("title"));put("source","api_server")}
                sessionMetadata[body.text("id")]=child
                sessionMetadata[target]=JsonObject(sessionMetadata[target].orEmpty()+mapOf("end_reason" to JsonPrimitive("branched")))
                buildJsonObject {put("session",child)}
            }
            SessionAction.Steer->{require(runs[target]?.status=="running") {"The Demo run is no longer running."};buildJsonObject {put("object","hermes.run.steer");put("run_id",target);put("accepted",true)}}
        }
    }
    override suspend fun sessions() = emptyList<RemoteSession>()
    override suspend fun createSession(id:String,title:String)=RemoteSession(id,title)
    override suspend fun messages(id:String)=emptyList<RemoteMessage>()
    private val demoJobs=ConcurrentHashMap<String,HermesJob>().apply {put("demo-job",HermesJob("demo-job","Weekly reflection · Demo","Review completed local goals. This sample job never runs.","Every Sunday · example only","demo"))}
    override suspend fun jobs()=demoJobs.values.sortedBy {it.name}
    override suspend fun job(id:String)=demoJobs[id] ?: throw HermesHttpException(404)
    override suspend fun mutateJob(operation: String,id: String,body: JsonObject): HermesJob? {
        JobProtocol.validate(operation,id,body)
        if(operation=="delete") {demoJobs.remove(id);return null}
        val old=if(operation=="create") HermesJob("demo-${UUID.randomUUID()}","","","","paused",false) else job(id)
        val next=when(operation) {
            "create","update"->old.copy(name=body.text("name"),prompt=body.text("prompt"),schedule=body.text("schedule").ifBlank {old.schedule})
            "pause"->old.copy(enabled=false,state="paused")
            "resume"->old.copy(enabled=true,state="demo")
            else->old.copy(state="demo",lastRun="Demo simulation only · no agent executed")
        }
        demoJobs[next.id]=next;return next
    }
    override suspend fun submitRun(session:String,input:String,key:String)="demo-run"
    override suspend fun run(id:String):JsonObject {
        val r=runs[id] ?: return buildJsonObject {put("status","interrupted");put("pending_controls",buildJsonArray {})}
        return buildJsonObject {put("run_id",id);put("status",r.status);put("output",r.output);put("execution_generation",1)
            put("pending_controls",buildJsonArray {r.control?.let {c -> add(buildJsonObject {put("prompt_id",c.requestId);put("execution_generation",1);put("kind",c.kind);put(if(c.kind=="approval") "description" else "question",c.prompt);put("choices",JsonArray(c.choices.map {JsonPrimitive(it)}))})}})}
    }
    override suspend fun stop(id:String) {runs[id]?.let {it.status="cancelled";it.answer.cancel()}}
    override suspend fun approve(approval:Approval,choice:String) {respondControl(ControlRequest(approval.runId,approval.requestId,approval.generation,"approval",approval.description,approval.choices).stored(""),choice)}
    override suspend fun respondControl(control:PendingControl,response:String) {
        ControlProtocol.responseBody(control,response)
        val r=runs[control.runId] ?: throw HermesHttpException(409)
        require(r.control?.requestId==control.requestId && control.generation==1L) {"The Demo prompt is no longer pending."}
        r.control=null;r.status="running";r.answer.complete(response)
    }
    override fun stream(path:String,body:JsonObject?,key:String,lastEventId:Long?,channelKey:String)=flow {
        val r=DemoRun("demo_${UUID.randomUUID().toString().replace("-","")}");runs[r.id]=r
        var seq=0L
        suspend fun frame(update:StreamUpdate) {emit(update.copy(runId=r.id,sequence=seq++))}
        frame(StreamUpdate(status="running"))
        frame(StreamUpdate(tool=ToolUpdate("Demo note","started","A simulated local activity",null)))
        delay(80)
        val exercise=body?.text("input").orEmpty()
        var reply="This is **Demo mode**. Your message stays on this device.\n\nConnect your Hermes server in Settings to start a real conversation.\n\n```text\nYour agent. Your infrastructure.\n```"
        if(exercise in listOf("Demo approval exercise","Demo clarification exercise")) {
            val approval=exercise=="Demo approval exercise"
            val c=ControlRequest(r.id,"prompt_${UUID.randomUUID()}",1,if(approval) "approval" else "clarify",
                if(approval) "Demo only: allow a simulated local note? No real command or server action will run." else "Which Demo explanation would you like?",
                if(approval) listOf("once","deny") else listOf("Brief","Detailed"))
            r.control=c;r.status=if(approval) "waiting_for_approval" else "waiting_for_input"
            frame(StreamUpdate(status=r.status,control=c))
            val answer=r.answer.await()
            reply=if(approval) "Demo decision: **$answer**. No real tool or server action was executed." else "Demo answer received: **$answer**. This stayed on your device."
            frame(StreamUpdate(tool=ToolUpdate("Demo note",if(answer=="deny") "failed" else "completed",if(answer=="deny") "The simulated action was denied." else "The simulated action finished.",80)))
        } else frame(StreamUpdate(tool=ToolUpdate("Demo note","completed","The simulated activity finished.",80)))
        for(chunk in reply.chunked(9)) {delay(20);frame(StreamUpdate(delta=chunk))}
        r.output=reply;r.status="completed";frame(StreamUpdate(status="completed",finalText=reply))
    }
}

class UnavailableHermesApiClient : HermesApiClient {
    private fun unavailable():Nothing=throw java.io.IOException("Server is not configured")
    override suspend fun capabilities():HermesCapabilities=unavailable()
    override suspend fun sessions():List<RemoteSession> = unavailable()
    override suspend fun createSession(id:String,title:String):RemoteSession=unavailable()
    override suspend fun messages(id:String):List<RemoteMessage> = unavailable()
    override suspend fun jobs():List<HermesJob> = unavailable()
    override suspend fun job(id:String):HermesJob=unavailable()
    override suspend fun submitRun(session:String,input:String,key:String):String=unavailable()
    override suspend fun run(id:String):JsonObject=unavailable()
    override suspend fun stop(id:String):Unit=unavailable()
    override suspend fun approve(approval:Approval,choice:String):Unit=unavailable()
    override fun stream(path:String,body:JsonObject?,key:String,lastEventId:Long?,channelKey:String)=flow<StreamUpdate> {unavailable()}
}
