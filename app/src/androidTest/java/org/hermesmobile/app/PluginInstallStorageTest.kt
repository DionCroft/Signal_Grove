package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginInstallStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("plugin-install-fixture","refresh-fixture","password","fixture",4102444800)
    private fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
    private fun exercise(mode:String,catalog:Boolean=true)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger();val tickets=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server ->
            lateinit var target:ManagementTarget
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when(q.path) {
                "/prefix/api/auth/me"->{assertEquals("Bearer plugin-install-fixture",q.getHeader("Authorization"));json(buildJsonObject {put("provider","password");put("user_id","fixture")})}
                "/prefix/api/auth/ws-ticket"->{assertEquals("Bearer plugin-install-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json(buildJsonObject {put("ticket","i".repeat(43));put("ttl_seconds",30)})}
                "/prefix/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val p=j["params"]!!.jsonObject;assertEquals("worker",p.text("profile"))
                        val result=if(j.text("method")=="plugins.catalog.lookup") PluginInstallFixture.catalog(mode=="catalog-changed") else {
                            assertEquals("plugins.manage",j.text("method"));when(p.text("action")) {
                                "list"->PluginInstallFixture.inventory(mode=="duplicate")
                                "inspect"->PluginInstallFixture.inspection(mode=="changed")
                                "install"->{
                                    assertEquals(setOf("profile","action","force","enable","inspected_sha",if(catalog) "catalog_name" else "identifier"),p.keys);assertEquals(JsonPrimitive(false),p["force"]);assertEquals(JsonPrimitive(true),p["enable"]);assertEquals(PluginInstallFixture.pin,p.text("inspected_sha"));assertFalse(p.containsKey("ref"))
                                    assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});writes.incrementAndGet();entered.countDown()
                                    if(mode=="cancel") release.await(10,TimeUnit.SECONDS)
                                    if(mode=="lost") {s.cancel();return}
                                    if(mode=="source-moved") {s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("error",buildJsonObject {put("code",5026);put("message","private-source-error");put("data",buildJsonObject {put("reason","source_moved")})})}.toString());return}
                                    when(mode) {"bad-ack"->JsonObject(PluginInstallFixture.acknowledgement()+mapOf("plugin_name" to JsonPrimitive("other-agent")));"bad-details"->JsonObject(PluginInstallFixture.acknowledgement()+mapOf("activation" to JsonPrimitive("private-invalid-details")));else->PluginInstallFixture.acknowledgement()}
                                }
                                else->error("Unexpected action")
                            }
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->json(JsonObject(emptyMap())).setResponseCode(404)
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val request=PluginInstallFixture.request(catalog);val repo=PluginInstallRepository(db.dao(),CompletableDeferred(Unit))
                if(mode=="cancel") {
                    val job=launch {runCatching {repo.install(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};assertTrue(runCatching {repo.install(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown();assertEquals("unknown",db.dao().unresolvedManagementOperations(target.resourceScope).single().state);assertEquals(1,writes.get())
                } else {
                    val result=runCatching {repo.install(target,request,api,tokens)}
                    if(mode in setOf("changed","catalog-changed","duplicate")) {assertTrue(result.exceptionOrNull() is PluginSourceChanged);assertEquals(0,writes.get());assertTrue(db.dao().unresolvedManagementOperations(target.resourceScope).isEmpty())}
                    else if(mode in setOf("accepted","bad-details","source-moved")) {
                        val outcome=result.getOrThrow();assertEquals("acknowledged",outcome.receipt.state);assertEquals(1,writes.get());assertEquals(mode=="source-moved",outcome.sourceChanged)
                        if(mode=="bad-details") {assertNull(outcome.report);assertTrue(outcome.receipt.resultSummary.contains("\"details_available\":false"))}
                        if(mode=="accepted") {assertNotNull(outcome.report);assertEquals(listOf("fixture_deferred_tool"),outcome.report!!.deferred["tools"]);assertEquals(true,outcome.report.restartRequired);assertFalse(PluginInstallFixture.acknowledgement().containsKey("sha"))}
                        for(secret in listOf("private-install-source","private-install-dependency","private-install-home","private-install-error","private-invalid-details","private-source-error","private-warning-home")) assertFalse(outcome.receipt.toString().contains(secret))
                        assertTrue(db.dao().unresolvedManagementOperations(target.resourceScope).isEmpty())
                    } else {
                        assertTrue(result.isFailure);val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state);assertFalse(receipt.toString().contains("private-install-source"));db.dao().recoverManagementOperations();db.dao().clearChats()
                        assertTrue(runCatching {PluginInstallRepository(db.dao(),CompletableDeferred(Unit)).install(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get());assertEquals(1,tickets.get())
                    }
                }
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun catalogInstallationRetainsAckAndReportedActivationWithoutInventedAckSha()=exercise("accepted")
    @Test fun repositoryInstallationRedactsSourceCredentialsAndDependencyUrls()=exercise("accepted",false)
    @Test fun changedInspectedCommitRefusesBeforeClaimAndInstall()=exercise("changed")
    @Test fun changedCatalogSourceRefusesBeforeClaimAndInstall()=exercise("catalog-changed")
    @Test fun knownDuplicateNameRefusesBeforeClaimAndInstall()=exercise("duplicate")
    @Test fun lostInstallReplyCannotReplayAcrossRecoveryAndChatClear()=exercise("lost",false)
    @Test fun wrongNameAckRemainsUnknownWithoutReplay()=exercise("bad-ack")
    @Test fun malformedOptionalActivationCannotEraseKnownInstallationAck()=exercise("bad-details")
    @Test fun exactSourceMovedRefusalRetainsNegativeAckWithoutClaimingPublication()=exercise("source-moved")
    @Test fun cancellationAndAnotherRequestCannotRepeatTheClaimedInstall()=exercise("cancel")
}
