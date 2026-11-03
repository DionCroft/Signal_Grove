package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HubInstallStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("hub-installer-fixture","refresh-fixture","password","fixture",4102444800)
    private fun json(j:JsonElement)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val identifier=if(mode=="lost") "https://skills.example/fixture?credential=install-private-fixture" else "official/general/fixture"
        val previewJson=JsonObject(SkillHubFixture.preview()+mapOf("identifier" to JsonPrimitive(identifier)))
        val selected=SkillHubProtocol.official(buildJsonObject {put("skills",buildJsonArray {add(JsonObject(SkillHubFixture.entry()+mapOf("identifier" to JsonPrimitive(identifier))))})}).single()
        val scanJson=JsonObject(SkillHubFixture.scan()+mapOf("identifier" to JsonPrimitive(identifier),"name" to JsonPrimitive("Official fixture"),"source" to JsonPrimitive(identifier),"policy" to JsonPrimitive("allow")))
        val request=HubInstallRequest(SkillHubProtocol.preview(previewJson,selected),SkillHubProtocol.scan(scanJson,selected))
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer hub-installer-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"))
                if(q.requestUrl!!.encodedPath!="/dashboard/api/auth/me") assertEquals("worker",q.requestUrl!!.queryParameter("profile"))
                return when(q.requestUrl!!.encodedPath) {
                    "/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture")})
                    "/dashboard/api/skills"->json(if(mode=="duplicate") buildJsonArray {add(buildJsonObject {put("name","Official fixture");put("description","Fixture");put("enabled",true)})} else JsonArray(emptyList()))
                    "/dashboard/api/skills/hub/sources"->json(JsonObject(SkillHubFixture.sources()+mapOf("installed" to JsonObject(emptyMap()))))
                    "/dashboard/api/skills/hub/preview"->json(if(mode=="changed") JsonObject(previewJson+mapOf("skill_md" to JsonPrimitive("# Changed since review"))) else previewJson)
                    "/dashboard/api/skills/hub/scan"->json(if(mode=="policy") JsonObject(scanJson+mapOf("policy" to JsonPrimitive("ask"))) else scanJson)
                    "/dashboard/api/skills/hub/install"->{
                        assertEquals("POST",q.method);val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                        assertEquals(buildJsonObject {put("identifier",identifier);put("profile","worker")},body)
                        // The durable claim must exist before the fixture observes the POST.
                        assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(ManagementTarget("a".repeat(64),server.url("dashboard/").toString(),"worker","password · fixture").resourceScope).single().state})
                        writes.incrementAndGet();entered.countDown()
                        if(mode=="cancel") release.await(10,TimeUnit.SECONDS)
                        if(mode=="lost") MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                        else json(buildJsonObject {put("ok",true);put("pid",12345);put("name",if(mode=="bad-ack") "other-action" else HubInstallRequest.actionName(identifier))})
                    }
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }}
            try {
                val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val repo=HubInstallRepository(db.dao(),CompletableDeferred(Unit))
                if(mode=="cancel") {
                    val job=launch {runCatching {repo.install(target,request,api,tokens)}}
                    withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))}
                    assertTrue(runCatching {HubInstallRepository(db.dao(),CompletableDeferred(Unit)).install(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                    job.cancelAndJoin();release.countDown();assertEquals("unknown",db.dao().unresolvedManagementOperations(target.resourceScope).single().state);assertEquals(1,writes.get())
                } else {
                    val result=runCatching {repo.install(target,request,api,tokens)}
                    if(mode in setOf("changed","policy","duplicate")) {assertTrue(result.exceptionOrNull() is SkillHubChanged);assertEquals(0,writes.get());assertTrue(db.dao().unresolvedManagementOperations(target.resourceScope).isEmpty())}
                    else if(mode=="accepted") {val receipt=result.getOrThrow();assertEquals("acknowledged",receipt.state);assertFalse(receipt.resultSummary.contains("12345"));assertFalse(receipt.resultSummary.contains("skills-install-"));assertTrue(receipt.resultSummary.contains("accepted"));assertEquals(1,writes.get())}
                    else {
                        assertTrue(result.isFailure);val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state);assertFalse(receipt.toString().contains("install-private-fixture"));assertFalse(receipt.toString().contains("# Fixture"));assertFalse(receipt.toString().contains("private-tier1-message"))
                        db.dao().recoverManagementOperations();db.dao().clearChats()
                        assertTrue(runCatching {HubInstallRepository(db.dao(),CompletableDeferred(Unit)).install(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get())
                    }
                }
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun processStartupIsAcknowledgedWithoutClaimingInstallationCompletion()=exercise("accepted")
    @Test fun changedPreviewRequiresNewReviewBeforeClaimOrPost()=exercise("changed")
    @Test fun changedPolicyRequiresNewReviewBeforeClaimOrPost()=exercise("policy")
    @Test fun existingInstalledNameCannotBeOverwritten()=exercise("duplicate")
    @Test fun lostReplyRemainsRedactedAndCannotReplayAfterRecoveryOrChatClear()=exercise("lost")
    @Test fun wrongActionAckRemainsUnknownAndCannotReplay()=exercise("bad-ack")
    @Test fun cancellationAndASecondInstallerCannotRepeatTheClaimedPost()=exercise("cancel")
}
