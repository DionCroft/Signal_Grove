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

class HostFallbackStorageTest {
    private val tokens=ManagementTokens("fallback-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val current=AtomicReference(HostFallbackFixture.config());val sends=AtomicInteger();val reads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            lateinit var target:ManagementTarget;lateinit var request:HostFallbackRequest
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer fallback-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val u=q.requestUrl!!
                return when {
                    u.encodedPath=="/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    u.encodedPath=="/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(if(mode=="home" || mode=="after-home" && n==2 || mode=="readback-home" && n>=3) buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path","/different/home")})})} else PluginUpdateFixture.profiles())}
                    u.encodedPath=="/prefix/api/config" && q.method=="GET"->{
                        assertEquals("worker",u.queryParameter("profile"));assertEquals("false",u.queryParameter("include_defaults"));val n=reads.incrementAndGet()
                        if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when {mode=="readback-error" && n==2->MockResponse().setResponseCode(500).setBody("private-host-error");mode in setOf("secret","legacy") && n==1->json(HostFallbackFixture.config(mode));else->json(current.get())}
                    }
                    u.encodedPath=="/prefix/api/config" && q.method=="PUT"->{
                        assertEquals("worker",u.queryParameter("profile"));assertNull(u.queryParameter("include_defaults"));assertNull(u.queryParameter("preserve_language"));assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});sends.incrementAndGet()
                        val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(request.body(),body);assertEquals(setOf("config","profile"),body.keys);assertEquals(setOf("fallback_providers"),body["config"]!!.jsonObject.keys);val rows=body["config"]!!.jsonObject["fallback_providers"]!!.jsonArray;assertEquals(JsonPrimitive(17),rows[1]);assertEquals(HostFallbackFixture.row(),rows[0]);assertEquals("secondary-model",rows[2].jsonObject.text("model"))
                        current.set(JsonObject(current.get()+body["config"]!!.jsonObject));if(mode=="different") current.set(HostFallbackFixture.config())
                        if(mode=="cancel-write") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {"lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);"http"->MockResponse().setResponseCode(400).setBody("private-host-error");"redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"));"badack"->json(buildJsonObject {put("ok","true")});"missingack"->json(JsonObject(emptyMap()));else->json(HostFallbackFixture.ack())}
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val expected=HostFallbackSnapshot.parse(HostFallbackFixture.config(),target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home))!!.identity());request=HostFallbackRequest(HostFallbackDraft.from(expected).move(FallbackSection.Primary,2,-1));val repo=HostFallbackRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel-write","cancel-readback")) {val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};job.cancelAndJoin();release.countDown()}
                else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "identity","home","after-home","secret","legacy"->{assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode in setOf("identity","home")) assertEquals(0,reads.get());return@use}
                        "lost","http","redirect","badack","missingack"->assertTrue(result.exceptionOrNull() is HostFallbackUncertain)
                        "readback-error","readback-home"->assertTrue(result.exceptionOrNull() is HostFallbackReadbackPending)
                        else->{val out=result.getOrThrow();assertEquals("acknowledged",out.receipt.state);assertEquals(mode!="different",out.matches)}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipt=db.dao().managementOperations(target.apiScope).first().single();assertEquals(1,sends.get());assertEquals(0,other.get());assertEquals("FallbackConfig",receipt.action)
                for(secret in listOf(HostFallbackFixture.inline,HostFallbackFixture.fresh,HostFallbackFixture.endpoint,HostFallbackFixture.env,HostFallbackFixture.extra,"primary-model","custom:fixture","fallback-management-fixture","private-host-error")) assertFalse(secret,receipt.toString().contains(secret))
                if(mode=="cancel-readback") assertEquals("acknowledged",receipt.state)
                if(receipt.state=="unknown") {val before=reads.get();assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);val rotated=target.copy(actor="password · rotated");val snapshot=HostFallbackSnapshot.parse(HostFallbackFixture.config(),rotated,expected.homeId);assertTrue(runCatching {repo.execute(rotated,HostFallbackRequest(HostFallbackDraft.from(snapshot).clear()),api,ManagementTokens("other","other","password","rotated",4102444800))}.exceptionOrNull() is ManagementOutcomePending);assertEquals(before,reads.get());assertEquals(1,sends.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun exactScopedOverlayPreservesPrivateRowsAndClaimsBeforeOnePut()=exercise("success")
    @Test fun laterDifferentFieldsDoNotEraseAcknowledgment()=exercise("different")
    @Test fun wrongActorRefusesBeforeConfigRead()=exercise("identity")
    @Test fun changedHomeRefusesBeforeConfigRead()=exercise("home")
    @Test fun changedHomeAfterConfigReadRefusesBeforeClaim()=exercise("after-home")
    @Test fun changedPrivateCredentialRefusesBeforeClaim()=exercise("secret")
    @Test fun changedUntouchedLegacyChainRefusesBeforeClaim()=exercise("legacy")
    @Test fun lostPutBlocksAcrossRecoveryClearAndActorRotation()=exercise("lost")
    @Test fun arbitraryPostMutation400RemainsUnknown()=exercise("http")
    @Test fun redirectNeverFollowsOrReplays()=exercise("redirect")
    @Test fun stringSuccessFlagCannotAcknowledge()=exercise("badack")
    @Test fun missingMandatorySuccessCannotAcknowledge()=exercise("missingack")
    @Test fun failedReadbackRetainsKnownAck()=exercise("readback-error")
    @Test fun changedReadbackHomeRetainsKnownAck()=exercise("readback-home")
    @Test fun canceledWriteLeavesUnknown()=exercise("cancel-write")
    @Test fun canceledReadbackCannotEraseKnownAck()=exercise("cancel-readback")
}
