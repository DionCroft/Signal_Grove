package org.hermesmobile.app

import androidx.compose.foundation.lazy.LazyColumn

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
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
import kotlinx.serialization.json.*
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
        val snapshot=CanonicalSessionSnapshot(owner,"fixture-history","fixture-history","fixture-subscription",cursor,2,3,false,2,true,CanonicalWorkspaceDirectory.Reported("/fixture/private-project"))
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
        var workspaceChanges=0
        var workspaceError:Exception?=null
        var adoptions=0;var requireHeartbeat=false;var saves=0;var previousReads=0
        var expectedApplied=cursor
        var adoptError:Exception?=null
        override suspend fun prepare(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens,scope:CanonicalSessionScope):CanonicalSessionInventory {
            assertEquals(this@CanonicalViewerUiTest.target,target);assertEquals(home,homeId);reads+=scope;return inventory(scope)
        }
        override suspend fun adopt(review:CanonicalResumeReview,api:ManagementClient,tokens:ManagementTokens,previous:CanonicalReplayCheckpoint?,requireHeartbeat:Boolean):CanonicalViewerLink {
            adoptions++;this.requireHeartbeat=requireHeartbeat;review.consume(review.expected);adoptError?.let {throw it};return link
        }
        override suspend fun changeWorkspace(review:CanonicalWorkspaceReview,api:ManagementClient,tokens:ManagementTokens):CanonicalWorkspaceAcknowledgement {
            workspaceChanges++;review.consume(review.expected);workspaceError?.let {throw it}
            return CanonicalWorkspaceAcknowledgement.parse(buildJsonObject {
                put("session_id",owner.sessionId);put("operation","workspace");put("revision",3);put("execution_generation",4);put("cwd",review.requestedDirectory)
            },review)
        }
        override suspend fun lastApplied(target:ManagementTarget,homeId:String,requested:String):CanonicalReplayCheckpoint? {previousReads++;return null}
        override suspend fun recordApplied(key:String,owner:CanonicalFeedOwner,cursor:CanonicalReplayCursor) {
            assertEquals(link.receipt.key,key);assertEquals(this@CanonicalViewerUiTest.owner,owner);assertEquals(expectedApplied,cursor);saves++
        }
    }
    private val repo=Repo()
    private var access:CanonicalViewerAccess?=null
    private val viewer=CanonicalViewerController(scope,repo,{access},guard::set)
    private var shown by mutableStateOf(true)
    private var available by mutableStateOf(true)
    private fun render(pending:Boolean=false,large:Boolean=false,selection:CanonicalViewerSelection?=null) {
        compose.runOnUiThread {
            access=CanonicalViewerAccess(target,home,1,ManagementClient(target.url,false),tokens)
            lifecycleOwner.registry.currentState=Lifecycle.State.RESUMED
        }
        compose.setContent {
            HermesAppTheme(AppPreferences(chinese=large,pinyin=large,reducedMotion=true)) {
                val density=LocalDensity.current
                CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides lifecycleOwner,
                    LocalDensity provides if(large) Density(density.density,1.8f) else density) {
                    if(shown) CanonicalSessionsDialog(viewer,available,pending,selection) {viewer.close();shown=false}
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
    private fun capture(tag:String,name:String) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(checkNotNull(context.getExternalFilesDir(null)),"qa/"+name+".png")
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("The native screenshot was unavailable.")
        try {file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}
        finally {bitmap.recycle()}
    }
    private fun open() {
        review();consent();compose.onNodeWithTag("canonical-open-confirm").assertIsEnabled().performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Following}
        scroll("canonical-viewer-list","canonical-following").assertIsDisplayed()
    }
    @After fun stop() {
        compose.runOnUiThread {viewer.close();shown=false;scope.cancel()}
        compose.waitForIdle();assertFalse(guard.active.value)
    }
    @Test fun projectConversationSelectionShowsConsentBeforeOpeningAtLargeCaptions() {
        render(large=true,selection=CanonicalViewerSelection(target,home,1,"fixture-history"))
        assertEquals(listOf(CanonicalSessionScope.Stored),repo.reads);assertEquals(0,repo.adoptions);assertEquals(0,repo.previousReads)
        compose.onNodeWithTag("canonical-open-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("canonical-open-cancel").assertIsDisplayed().performClick();assertEquals(0,repo.adoptions)
        review();consent();compose.onNodeWithTag("canonical-open-confirm").assertIsEnabled().performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Following};assertEquals(1,repo.adoptions);assertTrue(repo.requireHeartbeat);assertEquals(0,repo.workspaceChanges)
    }

    @Test fun workspaceDetailsArePrivateUntilOpenedAndClearedOnClose() {
        render();open()
        compose.onNodeWithTag("canonical-workspace-path").assertDoesNotExist()
        scroll("canonical-viewer-list","canonical-workspace-open").performClick()
        scroll("canonical-viewer-list","canonical-workspace-path").assertTextEquals("/fixture/private-project")
        assertNotNull(viewer.state.value.workspaceObservedAt)
        compose.onNodeWithTag("canonical-viewer-list").performScrollToNode(hasText(Words.ViewerWorkspaceBoundary.en))
        compose.onNodeWithText(Words.ViewerWorkspaceBoundary.en).assertIsDisplayed()
        capture("canonical-viewer-list","canonical-workspace-details")
        compose.onNodeWithTag("canonical-close").performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Closed}
        assertNull(viewer.state.value.workspace);assertNull(viewer.state.value.workspaceObservedAt)
        assertEquals(1,repo.adoptions);assertEquals(1,repo.link.closes)
    }

    private fun editWorkspace() {
        scroll("canonical-viewer-list","canonical-workspace-open").performClick()
        scroll("canonical-viewer-list","workspace-edit").performClick()
        compose.onNodeWithTag("workspace-directory").performScrollTo().performTextReplacement("/fixture/new project")
    }
    private fun grantWorkspaceConsent() {
        val checkbox=compose.onNodeWithTag("workspace-consent").performScrollTo().assertIsOff()
        compose.waitUntil(5000) {!focusedDialogKeyboardVisible()}
        var previous:androidx.compose.ui.geometry.Rect?=null;var stableAt=0L
        compose.waitUntil(5000) {
            val bounds=checkbox.fetchSemanticsNode().boundsInWindow;val now=android.os.SystemClock.uptimeMillis()
            if(bounds!=previous) {previous=bounds;stableAt=now;false} else now-stableAt>=250
        }
        // One tap only. Require the checkbox itself to change before inspecting Confirm.
        checkbox.performClick().assertIsOn()
    }
    @Test fun workspaceCancelFromCheckedReviewNeverChangesTheSession() {
        render();open();editWorkspace()
        compose.onNodeWithTag("workspace-review").assertIsEnabled().performClick()
        compose.onNodeWithTag("workspace-confirm").assertIsNotEnabled()
        grantWorkspaceConsent()
        compose.onNodeWithTag("workspace-confirm").assertIsEnabled()
        compose.onNodeWithTag("workspace-cancel").performClick()
        assertEquals(0,repo.workspaceChanges);assertEquals(CanonicalViewerPhase.Following,viewer.state.value.phase)
    }
    @Test fun workspaceRefusalAdviceDoesNotClaimAcknowledgementOrRetry() {
        render();open()
        val cases=listOf("session_busy" to Words.WorkspaceBusy,"compression" to Words.WorkspaceCompressing,"permission_denied" to Words.WorkspacePermission)
        for((index,pair) in cases.withIndex()) {
            val (reason,word)=pair
            repo.workspaceError=CanonicalWorkspaceRefused.parse(4001,buildJsonObject {
                put("message",if(reason=="compression") "session_busy" else reason)
                put("data",buildJsonObject {put("reason",if(reason=="compression") "session_busy" else reason);if(reason=="compression") put("field","compression");put("detail","private-fixture-detail")})
            })
            assertNotNull(repo.workspaceError)
            if(index==0) editWorkspace() else {
                scroll("canonical-viewer-list","workspace-edit").performClick()
                compose.onNodeWithTag("workspace-directory").performScrollTo().performTextReplacement("/fixture/new project")
            }
            compose.onNodeWithTag("workspace-review").performClick()
            // This checks advice/uncertainty state. Other consent tests exercise physical taps.
            val consent=compose.onNodeWithTag("workspace-consent").performScrollTo().assertIsOff()
            consent.performSemanticsAction(SemanticsActions.OnClick) {it()}
            consent.assertIsOn()
            compose.onNodeWithTag("workspace-confirm").performClick()
            compose.waitUntil(5000) {viewer.state.value.workspaceNotice==word && !viewer.state.value.workspaceChanging}
            scroll("canonical-viewer-list","workspace-result").assertTextEquals(word.en)
            assertEquals(index+1,repo.workspaceChanges);assertEquals(CanonicalViewerPhase.Following,viewer.state.value.phase)
            assertNotNull(viewer.state.value.workspaceSnapshot);assertTrue("An attached viewer retains its installation guard",guard.active.value)
            compose.onAllNodesWithText(Words.WorkspaceAcknowledged.en).assertCountEquals(0)
            compose.onAllNodesWithText("private-fixture-detail",substring=true).assertCountEquals(0)
            capture("workspace-result","workspace-refusal-"+reason)
        }
        compose.onNodeWithTag("canonical-close").performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Closed}
        assertFalse(guard.active.value)
    }

    @Test fun checkedWorkspaceReviewSendsOnceAndRequiresAFreshAttachmentAfterAcknowledgement() {
        render();open();editWorkspace()
        compose.onNodeWithTag("workspace-review").performClick()
        compose.onNodeWithTag("workspace-reviewed-directory").performScrollTo().assertTextEquals("/fixture/new project")
        grantWorkspaceConsent()
        capture("workspace-confirm","workspace-review-checked")
        compose.onNodeWithTag("workspace-confirm").performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Closed}
        assertEquals(1,repo.workspaceChanges);assertNull(viewer.state.value.workspaceSnapshot)
        scroll("canonical-viewer-list","workspace-result").assertTextEquals(Words.WorkspaceAcknowledged.en)
        assertEquals(1,repo.link.closes)
    }
    @Test fun largeBilingualWorkspaceFormKeepsKeyboardActionsReachableAndCancelHasNoEffect() {
        render(large=true);open();editWorkspace()
        compose.onNodeWithTag("workspace-directory").performScrollTo().performClick()
        compose.waitUntil(5000) {focusedDialogKeyboardVisible()}
        var previous:FocusedDialogViewport?=null;var stableAt=0L
        compose.waitUntil(5000) {
            val current=focusedDialogViewport();val now=android.os.SystemClock.uptimeMillis()
            if(current==null || previous!=current) {previous=current;stableAt=now;false} else now-stableAt>=250
        }
        val viewport=checkNotNull(previous)
        try {
            for((tag,word) in listOf("workspace-review" to Words.WorkspaceReview,"workspace-cancel" to Words.Cancel)) {
                val control=compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInWindow
                val bounds="tag=$tag control=$control viewport=$viewport"
                assertTrue("Complete workspace action above native IME: "+bounds,control.left+viewport.screenOffsetX>=viewport.visible.left && control.right+viewport.screenOffsetX<=viewport.visible.right && control.top+viewport.screenOffsetY>=viewport.visible.top && control.bottom+viewport.screenOffsetY<=viewport.visible.bottom)
                val text=word.localized(AppPreferences(chinese=true,pinyin=true))
                val native=focusedDialogCaptionScreenBounds(text,text)
                assertTrue("Complete workspace caption above IME: $bounds native=$native",viewport.visible.contains(native))
            }
        } catch(failure:Throwable) {
            runCatching {capture("workspace-review","workspace-form-large-failed")}
            throw failure
        }
        capture("workspace-review","workspace-form-large")
        compose.onNodeWithTag("workspace-review").performClick()
        compose.waitUntil(5000) {!focusedDialogKeyboardVisible()}
        var lastCancel:android.graphics.Rect?=null;var cancelStableAt=0L
        compose.waitUntil(5000) {
            val text=Words.Cancel.localized(AppPreferences(chinese=true,pinyin=true))
            val bounds=focusedDialogCaptionScreenBounds(text,text);val now=android.os.SystemClock.uptimeMillis()
            if(lastCancel!=bounds) {lastCancel=bounds;cancelStableAt=now;false} else now-cancelStableAt>=250
        }
        grantWorkspaceConsent()
        compose.onNodeWithTag("workspace-confirm").assertIsDisplayed().assertIsEnabled()
        val cancel=compose.onNodeWithTag("workspace-cancel").assertIsDisplayed().fetchSemanticsNode().boundsInWindow.center
        val tapId=java.util.UUID.randomUUID().toString();var primary:Throwable?=null
        try {
            tapFocusedDialogWindowPoint(cancel.x,cancel.y,NativeDialogTapAction.WorkspaceCancel,tapId)
            compose.waitUntil(5000) {compose.onAllNodesWithTag("workspace-cancel").fetchSemanticsNodes().isEmpty()}
            assertEquals(0,repo.workspaceChanges)
        } catch(error:Throwable) {primary=error;throw error}
        finally {
            // One read-only observation even if dispatch reports WARNING/failure. It cannot accept the test or tap again.
            val present=runCatching {compose.onAllNodesWithTag("workspace-cancel").fetchSemanticsNodes().isNotEmpty()}
            try {writeNativeTapDiagnostic("workspace-cancel-observation-$tapId",buildJsonObject {
                put("schema",1);put("id",tapId);put("api",android.os.Build.VERSION.SDK_INT)
                put("cancelControlPresent",present.getOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                put("observationAvailable",present.isSuccess);put("workspaceChanges",repo.workspaceChanges)
                put("viewerPhase",viewer.state.value.phase.name);put("readOnly",true)
            })} catch(diagnostic:Throwable) {if(primary!=null) primary.addSuppressed(diagnostic) else throw diagnostic}
        }
    }

    @Test fun workspaceReceiptShowsExactRequestIdAndAcknowledgementWithoutLeakingPrivateDetails() {
        val row=repo.link.receipt.copy(action=CanonicalWorkspaceAction.Id,state="acknowledged",requestSummary="private-directory-sentinel")
        var receipt by mutableStateOf(row)
        compose.setContent {HermesAppTheme(AppPreferences(chinese=false,pinyin=false,reducedMotion=true)) {CanonicalViewerReceiptCard(receipt,null,null)}}
        compose.onNodeWithTag("workspace-receipt-request-id").assertTextEquals(row.key)
        compose.onNodeWithText(Words.ManagementAcknowledged.en).assertIsDisplayed()
        compose.onAllNodesWithText("private-directory-sentinel",substring=true).assertCountEquals(0)
        compose.runOnUiThread {receipt=row.copy(state="confirmed")}
        compose.onNodeWithText(Words.ManagementAcknowledged.en).assertIsDisplayed()
        compose.onAllNodesWithText(Words.ManagementVerified.en).assertCountEquals(0)
    }

    @Test fun unknownWorkspaceReceiptExplainsExactHostInspectionWithoutInventingSuccess() {
        var row by mutableStateOf(repo.link.receipt.copy(action=CanonicalWorkspaceAction.Id,state="unknown"))
        compose.setContent {HermesAppTheme(AppPreferences(chinese=false,pinyin=false,reducedMotion=true)) {
            LazyColumn {item {CanonicalViewerReceiptCard(row,null,null)}}
        }}
        compose.onNodeWithTag("workspace-receipt-request-id").assertTextEquals(row.key)
        compose.onNodeWithTag("canonical-receipt-unknown-note").assertTextEquals(Words.WorkspaceUncertain.en).assertIsDisplayed()
        compose.onAllNodesWithText(Words.ManagementAcknowledged.en).assertCountEquals(0)
        compose.onAllNodesWithText(Words.ManagementVerified.en).assertCountEquals(0)
        compose.onAllNodesWithTag("canonical-receipt-review").assertCountEquals(0)
        compose.runOnUiThread {row=row.copy(state="acknowledged")}
        compose.onAllNodesWithTag("canonical-receipt-unknown-note").assertCountEquals(0)
        compose.onNodeWithText(Words.ManagementAcknowledged.en).assertIsDisplayed()
    }

    @Test fun privateReasoningAdvancesTheCursorWithoutEnteringVisibleActivity() {
        render();open()
        val secret="fixture-private-reasoning-sentinel"
        fun event(sequence:Long,type:String,text:String)=CanonicalFeedEvent.parse(buildJsonObject {
            put("type",type);put("session_id",owner.sessionId);put("replay_epoch",cursor.epoch);put("seq",sequence)
            put("payload",buildJsonObject {put("text",text);if(type=="status.update") put("kind","status")})
        },owner,cursor,65536)
        compose.runOnUiThread {
            repo.expectedApplied=cursor.copy(sequence=3)
            val events=listOf(event(1,"status.update","Fixture public activity"),event(2,"reasoning.delta",secret),event(3,"subagent.thinking",secret))
            check(repo.link.events.trySend(CanonicalFeedDelivery(CanonicalFeedStatus(CanonicalFeedPhase.Following,repo.expectedApplied,null,0),events)).isSuccess)
        }
        compose.waitUntil(5000) {viewer.state.value.cursor?.sequence==3L && repo.saves==2}
        val activity=viewer.state.value.activities
        assertEquals(3L,viewer.state.value.observed);assertEquals(listOf(1L,2L,3L),activity.map {it.sequence})
        assertEquals(listOf("Fixture public activity","",""),activity.map {it.text})
        assertEquals(listOf(Words.ViewerPrivateActivity,Words.ViewerPrivateActivity),activity.drop(1).map {it.kind})
        compose.onNodeWithTag("canonical-viewer-list").performScrollToNode(hasText(Words.ViewerPrivateActivity.en))
        compose.onAllNodesWithText(Words.ViewerPrivateActivity.en).onFirst().assertIsDisplayed()
        compose.onAllNodesWithText(secret,substring=true,useUnmergedTree=true).assertCountEquals(0)
        assertTrue(guard.active.value)
        capture("canonical-viewer-list","canonical-viewer-private-activity")
    }

    @Test fun readingAndCancellingAnUncheckedReviewCannotAdopt() {
        render();assertEquals(listOf(CanonicalSessionScope.Live),repo.reads);assertFalse(guard.active.value)
        review();assertEquals(0,repo.adoptions)
        compose.onNodeWithTag("canonical-open-cancel").performClick()
        compose.onNodeWithTag("canonical-open-confirm").assertDoesNotExist()
        assertEquals(0,repo.adoptions);assertEquals(0,repo.previousReads);assertFalse(guard.active.value)
    }
    @Test fun explicitConsentOpensOnceAndCloseReleasesTheActualInstallationGuard() {
        render();open();capture("canonical-following","canonical-viewer-following")
        assertEquals(1,repo.adoptions);assertTrue(repo.requireHeartbeat)
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
        capture("canonical-open-confirm","canonical-viewer-large-review")
        compose.onNodeWithTag("canonical-open-cancel").assertIsDisplayed().performClick()
        assertEquals(0,repo.adoptions);assertFalse(guard.active.value)
    }
    @Test fun unavailableProfileTransportExplainsTheWebappAddressInAllEnabledLanguages() {
        repo.adoptError=CanonicalProfileTransportUnavailable();render(large=true);review();consent()
        compose.onNodeWithTag("canonical-open-confirm").performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Held && !guard.active.value}
        scroll("canonical-viewer-list","canonical-error").assertTextEquals(Words.ViewerProfileTransportUnavailable.localized(AppPreferences(chinese=true,pinyin=true)))
        compose.waitForIdle();assertEquals(1,repo.adoptions);assertEquals(0,repo.saves)
        capture("canonical-error","profile-ticket-unavailable-large")
    }
    @Test fun unsupportedHeartbeatShowsAnExplainedHoldWithoutAutomaticRetry() {
        repo.adoptError=CanonicalNotSupported();render();review();consent()
        compose.onNodeWithTag("canonical-open-confirm").performClick()
        compose.waitUntil(5000) {viewer.state.value.phase==CanonicalViewerPhase.Held && !guard.active.value}
        scroll("canonical-viewer-list","canonical-error").assertTextEquals(Words.ViewerUnsupported.en)
        compose.waitForIdle();assertEquals(1,repo.adoptions);assertEquals(0,repo.saves)
    }
}
