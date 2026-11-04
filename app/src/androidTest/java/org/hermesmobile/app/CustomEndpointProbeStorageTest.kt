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

class CustomEndpointProbeStorageTest {
    private val tokens=ManagementTokens("endpoint-probe-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val sends=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val other=AtomicInteger();val profileReads=AtomicInteger()
        MockWebServer().use {server->
            lateinit var target:ManagementTarget;lateinit var request:CustomEndpointProbeRequest
            fun foreign(state:String):ManagementOperation {
                val owner=target.copy(apiScope="b".repeat(64),profile="private-other-profile",actor="password · private-other-actor")
                return ManagementOperation("foreign-instance-probe",owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,"CustomEndpointProbe","custom-endpoints",buildJsonObject {put("listener_network_scope",true)}.toString(),state,"",1)
            }
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer endpoint-probe-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val u=q.requestUrl!!
                return when(u.encodedPath) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->{
                        if(mode=="profile-race" && profileReads.incrementAndGet()==2) runBlocking {db.dao().claimManagementOperation(foreign("sending"))}
                        json(if(mode=="home") buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path","/other/home")})})} else PluginUpdateFixture.profiles())
                    }
                    "/prefix/api/config"->{assertEquals("worker",u.queryParameter("profile"));assertEquals("false",u.queryParameter("include_defaults"));json(CustomEndpointFixture.config(change=if(mode=="stale") "private" else ""))}
                    "/prefix/api/providers/custom-endpoints"->{assertEquals("GET",q.method);assertEquals("worker",u.queryParameter("profile"));json(CustomEndpointFixture.metadata())}
                    "/prefix/api/providers/custom-endpoints/validate"->{
                        assertEquals("POST",q.method);assertNull(u.query);val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(request.body(),body);assertEquals(if(request.fresh) setOf("name","base_url","model","api_mode","api_key") else setOf("name","base_url","model","api_mode"),body.keys);assertFalse(body.toString().contains(CustomEndpointFixture.inline));assertFalse(body.toString().contains(CustomEndpointFixture.header));assertFalse(body.containsKey("profile"));assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});sends.incrementAndGet()
                        if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "http"->MockResponse().setResponseCode(400).setBody("private-probe-error")
                            "badack"->json(buildJsonObject {put("ok","true");put("reachable",true)})
                            "missing"->json(buildJsonObject {put("ok",true)})
                            else->json(buildJsonObject {put("ok",mode!="negative");put("reachable",mode!="unreachable");put("models",if(mode=="optional") JsonPrimitive(17) else JsonArray(listOf(JsonPrimitive("private-returned-model"))));put("message","private-returned-message");put("resolved_base_url","https://resolved-private.test/v1");put("model_details",buildJsonArray {add(buildJsonObject {put("id","private-returned-model");put("canonical_model","private-canonical");put("reasoning_effort","high")})})})
                        }
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val expected=CustomEndpointSnapshot.parse(CustomEndpointFixture.metadata(),CustomEndpointFixture.config(),target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home))!!.identity())
                val row=expected.rows.single();val draft=CustomEndpointDraft(row.id,row.name,row.baseUrl,row.model,if(mode in setOf("fresh","pending-profile","profile-race")) CustomEndpointKey.Fresh else CustomEndpointKey.Keep,if(mode in setOf("fresh","pending-profile","profile-race")) CustomEndpointFixture.fresh else "")
                request=CustomEndpointProbeRequest(expected,draft);val repo=CustomEndpointRepository(db.dao(),CompletableDeferred(Unit))
                if(mode=="pending-profile") db.dao().insertManagementOperation(foreign("unknown"))
                if(mode=="cancel") {val job=launch {runCatching {repo.probe(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};job.cancelAndJoin();release.countDown()}
                else {
                    val result=runCatching {repo.probe(target,request,api,tokens)}
                    if(mode in setOf("pending-profile","profile-race")) {
                        assertTrue(result.exceptionOrNull() is ManagementOutcomePending);assertEquals(0,sends.get());assertEquals(0,other.get())
                        assertEquals(mode=="profile-race",server.requestCount>0)
                        assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty())
                        assertEquals(if(mode=="profile-race") "sending" else "unknown",db.dao().managementOperation("foreign-instance-probe")!!.state)
                        val count=server.requestCount
                        assertTrue(runCatching {repo.probe(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                        assertEquals(count,server.requestCount);return@use
                    }
                    if(mode in setOf("identity","home","stale")) {assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());return@use}
                    if(mode in setOf("lost","redirect","http","badack","missing")) assertTrue(result.exceptionOrNull() is CustomEndpointUncertain)
                    else {val report=result.getOrThrow();assertEquals(mode!="negative",report.ack.ok);assertEquals(mode!="unreachable",report.ack.reachable);assertEquals(mode=="optional",report.details==null);if(mode!="optional") assertEquals("private-canonical",report.details!!.details.single().canonicalModel)}
                }
                val receipt=db.dao().managementOperations(target.apiScope).first().single();val unknown=mode in setOf("lost","redirect","http","badack","missing","cancel");assertEquals(if(unknown) "unknown" else "acknowledged",receipt.state);assertEquals(1,sends.get());assertEquals(0,other.get())
                for(secret in listOf(row.id,row.name,row.baseUrl,row.model,CustomEndpointFixture.fresh,CustomEndpointFixture.inline,CustomEndpointFixture.header,"private-returned","resolved-private","private-probe-error")) {assertFalse(receipt.requestSummary.contains(secret));assertFalse(receipt.resultSummary.contains(secret))}
                if(unknown) {val rotated=target.copy(actor="password · rotated");val rotatedTokens=ManagementTokens(tokens.access,tokens.refresh,"password","rotated",4102444800);val otherExpected=CustomEndpointSnapshot.parse(CustomEndpointFixture.metadata(),CustomEndpointFixture.config(),rotated,expected.homeId);assertTrue(runCatching {repo.probe(rotated,CustomEndpointProbeRequest(otherExpected,draft),api,rotatedTokens)}.exceptionOrNull() is ManagementOutcomePending);assertTrue(runCatching {repo.execute(target,CustomEndpointRequest.remove(expected,row),api,tokens)}.exceptionOrNull() is ManagementOutcomePending);db.dao().recoverManagementOperations();db.dao().clearChats();assertEquals("unknown",db.dao().managementOperation(receipt.key)!!.state);assertEquals(1,sends.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun anotherProfilesUnknownProbeBlocksBeforeAnyNetworkOrFreshKeyExposure()=exercise("pending-profile")
    @Test fun anotherProfilesClaimDuringPreflightPreventsTheProbePost()=exercise("profile-race")
    @Test fun probeUsesListenerRouteWithoutInventingProfileAndKeepsStoredKeyPrivate()=exercise("keep")
    @Test fun onlyFreshReviewedKeyIsSentExactlyOnceAfterClaim()=exercise("fresh")
    @Test fun typedNegativeIsAnAcknowledgedReport()=exercise("negative")
    @Test fun typedUnreachableIsAnAcknowledgedReport()=exercise("unreachable")
    @Test fun malformedOptionalDetailsCannotEraseReportedFlags()=exercise("optional")
    @Test fun lostReplyBlocksAnotherProbeAndAnotherMutationAcrossActorRotation()=exercise("lost")
    @Test fun redirectsAreNeverFollowedOrRetried()=exercise("redirect")
    @Test fun arbitraryHttpErrorRemainsUnknown()=exercise("http")
    @Test fun stringStatusFlagCannotAcknowledgeAProbe()=exercise("badack")
    @Test fun missingReachableFlagCannotAcknowledgeAProbe()=exercise("missing")
    @Test fun cancellationPreservesOriginalUnknownReceipt()=exercise("cancel")
    @Test fun changedPrivateConfigRefusesBeforeClaimAndSend()=exercise("stale")
    @Test fun changedActorRefusesBeforeClaimAndSend()=exercise("identity")
    @Test fun changedHomeRefusesBeforeClaimAndSend()=exercise("home")
}
