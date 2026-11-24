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
    @Test fun separatorsInsideIdsCannotMergeAnOlderCompletionWithTheLiveCall() {
        val current = Conversation("c", "scope-a", "session", runId = "r|2|tool|first",
            status = "running", executionGeneration = 2)
        val start = event("start", "second", "started", 1, run = current.runId)
        val olderCompletion = event("done", "first|2|tool|second", "completed", 2)
        val timeline = activityTimeline(listOf(start, olderCompletion))
        assertEquals(2, timeline.size)
        assertEquals(start, activeActivity(current, timeline))
    }

    @Test fun timelineKeepsTheFirstObservedStartReceiptAcrossLateEvents() {
        val start = event("start", "a", "started", 2).copy(receivedAt = 10)
        val completion = event("done", "a", "completed", 3).copy(receivedAt = 20)
        val lateStart = event("late", "a", "started", 1).copy(receivedAt = 30)
        val entry = activityTimeline(listOf(completion, lateStart, start)).single()
        assertEquals(start, entry.startReceipt)
        assertEquals(completion, entry.latest)
        assertNull(activityTimeline(listOf(completion)).single().startReceipt)
    }

    @Test fun liveStepUsesOnlyAnUnfinishedIdentifiedCallForTheCurrentRun() {
        val current = Conversation("c", "scope-a", "session", runId = "r", status = "running",
            executionGeneration = 2)
        val start = event("start", "current-call", "started", 1)
        val timeline = activityTimeline(listOf(
            start,
            event("anonymous", "", "started", 20),
            event("old-run", "old-call", "started", 21, run = "other"),
            event("old-gen", "old-generation-call", "started", 22, gen = 1),
            event("foreign", "foreign-call", "started", 23).copy(sessionKey = "other-session"),
        ))
        assertEquals(start, activeActivity(current, timeline))
        assertNull(activeActivity(current, activityTimeline(listOf(start, event("end", "current-call", "failed", 2)))))
        for (status in listOf("queued", "unknown", "completed", "waiting_for_approval")) {
            assertNull(activeActivity(current.copy(status = status), timeline))
        }
        assertNull(activeActivity(current.copy(detailsJson = """{"source":"cli"}"""), timeline))
    }
    @Test fun activityShowsOnlyCurrentIdentifiedWaitsWithoutRequestContent() {
        val conversation = Conversation("scope-a:session", "scope-a", "session", runId = "run",
            status = "waiting_for_approval", executionGeneration = 4)
        val approval = PendingControl("approval", conversation.key, "run", "request", 4, "approval",
            "private task", "[\"private choice\"]", false, receivedAt = 100)
        val answer = approval.copy(key = "answer", requestId = "answer-request", kind = "clarify",
            state = "unknown", receivedAt = 200)
        val stale = listOf(
            approval.copy(sessionKey = "scope-b:session"),
            approval.copy(runId = "older-run"),
            approval.copy(generation = 3),
            approval.copy(generation = null),
            approval.copy(requestId = ""),
            approval.copy(state = "resolved"),
            approval.copy(kind = "unsupported")
        )
        val waits = activityWaits(conversation, stale + listOf(approval, answer))
        assertEquals(listOf(ActivityWait(true, "pending", 100), ActivityWait(false, "unknown", 200)), waits)
        assertFalse(waits.toString().contains("private"))
        assertTrue(activityWaits(conversation.copy(status = "completed"), listOf(approval)).isEmpty())
        assertTrue(activityWaits(conversation.copy(detailsJson = """{"source":"cli"}"""), listOf(approval)).isEmpty())
        assertEquals(listOf(ActivityWait(true, "responding", 100)),
            activityWaits(conversation.copy(executionGeneration = null), listOf(approval.copy(state = "responding"))))
    }
    @Test fun toolWireIdentityAndErrorRemainBoundToTheExactCompletion() {
        val update=StreamDecoder.decode(SseEvent("tool.completed","""{"tool":"shell","tool_call_id":"call-b","duration":0.125,"error":true,"execution_generation":4,"seq":9,"preview":"fixture-raw-result","args":{"credential":"fixture-raw-argument"}}"""))
        val tool=update.tool!!;assertEquals("call-b",tool.callId);assertEquals("failed",tool.kind);assertEquals(125L,tool.durationMs);assertEquals(4L,tool.generation)
        assertEquals("", tool.preview)
        assertFalse(update.toString().contains("fixture-raw-"))
        assertNull(ActivityProtocol.duration(JsonPrimitive(-1)));assertNull(ActivityProtocol.duration(JsonPrimitive(1e30)))
    }
    @Test fun subagentActivityKeepsLineageAndAccountingWithoutRawPayloads() {
        val raw = obj("""{"subagent_id":"agent-a","child_session_id":"child-a","status":"completed",
            "goal":"fixture-private-goal","summary":"fixture-private-summary","reasoning":"fixture-private-reasoning",
            "output_tail":"fixture-private-output","files_read":["fixture-private-path"],
            "cost_usd":0.02,"input_tokens":12}""")
        val update = StreamDecoder.decode(SseEvent("subagent.complete", raw.toString())).tool!!
        assertEquals("subagent", update.category)
        assertEquals("agent-a", update.callId)
        assertEquals("child-a", update.details.text("child_session_id"))
        assertEquals("completed", update.kind)
        assertEquals(12L, update.details["input_tokens"]!!.jsonPrimitive.long)
        assertEquals("", update.preview)
        assertFalse(update.toString().contains("fixture-private-"))
        assertEquals("", ActivityProtocol.subagent(obj("""{"delegation_id":"batch"}"""), false).callId)
        assertEquals("batch:1", ActivityProtocol.subagent(obj("""{"delegation_id":"batch","task_index":1}"""), false).callId)
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
        for (type in listOf("reasoning.available", "assistant.commentary", "message.interim")) {
            val update = StreamDecoder.decode(SseEvent(type, """{"text":"private-text","seq":12}"""))
            assertEquals(StreamUpdate(sequence = 12), update)
            assertFalse(update.toString().contains("private-text"))
        }
    }
}
