package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.CanonicalRuntimeBinding
import org.junit.Assert.*
import org.junit.Test

/** Real Room predicates and CAS, isolated in-memory database; no application credentials. */
class CanonicalViewerStorageTest {
    private val target=ManagementTarget("d".repeat(64),"https://hermes.test/dashboard/","default","fixture · owner")
    private val profile="/fixture/viewer-storage"
    private val home=checkNotNull(PluginHome.project(JsonPrimitive(profile))).identity()
    private val owner=CanonicalFeedOwner(target,home,profile,"runtime-storage","resolved-history",7)
    private val cursor=CanonicalReplayCursor("storage-ring",4)
    private fun db()=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
    private fun receipt(key:String="viewer-storage",created:Long=1):ManagementOperation {
        val binding=CanonicalRuntimeBinding(target,home,profile,owner.instanceId,owner.authorityEpoch)
        val inventory=CanonicalSessionInventory(binding,listOf(CanonicalStoredSession("history","history",owner.sessionId,"Fixture storage","cli",null,2,1.0,false,"fingerprint")),CanonicalSessionScope.Stored)
        val snapshot=CanonicalSessionSnapshot(owner,"history","reported-history","subscription",cursor,2,3,false,2,true)
        return ManagementOperation(key,target.resourceScope,target.apiScope,target.url,target.profile,target.actor,
            CanonicalResumeAction.Id,"history",inventory.review("history").summary(),"confirmed",snapshot.receiptSummary(),created,home)
    }
    @Test fun appliedCursorSurvivesANewRepositoryAndNeverStoresTranscriptText()=runBlocking {
        val database=db()
        try {
            val dao=database.dao();val row=receipt();dao.insertManagementOperation(row)
            CanonicalSessionsRepository(dao,CompletableDeferred(Unit)).recordApplied(row.key,owner,cursor)
            val restored=CanonicalSessionsRepository(dao,CompletableDeferred(Unit)).lastApplied(target,home,"history")
            assertEquals(CanonicalReplayCheckpoint(owner,cursor),restored)
            val saved=checkNotNull(dao.managementOperation(row.key));assertEquals("confirmed",saved.state)
            assertFalse(saved.resultSummary.contains("Fixture storage"));assertFalse(saved.resultSummary.contains("access_token"))
        } finally {database.close()}
    }
    @Test fun everyConnectionActorProfileHomeAndRequestedIdBoundaryIsEnforcedByTheQuery()=runBlocking {
        val database=db()
        try {
            val dao=database.dao();val row=receipt();dao.insertManagementOperation(row)
            val repo=CanonicalSessionsRepository(dao,CompletableDeferred(Unit));repo.recordApplied(row.key,owner,cursor)
            for(other in listOf(target.copy(apiScope="e".repeat(64)),target.copy(url="https://other.test/dashboard/"),
                target.copy(profile="sibling"),target.copy(actor="fixture · foreign"))) assertNull(repo.lastApplied(other,home,"history"))
            assertNull(repo.lastApplied(target,"f".repeat(64),"history"));assertNull(repo.lastApplied(target,home,"other-history"))
            val before=checkNotNull(dao.managementOperation(row.key)).resultSummary
            val error=runCatching {repo.recordApplied(row.key,owner.copy(target=target.copy(actor="fixture · foreign")),cursor)}.exceptionOrNull()
            assertTrue(error is CanonicalReviewChanged);assertEquals(before,dao.managementOperation(row.key)!!.resultSummary)
        } finally {database.close()}
    }
    @Test fun concurrentStaleCasUpdatesHaveOneWinnerAndCannotMoveTheCursorBackwards()=runBlocking {
        val database=db()
        try {
            val dao=database.dao();val row=receipt();dao.insertManagementOperation(row)
            val repo=CanonicalSessionsRepository(dao,CompletableDeferred(Unit));repo.recordApplied(row.key,owner,cursor)
            val before=checkNotNull(dao.managementOperation(row.key));val values=listOf(5L,6L).map {CanonicalViewerCheckpoint.updated(before,owner,cursor.copy(sequence=it))}
            val counts=coroutineScope {values.map {result->async(Dispatchers.IO) {dao.updateCanonicalViewerCheckpoint(row.key,before.resultSummary,result)}}.awaitAll()}
            assertEquals(1,counts.sum());assertEquals(listOf(0,1),counts.sorted())
            val winner=checkNotNull(repo.lastApplied(target,home,"history"));assertTrue(winner.cursor.sequence in setOf(5L,6L))
            val saved=dao.managementOperation(row.key)!!.resultSummary
            assertTrue(runCatching {repo.recordApplied(row.key,owner,cursor)}.exceptionOrNull() is CanonicalReviewChanged)
            assertEquals(saved,dao.managementOperation(row.key)!!.resultSummary)
            assertEquals(0,dao.updateCanonicalViewerCheckpoint(row.key,before.resultSummary,values.first()))
        } finally {database.close()}
    }
    @Test fun aNewerCorruptCheckpointCannotSilentlyFallBackToAnOlderCursor()=runBlocking {
        val database=db()
        try {
            val dao=database.dao();val old=receipt("old",1);dao.insertManagementOperation(old)
            val repo=CanonicalSessionsRepository(dao,CompletableDeferred(Unit));repo.recordApplied(old.key,owner,cursor)
            val recent=receipt("recent",2);dao.insertManagementOperation(recent.copy(resultSummary="{\"applied_view\":null}"))
            assertTrue(runCatching {repo.lastApplied(target,home,"history")}.exceptionOrNull() is CanonicalReviewChanged)
            assertEquals(CanonicalReplayCheckpoint(owner,cursor),CanonicalViewerCheckpoint.read(dao.managementOperation(old.key)!!,target,home,"history"))
        } finally {database.close()}
    }
    @Test fun fiftyNewerReceiptsWithoutCursorsHoldInsteadOfSearchingUnboundedHistory()=runBlocking {
        val database=db()
        try {
            val dao=database.dao();val old=receipt("old",0);dao.insertManagementOperation(old)
            val repo=CanonicalSessionsRepository(dao,CompletableDeferred(Unit));repo.recordApplied(old.key,owner,cursor)
            repeat(50) {index->dao.insertManagementOperation(receipt("new-"+index,index.toLong()+1))}
            val rows=dao.canonicalViewerReceipts(target.apiScope,target.url,target.profile,target.actor,home,"history")
            assertEquals(50,rows.size);assertTrue(rows.none {it.key==old.key})
            assertTrue(runCatching {repo.lastApplied(target,home,"history")}.exceptionOrNull() is CanonicalReviewChanged)
        } finally {database.close()}
    }
}
