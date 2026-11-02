package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class ActivityProtocolTest {
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private fun event(id:String,call:String,kind:String,seq:Long,run:String="r",gen:Long?=2)=ToolEvent(id,"c",run,"shell",kind,"preview-$id",null,seq,callId=call,generation=gen,sequence=seq)
    @Test fun parallelCallsWithTheSameToolNamePairOnlyByExactCallIdentity() {
        val entries=activityTimeline(listOf(event("1","a","started",1),event("2","b","started",2),event("3","b","failed",3),event("4","a","completed",4)))
        assertEquals(2,entries.size)
        assertEquals("completed",entries.first {it.first.callId=="a"}.latest.kind)
        assertEquals("failed",entries.first {it.first.callId=="b"}.latest.kind)
        assertTrue(entries.all {it.events.size==2})
    }
    @Test fun absentIdentityDifferentRunsAndGenerationsNeverMerge() {
        val rows=listOf(event("1","","started",1),event("2","","completed",2),event("3","a","started",3),event("4","a","completed",4,run="other"),event("5","a","completed",5,gen=3))
        assertEquals(5,activityTimeline(rows).size)
        assertEquals("failed",activityTimeline(listOf(event("1","a","failed",1),event("2","a","started",2))).single().latest.kind)
    }
    @Test fun toolWireIdentityAndErrorRemainBoundToTheExactCompletion() {
        val update=StreamDecoder.decode(SseEvent("tool.completed","""{"tool":"shell","tool_call_id":"call-b","duration":0.125,"error":true,"execution_generation":4,"seq":9}"""))
        val tool=update.tool!!;assertEquals("call-b",tool.callId);assertEquals("failed",tool.kind);assertEquals(125L,tool.durationMs);assertEquals(4L,tool.generation)
        assertNull(ActivityProtocol.duration(JsonPrimitive(-1)));assertNull(ActivityProtocol.duration(JsonPrimitive(1e30)))
    }
    @Test fun publicSubagentMetadataRetainsLineageAndBoundsTextAndFiles() {
        val raw=buildJsonObject {put("subagent_id","agent-a");put("child_session_id","child-a");put("goal","Goal");put("summary","x".repeat(5000));put("reasoning","private");put("cost_usd",0.02);put("input_tokens",12);put("files_read",JsonArray(List(100) {JsonPrimitive("f".repeat(1000))}))}
        val update=StreamDecoder.decode(SseEvent("subagent.complete",raw.toString())).tool!!
        assertEquals("subagent",update.category);assertEquals("agent-a",update.callId);assertEquals("child-a",update.details.text("child_session_id"));assertEquals(1000,update.preview.length)
        assertEquals("",ActivityProtocol.subagent(obj("""{"delegation_id":"batch"}"""),false).callId)
        assertEquals("batch:1",ActivityProtocol.subagent(obj("""{"delegation_id":"batch","task_index":1}"""),false).callId)
        assertFalse(update.details.containsKey("reasoning"));assertEquals(20,update.details["files_read"]!!.jsonArray.size);assertTrue(update.details.toString().length<20000)
    }
    @Test fun accountingKeepsMissingUnknownAndSeparatesRequestedFromServedRuntime() {
        val j=obj("""{"usage":{"input_tokens":120,"output_tokens":4,"cache_read_tokens":100,"cache_write_tokens":-3,"reasoning":"private","total_tokens":"bad"},"runtime":{"model":"fallback","provider":"provider-b","requested":{"model":"original","provider":"provider-a","api_key":"private"},"api_key":"private"},"model":"echoed-request"}""")
        val result=RunTelemetry.from(j);assertEquals("fallback",result["runtime"]!!.jsonObject.text("model"));assertEquals("original",result["runtime"]!!.jsonObject["requested"]!!.jsonObject.text("model"))
        assertFalse(result.toString().contains("private"));assertFalse(result["usage"]!!.jsonObject.containsKey("cache_write_tokens"));assertFalse(result["usage"]!!.jsonObject.containsKey("total_tokens"))
        val changed=RunTelemetry.parse(RunTelemetry.merge(result.toString(),obj("""{"runtime":{"model":"another"}}""")))
        assertFalse(changed["runtime"]!!.jsonObject.containsKey("provider"))
        assertEquals("",RunTelemetry.merge("",obj("""{"reasoning":"private"}""")))
    }
    @Test fun partialAccountingMergesWithoutInventingZerosOrClearingKnownValues() {
        val first=RunTelemetry.from(obj("""{"usage":{"prompt_tokens":10,"completion_tokens":3},"runtime":{"model":"served"}}"""))
        val merged=RunTelemetry.parse(RunTelemetry.merge(first.toString(),obj("""{"usage":{"cache_read_tokens":0}}""")))
        assertEquals(10,merged["usage"]!!.jsonObject["input_tokens"]!!.jsonPrimitive.int)
        assertEquals(0,merged["usage"]!!.jsonObject["cache_read_tokens"]!!.jsonPrimitive.int);assertFalse(merged["usage"]!!.jsonObject.containsKey("total_tokens"));assertEquals("served",merged["runtime"]!!.jsonObject.text("model"))
    }
    @Test fun incompleteIsAnExplicitTerminalRunOutcomeAndPrivateReasoningEventsStayUnstored() {
        assertEquals("incomplete",StreamDecoder.decode(SseEvent("run.incomplete","{} ")).status)
        val private=StreamDecoder.decode(SseEvent("reasoning.available","""{"text":"private"}"""))
        assertEquals("",private.delta);assertNull(private.activity);assertTrue(private.telemetry.isEmpty());assertNull(private.tool)
    }
}
