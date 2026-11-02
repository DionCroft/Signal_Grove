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
    override suspend fun capabilities() = HermesCapabilities("Demo",buildJsonObject {
        put("session_resources",true);put("session_chat",true);put("session_chat_streaming",true);put("run_stop",true);put("run_status",true);put("run_approval_response",true)
    })
    override suspend fun sessions() = emptyList<RemoteSession>()
    override suspend fun createSession(id:String,title:String)=RemoteSession(id,title)
    override suspend fun messages(id:String)=emptyList<RemoteMessage>()
    override suspend fun jobs()=listOf(HermesJob("demo-job","Weekly reflection · Demo","Review completed local goals. This sample job never runs.","Every Sunday · example only","demo"))
    override suspend fun job(id:String)=jobs().first()
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
    override fun stream(path:String,body:JsonObject?,key:String,lastEventId:Long?)=flow {
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
    override fun stream(path:String,body:JsonObject?,key:String,lastEventId:Long?)=flow<StreamUpdate> {unavailable()}
}
