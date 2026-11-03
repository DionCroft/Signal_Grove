package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginUpdateStorageTest {
    private val tokens=ManagementTokens("update-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun exercise(mode:String)=runBlocking {
        val catalog=mode in setOf("catalog","tag","rename","caps","caps-approve","catalog-scan","catalog-changed","approved-origin-changed","badproposal","movedpin")
        val retry=mode in setOf("caps-approve","scan-approve","scan-dangerous","approved-origin-changed")
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build();val sends=AtomicInteger();val published=AtomicInteger();val reads=AtomicInteger();val profiles=AtomicInteger();val tickets=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server ->
            lateinit var target:ManagementTarget
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when(q.path) {
                "/prefix/api/auth/me"->{assertEquals("Bearer update-management-fixture",q.getHeader("Authorization"));json(buildJsonObject {put("provider","password");put("user_id","fixture")})}
                "/prefix/api/profiles"->{val n=profiles.incrementAndGet();if(mode=="cancel-readback" && n==2) {entered.countDown();release.await(10,TimeUnit.SECONDS)};json(PluginUpdateFixture.profiles(mode=="home" || mode=="readback-home" && n==2))}
                "/prefix/api/auth/ws-ticket"->{assertEquals("Bearer update-management-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();json(buildJsonObject {put("ticket","u".repeat(43));put("ttl_seconds",30)})}
                "/prefix/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val p=j["params"]!!.jsonObject;assertEquals("worker",p.text("profile"))
                        val result=if(j.text("method")=="plugins.catalog.lookup") PluginUpdateFixture.catalog(mode=="catalog-changed",mode=="approved-origin-changed" && sends.get()==1) else {
                            assertEquals("plugins.manage",j.text("method"));when(p.text("action")) {
                                "list"->{reads.incrementAndGet();PluginUpdateFixture.inventory(catalog,published.get()>0,mode)}
                                "update"->{
                                    val n=sends.incrementAndGet();assertEquals(setOf("profile","action","name","accept_capabilities","force"),p.keys);assertEquals(PluginUpdateFixture.install,p.text("name"));assertEquals(JsonPrimitive(n==2 && mode=="caps-approve"),p["accept_capabilities"]);assertEquals(JsonPrimitive(n==2 && mode in setOf("scan-approve","scan-dangerous")),p["force"])
                                    assertEquals("sending",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().state})
                                    if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                                    if(mode=="lost") {s.cancel();return}
                                    when {
                                        mode in setOf("caps","caps-approve","approved-origin-changed","badproposal") && n==1->if(mode=="badproposal") JsonObject(PluginUpdateFixture.capability()+mapOf("delta" to JsonPrimitive("private-invalid-proposal"))) else PluginUpdateFixture.capability()
                                        mode in setOf("scan","scan-approve","scan-dangerous","dangerous","catalog-scan") && (n==1 || mode=="scan-dangerous")->PluginUpdateFixture.scan(if(mode=="dangerous" || n==2) "dangerous" else "caution")
                                        else->{published.incrementAndGet();PluginUpdateFixture.accepted(mode,catalog)}
                                    }
                                }
                                else->error("Unexpected action")
                            }
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->MockResponse().setResponseCode(404)
            }}
            try {
                val api=ManagementClient(server.url("prefix/").toString(),true,true);target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val request=PluginUpdateFixture.request(catalog);val repo=PluginUpdateRepository(db.dao(),CompletableDeferred(Unit))
                if(mode in setOf("cancel","cancel-readback")) {
                    val job=launch {runCatching {repo.execute(target,request,api,tokens)}};withContext(Dispatchers.IO) {assertTrue(entered.await(10,TimeUnit.SECONDS))};if(mode=="cancel") assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);job.cancelAndJoin();release.countDown();assertEquals(if(mode=="cancel") "unknown" else "acknowledged",db.dao().managementOperations(target.apiScope).first().single().state)
                } else {
                    val first=runCatching {repo.execute(target,request,api,tokens)}
                    when(mode) {
                        "changed","home","catalog-changed","collision"->{assertTrue(first.exceptionOrNull() is PluginUpdateChanged);assertEquals(0,sends.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty());if(mode=="home") assertEquals(0,tickets.get());return@use}
                        "lost","badack"->{assertTrue(first.exceptionOrNull() is PluginUpdateUncertain);assertEquals("unknown",db.dao().unresolvedManagementOperations(target.resourceScope).single().state);assertEquals(1,reads.get())}
                        "readback","readback-home"->{assertTrue(first.exceptionOrNull() is PluginUpdateReadbackPending);assertEquals("acknowledged",db.dao().managementOperations(target.apiScope).first().single().state);if(mode=="readback-home") assertEquals(1,reads.get())}
                        else->{
                            val outcome=first.getOrThrow()
                            if(outcome.kind!=PluginUpdateKind.Updated) {
                                assertEquals("acknowledged",outcome.receipt.state);assertEquals(0,published.get());assertEquals(JsonPrimitive(false),Json.parseToJsonElement(outcome.receipt.resultSummary).jsonObject["published"])
                                if(mode=="badproposal") assertNull(outcome.decision) else assertEquals(mode !in setOf("dangerous","catalog-scan"),outcome.decision!!.eligible())
                            } else {assertEquals(if(catalog) "confirmed" else "acknowledged",outcome.receipt.state);if(mode=="baddetails") assertNull(outcome.report);if(mode=="rename") assertEquals("renamed-key",outcome.inventory.plugins.single().key);if(mode=="movedpin") assertEquals(JsonPrimitive(true),Json.parseToJsonElement(outcome.receipt.resultSummary).jsonObject["pin_changed_during_update"])}
                            if(retry) {
                                val next=PluginUpdateRequest(outcome.inventory.plugins.single(),outcome.decision!!);val second=runCatching {repo.execute(target,next,api,tokens)}
                                if(mode=="approved-origin-changed") {assertTrue(second.exceptionOrNull() is PluginUpdateChanged);assertEquals(1,sends.get());assertEquals(1,db.dao().managementOperations(target.apiScope).first().size);return@use}
                                val result=second.getOrThrow();assertEquals(2,sends.get());assertEquals(2,tickets.get());assertEquals(2,db.dao().managementOperations(target.apiScope).first().size);assertEquals(outcome.receipt.key,Json.parseToJsonElement(result.receipt.requestSummary).jsonObject.text("decision_origin"))
                                if(mode=="scan-dangerous") {assertEquals(0,published.get());assertFalse(result.decision!!.eligible())} else {assertEquals(1,published.get());assertEquals(if(catalog) "confirmed" else "acknowledged",result.receipt.state)}
                            }
                        }
                    }
                }
                db.dao().recoverManagementOperations();db.dao().clearChats();val receipts=db.dao().managementOperations(target.apiScope).first();for(receipt in receipts) for(secret in listOf("private-update-profile","private-update-reference","private-update-error","private-update-output","private-update-warning","private-invalid-activation","private-invalid-proposal")) assertFalse(receipt.toString().contains(secret))
                assertEquals(if(retry) 2 else 1,sends.get());if(receipts.any {it.state=="unknown"}) {assertTrue(runCatching {PluginUpdateRepository(db.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,sends.get());assertEquals(1,tickets.get())}
            } finally {release.countDown();db.close()}
        }
    }
    @Test fun gitReplyHasNoRevisionAndStaysAcknowledgedAfterInventoryObservation()=exercise("git")
    @Test fun catalogCommitPinIsConfirmedBySeparateMetadataReadback()=exercise("catalog")
    @Test fun catalogTagObjectDoesNotHaveToEqualItsInstalledCommit()=exercise("tag")
    @Test fun catalogManifestRenameIsCorrelatedByDirectoryAndCatalog()=exercise("rename")
    @Test fun capabilityRefusalPublishesNothingAndDoesNotAutoApprove()=exercise("caps")
    @Test fun capabilityApprovalNeedsAnotherReceiptAndOnlyItsOwnFlag()=exercise("caps-approve")
    @Test fun cautionRefusalDoesNotAutoOverrideItsFindings()=exercise("scan")
    @Test fun cautionApprovalNeedsAnotherReceiptAndOnlyForce()=exercise("scan-approve")
    @Test fun dangerousScanCannotProduceAnOverrideRequest()=exercise("dangerous")
    @Test fun catalogCautionCannotExposeAnIneffectiveForceAction()=exercise("catalog-scan")
    @Test fun changedVersionRefusesBeforeClaimOrUpdate()=exercise("changed")
    @Test fun changedHomeRefusesBeforeTicketOrUpdate()=exercise("home")
    @Test fun changedCatalogPinRefusesBeforeClaimOrUpdate()=exercise("catalog-changed")
    @Test fun changedCatalogOriginRefusesTheReviewedApprovalBeforeAnotherSend()=exercise("approved-origin-changed")
    @Test fun lostReplySurvivesRecoveryAndChatClearWithoutRepetition()=exercise("lost")
    @Test fun wrongGitAckNameIsUncertainWithoutReadback()=exercise("badack")
    @Test fun malformedActivationCannotEraseMandatoryAcknowledgement()=exercise("baddetails")
    @Test fun malformedProposalCannotEraseKnownRefusalOrCreateAGrant()=exercise("badproposal")
    @Test fun missingReadbackCannotEraseAcknowledgement()=exercise("readback")
    @Test fun changedReadbackHomePreservesAckAndDoesNotQueryTheNewHome()=exercise("readback-home")
    @Test fun cancellationAndASecondAttemptSendOneUpdate()=exercise("cancel")
    @Test fun cancellationAfterAckKeepsTheCapturedReport()=exercise("cancel-readback")
    @Test fun aDangerousRescanStillBlocksAfterAnExplicitCautionOverride()=exercise("scan-dangerous")
    @Test fun changedExecutionPinIsReportedAsChangedWithoutAnAutomaticRetry()=exercise("movedpin")
    @Test fun knownDirectoryCollisionRefusesBeforeClaimOrUpdate()=exercise("collision")
}
