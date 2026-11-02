package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.voice.*
import org.junit.Assert.*
import org.junit.Test

class ControlsAndSpeechTest {
    private fun objectOf(text:String)=Json.parseToJsonElement(text).jsonObject
    private fun control(kind:String="approval",generation:Long?=3)=ControlRequest("run_1","prompt_1",generation,kind,"Review this harmless action",listOf("once","deny")).stored("chat")
    @Test fun canonicalDecisionHasExactlyTheCurrentIdentityAndOneDecision() {
        val body=ControlProtocol.responseBody(control(),"once")
        assertEquals(setOf("request_id","execution_generation","choice"),body.keys)
        assertEquals(3,body["execution_generation"]!!.jsonPrimitive.int)
        assertEquals("prompt_1",body.text("request_id"))
        try {ControlProtocol.responseBody(control(),"always");fail("Permanent approvals are unavailable")} catch(_:IllegalArgumentException) {}
    }
    @Test fun truncatedOrUnidentifiedConsentCannotBeAllowed() {
        try {ControlProtocol.responseBody(control().copy(limited=true),"once");fail("Incomplete consent")} catch(_:IllegalArgumentException) {}
        assertEquals("deny",ControlProtocol.responseBody(control().copy(limited=true),"deny").text("choice"))
        try {ControlProtocol.responseBody(control().copy(requestId=""),"once");fail("Identity missing")} catch(_:IllegalArgumentException) {}
    }
    @Test fun canonicalSnapshotSupportsSeveralGenerationBoundPromptsAndIgnoresSecretKinds() {
        val rows=ControlProtocol.snapshot(objectOf("""{"execution_generation":7,"pending_controls":[{"kind":"approval","prompt_id":"a","command":"echo safe","choices":["once","deny"]},{"kind":"clarify","prompt_id":"b","question":"Which folder?","choices":["one","two"]},{"kind":"secret","prompt_id":"c"}]}"""),"r")
        assertEquals(listOf("approval","clarify"),rows.map {it.kind});assertTrue(rows.all {it.generation==7L})
        assertFalse(ControlProtocol.matches(rows.first().stored("chat"),rows.first().copy(prompt="Changed command")))
    }
    @Test fun legacyApprovalRetainsItsRequestIdWithoutInventingAGeneration() {
        val c=ControlProtocol.snapshot(objectOf("""{"status":"waiting_for_approval","approval":{"request_id":"q","description":"Review","choices":["once","deny"]}}"""),"r").single()
        assertNull(c.generation);assertEquals(setOf("request_id","choice"),ControlProtocol.responseBody(c.stored("c"),"deny").keys)
    }
    @Test fun controlMutationUsesDocumentedRouteAndExactBodyAndRequiresAnAcknowledgement()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("""{"run_id":"run_1","status":"resolved","prompt_id":"prompt_1"}"""))
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/test/").toString(),"fixture",true),true)
            api.respondControl(control(),"once")
            val request=server.takeRequest();assertEquals("POST",request.method);assertEquals("/p/test/v1/runs/run_1/approval",request.path)
            assertEquals(setOf("request_id","execution_generation","choice"),objectOf(request.body.readUtf8()).keys)
            server.enqueue(MockResponse().setBody("{}"))
            try {api.respondControl(control(),"deny");fail("Unconfirmed response")} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun clarificationAndUnknownResolutionNeverSubmitANewRun()=runBlocking {
        MockWebServer().use {server ->
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/test/").toString(),"fixture",true),true)
            server.enqueue(MockResponse().setBody("""{"run_id":"run_1","status":"resolved","prompt_id":"prompt_1"}"""))
            api.respondControl(control("clarify"),"Use the sample folder")
            val answer=server.takeRequest();assertEquals("/p/test/v1/runs/run_1/clarify",answer.path)
            assertEquals(setOf("request_id","execution_generation","answer"),objectOf(answer.body.readUtf8()).keys)
            server.enqueue(MockResponse().setBody("""{"run_id":"run_1","outcome":"interrupted"}"""))
            api.resolveUnknown("run_1","adm_1",3)
            val ack=server.takeRequest();assertEquals("/p/test/v1/runs/run_1/resolve-unknown",ack.path)
            assertEquals(setOf("admission_id","execution_generation"),objectOf(ack.body.readUtf8()).keys)
        }
    }
    @Test fun toolFailureAndCompatibilityTerminalStatesAreDistinct() {
        val tool=StreamDecoder.decode(SseEvent("tool.completed","""{"tool":"terminal","error":true,"duration":0.2,"preview":"denied","seq":5}"""))
        assertEquals("failed",tool.tool!!.kind);assertEquals(200L,tool.tool!!.durationMs);assertNull(tool.status);assertEquals(5L,tool.sequence)
        mapOf("cancelled" to "cancelled","error" to "failed","length" to "incomplete","stop" to "response_finished").forEach {(finish,status) ->
            assertEquals(status,StreamDecoder.decode(SseEvent("message","""{"choices":[{"delta":{},"finish_reason":"$finish"}]}""")).status)
        }
        val reasoning=StreamDecoder.decode(SseEvent("reasoning.available","""{"text":"hidden"}"""))
        assertNull(reasoning.activity);assertEquals("",reasoning.delta)
    }
    @Test fun resumePassesTheSavedEventCursorAndDetectsReplayGaps()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody("""id: 8
data: {"event":"message.delta","delta":"next","seq":8}


"""))
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/test/").toString(),"fixture",true),true)
            val rows=api.stream("v1/runs/run_1/events",lastEventId=7).toList()
            assertEquals("7",server.takeRequest().getHeader("Last-Event-ID"));assertEquals(8L,rows.single().sequence)
            assertTrue(StreamDecoder.decode(SseEvent("message","""{"event":"replay.truncated"}""")).replayGap)
        }
    }
    @Test fun onDeviceOnlyNeverSilentlyFallsBackToOnlineRecognition() {
        assertEquals(RecognitionMode.UNAVAILABLE,SpeechPolicy.recognition(true,30,true,true))
        assertEquals(RecognitionMode.UNAVAILABLE,SpeechPolicy.recognition(true,36,false,true))
        assertEquals(RecognitionMode.ON_DEVICE,SpeechPolicy.recognition(true,36,true,true))
        assertEquals(RecognitionMode.SYSTEM,SpeechPolicy.recognition(false,36,false,true))
    }
    @Test fun speechChunksPreserveUnicodeAndStayWithinTheEngineLimit() {
        val text="Hello 你好 😀 with accented pīnyīn. ".repeat(12)
        val chunks=SpeechPolicy.chunks(text,17)
        assertEquals(text,chunks.joinToString(""));assertTrue(chunks.all {it.length<=17 && !Character.isHighSurrogate(it.last())})
        val clean=SpeechPolicy.plainText("Read **this** and [the label](https://example.invalid).\n```text\nprivate code\n```")
        assertFalse(clean.contains("private code"));assertFalse(clean.contains("example.invalid"));assertTrue(clean.contains("the label"))
    }
}
