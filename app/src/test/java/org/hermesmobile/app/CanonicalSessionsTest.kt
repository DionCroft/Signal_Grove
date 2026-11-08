package org.hermesmobile.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalSessionsTest {
    private val target = ManagementTarget("b".repeat(64),"https://hermes.test/dashboard/","default","fixture · owner")
    private val home = checkNotNull(PluginHome.project(JsonPrimitive("/fixture/home"))).identity()
    private fun binding(target: ManagementTarget = this.target, instance: String = "instance", epoch: Long = 7) =
        CanonicalRuntimeBinding(target,home,"/fixture/home",instance,epoch)
    private fun row(id: String = "history", count: Long = 2, running: Boolean = false) = buildJsonObject {
        put("id",id); put("session_id",id); put("resolved_id",id); put("title","Fixture history")
        put("source","cli"); put("model","fixture-model"); put("message_count",count); put("started_at",1.0); put("running",running)
    }
    private fun catalog(row: JsonObject = row(), owner: CanonicalRuntimeBinding = binding(), scope: CanonicalSessionScope = CanonicalSessionScope.Stored) =
        CanonicalSessionProtocol.inventory(buildJsonObject {put("scope",scope.wire);put("sessions",JsonArray(listOf(row)))},owner,scope)
    private fun snapshot() = buildJsonObject {
        put("session_id","resolved-history"); put("stored_session_id","reported-history"); put("messages",JsonArray(emptyList()))
        put("messages_omitted",true); put("message_count",2); put("running",false); put("authority_epoch",7)
        put("replay_epoch","ring"); put("last_sequence",4); put("subscription_id","subscription")
        put("revision",2); put("execution_generation",3); put("pending",JsonArray(emptyList())); put("prompts",JsonArray(emptyList()))
        put("info",buildJsonObject {put("desktop_protocol","hermes-gateway-v1");put("profile_id","/fixture/home")})
    }
    private fun change(j: JsonObject,key: String,value: JsonElement) = JsonObject(j+mapOf(key to value))
    private fun refuses(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun storedInventoryBindsExactIdentityAndPreservesObservedLabels() {
        val rows = catalog()
        assertEquals(target,rows.binding.target); assertEquals(home,rows.binding.homeId)
        assertEquals("history",rows.rows.single().sessionId); assertEquals("fixture-model",rows.rows.single().model)
        assertEquals(2L,rows.rows.single().messageCount); assertFalse(rows.rows.single().reportedRunning)
    }
    @Test fun liveInventoryRequiresItsExplicitReturnedScope() {
        val live = catalog(scope=CanonicalSessionScope.Live); assertEquals(CanonicalSessionScope.Live,live.scope)
        refuses {CanonicalSessionProtocol.inventory(buildJsonObject {put("scope","live");put("sessions",JsonArray(emptyList()))},binding())}
    }
    @Test fun duplicateSessionIdentifiersCannotProduceAnAmbiguousReview() {
        refuses {CanonicalSessionProtocol.inventory(buildJsonObject {put("scope","stored");put("sessions",JsonArray(listOf(row(),row())))},binding())}
    }
    @Test fun oversizedInventoryIsRefusedWithoutTruncatingEligibility() {
        refuses {CanonicalSessionProtocol.inventory(buildJsonObject {put("scope","stored");put("sessions",JsonArray((0..200).map {row("id-"+it)}))},binding())}
    }
    @Test fun invalidIdentityControlCharactersAreRefused() {
        refuses {catalog(change(row(),"session_id",JsonPrimitive("history\nforeign")))}
    }
    @Test fun nullableAndEmptyDisplayMetadataDoNotInventAProvider() {
        val value = catalog(JsonObject(row()+mapOf("source" to JsonNull,"model" to JsonPrimitive(""),"started_at" to JsonNull,"title" to JsonPrimitive("")))).rows.single()
        assertNull(value.source);assertEquals("",value.model);assertNull(value.startedAt);assertEquals("",value.title)
    }
    @Test fun booleanAndFractionalMessageCountsAreRefused() {
        refuses {catalog(change(row(),"message_count",JsonPrimitive(true)))}
        refuses {catalog(change(row(),"message_count",JsonPrimitive(2.5)))}
    }
    @Test fun reportedRunningRequiresAnActualBoolean() {
        refuses {catalog(change(row(),"running",JsonPrimitive("false")))}
        refuses {catalog(change(row(),"running",JsonPrimitive(0)))}
    }
    @Test fun aReviewCannotSelectAnUnlistedSession() {
        refuses {catalog().review("foreign")}
    }
    @Test fun changingActivityCountsDoesNotTurnAViewerReviewIntoAnExecutionGrant() {
        val expected = catalog(); val review=expected.review("history")
        review.consume(catalog(row(count=3,running=true)))
    }
    @Test fun changedSessionIdentitySourceTitleAndModelInvalidateTheReview() {
        for ((key,value) in listOf("id" to JsonPrimitive("replacement"),"resolved_id" to JsonPrimitive("replacement"),
            "source" to JsonPrimitive("discord"),"title" to JsonPrimitive("Changed title"),"model" to JsonPrimitive("changed-model"))) {
            val review=catalog().review("history")
            assertTrue(runCatching {review.consume(catalog(change(row(),key,value)))}.exceptionOrNull() is CanonicalReviewChanged)
        }
    }
    @Test fun changingActorProfileApiScopeAndUrlInvalidatesReview() {
        for (owner in listOf(target.copy(actor="foreign"),target.copy(profile="worker"),target.copy(apiScope="a".repeat(64)),target.copy(url="https://other.test/"))) {
            assertTrue(runCatching {catalog().review("history").consume(catalog(owner=binding(target=owner)))}.exceptionOrNull() is CanonicalReviewChanged)
        }
    }
    @Test fun changedRuntimeInstanceEpochAndInventoryScopeInvalidateReview() {
        assertTrue(runCatching {catalog().review("history").consume(catalog(owner=binding(instance="replacement")))}.exceptionOrNull() is CanonicalReviewChanged)
        assertTrue(runCatching {catalog().review("history").consume(catalog(owner=binding(epoch=8)))}.exceptionOrNull() is CanonicalReviewChanged)
        assertTrue(runCatching {catalog().review("history").consume(catalog(scope=CanonicalSessionScope.Live))}.exceptionOrNull() is CanonicalReviewChanged)
    }
    @Test fun successfulAndStaleApprovalsAreBothSingleUse() {
        val inventory=catalog();val accepted=inventory.review("history");accepted.consume(inventory)
        assertTrue(runCatching {accepted.consume(inventory)}.exceptionOrNull() is CanonicalApprovalUsed)
        val stale=inventory.review("history");runCatching {stale.consume(catalog(owner=binding(epoch=8)))}
        assertTrue(runCatching {stale.consume(inventory)}.exceptionOrNull() is CanonicalApprovalUsed)
    }
    @Test fun concurrentApprovalConsumptionHasExactlyOneWinner() {
        val inventory=catalog();val review=inventory.review("history");val gate=CountDownLatch(1);val pool=Executors.newFixedThreadPool(4)
        try {
            val results=(0 until 16).map {pool.submit<Boolean> {gate.await();runCatching {review.consume(inventory)}.isSuccess}}
            gate.countDown();assertEquals(1,results.count {it.get()})
        } finally {pool.shutdownNow()}
    }
    @Test fun snapshotUsesServerResolvedIdsWithoutRenamingTheRequestedHistory() {
        val value=CanonicalSessionProtocol.snapshot(snapshot(),binding(),"original-history")
        assertEquals("original-history",value.requestedSessionId);assertEquals("resolved-history",value.owner.sessionId)
        assertEquals("reported-history",value.reportedStoredId);assertEquals(7L,value.owner.authorityEpoch)
        assertEquals(2L,value.revision);assertEquals(3L,value.executionGeneration);assertTrue(value.messagesOmitted)
    }
    @Test fun falseRunningDoesNotProduceIdleTerminalOrAWriterGrant() {
        val value=CanonicalSessionProtocol.snapshot(snapshot(),binding(),"history")
        assertFalse(value.reportedRunning)
        val receipt=Json.parseToJsonElement(value.receiptSummary()).jsonObject
        assertEquals(JsonPrimitive("unproven"),receipt["execution_outcome"])
        assertFalse(receipt.containsKey("execution_state"));assertFalse(receipt.containsKey("grants"))
    }
    @Test fun changedAuthorityEpochAndProfileHomeRefuseSnapshotBinding() {
        assertTrue(runCatching {CanonicalSessionProtocol.snapshot(change(snapshot(),"authority_epoch",JsonPrimitive(8)),binding(),"history")}.exceptionOrNull() is CanonicalReviewChanged)
        val wrong=change(snapshot(),"info",buildJsonObject {put("desktop_protocol","hermes-gateway-v1");put("profile_id","/fixture/worker")})
        assertTrue(runCatching {CanonicalSessionProtocol.snapshot(wrong,binding(),"history")}.exceptionOrNull() is CanonicalReviewChanged)
    }
    @Test fun malformedCursorAndGenerationStampsAreRefused() {
        for (key in listOf("last_sequence","revision","execution_generation","authority_epoch")) {
            refuses {CanonicalSessionProtocol.snapshot(change(snapshot(),key,JsonPrimitive(true)),binding(),"history")}
            refuses {CanonicalSessionProtocol.snapshot(change(snapshot(),key,JsonPrimitive(-1)),binding(),"history")}
            refuses {CanonicalSessionProtocol.snapshot(change(snapshot(),key,JsonPrimitive("7")),binding(),"history")}
        }
    }
    @Test fun omittedHistoryCannotSmuggleMessagesIntoAMetadataSnapshot() {
        refuses {CanonicalSessionProtocol.snapshot(change(snapshot(),"messages",JsonArray(listOf(buildJsonObject {put("role","assistant");put("content","private")}))),binding(),"history")}
    }
    @Test fun unomittedHistoryRequiresCompleteCountAndObjectRows() {
        val value=JsonObject(snapshot()+mapOf("messages_omitted" to JsonPrimitive(false),"message_count" to JsonPrimitive(1)))
        refuses {CanonicalSessionProtocol.snapshot(value,binding(),"history")}
        refuses {CanonicalSessionProtocol.snapshot(change(value,"messages",JsonArray(listOf(JsonPrimitive("private")))),binding(),"history")}
    }
    @Test fun malformedPendingAndPromptCollectionsAreRefused() {
        for (key in listOf("pending","prompts")) refuses {CanonicalSessionProtocol.snapshot(change(snapshot(),key,JsonObject(emptyMap())),binding(),"history")}
    }
    @Test fun receiptsAndDebugStringsDoNotExposeTranscriptOrPromptContent() {
        val inventory=catalog();val review=inventory.review("history");val value=CanonicalSessionProtocol.snapshot(snapshot(),binding(),"history")
        assertEquals("CanonicalSessionInventory(redacted)",inventory.toString());assertEquals("CanonicalStoredSession(redacted)",inventory.rows.single().toString())
        assertEquals("CanonicalResumeReview(redacted)",review.toString());assertEquals("CanonicalSessionSnapshot(redacted)",value.toString())
        val summary=review.summary();assertFalse(summary.contains("Fixture history"));assertFalse(summary.contains("fixture-model"))
        assertEquals(JsonPrimitive(true),Json.parseToJsonElement(summary).jsonObject["viewer_lifecycle_reviewed"])
        assertFalse(value.receiptSummary().contains("messages"));assertFalse(value.receiptSummary().contains("prompts"))
    }
}
