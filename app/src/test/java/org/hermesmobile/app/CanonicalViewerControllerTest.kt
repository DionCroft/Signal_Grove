package org.hermesmobile.app

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CanonicalViewerControllerTest {
    private class Link:CanonicalViewerLink {
        override val attachment=CanonicalGatewayChannel.Attachment(CanonicalViewerFixtures.snapshot(),CanonicalViewerFixtures.delivery())
        override val receipt=CanonicalViewerFixtures.receipt()
        val events=Channel<CanonicalFeedDelivery>(Channel.UNLIMITED)
        var beats=0;var closes=0;var beat:suspend ()->Unit={}
        override fun observations()=events.receiveAsFlow()
        override suspend fun heartbeat() {beats++;beat()}
        override fun close() {if(closes==0) {closes++;events.close()}}
    }
    private class Repo:CanonicalViewerRepository {
        val link=Link();var prepares=0;var adoptions=0;var cursorReads=0
        var requireHeartbeat=false;var previous:CanonicalReplayCheckpoint?=null
        var savedPrevious:CanonicalReplayCheckpoint?=null
        var prepareGate:CompletableDeferred<Unit>?=null;var adoptGate:CompletableDeferred<Unit>?=null
        var prepareError:Exception?=null;var adoptError:Exception?=null
        val saves=mutableListOf<CanonicalReplayCheckpoint>();val savedKeys=mutableListOf<String>()
        var save:suspend ()->Unit={}
        override suspend fun prepare(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens,scope:CanonicalSessionScope):CanonicalSessionInventory {
            prepares++;prepareGate?.let {withContext(NonCancellable) {it.await()}};prepareError?.let {throw it}
            return CanonicalViewerFixtures.inventory(scope)
        }
        override suspend fun adopt(review:CanonicalResumeReview,api:ManagementClient,tokens:ManagementTokens,previous:CanonicalReplayCheckpoint?,requireHeartbeat:Boolean):CanonicalViewerLink {
            adoptions++;this.previous=previous;this.requireHeartbeat=requireHeartbeat
            adoptGate?.let {withContext(NonCancellable) {it.await()}};adoptError?.let {throw it}
            review.consume(review.expected);return link
        }
        override suspend fun lastApplied(target:ManagementTarget,homeId:String,requested:String):CanonicalReplayCheckpoint? {cursorReads++;return savedPrevious}
        override suspend fun recordApplied(key:String,owner:CanonicalFeedOwner,cursor:CanonicalReplayCursor) {save();savedKeys+=key;saves+=CanonicalReplayCheckpoint(owner,cursor)}
    }
    private class Harness(val tests:TestScope) {
        val f=CanonicalViewerFixtures;val repo=Repo();val guards=mutableSetOf<Any>()
        val gates=mutableListOf<CompletableDeferred<Unit>>()
        var access:CanonicalViewerAccess?=CanonicalViewerAccess(f.target,f.home,1,ManagementClient(f.target.url,false),f.tokens)
        val viewer=CanonicalViewerController(tests,repo,{access},{owner,busy->if(busy) guards.add(owner) else guards.remove(owner)},
            now={tests.testScheduler.currentTime},heartbeatDelay=100,heartbeatDeadline=200)
        suspend fun loaded(scope:CanonicalSessionScope=CanonicalSessionScope.Live) {viewer.load(scope);tests.runCurrent();assertEquals(CanonicalViewerPhase.Inventory,viewer.state.value.phase)}
        suspend fun opened(fresh:Boolean=false) {loaded();viewer.review("history");viewer.approve(checkNotNull(viewer.state.value.review),fresh);tests.runCurrent()}
    }
    private suspend fun TestScope.exercise(block:suspend Harness.()->Unit) {
        val h=Harness(this)
        try {block(h)} finally {h.repo.prepareGate?.complete(Unit);h.repo.adoptGate?.complete(Unit);h.viewer.close();h.gates.forEach {it.complete(Unit)};runCurrent()}
        assertTrue(h.guards.isEmpty())
    }

    @Test fun missingAuthenticatedContextCannotReadOrOpenAnything()=runTest {exercise {
        access=null;viewer.load(CanonicalSessionScope.Live);runCurrent()
        assertEquals(Words.ManagementVerify,viewer.state.value.error);assertEquals(0,repo.prepares);assertEquals(0,repo.adoptions)
    }}
    @Test fun inventoryAndCancelledReviewNeverAdoptOrKeepTheUpdateGuard()=runTest {exercise {
        loaded(CanonicalSessionScope.Stored);assertTrue(guards.isEmpty());assertEquals(CanonicalSessionScope.Stored,viewer.state.value.scope)
        viewer.review("history");assertNotNull(viewer.state.value.review);viewer.cancelReview()
        assertNull(viewer.state.value.review);assertEquals(0,repo.adoptions);assertEquals(0,repo.cursorReads)
    }}
    @Test fun duplicateConfirmationMakesOneOpeningAndReleasesTheUpdateGuardOnClose()=runTest {exercise {
        loaded();viewer.review("history");val review=checkNotNull(viewer.state.value.review)
        viewer.approve(review,false);viewer.approve(review,false);runCurrent()
        assertEquals(1,repo.adoptions);assertTrue(repo.requireHeartbeat);assertEquals(CanonicalViewerPhase.Following,viewer.state.value.phase)
        assertEquals(1,guards.size);assertEquals(listOf(repo.link.receipt.key),repo.savedKeys)
        viewer.close();runCurrent();assertEquals(1,repo.link.closes);assertTrue(guards.isEmpty());assertEquals(CanonicalViewerPhase.Closed,viewer.state.value.phase)
    }}
    @Test fun normalOpeningResumesOnlyThePersistedAppliedCursor()=runTest {exercise {
        repo.savedPrevious=CanonicalReplayCheckpoint(f.owner,f.cursor);opened()
        assertEquals(1,repo.cursorReads);assertSame(repo.savedPrevious,repo.previous)
        assertEquals(CanonicalReplayCheckpoint(f.owner,f.cursor),repo.saves.single())
    }}
    @Test fun explicitlyFreshOpeningDoesNotReadOrReuseAnOlderCursor()=runTest {exercise {
        repo.savedPrevious=CanonicalReplayCheckpoint(f.owner,f.cursor);opened(true)
        assertEquals(0,repo.cursorReads);assertNull(repo.previous);assertEquals(CanonicalViewerPhase.Following,viewer.state.value.phase)
    }}
    @Test fun closedInventoryReadCannotPublishALateResult()=runTest {exercise {
        val gate=CompletableDeferred<Unit>();repo.prepareGate=gate;viewer.load(CanonicalSessionScope.Live);runCurrent()
        assertEquals(CanonicalViewerPhase.Loading,viewer.state.value.phase);viewer.close();gate.complete(Unit);runCurrent()
        assertEquals(CanonicalViewerPhase.Closed,viewer.state.value.phase);assertNull(viewer.state.value.inventory);assertEquals(0,repo.adoptions)
    }}
    @Test fun lateAcknowledgedLinkIsClosedAfterCancellationWithoutPublishingOrReopening()=runTest {exercise {
        loaded();repo.adoptGate=CompletableDeferred();viewer.review("history")
        viewer.approve(checkNotNull(viewer.state.value.review),false);runCurrent()
        assertEquals(CanonicalViewerPhase.Opening,viewer.state.value.phase);viewer.close();repo.adoptGate!!.complete(Unit);runCurrent()
        assertEquals(1,repo.adoptions);assertEquals(1,repo.link.closes);assertTrue(repo.saves.isEmpty());assertEquals(CanonicalViewerPhase.Closed,viewer.state.value.phase)
    }}
    @Test fun changedContextClosesBeforeTheNextHeartbeatOrAnotherRequest()=runTest {exercise {
        opened();access=CanonicalViewerAccess(f.target,f.home,2,checkNotNull(access).api,f.tokens)
        advanceTimeBy(100);runCurrent()
        assertEquals(CanonicalViewerPhase.Closed,viewer.state.value.phase);assertEquals(Words.ManagementReviewChanged,viewer.state.value.error)
        assertEquals(1,repo.link.beats);assertEquals(1,repo.link.closes);assertEquals(1,repo.adoptions)
    }}
    @Test fun heartbeatTimeoutHoldsAndClosesWithoutAnAutomaticReopen()=runTest {exercise {
        opened();repo.link.beat={awaitCancellation()};advanceTimeBy(301);runCurrent()
        assertEquals(CanonicalViewerPhase.Held,viewer.state.value.phase);assertEquals(Words.ViewerHeld,viewer.state.value.error)
        assertEquals(1,repo.link.closes);assertTrue(guards.isEmpty());advanceTimeBy(1000);runCurrent();assertEquals(1,repo.adoptions)
    }}
    @Test fun replayDiscontinuityCannotAdvanceThePersistedCursorOrClaimCompletion()=runTest {exercise {
        opened();repo.link.events.send(f.delivery(5,phase=CanonicalFeedPhase.SnapshotRequired));runCurrent()
        assertEquals(CanonicalViewerPhase.Held,viewer.state.value.phase);assertEquals(4L,repo.saves.single().cursor.sequence)
        assertEquals(4L,viewer.state.value.cursor!!.sequence);assertEquals(1,repo.link.closes)
    }}
    @Test fun activityProjectionKeepsFortyEntriesAndPersistsItsExactAppliedCursor()=runTest {exercise {
        opened();repo.link.events.send(f.delivery(50,(5L..50L).map {f.event(it)}));runCurrent()
        val state=viewer.state.value;assertEquals(46L,state.observed);assertEquals(40,state.activities.size)
        assertEquals((11L..50L).toList(),state.activities.map {it.sequence});assertEquals(50L,state.cursor!!.sequence)
        assertEquals(CanonicalReplayCheckpoint(f.owner,state.cursor),repo.saves.last());assertEquals(2,repo.saves.size)
        assertEquals(CanonicalViewerPhase.Following,state.phase);assertFalse(state.toString().contains("Private fixture reply"))
    }}
    @Test fun failedCheckpointWriteHoldsWithoutReplayingTheSessionOperation()=runTest {exercise {
        opened();repo.save={throw IOException("fixture failure")};repo.link.events.send(f.delivery(5,listOf(f.event(5))));runCurrent()
        assertEquals(CanonicalViewerPhase.Held,viewer.state.value.phase);assertEquals(4L,repo.saves.single().cursor.sequence)
        assertEquals(1,repo.adoptions);assertEquals(1,repo.link.closes);assertTrue(guards.isEmpty())
    }}
    @Test fun previewTruncationNeverSplitsAUnicodeScalar()=runTest {exercise {
        opened();val emoji=String(Character.toChars(0x1F331));val text="a".repeat(511)+emoji+"tail"
        repo.link.events.send(f.delivery(5,listOf(f.event(5,text=text))));runCurrent()
        assertEquals("a".repeat(511)+emoji,viewer.state.value.activities.single().text)
        assertTrue(viewer.state.value.activities.single().truncated)
    }}
    @Test fun lateCheckpointCompletionCannotReplaceANewerInventory()=runTest {exercise {
        opened();val gate=CompletableDeferred<Unit>();gates+=gate;repo.save={gate.await()}
        repo.link.events.send(f.delivery(5,listOf(f.event(5))));runCurrent();viewer.close()
        repo.save={};viewer.load(CanonicalSessionScope.Stored);runCurrent();assertEquals(CanonicalViewerPhase.Inventory,viewer.state.value.phase)
        gate.complete(Unit);runCurrent()
        assertEquals(CanonicalViewerPhase.Inventory,viewer.state.value.phase);assertEquals(CanonicalSessionScope.Stored,viewer.state.value.scope)
        assertTrue(viewer.state.value.activities.isEmpty());assertEquals(5L,repo.saves.last().cursor.sequence)
    }}
    @Test fun unsupportedHeartbeatIsAnExplainedHoldWithoutCursorPersistence()=runTest {exercise {
        repo.adoptError=CanonicalNotSupported();opened()
        assertEquals(CanonicalViewerPhase.Held,viewer.state.value.phase);assertEquals(Words.ViewerUnsupported,viewer.state.value.error)
        assertTrue(repo.saves.isEmpty());assertTrue(guards.isEmpty())
    }}
    @Test fun cancelledOrPreviousGenerationReviewCannotAuthorizeANewOpening()=runTest {exercise {
        loaded();viewer.review("history");val old=checkNotNull(viewer.state.value.review)
        viewer.cancelReview();viewer.approve(old,false);assertEquals(0,repo.adoptions)
        viewer.review("history");val previous=checkNotNull(viewer.state.value.review);viewer.load(CanonicalSessionScope.Stored);runCurrent()
        viewer.approve(previous,false);runCurrent();assertEquals(0,repo.adoptions);assertEquals(CanonicalViewerPhase.Inventory,viewer.state.value.phase)
    }}
    @Test fun aRemovedRowTapCannotCrashTheUiOrCreateAnApproval()=runTest {exercise {
        loaded();viewer.review("removed-history")
        assertNull(viewer.state.value.review);assertEquals(Words.ManagementReviewChanged,viewer.state.value.error);assertEquals(0,repo.adoptions)
    }}
    @Test fun shortPreviewKeepsNewlinesAndTabsWhileRemovingOtherControls()=runTest {exercise {
        opened();repo.link.events.send(f.delivery(5,listOf(f.event(5,text="line\n\tvalue"+27.toChar()))));runCurrent()
        val preview=viewer.state.value.activities.single();assertEquals("line\n\tvalue",preview.text);assertFalse(preview.truncated)
    }}
    @Test fun exactPreviewLimitIsNotLabelledTruncatedAndLongerPreviewIs()=runTest {exercise {
        opened();repo.link.events.send(f.delivery(5,listOf(f.event(5,text="a".repeat(512)))));runCurrent()
        assertFalse(viewer.state.value.activities.single().truncated)
        repo.link.events.send(f.delivery(6,listOf(f.event(6,text="b".repeat(513)))));runCurrent()
        assertEquals("b".repeat(512),viewer.state.value.activities.last().text);assertTrue(viewer.state.value.activities.last().truncated)
    }}
    @Test fun anExpiredIdentityReadCannotBeReportedAsAnExecutionOutcome()=runTest {exercise {
        repo.prepareError=HermesHttpException(401,"fixture");viewer.load(CanonicalSessionScope.Live);runCurrent()
        assertEquals(CanonicalViewerPhase.Held,viewer.state.value.phase);assertEquals(Words.ManagementExpired,viewer.state.value.error)
        assertNull(viewer.state.value.inventory);assertEquals(0,repo.adoptions);assertTrue(guards.isEmpty())
    }}
}
