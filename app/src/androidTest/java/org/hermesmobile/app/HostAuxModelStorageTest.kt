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

class HostAuxModelStorageTest {
    private val tokens=ManagementTokens("aux-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val sends=AtomicInteger();val reads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val current=AtomicReference(HostAuxModelFixture.auxiliary());val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val effort=when(mode) {"inherit"->HostAuxEffort.Inherit;"disabled"->HostAuxEffort.None;"high"->HostAuxEffort.High;else->HostAuxEffort.Keep}
        MockWebServer().use {server->
            lateinit var target:ManagementTarget
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer aux-management-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));val url=q.requestUrl!!
                return when(url.encodedPath) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(PluginUpdateFixture.profiles(mode=="home" || mode=="readback-home" && n>2))}
                    "/prefix/api/model/auxiliary"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));val n=reads.incrementAndGet();if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)};json(if(mode=="changed") HostAuxModelFixture.auxiliary(changed=true) else if(mode=="new-plugin") HostAuxModelFixture.auxiliary(extra=true) else if(mode=="readback") HostAuxModelFixture.auxiliary() else current.get())}
                    "/prefix/api/model/options"->{assertEquals("GET",q.method);assertEquals("worker",url.queryParameter("profile"));assertEquals("false",url.queryParameter("refresh"));assertEquals("true",url.queryParameter("include_unconfigured"));json(HostMainModelFixture.catalog(blocked=mode=="blocked"))}
                    "/prefix/api/model/set"->{
                        assertEquals("POST",q.method);assertEquals("worker",url.queryParameter("profile"));val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                        assertEquals(setOf("scope","profile","task","provider","model","confirm_expensive_model")+if(effort==HostAuxEffort.Keep) emptySet() else setOf("reasoning_effort"),body.keys);assertEquals(JsonPrimitive("auxiliary"),body["scope"]);assertEquals(JsonPrimitive("worker"),body["profile"]);assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state});val n=sends.incrementAndGet();assertEquals(JsonPrimitive(mode=="warning" && n==2),body["confirm_expensive_model"])
                        if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "http"->MockResponse().setResponseCode(400).setBody("private-host-error")
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "untyped"->json(JsonObject(HostAuxModelFixture.saved()+mapOf("ok" to JsonPrimitive("true"))))
                            "badack"->json(JsonObject(HostAuxModelFixture.saved()+mapOf("tasks" to buildJsonArray {add("wrong.task")})))
                            "badwarning"->json(JsonObject(HostAuxModelFixture.warning()+mapOf("provider" to JsonPrimitive("other"))))
                            "warning"->if(n==1) json(HostAuxModelFixture.warning()) else {current.set(HostAuxModelFixture.auxiliary(HostMainModelFixture.next));json(HostAuxModelFixture.saved())}
                            "reset"->{assertEquals(JsonPrimitive("__reset__"),body["task"]);assertEquals(JsonPrimitive(""),body["provider"]);assertEquals(JsonPrimitive(""),body["model"]);current.set(HostAuxModelFixture.auxiliary(reset=true));json(buildJsonObject {put("ok",true);put("scope","auxiliary");put("reset",true)})}
                            "bulk"->{assertEquals(JsonPrimitive(""),body["task"]);val after=HostAuxModelFixture.auxiliary(HostMainModelFixture.next,extra=true);current.set(JsonObject(after+mapOf("tasks" to JsonArray(after["tasks"]!!.jsonArray.map {JsonObject(it.jsonObject+mapOf("provider" to JsonPrimitive(HostMainModelFixture.provider),"model" to JsonPrimitive(HostMainModelFixture.next)))}))));json(HostAuxModelFixture.saved(listOf(HostAuxModelFixture.task,"voice_chat","plugin.task","later.plugin")))}
                            else->{assertEquals(JsonPrimitive(HostAuxModelFixture.task),body["task"]);assertEquals(JsonPrimitive(HostMainModelFixture.provider),body["provider"]);assertEquals(JsonPrimitive(HostMainModelFixture.next),body["model"]);val reported=if(effort==HostAuxEffort.Keep) "low" else effort.wire;current.set(HostAuxModelFixture.auxiliary(HostMainModelFixture.next,reported));json(HostAuxModelFixture.saved(effort=if(effort==HostAuxEffort.Keep) null else effort.wire?.let(::JsonPrimitive) ?: JsonNull))}
                        }
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val initial=HostModelInventory.parse(HostAuxModelFixture.auxiliary(),HostMainModelFixture.catalog(),target,PluginHome.project(JsonPrimitive(PluginUpdateFixture.home)))
                val request=if(mode=="reset") HostAuxModelRequest.reset(initial) else HostAuxModelRequest.assign(initial,if(mode=="bulk") "" else HostAuxModelFixture.task,HostModelRoute(HostMainModelFixture.provider,HostMainModelFixture.next),effort)
                val repo=HostAuxModelRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel","cancel-readback")) {
                    val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};if(mode=="cancel") assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown()
                } else {
                    val result=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "changed","new-plugin","blocked","home","identity"->{assertTrue(result.isFailure);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode in setOf("home","identity")) assertEquals(0,reads.get());return@use}
                        "lost","http","redirect","untyped","badack","badwarning"->assertTrue(result.exceptionOrNull() is HostAuxModelUncertain)
                        "readback","readback-home"->assertTrue(result.exceptionOrNull() is HostAuxModelReadbackPending)
                        "warning"->{val out=result.getOrThrow() as HostAuxModelOutcome.Warning;assertEquals(1,sends.get());assertSame(out.inventory,out.warning.request.expected);assertEquals("acknowledged",out.receipt.state);assertEquals(JsonPrimitive(false),Json.parseToJsonElement(out.receipt.resultSummary).jsonObject["reported_assigned"]);repo.execute(target,out.warning.request.approve(out.warning),api,tokens) as HostAuxModelOutcome.Saved;assertEquals(2,sends.get())}
                        else->{val out=result.getOrThrow() as HostAuxModelOutcome.Saved;assertEquals("acknowledged",out.receipt.state);if(mode=="bulk") assertEquals(4,Json.parseToJsonElement(out.receipt.resultSummary).jsonObject["reported_tasks"]!!.jsonArray.size)}
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipts=db.dao().managementOperations(target.apiScope).first();assertEquals(if(mode=="warning") 2 else 1,receipts.size);assertEquals(if(mode=="warning") 2 else 1,sends.get());assertEquals(0,other.get())
                for(receipt in receipts) {assertEquals("AuxModel",receipt.action);for(value in listOf("private-",HostMainModelFixture.warningText,"aux-management-fixture")) assertFalse(value,receipt.toString().contains(value))}
                if(mode=="cancel-readback") assertEquals("acknowledged",receipts.single().state)
                if(receipts.any {it.state=="unknown"}) {val count=sends.get();assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(count,sends.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun claimBeforeOneScopedSaveAndSeparateConfigurationReadback()=exercise("success")
    @Test fun bulkAcknowledgementRetainsNewPluginAffectedList()=exercise("bulk")
    @Test fun resetUsesDedicatedSentinelAndNeverClaimsExactAffectedList()=exercise("reset")
    @Test fun inheritSendsNullAndObservesUnreportedReasoning()=exercise("inherit")
    @Test fun disabledSendsNoneRatherThanNull()=exercise("disabled")
    @Test fun canonicalReasoningLevelIsExplicitlyAcknowledged()=exercise("high")
    @Test fun knownWarningNeedsSeparateExplicitApprovalAndFreshClaim()=exercise("warning")
    @Test fun changedTaskConfigurationRefusesBeforeClaim()=exercise("changed")
    @Test fun newlyRegisteredPluginBeforePreflightRefusesReview()=exercise("new-plugin")
    @Test fun newlyRestrictedModelRefusesBeforeClaim()=exercise("blocked")
    @Test fun changedHomeRefusesBeforeModelRead()=exercise("home")
    @Test fun changedActorRefusesBeforeModelRead()=exercise("identity")
    @Test fun lostReplySurvivesRecoveryAndChatClearWithoutReplay()=exercise("lost")
    @Test fun arbitraryHttpRefusalRemainsUnknown()=exercise("http")
    @Test fun redirectIsNeverFollowedAndRemainsUnknown()=exercise("redirect")
    @Test fun untypedSuccessRemainsUnknown()=exercise("untyped")
    @Test fun wrongAffectedTaskRemainsUnknown()=exercise("badack")
    @Test fun mismatchedWarningRemainsUnknown()=exercise("badwarning")
    @Test fun staleReadbackCannotEraseCapturedAcknowledgement()=exercise("readback")
    @Test fun changedReadbackHomeCannotEraseAcknowledgement()=exercise("readback-home")
    @Test fun cancelledSaveAndConcurrentMutationNeverReplay()=exercise("cancel")
    @Test fun cancelledReadbackPreservesAcknowledgement()=exercise("cancel-readback")
}
