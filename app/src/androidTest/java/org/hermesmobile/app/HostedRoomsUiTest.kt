package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Compose browser/lifecycle wiring with synthetic metadata, no network or saved credentials. */
class HostedRoomsUiTest {
    @get:Rule val compose=createComposeRule()
    private class Owner:LifecycleOwner {val registry=LifecycleRegistry(this);override val lifecycle get()=registry}
    private val lifecycleOwner=Owner()
    private val binding=HostedRoomScope(ManagementTarget("c".repeat(64),"https://hermes.test/dashboard/","worker","fixture · owner"),"d".repeat(64))
    private val tokens=ManagementTokens("room-ui-fixture","refresh-fixture","fixture","owner",4102444800)
    private class Reader:HostedRoomReader {
        val pages=mutableListOf<Pair<Int,Boolean>>();var details=0;var stateAdvertised=true;var failure:Exception?=null;var large=false;var legacy=false
        override suspend fun page(scope:HostedRoomScope,api:ManagementClient,tokens:ManagementTokens,offset:Int,includeDisbanded:Boolean):HostedRoomListing {
            pages+=offset to includeDisbanded;failure?.let {throw it}
            val member=HostedRoomMember("member-1","worker","helper","Fixture helper","local")
            val row=HostedRoom("r-$offset",if(large) "界".repeat(200) else "Fixture room",listOf(member),"gateway-1",1,1,1.25,2.75,if(includeDisbanded) 3.5 else null,null)
            val methods=if(stateAdvertised) setOf("groups.list","groups.state") else setOf("groups.list")
            val caps=HostedRoomCapabilities(scope,2,false,true,"gateway-1",methods,emptySet(),null)
            return HostedRoomListing(caps,HostedRoomPage(scope,listOf(row),offset,if(offset==0) 731 else null,42),includeDisbanded)
        }
        override suspend fun detail(listing:HostedRoomListing,selected:HostedRoom,api:ManagementClient,tokens:ManagementTokens):HostedRoomDetail {
            details++;return HostedRoomDetail(listing.scope,selected,if(legacy) null else HostedRoomContext("Fixture purpose","Fixture shared notes","lead","member-1"),if(legacy) null else HostedRoomDriverObservation(false,false,false),43)
        }
    }
    private val reader=Reader();private val guard=UpdateWorkGuard();private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var access:HostedRoomAccess?=null
    private val controller=HostedRoomBrowserController(scope,reader,{access},guard::set)
    private var shown by mutableStateOf(true)
    private var available by mutableStateOf(true)
    private fun render(large:Boolean=false) {
        compose.runOnUiThread {reader.large=large;access=HostedRoomAccess(binding,1,ManagementClient(binding.owner.url,false),tokens);lifecycleOwner.registry.currentState=Lifecycle.State.RESUMED}
        compose.setContent {HermesAppTheme(AppPreferences(chinese=large,pinyin=large,reducedMotion=true)) {
            val density=LocalDensity.current
            CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides lifecycleOwner,LocalDensity provides if(large) Density(density.density,1.8f) else density) {
                if(shown) HostedRoomsDialog(controller,available) {controller.close();shown=false}
            }
        }}
        compose.waitUntil(5000) {controller.state.value.phase==HostedRoomBrowserPhase.Page};compose.waitForIdle()
    }
    private fun scroll(tag:String):SemanticsNodeInteraction {compose.onNodeWithTag("hosted-rooms-list").performScrollToNode(hasTestTag(tag));return compose.onNodeWithTag(tag).assertIsDisplayed()}
    private fun view() {scroll("hosted-room-view-r-0").performClick();compose.waitUntil(5000) {controller.state.value.phase==HostedRoomBrowserPhase.Detail}}
    private fun capture(name:String) {
        compose.onNodeWithTag("hosted-rooms-close").assertIsDisplayed()
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val file=File(checkNotNull(context.getExternalFilesDir(null)),"qa/"+name+".png");check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Native screenshot unavailable.")
        try {file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}} finally {bitmap.recycle()}
    }
    @After fun stop() {compose.runOnUiThread {controller.close();shown=false;scope.cancel()};compose.waitForIdle();assertFalse(guard.active.value)}

    @Test fun openingReadsOnePageAndKeepsCloseReachable() {
        render();compose.onNodeWithTag("hosted-rooms-list").performScrollToNode(hasText(Words.RoomsSharedScope.en));compose.onNodeWithText(Words.RoomsSharedScope.en).assertIsDisplayed();scroll("hosted-room-view-r-0").assertIsEnabled();assertEquals(listOf(0 to false),reader.pages);assertFalse(guard.active.value)
        capture("hosted-rooms-browser");compose.onNodeWithTag("hosted-rooms-close").performClick();compose.onNodeWithTag("hosted-rooms-list").assertDoesNotExist();assertEquals(HostedRoomBrowserPhase.Closed,controller.state.value.phase)
    }
    @Test fun detailShowsPurposeNotesAndMembersWithoutAnyControlOrExtraPageRead() {
        render();view();compose.onNodeWithTag("hosted-rooms-list").performScrollToNode(hasText("Fixture shared notes"));compose.onNodeWithText("Fixture shared notes").assertIsDisplayed()
        compose.onNodeWithTag("hosted-rooms-list").performScrollToNode(hasText("Fixture helper"));compose.onNodeWithText("Fixture helper").assertIsDisplayed()
        assertEquals(1,reader.details);assertEquals(1,reader.pages.size);scroll("hosted-rooms-back").performClick();assertNull(controller.state.value.detail);assertEquals(1,reader.pages.size)
    }
    @Test fun moreAndFirstPageUseOnlyTheServerContinuationCursor() {
        render();scroll("hosted-rooms-more").performClick();compose.waitUntil(5000) {controller.state.value.listing?.page?.offset==731}
        scroll("hosted-rooms-first").performClick();compose.waitUntil(5000) {controller.state.value.listing?.page?.offset==0}
        assertEquals(listOf(0 to false,731 to false,0 to false),reader.pages)
    }
    @Test fun disbandedFilterRestartsTheListAtPageZero() {
        render();scroll("hosted-rooms-more").performClick();compose.waitUntil(5000) {controller.state.value.listing?.page?.offset==731}
        scroll("hosted-rooms-filter").performClick();compose.waitUntil(5000) {controller.state.value.listing?.includeDisbanded==true}
        assertEquals(0,controller.state.value.listing!!.page.offset);assertEquals(0 to true,reader.pages.last());compose.onNodeWithTag("hosted-rooms-list").performScrollToNode(hasText(Words.RoomsDisbanded.en));compose.onNodeWithText(Words.RoomsDisbanded.en).assertIsDisplayed()
    }
    @Test fun unsupportedDetailsAreDisabledWithAnExplanation() {
        reader.stateAdvertised=false;render();scroll("hosted-room-view-r-0").assertIsNotEnabled()
        compose.onNodeWithTag("hosted-rooms-list").performScrollToNode(hasText(Words.RoomsStateUnavailable.en));compose.onNodeWithText(Words.RoomsStateUnavailable.en).assertIsDisplayed();assertEquals(0,reader.details)
    }
    @Test fun lifecycleStopClosesAndResumeDoesNotReadAgain() {
        render();compose.runOnUiThread {lifecycleOwner.registry.currentState=Lifecycle.State.CREATED};compose.waitUntil(5000) {controller.state.value.phase==HostedRoomBrowserPhase.Closed}
        compose.runOnUiThread {lifecycleOwner.registry.currentState=Lifecycle.State.RESUMED};compose.waitForIdle();assertEquals(1,reader.pages.size)
        scroll("hosted-rooms-refresh").performClick();compose.waitUntil(5000) {controller.state.value.phase==HostedRoomBrowserPhase.Page};assertEquals(2,reader.pages.size)
    }
    @Test fun failedRefreshKeepsAnExplainedOldSnapshotWithoutRawErrorOrAutomaticRetry() {
        render();compose.runOnUiThread {reader.failure=IOException("fixture-private-room-error")};scroll("hosted-rooms-refresh").performClick();compose.waitUntil(5000) {controller.state.value.phase==HostedRoomBrowserPhase.Held}
        scroll("hosted-rooms-error").assertTextEquals(Words.RoomsReadHeld.en);compose.onAllNodesWithText("fixture-private-room-error",substring=true,useUnmergedTree=true).assertCountEquals(0)
        assertNotNull(controller.state.value.listing);assertEquals(2,reader.pages.size);capture("hosted-rooms-held")
    }
    @Test fun missingLiveStatusAndContextStayExplicitlyUnknown() {
        reader.legacy=true;render();view()
        compose.onNodeWithTag("hosted-rooms-list").performScrollToNode(hasText(Words.RoomsContextUnknown.en));compose.onNodeWithText(Words.RoomsContextUnknown.en).assertIsDisplayed()
        compose.onNodeWithTag("hosted-rooms-list").performScrollToNode(hasText(Words.RoomsStatusUnknown.en));compose.onNodeWithText(Words.RoomsStatusUnknown.en).assertIsDisplayed()
        compose.onAllNodesWithText(Words.RoomsNoWorkReported.en).assertCountEquals(0)
    }
    @Test fun largeTrilingualRoomNamesKeepDetailAndCloseActionsReachable() {
        render(large=true);scroll("hosted-room-view-r-0").assertIsEnabled().performClick();compose.waitUntil(5000) {controller.state.value.phase==HostedRoomBrowserPhase.Detail}
        compose.onNodeWithTag("hosted-rooms-close").assertIsDisplayed().assertIsEnabled();scroll("hosted-rooms-back").assertIsEnabled();capture("hosted-rooms-large-detail")
    }
}
