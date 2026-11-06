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

class HostKanbanOverrideFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private val form="task-override-form"
    private fun tap(root:String,tag:String) {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun input(tag:String,value:String) {compose.onNodeWithTag(form).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performTextReplacement(value);closeDialogKeyboard()}
    private fun await()=compose.waitUntil(15000) {!vm.state.value.busy}
    private fun card(model:String?="private-old-model",provider:String?="private-old-provider",effort:String?="high",status:String="running")=JsonObject(HostKanbanTaskFixture.card(status=status)+mapOf("model_override" to (model?.let(::JsonPrimitive) ?: JsonNull),"provider_override" to (provider?.let(::JsonPrimitive) ?: JsonNull),"reasoning_effort" to (effort?.let(::JsonPrimitive) ?: JsonNull)))
    private fun change(kind:String,v:HostKanbanState)=when(kind) {
        "model"->HostKanbanTaskChange.modelOverride(v.inventory!!,v.board!!,v.detail!!,"private-new-model","private-new-provider")
        "model-only"->HostKanbanTaskChange.modelOverride(v.inventory!!,v.board!!,v.detail!!,"private-new-model")
        "clear-model"->HostKanbanTaskChange.clearModelOverride(v.inventory!!,v.board!!,v.detail!!)
        "none","ultra"->HostKanbanTaskChange.reasoningOverride(v.inventory!!,v.board!!,v.detail!!,kind)
        else->HostKanbanTaskChange.clearReasoningOverride(v.inventory!!,v.board!!,v.detail!!)
    }
    private fun capture(name:String) {compose.waitForIdle();val ui=InstrumentationRegistry.getInstrumentation().uiAutomation;ui.waitForIdle(250,5000);val bitmap=checkNotNull(ui.takeScreenshot());val file=File(compose.activity.getExternalFilesDir(null),"qa/task-override-$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
    private fun exercise(kind:String="model",mode:String="normal") {
        val action=if(kind in setOf("none","ultra","clear-reasoning")) HostKanbanTaskAction.ReasoningOverride else HostKanbanTaskAction.ModelOverride
        credentials.managementServer().use {server->
            val f=HostKanbanTaskFixture(server,if(mode=="lost") "lost" else "normal")
            var before=card(status=if(mode=="archived") "archived" else "running")
            if(mode=="missing") before=JsonObject(before.filterKeys {it!="provider_override"})
            if(mode=="empty-current") before=card(null,"",null)
            f.beforeDetail.set(HostKanbanTaskFixture.detailJson(before));f.boardCards=listOf(before,HostKanbanTaskFixture.card("t_parent",status="done"))
            val after=when(kind) {"model"->card("private-new-model","private-new-provider");"model-only"->card("private-new-model",null);"clear-model"->card(null,null);"none","ultra"->card(effort=kind);else->card(effort=null)}
            f.body=when(kind) {"model"->buildJsonObject {put("model_override","private-new-model");put("provider_override","private-new-provider")};"model-only"->buildJsonObject {put("model_override","private-new-model")};"clear-model"->buildJsonObject {put("clear_model_override",true)};"none","ultra"->buildJsonObject {put("reasoning_effort",kind)};else->buildJsonObject {put("clear_reasoning_effort",true)}}
            f.afterDetail.set(HostKanbanTaskFixture.detailJson(after));f.ack.set(buildJsonObject {put("task",if(mode=="ack-missing") JsonObject(after.filterKeys {it!="provider_override"}) else after)})
            f.start();val original=api.preferences.value;val local=runBlocking {app.database.dao().tasks().first()};val bilingual=mode=="bilingual"
            try {
                compose.waitUntil(10000) {!api.connecting.value}
                compose.runOnUiThread {api.configure(server.url("prefix/inference/").toString(),credentials.uniqueToken("override-inference"),true);api.updatePreferences(api.preferences.value.copy(theme="STARHAVEN",chinese=bilingual,pinyin=bilingual))}
                compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==bilingual && api.preferences.value.pinyin==bilingual}
                val scope=api.profileScope.value
                app.credentials.saveManagement(scope,buildJsonObject {put("url",f.delegate.base);put("profile","worker");put("debugHttp",true);put("tokens",f.tokens.json())})
                compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(10000) {vm.state.value.signedIn};compose.runOnUiThread {vm.refresh()};await()
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,vm)}}}
                compose.onNodeWithTag("management-open").performClick();tap("management-list","host-kanban-open")
                for(tag in listOf("kanban-inventory-read","kanban-board-read-garden","kanban-task-read-t_child")) {tap("host-kanban-list",tag);compose.onNodeWithTag("kanban-read-confirm").performClick();await()}
                assertNotNull(vm.state.value.kanban.detail);assertEquals(0,f.writes.get())
                if(mode in setOf("missing","archived")) {
                    for(a in kanbanTaskOverrideActions) {compose.onNodeWithTag("kanban-task-detail").performScrollToNode(hasTestTag("task-edit-"+a.name));compose.onNodeWithTag("task-edit-"+a.name).assertIsNotEnabled();assertNull(vm.prepareKanbanTaskEditor(a))}
                    val reason=if(mode=="missing") Words.KanbanOverrideMissing else Words.KanbanOverrideArchived
                    compose.onNodeWithTag("kanban-task-detail").performScrollToNode(hasText(reason.en));compose.onNodeWithText(reason.en).assertIsDisplayed()
                    compose.onNodeWithTag("kanban-task-detail").performScrollToNode(hasTestTag("task-edit-Metadata"));compose.onNodeWithTag("task-edit-Metadata").assertIsEnabled();assertEquals(0,f.writes.get());return@use
                }
                tap("kanban-task-detail","task-edit-${action.name}")
                val editor=vm.prepareKanbanTaskEditor(action)!!;compose.onNodeWithTag("task-form-review").assertIsNotEnabled()
                if(bilingual) {
                    val viewport=compose.onNodeWithTag(form).fetchSemanticsNode().boundsInRoot
                    for(tag in listOf("task-override-set","task-override-set-label")) {
                        val node=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
                        assertTrue("The complete first choice must fit in the initial editor viewport",node.boundsInRoot.height+1f>=node.size.height && node.boundsInRoot.top>=viewport.top-1f && node.boundsInRoot.bottom<=viewport.bottom+1f)
                    }
                    compose.onNodeWithTag("task-form-review").assertIsDisplayed().assertHeightIsAtLeast(48.dp);compose.onNodeWithTag("task-form-cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    capture(kind+"-form")
                }
                if(mode=="empty-current") {
                    compose.onNodeWithTag(form).performScrollToNode(hasTestTag("task-current-provider_override"));compose.onNodeWithTag("task-current-provider_override").assert(hasAnyDescendant(hasText(Words.ProjectEmptyValue.en)))
                    compose.onNodeWithTag("task-current-model_override").assert(hasAnyDescendant(hasText(Words.KanbanOverrideUnset.en)))
                    compose.onNodeWithTag("task-form-cancel").performClick();assertEquals(0,f.writes.get());return@use
                }
                if(mode=="explicit-choice") {
                    tap(form,"task-override-clear");compose.onNodeWithTag("task-form-review").assertIsEnabled();tap(form,"task-override-set");compose.onNodeWithTag("task-form-review").assertIsNotEnabled()
                    tap(form,"task-override-effort-none");compose.onNodeWithTag("task-form-review").assertIsEnabled();compose.onNodeWithTag("task-form-cancel").performClick();assertEquals(0,f.writes.get());return@use
                }
                if(kind.startsWith("clear-")) tap(form,"task-override-clear") else {
                    tap(form,"task-override-set")
                    if(action==HostKanbanTaskAction.ModelOverride) {
                        input("task-field-model_override","");compose.onNodeWithTag("task-form-review").assertIsNotEnabled()
                        if(mode=="bounds") {
                            input("task-field-model_override","m".repeat(513));compose.onNodeWithTag("task-form-review").assertIsNotEnabled();input("task-field-model_override","m".repeat(512));input("task-field-provider_override","p".repeat(257));compose.onNodeWithTag("task-form-review").assertIsNotEnabled();input("task-field-provider_override","p".repeat(256));compose.onNodeWithTag("task-form-review").assertIsEnabled();compose.onNodeWithTag("task-form-cancel").performClick();assertEquals(0,f.writes.get());return@use
                        }
                        input("task-field-model_override","  private-new-model  ");input("task-field-provider_override",if(kind=="model-only") "   " else "  private-new-provider  ")
                        if(kind=="model-only") {compose.onNodeWithTag(form).performScrollToNode(hasText(Words.KanbanOverrideProviderClear.en));compose.onNodeWithText(Words.KanbanOverrideProviderClear.en).assertIsDisplayed()}
                    } else {compose.onNodeWithTag("task-form-review").assertIsNotEnabled();tap(form,"task-override-effort-"+kind)}
                }
                compose.onNodeWithTag("task-form-review").assertIsEnabled().assertHeightIsAtLeast(48.dp);assertEquals(0,f.writes.get())
                if(mode=="cancel-form") {
                    compose.onNodeWithTag("task-form-cancel").performClick();tap("kanban-task-detail","task-edit-${action.name}");compose.onNodeWithTag("task-form-review").assertIsNotEnabled();tap(form,"task-override-set");compose.onNodeWithTag(form).performScrollToNode(hasTestTag("task-field-model_override"));compose.onNodeWithTag("task-field-model_override").assert(hasText("private-old-model"));compose.onNodeWithTag("task-form-cancel").performClick();assertEquals(0,f.writes.get());return@use
                }
                val visible=vm.visibleHostKanban(vm.state.value);val expected=change(kind,visible)
                if(mode!="pending-editor") compose.onNodeWithTag("task-form-review").performClick()
                val frozen=vm.prepareKanbanTaskChange(expected)!!
                if(mode in setOf("pending-editor","pending-review")) {
                    val other=visible.inventory!!.owner.copy(apiScope="e".repeat(64),profile="alias",actor="password · private-foreign")
                    val pending=ManagementOperation(UUID.randomUUID().toString(),other.resourceScope,other.apiScope,other.url,other.profile,other.actor,"KanbanTaskModelOverride","private-target",buildJsonObject {put("access_home_sha256",f.home)}.toString(),"unknown","private-result",System.currentTimeMillis())
                    runBlocking {app.database.dao().insertManagementOperation(pending)};compose.waitUntil(10000) {vm.hostProjectPending(vm.state.value)}
                    compose.onNodeWithTag(form).assertDoesNotExist();compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.onNodeWithText("private-foreign",substring=true,useUnmergedTree=true).assertDoesNotExist()
                    if(mode=="pending-review") compose.runOnUiThread {vm.performKanbanTaskChange(frozen)}
                    runBlocking {app.database.dao().finishManagementOperation(pending.key,"reviewed","")};compose.waitUntil(10000) {!vm.hostProjectPending(vm.state.value)}
                    compose.onNodeWithTag(form).assertDoesNotExist();compose.onNodeWithTag("task-change-review").assertDoesNotExist();assertEquals(0,f.writes.get());return@use
                }
                compose.onNodeWithTag("task-change-confirm").assertIsNotEnabled();assertEquals(0,f.writes.get())
                if(mode=="stale") {compose.runOnUiThread {vm.chooseProfile("other")};await();compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(0,f.writes.get());return@use}
                if(mode=="closed") {val raw=vm.state.value;compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {HermesAppTheme(AppPreferences()) {HostKanbanDialog(vm,raw) {}}}};compose.onNodeWithTag("task-change-review").assertDoesNotExist();assertFalse(vm.kanbanTaskEditorCurrent(editor));compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(0,f.writes.get());return@use}
                tap("task-change-review","task-set-grant");compose.onNodeWithTag("task-set-grant").assertContentDescriptionEquals(Words.KanbanTaskGrant.localized(api.preferences.value));compose.onNodeWithTag("task-change-confirm").assertIsEnabled().assertHeightIsAtLeast(48.dp)
                if(mode=="cancel-review") {compose.onNodeWithTag("task-change-cancel").performClick();assertEquals(0,f.writes.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                if(bilingual) {compose.onNodeWithTag("task-change-review").performScrollToNode(hasTestTag("task-current-model_override"));capture(kind+"-review");compose.onNodeWithTag("task-change-confirm").assertIsDisplayed()}
                if(mode=="before-task") f.beforeDetail.set(HostKanbanTaskFixture.detailJson(card(effort="low")))
                compose.onNodeWithTag("task-change-confirm").performClick();await()
                if(mode=="before-task") {assertEquals(0,f.writes.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                assertEquals(1,f.writes.get());val receipt=runBlocking {app.database.dao().managementOperations(scope).first().single()}
                assertEquals(action.receiptAction,receipt.action);assertEquals(receipt,vm.state.value.kanban.taskReport!!.receipt)
                val durable=receipt.target+receipt.requestSummary+receipt.resultSummary
                for(value in listOf("private-old-model","private-old-provider","private-new-model","private-new-provider","Private task","t_child","/shared/")) assertFalse(value,durable.contains(value))
                if(mode=="lost") {
                    assertEquals("unknown",receipt.state);tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-confirm").performClick();await();assertNull(vm.prepareKanbanTaskEditor(action));assertEquals(1,f.writes.get());assertEquals(receipt,runBlocking {app.database.dao().managementOperation(receipt.key)});return@use
                }
                assertNull(vm.state.value.error);assertEquals(if(mode=="ack-missing") "acknowledged" else "confirmed",receipt.state)
                val summary=Json.parseToJsonElement(receipt.resultSummary).jsonObject
                for(key in listOf("runtime_adoption_verified","worker_stop_verified","file_erasure_verified","path_mapping_verified")) assertEquals(JsonPrimitive(false),summary[key])
                compose.onNodeWithTag("kanban-detail-close").performClick();compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("task-change-report"))
                compose.onNodeWithTag("task-change-report").assert(hasAnyDescendant(hasText(Words.KanbanOverrideNextDispatch.localized(api.preferences.value))))
                if(bilingual) {capture(kind+"-result");compose.onNodeWithTag("kanban-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)}
            } finally {
                f.release.countDown();compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(theme=original.theme,chinese=original.chinese,pinyin=original.pinyin))}
                compose.waitUntil(10000) {api.preferences.value.theme==original.theme && api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin}
                assertEquals(local,runBlocking {app.database.dao().tasks().first()});f.assertClean()
            }
        }
    }
    @Test fun modelAndProviderUseOneReviewedTrimmedPatch()=exercise()
    @Test fun blankProviderIsExplicitlyDisclosedAndOmittedFromThePatch()=exercise("model-only")
    @Test fun clearModelUsesOnlyItsClearFlagAndPreservesReasoning()=exercise("clear-model")
    @Test fun noneReasoningIsLiteralAndPreservesModelProvider()=exercise("none")
    @Test fun ultraReasoningIsReachableAndTransmittedExactly()=exercise("ultra")
    @Test fun clearReasoningUsesItsOwnFlagAndPreservesModelProvider()=exercise("clear-reasoning")
    @Test fun missingMetadataDisablesBothOverridesWithReasonWithoutBlockingTextEdit()=exercise(mode="missing")
    @Test fun archivedTaskDisablesBothOverridesWithReason()=exercise(mode="archived")
    @Test fun blankAndOversizedModelProviderCannotReachReview()=exercise(mode="bounds")
    @Test fun explicitSetWithoutAnEffortDoesNotBecomeClearOrNone()=exercise("none","explicit-choice")
    @Test fun knownEmptyAndUnsetValuesHaveDifferentCurrentLabels()=exercise(mode="empty-current")
    @Test fun cancellingThePrivateFormDropsItsValues()=exercise(mode="cancel-form")
    @Test fun cancellingGrantedReviewDoesNotClaimOrWrite()=exercise(mode="cancel-review")
    @Test fun sharedPendingWorkDiscardsEditorAndDoesNotRestoreItWhenResolved()=exercise(mode="pending-editor")
    @Test fun sharedPendingWorkDiscardsReviewAndNeverRestoresItsGrant()=exercise(mode="pending-review")
    @Test fun profileSwitchInvalidatesFrozenOverrideApproval()=exercise(mode="stale")
    @Test fun closingRejectsOldRawStateAndFrozenOverrideCall()=exercise(mode="closed")
    @Test fun changedIndependentReasoningRefusesBeforeClaimOrWrite()=exercise(mode="before-task")
    @Test fun lostOverrideReplyRemainsUnknownAfterFreshReadsWithoutReplay()=exercise(mode="lost")
    @Test fun missingNullAckStaysAcknowledgedInsteadOfConfirmed()=exercise("clear-model","ack-missing")
    @Test fun bilingualModelReviewAndReceiptKeepFooterControlsReachable()=exercise(mode="bilingual")
    @Test fun bilingualReasoningClearRemainsIndependentAndReachable()=exercise("clear-reasoning","bilingual")
}
