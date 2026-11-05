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

class HostKanbanDependencyFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun tap(root:String,tag:String) {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun await()=compose.waitUntil(15000) {!vm.state.value.busy}
    private fun capture(name:String) {compose.waitForIdle();val ui=InstrumentationRegistry.getInstrumentation().uiAutomation;ui.waitForIdle(250,5000);val image=checkNotNull(ui.takeScreenshot());val file=File(compose.activity.getExternalFilesDir(null),"qa/dependency-$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()}
    private fun pending(scope:String,home:String,project:Boolean=false):ManagementOperation {
        val owner=vm.state.value.kanban.inventory!!.owner.copy(apiScope="e".repeat(64),profile="alias",actor="password · private-other")
        val row=ManagementOperation(UUID.randomUUID().toString(),owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,if(project) "ProjectUpdate" else "KanbanTaskLink","private-target",buildJsonObject {put("access_home_sha256",home)}.toString(),"unknown","private-result",System.currentTimeMillis(),if(project) home else "")
        assertNotEquals(scope,owner.apiScope);runBlocking {app.database.dao().insertManagementOperation(row)};compose.waitUntil(10000) {vm.hostProjectPending(vm.state.value)};return row
    }
    private fun exercise(mode:String) {
        val remove=mode in setOf("remove","remove-hidden","false")
        credentials.managementServer().use {server->
            val f=HostKanbanTaskFixture(server,when(mode) {"held-pair","pending-pair"->"held-pair";"held","lost","readback-fail","http409"->mode;else->"normal"})
            val card=HostKanbanTaskFixture.card(status=if(mode=="running") "running" else "ready")
            f.beforeDetail.set(HostKanbanTaskFixture.detailJson(card,if(remove || mode=="already-linked") listOf("t_parent") else emptyList()))
            f.parentDetail.set(HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card("t_parent","Private parent",status="done")))
            if(mode in setOf("remove-hidden","no-candidates")) f.boardCards=listOf(card)
            f.method=if(remove) "DELETE" else "POST";f.tail="";f.link=true
            if(remove) {f.body=null;f.query=mapOf("parent_id" to "t_parent","child_id" to "t_child")} else f.body=buildJsonObject {put("parent_id","t_parent");put("child_id","t_child")}
            f.ack.set(buildJsonObject {put("ok",mode!="false");if(!remove) put("gated",false)})
            f.afterDetail.set(HostKanbanTaskFixture.detailJson(card,if(remove) emptyList() else listOf("t_parent")))
            f.start();val original=api.preferences.value;val local=runBlocking {app.database.dao().tasks().first()}
            try {
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(server.url("prefix/inference/").toString(),credentials.uniqueToken("dependency-inference"),true);api.updatePreferences(api.preferences.value.copy(theme="STARHAVEN",chinese=mode=="bilingual",pinyin=mode=="bilingual"))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==(mode=="bilingual") && api.preferences.value.pinyin==(mode=="bilingual")}
                val scope=api.profileScope.value
                app.credentials.saveManagement(scope,buildJsonObject {put("url",f.delegate.base);put("profile","worker");put("debugHttp",true);put("tokens",f.tokens.json())})
                compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(10000) {vm.state.value.signedIn};compose.runOnUiThread {vm.refresh()};await();assertTrue(vm.state.value.loaded)
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,vm)}}}
                compose.onNodeWithTag("management-open").performClick();tap("management-list","host-kanban-open")
                tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-confirm").performClick();await()
                tap("host-kanban-list","kanban-board-read-garden");compose.onNodeWithTag("kanban-read-confirm").performClick();await()
                tap("host-kanban-list","kanban-task-read-t_child");compose.onNodeWithTag("kanban-read-confirm").performClick();await();assertNotNull(vm.state.value.kanban.detail)
                val beforeReads=f.reads.get()
                if(mode in setOf("running","no-candidates","already-linked")) {
                    compose.onNodeWithTag("kanban-task-detail").performScrollToNode(hasTestTag("task-dependency-add"));compose.onNodeWithTag("task-dependency-add").assertIsNotEnabled()
                    assertEquals(if(mode=="running") Words.KanbanDependencyRunning else Words.KanbanDependencyNoCandidates,vm.kanbanDependencyBlock(vm.state.value,false));assertNull(vm.prepareKanbanDependencyPicker(false));assertEquals(0,f.writes.get());return@use
                }
                tap("kanban-task-detail",if(remove) "task-dependency-remove" else "task-dependency-add")
                val picker=vm.prepareKanbanDependencyPicker(remove)!!
                assertNull(vm.prepareKanbanDependencyRead(picker,"t_child"));assertNull(vm.prepareKanbanDependencyRead(picker,"t_unlisted"))
                assertEquals(beforeReads,f.reads.get());assertEquals(0,f.writes.get())
                compose.onNodeWithTag("dependency-select-t_child").assertDoesNotExist()
                if(mode=="cancel-picker") {compose.onNodeWithTag("dependency-picker-cancel").performClick();assertEquals(beforeReads,f.reads.get());assertEquals(0,f.writes.get());return@use}
                if(mode=="bilingual") {
                    compose.onNodeWithTag("dependency-picker").performScrollToNode(hasTestTag("task-input-dependency-search"))
                    val labelBounds=compose.onNodeWithTag("task-label-dependency-search").assertIsDisplayed().assert(hasText(Words.Search.zh,substring=true)).assert(hasText(Words.Search.pin,substring=true)).fetchSemanticsNode().boundsInRoot
                    val fieldBounds=compose.onNodeWithTag("task-field-dependency-search").assertIsDisplayed().assertContentDescriptionEquals(Words.Search.localized(api.preferences.value)).fetchSemanticsNode().boundsInRoot
                    assertTrue(labelBounds.bottom<=fieldBounds.top);compose.onNodeWithTag("dependency-picker-cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp);capture("bilingual-picker")
                }
                tap("dependency-picker","dependency-select-t_parent");compose.onNodeWithTag("dependency-read-review").assertExists();assertEquals(beforeReads,f.reads.get());assertEquals(0,f.writes.get())
                val read=vm.prepareKanbanDependencyRead(picker,"t_parent")!!
                if(mode=="cancel-read") {compose.onNodeWithTag("dependency-read-cancel").performClick();assertEquals(beforeReads,f.reads.get());assertNull(vm.state.value.kanban.dependencyReview);assertEquals(0,f.writes.get());return@use}
                if(mode=="bilingual") {compose.onNodeWithTag("dependency-read-review").performScrollToNode(hasText("t_parent"));compose.onNodeWithTag("dependency-read-confirm").assertIsDisplayed().assertHeightIsAtLeast(48.dp);compose.onNodeWithTag("dependency-read-cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp);capture("bilingual-read")}
                if(mode=="changed-edge") f.beforeDetail.set(HostKanbanTaskFixture.detailJson(card,listOf("t_parent")))
                compose.onNodeWithTag("dependency-read-confirm").performClick()
                if(mode in setOf("held-pair","pending-pair")) {
                    compose.waitUntil(10000) {f.entered.count==0L}
                    if(mode=="held-pair") tap("host-kanban-list","kanban-cancel-work") else pending(scope,f.home)
                    f.release.countDown();await();assertNull(vm.state.value.kanban.dependencyReview);assertEquals(0,f.writes.get());assertFalse(vm.kanbanDependencyReadCurrent(read));return@use
                }
                await();assertEquals(beforeReads+2,f.reads.get());assertEquals(0,f.writes.get())
                if(mode=="changed-edge") {assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertNull(vm.state.value.kanban.dependencyReview);return@use}
                val frozen=vm.state.value.kanban.dependencyReview!!
                assertSame(frozen,vm.prepareKanbanTaskChange(frozen.request));assertSame(frozen,vm.visibleHostKanban(vm.state.value).dependencyReview)
                assertEquals("t_child",frozen.request.subject!!.task.id);assertEquals("t_parent",frozen.request.related!!.task.id)
                compose.onNodeWithTag("task-change-confirm").assertIsNotEnabled()
                for(key in listOf("parent_id","child_id")) {compose.onNodeWithTag("task-change-review").performScrollToNode(hasTestTag("task-reviewed-$key"));compose.onNodeWithTag("task-reviewed-$key").assertIsDisplayed()}
                if(mode in setOf("pending","pending-project","pending-cleared")) {
                    val row=pending(scope,f.home,mode=="pending-project");compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.onNodeWithText("Private parent",substring=true).assertDoesNotExist();compose.onNodeWithText("private-other",substring=true,useUnmergedTree=true).assertDoesNotExist()
                    compose.waitUntil(10000) {vm.state.value.kanban.dependencyReview==null}
                    if(mode=="pending-cleared") {runBlocking {app.database.dao().reviewManagementOperation(row.key,row.resourceScope,row.url,row.projectHome)};compose.waitUntil(10000) {!vm.hostProjectPending(vm.state.value)};compose.onNodeWithTag("task-change-review").assertDoesNotExist()}
                    compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(0,f.writes.get());return@use
                }
                if(mode=="scope") {compose.runOnUiThread {vm.chooseProfile("other")};await();compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,f.writes.get());return@use}
                if(mode=="closed") {val raw=vm.state.value;compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {HermesAppTheme(AppPreferences()) {HostKanbanDialog(vm,raw) {}}}};assertNull(vm.visibleHostKanban(raw).dependencyReview);compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.onNodeWithText("Private parent",substring=true).assertDoesNotExist();compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(0,f.writes.get());return@use}
                tap("task-change-review","task-set-grant");compose.onNodeWithTag("task-change-confirm").assertIsEnabled().assertHeightIsAtLeast(48.dp)
                val motion=!api.preferences.value.reducedMotion;compose.runOnUiThread {api.updatePreferences(api.preferences.value.copy(reducedMotion=motion))};compose.waitUntil(10000) {api.preferences.value.reducedMotion==motion};compose.waitForIdle();assertSame(frozen,vm.state.value.kanban.dependencyReview);compose.onNodeWithTag("task-change-confirm").assertIsEnabled()
                if(mode=="cancel-review") {compose.onNodeWithTag("task-change-cancel").performClick();assertNull(vm.state.value.kanban.dependencyReview);compose.runOnUiThread {vm.performKanbanTaskChange(frozen)};assertEquals(0,f.writes.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                if(mode=="bilingual") {compose.onNodeWithTag("task-change-review").performScrollToNode(hasTestTag("task-reviewed-parent_id"));compose.onNodeWithTag("task-change-confirm").assertIsDisplayed();compose.onNodeWithTag("task-change-cancel").assertIsDisplayed();capture("bilingual-review")}
                if(mode=="before-home") f.currentHome.set("/srv/remapped")
                if(mode=="changed-child") f.beforeDetail.set(HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card(title="Changed child")))
                if(mode=="changed-parent") f.parentDetail.set(HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card("t_parent",title="Changed parent",status="done")))
                if(mode=="changed-board") {val c=f.catalogBefore;f.delegate.beforeOverride.set(JsonObject(c+mapOf("boards" to JsonArray(c["boards"]!!.jsonArray.map {v->val b=v.jsonObject;if(b["slug"]==JsonPrimitive("garden")) JsonObject(b+mapOf("db_path" to JsonPrimitive("/other/kanban.db"))) else b}))))}
                compose.onNodeWithTag("task-change-confirm").performClick()
                if(mode=="held") {compose.waitUntil(10000) {f.entered.count==0L};tap("host-kanban-list","kanban-cancel-work");f.release.countDown();await();compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first().singleOrNull()?.state}=="unknown"};assertEquals(1,f.writes.get());assertNull(vm.state.value.kanban.taskReport);return@use}
                await()
                if(mode in setOf("before-home","changed-child","changed-parent","changed-board")) {assertEquals(0,f.writes.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                assertEquals(1,f.writes.get());val row=runBlocking {app.database.dao().managementOperations(scope).first().single()};assertEquals(row,vm.state.value.kanban.taskReport!!.receipt)
                for(value in listOf("Private task","Private body","Private parent","t_child","t_parent","/shared/")) assertFalse(value,(row.target+row.requestSummary+row.resultSummary).contains(value))
                if(mode in setOf("lost","http409")) {assertEquals("unknown",row.state);tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-confirm").performClick();await();assertNull(vm.prepareKanbanDependencyPicker(remove));assertEquals(1,f.writes.get());assertEquals(row,runBlocking {app.database.dao().managementOperation(row.key)});return@use}
                if(mode=="readback-fail") {assertEquals("acknowledged",row.state);assertEquals(Words.KanbanBoardReadbackPending,vm.state.value.error);return@use}
                assertNull(vm.state.value.error);assertEquals(if(mode=="false") "acknowledged" else "confirmed",row.state)
                val summary=Json.parseToJsonElement(row.resultSummary).jsonObject;assertEquals(JsonPrimitive(mode!="false"),summary["effect_reported"]);assertEquals(JsonPrimitive(false),summary["worker_stop_verified"])
                if(mode=="bilingual") {compose.onNodeWithTag("kanban-detail-close").performClick();compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("task-change-report"));compose.onNodeWithTag("kanban-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp);capture("bilingual-result")}
            } finally {f.release.countDown();compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(theme=original.theme,chinese=original.chinese,pinyin=original.pinyin,reducedMotion=original.reducedMotion))};compose.waitUntil(10000) {api.preferences.value.theme==original.theme && api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin && api.preferences.value.reducedMotion==original.reducedMotion};assertEquals(local,runBlocking {app.database.dao().tasks().first()});f.assertClean()}
        }
    }
    @Test fun addUsesSeparatePairReadAndExactChildParentApproval()=exercise("add")
    @Test fun removeUsesExactQueryAndReportsObservedAbsence()=exercise("remove")
    @Test fun removeCanSelectCurrentParentOutsideBoardColumns()=exercise("remove-hidden")
    @Test fun unlinkFalseIsAcknowledgedWithoutEffectConfirmation()=exercise("false")
    @Test fun cancelledPickerDoesNotReadOrWrite()=exercise("cancel-picker")
    @Test fun cancelledPairReadReviewDoesNotReadOrWrite()=exercise("cancel-read")
    @Test fun cancelledMutationReviewCannotReuseItsFrozenCall()=exercise("cancel-review")
    @Test fun runningChildDisablesAddingWithReason()=exercise("running")
    @Test fun missingCandidatesDisablesAddingWithReason()=exercise("no-candidates")
    @Test fun alreadyLinkedAndSelfAreNotOfferedForAddition()=exercise("already-linked")
    @Test fun edgeChangedDuringApprovedPairReadRefusesMutationReview()=exercise("changed-edge")
    @Test fun cancellingHeldPairReadDropsLatePrivateDetailsWithoutClaim()=exercise("held-pair")
    @Test fun pendingDuringPairReadDiscardsMutationReview()=exercise("pending-pair")
    @Test fun changedParentPreflightNeverClaimsOrSends()=exercise("changed-parent")
    @Test fun changedChildPreflightNeverClaimsOrSends()=exercise("changed-child")
    @Test fun changedBoardMappingPreflightNeverClaimsOrSends()=exercise("changed-board")
    @Test fun changedHomePreflightNeverClaimsOrSends()=exercise("before-home")
    @Test fun foreignAliasPendingDiscardsPrivateReview()=exercise("pending")
    @Test fun sharedHomeProjectPendingDiscardsPrivateReview()=exercise("pending-project")
    @Test fun clearingPendingDoesNotResurrectOldApproval()=exercise("pending-cleared")
    @Test fun profileSwitchInvalidatesPrivateReviewAndFrozenCall()=exercise("scope")
    @Test fun oldRawStateCannotResurrectClosedPairReview()=exercise("closed")
    @Test fun lostReplyStaysUnknownAndFreshReadsNeverReplay()=exercise("lost")
    @Test fun cancelledWriteKeepsUnknownBarrierAndDropsLateReply()=exercise("held")
    @Test fun failedReadbackKeepsDurableAcknowledgement()=exercise("readback-fail")
    @Test fun serverCycleRaceErrorAfterAttemptStaysUnknown()=exercise("http409")
    @Test fun bilingualPickerPairReadMutationReviewAndResultKeepFootersReachable()=exercise("bilingual")
}
