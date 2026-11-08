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
