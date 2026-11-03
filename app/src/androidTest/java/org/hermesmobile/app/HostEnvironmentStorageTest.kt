package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HostEnvironmentStorageTest {
    private val tokens=ManagementTokens("environment-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build();val writes=AtomicInteger();val reads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val remove=mode in setOf("remove","unset-remove","readback-remove");val key=if(mode=="new") "NEW_FIXTURE_KEY" else HostEnvironmentFixture.key;val initialPresent=mode!="unset-remove"
        MockWebServer().use {server->
            lateinit var target:ManagementTarget
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer environment-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"))
                return when(q.path) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(PluginUpdateFixture.profiles(mode=="home" || mode=="readback-home" && n>1))}
                    "/prefix/api/env?profile=worker"->if(q.method=="GET") {
                        val n=reads.incrementAndGet();if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        if(mode=="invalid") json(buildJsonObject {put(key,buildJsonObject {put("is_set","true")})}) else json(HostEnvironmentFixture.inventory(if(n==1) initialPresent else if(mode=="readback" || remove && mode!="readback-remove") false else true,mode=="changed",mode=="new" && n>1))
                    } else {
                        assertEquals(if(remove) "DELETE" else "PUT",q.method);val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(if(remove) setOf("profile","key") else setOf("profile","key","value","provider_setup"),body.keys);assertEquals(JsonPrimitive("worker"),body["profile"]);assertEquals(JsonPrimitive(key),body["key"]);if(!remove) {assertEquals(JsonPrimitive(HostEnvironmentFixture.secret),body["value"]);assertEquals(JsonPrimitive(false),body["provider_setup"])}
                        assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});writes.incrementAndGet()
                        if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "refused"->MockResponse().setResponseCode(400).setBody("private-host-error")
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "badack"->json(HostEnvironmentFixture.accepted("WRONG_KEY"))
                            "untyped"->json(buildJsonObject {put("ok","true");put("key",key)})
                            else->json(HostEnvironmentFixture.accepted(key,remove,mode=="optional"))
                        }
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val inventory=HostEnvInventory.parse(HostEnvironmentFixture.inventory(initialPresent),target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home)));val request=HostEnvRequest(inventory,key,if(remove) HostEnvAction.Remove else HostEnvAction.Save,if(remove) "" else HostEnvironmentFixture.secret);val repo=HostEnvironmentRepository(db.dao(),CompletableDeferred(Unit))
                if(mode=="read") {val out=repo.read(target,api,tokens);assertEquals(1,out.rows.size);assertEquals(0,writes.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());return@use}
                if(mode in setOf("cancel","cancel-readback")) {
                    val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};if(mode=="cancel") assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown()
                } else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "changed","home","identity","invalid"->{assertTrue(result.isFailure);assertEquals(0,writes.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode in setOf("home","identity")) assertEquals(0,reads.get());return@use}
                        "lost","refused","redirect","badack","untyped"->assertTrue(result.exceptionOrNull() is HostEnvUncertain)
                        "readback","readback-home","readback-remove"->assertTrue(result.exceptionOrNull() is HostEnvReadbackPending)
                        else->{val out=result.getOrThrow();assertEquals("acknowledged",out.receipt.state);assertTrue(request.presenceMatches(out.inventory))}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipt=db.dao().managementOperations(target.apiScope).first().single();for(value in listOf(HostEnvironmentFixture.secret,HostEnvironmentFixture.mask,"private-unexpected-old-value","private-mirror-path","private-malformed-path","private-host-error","environment-management-fixture")) assertFalse(value,receipt.toString().contains(value));assertEquals(1,writes.get());assertEquals(0,other.get());if(mode=="readback-home") assertEquals(1,reads.get());if(mode=="cancel-readback") assertEquals("acknowledged",receipt.state)
                if(receipt.state=="unknown") {assertTrue(runCatching {HostEnvironmentRepository(db.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun reviewedReadHasNoWritesOrDurableSecretState()=exercise("read")
    @Test fun replacementIsClaimedOnceAndPresenceDoesNotConfirmItsValue()=exercise("save")
    @Test fun customKeyAdditionRequiresFreshAbsence()=exercise("new")
    @Test fun removalAcknowledgesReferenceCleanupWithoutProviderRevocation()=exercise("remove")
    @Test fun unsetPresenceDoesNotSkipAReviewedReferenceRemoval()=exercise("unset-remove")
    @Test fun changedMetadataRefusesBeforeClaim()=exercise("changed")
    @Test fun changedHomeRefusesBeforeReadingEnvironmentOrWriting()=exercise("home")
    @Test fun wrongIdentityRefusesBeforeReadingEnvironmentOrWriting()=exercise("identity")
    @Test fun malformedMetadataDoesNotBecomeAnEmptyInventory()=exercise("invalid")
    @Test fun lostReplySurvivesRecoveryAndChatClearWithoutReplay()=exercise("lost")
    @Test fun hostErrorIsUncertainAndNeverStoresItsPrivateBody()=exercise("refused")
    @Test fun redirectedWritesAreNeverFollowedOrRepeated()=exercise("redirect")
    @Test fun wrongKeyAcknowledgementIsUncertain()=exercise("badack")
    @Test fun untypedAcknowledgementIsUncertain()=exercise("untyped")
    @Test fun malformedOptionalMirrorDetailsCannotEraseMandatoryAck()=exercise("optional")
    @Test fun missingSavedPresencePreservesAcknowledgement()=exercise("readback")
    @Test fun retainedPresenceAfterRemovalPreservesAcknowledgement()=exercise("readback-remove")
    @Test fun changedReadbackHomeKeepsAckAndAvoidsTheNewHome()=exercise("readback-home")
    @Test fun cancellationAndSecondAttemptNeverWriteTwice()=exercise("cancel")
    @Test fun cancellationAfterAckCannotEraseIt()=exercise("cancel-readback")
}
