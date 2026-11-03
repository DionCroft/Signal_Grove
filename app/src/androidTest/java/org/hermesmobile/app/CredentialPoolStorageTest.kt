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
import java.util.concurrent.atomic.AtomicReference

class CredentialPoolStorageTest {
    private val tokens=ManagementTokens("pool-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val provider=if(mode=="custom") "custom:fixture-endpoint" else CredentialPoolFixture.provider
        val remove=mode in setOf("remove","remove-still","cleanup-bad","remove-lost","remove-readback-error")
        val current=AtomicReference(CredentialPoolFixture.inventory(provider));val sends=AtomicInteger();val reads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            lateinit var target:ManagementTarget
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer pool-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val u=q.requestUrl!!
                return when {
                    u.encodedPath=="/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    u.encodedPath=="/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(if(mode=="home" || mode=="after-home" && n==2 || mode=="readback-home" && n>=3) buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path","/different/home")})})} else PluginUpdateFixture.profiles())}
                    u.encodedPath=="/prefix/api/credentials/pool" && q.method=="GET"->{
                        assertEquals("worker",u.queryParameter("profile"));val n=reads.incrementAndGet()
                        if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when {
                            mode in setOf("readback-error","remove-readback-error") && n==2->MockResponse().setResponseCode(500).setBody("private-readback-error")
                            mode in setOf("label","order","source","counter") && n==1->json(CredentialPoolFixture.inventory(provider,CredentialPoolFixture.rows(mode)))
                            else->json(current.get())
                        }
                    }
                    u.encodedPath=="/prefix/api/credentials/pool" && q.method=="POST" || u.pathSegments.take(4)==listOf("prefix","api","credentials","pool") && u.pathSegments.size==6 && q.method=="DELETE"->{
                        assertEquals("worker",u.queryParameter("profile"));assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});sends.incrementAndGet()
                        if(remove) {assertEquals("DELETE",q.method);assertEquals(provider,u.pathSegments[4]);assertEquals("2",u.pathSegments[5]);assertEquals(0L,q.bodySize);current.set(if(mode=="remove-still") CredentialPoolFixture.inventory(provider) else CredentialPoolFixture.remove(provider))}
                        else {assertEquals("POST",q.method);val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(setOf("provider","api_key","label"),body.keys);assertEquals(provider,body.text("provider"));assertEquals(CredentialPoolFixture.secret,body.text("api_key"));assertEquals(CredentialPoolFixture.label,body.text("label"));current.set(CredentialPoolFixture.add(provider))}
                        if(mode=="cancel-write") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        val ack=CredentialPoolFixture.ack(provider,if(remove) 1 else if(mode=="count-race") 99 else 3,remove)
                        when(mode) {
                            "lost","remove-lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "http"->MockResponse().setResponseCode(400).setBody("private-host-error")
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "badack"->json(JsonObject(ack+mapOf("ok" to JsonPrimitive("true"))))
                            "badprovider"->json(JsonObject(ack+mapOf("provider" to JsonPrimitive("other"))))
                            "missing-count"->json(JsonObject(ack-"count"))
                            "cleanup-bad"->json(JsonObject(ack+mapOf("cleaned" to JsonPrimitive("private-cleanup-error"))))
                            else->json(ack)
                        }
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val expected=CredentialPoolInventory.parse(CredentialPoolFixture.inventory(provider),target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home))!!.identity())
                val request=if(remove) CredentialPoolRequest.remove(expected,provider,expected.providers.single().entries[1]) else CredentialPoolRequest.add(expected,provider,CredentialPoolFixture.secret,CredentialPoolFixture.label)
                val repo=CredentialPoolRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel-write","cancel-readback")) {val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};job.cancelAndJoin();release.countDown()}
                else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "identity","home","after-home","label","order","source"->{assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode in setOf("identity","home")) assertEquals(0,reads.get());return@use}
                        "lost","remove-lost","http","redirect","badack","badprovider","missing-count"->assertTrue(result.exceptionOrNull() is CredentialPoolUncertain)
                        "readback-error","readback-home","remove-readback-error"->{assertTrue(result.exceptionOrNull() is CredentialPoolReadbackPending);if(mode=="remove-readback-error") {val report=(result.exceptionOrNull() as CredentialPoolReadbackPending).report!!;assertEquals(target,report.owner);assertEquals(listOf("private-cli-command-fixture"),report.hints);assertFalse(report.toString().contains("private"))}}
                        else->{val out=result.getOrThrow();assertEquals("acknowledged",out.receipt.state);assertEquals(if(remove) mode=="remove-still" else null,out.reviewedIdPresent);if(mode=="cleanup-bad") assertEquals(JsonPrimitive(false),Json.parseToJsonElement(out.receipt.resultSummary).jsonObject["cleanup_report_available"])}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipt=db.dao().managementOperations(target.apiScope).first().single();assertEquals(1,sends.get());assertEquals(0,other.get());assertEquals(if(remove) "CredentialPoolRemove" else "CredentialPoolAdd",receipt.action)
                for(secret in listOf(CredentialPoolFixture.secret,CredentialPoolFixture.label,CredentialPoolFixture.preview,CredentialPoolFixture.source,"pool-management-fixture","private-cleanup","private-host-error")) assertFalse(secret,receipt.toString().contains(secret))
                if(mode=="cancel-readback") assertEquals("acknowledged",receipt.state)
                if(receipt.state=="unknown") {
                    val before=reads.get();assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                    val rotated=target.copy(actor="password · rotated");val snapshot=CredentialPoolInventory(rotated,expected.homeId,expected.providers)
                    assertTrue(runCatching {repo.execute(rotated,CredentialPoolRequest.add(snapshot,provider,CredentialPoolFixture.secret),api,ManagementTokens("other","other","password","rotated",4102444800))}.exceptionOrNull() is ManagementOutcomePending)
                    val oauth=ProviderOAuthInventory.parse(ProviderOAuthFixture.catalog(),target,expected.homeId);val codes=object:ProviderCodeStore {override fun save(binding:ProviderOAuthBinding,value:ProviderDeviceCode) {};override fun read(binding:ProviderOAuthBinding,now:Long):ProviderDeviceCode?=null;override fun remove(binding:ProviderOAuthBinding) {}}
                    assertTrue(runCatching {ProviderOAuthRepository(db.dao(),CompletableDeferred(Unit),codes).execute(target,ProviderOAuthRequest(oauth,oauth.rows.first(),ProviderOAuthAction.Start),api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(before,reads.get());assertEquals(1,sends.get())
                }
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun addSendsOneExactScopedBodyAfterClaimAndSeparateReadback()=exercise("success")
    @Test fun canonicalCustomPoolIsPreservedInBodyAndReadback()=exercise("custom")
    @Test fun removeUsesReviewedOneBasedIndexAndNeverClaimsEchoedIdentity()=exercise("remove")
    @Test fun unchangedReviewedIdIsObservationWithoutErasingAck()=exercise("remove-still")
    @Test fun optionalMalformedCleanupCannotEraseKnownAck()=exercise("cleanup-bad")
    @Test fun changingLiveCountersDoesNotRejectSafeStructuralReview()=exercise("counter")
    @Test fun reportedCountCanDifferWithoutInventingIdentityOrLostWrite()=exercise("count-race")
    @Test fun actorChangeRefusesBeforePoolRead()=exercise("identity")
    @Test fun homeChangeRefusesBeforePoolRead()=exercise("home")
    @Test fun changedHomeAfterMetadataReadRefusesBeforeClaim()=exercise("after-home")
    @Test fun changedLabelRefusesBeforeClaim()=exercise("label")
    @Test fun reorderedRowsRefuseBeforeClaim()=exercise("order")
    @Test fun privateSourceChangesRefuseWithoutExposingSource()=exercise("source")
    @Test fun lostAddReplyBlocksAcrossActorRotationRecoveryChatClearAndOAuth()=exercise("lost")
    @Test fun lostRemoveReplyCannotReplayOrAllowAnotherMutation()=exercise("remove-lost")
    @Test fun arbitraryPostMutation400RemainsUnknown()=exercise("http")
    @Test fun redirectNeverFollowsAndRemainsUnknown()=exercise("redirect")
    @Test fun stringSuccessFlagCannotAcknowledge()=exercise("badack")
    @Test fun wrongProviderCannotAcknowledge()=exercise("badprovider")
    @Test fun missingMandatoryCountRemainsUnknown()=exercise("missing-count")
    @Test fun failedReadbackKeepsCapturedAck()=exercise("readback-error")
    @Test fun changedReadbackHomeKeepsCapturedAck()=exercise("readback-home")
    @Test fun canceledWriteLeavesUnknownAndNeverReplays()=exercise("cancel-write")
    @Test fun removalReadbackFailureRetainsTransientOriginalCleanupHints()=exercise("remove-readback-error")
    @Test fun canceledReadbackCannotEraseCapturedAck()=exercise("cancel-readback")
}
