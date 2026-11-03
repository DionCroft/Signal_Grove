package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginRemovalStorageTest {
    private val tokens=ManagementTokens("removal-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build();val writes=AtomicInteger();val reads=AtomicInteger();val profiles=AtomicInteger();val tickets=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server ->
            lateinit var target:ManagementTarget
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when(q.path) {
                "/prefix/api/auth/me"->{assertEquals("Bearer removal-management-fixture",q.getHeader("Authorization"));json(buildJsonObject {put("provider","password");put("user_id","fixture")})}
                "/prefix/api/profiles"->{assertEquals("Bearer removal-management-fixture",q.getHeader("Authorization"));val n=profiles.incrementAndGet();if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)};json(PluginRemovalFixture.profiles(mode=="home-changed" || mode=="readback-home-changed" && n==2))}
                "/prefix/api/auth/ws-ticket"->{assertEquals("Bearer removal-management-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json(buildJsonObject {put("ticket","r".repeat(43));put("ttl_seconds",30)})}
                "/prefix/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val p=j["params"]!!.jsonObject;assertEquals("plugins.manage",j.text("method"));assertEquals("worker",p.text("profile"))
                        val result=when(p.text("action")) {
                            "list"->{val n=reads.incrementAndGet();PluginRemovalFixture.inventory(when {mode=="absent"->"absent";mode=="absent-fallback"->"fallback";n==1 && mode in setOf("changed","outside","collision")->mode;n==1 || mode=="readback"->"before";mode=="fallback"->"fallback";else->"absent"})}
                            "remove"->{
                                assertEquals(setOf("profile","action","name"),p.keys);assertEquals(PluginRemovalFixture.install,p.text("name"));assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});writes.incrementAndGet()
                                if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                                if(mode=="lost") {s.cancel();return}
                                buildJsonObject {put("ok",true);put("name",if(mode=="bad-ack") PluginRemovalFixture.key else PluginRemovalFixture.install);put("cleared_memory_provider",if(mode=="bad-details") JsonPrimitive("true") else JsonPrimitive(true));put("private_path","/private-removal-result");put("error","private-removal-error")}
                            }
                            else->error("Unexpected action")
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->MockResponse().setResponseCode(404)
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val request=PluginRemovalFixture.request();val repo=PluginRemovalRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel","cancel-readback")) {
                    val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))}
                    if(mode=="cancel") assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                    job.cancelAndJoin();release.countDown();assertEquals(if(mode=="cancel") "unknown" else "acknowledged",db.dao().managementOperations(target.apiScope).first().single().state)
                } else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "changed","outside","collision","home-changed"->{assertTrue(result.exceptionOrNull() is PluginRemovalChanged);assertEquals(0,writes.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode=="home-changed") assertEquals(0,tickets.get());return@use}
                        "absent","absent-fallback"->{val out=result.getOrThrow();assertTrue(out.observedOnly);assertNull(out.receipt);assertEquals(0,writes.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());return@use}
                        "lost","bad-ack"->{assertTrue(result.exceptionOrNull() is PluginRemovalUncertain);assertEquals("unknown",db.dao().unresolvedManagementOperations(target.resourceScope).single().state);assertEquals(1,reads.get())}
                        "readback","readback-home-changed"->{assertTrue(result.exceptionOrNull() is PluginRemovalReadbackPending);assertEquals("acknowledged",db.dao().managementOperations(target.apiScope).first().single().state);if(mode=="readback-home-changed") assertEquals(1,reads.get())}
                        else->{val out=result.getOrThrow();assertEquals("confirmed",out.receipt!!.state);assertTrue(request.observed(out.inventory));val summary=Json.parseToJsonElement(out.receipt.resultSummary).jsonObject;assertEquals(if(mode=="bad-details") JsonNull else JsonPrimitive(true),summary["memory_provider_cleared"])}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipt=db.dao().managementOperations(target.apiScope).first().single();for(secret in listOf("private-removal-profile","private-removal-result","private-removal-error")) assertFalse(receipt.toString().contains(secret));assertEquals(1,writes.get());assertEquals(1,tickets.get())
                if(receipt.state=="unknown") {assertTrue(runCatching {PluginRemovalRepository(db.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get());assertEquals(1,tickets.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun removalIsClaimedOnceAndMemoryResetReportedWithoutFileErasureProof()=exercise("ok")
    @Test fun bundledFallbackCanRemainAfterTheUserInstallDisappears()=exercise("fallback")
    @Test fun changedVersionRefusesBeforeClaimOrRemove()=exercise("changed")
    @Test fun changedProfileHomeRefusesBeforeTicketOrRemove()=exercise("home-changed")
    @Test fun outsideDirectoryCannotMatchTheFrozenReview()=exercise("outside")
    @Test fun knownBundledNameCollisionRefusesBeforeRemove()=exercise("collision")
    @Test fun absentInstallProducesObservationWithoutMutationOrReceipt()=exercise("absent")
    @Test fun existingBundledFallbackProducesObservationWithoutRemoval()=exercise("absent-fallback")
    @Test fun lostReplySurvivesRecoveryAndChatClearWithoutReplay()=exercise("lost")
    @Test fun wrongAcknowledgementNameIsUncertainWithoutReadback()=exercise("bad-ack")
    @Test fun staleInventoryCannotEraseAcknowledgement()=exercise("readback")
    @Test fun changedReadbackHomePreservesAckAndDoesNotQueryThatHome()=exercise("readback-home-changed")
    @Test fun malformedOptionalFlagCannotEraseMandatoryAcknowledgement()=exercise("bad-details")
    @Test fun cancellationAndSecondAttemptNeverSendAnotherRemove()=exercise("cancel")
    @Test fun cancellationDuringReadbackRetainsTheCapturedAcknowledgement()=exercise("cancel-readback")
}
