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

class HostRelayUnlinkStorageTest {
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val sends=AtomicInteger();val reads=AtomicInteger();val channels=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val privateRows=mutableMapOf<String,Pair<JsonObject,HostRelayAccount>>();lateinit var owner:ManagementTarget
        val store=object:HostRelayTargetStore {
            override fun save(binding:HostRelayUnlinkBinding,account:HostRelayAccount) {
                if(mode=="cache") throw IllegalStateException("private-storage-failure")
                privateRows[binding.receipt.key]=binding.owner() to account
                if(mode=="race") runBlocking {db.dao().claimManagementOperation(ManagementOperation("foreign-race",owner.resourceScope,"b".repeat(64),owner.url,owner.profile,"private-foreign-actor","Other","private-foreign-target","private-foreign-summary","sending","",1))}
            }
            override fun read(binding:HostRelayUnlinkBinding)=privateRows[binding.receipt.key]?.takeIf {it.first==binding.owner()}?.second
            override fun remove(binding:HostRelayUnlinkBinding) {if(privateRows[binding.receipt.key]?.first==binding.owner()) privateRows.remove(binding.receipt.key)}
            override fun clear(target:ManagementTarget) {privateRows.entries.removeAll {it.value.first["api_scope"]==JsonPrimitive(target.apiScope) && it.value.first["url"]==JsonPrimitive(target.url) && it.value.first["profile"]==JsonPrimitive(target.profile) && it.value.first["actor"]==JsonPrimitive(target.actor)}}
        }
        try {MockWebServer().use {server->
            fun json(raw:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(raw.toString())
            fun report():JsonObject {
                val raw=HostRelayFixture.report(if(mode=="manager" || mode=="after-manager" && sends.get()>0) "portal" else "connector")
                val row=raw["platforms"]!!.jsonArray.single().jsonObject;val account=row["accounts"]!!.jsonArray.single().jsonObject
                val kept=mode in setOf("still","lost","clear","local","cancel-write") || sends.get()==0
                return JsonObject(raw+mapOf("platforms" to buildJsonArray {add(JsonObject(row+mapOf("accounts" to if(kept) buildJsonArray {add(if(mode=="target") JsonObject(account+mapOf("displayName" to JsonPrimitive("changed-private-target"))) else account)} else JsonArray(emptyList()),"bot_url" to if(mode=="bot") JsonPrimitive("https://t.me/changed") else row["bot_url"]!!)))}))
            }
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer unlink-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val url=q.requestUrl!!
                return when(url.encodedPath) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(PluginUpdateFixture.profiles(mode=="home" || mode=="home-race" && n>=3 || mode=="readback-home" && sends.get()>0))}
                    "/prefix/api/messaging/platforms"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));val n=channels.incrementAndGet();json(HostRelayFixture.channels(!(mode=="marker" && n>=2)))}
                    "/prefix/api/messaging/relay"->{
                        assertEquals("GET",q.method);assertNull(url.query);assertEquals(0L,q.bodySize);reads.incrementAndGet()
                        if(mode=="cancel-readback" && sends.get()>0) {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        if(mode=="bad-readback" && sends.get()>0) MockResponse().setResponseCode(500) else json(report())
                    }
                    "/prefix/api/messaging/relay/"+HostChannelsFixture.id+"/accounts/"+HostRelayFixture.account->{
                        assertEquals("DELETE",q.method);assertNull(url.query);assertEquals(0L,q.bodySize)
                        val pending=runBlocking {db.dao().unresolvedManagementOperations(owner.resourceScope).single()};assertEquals("sending",pending.state);assertNotNull(privateRows[pending.key]);sends.incrementAndGet()
                        if(mode=="cancel-write") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "lost","clear","local"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "http"->MockResponse().setResponseCode(409).setBody("private-after-unlink-refusal")
                            "bad-ack"->json(buildJsonObject {put("unlinked","true")})
                            "false-ack"->json(buildJsonObject {put("unlinked",false)})
                            "missing-ack"->json(buildJsonObject {put("ok",true)})
                            else->json(buildJsonObject {put("unlinked",true)})
                        }
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            val api=ManagementClient(server.url("prefix/").toString(),true,true);val tokens=ManagementTokens("unlink-fixture","refresh-fixture","password","fixture",4102444800);owner=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
            val home=PluginHome.project(JsonPrimitive(PluginUpdateFixture.home))!!.identity();val scope=HostRelayScope.from(HostChannelInventory.parse(HostRelayFixture.channels(),owner,home));val expected=HostRelayInventory.parse(HostRelayFixture.report("connector"),scope);val request=HostRelayUnlinkRequest(expected,HostChannelsFixture.id,expected.platforms.single().accounts.single())
            val reader=HostRelayRepository(HostChannelsRepository(db.dao(),CompletableDeferred(Unit)));val repo=HostRelayUnlinkRepository(db.dao(),CompletableDeferred(Unit),reader,store)
            if(mode=="pending") {db.dao().claimManagementOperation(ManagementOperation("foreign-pending",owner.resourceScope,"b".repeat(64),owner.url,owner.profile,"private-foreign-actor","Other","private-foreign-target","private-foreign-summary","unknown","",1));assertTrue(runCatching {repo.execute(owner,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(0,reads.get());assertTrue(privateRows.isEmpty());return@use}
            if(mode=="foreign") {assertTrue(runCatching {repo.execute(owner.copy(apiScope="b".repeat(64)),request,api,tokens)}.isFailure);assertEquals(0,reads.get());assertEquals(0,sends.get());return@use}
            if(mode in setOf("cancel-write","cancel-readback")) {val task=async {repo.execute(owner,request,api,tokens)};assertTrue(withContext(Dispatchers.IO) {entered.await(10,TimeUnit.SECONDS)});task.cancelAndJoin();release.countDown();val receipt=db.dao().managementOperations(owner.apiScope).first().single();assertEquals(if(mode=="cancel-write") "unknown" else "acknowledged",receipt.state);assertEquals(1,sends.get());assertNotNull(repo.local(owner,receipt.key).account);return@use}
            val result=runCatching {repo.execute(owner,request,api,tokens)}
            if(mode in setOf("identity","home","home-race","marker","manager","target","bot","cache","race")) {
                assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(owner.apiScope).first().isEmpty());assertTrue(privateRows.isEmpty());return@use
            }
            val receipt=db.dao().managementOperations(owner.apiScope).first().single();assertEquals(1,sends.get())
            if(mode in setOf("lost","clear","local","redirect","http","bad-ack","false-ack","missing-ack")) {
                assertTrue(result.exceptionOrNull() is HostRelayUnlinkUncertain);assertEquals("unknown",receipt.state)
                val count=reads.get();assertTrue(runCatching {repo.execute(owner,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(count,reads.get());reader.read(owner,api,tokens);assertEquals(1,sends.get())
            } else if(mode in setOf("bad-readback","readback-home")) {assertTrue(result.exceptionOrNull() is HostRelayUnlinkReadbackPending);assertEquals("acknowledged",receipt.state)}
            else {val out=result.getOrThrow();assertEquals("acknowledged",receipt.state);assertEquals(when(mode) {"still"->false;"after-manager"->null;else->true},out.absent)}
            val local=repo.local(owner,receipt.key);assertEquals(HostRelayFixture.account,local.account!!.id)
            val n=reads.get();assertTrue(runCatching {repo.local(owner.copy(actor="other"),receipt.key)}.isFailure);assertEquals(n,reads.get())
            if(mode=="clear") {store.clear(owner);assertNull(repo.local(owner,receipt.key).account);assertEquals("unknown",db.dao().managementOperation(receipt.key)!!.state);assertTrue(runCatching {repo.execute(owner,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,sends.get())}
            for(secret in listOf(HostRelayFixture.account,HostRelayFixture.name,HostRelayFixture.handle,"private-after-unlink-refusal","https://t.me/")) {assertFalse(receipt.requestSummary.contains(secret));assertFalse(receipt.resultSummary.contains(secret))}
            assertEquals(0,other.get())
        }} finally {release.countDown();db.close()}
    }
    @Test fun frozenTargetHasOnePrefixBearerDeleteWithoutBodyOrProfileAndSeparateAbsentReadback()=exercise("normal")
    @Test fun staleAccountMetadataBotOrAuthorityRefusesBeforePrivateSaveAndClaim() {for(m in listOf("target","bot","manager")) exercise(m)}
    @Test fun identityHomeAndRelayMarkerChangesRefuseBeforeClaim() {for(m in listOf("identity","home","home-race","marker")) exercise(m)}
    @Test fun foreignInferenceOwnerCannotReuseTheReviewedTarget()=exercise("foreign")
    @Test fun sharedForeignPendingWorkBlocksBeforePrivateReadsOrCacheWrites()=exercise("pending")
    @Test fun failedPrivatePersistenceCannotSendOrCreateAnOperation()=exercise("cache")
    @Test fun atomicClaimRaceCleansOnlyTheUnclaimedPrivateTarget()=exercise("race")
    @Test fun lostReplyRetainsOriginalTargetAndBlocksResendWhileReadsRemainExplicit()=exercise("lost")
    @Test fun arbitraryHttpRefusalAfterClaimRemainsUncertain()=exercise("http")
    @Test fun redirectsCannotForwardThePrivateAccountOrBearer()=exercise("redirect")
    @Test fun falseMissingAndUntypedAcknowledgementsRemainUncertain() {for(m in listOf("bad-ack","false-ack","missing-ack")) exercise(m)}
    @Test fun cancellationDuringDeleteRetainsDurableUncertainty()=exercise("cancel-write")
    @Test fun cancellationDuringReadbackPreservesAcknowledgement()=exercise("cancel-readback")
    @Test fun failedOrChangedHomeReadbackCannotDowngradeAcknowledgement() {exercise("bad-readback");exercise("readback-home")}
    @Test fun stillListedAccountsDoNotTriggerAnotherUnlink()=exercise("still")
    @Test fun changedLaterAuthorityDoesNotClaimVerifiedAbsence()=exercise("after-manager")
    @Test fun historicalPrivateTargetInspectionDoesNotContactTheHost()=exercise("local")
    @Test fun localPrivateClearCannotUndoOrUnblockAnUncertainRemoteRequest()=exercise("clear")
}
