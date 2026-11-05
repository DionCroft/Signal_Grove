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

class HostKanbanBoardStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun db()=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
    private fun meta(slug:String="garden",archived:Boolean=false)=HostKanbanBoardFixture.metadata(slug,archived)
    private fun catalog(rows:List<JsonObject> = listOf(meta("default"),meta(),meta("archive",true)),current:String="garden")=HostKanbanBoardFixture.catalog(rows,current)
    private fun fields(j:JsonObject,vararg pairs:Pair<String,JsonElement>)=JsonObject(j+mapOf(*pairs))
    private fun ack(row:JsonObject)=buildJsonObject {put("board",row);put("current","garden")}
    private fun repo(dao:HermesDao)=HostKanbanBoardRepository(dao,CompletableDeferred(Unit))
    private fun update(f:HostKanbanBoardFixture)=HostKanbanBoardChange.update(f.expected(),"garden",mapOf("description" to "Reviewed private note"))
    private suspend fun fixture(mode:String="normal",ack:JsonObject=ack(fields(meta(),"description" to JsonPrimitive("Reviewed private note"))),after:JsonObject=catalog(listOf(meta("default"),fields(meta(),"description" to JsonPrimitive("Reviewed private note")),meta("archive",true))),block:suspend (HermesDatabase,HostKanbanBoardFixture)->Unit) {
        val database=db()
        try {MockWebServer().use {server->val f=HostKanbanBoardFixture(server,mode,"PATCH","/garden",buildJsonObject {put("description","Reviewed private note")},catalog(),ack,after);f.start();try {withTimeout(15000) {block(database,f)}} finally {f.release.countDown();f.assertClean()}}} finally {database.close()}
    }
    private fun receipt(owner:ManagementTarget,key:String="prior",action:String="KanbanBoardUpdate",home:String="b".repeat(64),state:String="unknown")=ManagementOperation(key,owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,action,"private-target",if(HostKanbanBoardAction.receipt(action)) buildJsonObject {put("schema",1);put("access_home_sha256",home)}.toString() else "private-request",state,"private-result",123,if(HostProjectAction.receipt(action)) home else "")

    @Test fun fiveBoardActionsSendLiteralRoutesAndPersistOnlyStructuralReceipts()=runBlocking {
        val old=meta();val default=meta("default");val archive=meta("archive",true)
        val edited=fields(old,"description" to JsonPrimitive("Reviewed private note"))
        val created=fields(meta("new-board"),"name" to JsonPrimitive("New Board"),"description" to JsonPrimitive(""),"icon" to JsonPrimitive(""),"color" to JsonPrimitive(""),"default_workdir" to JsonNull)
        data class Case(val action:HostKanbanBoardAction,val tail:String,val after:JsonObject,val ack:JsonObject)
        for(c in listOf(
            Case(HostKanbanBoardAction.Create,"",catalog(listOf(default,old,archive,created)),ack(created)),
            Case(HostKanbanBoardAction.Update,"/garden",catalog(listOf(default,edited,archive)),ack(edited)),
            Case(HostKanbanBoardAction.Archive,"/garden",catalog(listOf(default,meta(archived=true),archive),"default"),HostKanbanBoardFixture.obj("""{"result":{"slug":"garden","action":"archived","new_path":"/private/archive/garden-123"},"current":"default"}""")),
            Case(HostKanbanBoardAction.Delete,"/garden",catalog(listOf(default,archive),"default"),HostKanbanBoardFixture.obj("""{"result":{"slug":"garden","action":"deleted","new_path":""},"current":"default"}""")),
            Case(HostKanbanBoardAction.Switch,"/garden/switch",catalog(),HostKanbanBoardFixture.obj("""{"current":"garden"}"""))
        )) {
            val database=db()
            try {MockWebServer().use {server->
                val seed=HostKanbanBoardFixture(server,"normal",c.action.method,c.tail,null,catalog(),c.ack,c.after)
                val request=when(c.action) {HostKanbanBoardAction.Create->HostKanbanBoardChange.create(seed.expected(),HostKanbanBoardDraft("new-board",emptyMap()));HostKanbanBoardAction.Update->update(seed);else->HostKanbanBoardChange.action(seed.expected(),"garden",c.action)}
                val f=HostKanbanBoardFixture(server,"normal",c.action.method,c.tail,if(c.action.method=="DELETE") null else request.body(),catalog(),c.ack,c.after);f.start()
                val result=withTimeout(12000) {repo(database.dao()).execute(f.owner,request,f.api(),f.tokens)}
                assertEquals(c.action.name,"confirmed",result.receipt.state);assertEquals(1,f.writes.get());assertEquals(2,f.catalogs.get());assertEquals(0,f.tickets.get())
                assertEquals("",result.receipt.projectHome);assertEquals(f.homeId,HostKanbanBoardChange.accessHome(result.receipt.requestSummary))
                val durable=result.receipt.target+result.receipt.requestSummary+result.receipt.resultSummary
                for(privateValue in listOf("garden","new-board","Private board","Reviewed private note","/shared/","/srv/","/private/")) assertFalse(c.action.name,durable.contains(privateValue))
                val summary=Json.parseToJsonElement(result.receipt.resultSummary).jsonObject
                assertEquals(JsonPrimitive(true),summary["configuration_observed"])
                for(key in listOf("new_board_verified","file_erasure_verified","runtime_stop_verified","path_mapping_verified")) assertEquals(JsonPrimitive(false),summary[key])
                f.assertClean()
            }} finally {database.close()}
        }
    }
    @Test fun archiveTombstoneDeletionHasItsOwnExactRequestAndDoesNotClaimArchiveErasure()=runBlocking {
        val database=db();val ack=HostKanbanBoardFixture.obj("""{"result":{"slug":"archive","action":"deleted","new_path":""},"current":"garden"}""")
        try {MockWebServer().use {server->val f=HostKanbanBoardFixture(server,"normal","DELETE","/archive",null,catalog(),ack,catalog(listOf(meta("default"),meta())));f.start()
            val result=repo(database.dao()).execute(f.owner,HostKanbanBoardChange.action(f.expected(),"archive",HostKanbanBoardAction.Delete),f.api(),f.tokens)
            assertEquals("confirmed",result.receipt.state);assertEquals(1,f.writes.get());assertEquals(JsonPrimitive(false),Json.parseToJsonElement(result.receipt.resultSummary).jsonObject["file_erasure_verified"]);f.assertClean()
        }} finally {database.close()}
    }
    @Test fun lostReplyAndPostWriteHttp400RemainUnknownAndNeverReplay()=runBlocking {
        for(mode in listOf("lost","http400")) fixture(mode) {database,f->
            val failure=runCatching {repo(database.dao()).execute(f.owner,update(f),f.api(),f.tokens)}.exceptionOrNull()
            assertTrue(mode,failure is HostKanbanBoardUncertain);val row=database.dao().managementOperations(f.owner.apiScope).first().single();assertEquals("unknown",row.state);assertEquals(row,(failure as HostKanbanBoardUncertain).receipt)
            assertTrue(runCatching {repo(database.dao()).execute(f.owner,update(f),f.api(),f.tokens)}.exceptionOrNull() is ManagementOutcomePending)
            HostKanbanRepository().inventory(f.owner,f.homeId,f.api(),f.tokens)
            assertEquals("unknown",database.dao().managementOperation(row.key)!!.state);assertEquals(1,f.writes.get())
        }
    }
    @Test fun wrongAcknowledgementIdentityStaysUnknownEvenWithSuccessfulHttp()=runBlocking {
        fixture(ack=ack(meta("foreign"))) {database,f->
            assertTrue(runCatching {repo(database.dao()).execute(f.owner,update(f),f.api(),f.tokens)}.exceptionOrNull() is HostKanbanBoardUncertain)
            assertEquals("unknown",database.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(1,f.writes.get())
        }
    }
    @Test fun validAcknowledgementWithDifferentRequestedEffectIsNotConfirmed()=runBlocking {
        fixture(ack=ack(meta()),after=catalog()) {database,f->val result=repo(database.dao()).execute(f.owner,update(f),f.api(),f.tokens)
            assertEquals("acknowledged",result.receipt.state);assertFalse(result.acknowledgement.effectReported);assertEquals(1,f.writes.get())
        }
    }
    @Test fun failedReadbackAndChangedHomeOrRootRetainTheExactAcknowledgedRow()=runBlocking {
        for(mode in listOf("readback-fail","home-after","root-after")) fixture(mode) {database,f->
            val failure=runCatching {repo(database.dao()).execute(f.owner,update(f),f.api(),f.tokens)}.exceptionOrNull();assertTrue(mode,failure is HostKanbanBoardReadbackPending)
            val row=database.dao().managementOperations(f.owner.apiScope).first().single();assertEquals("acknowledged",row.state);assertEquals(row,(failure as HostKanbanBoardReadbackPending).receipt);assertEquals(1,f.writes.get())
        }
    }
    @Test fun preflightMetadataHomeAndRootDriftCreateNeitherWriteNorReceipt()=runBlocking {
        for(mode in listOf("metadata","home","root")) fixture {database,f->
            if(mode=="home") f.home.set("/other/access") else f.beforeOverride.set(catalog(listOf(if(mode=="root") fields(meta("default"),"db_path" to JsonPrimitive("/other/root.db")) else meta("default"),if(mode=="metadata") fields(meta(),"name" to JsonPrimitive("Other")) else meta(),meta("archive",true))))
            assertNotNull(runCatching {repo(database.dao()).execute(f.owner,update(f),f.api(),f.tokens)}.exceptionOrNull());assertEquals(0,f.writes.get());assertTrue(database.dao().managementOperations(f.owner.apiScope).first().isEmpty())
        }
    }
    @Test fun cancellationAfterSendKeepsUnknownAndDiscardsLateSuccess()=runBlocking {
        fixture("held") {database,f->val job=async {repo(database.dao()).execute(f.owner,update(f),f.api(),f.tokens)}
            assertTrue(withContext(Dispatchers.IO) {f.entered.await(5,TimeUnit.SECONDS)});job.cancelAndJoin();f.release.countDown()
            assertEquals("unknown",database.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(1,f.writes.get())
        }
    }
    @Test fun cancellationAfterAtomicClaimBeforeAttemptIsLocallyRefused()=runBlocking {
        fixture {database,f->val dao=object:HermesDao by database.dao() {
            override suspend fun claimManagementOperation(receipt:ManagementOperation) {database.dao().claimManagementOperation(receipt);currentCoroutineContext().cancel()}
        }
            val job=launch {repo(dao).execute(f.owner,update(f),f.api(),f.tokens)};job.join();assertTrue(job.isCancelled)
            assertEquals("refused",database.dao().managementOperations(f.owner.apiScope).first().single().state);assertEquals(0,f.writes.get())
        }
    }
    @Test fun projectBindingRereadsCanonicalNamespaceBeforeSending()=runBlocking {
        fixture {database,f->
            val projects=HostProjectsRepository().read(f.owner,f.homeId,f.api(),f.tokens)
            val request=HostKanbanBoardChange.update(f.expected(),"garden",mapOf("project_id" to "p_bind"),projects)
            f.projects.set(HostKanbanBoardFixture.obj("""{"projects":[{"id":"p_bind","slug":"binding","name":"Private binding project","primary_path":"/srv/changed","created_at":1,"folders":[]}],"active_id":null}"""))
            assertTrue(runCatching {repo(database.dao()).execute(f.owner,request,f.api(),f.tokens)}.exceptionOrNull() is HostKanbanBoardChanged)
            assertEquals(2,f.projectReads.get());assertEquals(0,f.writes.get());assertTrue(database.dao().managementOperations(f.owner.apiScope).first().isEmpty())
        }
    }
    @Test fun crossProfileBoardBarrierCoversAllFiveActionsVisibilityAndExplicitLocalReview()=runBlocking {
        for(action in HostKanbanBoardAction.entries) fixture {database,f->
            val other=f.owner.copy(profile="other",apiScope="c".repeat(64),actor="password · private-other")
            database.dao().claimManagementOperation(receipt(f.owner,action=action.receiptAction,home=f.homeId,state="sending"))
            assertEquals(1,database.dao().unresolvedManagementOperations(other.resourceScope,other.url,"d".repeat(64)).size)
            assertTrue(runCatching {database.dao().claimManagementOperation(receipt(other,"second","Probe"))}.exceptionOrNull() is ManagementOutcomePending)
            val raw=database.dao().managementVisibleResourceOperations(other.resourceScope,other.apiScope,other.url,"d".repeat(64)).first();assertEquals(1,raw.size)
            val projected=ManagementReceiptVisibility.project(raw,other.apiScope,other.resourceScope,other.url,"d".repeat(64)).single();assertEquals(ManagementReceiptVisibility.SharedInstancePending,projected.action)
            for(value in listOf(f.owner.profile,f.owner.actor,f.owner.apiScope,"private-target","private-result",f.homeId)) assertFalse(projected.toString(),projected.toString().contains(value))
            assertTrue(ManagementReceiptVisibility.project(listOf(projected),other.apiScope,other.resourceScope,other.url+"other/").isEmpty())
            database.dao().reviewManagementOperation("prior",other.resourceScope,other.url);assertEquals("sending",database.dao().managementOperation("prior")!!.state)
            database.dao().recoverManagementOperations();database.dao().reviewManagementOperation("prior",other.resourceScope,other.url+"other/");assertEquals("unknown",database.dao().managementOperation("prior")!!.state)
            database.dao().reviewManagementOperation("prior",other.resourceScope,other.url);assertEquals("reviewed",database.dao().managementOperation("prior")!!.state);assertEquals(0,f.writes.get())
        }
    }
    @Test fun sameHomeProjectAliasBarrierAndBoardBarrierAreSymmetric()=runBlocking {
        for(boardFirst in listOf(false,true)) fixture {database,f->
            val alias=f.owner.copy(profile="alias",apiScope="c".repeat(64))
            val board=receipt(f.owner,"board",home=f.homeId);val project=receipt(alias,"project","ProjectUpdate",f.homeId)
            database.dao().claimManagementOperation(if(boardFirst) board else project)
            assertTrue(runCatching {database.dao().claimManagementOperation(if(boardFirst) project else board)}.exceptionOrNull() is ManagementOutcomePending)
            assertEquals(1,database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM management_operations").use {it.moveToFirst();it.getInt(0)})
        }
    }
    @Test fun concurrentBoardAndSameHomeProjectClaimsHaveExactlyOneWinner()=runBlocking {
        fixture {database,f->val start=CompletableDeferred<Unit>();val alias=f.owner.copy(profile="alias",apiScope="c".repeat(64))
            val jobs=listOf(receipt(f.owner,"board",home=f.homeId,state="sending"),receipt(alias,"project","ProjectUpdate",f.homeId,"sending")).map {row->async(Dispatchers.IO) {start.await();runCatching {database.dao().claimManagementOperation(row)}}}
            start.complete(Unit);val results=jobs.awaitAll();assertEquals(1,results.count {it.isSuccess});assertEquals(1,results.count {it.exceptionOrNull() is ManagementOutcomePending})
        }
    }
    @Test fun unrelatedOrdinaryProfileWorkAndExactOtherPrefixesRemainIndependent()=runBlocking {
        fixture {database,f->
            database.dao().claimManagementOperation(receipt(f.owner.copy(profile="other"),"ordinary","Probe"))
            database.dao().claimManagementOperation(receipt(f.owner,"board",home=f.homeId))
            database.dao().claimManagementOperation(receipt(f.owner.copy(url=f.owner.url+"other/"),"other-prefix",home=f.homeId))
            assertEquals(3,database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM management_operations").use {it.moveToFirst();it.getInt(0)})
        }
    }
    @Test fun malformedBoardHomeAndIncorrectProjectHomeStorageAreRejectedAtomically()=runBlocking {
        fixture {database,f->
            for(row in listOf(receipt(f.owner,home="short"),receipt(f.owner).copy(requestSummary="{}"),receipt(f.owner).copy(projectHome=f.homeId))) assertTrue(runCatching {database.dao().claimManagementOperation(row)}.exceptionOrNull() is IllegalArgumentException)
            assertTrue(database.dao().managementOperations(f.owner.apiScope).first().isEmpty());assertEquals(0,f.writes.get())
        }
    }
}
