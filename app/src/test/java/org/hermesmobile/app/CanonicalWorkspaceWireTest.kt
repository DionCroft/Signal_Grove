package org.hermesmobile.app

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalWorkspaceWireTest {
    private class Fixture(val mode:String="normal"):Closeable {
        val server=MockWebServer();val http=OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build()
        val calls=CopyOnWriteArrayList<JsonObject>();val order=CopyOnWriteArrayList<String>()
        val target get()=ManagementTarget("b".repeat(64),server.url("/prefix/").toString(),"default","fixture · owner")
        val home=CanonicalViewerFixtures.home
        init {
            server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(ws:WebSocket,response:Response) {
                    ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"replay_epoch":"ring","session_authority":true}}}""")
                }
                override fun onMessage(ws:WebSocket,text:String) {
                    val q=Json.parseToJsonElement(text).jsonObject;calls+=q
                    fun reply(value:JsonObject)=ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",q["id"]!!);put("result",value)}.toString())
                    when(q["method"]!!.jsonPrimitive.content) {
                        "runtime.describe"->reply(Json.parseToJsonElement("""{"instance_id":"runtime","profile_id":"/fixture/home","authority_epoch":7,"capabilities":["durable-admission-v1","event-replay-v1"],"session_create":{"sources":["cli"],"parameters":["title"]}}""").jsonObject)
                        "session.mutate"->{
                            order+="wire"
                            if(mode=="drop") {ws.close(1000,"fixture");return}
                            val result=buildJsonObject {
                                put("session_id",if(mode=="foreign") "other" else "resolved-history");put("operation","workspace")
                                put("revision",3);put("execution_generation",if(mode=="generation") 3 else 4)
                                put("cwd",if(mode=="relative") "relative" else "/fixture/normalized")
                                put("private_extra","private-sentinel")
                            }
                            reply(result)
                        }
                        else->error("Unexpected fixture request")
                    }
                }
            }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1"))
            server.start()
        }
        fun snapshot()=CanonicalSessionSnapshot(CanonicalViewerFixtures.owner.copy(target=target),"requested","reported","sub",CanonicalViewerFixtures.cursor,2,3,false,2,true,CanonicalWorkspaceDirectory.Reported("/fixture/old"))
        fun review()=CanonicalWorkspaceReview.create(snapshot(),"/fixture/new/../normalized")
        suspend fun connect()=CanonicalGatewayChannel.connect(http,server.url("/prefix/"),"fixture_ticket_0123456789",target,home)
        override fun close() {http.dispatcher.executorService.shutdown();http.connectionPool.evictAll();server.close()}
    }
    @Test fun oneReviewedExactMutationUsesSeparatePrefixScopedSocketAndRecordsAckBeforeReturn()=runBlocking {
        Fixture().use {f->f.connect().use {channel->
            val review=f.review()
            val ack=channel.changeWorkspaceReviewed(review,{f.order+="claim"},{f.order+="ack"})
            assertEquals(listOf("claim","wire","ack"),f.order)
            assertEquals(listOf("runtime.describe","session.mutate"),f.calls.map {it["method"]!!.jsonPrimitive.content})
            val q=f.calls.last()["params"]!!.jsonObject
            assertEquals(review.requestId,q["request_id"]!!.jsonPrimitive.content)
            assertEquals(buildJsonObject {put("cwd",review.requestedDirectory)},q["payload"])
            assertEquals("resolved-history",q["session_id"]!!.jsonPrimitive.content)
            assertEquals("/fixture/normalized",ack.directory.path)
            assertFalse(ack.summary().contains("private-sentinel"));assertFalse(ack.summary().contains("/fixture"))
            assertFalse(review.summary().contains("/fixture"));assertFalse(ack.toString().contains("/fixture"))
            val handshake=f.server.takeRequest();assertEquals("/prefix/api/ws",handshake.path)
            assertNull(handshake.getHeader("Authorization"));assertTrue(handshake.getHeader("Sec-WebSocket-Protocol")!!.contains("hermes-gateway-ticket."))
        }}
    }
    @Test fun changedRuntimeIdentityCannotClaimOrSend()=runBlocking {
        Fixture().use {f->f.connect().use {channel->
            val s=f.snapshot();val changed=CanonicalSessionSnapshot(s.owner.copy(instanceId="other"),s.requestedSessionId,s.reportedStoredId,s.subscriptionId,s.cursor,2,3,false,2,true,s.workspace)
            val r=CanonicalWorkspaceReview.create(changed,"/new")
            assertTrue(runCatching {channel.changeWorkspaceReviewed(r,{f.order+="claim"},{f.order+="ack"})}.exceptionOrNull() is CanonicalReviewChanged)
            assertTrue(f.order.isEmpty());assertEquals(1,f.calls.size)
        }}
    }
    @Test fun aFailedDurableClaimCannotReachTheMutationAndTheReviewCannotBeReused()=runBlocking {
        Fixture().use {f->f.connect().use {channel->
            val r=f.review()
            assertTrue(runCatching {channel.changeWorkspaceReviewed(r,{throw ManagementOutcomePending()},{error("Unexpected ACK")})}.exceptionOrNull() is ManagementOutcomePending)
            assertTrue(runCatching {channel.changeWorkspaceReviewed(r,{f.order+="claim"},{error("Unexpected ACK")})}.exceptionOrNull() is CanonicalApprovalUsed)
            assertTrue(f.order.isEmpty());assertEquals(1,f.calls.size)
        }}
    }
    @Test fun droppedOrMalformedAckNeverRetriesOrCallsAcknowledgement()=runBlocking {
        for(mode in listOf("drop","foreign","generation","relative")) Fixture(mode).use {f->f.connect().use {channel->
            assertTrue(runCatching {channel.changeWorkspaceReviewed(f.review(),{f.order+="claim"},{f.order+="ack"})}.isFailure)
            assertEquals(listOf("claim","wire"),f.order);assertEquals(1,f.calls.count {it["method"]==JsonPrimitive("session.mutate")})
        }}
    }
    @Test fun cancellationDuringClaimPreventsSocketSendAndConsumesReview()=runBlocking {
        Fixture().use {f->f.connect().use {channel->
            val r=f.review();val entered=CompletableDeferred<Unit>()
            val job=launch {channel.changeWorkspaceReviewed(r,{entered.complete(Unit);awaitCancellation()},{error("Unexpected ACK")})}
            entered.await();job.cancelAndJoin()
            assertTrue(runCatching {channel.changeWorkspaceReviewed(r,{f.order+="claim"},{error("Unexpected ACK")})}.exceptionOrNull() is CanonicalApprovalUsed)
            assertEquals(1,f.calls.size);assertTrue(f.order.isEmpty())
        }}
    }
}
