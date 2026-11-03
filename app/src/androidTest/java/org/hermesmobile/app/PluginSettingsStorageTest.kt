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

class PluginSettingsStorageTest {
    private val tokens=ManagementTokens("settings-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build();val sends=AtomicInteger();val reads=AtomicInteger();val tickets=AtomicInteger();val profiles=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            lateinit var target:ManagementTarget
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when(q.path) {
                "/prefix/api/auth/me"->{assertEquals("Bearer settings-management-fixture",q.getHeader("Authorization"));json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})}
                "/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(PluginUpdateFixture.profiles(mode=="home" || n>1 && mode=="readback-home"))}
                "/prefix/api/auth/ws-ticket"->{assertEquals("Bearer settings-management-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json(buildJsonObject {put("ticket","s".repeat(43));put("ttl_seconds",30)})}
                "/prefix/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));assertEquals(listOf("hermes-gateway-v1","hermes-gateway-ticket."+"s".repeat(43)),q.getHeader("Sec-WebSocket-Protocol").orEmpty().split(',').map {it.trim()});MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val params=j["params"]!!.jsonObject;assertEquals("plugins.manage",j.text("method"));assertEquals("worker",params.text("profile"))
                        val result=when(params.text("action")) {
                            "list"->{val n=reads.incrementAndGet();if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)};PluginSettingsFixture.inventory(mode,n>1)}
                            "settings"->{assertEquals(setOf("profile","action","key","values"),params.keys);assertEquals(PluginUpdateFixture.key,params.text("key"));assertEquals(buildJsonObject {put(PluginSettingsFixture.key(mode),PluginSettingsFixture.value(mode))},params["values"]);assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});sends.incrementAndGet()
                                if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                                if(mode=="lost") {s.cancel();return}
                                if(mode=="denied") {s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("error",buildJsonObject {put("code",4021);put("message","private-management-error")})}.toString());return}
                                buildJsonObject {put("ok",true);put("name",if(mode=="badack") "wrong-key" else PluginUpdateFixture.key);put("written",buildJsonArray {add(if(mode=="wrong-written") "other" else PluginSettingsFixture.key(mode))});put("plugin","private-malformed-optional-row")}
                            }
                            else->error("Unexpected request")
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->MockResponse().setResponseCode(404)
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val initial=PluginSettingsFixture.inventory(if(mode in setOf("changed","schema-changed")) "" else mode);val snapshot=PluginSettingsSnapshot.parse(initial,target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home)),PluginUpdateFixture.key);val field=snapshot.fields.first();val request=PluginSettingsRequest(snapshot,field,PluginSettingsFixture.value(mode));val repo=PluginSettingsRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel","cancel-readback")) {
                    val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};if(mode=="cancel") assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown()
                } else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "changed","schema-changed","home","identity"->{assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode in setOf("home","identity")) assertEquals(0,tickets.get());return@use}
                        "lost","badack","wrong-written","denied"->assertTrue(result.exceptionOrNull() is PluginSettingsUncertain)
                        "readback","readback-home"->assertTrue(result.exceptionOrNull() is PluginSettingsReadbackPending)
                        else->{val out=result.getOrThrow();assertEquals(if(mode=="nested") "acknowledged" else "confirmed",out.receipt.state);if(mode!="nested") assertTrue(request.observed(out.snapshot))}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipt=db.dao().managementOperations(target.apiScope).first().single();val saved=receipt.toString();for(value in listOf(PluginSettingsFixture.privateValue,PluginSettingsFixture.privateDefault,"private-management-error","private-malformed-optional-row","settings-management-fixture")) assertFalse(value,saved.contains(value));assertEquals(1,sends.get());assertEquals(1,tickets.get())
                if(mode=="readback-home") assertEquals(1,reads.get())
                if(mode=="cancel-readback") assertEquals("acknowledged",receipt.state)
                if(receipt.state=="unknown") {assertTrue(runCatching {PluginSettingsRepository(db.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,sends.get());assertEquals(1,tickets.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun stringSaveHasOneTypedClaimedWriteAndSeparateReadback()=exercise("string")
    @Test fun numericZeroStaysANumber()=exercise("number")
    @Test fun falseStaysABoolean()=exercise("boolean")
    @Test fun enumChoiceIsValidatedAndReadBack()=exercise("enum")
    @Test fun jsonValueStaysStructuredAndPrivate()=exercise("json")
    @Test fun dottedWriteIsAcknowledgedWithoutFalseReadbackVerification()=exercise("nested")
    @Test fun changedReportedValueRefusesBeforeClaimOrWrite()=exercise("changed")
    @Test fun changedDeclaredTypeRefusesBeforeClaimOrWrite()=exercise("schema-changed")
    @Test fun changedHomeRefusesBeforeTicket()=exercise("home")
    @Test fun wrongIdentityRefusesBeforeTicket()=exercise("identity")
    @Test fun lostReplyPersistsUncertaintyAcrossRecoveryWithoutReplay()=exercise("lost")
    @Test fun wrongCanonicalAcknowledgementIsUncertain()=exercise("badack")
    @Test fun wrongWrittenKeyIsUncertain()=exercise("wrong-written")
    @Test fun hostErrorRetainsUncertaintyWithoutPrivateMessageOrRetry()=exercise("denied")
    @Test fun missingReadbackPreservesAcknowledgement()=exercise("readback")
    @Test fun changedReadbackHomeKeepsAckAndAvoidsTheNewHome()=exercise("readback-home")
    @Test fun cancellationAndSecondAttemptNeverWriteTwice()=exercise("cancel")
    @Test fun cancellationAfterAcknowledgementCannotEraseIt()=exercise("cancel-readback")
    @Test fun missingSettingsSchemaIsUnavailable()=runBlocking {
        // The same scoped parser rejects absent schema; read transport is exercised by the UI flow.
        val raw=PluginUpdateFixture.inventory();val target=ManagementTarget("a".repeat(64),"https://example.test/","worker","password · fixture")
        assertTrue(runCatching {PluginSettingsSnapshot.parse(raw,target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home)),PluginUpdateFixture.key)}.exceptionOrNull() is PluginSettingsUnavailable)
    }
}
