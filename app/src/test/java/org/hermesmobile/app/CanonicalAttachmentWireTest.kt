package org.hermesmobile.app

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalAttachmentWireTest {
    private class Fixture(val mode:String="normal"):Closeable {
        val server=MockWebServer()
        val http=OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).build()
        val calls=CopyOnWriteArrayList<JsonObject>()
        @Volatile var peer:WebSocket?=null
        private var catalogs=0
        private val worker = "11111111-2222-4333-8444-555555555555"
        val target get()=ManagementTarget("b".repeat(64),server.url("/prefix/").toString(),"default","fixture · owner")
        val home=checkNotNull(PluginHome.project(JsonPrimitive("/fixture/home"))).identity()
        private fun event(seq:Long,session:String="history",text:String="event-"+seq)=buildJsonObject {
            put("jsonrpc","2.0");put("method","event");put("params",buildJsonObject {
                put("type","status.update");put("session_id",session);put("replay_epoch","ring");put("seq",seq)
                put("authority_epoch",7);put("payload",buildJsonObject {put("text",text)})
            })
        }
        fun send(seq:Long)=checkNotNull(peer).send(event(seq).toString())
        init {
            server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(ws:WebSocket,response:Response) {
                    peer=ws
                    ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"replay_epoch":"ring","session_authority":true}}}""")
                }
                override fun onMessage(ws:WebSocket,text:String) {
                    val q=Json.parseToJsonElement(text).jsonObject;calls+=q
                    fun reply(result:JsonElement)=ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",q["id"]!!);put("result",result)}.toString())
                    when(q["method"]!!.jsonPrimitive.content) {
                        "runtime.describe"->reply(Json.parseToJsonElement("""{"instance_id":"instance","profile_id":"/fixture/home","authority_epoch":7,"capabilities":["durable-admission-v1","event-replay-v1"],"session_create":{"sources":["cli"],"parameters":["title"]}}"""))
                        "session.list"->{
                            catalogs++
                            val changed=mode=="stale-row" && catalogs>1
                            reply(buildJsonObject {put("scope",q["params"]!!.jsonObject["scope"]!!);put("sessions",buildJsonArray {add(buildJsonObject {
                                put("id","history");put("session_id","history");put("resolved_id","history");put("title","Fixture history")
                                put("source","cli");put("started_at",1.0);put("message_count",if(mode=="activity-changed" && catalogs>1) 3 else 2)
                                put("running",mode=="activity-changed" && catalogs>1);put("model",if(changed) "changed-model" else "fixture-model")
                            })})})
                        }
                        "session.resume"->{
                            if(mode=="resume-drop") {ws.close(1000,"fixture-drop");return}
                            if(mode=="host-empty") ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"sessions.changed","session_id":"","payload":{}}}""")
                            if(mode=="foreign") ws.send(event(2,"foreign").toString())
                            else if(mode=="covered-conflict") {ws.send(event(1).toString());ws.send(event(1,text="conflict").toString())}
                            else {ws.send(event(1).toString());ws.send(event(2).toString())}
                            reply(buildJsonObject {
                                put("session_id","history");put("stored_session_id","reported-history");put("messages",JsonArray(emptyList()));put("messages_omitted",true)
                                put("message_count",2);put("running",false);put("authority_epoch",7);put("replay_epoch","ring")
                                put("last_sequence",if(mode=="checkpoint") 3 else 1);put("subscription_id","subscription");put("revision",2);put("execution_generation",0)
                                put("pending",JsonArray(emptyList()));put("prompts",JsonArray(emptyList()))
                                put("info",buildJsonObject {put("desktop_protocol","hermes-gateway-v1")})
                            })
                        }
                        "session.events.since"->{
                            if(mode=="replay-drop") {ws.close(1000,"fixture-drop");return}
                            if(mode=="invalid-reply") {reply(JsonArray(emptyList()));return}
                            ws.send(event(3).toString())
                            if(mode=="covered-late") ws.send(event(1).toString())
                            reply(buildJsonObject {
                                put("events",JsonArray(listOf(event(2)["params"]!!,event(3)["params"]!!)))
                                put("latest_seq",3);put("last_sequence",3);put("epoch","ring");put("replay_epoch","ring")
                                put("truncated",mode=="truncated");put("snapshot_required",mode=="truncated");put("count",2)
                            })
                        }
                        SharedChatProtocol.Submit, SharedChatProtocol.Receipt -> {
                            val lookup = q["method"] == JsonPrimitive(SharedChatProtocol.Receipt)
                            val ref = if (mode == "foreign-prompt") "other-chat" else "history"
                            reply(buildJsonObject {
                                put("admission_id", "admission-1")
                                put("ref", buildJsonObject { put("profile_id", "/fixture/home"); put("session_id", ref) })
                                put("authority_epoch", 7)
                                put("sequence", 1)
                                put("status", if (lookup) "terminal" else "queued")
                                put("execution_generation", if (lookup) JsonPrimitive(2) else JsonNull)
                                put("outcome", if (lookup) JsonPrimitive("completed") else JsonNull)
                                if (lookup) put("result", buildJsonObject {
                                    put("final_response", "Fixture reply")
                                    put("reasoning", "private")
                                })
                            })
                        }
                        SharedChatProtocol.Respond -> {
                            val params = q.getValue("params").jsonObject
                            reply(buildJsonObject {
                                put("prompt_id", params.getValue("prompt_id"))
                                put("status", if (mode == "approval-already") "already_resolved" else "resolved")
                            })
                        }
                        AttachedWorkerProtocol.Describe -> {
                            if (mode == "worker-unsupported") {
                                ws.send(buildJsonObject {
                                    put("jsonrpc", "2.0")
                                    put("id", q.getValue("id"))
                                    put("error", buildJsonObject { put("code", -32601); put("message", "unknown_method") })
                                }.toString())
                            } else {
                                reply(Json.parseToJsonElement("""{"schema":1,"state":"ok","payload":{"schema":1,"device":"FIXTURE-WORKER","state":"ready","worker_instance":"$worker","failure_code":null,"project_ids":["fixture-project"],"operations":["prepare","list","preview"],"root":"E:\\work\\fixture"}}"""))
                            }
                        }
                        AttachedWorkerProtocol.Read -> {
                            val req = q.getValue("params").jsonObject.getValue("request").jsonObject
                            val operation = req.getValue("operation").jsonPrimitive.content
                            val result = when (operation) {
                                "prepare" -> Json.parseToJsonElement("""{"schema":1,"project_id":"fixture-project","worker":{"device":"FIXTURE-WORKER","instance_id":"$worker"},"root":"E:\\work\\${if (mode == "foreign-root") "wrong" else "fixture"}","binding_token":"${"b".repeat(64)}","expires_in_seconds":297}""")
                                "list" -> Json.parseToJsonElement("""{"schema":1,"worker_instance":"$worker","project_id":"fixture-project","relative_path":"","truncated":false,"excluded_entries":0,"entries":[{"name":"note.txt","relative_path":"${if (mode == "foreign-listing") "elsewhere/" else ""}note.txt","kind":"file","byte_size":6,"entry_token":"${"c".repeat(64)}"}]}""")
                                "preview" -> Json.parseToJsonElement("""{"schema":1,"worker_instance":"$worker","project_id":"fixture-project","relative_path":"note.txt","byte_size":6,"preview_bytes":6,"preview_sha256":"${"d".repeat(64)}","binary":false,"text":"hello\n","truncated":false}""")
                                else -> error("Unexpected worker RPC")
                            }
                            reply(buildJsonObject {
                                put("schema", 1)
                                put("state", "ok")
                                put("payload", buildJsonObject {
                                    put("schema", 1)
                                    put("request_id", req.getValue("request_id"))
                                    put("worker_instance", worker)
                                    put("status", "ok")
                                    put("result", result)
                                })
                            })
                        }
                        else->ws.close(1011,"unreviewed-fixture-rpc")
                    }
                }
            }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1"))
        }
        suspend fun connect()=CanonicalGatewayChannel.connect(http,server.url("/prefix/"),"t".repeat(43),target,home)
        override fun close() {server.close();http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()}
    }

    private fun success(mode:String,scope:CanonicalSessionScope=CanonicalSessionScope.Stored)=runBlocking {
        Fixture(mode).use {f->withTimeout(6000) {
            val channel=f.connect()
            try {
                val inventory=channel.sessionInventory(scope);val review=inventory.review("history")
                var claimed=0;var acknowledged=0
                val previous=if(mode=="checkpoint") CanonicalReplayCheckpoint(
                    CanonicalFeedOwner(f.target,f.home,"/fixture/home","instance","history",7),CanonicalReplayCursor("ring",1)) else null
                val attached=channel.resumeReviewed(review,previous,beforeSend={claimed++},acknowledged={acknowledged++})
                assertEquals(1,claimed);assertEquals(1,acknowledged)
                assertEquals(listOf(2L,3L),attached.initial.events.map {it.sequence})
                assertEquals(CanonicalFeedPhase.Following,attached.initial.status.phase)
                assertEquals(3L,attached.initial.status.cursor.sequence)
                assertEquals("history",attached.snapshot.owner.sessionId);assertEquals("reported-history",attached.snapshot.reportedStoredId)
                assertFalse(attached.snapshot.reportedRunning)
                f.send(4)
                assertEquals(listOf(4L),channel.observations().first().events.map {it.sequence})
                assertTrue(runCatching {channel.observations()}.isFailure)
                val methods=f.calls.map {it["method"]!!.jsonPrimitive.content}
                assertEquals(listOf("runtime.describe","session.list","session.list","session.resume","session.events.since"),methods)
                assertEquals(buildJsonObject {put("session_id","history");put("omit_messages",true)},f.calls[3]["params"])
                assertEquals(JsonPrimitive(1),f.calls[4]["params"]!!.jsonObject["last_sequence"])
            } finally {channel.close()}
        }}
    }
    private fun refused(mode:String,acknowledgedExpected:Int)=runBlocking {
        Fixture(mode).use {f->withTimeout(6000) {
            val channel=f.connect()
            try {
                val review=channel.sessionInventory().review("history");var claimed=0;var acknowledged=0
                val error=runCatching {channel.resumeReviewed(review,beforeSend={claimed++},acknowledged={acknowledged++})}.exceptionOrNull()
                assertNotNull(error);assertFalse("The failure must settle promptly, not by the outer deadline.",error is TimeoutCancellationException)
                assertEquals(1,claimed);assertEquals(acknowledgedExpected,acknowledged)
                assertEquals(1,f.calls.count {it["method"]==JsonPrimitive("session.resume")})
                assertTrue(f.calls.none {it["method"] in listOf(JsonPrimitive("prompt.submit"),JsonPrimitive("session.interrupt"),JsonPrimitive("approval.respond"))})
            } finally {channel.close()}
        }}
    }
    @Test fun sharedChatUsesOneAttachedSocketAndReadsTheExactReceipt() = runBlocking {
        Fixture().use { f -> withTimeout(6000) {
            f.connect().use { channel ->
                val snapshot = channel.resumeReviewed(channel.sessionInventory().review("history"),
                    beforeSend = {}, acknowledged = {}).snapshot
                val prompt = channel.submitPrompt(snapshot, "test-submission", "Continue the original chat")
                assertEquals(SharedPromptStatus.Queued, prompt?.status)
                assertTrue(prompt?.reply.isNullOrEmpty())
                val final = channel.promptReceipt(snapshot, "admission-1")
                assertEquals(SharedPromptStatus.Terminal, final.status)
                assertEquals("completed", final.outcome)
                assertEquals("Fixture reply", final.reply)
                val approval = SharedApproval("prompt-1", 2, "fixture read", "Read fixture", setOf("once", "deny"))
                assertTrue(channel.answerApproval(snapshot, approval, "once"))
                val calls = f.calls.takeLast(3).map { it.getValue("params").jsonObject }
                assertEquals(Json.parseToJsonElement("""{"session_id":"history","submission_id":"test-submission","text":"Continue the original chat"}"""), calls[0])
                assertEquals(Json.parseToJsonElement("""{"session_id":"history","admission_id":"admission-1","include_result":true}"""), calls[1])
                assertEquals(Json.parseToJsonElement("""{"session_id":"history","prompt_id":"prompt-1","execution_generation":2,"choice":"once"}"""), calls[2])
                assertTrue(runCatching { channel.answerApproval(snapshot, approval, "always") }.isFailure)
                assertEquals(1, f.calls.count { it["method"] == JsonPrimitive(SharedChatProtocol.Respond) })
            }
        }}
    }

    @Test fun sharedChatRejectsAnAdmissionForADifferentChat() = runBlocking {
        Fixture("foreign-prompt").use { f -> withTimeout(6000) {
            f.connect().use { channel ->
                val snapshot = channel.resumeReviewed(channel.sessionInventory().review("history"),
                    beforeSend = {}, acknowledged = {}).snapshot
                assertTrue(runCatching { channel.submitPrompt(snapshot, "once", "Fixture") }.isFailure)
                assertEquals(1, f.calls.count { it["method"] == JsonPrimitive(SharedChatProtocol.Submit) })
                assertTrue(f.calls.none { it["method"] in listOf(JsonPrimitive("session.create"), JsonPrimitive(SharedChatProtocol.Receipt)) })
            }
        }}
    }

    @Test fun attachedProjectReadsUseTheSameFollowedSocketAndOnlyTheReviewedReference() = runBlocking {
        Fixture("worker-ready").use { f -> withTimeout(6000) {
            f.connect().use { channel ->
                val attached = channel.resumeReviewed(channel.sessionInventory().review("history"),
                    beforeSend = {}, acknowledged = {})
                val project = channel.describeWorker(attached.snapshot)
                assertEquals("FIXTURE-WORKER", project.device)
                val binding = channel.prepareWorker(attached.snapshot, project)
                val listing = channel.listWorker(attached.snapshot, binding, null)
                val entry = listing.entries.single()
                assertEquals("note.txt", entry.path)
                assertEquals("hello\n", channel.previewWorker(attached.snapshot, binding, entry).text)
                f.send(4)
                assertEquals(4L, channel.observations().first().status.cursor.sequence)

                val calls = f.calls.filter { it["method"] == JsonPrimitive(AttachedWorkerProtocol.Describe) ||
                    it["method"] == JsonPrimitive(AttachedWorkerProtocol.Read) }.map { it.getValue("params").jsonObject }
                assertEquals(4, calls.size)
                assertEquals(setOf("attached_session_id", "expected_revision", "expected_generation"), calls.first().keys)
                calls.forEach {
                    assertEquals(JsonPrimitive("history"), it["attached_session_id"])
                    assertEquals(JsonPrimitive(2), it["expected_revision"])
                    assertEquals(JsonPrimitive(0), it["expected_generation"])
                    assertFalse(it.keys.any { key -> key in setOf("session_id", "worker_root", "profile", "device") })
                }
                val requests = calls.drop(1).map { it.getValue("request").jsonObject }
                assertEquals(listOf("prepare", "list", "preview"), requests.map { it.getValue("operation").jsonPrimitive.content })
                assertEquals(JsonNull, requests[1]["entry_token"])
                assertEquals(JsonPrimitive("c".repeat(64)), requests[2]["entry_token"])
                assertEquals(3, requests.map { it["request_id"] }.toSet().size)
                assertEquals(1, f.calls.count { it["method"] == JsonPrimitive("session.resume") })
            }
        } }
    }

    @Test fun unsupportedProjectReadKeepsTheOriginalSubscription() = runBlocking {
        Fixture("worker-unsupported").use { f -> withTimeout(6000) {
            f.connect().use { channel ->
                val attached = channel.resumeReviewed(channel.sessionInventory().review("history"),
                    beforeSend = {}, acknowledged = {})
                assertTrue(runCatching { channel.describeWorker(attached.snapshot) }.exceptionOrNull() is CanonicalNotSupported)
                f.send(4)
                assertEquals(4L, channel.observations().first().status.cursor.sequence)
                assertEquals(1, f.calls.count { it["method"] == JsonPrimitive("session.resume") })
            }
        } }
    }

    @Test fun attachedProjectReadDiscardsUnreviewedRootsAndForeignListingEntries() = runBlocking {
        for (mode in listOf("foreign-root", "foreign-listing")) {
            Fixture(mode).use { f -> withTimeout(6000) {
                f.connect().use { channel ->
                    val attached = channel.resumeReviewed(channel.sessionInventory().review("history"),
                        beforeSend = {}, acknowledged = {})
                    val project = channel.describeWorker(attached.snapshot)
                    val error = runCatching {
                        val binding = channel.prepareWorker(attached.snapshot, project)
                        channel.listWorker(attached.snapshot, binding, null)
                    }.exceptionOrNull()
                    assertTrue(error is GatewayProtocolException)
                    f.send(4)
                    assertEquals(4L, channel.observations().first().status.cursor.sequence)
                }
            } }
        }
    }

    @Test fun attachFirstReplayMergesRacingLiveOverlapExactlyOnce()=success("normal")
    @Test fun actualEmptyScopeHostChangeDoesNotBecomeATranscriptEvent()=success("host-empty")
    @Test fun snapshotCoveredLateDuplicateDoesNotCreateAFalseGap()=success("covered-late")
    @Test fun approvedCheckpointRecoversTheGapBeforeTheNewSnapshotWatermark()=success("checkpoint")
    @Test fun liveEligibilityUsesTheExplicitLiveScope()=success("normal",CanonicalSessionScope.Live)
    @Test fun changingActivityCountsDoNotBlockAViewerAttachment()=success("activity-changed")
    @Test fun lostResumeReplyDoesNotProduceAnAcknowledgementOrReplay()=refused("resume-drop",0)
    @Test fun lostReplayReplyPreservesTheKnownResumeAcknowledgement()=refused("replay-drop",1)
    @Test fun malformedReplySettlesItsWaiterWithoutWaitingForTimeout()=refused("invalid-reply",1)
    @Test fun truncatedReplayHoldsTheViewAfterKnownAdoption()=refused("truncated",1)
    @Test fun foreignEarlyFrameCannotEnterTheAcceptedOwnerView()=refused("foreign",1)
    @Test fun conflictingCoveredFramesHoldTheViewAfterKnownAdoption()=refused("covered-conflict",1)
    @Test fun changedReviewRefusesBeforeDurableClaimAndResume()=runBlocking {
        Fixture("stale-row").use {f->withTimeout(6000) {
            val channel=f.connect()
            try {
                val review=channel.sessionInventory().review("history");var claimed=0
                val error=runCatching {channel.resumeReviewed(review,beforeSend={claimed++},acknowledged={error("No ACK expected")})}.exceptionOrNull()
                assertTrue(error is CanonicalReviewChanged);assertEquals(0,claimed)
                assertTrue(f.calls.none {it["method"]==JsonPrimitive("session.resume")})
            } finally {channel.close()}
        }}
    }
    @Test fun foreignCheckpointRefusesBeforeClaimAndResume()=runBlocking {
        Fixture().use {f->withTimeout(6000) {
            val channel=f.connect()
            try {
                val review=channel.sessionInventory().review("history");var claimed=0
                val checkpoint=CanonicalReplayCheckpoint(CanonicalFeedOwner(f.target.copy(actor="foreign"),f.home,"/fixture/home","instance","history",7),CanonicalReplayCursor("ring",1))
                val error=runCatching {channel.resumeReviewed(review,checkpoint,beforeSend={claimed++},acknowledged={error("No ACK expected")})}.exceptionOrNull()
                assertTrue(error is CanonicalReviewChanged);assertEquals(0,claimed)
                assertTrue(f.calls.none {it["method"]==JsonPrimitive("session.resume")})
            } finally {channel.close()}
        }}
    }
    @Test fun failedDurableClaimCannotSendAnAdoption()=runBlocking {
        Fixture().use {f->withTimeout(6000) {
            val channel=f.connect()
            try {
                val review=channel.sessionInventory().review("history")
                assertTrue(runCatching {channel.resumeReviewed(review,beforeSend={throw ManagementOutcomePending()},acknowledged={error("No ACK expected")})}.exceptionOrNull() is ManagementOutcomePending)
                assertTrue(f.calls.none {it["method"]==JsonPrimitive("session.resume")})
            } finally {channel.close()}
        }}
    }
}
