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

class HostKanbanTaskStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun db()=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
    private fun repo(dao:HermesDao)=HostKanbanTaskRepository(dao,CompletableDeferred(Unit))
    private fun fields(j:JsonObject,vararg fields:Pair<String,JsonElement>)=JsonObject(j+mapOf(*fields))
    private fun row(owner:ManagementTarget,action:String="KanbanTaskMetadata",key:String="foreign",home:String="b".repeat(64))=ManagementOperation(key,owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,action,"private-target",buildJsonObject {put("schema",1);put("access_home_sha256",home)}.toString(),"unknown","private-result",1)
    private suspend fun fixture(mode:String="normal",configure:(HostKanbanTaskFixture)->Unit={},block:suspend (HermesDatabase,HostKanbanTaskFixture)->Unit) {
        val db=db();try {MockWebServer().use {server->val f=HostKanbanTaskFixture(server,mode);configure(f);f.start();try {withTimeout(20000) {block(db,f)}} finally {f.release.countDown();f.assertClean()}}} finally {db.close()}
    }
    private suspend fun failure(db:HermesDatabase,f:HostKanbanTaskFixture)=runCatching {repo(db.dao()).execute(f.owner,f.request(),f.api(),f.tokens)}.exceptionOrNull()

    @Test fun allTaskOperationsUseLiteralScopedRoutesAndStructuralReceipts()=runBlocking {
        for(action in HostKanbanTaskAction.entries) fixture(configure={f->
            val card=fields(HostKanbanTaskFixture.card(),"model_override" to JsonNull,"provider_override" to JsonNull,"reasoning_effort" to JsonNull);f.beforeDetail.set(HostKanbanTaskFixture.detailJson(card));val after=when(action) {
                HostKanbanTaskAction.Create->fields(card,"id" to JsonPrimitive("t_new"),"title" to JsonPrimitive("Reviewed title"),"body" to JsonNull)
                HostKanbanTaskAction.Metadata->fields(card,"title" to JsonPrimitive("Reviewed title"))
                HostKanbanTaskAction.ModelOverride->fields(card,"model_override" to JsonPrimitive("private-model"),"provider_override" to JsonPrimitive("private-provider"))
                HostKanbanTaskAction.ReasoningOverride->fields(card,"reasoning_effort" to JsonPrimitive("none"))
                HostKanbanTaskAction.Priority->fields(card,"priority" to JsonPrimitive(-9))
                HostKanbanTaskAction.Assignee->fields(card,"assignee" to JsonPrimitive("other"))
                else->card
            }
            f.method=action.method;f.link=action in setOf(HostKanbanTaskAction.Link,HostKanbanTaskAction.Unlink)
            f.tail=when(action) {HostKanbanTaskAction.Create,HostKanbanTaskAction.Link,HostKanbanTaskAction.Unlink->"";HostKanbanTaskAction.Comment->"/t_child/comments";else->"/t_child"}
            if(action==HostKanbanTaskAction.Unlink) {f.beforeDetail.set(HostKanbanTaskFixture.detailJson(card,listOf("t_parent")));f.query=mapOf("parent_id" to "t_parent","child_id" to "t_child")}
            f.body=when(action) {
                HostKanbanTaskAction.ModelOverride->buildJsonObject {put("model_override","private-model");put("provider_override","private-provider")}
                HostKanbanTaskAction.ReasoningOverride->buildJsonObject {put("reasoning_effort","none")}
                HostKanbanTaskAction.Create->HostKanbanBoardFixture.obj("""{"title":"Reviewed title","priority":0,"parents":[],"triage":false,"goal_mode":false}""")
                HostKanbanTaskAction.Metadata->HostKanbanBoardFixture.obj("""{"title":"Reviewed title"}""")
                HostKanbanTaskAction.Priority->HostKanbanBoardFixture.obj("""{"priority":-9}""")
                HostKanbanTaskAction.Assignee->HostKanbanBoardFixture.obj("""{"assignee":"other"}""")
                HostKanbanTaskAction.Comment->HostKanbanBoardFixture.obj("""{"body":"Private comment","author":"dashboard"}""")
                HostKanbanTaskAction.Link->HostKanbanBoardFixture.obj("""{"parent_id":"t_parent","child_id":"t_child"}""")
                HostKanbanTaskAction.Unlink,HostKanbanTaskAction.Delete->null
            }
            f.ack.set(when(action) {
                HostKanbanTaskAction.Delete->buildJsonObject {put("deleted",true);put("task_id","t_child")}
                HostKanbanTaskAction.Comment,HostKanbanTaskAction.Unlink->buildJsonObject {put("ok",true)}
                HostKanbanTaskAction.Link->buildJsonObject {put("ok",true);put("gated",false)}
                else->buildJsonObject {put("task",after)}
            })
            f.afterDetail.set(HostKanbanTaskFixture.detailJson(after,if(action==HostKanbanTaskAction.Link) listOf("t_parent") else emptyList()))
        }) {db,f->
            val actionRequest=when(action) {
                HostKanbanTaskAction.ModelOverride->HostKanbanTaskChange.modelOverride(f.inventory(),f.snapshot(),f.detail(),"private-model","private-provider")
                HostKanbanTaskAction.ReasoningOverride->HostKanbanTaskChange.reasoningOverride(f.inventory(),f.snapshot(),f.detail(),"none")
                HostKanbanTaskAction.Create->HostKanbanTaskChange.create(f.inventory(),f.snapshot(),HostKanbanTaskDraft("Reviewed title"))
                HostKanbanTaskAction.Metadata->f.request()
                HostKanbanTaskAction.Priority->HostKanbanTaskChange.priority(f.inventory(),f.snapshot(),f.detail(),-9)
                HostKanbanTaskAction.Assignee->HostKanbanTaskChange.assignee(f.inventory(),f.snapshot(),f.detail(),"other")
                HostKanbanTaskAction.Comment->HostKanbanTaskChange.comment(f.inventory(),f.snapshot(),f.detail(),"Private comment")
                HostKanbanTaskAction.Link,HostKanbanTaskAction.Unlink->HostKanbanTaskChange.dependency(f.inventory(),f.snapshot(),f.detail(),f.detail("t_parent"),action==HostKanbanTaskAction.Unlink)
                HostKanbanTaskAction.Delete->HostKanbanTaskChange.delete(f.inventory(),f.snapshot(),f.detail())
            }
            val result=repo(db.dao()).execute(f.owner,actionRequest,f.api(),f.tokens)
            assertEquals(action.name,if(action==HostKanbanTaskAction.Comment) "acknowledged" else "confirmed",result.receipt.state);assertEquals(1,f.writes.get());assertEquals(0,f.delegate.tickets.get());assertEquals("",result.receipt.projectHome)
            assertEquals(f.home,HostKanbanTaskChange.accessHome(result.receipt.requestSummary))
            val durable=result.receipt.target+result.receipt.requestSummary+result.receipt.resultSummary
            for(value in listOf("garden","t_child","t_parent","t_new","Reviewed title","Private body","Private comment","/shared/","/srv/","private-model","private-provider",actionRequest.values["idempotency_key"]?.jsonPrimitive?.content ?: "never-value")) assertFalse(action.name+":"+value,durable.contains(value))
            val summary=Json.parseToJsonElement(result.receipt.resultSummary).jsonObject
            for(key in listOf("created_identity_verified","worker_stop_verified","file_erasure_verified","runtime_adoption_verified","path_mapping_verified","comment_identity_verified")) assertEquals(JsonPrimitive(false),summary[key])
        }
    }
    @Test fun postWriteHttpFailureAndLostReplyStayUnknownAndNeverReplay()=runBlocking {
        for(mode in listOf("http400","lost")) fixture(mode) {db,f->assertTrue(failure(db,f) is HostKanbanTaskUncertain);val row=db.dao().managementOperations(f.owner.apiScope).first().single();assertEquals("unknown",row.state);assertTrue(failure(db,f) is ManagementOutcomePending);HostKanbanRepository().inventory(f.owner,f.home,f.api(),f.tokens);assertEquals("unknown",db.dao().managementOperation(row.key)!!.state);assertEquals(1,f.writes.get())}
    }
    @Test fun foreignOrMalformedAcknowledgementDoesNotBecomeSuccess()=runBlocking {
        for(j in listOf(buildJsonObject {put("task",HostKanbanTaskFixture.card("foreign"))},buildJsonObject {put("ok",true)})) fixture(configure={it.ack.set(j)}) {db,f->assertTrue(failure(db,f) is HostKanbanTaskUncertain);assertEquals("unknown",db.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(1,f.writes.get())}
    }
    @Test fun validAckWithDifferentEffectRetainsAckWithoutConfirmation()=runBlocking {
        fixture(configure={it.ack.set(buildJsonObject {put("task",HostKanbanTaskFixture.card())});it.afterDetail.set(it.beforeDetail.get())}) {db,f->val result=repo(db.dao()).execute(f.owner,f.request(),f.api(),f.tokens);assertEquals("acknowledged",result.receipt.state);assertFalse(result.acknowledgement.effectReported)}
    }
    @Test fun failedObservationOrChangedHomeKeepsAcknowledgement()=runBlocking {
        for(mode in listOf("readback-fail","home-after")) fixture(mode) {db,f->assertTrue(mode,failure(db,f) is HostKanbanTaskReadbackPending);assertEquals("acknowledged",db.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(1,f.writes.get())}
    }
    @Test fun changedRootOrBoardAfterAckKeepsAcknowledgement()=runBlocking {
        for(slug in listOf("default","garden")) fixture(configure={f->f.catalogAfter.set(HostKanbanBoardFixture.catalog(listOf("default","garden","archive").map {s->val m=HostKanbanBoardFixture.metadata(s,s=="archive");if(s==slug) fields(m,"db_path" to JsonPrimitive("/changed/root.db")) else m}))}) {db,f->assertTrue(failure(db,f) is HostKanbanTaskReadbackPending);assertEquals("acknowledged",db.dao().managementOperations(f.owner.apiScope).first().single().state)}
    }
    @Test fun staleTaskOrHistoryRefusesBeforeClaimOrWrite()=runBlocking {
        for(history in listOf(false,true)) fixture {db,f->val request=f.request();f.beforeDetail.set(if(history) HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card(),comments=buildJsonArray {add(buildJsonObject {put("id",1);put("task_id","t_child");put("author","dashboard");put("body","New private comment");put("created_at",1)})}) else HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card(title="Changed")))
            assertTrue(runCatching {repo(db.dao()).execute(f.owner,request,f.api(),f.tokens)}.exceptionOrNull() is HostKanbanTaskChanged);assertEquals(0,f.writes.get());assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())}
    }
    @Test fun cancellationAfterSendStaysUnknownWithoutLateSuccess()=runBlocking {
        fixture("held") {db,f->val job=async {repo(db.dao()).execute(f.owner,f.request(),f.api(),f.tokens)};assertTrue(withContext(Dispatchers.IO) {f.entered.await(5,TimeUnit.SECONDS)});job.cancelAndJoin();f.release.countDown();assertEquals("unknown",db.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(1,f.writes.get())}
    }
    @Test fun cancellationDuringReadbackPreservesAlreadyDurableAck()=runBlocking {
        fixture("held-readback") {db,f->val job=async {repo(db.dao()).execute(f.owner,f.request(),f.api(),f.tokens)};assertTrue(withContext(Dispatchers.IO) {f.entered.await(5,TimeUnit.SECONDS)});assertEquals("acknowledged",db.dao().managementOperations(f.owner.apiScope).first().single().state);job.cancelAndJoin();f.release.countDown();assertEquals("acknowledged",db.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(1,f.writes.get())}
    }
    @Test fun allTaskActionsBlockOtherProfilesAtSameUrlBeforeNetworking()=runBlocking {
        for(action in HostKanbanTaskAction.entries) fixture {db,f->val foreign=f.owner.copy(profile="other",apiScope="c".repeat(64),actor="password · other");db.dao().insertManagementOperation(row(foreign,action.receiptAction,home=f.home));assertTrue(failure(db,f) is ManagementOutcomePending);assertEquals(0,f.writes.get());assertEquals(0,f.delegate.catalogs.get());assertEquals(0,f.reads.get())}
    }
    @Test fun sameHomeProjectWritesAndBoardWritesBlockTaskWrites()=runBlocking {
        for(action in listOf("ProjectUpdate","KanbanBoardUpdate")) fixture {db,f->val foreign=f.owner.copy(profile="alias",apiScope="c".repeat(64));val row=row(foreign,action,home=f.home).copy(projectHome=if(action=="ProjectUpdate") f.home else "");db.dao().insertManagementOperation(row);assertTrue(failure(db,f) is ManagementOutcomePending);assertEquals(0,f.delegate.catalogs.get())}
    }
    @Test fun finalAtomicClaimSeesForeignTaskInsertedDuringPreflight()=runBlocking {
        fixture {db,f->var inserted=false;f.delegate.catalogHook={if(!inserted && f.delegate.catalogs.get()>=2) {inserted=true;runBlocking {db.dao().insertManagementOperation(row(f.owner.copy(profile="other",apiScope="c".repeat(64)),home=f.home))}}}
            assertTrue(failure(db,f) is ManagementOutcomePending);assertEquals(0,f.writes.get());assertEquals(1,db.dao().managementOperations("c".repeat(64)).first().size);assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())}
    }
    @Test fun taskClaimsAndBoardClaimsRaceForOneSharedSlot()=runBlocking {
        fixture {db,f->for(action in HostKanbanTaskAction.entries) {val foreign=f.owner.copy(profile="other",apiScope="c".repeat(64));val one=row(f.owner,action.receiptAction,"task",f.home).copy(state="sending");val two=row(foreign,"KanbanBoardUpdate","board",f.home).copy(state="sending")
            val go=CompletableDeferred<Unit>();val attempts=listOf(one,two).map {record->async(Dispatchers.IO) {go.await();runCatching {db.dao().claimManagementOperation(record)}.isSuccess}};go.complete(Unit);assertEquals(1,attempts.awaitAll().count {it});db.openHelper.writableDatabase.execSQL("DELETE FROM management_operations WHERE [key] IN ('task','board')")}}
    }
    @Test fun foreignTaskReceiptIsRedactedAndNeedsExplicitLocalReview()=runBlocking {
        fixture {db,f->val foreign=f.owner.copy(profile="other",apiScope="c".repeat(64),actor="password · private-user");val original=row(foreign,home=f.home);db.dao().insertManagementOperation(original)
            val found=db.dao().managementVisibleResourceOperations(f.owner.resourceScope,f.owner.apiScope,f.owner.url,f.home).first().single();val visible=ManagementReceiptVisibility.project(listOf(found),f.owner.apiScope,f.owner.resourceScope,f.owner.url,f.home).single()
            assertEquals(ManagementReceiptVisibility.SharedInstancePending,visible.action);assertEquals("",visible.actor);assertEquals("",visible.profile);assertEquals("",visible.requestSummary);assertEquals("",visible.resultSummary);assertEquals(0L,visible.createdAt)
            assertEquals(original,db.dao().managementOperation(original.key));db.dao().reviewManagementOperation(original.key,f.owner.resourceScope,f.owner.url,f.home);assertEquals("reviewed",db.dao().managementOperation(original.key)!!.state);assertEquals(0,f.writes.get())}
    }
    @Test fun prefixesOtherListenersAndSettledForeignRowsDoNotShareUncertainty()=runBlocking {
        fixture {db,f->val foreign=f.owner.copy(url=f.owner.url+"other/",profile="other",apiScope="c".repeat(64));db.dao().insertManagementOperation(row(foreign,home=f.home));db.dao().insertManagementOperation(row(f.owner.copy(profile="other",apiScope="d".repeat(64)),key="settled",home=f.home).copy(state="acknowledged"))
            assertEquals("confirmed",repo(db.dao()).execute(f.owner,f.request(),f.api(),f.tokens).receipt.state);assertEquals(1,f.writes.get())}
    }
    @Test fun restartRecoveryMarksTaskSendingUnknownWithoutSending()=runBlocking {
        fixture {db,f->for(action in HostKanbanTaskAction.entries) db.dao().insertManagementOperation(row(f.owner,action.receiptAction,action.name,f.home).copy(state="sending"));db.dao().recoverManagementOperations();val rows=db.dao().managementOperations(f.owner.apiScope).first();assertEquals(10,rows.size);assertEquals(HostKanbanTaskAction.entries.map {it.receiptAction}.toSet(),rows.map {it.action}.toSet());assertTrue(rows.all {it.state=="unknown"});assertEquals(0,f.writes.get());assertEquals(0,f.reads.get());assertEquals(0,f.delegate.catalogs.get())}
    }
    @Test fun commentContentReadbackStillCannotIdentifyTheCreatedComment()=runBlocking {
        fixture(configure={f->f.method="POST";f.tail="/t_child/comments";f.body=buildJsonObject {put("body","Private comment");put("author","dashboard")};f.ack.set(buildJsonObject {put("ok",true)});f.afterDetail.set(HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card(),comments=buildJsonArray {add(buildJsonObject {put("id",123);put("task_id","t_child");put("author","dashboard");put("body","Private comment");put("created_at",1)})}))}) {db,f->val request=HostKanbanTaskChange.comment(f.inventory(),f.snapshot(),f.detail(),"Private comment");val result=repo(db.dao()).execute(f.owner,request,f.api(),f.tokens);assertEquals("acknowledged",result.receipt.state);assertEquals(123L,result.detail!!.comments.single().id);assertEquals(JsonPrimitive(false),Json.parseToJsonElement(result.receipt.resultSummary).jsonObject["comment_identity_verified"])}
    }
    private fun richFixture(f:HostKanbanTaskFixture) {
        f.method="POST";f.tail="";f.body=HostKanbanBoardFixture.obj("""{"title":"Rich task","body":"Rich body","assignee":"other","tenant":"rich-tenant","priority":5,"workspace_kind":"worktree","workspace_path":"/srv/rich-worktree","parents":["t_parent"],"triage":false,"max_runtime_seconds":60,"skills":["review"],"goal_mode":true,"goal_max_turns":20,"model_override":"rich-model","provider_override":"rich-provider","reasoning_effort":"high","project_id":"p_bind"}""")
        val card=fields(HostKanbanTaskFixture.card("t_new","Rich task"),"body" to JsonPrimitive("Rich body"),"assignee" to JsonPrimitive("other"),"tenant" to JsonPrimitive("rich-tenant"),"priority" to JsonPrimitive(5),"workspace_kind" to JsonPrimitive("worktree"),"workspace_path" to JsonPrimitive("/srv/rich-worktree"),"max_runtime_seconds" to JsonPrimitive(60),"skills" to JsonArray(listOf(JsonPrimitive("review"))),"goal_mode" to JsonPrimitive(true),"goal_max_turns" to JsonPrimitive(20),"model_override" to JsonPrimitive("rich-model"),"provider_override" to JsonPrimitive("rich-provider"),"reasoning_effort" to JsonPrimitive("high"),"project_id" to JsonPrimitive("p_bind"))
        f.ack.set(buildJsonObject {put("task",card)});f.afterDetail.set(HostKanbanTaskFixture.detailJson(card,listOf("t_parent")))
    }
    private fun richRequest(f:HostKanbanTaskFixture)=HostKanbanTaskChange.create(f.inventory(),f.snapshot(),HostKanbanTaskDraft("Rich task",body="Rich body",assignee="other",tenant="rich-tenant",priority=5,workspaceKind="worktree",workspacePath="/srv/rich-worktree",parents=listOf("t_parent"),maxRuntimeSeconds=60,skills=listOf("review"),goalMode=true,goalMaxTurns=20,model="rich-model",provider="rich-provider",reasoning="high",projectId="p_bind"),HostProjectProtocol.inventory(HostKanbanBoardFixture.projectList(),f.owner,f.home))
    @Test fun richCreateSendsEveryLiteralFieldAfterCanonicalProjectAndParentReads()=runBlocking {
        fixture(configure=::richFixture) {db,f->val request=richRequest(f);val result=repo(db.dao()).execute(f.owner,request,f.api(),f.tokens)
            assertEquals("confirmed",result.receipt.state);assertEquals(1,f.writes.get());assertEquals(1,f.delegate.projectReads.get());assertEquals(1,f.delegate.tickets.get());assertEquals(listOf("t_parent"),result.detail!!.parents)
            val durable=result.receipt.requestSummary+result.receipt.resultSummary
            for(value in listOf("Rich task","Rich body","rich-tenant","/srv/rich-worktree","rich-model","rich-provider","p_bind",request.values.getValue("idempotency_key").jsonPrimitive.content)) assertFalse(value,durable.contains(value))}
    }
    @Test fun staleParentAndCanonicalProjectRefuseCreationBeforeClaim()=runBlocking {
        for(mode in listOf("parent","project")) fixture(configure=::richFixture) {db,f->val request=richRequest(f)
            if(mode=="parent") f.parentDetail.set(HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card("t_parent","Changed parent",status="done")))
            else {val original=f.delegate.projects.get();val rows=original.getValue("projects").jsonArray.map {fields(it.jsonObject,"primary_path" to JsonPrimitive("/changed/repository"))};f.delegate.projects.set(JsonObject(original+mapOf("projects" to JsonArray(rows))))}
            assertTrue(mode,runCatching {repo(db.dao()).execute(f.owner,request,f.api(),f.tokens)}.exceptionOrNull() is HostKanbanTaskChanged)
            assertEquals(0,f.writes.get());assertTrue(db.dao().managementOperations(f.owner.apiScope).first().isEmpty())}
    }

}
