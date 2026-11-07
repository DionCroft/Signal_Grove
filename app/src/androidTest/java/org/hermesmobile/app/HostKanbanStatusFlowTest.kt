package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class HostKanbanStatusFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val captureToken=UUID.randomUUID().toString()
    private val app get()=compose.activity.application as HermesApplication
    private fun await()=compose.waitUntil(15000) {!vm.state.value.busy}
    private fun tap(root:String,tag:String) {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun input(root:String,tag:String,value:String) {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performTextReplacement(value);closeDialogKeyboard()}
    private fun shot(name:String) {
        compose.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(250,5000)
        val bitmap=checkNotNull(automation.takeScreenshot());val file=File(compose.activity.getExternalFilesDir(null),"qa/status-$name-$captureToken.png");file.parentFile!!.mkdirs()
        file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun exercise(target:HostKanbanStatusTarget=HostKanbanStatusTarget.Triage,mode:String="normal") {
        MockWebServer().use {server->
            val f=HostKanbanStatusFixture(server,if(mode=="lost") "lost" else "normal")
            val text="  Private 🌱 status\n任务  "
            val initial=if(mode=="graph") "done" else if(target==HostKanbanStatusTarget.Ready) "blocked" else "ready"
            val before=HostKanbanStatusFixture.fields(HostKanbanStatusFixture.card(status=initial),"result" to JsonPrimitive("Private stored result 🌱"))
            f.before["t_root"]=HostKanbanStatusFixture.detail(if(mode=="missing") JsonObject(before-"claim_lock") else before)
            val reported=if(mode=="different") "ready" else target.wire
            val after=HostKanbanStatusFixture.fields(before,"status" to JsonPrimitive(if(mode in setOf("divergent","receipt-route")) "blocked" else reported),"result" to JsonPrimitive(if(target==HostKanbanStatusTarget.Done) text else "Private stored result 🌱"))
            f.after["t_root"]=HostKanbanStatusFixture.detail(after)
            f.acknowledgement.set(buildJsonObject {put("task",HostKanbanStatusFixture.fields(after,"status" to JsonPrimitive(reported)))})
            val statement=if(target in setOf(HostKanbanStatusTarget.Done,HostKanbanStatusTarget.Blocked,HostKanbanStatusTarget.Review,HostKanbanStatusTarget.Scheduled) && mode!="omit") text else null
            f.expectedBody.set(buildJsonObject {put("status",target.wire);statement?.let {put(when(target) {HostKanbanStatusTarget.Done->"result";HostKanbanStatusTarget.Review->"summary";else->"block_reason"},it)}})
            if(mode=="graph") {
                val child=HostKanbanStatusFixture.fields(HostKanbanStatusFixture.card("t_child","done"),"title" to JsonPrimitive("Private linked impact"))
                f.before["t_root"]=HostKanbanStatusFixture.detail(before,children=listOf("t_child"))
                f.before["t_child"]=HostKanbanStatusFixture.detail(child,parents=listOf("t_root"))
                f.after["t_child"]=HostKanbanStatusFixture.detail(HostKanbanStatusFixture.fields(child,"status" to JsonPrimitive("todo")),parents=listOf("t_root"))
            }
            f.start();val inferenceUrl=server.url("prefix/inference/").toString();val original=api.preferences.value;val local=runBlocking {app.database.dao().tasks().first()};val bilingual=mode in setOf("bilingual","keyboard")
            try {
                compose.waitUntil(10000) {!api.connecting.value}
                compose.runOnUiThread {api.configure(inferenceUrl,credentials.uniqueToken("status-inference"),true);api.updatePreferences(api.preferences.value.copy(theme="STARHAVEN",chinese=bilingual,pinyin=bilingual))}
                compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==bilingual && api.preferences.value.pinyin==bilingual}
                val scope=api.profileScope.value
                app.credentials.saveManagement(scope,buildJsonObject {put("url",f.owner.url);put("profile","worker");put("debugHttp",true);put("tokens",f.tokens.json())})
                compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(10000) {vm.state.value.signedIn};compose.runOnUiThread {vm.refresh()};await()
                compose.runOnUiThread {compose.activity.setContent {val prefs by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(prefs) {ManagementEntry(api,vm)}}}
                compose.onNodeWithTag("management-open").performClick();tap("management-list","host-kanban-open")
                for(tag in listOf("kanban-inventory-read","kanban-board-read-garden","kanban-task-read-t_root")) {tap("host-kanban-list",tag);compose.onNodeWithTag("kanban-read-confirm").performClick();await()}
                tap("kanban-task-detail","status-open");val editor=checkNotNull(vm.prepareKanbanStatusEditor())
                compose.onNodeWithTag("status-read-impact").assertIsNotEnabled()
                if(mode=="availability") {
                    tap("status-form","status-target-Done");compose.onNodeWithTag("status-read-impact").assertIsNotEnabled()
                    compose.onNodeWithTag("status-form").performScrollToNode(hasTestTag("status-target-Ready"));compose.onNodeWithTag("status-target-Ready").assertIsNotEnabled()
                    compose.onNodeWithTag("status-target-Running").assertDoesNotExist();compose.onNodeWithTag("status-form-cancel").performClick();assertEquals(0,f.writes.get());return@use
                }
                tap("status-form","status-target-"+target.name)
                if(target in setOf(HostKanbanStatusTarget.Review,HostKanbanStatusTarget.Scheduled) && mode!="omit") tap("status-form","task-set-status-include")
                if(statement!=null) {
                    compose.onNodeWithTag("status-read-impact").assertIsNotEnabled()
                    input("status-form","task-field-status-statement",text)
                }
                compose.onNodeWithTag("status-read-impact").assertIsEnabled().assertHeightIsAtLeast(48.dp)
                if(bilingual) {compose.onNodeWithTag("status-form-cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp);shot("bilingual-form");compose.assertDialogActionCaptionVisible("status-read-impact")}
                if(mode=="keyboard") {
                    compose.onNodeWithTag("status-form").performScrollToNode(hasTestTag("task-field-status-statement"))
                    compose.onNodeWithTag("task-field-status-statement").performClick().assertIsFocused()
                    compose.waitUntil(5000) {focusedDialogKeyboardVisible()}
                    for(tag in listOf("status-read-impact","status-form-cancel")) compose.assertDialogActionCaptionVisible(tag)
                    shot("bilingual-keyboard-form")
                    val read=compose.onNodeWithTag("status-read-impact").assertIsEnabled().fetchSemanticsNode().boundsInWindow.center
                    tapFocusedDialogWindowPoint(read.x,read.y);await()
                    val review=checkNotNull(vm.visibleHostKanban(vm.state.value).statusReview)
                    assertEquals(f.expectedBody.get(),review.request.body());assertEquals(0,f.writes.get())
                    compose.onNodeWithTag("status-review").performScrollToNode(hasTestTag("task-field-status-confirm-id"))
                    compose.onNodeWithTag("task-field-status-confirm-id").performClick().performTextReplacement("t_root")
                    compose.onNodeWithTag("task-field-status-confirm-id").assertIsFocused()
                    compose.waitUntil(5000) {focusedDialogKeyboardVisible()}
                    compose.onNodeWithTag("status-confirm").assertIsNotEnabled()
                    for(tag in listOf("status-confirm","status-review-cancel")) compose.assertDialogActionCaptionVisible(tag)
                    shot("bilingual-keyboard-review")
                    val cancel=compose.onNodeWithTag("status-review-cancel").fetchSemanticsNode().boundsInWindow.center
                    tapFocusedDialogWindowPoint(cancel.x,cancel.y)
                    compose.waitUntil(5000) {vm.visibleHostKanban(vm.state.value).statusReview==null}
                    compose.onNodeWithTag("status-review").assertDoesNotExist();assertFalse(vm.kanbanStatusChangeCurrent(review));assertEquals(0,f.writes.get());return@use
                }
                if(mode=="cancel-form") {
                    compose.onNodeWithTag("status-form-cancel").performClick();assertFalse(vm.kanbanStatusEditorCurrent(editor));tap("kanban-task-detail","status-open")
                    compose.onNodeWithTag("status-read-impact").assertIsNotEnabled();compose.onNodeWithTag("task-field-status-statement").assertDoesNotExist();assertEquals(0,f.writes.get());return@use
                }
                var frozen:ReviewedKanbanStatusChange?=null
                if(mode!="pending-form") {
                    compose.onNodeWithTag("status-read-impact").performClick();await()
                    if(mode=="missing") {assertEquals(Words.KanbanStatusIncomplete,vm.state.value.error);compose.onNodeWithTag("status-review").assertDoesNotExist();assertEquals(0,f.writes.get());return@use}
                    frozen=checkNotNull(vm.visibleHostKanban(vm.state.value).statusReview)
                    assertEquals(f.expectedBody.get(),frozen.request.body());assertEquals(0,f.writes.get())
                    if(mode=="graph") {
                        assertEquals(setOf("t_root","t_child"),frozen.request.observations.keys)
                        for(tag in listOf("status-impact-t_root","status-impact-t_child","status-effect-MayInvalidateDescendants")) {compose.onNodeWithTag("status-review").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed()}
                    }

                    compose.onNodeWithTag("status-confirm").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
                    if(target==HostKanbanStatusTarget.Done) {compose.onNodeWithTag("status-review").performScrollToNode(hasTestTag("status-reviewed-result"));compose.onNodeWithTag("status-reviewed-result").assert(hasAnyDescendant(hasText(text)))}
                }
                if(mode in setOf("pending-form","pending-review")) {
                    val owner=editor.inventory.owner.copy(profile="alias",apiScope="e".repeat(64),actor="password · private-foreign")
                    val row=ManagementOperation(UUID.randomUUID().toString(),owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,HostKanbanStatusChange.ReceiptAction,"private-target","private-request","unknown","private-result",1)
                    runBlocking {app.database.dao().insertManagementOperation(row)};compose.waitUntil(10000) {vm.hostProjectPending(vm.state.value)}
                    compose.onNodeWithTag("status-form").assertDoesNotExist();compose.onNodeWithTag("status-review").assertDoesNotExist();compose.onNodeWithText("private-foreign",substring=true,useUnmergedTree=true).assertDoesNotExist()
                    runBlocking {app.database.dao().finishManagementOperation(row.key,"reviewed","")};compose.waitUntil(10000) {!vm.hostProjectPending(vm.state.value)}
                    compose.onNodeWithTag("status-form").assertDoesNotExist();compose.onNodeWithTag("status-review").assertDoesNotExist();assertFalse(vm.kanbanStatusEditorCurrent(editor))
                    frozen?.let {held->assertFalse(vm.kanbanStatusChangeCurrent(held));compose.runOnUiThread {vm.performKanbanStatusChange(held,"t_root",true)}}
                    assertEquals(0,f.writes.get());return@use
                }
                val review=checkNotNull(frozen)
                if(mode=="profile") {compose.runOnUiThread {vm.chooseProfile("other")};await();compose.onNodeWithTag("status-review").assertDoesNotExist();assertFalse(vm.kanbanStatusChangeCurrent(review));compose.runOnUiThread {vm.performKanbanStatusChange(review,"t_root",true)};assertEquals(0,f.writes.get());return@use}
                if(mode=="closed") {
                    val raw=vm.state.value;compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {HermesAppTheme(AppPreferences()) {HostKanbanDialog(vm,raw) {}}}}
                    compose.onNodeWithTag("status-review").assertDoesNotExist();assertNull(vm.visibleHostKanban(raw).statusReview);compose.runOnUiThread {vm.performKanbanStatusChange(review,"t_root",true)};assertEquals(0,f.writes.get());return@use
                }
                input("status-review","task-field-status-confirm-id","wrong-task");tap("status-review","task-set-status-grant");compose.onNodeWithTag("status-confirm").assertIsNotEnabled()
                input("status-review","task-field-status-confirm-id","t_root")
                if(mode=="cancel-review") {compose.onNodeWithTag("status-review-cancel").performClick();assertFalse(vm.kanbanStatusChangeCurrent(review));assertEquals(0,f.writes.get());return@use}
                if(mode=="grant") {
                    tap("status-review","task-set-status-grant");compose.onNodeWithTag("status-confirm").assertIsNotEnabled();compose.runOnUiThread {vm.performKanbanStatusChange(review,"t_root",false)};assertEquals(0,f.writes.get());tap("status-review","task-set-status-grant")
                }
                if(mode=="changed") f.before["t_root"]=HostKanbanStatusFixture.detail(HostKanbanStatusFixture.fields(before,"claim_lock" to JsonPrimitive("changed-private-claim")))
                compose.onNodeWithTag("status-confirm").assertIsDisplayed().assertIsEnabled()
                if(bilingual) {compose.onNodeWithTag("status-review-cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp);for(tag in listOf("status-confirm","status-review-cancel")) compose.assertDialogActionCaptionVisible(tag);shot("bilingual-review")}
                compose.onNodeWithTag("status-confirm").performClick();await()
                if(mode=="changed") {assertEquals(0,f.writes.get());assertEquals(Words.ManagementReviewChanged,vm.state.value.error);return@use}
                assertEquals(1,f.writes.get());val report=checkNotNull(vm.visibleHostKanban(vm.state.value).statusReport)
                assertEquals(if(mode=="lost") "unknown" else if(mode in setOf("divergent","receipt-route")) "acknowledged" else "confirmed",report.receipt.state)
                if(mode=="lost") {
                    compose.runOnUiThread {vm.observeReceipt(report.receipt.key)};await();assertEquals("unknown",runBlocking {app.database.dao().managementOperation(report.receipt.key)}!!.state);assertEquals(1,f.writes.get())
                }
                compose.runOnUiThread {vm.performKanbanStatusChange(review,"t_root",true)};assertEquals(1,f.writes.get())
                if(mode=="receipt-route") {
                    val opened=AtomicBoolean(false)
                    compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {val receiptState by vm.state.collectAsStateWithLifecycle();HermesAppTheme(AppPreferences()) {Column(Modifier.verticalScroll(rememberScrollState())) {ManagementReceiptCard(report.receipt,vm,receiptState,openKanban={opened.set(true)})}}}}
                    for(tag in listOf("board-receipt-open-"+report.receipt.key,"management-observe-"+report.receipt.key)) {opened.set(false);compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick();assertTrue(opened.get())}
                    assertEquals(1,f.writes.get());assertEquals("acknowledged",runBlocking {app.database.dao().managementOperation(report.receipt.key)}!!.state);return@use
                }

                if(vm.state.value.kanban.detail!=null) compose.onNodeWithTag("kanban-detail-close").performClick()
                compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("status-report"));compose.onNodeWithTag("status-report").assertIsDisplayed()
                val word=when(mode) {"different"->Words.KanbanStatusDifferent;"divergent"->Words.KanbanStatusReadback;"lost"->Words.KanbanStatusUnknown;else->Words.KanbanStatusReceiptBoundary}
                compose.onNodeWithTag("status-report").assert(hasAnyDescendant(hasText(word.localized(api.preferences.value))))
                if(bilingual) {shot("bilingual-result");compose.onNodeWithTag("kanban-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)}
            } finally {
                f.release.countDown();compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(theme=original.theme,chinese=original.chinese,pinyin=original.pinyin))}
                compose.waitUntil(10000) {api.preferences.value.theme==original.theme && api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin}
                assertEquals(local,runBlocking {app.database.dao().tasks().first()});f.assertClean()
            }
        }
    }
    @Test fun triageUsesOnePrivateReviewedStatusRequest()=exercise()
    @Test fun todoRemainsAStatusOperationWithoutDispatchControls()=exercise(HostKanbanStatusTarget.Todo)
    @Test fun readyReopensBlockedWorkThroughReviewedImpact()=exercise(HostKanbanStatusTarget.Ready)
    @Test fun doneRequiresAndPreservesExactReplacementResult()=exercise(HostKanbanStatusTarget.Done)
    @Test fun blockedRequiresAndPreservesExactReason()=exercise(HostKanbanStatusTarget.Blocked)
    @Test fun reviewCanIncludeAnExactHandoffSummary()=exercise(HostKanbanStatusTarget.Review)
    @Test fun scheduledCanIncludeAReasonWithoutInventingWakeTime()=exercise(HostKanbanStatusTarget.Scheduled)
    @Test fun archiveIsReviewedAsAHostOperation()=exercise(HostKanbanStatusTarget.Archived)
    @Test fun optionalScheduleReasonIsNotAddedWithoutTheChoice()=exercise(HostKanbanStatusTarget.Scheduled,"omit")
    @Test fun unsupportedSameStateAndRunningControlsCannotBeSelected()=exercise(mode="availability")
    @Test fun cancellingTheFormDropsPrivateValues()=exercise(HostKanbanStatusTarget.Done,"cancel-form")
    @Test fun cancellingTheFinalReviewDoesNotSend()=exercise(mode="cancel-review")
    @Test fun exactTaskIdAndExplicitLatestImpactGrantAreBothRequired()=exercise(mode="grant")
    @Test fun sharedPendingStatusWorkDiscardsEditorAndFrozenContext()=exercise(mode="pending-form")
    @Test fun sharedPendingStatusWorkDiscardsFinalReviewPermanently()=exercise(mode="pending-review")
    @Test fun profileSwitchInvalidatesFrozenStatusApproval()=exercise(mode="profile")
    @Test fun closingRejectsOldRawStateAndFrozenStatusCall()=exercise(mode="closed")
    @Test fun aChangedClaimRefusesBeforeAnyStatusWrite()=exercise(mode="changed")
    @Test fun lostReplyStaysUnknownAndCannotUseGenericMcpObservation()=exercise(mode="lost")
    @Test fun differingHostStatusIsReportedWithoutCorrectiveReplay()=exercise(mode="different")
    @Test fun mismatchedReadbackRetainsOnlyAcknowledgement()=exercise(mode="divergent")
    @Test fun missingClaimMetadataStopsAtTheReadOnlyStage()=exercise(mode="missing")
    @Test fun connectedImpactAndDescendantResetEffectsAreFullyReviewable()=exercise(mode="graph")
    @Test fun durableStatusReceiptLinksReturnToKanbanInsteadOfMcpObservation()=exercise(mode="receipt-route")
    @Test fun bilingualKeyboardLeavesReadAndCancelReachableWithoutGrantingStatusWrite()=exercise(HostKanbanStatusTarget.Done,"keyboard")
    @Test fun bilingualPrivateReviewAndReportKeepFooterActionsReachable()=exercise(HostKanbanStatusTarget.Done,"bilingual")
}
