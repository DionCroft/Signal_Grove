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

class CustomEndpointStorageTest {
    private val tokens=ManagementTokens("endpoint-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val kind=if(mode=="legacy") "legacy" else "provider"
        val initialMetadata=if(mode=="body-id") buildJsonObject {put("endpoints",JsonArray(listOf(CustomEndpointFixture.row("Team/Model?%entry"))));put("current",CustomEndpointFixture.route())} else CustomEndpointFixture.metadata(kind)
        val initialConfig=if(mode=="body-id") buildJsonObject {put("providers",buildJsonObject {put("Team/Model?%entry",CustomEndpointFixture.provider())});put("custom_providers",JsonArray(emptyList()))} else CustomEndpointFixture.config(kind)
        val current=AtomicReference(initialMetadata);val config=AtomicReference(initialConfig);val sends=AtomicInteger();val reads=AtomicInteger();val rawReads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            lateinit var target:ManagementTarget;lateinit var request:CustomEndpointRequest
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer endpoint-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val u=q.requestUrl!!
                return when {
                    u.encodedPath=="/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    u.encodedPath=="/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(if(mode=="home" || mode=="after-home" && n==2 || mode=="readback-home" && n>=3) buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path","/changed/home")})})} else PluginUpdateFixture.profiles())}
                    u.encodedPath=="/prefix/api/config" && q.method=="GET"->{assertEquals("worker",u.queryParameter("profile"));assertEquals("false",u.queryParameter("include_defaults"));rawReads.incrementAndGet();json(if(mode in setOf("private","collision")) CustomEndpointFixture.config(kind,mode) else config.get())}
                    u.encodedPath=="/prefix/api/providers/custom-endpoints" && q.method=="GET"->{
                        assertEquals("worker",u.queryParameter("profile"));val n=reads.incrementAndGet();if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        if(mode=="readback-error" && n==2) MockResponse().setResponseCode(500).setBody("private-endpoint-error") else json(current.get())
                    }
                    u.pathSegments.take(4)==listOf("prefix","api","providers","custom-endpoints") && q.method in setOf("POST","DELETE")->{
                        assertEquals("worker",u.queryParameter("profile"));assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});sends.incrementAndGet()
                        val ack=when(request.action) {
                            CustomEndpointAction.Save->{assertEquals("POST",q.method);assertEquals(4,u.pathSegments.size);val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(request.draft!!.body(),body);assertFalse(body.containsKey("profile"));assertFalse(body.containsKey("confirm_expensive_model"));assertFalse(body.toString().contains(CustomEndpointFixture.inline));assertFalse(body.toString().contains(CustomEndpointFixture.header));if(mode!="different") {current.set(CustomEndpointFixture.saved(current.get(),body));config.set(CustomEndpointFixture.savedConfig(config.get(),body))};CustomEndpointFixture.ack(id=if(mode=="normalized") "normalized-id" else request.id)}
                            CustomEndpointAction.Activate->{assertEquals("POST",q.method);assertEquals(listOf(request.id,"activate"),u.pathSegments.drop(4));assertEquals(JsonObject(emptyMap()),Json.parseToJsonElement(q.body.readUtf8()));if(mode!="activate-different") current.set(CustomEndpointFixture.activated(current.get(),request.id));CustomEndpointFixture.ack(provider=request.id,model=request.row!!.model)}
                            CustomEndpointAction.Remove->{assertEquals("DELETE",q.method);assertEquals(listOf(request.id),u.pathSegments.drop(4));assertEquals(0L,q.bodySize);if(mode!="remove-still") {current.set(JsonObject(current.get()+mapOf("endpoints" to JsonArray(emptyList()))));config.set(buildJsonObject {put("providers",JsonObject(emptyMap()));put("custom_providers",JsonArray(emptyList()))})};CustomEndpointFixture.ack()}
                        }
                        if(mode=="cancel-write") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "lost","remove-lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "http"->MockResponse().setResponseCode(400).setBody("private-endpoint-partial-write-error")
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "badack"->json(JsonObject(ack+mapOf("ok" to JsonPrimitive("true"))))
                            "missing-id"->json(JsonObject(ack-"id"))
                            "missing-model"->json(JsonObject(ack-"model"))
                            "optional"->json(JsonObject(ack+mapOf("endpoints" to JsonPrimitive("private-optional-error"),"current" to JsonPrimitive(17))))
                            else->json(ack)
                        }
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val expected=CustomEndpointSnapshot.parse(initialMetadata,initialConfig,target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home))!!.identity());val row=expected.rows.single();val create=mode in setOf("create","collision")
                request=when(mode) {
                    "activate","activate-different","missing-model"->CustomEndpointRequest.activate(expected,row)
                    "remove","remove-still","remove-lost"->CustomEndpointRequest.remove(expected,row)
                    else->{val draft=if(create) CustomEndpointDraft(CustomEndpointFixture.newId,"New endpoint fixture",CustomEndpointFixture.url,CustomEndpointFixture.model,CustomEndpointKey.Fresh,CustomEndpointFixture.fresh,"",8192,false,true,listOf("added-model")) else CustomEndpointDraft(row.id,"Updated endpoint fixture",row.baseUrl,row.model,if(mode=="clear") CustomEndpointKey.Clear else CustomEndpointKey.Keep);CustomEndpointRequest.save(expected,draft,if(create) null else row)}
                }
                val repo=CustomEndpointRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel-write","cancel-readback")) {val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};job.cancelAndJoin();release.countDown()}
                else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "identity","home","after-home","private","collision"->{assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode in setOf("identity","home")) {assertEquals(0,reads.get());assertEquals(0,rawReads.get())};return@use}
                        "lost","remove-lost","http","redirect","badack","missing-id","missing-model"->assertTrue(result.exceptionOrNull() is CustomEndpointUncertain)
                        "readback-error","readback-home"->assertTrue(result.exceptionOrNull() is CustomEndpointReadbackPending)
                        else->{val out=result.getOrThrow();assertEquals("acknowledged",out.receipt.state);assertEquals(mode in setOf("different","normalized","remove-still","activate-different"),out.different);if(create) assertTrue(out.snapshot.canAddress(out.snapshot.rows.single {it.id==CustomEndpointFixture.newId}))}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipt=db.dao().managementOperations(target.apiScope).first().single();assertEquals(1,sends.get());assertEquals(0,other.get());assertEquals(request.action.receiptAction,receipt.action)
                for(secret in listOf(CustomEndpointFixture.id,CustomEndpointFixture.name,CustomEndpointFixture.model,CustomEndpointFixture.url,CustomEndpointFixture.inline,CustomEndpointFixture.header,CustomEndpointFixture.preview,CustomEndpointFixture.fresh,"PRIVATE_ENDPOINT_ALIAS","endpoint-management-fixture","private-endpoint-error","private-optional-error")) assertFalse(secret,receipt.toString().contains(secret))
                if(mode=="cancel-readback") assertEquals("acknowledged",receipt.state)
                if(receipt.state=="unknown") {
                    val before=reads.get();val beforeRaw=rawReads.get();assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                    val rotated=target.copy(actor="password · rotated");val snapshot=CustomEndpointSnapshot.parse(CustomEndpointFixture.metadata(),CustomEndpointFixture.config(),rotated,expected.homeId);assertTrue(runCatching {repo.execute(rotated,CustomEndpointRequest.remove(snapshot,snapshot.rows.single()),api,ManagementTokens("other","other","password","rotated",4102444800))}.exceptionOrNull() is ManagementOutcomePending)
                    val fallback=HostFallbackSnapshot.parse(JsonObject(emptyMap()),target,expected.homeId);assertTrue(runCatching {HostFallbackRepository(db.dao(),CompletableDeferred(Unit)).execute(target,HostFallbackRequest(HostFallbackDraft.from(fallback).clear()),api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(before,reads.get());assertEquals(beforeRaw,rawReads.get());assertEquals(1,sends.get())
                }
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun creationSendsExactReviewedFieldsAndFreshCredentialOnceAfterClaim()=exercise("create")
    @Test fun bodyOnlyIdIsPreservedInJsonWithoutBeingAddedToTheRequestPath()=exercise("body-id")
    @Test fun editsPreserveLiteralStoredIdAndOmitKeptCredentialModeAndContext()=exercise("edit")
    @Test fun explicitBlankKeyIsSentForClearWithoutClaimingRevocation()=exercise("clear")
    @Test fun activationUsesExactStoredIdAndEmptyBodyWithCapturedRouteAck()=exercise("activate")
    @Test fun removalUsesExactStoredIdWithoutSecretOrSyntheticRevision()=exercise("remove")
    @Test fun uniqueLegacyEntryCanBePromotedByReviewedSave()=exercise("legacy")
    @Test fun malformedOptionalInventoryCannotEraseTypedAck()=exercise("optional")
    @Test fun normalizedIdRemainsKnownAckWithDifferentNotice()=exercise("normalized")
    @Test fun unchangedLaterFieldsAreObservationRatherThanErasedAck()=exercise("different")
    @Test fun remainingRemovedIdKeepsAckAndShowsObservationNotice()=exercise("remove-still")
    @Test fun differingMainReadbackDoesNotEraseActivationAck()=exercise("activate-different")
    @Test fun wrongActorRefusesBeforePrivateConfigOrCatalogRead()=exercise("identity")
    @Test fun changedHomeRefusesBeforePrivateRead()=exercise("home")
    @Test fun changedHomeAfterCatalogRefusesBeforeClaim()=exercise("after-home")
    @Test fun changedPrivateHeaderRefusesBeforeClaim()=exercise("private")
    @Test fun newHiddenProviderCollisionRefusesBeforeCreationClaim()=exercise("collision")
    @Test fun lostSaveBlocksAcrossRecoveryClearRotationAndOtherMutators()=exercise("lost")
    @Test fun lostRemovalNeverReplays()=exercise("remove-lost")
    @Test fun arbitraryPostMutation400DoesNotProveNoCredentialSideEffect()=exercise("http")
    @Test fun redirectsNeverFollowOrRetry()=exercise("redirect")
    @Test fun stringSuccessFlagCannotAcknowledge()=exercise("badack")
    @Test fun missingMandatorySavedIdRemainsUnknown()=exercise("missing-id")
    @Test fun missingMandatoryActivatedModelRemainsUnknown()=exercise("missing-model")
    @Test fun failedReadbackKeepsCapturedAck()=exercise("readback-error")
    @Test fun changedReadbackHomeKeepsCapturedAck()=exercise("readback-home")
    @Test fun canceledWriteRemainsUnknownWithoutReplay()=exercise("cancel-write")
    @Test fun canceledReadbackCannotEraseKnownAck()=exercise("cancel-readback")
}
