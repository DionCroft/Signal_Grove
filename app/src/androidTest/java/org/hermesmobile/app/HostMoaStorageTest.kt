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

class HostMoaStorageTest {
    private val tokens=ManagementTokens("moa-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build();val sends=AtomicInteger();val reads=AtomicInteger();val modelReads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val current=AtomicReference(HostMoaFixture.initial());val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            lateinit var target:ManagementTarget
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer moa-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val url=q.requestUrl!!
                return when(url.encodedPath) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(PluginUpdateFixture.profiles(mode=="home" || mode=="readback-home" && n>3))}
                    "/prefix/api/model/auxiliary"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));modelReads.incrementAndGet();json(HostMainModelFixture.auxiliary())}
                    "/prefix/api/model/options"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));assertEquals("false",url.queryParameter("refresh"));assertEquals("true",url.queryParameter("include_unconfigured"));json(HostMoaFixture.catalog(mode=="provider",mode=="blocked"))}
                    "/prefix/api/model/moa"->{assertEquals("worker",url.queryParameter("profile"));if(q.method=="GET") {
                        val n=reads.incrementAndGet();if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        if(mode=="readback-error" && n==2) MockResponse().setResponseCode(500).setBody("private-readback-error") else json(if(mode=="changed") HostMoaFixture.initial(changed=true) else if(mode=="readback") HostMoaFixture.initial() else current.get())
                    } else {
                        assertEquals("PUT",q.method);val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(setOf("profile","default_preset","active_preset","presets"),body.keys);assertEquals(JsonPrimitive("worker"),body["profile"]);assertEquals(setOf("main","archive"),body["presets"]!!.jsonObject.keys);assertEquals(JsonPrimitive(""),body["active_preset"]);assertFalse(body.toString().contains("private"));assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});sends.incrementAndGet()
                        if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "http"->MockResponse().setResponseCode(422).setBody("private-host-error")
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "untyped"->json(JsonObject(HostMoaFixture.after(body)+mapOf("ok" to JsonPrimitive("true"))))
                            "badack"->json(JsonObject(HostMoaFixture.after(body)-"presets"+mapOf("ok" to JsonPrimitive(true))))
                            "badalias"->json(JsonObject(HostMoaFixture.after(body)+mapOf("ok" to JsonPrimitive(true),"fanout" to JsonPrimitive("per_iteration"))))
                            else->{current.set(HostMoaFixture.after(body,mode=="normalized"));json(JsonObject(current.get()+mapOf("ok" to JsonPrimitive(true))))}
                        }
                    }}
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val catalog=HostModelInventory.parse(HostMainModelFixture.auxiliary(),HostMoaFixture.catalog(),target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home)))
                val expected=HostMoaSnapshot(catalog,HostMoaConfig.parse(HostMoaFixture.initial()));val preset=expected.config.presets.getValue("main");val desired=expected.config.copy(presets=expected.config.presets+("main" to preset.copy(references=preset.references.map {it.copy(route=HostModelRoute(HostMainModelFixture.provider,HostMainModelFixture.next))})),activePreset="");val request=HostMoaRequest(expected,desired);val repo=HostMoaRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel","cancel-readback")) {
                    val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};if(mode=="cancel") assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown()
                } else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "changed","provider","blocked","home","identity"->{assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode in setOf("home","identity")) assertEquals(0,modelReads.get());return@use}
                        "lost","http","redirect","untyped","badack","badalias"->assertTrue(result.exceptionOrNull() is HostMoaUncertain)
                        "readback","readback-home","readback-error"->assertTrue(result.exceptionOrNull() is HostMoaReadbackPending)
                        else->{val out=result.getOrThrow();assertEquals(mode=="normalized",out.normalized);assertEquals("acknowledged",out.receipt.state);assertEquals(if(mode=="normalized") "archive" else "",out.snapshot.config.activePreset)}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipts=db.dao().managementOperations(target.apiScope).first();assertEquals(1,receipts.size);assertEquals(1,sends.get());assertEquals(0,other.get());val receipt=receipts.single();assertEquals("MoaConfig",receipt.action);assertFalse(receipt.toString().contains("private"));assertFalse(receipt.toString().contains("moa-management-fixture"));assertFalse(receipt.resultSummary.contains("reference_models"))
                if(mode=="cancel-readback") assertEquals("acknowledged",receipt.state)
                if(receipt.state=="unknown") {
                    val count=modelReads.get();assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                    assertTrue(runCatching {HostMainModelRepository(db.dao(),CompletableDeferred(Unit)).execute(target,HostMainModelRequest(catalog,HostMainModelFixture.provider,HostMainModelFixture.next),api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                    val rotated=target.copy(actor="password · rotated");val nextCatalog=HostModelInventory(rotated,catalog.homeId,catalog.main,catalog.auxiliary,catalog.providers,false);val next=HostMoaRequest(HostMoaSnapshot(nextCatalog,expected.config),desired);assertTrue(runCatching {repo.execute(rotated,next,api,ManagementTokens("other","other","password","rotated",4102444800))}.exceptionOrNull() is ManagementOutcomePending);assertEquals(count,modelReads.get());assertEquals(1,sends.get())
                }
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun oneScopedPutFollowsSharedClaimAndSeparateReadback()=exercise("success")
    @Test fun differentNormalizedMapIsAcknowledgedRatherThanMisclassifiedAsLost()=exercise("normalized")
    @Test fun changedFullMapRefusesBeforeClaim()=exercise("changed")
    @Test fun changedChosenProviderMetadataRefusesBeforeClaim()=exercise("provider")
    @Test fun newlyRestrictedModelRefusesBeforeClaim()=exercise("blocked")
    @Test fun changedHomeRefusesBeforeProviderOrMoaReads()=exercise("home")
    @Test fun changedActorRefusesBeforeProviderOrMoaReads()=exercise("identity")
    @Test fun lostReplyBlocksMoaMainAndRotatedActorAcrossRecoveryAndChatClear()=exercise("lost")
    @Test fun arbitraryHttp422CannotBeAssumedPreMutation()=exercise("http")
    @Test fun redirectNeverFollowsAndRemainsUnknown()=exercise("redirect")
    @Test fun stringStatusCannotAcknowledge()=exercise("untyped")
    @Test fun incompleteSuccessCannotAcknowledge()=exercise("badack")
    @Test fun contradictoryAliasesCannotAcknowledge()=exercise("badalias")
    @Test fun staleReadbackPreservesCapturedAcknowledgement()=exercise("readback")
    @Test fun changedReadbackHomePreservesAcknowledgement()=exercise("readback-home")
    @Test fun failedReadbackPreservesAcknowledgement()=exercise("readback-error")
    @Test fun cancelledSaveNeverReplaysOrAllowsAnotherMutation()=exercise("cancel")
    @Test fun cancelledReadbackRetainsCapturedAcknowledgement()=exercise("cancel-readback")
}
