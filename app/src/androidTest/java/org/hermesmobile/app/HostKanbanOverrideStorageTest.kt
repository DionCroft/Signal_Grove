package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HostKanbanOverrideStorageTest {
    private fun fields(j:JsonObject,vararg fields:Pair<String,JsonElement>)=JsonObject(j+mapOf(*fields))
    private fun card(model:String?="private-old-model",provider:String?="private-old-provider",effort:String?="high",status:String="running")=fields(HostKanbanTaskFixture.card(status=status),"model_override" to (model?.let(::JsonPrimitive) ?: JsonNull),"provider_override" to (provider?.let(::JsonPrimitive) ?: JsonNull),"reasoning_effort" to (effort?.let(::JsonPrimitive) ?: JsonNull))
    private fun request(f:HostKanbanTaskFixture,kind:String)=when(kind) {
        "model"->HostKanbanTaskChange.modelOverride(f.inventory(),f.snapshot(),f.detail(),"private-new-model","private-new-provider")
        "model-only"->HostKanbanTaskChange.modelOverride(f.inventory(),f.snapshot(),f.detail(),"private-new-model")
        "clear-model"->HostKanbanTaskChange.clearModelOverride(f.inventory(),f.snapshot(),f.detail())
        "reasoning"->HostKanbanTaskChange.reasoningOverride(f.inventory(),f.snapshot(),f.detail(),"none")
        else->HostKanbanTaskChange.clearReasoningOverride(f.inventory(),f.snapshot(),f.detail())
    }
    private suspend fun fixture(kind:String="model",mode:String="normal",configure:(HostKanbanTaskFixture)->Unit={},block:suspend (HermesDatabase,HostKanbanTaskFixture,HostKanbanTaskChange)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {MockWebServer().use {server->
            val f=HostKanbanTaskFixture(server,mode)
            f.beforeDetail.set(HostKanbanTaskFixture.detailJson(card()))
            val after=when(kind) {"model"->card("private-new-model","private-new-provider");"model-only"->card("private-new-model",null);"clear-model"->card(null,null);"reasoning"->card(effort="none");else->card(effort=null)}
            f.body=when(kind) {
                "model"->buildJsonObject {put("model_override","private-new-model");put("provider_override","private-new-provider")}
                "model-only"->buildJsonObject {put("model_override","private-new-model")}
                "clear-model"->buildJsonObject {put("clear_model_override",true)}
                "reasoning"->buildJsonObject {put("reasoning_effort","none")}
                else->buildJsonObject {put("clear_reasoning_effort",true)}
            }
            f.afterDetail.set(HostKanbanTaskFixture.detailJson(after));f.ack.set(buildJsonObject {put("task",after)})
            configure(f);val change=request(f,kind);f.start()
            try {withTimeout(20000) {block(db,f,change)}} finally {f.release.countDown();f.assertClean()}
        }} finally {db.close()}
    }
    private fun repo(db:HermesDatabase)=HostKanbanTaskRepository(db.dao(),CompletableDeferred(Unit))
    @Test fun allFiveOverrideShapesUseExactScopedSinglePatchAndConfigurationOnlyReceipts()=runBlocking {
        for(kind in listOf("model","model-only","clear-model","reasoning","clear-reasoning")) fixture(kind) {db,f,change->
            val result=repo(db).execute(f.owner,change,f.api(),f.tokens)
            assertEquals("confirmed",result.receipt.state);assertEquals(1,f.writes.get());assertEquals("running",result.detail!!.task.status)
            val stored=result.receipt.requestSummary+result.receipt.resultSummary
            for(v in listOf("private-new-model","private-new-provider","private-old-model","private-old-provider","t_child","garden")) assertFalse(stored.contains(v))
            val summary=Json.parseToJsonElement(result.receipt.resultSummary).jsonObject
            assertEquals(JsonPrimitive(false),summary["runtime_adoption_verified"]);assertEquals(JsonPrimitive(false),summary["worker_stop_verified"])
        }
    }
    @Test fun missingNullOverrideAckIsRetainedWithoutMatchingEffect()=runBlocking {
        fixture("clear-model",configure={f->val full=card(null,null);f.ack.set(buildJsonObject {put("task",JsonObject(full.filterKeys {it!="provider_override"}))})}) {db,f,change->
            val result=repo(db).execute(f.owner,change,f.api(),f.tokens);assertEquals("acknowledged",result.receipt.state);assertFalse(result.acknowledgement.effectReported);assertEquals(1,f.writes.get())
        }
    }
    @Test fun readbackOmissionCannotBecomeProofOfClearing()=runBlocking {
        fixture("clear-reasoning",configure={f->f.afterDetail.set(HostKanbanTaskFixture.detailJson(JsonObject(card(effort=null).filterKeys {it!="reasoning_effort"})))}) {db,f,change->
            val result=repo(db).execute(f.owner,change,f.api(),f.tokens);assertTrue(result.acknowledgement.effectReported);assertEquals("acknowledged",result.receipt.state)
        }
    }
    @Test fun independentOverrideChangedInAckDoesNotConfirmRequestedPhase()=runBlocking {
        fixture(configure={f->val changed=card("private-new-model","private-new-provider","none");f.ack.set(buildJsonObject {put("task",changed)});f.afterDetail.set(HostKanbanTaskFixture.detailJson(changed))}) {db,f,change->
            val result=repo(db).execute(f.owner,change,f.api(),f.tokens);assertEquals("acknowledged",result.receipt.state);assertFalse(result.acknowledgement.effectReported)
        }
    }
    @Test fun changedIndependentOverrideDuringPreflightNeverClaimsOrSends()=runBlocking {
        fixture {db,f,change->f.beforeDetail.set(HostKanbanTaskFixture.detailJson(card(effort="none")))
            assertTrue(runCatching {repo(db).execute(f.owner,change,f.api(),f.tokens)}.exceptionOrNull() is HostKanbanTaskChanged)
            assertEquals(0,f.writes.get());assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())
        }
    }
    @Test fun attemptedHttpErrorAndLostReplyStayUnknownAndCannotReplay()=runBlocking {
        for(kind in listOf("model","reasoning")) for(mode in listOf("http400","lost")) fixture(kind,mode) {db,f,change->
            assertTrue(runCatching {repo(db).execute(f.owner,change,f.api(),f.tokens)}.exceptionOrNull() is HostKanbanTaskUncertain)
            assertEquals("unknown",db.dao().managementOperations(f.owner.apiScope).first().single().state)
            assertTrue(runCatching {repo(db).execute(f.owner,change,f.api(),f.tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,f.writes.get())
        }
    }
    @Test fun failedReadbackPreservesAckForBothOverridePhases()=runBlocking {
        for(kind in listOf("clear-model","clear-reasoning")) fixture(kind,"readback-fail") {db,f,change->
            assertTrue(runCatching {repo(db).execute(f.owner,change,f.api(),f.tokens)}.exceptionOrNull() is HostKanbanTaskReadbackPending)
            assertEquals("acknowledged",db.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(1,f.writes.get())
        }
    }
    @Test fun cancelledAttemptPreservesUnknownBarrierForOverride()=runBlocking {
        fixture(mode="held") {db,f,change->
            val job=async {repo(db).execute(f.owner,change,f.api(),f.tokens)}
            assertTrue(withContext(Dispatchers.IO) {f.entered.await(5,TimeUnit.SECONDS)});job.cancelAndJoin();f.release.countDown()
            assertEquals("unknown",db.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(1,f.writes.get())
        }
    }
    @Test fun foreignProfilePendingOverrideBlocksBeforeAnyNetworkRead()=runBlocking {
        for(action in listOf(HostKanbanTaskAction.ModelOverride,HostKanbanTaskAction.ReasoningOverride)) fixture {db,f,change->
            val foreign=f.owner.copy(apiScope="c".repeat(64),profile="other")
            db.dao().insertManagementOperation(ManagementOperation("foreign",foreign.resourceScope,foreign.apiScope,foreign.url,foreign.profile,foreign.actor,action.receiptAction,"private-target",buildJsonObject {put("schema",1);put("access_home_sha256",f.home)}.toString(),"unknown","",1))
            assertTrue(runCatching {repo(db).execute(f.owner,change,f.api(),f.tokens)}.exceptionOrNull() is ManagementOutcomePending)
            assertEquals(0,f.writes.get());assertEquals(0,f.reads.get());assertEquals(0,f.delegate.catalogs.get())
        }
    }
    @Test fun foreignOverridePendingNoticeIsPrivateAndCanBeExplicitlyReviewed()=runBlocking {
        for(action in listOf(HostKanbanTaskAction.ModelOverride,HostKanbanTaskAction.ReasoningOverride)) fixture {db,f,_->
            val foreign=f.owner.copy(apiScope="c".repeat(64),profile="other",actor="password · private-actor")
            val row=ManagementOperation("foreign",foreign.resourceScope,foreign.apiScope,foreign.url,foreign.profile,foreign.actor,action.receiptAction,"private-target",buildJsonObject {put("schema",1);put("access_home_sha256",f.home)}.toString(),"unknown","private-result",1)
            db.dao().insertManagementOperation(row)
            val pending=db.dao().managementVisibleResourceOperations(f.owner.resourceScope,f.owner.apiScope,f.owner.url,f.home).first()
            assertEquals(1,pending.size)
            val visible=ManagementReceiptVisibility.project(pending,f.owner.apiScope,f.owner.resourceScope,f.owner.url,f.home).single()
            assertEquals(ManagementReceiptVisibility.SharedInstancePending,visible.action)
            for(value in listOf(visible.actor,visible.profile,visible.requestSummary,visible.resultSummary)) assertEquals("",value)
            assertEquals(row,db.dao().managementOperation(row.key))
            db.dao().reviewManagementOperation(row.key,f.owner.resourceScope,f.owner.url,f.home)
            assertEquals("reviewed",db.dao().managementOperation(row.key)!!.state)
            assertTrue(db.dao().managementVisibleResourceOperations(f.owner.resourceScope,f.owner.apiScope,f.owner.url,f.home).first().isEmpty())
            assertEquals(0,f.writes.get());assertEquals(0,f.reads.get())
        }
    }

}
