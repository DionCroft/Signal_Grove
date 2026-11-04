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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HostRelayLinkStorageTest {
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val sends=AtomicInteger();val reads=AtomicInteger();val states=AtomicInteger();val profiles=AtomicInteger();val changed=AtomicBoolean(false);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val cache=ConcurrentHashMap<String,Pair<JsonObject,HostRelayCode>>();lateinit var target:ManagementTarget
        val codeValue=if(mode=="opaque") "用户.一+12" else HostRelayLinkFixture.code
        val store=object:HostRelayCodeStore {
            override fun beforeStart(target:ManagementTarget,now:Long) {if(mode=="pre-cache") error("private-cache-error");if(mode=="full") throw HostRelayCodeFull();if(mode=="race") runBlocking {db.dao().claimManagementOperation(ManagementOperation("foreign-race",target.resourceScope,"b".repeat(64),target.url,target.profile,"private-foreign-actor","Other","private-foreign-target","private-foreign-summary","sending","",1))}}
            override fun save(binding:HostRelayLinkBinding,code:HostRelayCode,now:Long) {
                assertEquals("acknowledged",runBlocking {db.dao().managementOperation(binding.receipt.key)}!!.state)
                if(mode=="save-cache") error("private-cache-error")
                cache[binding.receipt.key]=binding.owner() to code
                if(mode=="cancel-save") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
            }
            override fun read(binding:HostRelayLinkBinding,now:Long)=cache[binding.receipt.key]?.takeIf {it.first==binding.owner() && it.second.expiresAt>now}?.second
            override fun remove(binding:HostRelayLinkBinding) {if(mode=="remove-fail") error("private-remove-error");if(cache[binding.receipt.key]?.first==binding.owner()) cache.remove(binding.receipt.key)}
            override fun clear(target:ManagementTarget) {cache.entries.removeAll {it.value.first["api_scope"]==JsonPrimitive(target.apiScope) && it.value.first["url"]==JsonPrimitive(target.url) && it.value.first["profile"]==JsonPrimitive(target.profile) && it.value.first["actor"]==JsonPrimitive(target.actor)}}
        }
        try {MockWebServer().use {server->
            fun json(raw:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(raw.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer link-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val url=q.requestUrl!!
                return when {
                    url.encodedPath=="/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    url.encodedPath=="/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(PluginUpdateFixture.profiles(mode=="home" || mode=="readback-home" && sends.get()>0 || mode=="poll-home" && changed.get() || mode=="home-race" && n>=3))}
                    url.encodedPath=="/prefix/api/messaging/platforms"->{assertEquals("worker",url.queryParameter("profile"));json(HostRelayLinkFixture.channels(!(mode=="poll-mode" && changed.get())))}
                    url.encodedPath=="/prefix/api/messaging/relay"->{
                        assertEquals("GET",q.method);assertNull(url.query);reads.incrementAndGet()
                        if(mode=="cancel-readback" && sends.get()>0) {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        if(mode=="read409") json(buildJsonObject {put("detail",buildJsonObject {put("code","managed_by_portal")})}).setResponseCode(409)
                        else if(mode=="bad-readback" && sends.get()>0) MockResponse().setResponseCode(500)
                        else json(HostRelayLinkFixture.report(if(mode=="manager") "portal" else "connector",if(mode=="bot" || mode=="poll-bot" && changed.get()) "https://t.me/changed" else HostRelayLinkFixture.bot))
                    }
                    url.encodedPath=="/prefix/api/messaging/relay/telegram/link"->{
                        assertEquals("POST",q.method);assertNull(url.query);assertEquals("{}",q.body.readUtf8());assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single()}.state);sends.incrementAndGet()
                        if(mode=="cancel-post") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "portal-refusal","platform-refusal"->json(buildJsonObject {put("detail",buildJsonObject {put("code",if(mode=="portal-refusal") "managed_by_portal" else "platform_not_fronted");put("message","private-refusal-message")})}).setResponseCode(409)
                            "ambiguous","wrong-status","wrong-shape"->json(if(mode=="wrong-shape") buildJsonObject {put("error",buildJsonObject {put("code","managed_by_portal")})} else buildJsonObject {put("detail",buildJsonObject {put("code",if(mode=="ambiguous") "connector_refused" else "managed_by_portal")})}).setResponseCode(if(mode=="wrong-status") 503 else 409)
                            "html"->MockResponse().setResponseCode(409).setHeader("Content-Type","text/html").setBody("{\"detail\":{\"code\":\"managed_by_portal\"}}")
                            "lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "bad-platform"->json(JsonObject(HostRelayLinkFixture.issued()+mapOf("platform" to JsonPrimitive("discord"))))
                            "bad-command"->json(JsonObject(HostRelayLinkFixture.issued()+mapOf("command" to JsonPrimitive("/other AB12CD"))))
                            "missing-code"->json(JsonObject(HostRelayLinkFixture.issued()-"code"))
                            "optional"->json(JsonObject(HostRelayLinkFixture.issued()+mapOf("expires_at" to JsonPrimitive(1),"bot_url" to buildJsonObject {put("bad",true)})))
                            else->json(HostRelayLinkFixture.issued(codeValue))
                        }
                    }
                    url.encodedPath.startsWith("/prefix/api/messaging/relay/link/")->{
                        assertEquals("GET",q.method);assertEquals(codeValue,url.pathSegments.last());assertNull(url.query);assertEquals(0L,q.bodySize);states.incrementAndGet()
                        if(mode=="cancel-poll") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "bad-state"->json(buildJsonObject {put("state",true)})
                            "poll409"->json(buildJsonObject {put("detail",buildJsonObject {put("code","managed_by_portal")})}).setResponseCode(409)
                            "optional-reason"->json(buildJsonObject {put("state","inactive");put("reason",buildJsonObject {put("bad",true)})})
                            else->json(HostRelayLinkFixture.state(when(mode) {"inactive","remove-fail"->"inactive";"refused"->"refused";else->"pending"}))
                        }
                    }
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val api=ManagementClient(server.url("prefix/").toString(),true,true);val tokens=ManagementTokens("link-fixture","refresh-fixture","password","fixture",4102444800);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
            val home=PluginHome.project(JsonPrimitive(PluginUpdateFixture.home))!!.identity();val scope=HostRelayScope.from(HostChannelInventory.parse(HostRelayLinkFixture.channels(),target,home));val inventory=HostRelayInventory.parse(HostRelayLinkFixture.report(),scope);val request=HostRelayLinkRequest(inventory,"telegram");val reader=HostRelayRepository(HostChannelsRepository(db.dao(),CompletableDeferred(Unit)));val repo=HostRelayLinkRepository(db.dao(),CompletableDeferred(Unit),reader,store)
            if(mode=="pending") {db.dao().claimManagementOperation(ManagementOperation("other-pending",target.resourceScope,"b".repeat(64),target.url,target.profile,"private-other-actor","Other","private-other-target","private-summary","unknown","",1));assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(0,reads.get());return@use}
            if(mode in setOf("cancel-post","cancel-save","cancel-readback")) {val job=async {repo.execute(target,request,api,tokens)};assertTrue(withContext(Dispatchers.IO) {entered.await(10,TimeUnit.SECONDS)});job.cancel();release.countDown();job.join();val r=db.dao().managementOperations(target.apiScope).first().single();assertEquals(if(mode=="cancel-post") "unknown" else "acknowledged",r.state);assertEquals(1,sends.get());return@use}
            val result=runCatching {repo.execute(target,request,api,tokens)}
            if(mode in setOf("identity","home","home-race","manager","bot","pre-cache","full","race","read409")) {assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode=="read409") assertTrue(result.exceptionOrNull() is HermesHttpException);return@use}
            val receipt=db.dao().managementOperations(target.apiScope).first().single();assertEquals(1,sends.get())
            if(mode in setOf("ambiguous","wrong-status","wrong-shape","html","lost","redirect","bad-platform","bad-command","missing-code")) {assertTrue(result.exceptionOrNull() is HostRelayLinkUncertain);assertEquals("unknown",receipt.state);assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,sends.get());return@use}
            if(mode in setOf("bad-readback","readback-home")) {assertTrue(result.exceptionOrNull() is HostRelayLinkReadbackPending);assertEquals("acknowledged",receipt.state);assertTrue(cache.containsKey(receipt.key));return@use}
            val observation=result.getOrThrow();assertEquals("acknowledged",receipt.state)
            if(mode in setOf("portal-refusal","platform-refusal")) {assertFalse(observation.binding.issued);assertEquals("not_issued",observation.binding.status);assertTrue(cache.isEmpty());assertEquals(0,states.get());return@use}
            if(mode=="save-cache") {assertTrue(observation.binding.issued);assertNull(observation.code);assertFalse(observation.available);assertEquals(0,states.get());return@use}
            assertEquals("unverified",observation.binding.status);assertEquals(0,states.get());assertEquals(codeValue,observation.code!!.code);if(mode=="optional") assertNull(observation.code.botUrl)
            val n=reads.get();val local=repo.local(target,receipt.key);assertNull(local.code);assertTrue(local.available);assertFalse(local.scopeVerified);assertEquals(n,reads.get())
            if(mode.startsWith("poll-") && mode!="poll-pending") changed.set(true)
            if(mode in setOf("poll-home","poll-mode","poll-bot")) {assertTrue(runCatching {repo.poll(target,receipt.key,api,tokens)}.isFailure);assertEquals(0,states.get());assertEquals("acknowledged",db.dao().managementOperation(receipt.key)!!.state);return@use}
            if(mode=="expired") {val entry=cache[receipt.key]!!;cache[receipt.key]=entry.first to HostRelayCode(entry.second.code,entry.second.botUrl,entry.second.originalBot,System.currentTimeMillis()-1);assertFalse(repo.local(target,receipt.key).available);assertTrue(runCatching {repo.poll(target,receipt.key,api,tokens)}.exceptionOrNull() is HostRelayCodeUnavailable);assertEquals(0,states.get());return@use}
            if(mode=="cancel-poll") {val job=async {repo.poll(target,receipt.key,api,tokens)};assertTrue(withContext(Dispatchers.IO) {entered.await(10,TimeUnit.SECONDS)});job.cancelAndJoin();release.countDown();assertEquals("acknowledged",db.dao().managementOperation(receipt.key)!!.state);return@use}
            val polled=runCatching {repo.poll(target,receipt.key,api,tokens)}
            if(mode in setOf("bad-state","poll409")) {assertTrue(polled.isFailure);assertEquals("unverified",HostRelayLinkBinding.parse(db.dao().managementOperation(receipt.key)!!,target).status)}
            else {
                val out=polled.getOrThrow();val expected=when(mode) {"inactive","optional-reason","remove-fail"->"inactive";"refused"->"refused";else->"pending"};assertEquals(expected,out.binding.status);assertEquals(expected=="pending",out.code!=null)
                if(mode=="optional-reason") assertNull(out.reason)
                if(expected!="pending") {db.dao().observeRelayCodeState(receipt.key,"pending");assertEquals(expected,HostRelayLinkBinding.parse(db.dao().managementOperation(receipt.key)!!,target).status);val count=states.get();assertNull(repo.poll(target,receipt.key,api,tokens).code);assertFalse(repo.local(target,receipt.key).available);assertEquals(count,states.get())}
            }
            assertEquals(1,sends.get());val final=db.dao().managementOperation(receipt.key)!!;for(secret in listOf(codeValue,"/link",HostRelayLinkFixture.bot,HostRelayLinkFixture.reason,"private-refusal-message")) {assertFalse(final.requestSummary.contains(secret));assertFalse(final.resultSummary.contains(secret))}
        }} finally {release.countDown();db.close()}
    }
    @Test fun exactPostPrefixBearerEmptyBodyCapturesAckBeforePrivateSaveAndExplicitPoll()=exercise("normal")
    @Test fun knownPortalRefusalIsAcknowledgedWithoutIssuanceOrPendingBarrier()=exercise("portal-refusal")
    @Test fun knownNotFrontedRefusalIsAcknowledgedBeforeMint()=exercise("platform-refusal")
    @Test fun arbitraryWrongStatusWrongShapeAndHtml409CannotPretendPreMintRefusal() {for(m in listOf("ambiguous","wrong-status","wrong-shape","html")) exercise(m)}
    @Test fun lostCreationKeepsUnknownAndCannotMintAgain()=exercise("lost")
    @Test fun redirectsNeverForwardCapabilityRequestsOrBearer()=exercise("redirect")
    @Test fun wrongPlatformCommandAndMissingCodeKeepUnknown() {for(m in listOf("bad-platform","bad-command","missing-code")) exercise(m)}
    @Test fun preflightActorHomeAndSelectedBotOrAuthorityChangesRefuseBeforeClaim() {for(m in listOf("identity","home","home-race","manager","bot")) exercise(m)}
    @Test fun inaccessibleOrFullPrivateCacheRefusesBeforeClaimAndMint() {exercise("pre-cache");exercise("full")}
    @Test fun sharedClaimRaceCannotStartAnotherMint()=exercise("race")
    @Test fun foreignPendingWorkBlocksBeforePrivateReads()=exercise("pending")
    @Test fun cancellationDuringMintKeepsUnknown()=exercise("cancel-post")
    @Test fun cancellationDuringOptionalSaveCannotEraseKnownIssuance()=exercise("cancel-save")
    @Test fun cancellationDuringReadbackRetainsAck()=exercise("cancel-readback")
    @Test fun failedOrChangedHomeReadbackKeepsAckAndEncryptedOriginal() {exercise("bad-readback");exercise("readback-home")}
    @Test fun originalPendingCodeCanBeResumedExplicitlyWithoutMintingAgain()=exercise("poll-pending")
    @Test fun inactiveMeansUsedOrExpiredAndCannotBecomePendingAgain()=exercise("inactive")
    @Test fun refusedObservationHidesCodeWithoutClaimingAccountLink()=exercise("refused")
    @Test fun malformedOptionalReasonCannotEraseKnownInactiveStatus()=exercise("optional-reason")
    @Test fun changedPollHomeModeOrBotCannotSendThePrivateCode() {for(m in listOf("poll-home","poll-mode","poll-bot")) exercise(m)}
    @Test fun cancelledStatusReadKeepsIssuanceAck()=exercise("cancel-poll")
    @Test fun expiredLocalCodeCannotBeQueriedOrReplacedAutomatically()=exercise("expired")
    @Test fun failedPrivateSaveRetainsAckWithoutInventingARecoverableCode()=exercise("save-cache")
    @Test fun invalidOptionalExpiryAndBotDoNotEraseMandatoryAck()=exercise("optional")
    @Test fun refusalWhitelistAppliesOnlyToMintPostNotReadOrStatus() {exercise("read409");exercise("poll409")}
    @Test fun terminalRoomObservationBlocksRestorationEvenWhenCacheDeletionFails()=exercise("remove-fail")
    @Test fun opaqueUnicodeAndPunctuationCodesAreEncodedAsOneOriginalPathSegment()=exercise("opaque")
    @Test fun malformedMandatoryStatusDoesNotChangeIssuanceOrCreateAnotherCode()=exercise("bad-state")
}
