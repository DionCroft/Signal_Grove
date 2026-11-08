package org.hermesmobile.app

import android.graphics.Bitmap
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
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class HostKanbanTaskFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun tap(root:String,tag:String) {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun input(tag:String,value:String,root:String="task-form") {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performTextReplacement(value);closeDialogKeyboard()}
    private fun await()=compose.waitUntil(15000) {!vm.state.value.busy}
    private fun capture(name:String) {compose.waitForIdle();val ui=InstrumentationRegistry.getInstrumentation().uiAutomation;ui.waitForIdle(250,5000);val bitmap=checkNotNull(ui.takeScreenshot());val file=File(compose.activity.getExternalFilesDir(null),"qa/task-edit-$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
    private fun exercise(mode:String) {
        val action=when(mode) {"priority"->HostKanbanTaskAction.Priority;"assign","unassign"->HostKanbanTaskAction.Assignee;"comment"->HostKanbanTaskAction.Comment;"delete"->HostKanbanTaskAction.Delete;else->HostKanbanTaskAction.Metadata}
        val changed=when(action) {
            HostKanbanTaskAction.Priority->JsonObject(HostKanbanTaskFixture.card()+mapOf("priority" to JsonPrimitive(Int.MIN_VALUE)))
            HostKanbanTaskAction.Assignee->JsonObject(HostKanbanTaskFixture.card()+mapOf("assignee" to if(mode=="unassign") JsonNull else JsonPrimitive("other")))
            HostKanbanTaskAction.Metadata->JsonObject(HostKanbanTaskFixture.card(title=if(mode=="body-only") "Private task" else "Reviewed title")+mapOf("body" to JsonPrimitive("")))
            else->HostKanbanTaskFixture.card()
        }
        credentials.managementServer().use {server->
            val f=HostKanbanTaskFixture(server,when(mode) {"lost","held","home-after","readback-fail"->mode;else->"normal"})
            f.method=action.method;f.tail=if(action==HostKanbanTaskAction.Comment) "/t_child/comments" else "/t_child"
            f.body=when(action) {
                HostKanbanTaskAction.Priority->buildJsonObject {put("priority",Int.MIN_VALUE)}
                HostKanbanTaskAction.Assignee->buildJsonObject {put("assignee",if(mode=="unassign") "" else "other")}
                HostKanbanTaskAction.Comment->buildJsonObject {put("body","Private reviewed comment");put("author","Declared author")}
                HostKanbanTaskAction.Delete->null
                else->buildJsonObject {if(mode!="body-only") put("title","Reviewed title");put("body","")}
            }
            if(mode=="unassign") f.beforeDetail.set(HostKanbanTaskFixture.detailJson(JsonObject(HostKanbanTaskFixture.card()+mapOf("assignee" to JsonPrimitive("worker")))))
            f.ack.set(when(action) {HostKanbanTaskAction.Comment->buildJsonObject {put("ok",true)};HostKanbanTaskAction.Delete->buildJsonObject {put("deleted",true);put("task_id","t_child")};else->buildJsonObject {put("task",changed)}})
            f.afterDetail.set(HostKanbanTaskFixture.detailJson(changed))
            f.start();val original=api.preferences.value;val local=runBlocking {app.database.dao().tasks().first()}
            try {
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(server.url("prefix/inference/").toString(),credentials.uniqueToken("task-edit-inference"),true);api.updatePreferences(api.preferences.value.copy(theme="STARHAVEN",chinese=mode=="bilingual",pinyin=mode=="bilingual"))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==(mode=="bilingual") && api.preferences.value.pinyin==(mode=="bilingual")}
                val scope=api.profileScope.value
                app.credentials.saveManagement(scope,buildJsonObject {put("url",f.delegate.base);put("profile","worker");put("debugHttp",true);put("tokens",f.tokens.json())})
                compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(10000) {vm.state.value.signedIn};compose.runOnUiThread {vm.refresh()};await();assertTrue(vm.state.value.loaded)
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,vm)}}}
                compose.onNodeWithTag("management-open").performClick();tap("management-list","host-kanban-open")
                tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-confirm").performClick();await()
                tap("host-kanban-list","kanban-board-read-garden");compose.onNodeWithTag("kanban-read-confirm").performClick();await()
                tap("host-kanban-list","kanban-task-read-t_child");compose.onNodeWithTag("kanban-read-confirm").performClick();await();assertNotNull(vm.state.value.kanban.detail);assertEquals(0,f.writes.get())
                if(mode=="missing-inventory") {val raw=vm.state.value;compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {HermesAppTheme(AppPreferences()) {HostKanbanDialog(vm,raw) {}}}};assertNull(vm.visibleHostKanban(raw).detail);compose.onNodeWithTag("task-edit-Metadata").assertDoesNotExist();assertNull(vm.prepareKanbanTaskEditor(action));return@use}
                tap("kanban-task-detail","task-edit-${action.name}")
                if(action==HostKanbanTaskAction.Metadata) {
                    compose.onNodeWithTag("task-form-review").assertIsNotEnabled()
                    if(mode!="body-only") {tap("task-form","task-set-title");input("task-field-title","");compose.onNodeWithTag("task-form-review").assertIsNotEnabled();input("task-field-title","  Reviewed title  ")}
                    tap("task-form","task-set-body");input("task-field-body","")
                } else when(action) {
                    HostKanbanTaskAction.Priority->{input("task-field-priority","2147483648");compose.onNodeWithTag("task-form-review").assertIsNotEnabled();input("task-field-priority",Int.MIN_VALUE.toString())}
                    HostKanbanTaskAction.Assignee->input("task-field-assignee",if(mode=="unassign") "" else "  OTHER  ")
                    HostKanbanTaskAction.Comment->{compose.onNodeWithTag("task-form-review").assertIsNotEnabled();input("task-field-body","  Private reviewed comment  ");input("task-field-author","");compose.onNodeWithTag("task-form-review").assertIsNotEnabled();input("task-field-author","Declared author")}
                    else->Unit
                }
                if(mode=="cancel-form") {compose.onNodeWithTag("task-form-cancel").performClick();assertEquals(0,f.writes.get());tap("kanban-task-detail","task-edit-Metadata");tap("task-form","task-set-title");compose.onNodeWithTag("task-field-title").assert(hasText("Private task"));compose.onNodeWithTag("task-form-cancel").performClick();return@use}
                if(mode=="bilingual") {
                    compose.onNodeWithTag("task-form").performScrollToNode(hasTestTag("task-input-body"))
                    val labelBounds=compose.onNodeWithTag("task-label-body").assertIsDisplayed().assert(hasText(Words.KanbanBody.zh,substring=true)).assert(hasText(Words.KanbanBody.pin,substring=true)).fetchSemanticsNode().boundsInRoot
                    val fieldBounds=compose.onNodeWithTag("task-field-body").assertIsDisplayed().assertContentDescriptionEquals(Words.KanbanBody.localized(api.preferences.value)).fetchSemanticsNode().boundsInRoot
                    assertTrue("The complete bilingual field label must stay above its editable area",labelBounds.bottom<=fieldBounds.top)
                    compose.onNodeWithTag("task-form-review").assertIsDisplayed();compose.onNodeWithTag("task-form-cancel").assertIsDisplayed();capture("bilingual-form")
                }
                compose.onNodeWithTag("task-form-review").assertIsEnabled().performClick();compose.onNodeWithTag("task-change-confirm").assertIsNotEnabled();assertEquals(0,f.writes.get())
                val v=vm.state.value.kanban
                val frozen=vm.prepareKanbanTaskChange(when(action) {
                    HostKanbanTaskAction.Priority->HostKanbanTaskChange.priority(v.inventory!!,v.board!!,v.detail!!,Int.MIN_VALUE)
                    HostKanbanTaskAction.Assignee->HostKanbanTaskChange.assignee(v.inventory!!,v.board!!,v.detail!!,if(mode=="unassign") "" else "other")
                    HostKanbanTaskAction.Comment->HostKanbanTaskChange.comment(v.inventory!!,v.board!!,v.detail!!,"Private reviewed comment","Declared author")
                    HostKanbanTaskAction.Delete->HostKanbanTaskChange.delete(v.inventory!!,v.board!!,v.detail!!)
                    else->HostKanbanTaskChange.metadata(v.inventory!!,v.board!!,v.detail!!,if(mode=="body-only") null else "Reviewed title","")
                })!!
                if(mode in setOf("pending","pending-project")) {
                    val other=v.inventory!!.owner.copy(apiScope="e".repeat(64),profile="alias",actor="password · private-other")
                    runBlocking {app.database.dao().insertManagementOperation(ManagementOperation(UUID.randomUUID().toString(),other.resourceScope,other.apiScope,other.url,other.profile,other.actor,if(mode=="pending-project") "ProjectUpdate" else "KanbanTaskPriority","private-target",buildJsonObject {put("access_home_sha256",f.home)}.toString(),"unknown","private-result",System.currentTimeMillis(),if(mode=="pending-project") f.home else ""))}
                    compose.waitUntil(10000) {vm.hostProjectPending(vm.state.value)};compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.onNodeWithText("Reviewed title",substring=true).assertDoesNotExist();compose.onNodeWithText("private-other",substring=true,useUnmergedTree=true).assertDoesNotExist();compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(0,f.writes.get());return@use
                }
                if(mode=="stale") {compose.runOnUiThread {vm.chooseProfile("other")};await();compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,f.writes.get());return@use}
                if(mode=="closed") {val raw=vm.state.value;compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {HermesAppTheme(AppPreferences()) {HostKanbanDialog(vm,raw) {}}}};assertNull(vm.visibleHostKanban(raw).detail);compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.onNodeWithText("Reviewed title",substring=true).assertDoesNotExist();compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(0,f.writes.get());return@use}
                tap("task-change-review","task-set-grant");compose.onNodeWithTag("task-set-grant").assertContentDescriptionEquals(Words.KanbanTaskGrant.localized(api.preferences.value))
                if(action==HostKanbanTaskAction.Delete) {compose.onNodeWithTag("task-change-confirm").assertIsNotEnabled();input("task-field-delete-id","wrong","task-change-review");compose.onNodeWithTag("task-change-confirm").assertIsNotEnabled();input("task-field-delete-id","t_child","task-change-review")}
                compose.onNodeWithTag("task-change-confirm").assertIsEnabled().assertHeightIsAtLeast(48.dp)
                if(mode=="cancel-review") {compose.onNodeWithTag("task-change-cancel").performClick();compose.onNodeWithTag("task-change-review").assertDoesNotExist();assertEquals(0,f.writes.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                if(mode=="bilingual") {compose.onNodeWithTag("task-change-review").performScrollToNode(hasText("Reviewed title"));capture("bilingual-review")}
                if(mode=="before-home") f.currentHome.set("/srv/remapped")
                if(mode=="before-task") f.beforeDetail.set(HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card(title="Externally changed")))
                compose.onNodeWithTag("task-change-confirm").performClick()
                if(mode=="held") {compose.waitUntil(10000) {f.entered.count==0L};compose.onNodeWithTag("kanban-cancel-work").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick();f.release.countDown();await();compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first().singleOrNull()?.state}=="unknown"};assertNull(vm.state.value.kanban.taskReport);assertEquals(1,f.writes.get());return@use}
                await()
                if(mode in setOf("before-home","before-task")) {assertEquals(0,f.writes.get());assertNull(vm.state.value.kanban.inventory);assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                assertEquals(1,f.writes.get());val receipt=runBlocking {app.database.dao().managementOperations(scope).first().single()};assertEquals(receipt,vm.state.value.kanban.taskReport!!.receipt);assertNull(vm.state.value.kanban.inventory)
                val durable=receipt.target+receipt.requestSummary+receipt.resultSummary;for(privateValue in listOf("Private task","Private body","Reviewed title","Private reviewed comment","Declared author","t_child","/shared/")) assertFalse(privateValue,durable.contains(privateValue))
                if(mode=="lost") {assertEquals("unknown",receipt.state);tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-confirm").performClick();await();assertEquals(1,f.writes.get());assertNull(vm.prepareKanbanTaskEditor(action));assertEquals(receipt,runBlocking {app.database.dao().managementOperation(receipt.key)});compose.onNodeWithTag("kanban-close").performClick();tap("management-list","management-review-${receipt.key}");compose.onNodeWithTag("management-review-confirm").assertIsNotEnabled();tap("management-own-review","management-instance-approve");compose.onNodeWithTag("management-review-confirm").performClick();await();compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperation(receipt.key)!!.state}=="reviewed"};assertEquals(receipt.copy(state="reviewed"),runBlocking {app.database.dao().managementOperation(receipt.key)});tap("management-list","management-observe-${receipt.key}");compose.onNodeWithTag("host-kanban-list").assertExists();assertEquals(1,f.writes.get());return@use}
                if(mode in setOf("home-after","readback-fail")) {assertEquals("acknowledged",receipt.state);assertEquals(Words.KanbanBoardReadbackPending,vm.state.value.error);return@use}
                assertNull(vm.state.value.error);assertEquals(if(action==HostKanbanTaskAction.Comment) "acknowledged" else "confirmed",receipt.state)
                val summary=Json.parseToJsonElement(receipt.resultSummary).jsonObject
                for(key in listOf("created_identity_verified","worker_stop_verified","file_erasure_verified","runtime_adoption_verified","path_mapping_verified","comment_identity_verified")) assertEquals(JsonPrimitive(false),summary[key])
                if(mode=="bilingual") {compose.onNodeWithTag("kanban-detail-close").performClick();compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("task-change-report"));capture("bilingual-result");compose.onNodeWithTag("kanban-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)}
            } finally {f.release.countDown();compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(theme=original.theme,chinese=original.chinese,pinyin=original.pinyin))};compose.waitUntil(10000) {api.preferences.value.theme==original.theme && api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin};assertEquals(local,runBlocking {app.database.dao().tasks().first()});f.assertClean()}
        }
    }
    @Test fun metadataSendsOnlySelectedFieldsAndExplicitlyClearsDescription()=exercise("metadata")
    @Test fun bodyOnlyEditPreservesOmittedTitle()=exercise("body-only")
    @Test fun priorityRejectsOverflowAndAcceptsSignedMinimum()=exercise("priority")
    @Test fun assignmentReviewsTheNormalizedProfileAndDispatchEffects()=exercise("assign")
    @Test fun blankAssignmentExplicitlyUnassigns()=exercise("unassign")
    @Test fun commentRequiresBodyAndDeclaredAuthorAndRetainsOnlyAcknowledgement()=exercise("comment")
    @Test fun deletionRequiresTypedIdAndGrantWithoutClaimingWorkerOrFileErasure()=exercise("delete")
    @Test fun cancelledFormDropsDraftAndPreservesOriginalText()=exercise("cancel-form")
    @Test fun cancelledReviewNeverClaimsOrSends()=exercise("cancel-review")
    @Test fun profileSwitchInvalidatesReviewedPrivateValuesAndFrozenCall()=exercise("stale")
    @Test fun closingCannotRestorePrivateReviewFromOldRawState()=exercise("closed")
    @Test fun missingCurrentInventoryCannotExposeTaskControlsFromOldState()=exercise("missing-inventory")
    @Test fun foreignTaskPendingReceiptInvalidatesReviewWithoutShowingItsValues()=exercise("pending")
    @Test fun sameHomeProjectPendingReceiptInvalidatesTaskReview()=exercise("pending-project")
    @Test fun changedHomeRefusesBeforeClaimOrWrite()=exercise("before-home")
    @Test fun changedTaskRefusesBeforeClaimOrWrite()=exercise("before-task")
    @Test fun lostReplyStaysUnknownAndFreshReadsNeverReplay()=exercise("lost")
    @Test fun cancellationDropsLateReplyAndRetainsUnknownBarrier()=exercise("held")
    @Test fun changedHomeAfterWriteRetainsAckWithoutReadback()=exercise("home-after")
    @Test fun failedReadbackRetainsAcknowledgement()=exercise("readback-fail")
    @Test fun bilingualPrivateFormReviewAndReceiptKeepFootersReachable()=exercise("bilingual")
}
