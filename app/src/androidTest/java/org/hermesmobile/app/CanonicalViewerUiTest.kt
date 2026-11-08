package org.hermesmobile.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.JsonPrimitive
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.updates.UpdateWorkGuard
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Compose consent/lifecycle/installation-guard wiring; no owner data or real backend. */
class CanonicalViewerUiTest {
    @get:Rule val compose=createComposeRule()
    private val target=ManagementTarget("c".repeat(64),"https://hermes.test/dashboard/","default","fixture · owner")
    private val profile="/fixture/viewer-home"
    private val home=checkNotNull(PluginHome.project(JsonPrimitive(profile))).identity()
    private val binding=CanonicalRuntimeBinding(target,home,profile,"fixture-runtime",3)
    private val owner=CanonicalFeedOwner(target,home,profile,"fixture-runtime","fixture-history",3)
    private val cursor=CanonicalReplayCursor("fixture-ring",0)
    private val tokens=ManagementTokens("native-viewer-test-access","native-viewer-test-refresh","fixture","owner",4102444800)
    private fun inventory(scope:CanonicalSessionScope)=CanonicalSessionInventory(binding,
        listOf(CanonicalStoredSession("fixture-history","fixture-history","fixture-history","Fixture conversation","cli",null,2,1.0,false,"fingerprint")),scope)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val guard=UpdateWorkGuard()
    private val lifecycleOwner=object:LifecycleOwner {
        val registry=LifecycleRegistry(this)
        override val lifecycle:Lifecycle get()=registry
    }
    private inner class Link:CanonicalViewerLink {
        val events=Channel<CanonicalFeedDelivery>(Channel.UNLIMITED)
        val initial=CanonicalFeedDelivery(CanonicalFeedStatus(CanonicalFeedPhase.Following,cursor,null,0),emptyList())
        val snapshot=CanonicalSessionSnapshot(owner,"fixture-history","fixture-history","fixture-subscription",cursor,2,3,false,2,true)
        override val attachment=CanonicalGatewayChannel.Attachment(snapshot,initial)
        override val receipt=ManagementOperation("native-viewer-receipt",target.resourceScope,target.apiScope,target.url,target.profile,target.actor,
            CanonicalResumeAction.Id,"fixture-history",inventory(CanonicalSessionScope.Live).review("fixture-history").summary(),"confirmed",snapshot.receiptSummary(),1,home)
        var closes=0;var beats=0
        override fun observations()=events.receiveAsFlow()
        override suspend fun heartbeat() {beats++}
        override fun close() {if(closes==0) {closes++;events.close()}}
    }
    private inner class Repo:CanonicalViewerRepository {
        val link=Link();val reads=mutableListOf<CanonicalSessionScope>()
        var adoptions=0;var requireHeartbeat=false;var saves=0;var previousReads=0
        var adoptError:Exception?=null
        override suspend fun prepare(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens,scope:CanonicalSessionScope):CanonicalSessionInventory {
            assertEquals(this@CanonicalViewerUiTest.target,target);assertEquals(home,homeId);reads+=scope;return inventory(scope)
        }
        override suspend fun adopt(review:CanonicalResumeReview,api:ManagementClient,tokens:ManagementTokens,previous:CanonicalReplayCheckpoint?,requireHeartbeat:Boolean):CanonicalViewerLink {
            adoptions++;this.requireHeartbeat=requireHeartbeat;review.consume(review.expected);adoptError?.let {throw it};return link
        }
        override suspend fun lastApplied(target:ManagementTarget,homeId:String,requested:String):CanonicalReplayCheckpoint? {previousReads++;return null}
        override suspend fun recordApplied(key:String,owner:CanonicalFeedOwner,cursor:CanonicalReplayCursor) {
            assertEquals(link.receipt.key,key);assertEquals(this@CanonicalViewerUiTest.owner,owner);assertEquals(this@CanonicalViewerUiTest.cursor,cursor);saves++
        }
    }
    private val repo=Repo()
    private var access:CanonicalViewerAccess?=null
    private val viewer=CanonicalViewerController(scope,repo,{access},guard::set)
    private var shown by mutableStateOf(true)
    private var available by mutableStateOf(true)
    private fun render(pending:Boolean=false,large:Boolean=false) {
        compose.runOnUiThread {
            access=CanonicalViewerAccess(target,home,1,ManagementClient(target.url,false),tokens)
            lifecycleOwner.registry.currentState=Lifecycle.State.RESUMED
        }
        compose.setContent {
            HermesAppTheme(AppPreferences(chinese=large,pinyin=large,reducedMotion=true)) {
                val density=LocalDensity.current
                CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides lifecycleOwner,
                    LocalDensity provides if(large) Density(density.density,1.8f) else density) {
                    if(shown) CanonicalSessionsDialog(viewer,available,pending) {viewer.close();shown=false}
                }
            }
        }
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Inventory}
        compose.waitForIdle()
    }
    private fun scroll(container:String,tag:String):SemanticsNodeInteraction {
        compose.onNodeWithTag(container).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun review() {
        scroll("canonical-viewer-list","canonical-review-fixture-history").performClick()
        compose.onNodeWithTag("canonical-open-confirm").assertIsNotEnabled()
    }
    private fun consent() {scroll("canonical-open-review","canonical-consent").performClick()}
    private fun open() {
        review();consent();compose.onNodeWithTag("canonical-open-confirm").assertIsEnabled().performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Following}
        scroll("canonical-viewer-list","canonical-following").assertIsDisplayed()
    }
    @After fun stop() {
        compose.runOnUiThread {viewer.close();shown=false;scope.cancel()}
        compose.waitForIdle();assertFalse(guard.active.value)
    }
    @Test fun readingAndCancellingAnUncheckedReviewCannotAdopt() {
        render();assertEquals(listOf(CanonicalSessionScope.Live),repo.reads);assertFalse(guard.active.value)
        review();assertEquals(0,repo.adoptions)
        compose.onNodeWithTag("canonical-open-cancel").performClick()
        compose.onNodeWithTag("canonical-open-confirm").assertDoesNotExist()
        assertEquals(0,repo.adoptions);assertEquals(0,repo.previousReads);assertFalse(guard.active.value)
    }
    @Test fun explicitConsentOpensOnceAndCloseReleasesTheActualInstallationGuard() {
        render();open();assertEquals(1,repo.adoptions);assertTrue(repo.requireHeartbeat)
        assertTrue(guard.active.value);assertEquals(1,repo.saves);assertEquals(1,repo.link.beats)
        compose.onNodeWithTag("canonical-close").performClick()
        compose.waitUntil(5000) {!guard.active.value}
        assertEquals(1,repo.link.closes);assertEquals(CanonicalViewerPhase.Closed,viewer.state.value.phase)
        compose.onNodeWithTag("canonical-viewer-list").assertDoesNotExist()
    }
    @Test fun lifecycleStopClosesTheLinkAndResumeNeverReopensIt() {
        render();open()
        compose.runOnUiThread {lifecycleOwner.registry.currentState=Lifecycle.State.CREATED}
        compose.waitUntil(5000) {!guard.active.value && repo.link.closes==1}
        compose.runOnUiThread {lifecycleOwner.registry.currentState=Lifecycle.State.RESUMED}
        compose.waitForIdle();assertEquals(CanonicalViewerPhase.Closed,viewer.state.value.phase)
        assertEquals(1,repo.adoptions);assertEquals(1,repo.reads.size)
        compose.onNodeWithTag("canonical-following").assertDoesNotExist()
    }
    @Test fun removingTheDialogFromCompositionClosesItsLink() {
        render();open();compose.runOnUiThread {shown=false}
        compose.waitUntil(5000) {!guard.active.value && repo.link.closes==1}
        assertEquals(CanonicalViewerPhase.Closed,viewer.state.value.phase);assertEquals(1,repo.adoptions)
    }
    @Test fun changedAuthenticatedContextCannotReuseACheckedConsent() {
        render();review();consent();compose.onNodeWithTag("canonical-open-confirm").assertIsEnabled()
        compose.runOnUiThread {access=null;available=false}
        compose.waitForIdle();compose.onNodeWithTag("canonical-open-confirm").assertDoesNotExist()
        assertEquals(0,repo.adoptions);assertEquals(0,repo.previousReads);assertFalse(guard.active.value)
    }
    @Test fun anUncertainReceiptDisablesResumeAndReturnsToReceiptInspection() {
        render(pending=true)
        scroll("canonical-viewer-list","canonical-review-fixture-history").assertIsNotEnabled()
        scroll("canonical-viewer-list","canonical-return-receipts").performClick()
        compose.onNodeWithTag("canonical-viewer-list").assertDoesNotExist()
        assertEquals(0,repo.adoptions);assertFalse(guard.active.value)
    }
    @Test fun storedInventoryStillNeedsFreshConsentAndCanSkipOnlyTheSavedCursor() {
        render();scroll("canonical-viewer-list","canonical-stored").performClick()
        compose.waitUntil(5000) {viewer.state.value.scope==CanonicalSessionScope.Stored && !viewer.state.value.busy}
        assertEquals(listOf(CanonicalSessionScope.Live,CanonicalSessionScope.Stored),repo.reads)
        review();scroll("canonical-open-review","canonical-fresh").performClick();consent()
        compose.onNodeWithTag("canonical-open-confirm").performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Following}
        assertEquals(0,repo.previousReads);assertEquals(1,repo.adoptions);assertTrue(repo.requireHeartbeat)
    }
    @Test fun largeBilingualConsentAndBothDecisionsStayReachable() {
        render(large=true);review();consent()
        compose.onNodeWithTag("canonical-open-confirm").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("canonical-open-cancel").assertIsDisplayed().performClick()
        assertEquals(0,repo.adoptions);assertFalse(guard.active.value)
    }
    @Test fun unsupportedHeartbeatShowsAnExplainedHoldWithoutAutomaticRetry() {
        repo.adoptError=CanonicalNotSupported();render();review();consent()
        compose.onNodeWithTag("canonical-open-confirm").performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Held && !guard.active.value}
        scroll("canonical-viewer-list","canonical-error").assertTextEquals(Words.ViewerUnsupported.en)
        compose.waitForIdle();assertEquals(1,repo.adoptions);assertEquals(0,repo.saves)
    }
}
