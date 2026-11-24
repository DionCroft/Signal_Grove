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

    @Test fun reportedEventTimesStayWithTheirBoundaryAcrossLateReceiptAndReplay() {
        val start = StreamDecoder.decode(SseEvent("tool.started", """{"timestamp":1700000000.125}""")).tool!!
        val end = StreamDecoder.decode(SseEvent("tool.completed", """{"timestamp":1700000005.75}""")).tool!!
        val first = event("start", "a", start.kind, 1).copy(receivedAt = 1_700_000_100_000L,
            detailsJson = start.details.toString())
        val last = event("end", "a", end.kind, 2).copy(receivedAt = 1_700_000_100_100L,
            detailsJson = end.details.toString())
        val entry = activityTimeline(listOf(last, first.copy(receivedAt = last.receivedAt + 5), first)).single()
        assertEquals(1_700_000_000_125L, entry.startReceipt?.serverEventAt)
        assertEquals(1_700_000_005_750L, entry.latest.serverEventAt)
        assertEquals(first.receivedAt, entry.startReceipt?.receivedAt)
        assertEquals(last.receivedAt, entry.latest.receivedAt)
        val child = StreamDecoder.decode(SseEvent("subagent.complete", """{"timestamp":1700000005.75}""")).tool!!
        assertEquals(last.serverEventAt, last.copy(detailsJson = child.details.toString()).serverEventAt)
    }

    @Test fun invalidAndMissingServerTimesKeepReceiptOnly() {
        for (raw in listOf("null", "true", "\"1700000000.125\"", "-1", "1e309", "9223372036854776", """{"time":2}""")) {
            val tool = StreamDecoder.decode(SseEvent("tool.completed", """{"timestamp":$raw}""")).tool!!
            assertFalse(raw, tool.details.containsKey(ACTIVITY_SERVER_EVENT_AT))
        }
        assertNull(event("legacy", "a", "completed", 1).serverEventAt)
        val quoted = event("quoted", "a", "started", 1).copy(detailsJson = """{"server_event_ms":"1700000000000"}""")
        assertNull(quoted.serverEventAt)
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
    @Test fun mcpOriginKeepsTheExactReportedServerAndNoToolPayload() {
        val payload = obj("""{"run_id":"r","tool":"mcp__fixture_api__read_entry","tool_call_id":"call",
            "mcp_server":"fixture-api","execution_generation":2,"timestamp":1700000000,
            "preview":"private fixture","args":{"key":"private fixture"},
            "result":"private fixture","runtime":{"model":"private fixture"}}""")
        val update = StreamDecoder.decode(SseEvent("tool.mcp_origin", payload.toString()))
        val origin = update.tool!!
        assertEquals(MCP_ORIGIN, origin.category)
        assertEquals("fixture-api", origin.details.text(MCP_SERVER))
        assertEquals("call", origin.callId)
        assertEquals(2L, origin.generation)
        assertEquals("", origin.preview)
        assertNull(origin.durationMs)
        assertFalse(update.toString().contains("private fixture"))
        for (field in listOf("run_id", "tool", "tool_call_id", "mcp_server")) {
            val without = StreamDecoder.decode(SseEvent("tool.mcp_origin", JsonObject(payload - field).toString()))
            assertNull(field, without.tool)
        }
        assertNull(ActivityProtocol.mcpOrigin(JsonObject(payload + ("execution_generation" to JsonPrimitive("2")))))
        assertNull(ActivityProtocol.mcpOrigin(JsonObject(payload + (MCP_SERVER to JsonPrimitive("fixture\u202E-api")))))
    }

    @Test fun mcpOriginsStayDistinctEvenWhenTheReportedToolNameAndCallIdMatch() {
        val tool = event("a", "call", "started", 1)
        val origin = tool.copy(key = "b", kind = MCP_ORIGIN, category = MCP_ORIGIN,
            detailsJson = """{"mcp_server":"fixture-api"}""")
        val other = origin.copy(key = "c", detailsJson = """{"mcp_server":"fixture_api"}""")
        val timeline = activityTimeline(listOf(tool, origin, other, origin.copy(key = "replay")))
        assertEquals(3, timeline.size)
        assertEquals(setOf("fixture-api", "fixture_api"), timeline.mapNotNull { it.latest.mcpServer }.toSet())
        assertNull(tool.copy(detailsJson = origin.detailsJson).mcpServer)
    }

    @Test fun skillLoadRequiresTheActualSkillViewCallAndKeepsNoSessionOrContentFields() {
        val payload = obj("""{"run_id":"r","tool":"skill_view","tool_call_id":"skill-call",
            "skill_name":"plugin:fixture","execution_generation":4,"timestamp":1700000000,
            "session_id":"foreign-session","content":"private instructions","path":"private path",
            "preview":"private","runtime":{"model":"private"}}""")
        val update = StreamDecoder.decode(SseEvent("skill.loaded", payload.toString(), "12"))
        val loaded = update.tool!!
        assertEquals(SKILL_LOADED, loaded.category)
        assertEquals("plugin:fixture", loaded.details.text(SKILL_NAME))
        assertEquals("skill-call", loaded.callId)
        assertEquals(4L, loaded.generation)
        assertEquals(12L, update.sequence)
        assertEquals("r", update.runId)
        assertEquals("", update.sessionId)
        assertNull(update.settledGeneration)
        assertFalse(update.toString().contains("private"))
        for (field in listOf("run_id", "tool", "tool_call_id", SKILL_NAME)) {
            assertNull(field, ActivityProtocol.skillLoaded(JsonObject(payload - field)))
        }
        assertNull(ActivityProtocol.skillLoaded(JsonObject(payload + ("tool" to JsonPrimitive("skills_list")))))
        assertNull(ActivityProtocol.skillLoaded(JsonObject(payload + (SKILL_NAME to JsonPrimitive("/private/SKILL.md")))))
    }

    @Test fun savedSkillLoadsDeduplicateReplayAndStaySeparateAcrossRuns() {
        val call = event("a", "skill-call", "started", 1)
        val loaded = call.copy(key = "b", tool = "skill_view", kind = SKILL_LOADED, category = SKILL_LOADED,
            detailsJson = """{"skill_name":"plugin:fixture"}""")
        val entries = activityTimeline(listOf(call, loaded, loaded.copy(key = "replay"),
            loaded.copy(key = "c", runId = "different-run")))
        assertEquals(3, entries.size)
        assertEquals(setOf(call.runId, "different-run"), entries.filter { it.latest.loadedSkill == "plugin:fixture" }
            .map { it.latest.runId }.toSet())
        assertNull(call.copy(detailsJson = loaded.detailsJson).loadedSkill)
    }

    @Test fun canonicalToolObservationMatchesTheCurrentSubmissionOnly() {
        val running = Conversation("key", "profile", "session", transport = "runs", runId = "r",
            status = "running", pendingKey = "turn", pendingAt = 1)
        val raw = obj("""{"status":"running","run_id":"r","session_id":"session","admission_id":"a",
            "execution_generation":2,"progress_at":1700000000.5,"tool":"workspace_read","preview":"private"}""")
        val reported = ReportedRunProgress.from(running, raw)!!
        assertEquals("workspace_read", reported.tool)
        assertTrue(reported.matches(running.copy(admissionId = "a", executionGeneration = 2)))
        assertNull(ReportedRunProgress.from(running, JsonObject(raw - "tool"))!!.tool)
        assertNotNull(reported.withoutTool().takeIf { it.matches(running) })
        assertNull(reported.withoutTool().tool)
        for (foreign in listOf(running.copy(status = "waiting_for_approval"), running.copy(pendingAt = 2),
            running.copy(remoteId = "other"), running.copy(runId = "other"), running.copy(scope = "other"),
            running.copy(admissionId = "other"), running.copy(executionGeneration = 3),
            running.copy(detailsJson = """{"source":"cli"}"""))) {
            assertFalse(reported.matches(foreign))
        }
        assertFalse(reported.toString().contains("private"))
    }

    @Test fun canonicalProgressRefusesStaleOrIncompleteReports() {
        val running = Conversation("key", "profile", "session", transport = "runs", runId = "r", status = "running")
        val raw = obj("""{"status":"running","run_id":"r","session_id":"session","admission_id":"a",
            "execution_generation":2,"progress_at":1700000000.5,"tool":"workspace_read"}""")
        val current = ReportedRunProgress.from(running, raw)!!
        for (missing in listOf("run_id", "session_id", "admission_id", "execution_generation", "progress_at")) {
            assertNull(missing, ReportedRunProgress.from(running, JsonObject(raw - missing)))
        }
        for ((key, invalid) in listOf("session_id" to JsonPrimitive("foreign"), "run_id" to JsonPrimitive("foreign"),
            "execution_generation" to JsonPrimitive("2"), "tool" to JsonNull, "progress_at" to JsonPrimitive(-1))) {
            assertNull(key, ReportedRunProgress.from(running, JsonObject(raw + (key to invalid))))
        }
        for ((field, stale) in listOf("execution_generation" to JsonPrimitive(1), "admission_id" to JsonPrimitive("b"))) {
            val observation = ReportedRunProgress.from(running, JsonObject(raw + (field to stale)))!!
            assertFalse(observation.follows(current))
        }
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
    @Test fun subagentCompletionRequiresARecognizedReportedOutcome() {
        val outcomes = mapOf(
            "completed" to "completed", "timeout" to "timeout", "stalled" to "stalled",
            "failed" to "failed", "error" to "failed", "cancelled" to "cancelled",
            "interrupted" to "interrupted", "incomplete" to "incomplete",
            "running" to "unknown", "fixture-unknown-status" to "unknown",
        )
        outcomes.forEach { (status, expected) ->
            val body = buildJsonObject { put("subagent_id", "child"); put("status", status) }
            val reported = StreamDecoder.decode(SseEvent("subagent.complete", body.toString()))
            assertEquals(status, expected, reported.tool?.kind)
            if (expected == "unknown") assertFalse(reported.toString().contains(status))
        }
        assertEquals("unknown", StreamDecoder.decode(SseEvent("subagent.complete", "{}")).tool?.kind)
    }

    @Test fun legacySubagentHistoryKeepsAnUncertainOutcomeAfterAReplayedStart() {
        val oldTimeout = event("done", "child", "completed", 2).copy(
            category = "subagent", detailsJson = """{"status":"timeout"}""",
        )
        val lateStart = event("late", "child", "started", 3).copy(
            category = "subagent", detailsJson = """{"status":"completed"}""",
        )
        val noOutcome = oldTimeout.copy(detailsJson = "{}")
        val current = Conversation("c", "scope-a", "session", runId = "r", status = "running",
            executionGeneration = 2)
        assertEquals("timeout", activityTimeline(listOf(oldTimeout, lateStart)).single().latest.kind)
        val timeline = activityTimeline(listOf(noOutcome, lateStart))
        assertEquals("unknown", timeline.single().latest.kind)
        assertNull(activeActivity(current, timeline))
        val newUnknown = noOutcome.copy(kind = "unknown")
        assertEquals("unknown", activityTimeline(listOf(newUnknown, lateStart)).single().latest.kind)
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
