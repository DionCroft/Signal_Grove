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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HostChannelsStorageTest {
    private val tokens=ManagementTokens("channel-storage-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val writes=AtomicInteger();val postures=AtomicInteger();val reads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val phase=AtomicBoolean(false)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            lateinit var owner:ManagementTarget
            fun json(value:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer channel-storage-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))
                val url=q.requestUrl!!
                return when(url.encodedPath) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(phase.get() && mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->{val n=if(phase.get()) profiles.incrementAndGet() else 0;json(PluginUpdateFixture.profiles(phase.get() && (mode=="home" || mode=="home-race" && n>=2 || mode=="readback-home" && writes.get()>0)))}
                    "/prefix/api/messaging/platforms"->{
                        assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));assertEquals(setOf("profile"),url.queryParameterNames)
                        val n=reads.incrementAndGet()
                        if(mode=="cancel-readback" && writes.get()>0) {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        if(mode=="bad-readback" && writes.get()>0) json(buildJsonObject {put("platforms",17)}) else if(mode=="http-readback" && writes.get()>0) MockResponse().setResponseCode(500) else json(HostChannelsFixture.catalog(enabled=writes.get()>0 && mode!="different",tokenSet=mode!="changed-presence",allowSet=writes.get()==0 || mode=="different",allowValue=if(mode=="changed-list") "changed-private-users" else HostChannelsFixture.users,changed=mode=="changed-field" && n>=1))
                    }
                    "/prefix/api/messaging/platforms/"+HostChannelsFixture.id->{
                        assertEquals("PUT",q.method);assertEquals("worker",url.queryParameter("profile"));val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                        assertEquals(buildJsonObject {put("profile","worker");put("enabled",true);put("env",buildJsonObject {put(HostChannelsFixture.key,HostChannelsFixture.secret)});put("clear_env",buildJsonArray {add(HostChannelsFixture.listKey)})},body)
                        assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(owner.resourceScope).single().state});writes.incrementAndGet()
                        if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "refused"->MockResponse().setResponseCode(409).setBody("private-multiplexer-refusal")
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "wrong-ack"->json(JsonObject(HostChannelsFixture.ack()+mapOf("platform" to JsonPrimitive("wrong"))))
                            "untyped-ack"->json(JsonObject(HostChannelsFixture.ack()+mapOf("ok" to JsonPrimitive("true"))))
                            "missing-ack"->json(JsonObject(HostChannelsFixture.ack()-"hot_served"))
                            else->json(HostChannelsFixture.ack(mode=="hot"))
                        }
                    }
                    "/prefix/api/messaging/platforms/"+HostChannelsFixture.id+"/test"->{
                        assertEquals("POST",q.method);assertEquals("worker",url.queryParameter("profile"));assertEquals("{}",q.body.readUtf8());assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(owner.resourceScope).single().state});postures.incrementAndGet()
                        json(if(mode=="posture-optional") JsonObject(HostChannelsFixture.posture()+mapOf("message" to JsonPrimitive(17))) else HostChannelsFixture.posture(mode=="posture-positive"))
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);owner=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val home=api.profiles(tokens).single {it.name=="worker"}.pluginHome!!.identity();val expected=HostChannelInventory.parse(HostChannelsFixture.catalog(),owner,home)
                val draft=HostChannelDraft(true,mapOf(HostChannelsFixture.key to HostChannelsFixture.secret),listOf(HostChannelsFixture.listKey))
                val request=HostChannelRequest(expected,HostChannelsFixture.id,if(mode.startsWith("posture")) null else draft)
                val repo=HostChannelsRepository(db.dao(),CompletableDeferred(Unit));phase.set(true)
                if(mode=="read") {val inventory=repo.read(owner,api,tokens);assertEquals(HostChannelsFixture.id,inventory.rows.single().id);assertNull(inventory.rows.single().fields.first().savedList);assertFalse(inventory.rows.single().home!!.containsKey("unknown_secret"));assertEquals(0,writes.get());return@use}
                if(mode=="foreign") {assertTrue(runCatching {repo.execute(owner.copy(apiScope="b".repeat(64)),request,api,tokens)}.isFailure);assertEquals(0,reads.get());assertEquals(0,writes.get());return@use}
                if(mode=="pending") {
                    val pending=ManagementOperation("foreign-pending",owner.resourceScope,"b".repeat(64),owner.url,owner.profile,"private-other-actor","EnvSave","private-other-key","private-other-summary","sending","",1)
                    db.dao().claimManagementOperation(pending);db.dao().finishManagementOperation(pending.key,"unknown","")
                    assertTrue(runCatching {repo.execute(owner,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                    val visible=ManagementReceiptVisibility.project(db.dao().managementVisibleResourceOperations(owner.resourceScope,owner.apiScope).first(),owner.apiScope,owner.resourceScope)
                    assertEquals(ManagementReceiptVisibility.SharedPending,visible.single().action);assertEquals("",visible.single().actor);assertEquals(0,reads.get());assertEquals(0,writes.get());return@use
                }
                if(mode in setOf("cancel","cancel-readback")) {
                    val task=async {repo.execute(owner,request,api,tokens)}
                    assertTrue(withContext(Dispatchers.IO) {entered.await(10,TimeUnit.SECONDS)})
                    task.cancelAndJoin();release.countDown();val receipt=db.dao().managementOperations(owner.apiScope).first().single()
                    assertEquals(if(mode=="cancel") "unknown" else "acknowledged",receipt.state);assertEquals(1,writes.get());return@use
                }
                val result=runCatching {repo.execute(owner,request,api,tokens)}
                if(mode in setOf("identity","home","home-race","changed-field","changed-presence","changed-list")) {
                    assertTrue(result.isFailure);assertEquals(0,writes.get());assertTrue(db.dao().managementOperations(owner.apiScope).first().isEmpty());return@use
                }
                val receipt=db.dao().managementOperations(owner.apiScope).first().single()
                if(mode in setOf("lost","refused","redirect","wrong-ack","untyped-ack","missing-ack")) {
                    assertTrue(result.exceptionOrNull() is HostChannelsUncertain);assertEquals("unknown",receipt.state)
                    assertTrue(runCatching {repo.execute(owner,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                    assertEquals(1,writes.get());val before=reads.get();repo.read(owner,api,tokens);assertEquals(before+1,reads.get());assertEquals(1,writes.get())
                } else if(mode in setOf("bad-readback","http-readback","readback-home")) {
                    assertTrue(result.exceptionOrNull() is HostChannelsReadbackPending);assertEquals("acknowledged",receipt.state);assertEquals(1,writes.get())
                } else {
                    val outcome=result.getOrThrow();assertEquals("acknowledged",receipt.state)
                    if(mode.startsWith("posture")) {assertEquals(mode=="posture-positive",outcome.posture!!.ack.ok);assertEquals(1,postures.get());assertEquals(0,writes.get());if(mode=="posture-optional") assertNull(outcome.posture.details) else assertEquals(HostChannelsFixture.message,outcome.posture.details!!.message)}
                    else {assertEquals(1,writes.get());assertEquals(mode=="different",outcome.different);assertEquals(mode=="hot",outcome.hotServed)}
                }
                for(secret in listOf(HostChannelsFixture.secret,HostChannelsFixture.stored,HostChannelsFixture.users,HostChannelsFixture.message,"private-channel-chat","private-channel-scope","private-multiplexer-refusal")) {assertFalse(receipt.requestSummary.contains(secret));assertFalse(receipt.resultSummary.contains(secret))}
                assertEquals(0,other.get())
            } finally {release.countDown()}
        }
        db.close()
    }
    @Test fun explicitProfilePrefixBearerAndPrivateProjectionArePreserved()=exercise("read")
    @Test fun reviewedFieldsProduceOneExactSaveAndSeparatePresenceReadback()=exercise("save")
    @Test fun hotRebuildNotificationIsRecordedSeparatelyFromRuntimeHealth()=exercise("hot")
    @Test fun managedOrDifferentReadbackRetainsTheAcknowledgedSave()=exercise("different")
    @Test fun negativePostureReportsAreAcknowledgedWithoutVendorCalls()=exercise("posture-negative")
    @Test fun positivePostureReportsKeepPrivateDetailsOutOfTheReceipt()=exercise("posture-positive")
    @Test fun malformedOptionalPostureDetailsDoNotEraseMandatoryAcknowledgement()=exercise("posture-optional")
    @Test fun metadataPresenceAndPrivateAllowlistChangesRefuseBeforeClaim() {for(mode in listOf("changed-field","changed-presence","changed-list")) exercise(mode)}
    @Test fun profileHomeChangesBeforeOrDuringPreflightRefuseBeforeClaim() {exercise("home");exercise("home-race")}
    @Test fun aDifferentNativeIdentityCannotMutateThisProfile()=exercise("identity")
    @Test fun lostSaveBlocksFurtherSubmissionsButExplicitReadDoesNotReplay()=exercise("lost")
    @Test fun cancellationDuringSaveLeavesDurableUncertainty()=exercise("cancel")
    @Test fun cancellationDuringReadbackKeepsTheKnownAcknowledgement()=exercise("cancel-readback")
    @Test fun malformedHttpAndChangedHomeReadbackRetainAck() {for(mode in listOf("bad-readback","http-readback","readback-home")) exercise(mode)}
    @Test fun wrongUntypedOrIncompleteAckRemainsUncertain() {for(mode in listOf("wrong-ack","untyped-ack","missing-ack")) exercise(mode)}
    @Test fun redirectsAreNeverFollowedOrRetried()=exercise("redirect")
    @Test fun serverRefusalDoesNotImplyThatNoPartialWriteOccurred()=exercise("refused")
    @Test fun anotherConnectionsPendingReceiptIsPrivateAndBlocksBeforeNetwork()=exercise("pending")
    @Test fun foreignInferenceOwnerCannotBeSubstitutedIntoTheReview()=exercise("foreign")
}
