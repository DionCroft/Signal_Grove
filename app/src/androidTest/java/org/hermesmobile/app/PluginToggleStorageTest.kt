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

class PluginToggleStorageTest {
    private val tokens=ManagementTokens("toggle-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun inventory(status:String,version:String="1")=buildJsonObject {
        put("plugins",buildJsonArray {add(buildJsonObject {put("name","fixture");put("key","tools/fixture");put("version",version);put("source","user");put("status",status);put("servers",JsonArray(emptyList()))})});put("user_count",1);put("bundled_count",0)
    }
    private fun exercise(mode:String,enable:Boolean=true)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build();val writes=AtomicInteger();val reads=AtomicInteger();val tickets=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server ->
            lateinit var target:ManagementTarget
            val original=if(enable) "disabled" else "enabled";val desired=if(enable) "enabled" else "disabled"
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when(q.path) {
                "/prefix/api/auth/me"->{assertEquals("Bearer toggle-management-fixture",q.getHeader("Authorization"));json(buildJsonObject {put("provider","password");put("user_id","fixture")})}
                "/prefix/api/auth/ws-ticket"->{tickets.incrementAndGet();json(buildJsonObject {put("ticket","t".repeat(43));put("ttl_seconds",30)})}
                "/prefix/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val p=j["params"]!!.jsonObject;assertEquals("plugins.manage",j.text("method"));assertEquals("worker",p.text("profile"))
                        val result=when(p.text("action")) {
                            "list"->{val n=reads.incrementAndGet();inventory(if(n==1 || mode=="readback") original else desired,if(mode=="changed") "2" else "1")}
                            "toggle"->{
                                assertEquals(setOf("profile","action","key","enable"),p.keys);assertEquals("tools/fixture",p.text("key"));assertEquals(JsonPrimitive(enable),p["enable"]);assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});writes.incrementAndGet();entered.countDown()
                                if(mode=="cancel") release.await(10,TimeUnit.SECONDS)
                                if(mode=="lost") {s.cancel();return}
                                buildJsonObject {put("ok",true);put("name",if(mode=="bad-ack") "fixture" else "tools/fixture");put("restart_required",!enable);if(mode=="bad-details") put("activation","private-invalid-details")}
                            }
                            else->error("Unexpected action")
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->MockResponse().setResponseCode(404)
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val row=GatewayPluginInventory.parse(inventory(original),"worker").plugins.single();val request=PluginToggleRequest(row,enable);val repo=PluginToggleRepository(db.dao(),CompletableDeferred(Unit))
                if(mode=="cancel") {
                    val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown();assertEquals("unknown",db.dao().unresolvedManagementOperations(target.resourceScope).single().state)
                } else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "changed"->{assertTrue(result.exceptionOrNull() is PluginToggleChanged);assertEquals(0,writes.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());return@use}
                        "lost","bad-ack"->{assertTrue(result.exceptionOrNull() is PluginToggleOutcomeUncertain);assertEquals("unknown",db.dao().unresolvedManagementOperations(target.resourceScope).single().state)}
                        "readback"->{assertTrue(result.exceptionOrNull() is PluginToggleReadbackPending);assertEquals("acknowledged",db.dao().managementOperations(target.apiScope).first().single().state)}
                        else->{val out=result.getOrThrow();assertEquals("confirmed",out.receipt.state);assertTrue(request.observed(out.inventory));if(mode=="bad-details") assertNull(out.report)}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipt=db.dao().managementOperations(target.apiScope).first().single();assertFalse(receipt.toString().contains("private-invalid-details"));assertEquals(1,writes.get());assertEquals(1,tickets.get())
                if(receipt.state=="unknown") {assertTrue(runCatching {PluginToggleRepository(db.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get());assertEquals(1,tickets.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun enableIsClaimedOnceAndConfirmedOnlyByConfigurationReadback()=exercise("ok")
    @Test fun disableRequiresAnExplicitDisabledConfigurationReport()=exercise("ok",false)
    @Test fun changedVersionRefusesBeforeClaimOrToggle()=exercise("changed")
    @Test fun lostReplySurvivesRecoveryAndChatClearWithoutReplay()=exercise("lost")
    @Test fun bareNameAcknowledgementIsUncertainWithoutReadback()=exercise("bad-ack")
    @Test fun staleReadbackCannotEraseTheCapturedAcknowledgement()=exercise("readback")
    @Test fun malformedOptionalActivationCannotEraseMandatoryAcknowledgement()=exercise("bad-details")
    @Test fun cancellationAndSecondAttemptNeverSendAnotherToggle()=exercise("cancel")
}
