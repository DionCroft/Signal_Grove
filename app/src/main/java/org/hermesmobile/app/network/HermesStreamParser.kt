package org.hermesmobile.app.network

import kotlinx.serialization.json.*

data class SseEvent(val name: String,val data: String,val id: String = "")
class HermesStreamParser {
    private var event="message";private var id="";private val data=mutableListOf<String>();private var size=0
    fun line(line: String): SseEvent? {
        if(line.isEmpty()) {val result=if(data.isEmpty()) null else SseEvent(event,data.joinToString("\n"),id);event="message";data.clear();size=0;return result}
        if(line.startsWith(':')) return null
        val field=line.substringBefore(':');val value=if(':' in line) line.substringAfter(':').removePrefix(" ") else ""
        when(field) {"event"->event=value;"id"->if('\u0000' !in value) id=value;"data"->{size+=value.length;require(size<=2_000_000) {"Stream event is too large"};data+=value}}
        return null
    }
}
data class ToolUpdate(val tool: String,val kind: String,val preview: String,val durationMs: Long?,val callId:String="",val category:String="tool",val details:JsonObject=buildJsonObject {},val generation:Long?=null)
data class StreamUpdate(val delta: String="",val finalText: String?=null,val status: String?=null,val activity: String?=null,
    val runId: String="",val sessionId: String="",val approval: org.hermesmobile.app.data.Approval?=null,
    val control: ControlRequest?=null,val tool: ToolUpdate?=null,val sequence: Long?=null,val settledRequestId: String="",val replayGap: Boolean=false,val responseBoundary: Boolean=false,val settledGeneration: Long?=null,val telemetry:JsonObject=buildJsonObject {})
object StreamDecoder {
    private val json=Json {ignoreUnknownKeys=true}
    fun decode(event: SseEvent): StreamUpdate {
        if(event.data=="[DONE]") return StreamUpdate(responseBoundary=true)
        val j=json.parseToJsonElement(event.data).jsonObject
        fun text(key: String)=(j[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val name=if(event.name=="message") text("type").ifBlank {text("event")}.ifBlank {event.name} else event.name
        val run=text("run_id");val session=text("session_id")
        val update=when(name) {
            "assistant.delta","message.delta","response.output_text.delta"->StreamUpdate(delta=text("delta"))
            "assistant.completed"->StreamUpdate(finalText=text("content"))
            "run.completed"->StreamUpdate(finalText=text("output").takeIf {it.isNotEmpty()},status="completed")
            "run.failed","run.cancelled","run.interrupted","run.incomplete"->StreamUpdate(status=name.removePrefix("run."))
            "run.started","run.queued"->StreamUpdate(status="running")
            "approval.request","clarify.request"->{
                val control=ControlProtocol.prompt(j,run,if(name=="approval.request") "approval" else "clarify")
                StreamUpdate(status=if(control.kind=="approval") "waiting_for_approval" else "waiting_for_input",control=control,
                    approval=if(control.kind=="approval") org.hermesmobile.app.data.Approval(run,control.requestId,control.prompt,control.choices,control.generation) else null)
            }
            "approval.responded","approval.settled","clarify.settled"->StreamUpdate(status="response_submitted",settledRequestId=text("prompt_id").ifBlank {text("request_id")})
            "tool.started","tool.completed","tool.failed"->{
                val kind=if(name=="tool.failed" || (j["error"] as? JsonPrimitive)?.booleanOrNull==true) "failed" else name.removePrefix("tool.")
                val tool=text("tool_name").ifBlank {text("tool")}
                StreamUpdate(activity="tool.$kind: $tool",tool=ActivityProtocol.tool(j,kind))
            }
            "subagent.start","subagent.complete" -> StreamUpdate(activity=name,tool=ActivityProtocol.subagent(j,name=="subagent.complete"))
            "assistant.commentary","message.interim"->StreamUpdate(activity=text("text"))
            "replay.truncated"->StreamUpdate(replayGap=true)
            "error","run.error"->StreamUpdate(status="failed")
            else->{
                val choice=(j["choices"] as? JsonArray)?.firstOrNull() as? JsonObject;val d=choice?.get("delta") as? JsonObject
                val finish=(choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull
                StreamUpdate(delta=(d?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty(),status=when(finish) {"cancelled"->"response_cancelled";"error"->"response_failed";"length"->"response_incomplete";null->null;else->"response_finished"})
            }
        }
        return update.copy(telemetry=org.hermesmobile.app.data.RunTelemetry.from(j),settledGeneration=(j["execution_generation"] as? JsonPrimitive)?.longOrNull,runId=run,sessionId=session,sequence=(j["seq"] as? JsonPrimitive)?.longOrNull ?: event.id.toLongOrNull())
    }
}
