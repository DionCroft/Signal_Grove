package org.hermesmobile.app

import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class HermesStreamParserTest {
    @Test fun framingSupportsCommentsMultilineDataAndEventIds() {
        val parser=HermesStreamParser()
        assertNull(parser.line(": keepalive"));assertNull(parser.line("id: 7"))
        assertNull(parser.line("event: assistant.delta"));assertNull(parser.line("""data: {"delta":"""));assertNull(parser.line("""data: "你好"}"""))
        val event=parser.line("")!!
        assertEquals("7",event.id);assertEquals("assistant.delta",event.name)
        assertEquals("你好",StreamDecoder.decode(event).delta)
        assertNull(parser.line(""))
    }
    @Test fun assistantFinalisationDoesNotImplyRunSuccess() {
        val text=StreamDecoder.decode(SseEvent("assistant.completed","""{"content":"Partial work","run_id":"r1"}"""))
        assertEquals("Partial work",text.finalText);assertNull(text.status)
        assertEquals("failed",StreamDecoder.decode(SseEvent("run.failed","""{"run_id":"r1"}""")).status)
    }
    @Test fun distinguishesAllTerminalOutcomes() {
        listOf("completed","failed","cancelled","interrupted").forEach { status ->
            assertEquals(status,StreamDecoder.decode(SseEvent("run.$status","{}")).status)
        }
        assertEquals("response_finished",StreamDecoder.decode(SseEvent("message","[DONE]")).status)
    }
    @Test fun approvalRetainsServerChoicesAndRequestIdentity() {
        val a=StreamDecoder.decode(SseEvent("approval.request","""{"run_id":"r1","request_id":"q1","command":"rm sample","choices":["once","deny"]}""")).approval!!
        assertEquals("q1",a.requestId);assertEquals(listOf("once","deny"),a.choices)
    }
    @Test fun openAiChunksDoNotAssertAgentCompletion() {
        val u=StreamDecoder.decode(SseEvent("message","""{"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}"""))
        assertEquals("Hello",u.delta);assertNull(u.status)
        assertEquals("response_finished",StreamDecoder.decode(SseEvent("message","""{"choices":[{"delta":{},"finish_reason":"stop"}]}""")).status)
    }
    @Test(expected=IllegalArgumentException::class) fun boundsUntrustedEventSize() {HermesStreamParser().line("data: "+"x".repeat(2_000_001))}
}
