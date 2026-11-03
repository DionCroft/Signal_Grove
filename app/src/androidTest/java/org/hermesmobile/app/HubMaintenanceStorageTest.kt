package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HubMaintenanceStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("hub-maintenance-fixture","refresh-fixture","password","fixture",4102444800)
    private fun json(j:JsonElement)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
    private fun sources(changed:Boolean=false)=JsonObject(SkillHubFixture.sources()+mapOf("installed" to buildJsonObject {
        put("https://skills.example/fixture?key=private-maintenance-fixture",buildJsonObject {put("name","fixture-skill");put("trust_level",if(changed) "trusted" else "community");put("scan_verdict","safe")})
    }))
    private fun exercise(action:HubMaintenanceAction,mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server ->
            lateinit var target:ManagementTarget
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer hub-maintenance-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"))
                return when(q.requestUrl!!.encodedPath) {
                    "/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture")})
                    "/dashboard/api/skills/hub/sources"->{assertEquals("worker",q.requestUrl!!.queryParameter("profile"));json(sources(mode=="changed"))}
                    "/dashboard/api/skills/hub/uninstall","/dashboard/api/skills/hub/update"->{
                        assertEquals("POST",q.method);assertEquals("worker",q.requestUrl!!.queryParameter("profile"));val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                        assertEquals(if(action==HubMaintenanceAction.Remove) setOf("name","profile") else setOf("profile"),body.keys);assertEquals("worker",body.text("profile"));if(action==HubMaintenanceAction.Remove) assertEquals("fixture-skill",body.text("name"))
                        assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});writes.incrementAndGet();entered.countDown()
                        if(mode=="cancel") release.await(10,TimeUnit.SECONDS)
                        if(mode=="lost") MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                        else json(buildJsonObject {put("ok",true);put("pid",777);put("name",if(mode=="bad-ack") "other-action" else if(action==HubMaintenanceAction.Remove) HubInstallRequest.actionName("fixture-skill","uninstall") else "skills-update")})
                    }
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }}
            try {
                val api=ManagementClient(server.url("dashboard/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val q=HubMaintenanceRequest(action,HubInstalledInventory.from(SkillHubProtocol.sources(sources())),if(action==HubMaintenanceAction.Remove) "fixture-skill" else "");val repo=HubMaintenanceRepository(db.dao(),CompletableDeferred(Unit))
                if(mode=="cancel") {
                    val job=launch {runCatching {repo.execute(target,q,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))}
                    assertTrue(runCatching {repo.execute(target,q,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown();assertEquals("unknown",db.dao().unresolvedManagementOperations(target.resourceScope).single().state);assertEquals(1,writes.get())
                } else {
                    val result=runCatching {repo.execute(target,q,api,tokens)}
                    if(mode=="changed") {assertTrue(result.exceptionOrNull() is SkillHubChanged);assertEquals(0,writes.get());assertTrue(db.dao().unresolvedManagementOperations(target.resourceScope).isEmpty())}
                    else if(mode=="accepted") {val receipt=result.getOrThrow();assertEquals("acknowledged",receipt.state);assertFalse(receipt.resultSummary.contains("777"));assertFalse(receipt.toString().contains("private-maintenance-fixture"));assertEquals(1,writes.get())}
                    else {
                        assertTrue(result.isFailure);val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state);assertFalse(receipt.toString().contains("private-maintenance-fixture"));assertFalse(receipt.toString().contains("skills-update"))
                        db.dao().recoverManagementOperations();db.dao().clearChats();assertTrue(runCatching {HubMaintenanceRepository(db.dao(),CompletableDeferred(Unit)).execute(target,q,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get())
                    }
                }
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun removalAcknowledgesOnlyStartup()=exercise(HubMaintenanceAction.Remove,"accepted")
    @Test fun bulkUpdateAcknowledgesOnlyStartup()=exercise(HubMaintenanceAction.UpdateAll,"accepted")
    @Test fun changedRemovalRecordRefusesBeforeClaimOrPost()=exercise(HubMaintenanceAction.Remove,"changed")
    @Test fun changedBulkInventoryRefusesBeforeClaimOrPost()=exercise(HubMaintenanceAction.UpdateAll,"changed")
    @Test fun lostRemovalReplyRetainsRedactedUncertaintyAcrossRecovery()=exercise(HubMaintenanceAction.Remove,"lost")
    @Test fun lostBulkReplyRetainsRedactedUncertaintyAcrossRecovery()=exercise(HubMaintenanceAction.UpdateAll,"lost")
    @Test fun wrongBulkActionReplyRemainsUnknownWithoutReplay()=exercise(HubMaintenanceAction.UpdateAll,"bad-ack")
    @Test fun cancellationAndAnotherMaintenanceRequestCannotReplayClaimedRemoval()=exercise(HubMaintenanceAction.Remove,"cancel")
}
