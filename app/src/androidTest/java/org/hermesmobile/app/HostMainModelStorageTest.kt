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

class HostMainModelStorageTest {
    private val tokens=ManagementTokens("main-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build();val sends=AtomicInteger();val reads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val model=AtomicReference(HostMainModelFixture.old);val provider=AtomicReference(HostMainModelFixture.provider);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            lateinit var target:ManagementTarget
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer main-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))
                val url=q.requestUrl!!
                return when(url.encodedPath) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(PluginUpdateFixture.profiles(mode=="home" || mode=="readback-home" && n>2))}
                    "/prefix/api/model/auxiliary"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));val n=reads.incrementAndGet();if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)};json(HostMainModelFixture.auxiliary(if(mode=="main-changed") "other-model" else if(mode=="readback") HostMainModelFixture.old else model.get(),provider.get()))}
                    "/prefix/api/model/options"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));assertEquals("false",url.queryParameter("refresh"));assertEquals("true",url.queryParameter("include_unconfigured"));json(HostMainModelFixture.catalog(if(mode=="main-changed") "other-model" else if(mode=="readback") HostMainModelFixture.old else model.get(),provider.get(),mode=="changed",mode=="blocked"))}
                    "/prefix/api/model/set"->{
                        assertEquals("POST",q.method);assertEquals("worker",url.queryParameter("profile"));val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(setOf("scope","profile","provider","model","confirm_expensive_model"),body.keys);assertEquals(JsonPrimitive("main"),body["scope"]);assertEquals(JsonPrimitive("worker"),body["profile"]);assertEquals(JsonPrimitive(HostMainModelFixture.provider),body["provider"]);assertEquals(JsonPrimitive(HostMainModelFixture.next),body["model"]);assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});val n=sends.incrementAndGet();assertEquals(JsonPrimitive(mode=="warning" && n==2),body["confirm_expensive_model"])
                        if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when {
                            mode=="lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            mode=="http"->MockResponse().setResponseCode(400).setBody("private-host-error")
                            mode=="redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            mode=="untyped"->json(JsonObject(HostMainModelFixture.saved()+mapOf("ok" to JsonPrimitive("true"))))
                            mode=="badack"->json(JsonObject(HostMainModelFixture.saved()+mapOf("scope" to JsonPrimitive("auxiliary"))))
                            mode=="warning" && n==1->json(HostMainModelFixture.warning())
                            mode=="badwarning"->json(JsonObject(HostMainModelFixture.warning()+mapOf("provider" to JsonPrimitive("other"))))
                            else->{model.set(if(mode=="normalized") "normalized-model" else HostMainModelFixture.next);provider.set(if(mode=="normalized") "normalized-provider" else HostMainModelFixture.provider);json(HostMainModelFixture.saved(model.get(),provider.get(),mode=="optional"))}
                        }
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val initial=HostModelInventory.parse(HostMainModelFixture.auxiliary(),HostMainModelFixture.catalog(),target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home)));val request=HostMainModelRequest(initial,HostMainModelFixture.provider,HostMainModelFixture.next);val repo=HostMainModelRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel","cancel-readback")) {
                    val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};if(mode=="cancel") assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown()
                } else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "changed","blocked","main-changed","home","identity"->{assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode in setOf("home","identity")) assertEquals(0,reads.get());return@use}
                        "lost","http","redirect","untyped","badack","badwarning"->assertTrue(result.exceptionOrNull() is HostMainModelUncertain)
                        "readback","readback-home"->assertTrue(result.exceptionOrNull() is HostMainModelReadbackPending)
                        "warning"->{val out=result.getOrThrow() as HostMainModelOutcome.Warning;assertEquals(1,sends.get());assertSame(out.inventory,out.warning.request.expected);assertEquals(HostMainModelFixture.old,out.inventory.main.model);assertEquals("acknowledged",out.receipt.state);assertEquals(JsonPrimitive(false),Json.parseToJsonElement(out.receipt.resultSummary).jsonObject["reported_assigned"]);val saved=repo.execute(target,out.warning.request.approve(out.warning),api,tokens) as HostMainModelOutcome.Saved;assertEquals(HostMainModelFixture.next,saved.inventory.main.model);assertEquals(2,sends.get())}
                        else->{val out=result.getOrThrow() as HostMainModelOutcome.Saved;assertEquals(if(mode=="normalized") HostModelRoute("normalized-provider","normalized-model") else HostModelRoute(HostMainModelFixture.provider,HostMainModelFixture.next),out.inventory.main);assertEquals("acknowledged",out.receipt.state)}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipts=db.dao().managementOperations(target.apiScope).first();assertEquals(if(mode=="warning") 2 else 1,receipts.size);assertEquals(if(mode=="warning") 2 else 1,sends.get());assertEquals(0,other.get())
                for(receipt in receipts) {assertEquals("MainModel",receipt.action);for(value in listOf("private-catalog-key","private-catalog-url","private-saved-endpoint","private-tool","private-malformed-tools","private-host-error",HostMainModelFixture.warningText,"Fixture post-assignment notice","main-management-fixture")) assertFalse(value,receipt.toString().contains(value))}
                if(mode=="cancel-readback") assertEquals("acknowledged",receipts.single().state)
                if(receipts.any {it.state=="unknown"}) {val count=sends.get();assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(count,sends.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun claimBeforeOneSaveAndReadbackKeepAcknowledgementSeparateFromRuntime()=exercise("success")
    @Test fun normalizedReportedPairIsAcknowledgedRatherThanMisclassifiedAsLost()=exercise("normalized")
    @Test fun knownWarningNeedsAnotherExplicitRequestAndFreshClaimToOverride()=exercise("warning")
    @Test fun changedProviderMetadataRefusesBeforeClaimOrPost()=exercise("changed")
    @Test fun newlyRestrictedModelRefusesBeforeClaimOrPost()=exercise("blocked")
    @Test fun changedMainAssignmentRefusesBeforeClaimOrPost()=exercise("main-changed")
    @Test fun changedHomeRefusesBeforeAnyModelRead()=exercise("home")
    @Test fun changedActorRefusesBeforeAnyModelRead()=exercise("identity")
    @Test fun lostReplySurvivesRecoveryAndChatClearWithoutReplay()=exercise("lost")
    @Test fun arbitraryHttpRefusalRemainsUncertain()=exercise("http")
    @Test fun redirectsAreUncertainAndNeverFollowed()=exercise("redirect")
    @Test fun untypedSuccessIsUncertain()=exercise("untyped")
    @Test fun wrongScopeSuccessIsUncertain()=exercise("badack")
    @Test fun wrongIdentityWarningCannotBecomeKnownRefusal()=exercise("badwarning")
    @Test fun staleReadbackCannotEraseMandatoryAcknowledgement()=exercise("readback")
    @Test fun changedReadbackHomeCannotEraseAcknowledgementOrTriggerModelProbe()=exercise("readback-home")
    @Test fun malformedOptionalDetailsCannotEraseMandatoryAcknowledgement()=exercise("optional")
    @Test fun cancellingSaveAndAttemptingASecondMutationNeverReplays()=exercise("cancel")
    @Test fun cancellingHeldReadbackPreservesCapturedAcknowledgement()=exercise("cancel-readback")
}
