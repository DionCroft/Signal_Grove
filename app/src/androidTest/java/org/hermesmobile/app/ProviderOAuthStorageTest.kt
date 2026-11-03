package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProviderOAuthStorageTest {
    private val tokens=ManagementTokens("provider-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val reads=AtomicInteger();val sends=AtomicInteger();val polls=AtomicInteger();val deletes=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        var display:ProviderDeviceCode?=null
        val codes=object:ProviderCodeStore {override fun save(binding:ProviderOAuthBinding,value:ProviderDeviceCode) {if(mode=="cache-error") error("private-cache-error");if(mode=="cancel-cache") {entered.countDown();release.await(10,TimeUnit.SECONDS)};display=value};override fun read(binding:ProviderOAuthBinding,now:Long)=display;override fun remove(binding:ProviderOAuthBinding) {display=null}}
        MockWebServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer provider-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val u=q.requestUrl!!
                if(u.encodedPath.contains("providers/oauth")) assertEquals("worker",u.queryParameter("profile"))
                return when(u.encodedPath) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->json(if(mode=="home") buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path","/different/home")})})} else PluginUpdateFixture.profiles())
                    "/prefix/api/providers/oauth"->{val n=reads.incrementAndGet();if(mode=="disconnect-readback" && n==2) MockResponse().setResponseCode(500) else json(ProviderOAuthFixture.catalog(mode=="changed"))}
                    "/prefix/api/providers/oauth/fixture-oauth/start"->{assertEquals("POST",q.method);assertEquals("{}",q.body.readUtf8());assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(targetScope).single().state});sends.incrementAndGet()
                        if(mode=="cancel-start") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {"lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);"http"->MockResponse().setResponseCode(422);"redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("wrong"));"badack"->json(JsonObject(ProviderOAuthFixture.start()-"session_id"));"no-display"->json(JsonObject(ProviderOAuthFixture.start()-setOf("user_code","verification_url","expires_in")));else->json(ProviderOAuthFixture.start())}}
                    "/prefix/api/providers/oauth/fixture-oauth/poll/${ProviderOAuthFixture.session}"->{assertEquals("GET",q.method);assertEquals("0",u.queryParameter("wait"));polls.incrementAndGet();if(mode=="cancel-poll") {entered.countDown();release.await(10,TimeUnit.SECONDS)};if(mode=="missing") MockResponse().setResponseCode(404) else json(ProviderOAuthFixture.poll(if(mode=="approved") "approved" else "pending",if(mode=="wrong-id") "different_session_12345" else ProviderOAuthFixture.session))}
                    "/prefix/api/providers/oauth/sessions/${ProviderOAuthFixture.session}"->{assertEquals("DELETE",q.method);deletes.incrementAndGet();when(mode) {"cancel-lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);"not-found"->json(buildJsonObject {put("ok",false);put("message","session not found")});else->json(buildJsonObject {put("ok",true);put("session_id",ProviderOAuthFixture.session)})}}
                    "/prefix/api/providers/oauth/fixture-oauth"->{assertEquals("DELETE",q.method);deletes.incrementAndGet();json(buildJsonObject {put("ok",true);put("provider",if(mode=="disconnect-wrong") "other" else ProviderOAuthFixture.provider)})}
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");targetScope=target.resourceScope
                val expected=ProviderOAuthInventory.parse(ProviderOAuthFixture.catalog(),target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home))!!.identity())
                val action=if(mode.startsWith("disconnect")) ProviderOAuthAction.Disconnect else ProviderOAuthAction.Start
                val request=ProviderOAuthRequest(expected,expected.rows.first(),action);val repo=ProviderOAuthRepository(db.dao(),CompletableDeferred(Unit),codes)
                if(mode in setOf("cancel-start","cancel-cache")) {val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};job.cancel();release.countDown();job.join()}
                else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    if(mode in setOf("home","identity","changed")) {assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode!="changed") assertEquals(0,reads.get());return@use}
                    if(mode in setOf("lost","http","redirect","badack","disconnect-wrong")) assertTrue(result.exceptionOrNull() is ProviderOAuthUncertain)
                    else if(mode=="disconnect-readback") {assertTrue(result.exceptionOrNull() is ProviderOAuthReadbackPending);assertEquals("acknowledged",db.dao().managementOperations(target.apiScope).first().single().state)}
                    else {val receipt=result.getOrThrow();assertEquals("acknowledged",receipt.state)
                        if(action==ProviderOAuthAction.Start) {
                            assertEquals(mode !in setOf("no-display","cache-error"),display!=null)
                            if(mode=="cancel-poll") {val job=launch {runCatching {repo.poll(target,receipt.key,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};job.cancelAndJoin();release.countDown()}
                            else if(mode in setOf("cancel","cancel-lost","not-found")) {val cancel=runCatching {repo.cancel(target,receipt.key,api,tokens)};if(mode=="cancel-lost") assertTrue(cancel.exceptionOrNull() is ProviderOAuthUncertain) else {assertEquals("acknowledged",cancel.getOrThrow().state);assertNull(display)}}
                            else {val out=runCatching {repo.poll(target,receipt.key,api,tokens)};when(mode) {"missing"->{assertTrue(out.exceptionOrNull() is ProviderOAuthUnavailable);assertNull(display)};"wrong-id"->assertTrue(out.isFailure);else->{assertEquals(if(mode=="approved") "approved" else "pending",out.getOrThrow().status);if(mode=="approved") assertNull(display)}}}
                            val rotated=target.copy(actor="password · other");val before=polls.get();assertTrue(runCatching {repo.poll(rotated,receipt.key,api,ManagementTokens("other","other","password","other",4102444800))}.isFailure);assertEquals(before,polls.get())
                        }
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipts=db.dao().managementOperations(target.apiScope).first();assertTrue(receipts.isNotEmpty());assertEquals(0,other.get());assertEquals(if(action==ProviderOAuthAction.Start) 1 else 0,sends.get())
                receipts.forEach {assertFalse(it.toString().contains(ProviderOAuthFixture.code));assertFalse(it.toString().contains("private"));assertFalse(it.toString().contains("provider-management-fixture"))}
                if(mode in setOf("cancel-poll","cancel-cache")) assertEquals("acknowledged",receipts.single().state)
                if(receipts.any {it.state=="unknown"}) {val n=reads.get();assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);val rotated=target.copy(actor="password · other");val next=ProviderOAuthInventory(rotated,expected.homeId,expected.rows);assertTrue(runCatching {repo.execute(rotated,ProviderOAuthRequest(next,next.rows.first(),action),api,ManagementTokens("other","other","password","other",4102444800))}.exceptionOrNull() is ManagementOutcomePending);assertEquals(n,reads.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Volatile private var targetScope=""
    @Test fun startCapturesOneTypedScopedAckAndPollDropsPrivateFields()=exercise("success")
    @Test fun missingOptionalDisplayStillAcknowledgesKnownStart()=exercise("no-display")
    @Test fun failedCodeStorageCannotEraseAcknowledgement()=exercise("cache-error")
    @Test fun approvedObservationRemovesCodeWithoutClaimingRevocation()=exercise("approved")
    @Test fun missingSessionDoesNotUndoAcknowledgedStart()=exercise("missing")
    @Test fun wrongSessionCannotUpdateOriginalReceipt()=exercise("wrong-id")
    @Test fun actorChangeRefusesBeforeProviderReads()=exercise("identity")
    @Test fun homeChangeRefusesBeforeProviderReads()=exercise("home")
    @Test fun changedProviderRefusesBeforeClaim()=exercise("changed")
    @Test fun lostStartBlocksAcrossActorRotationRecoveryAndChatClear()=exercise("lost")
    @Test fun arbitrary422AfterClaimRemainsUnknown()=exercise("http")
    @Test fun redirectNeverFollowsOrRetries()=exercise("redirect")
    @Test fun malformedMandatoryAckRemainsUnknown()=exercise("badack")
    @Test fun cancelPhoneWaitLeavesUnknownStartAndNeverReplays()=exercise("cancel-start")
    @Test fun cancelledStatusReadPreservesStartAcknowledgement()=exercise("cancel-poll")
    @Test fun explicitCancelChecksProviderBeforeScopedDelete()=exercise("cancel")
    @Test fun knownNotFoundCancelIsDistinctFromConfirmedCancellation()=exercise("not-found")
    @Test fun lostCancelReplyBlocksFurtherWrites()=exercise("cancel-lost")
    @Test fun disconnectRequiresExactProviderEcho()=exercise("disconnect")
    @Test fun cancelledOptionalCacheSaveCannotEraseCapturedAck()=exercise("cancel-cache")
    @Test fun failedDisconnectReadbackKeepsCapturedAck()=exercise("disconnect-readback")
    @Test fun wrongDisconnectEchoRemainsUnknown()=exercise("disconnect-wrong")
}
