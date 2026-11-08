package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostedRoomBrowserControllerTest {
    private val binding=HostedRoomScope(ManagementTarget("a".repeat(64),"https://hermes.test/dashboard/","worker","fixture · owner"),"b".repeat(64))
    private fun fixtureTokens()=ManagementTokens("room-fixture","refresh-fixture","fixture","owner",4102444800)
    private fun access(binding:HostedRoomScope=this.binding,generation:Long=1,tokens:ManagementTokens=fixtureTokens())=HostedRoomAccess(binding,generation,ManagementClient(binding.owner.url,false),tokens)
    private class Reader:HostedRoomReader {
        val pages=mutableListOf<Pair<Int,Boolean>>();var details=0;var pageGate:CompletableDeferred<Unit>?=null;var detailGate:CompletableDeferred<Unit>?=null
        var ignoreCancellation=false;var pageError:Exception?=null;var stateAdvertised=true;var replyScope:HostedRoomScope?=null;var replyOffset:Int?=null
        override suspend fun page(scope:HostedRoomScope,api:ManagementClient,tokens:ManagementTokens,offset:Int,includeDisbanded:Boolean):HostedRoomListing {
            pages+=offset to includeDisbanded;val gate=pageGate
            if(ignoreCancellation) withContext(NonCancellable) {gate?.await()} else gate?.await()
            pageError?.let {throw it};val owner=replyScope ?: scope
            val methods=if(stateAdvertised) setOf("groups.list","groups.state") else setOf("groups.list")
            val caps=HostedRoomCapabilities(owner,2,false,true,"gateway-1",methods,emptySet(),null)
            val room=HostedRoom("r-$offset","Fixture room",emptyList(),"gateway-1",1,1,1.25,2.75,null,null)
            return HostedRoomListing(caps,HostedRoomPage(owner,listOf(room),replyOffset ?: offset,if(offset==0) 731 else null,42),includeDisbanded)
        }
        override suspend fun detail(listing:HostedRoomListing,selected:HostedRoom,api:ManagementClient,tokens:ManagementTokens):HostedRoomDetail {
            details++;detailGate?.await();return HostedRoomDetail(replyScope ?: listing.scope,selected,HostedRoomContext("Fixture purpose","Fixture notes","everyone",""),null,43)
        }
    }
    private fun browser(scope:CoroutineScope,reader:Reader,guard:UpdateWorkGuard,context:()->HostedRoomAccess?)=HostedRoomBrowserController(scope,reader,context,guard::set)

    @Test fun missingVerifiedContextDoesNotReadAndExplainsWhy()=runTest {
        val r=Reader();val g=UpdateWorkGuard();val c=browser(backgroundScope,r,g) {null};c.reload();runCurrent()
        assertTrue(r.pages.isEmpty());assertEquals(Words.ManagementVerify,c.state.value.error);assertFalse(g.active.value)
    }
    @Test fun openingReadsExactlyOnePageAndReleasesItsInstallationLease()=runTest {
        val r=Reader();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();assertTrue(g.active.value);runCurrent()
        assertEquals(listOf(0 to false),r.pages);assertEquals(HostedRoomBrowserPhase.Page,c.state.value.phase);assertEquals(42L,c.state.value.listing!!.page.observedAt);assertFalse(g.active.value)
    }
    @Test fun duplicateRefreshWhileBusyDoesNotStartAnotherRead()=runTest {
        val r=Reader();r.pageGate=CompletableDeferred();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();c.reload();runCurrent()
        assertEquals(1,r.pages.size);r.pageGate!!.complete(Unit);runCurrent();assertFalse(g.active.value)
    }
    @Test fun moreUsesTheOpaqueServerCursorWithoutAccumulatingOtherPages()=runTest {
        val r=Reader();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();c.more();runCurrent()
        assertEquals(listOf(0 to false,731 to false),r.pages);assertEquals(listOf("r-731"),c.state.value.listing!!.page.rooms.map {it.id});assertNull(c.state.value.listing!!.page.nextOffset)
    }
    @Test fun endOfListCannotAutomaticallyRequestAnotherPage()=runTest {
        val r=Reader();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();c.more();runCurrent();c.more();advanceTimeBy(60_000);runCurrent();assertEquals(2,r.pages.size)
    }
    @Test fun changingTheDisbandedFilterRestartsAndDropsTheOtherFilterSnapshot()=runTest {
        val r=Reader();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();r.pageGate=CompletableDeferred();c.reload(true)
        assertNull(c.state.value.listing);runCurrent();assertEquals(listOf(0 to false,0 to true),r.pages);r.pageGate!!.complete(Unit);runCurrent();assertTrue(c.state.value.listing!!.includeDisbanded)
    }
    @Test fun refreshFailureRetainsOnlyLastGoodSameContextMetadataAndNeverRetries()=runTest {
        val r=Reader();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();val old=c.state.value.listing;r.pageError=GatewayUnavailable();c.reload();runCurrent();advanceTimeBy(60_000);runCurrent()
        assertSame(old,c.state.value.listing);assertEquals(HostedRoomBrowserPhase.Held,c.state.value.phase);assertEquals(Words.RoomsReadHeld,c.state.value.error);assertEquals(2,r.pages.size);assertFalse(g.active.value)
    }
    @Test fun unsupportedRoomsHaveAnExplainedHoldWithoutFallback()=runTest {
        val r=Reader();r.pageError=HostedRoomsUnsupported();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();assertEquals(Words.RoomsUnsupported,c.state.value.error);assertNull(c.state.value.listing);assertEquals(1,r.pages.size)
    }
    @Test fun closingAnInFlightReadCannotPublishLateMetadataOrReopen()=runTest {
        val r=Reader();r.ignoreCancellation=true;r.pageGate=CompletableDeferred();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();c.close();r.pageGate!!.complete(Unit);runCurrent();advanceTimeBy(60_000);runCurrent()
        assertEquals(HostedRoomBrowserPhase.Closed,c.state.value.phase);assertNull(c.state.value.listing);assertFalse(g.active.value);assertEquals(1,r.pages.size)
    }
    @Test fun contextChangeDuringAReadCannotExposeTheOldActorsRooms()=runTest {
        val r=Reader();r.pageGate=CompletableDeferred();val g=UpdateWorkGuard();var a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();a=access(binding.copy(owner=binding.owner.copy(actor="fixture · another")),2);r.pageGate!!.complete(Unit);runCurrent()
        assertNull(c.state.value.listing);assertEquals(Words.ManagementReviewChanged,c.state.value.error);assertFalse(g.active.value)
    }
    @Test fun tokenReplacementInvalidatesContinuationEvenForTheSameOwner()=runTest {
        val r=Reader();val g=UpdateWorkGuard();var a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();a=access(tokens=fixtureTokens());c.more();runCurrent();assertNull(c.state.value.listing);assertEquals(1,r.pages.size);assertEquals(Words.ManagementReviewChanged,c.state.value.error)
    }
    @Test fun detailAndBackKeepTheSamePageAndDoNotTriggerExtraReads()=runTest {
        val r=Reader();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();val listing=c.state.value.listing!!;c.detail(listing.page.rooms.single());runCurrent()
        assertEquals(HostedRoomBrowserPhase.Detail,c.state.value.phase);assertEquals("Fixture purpose",c.state.value.detail!!.context!!.purpose);c.back();assertSame(listing,c.state.value.listing);assertNull(c.state.value.detail);assertEquals(1,r.pages.size);assertEquals(1,r.details)
    }
    @Test fun copiedSelectionCannotReadADifferentOrUnreviewedRoom()=runTest {
        val r=Reader();val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();c.detail(c.state.value.listing!!.page.rooms.single().copy());runCurrent();assertEquals(0,r.details);assertEquals(Words.ManagementReviewChanged,c.state.value.error)
    }
    @Test fun changedProfileClearsItsOldPageBeforeAnyDetailRead()=runTest {
        val r=Reader();val g=UpdateWorkGuard();var a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();val row=c.state.value.listing!!.page.rooms.single();a=access(binding.copy(owner=binding.owner.copy(profile="other")),2);c.detail(row);runCurrent()
        assertNull(c.state.value.listing);assertEquals(0,r.details);assertEquals(Words.ManagementReviewChanged,c.state.value.error)
    }
    @Test fun detailRequiresTheCurrentReadAdvertisement()=runTest {
        val r=Reader();r.stateAdvertised=false;val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();c.detail(c.state.value.listing!!.page.rooms.single());runCurrent();assertEquals(0,r.details);assertEquals(Words.RoomsStateUnavailable,c.state.value.error)
    }
    @Test fun aForeignOrIncorrectlyPagedRepositoryReplyCannotEnterTheBrowser()=runTest {
        for(foreign in listOf(false,true)) {
            val r=Reader();if(foreign) r.replyScope=binding.copy(homeId="c".repeat(64)) else r.replyOffset=5
            val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();assertNull(c.state.value.listing);assertEquals(Words.ManagementReviewChanged,c.state.value.error)
        }
    }
    @Test fun passiveContextRecheckClearsThePageWithoutAnyNewRead()=runTest {
        val r=Reader();val g=UpdateWorkGuard();var a:HostedRoomAccess?=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();a=null;c.recheckContext();runCurrent();assertNull(c.state.value.listing);assertEquals(1,r.pages.size)
    }
    @Test fun anOldCompletionCannotReleaseANewerReadersInstallationLease()=runTest {
        val r=Reader();r.ignoreCancellation=true;val old=CompletableDeferred<Unit>();r.pageGate=old;val g=UpdateWorkGuard();val a=access();val c=browser(backgroundScope,r,g) {a};c.reload();runCurrent();c.close();val newer=CompletableDeferred<Unit>();r.pageGate=newer;c.reload();runCurrent()
        old.complete(Unit);runCurrent();assertTrue(g.active.value);assertTrue(c.state.value.busy);newer.complete(Unit);runCurrent();assertFalse(g.active.value);assertEquals(HostedRoomBrowserPhase.Page,c.state.value.phase)
    }
}
