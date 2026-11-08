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

class HostKanbanTaskCreateFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun tap(root:String,tag:String) {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun input(key:String,value:String) {compose.onNodeWithTag("task-create-form").performScrollToNode(hasTestTag("task-field-$key"));compose.onNodeWithTag("task-field-$key").performTextReplacement(value);closeDialogKeyboard()}
    private fun supply(key:String,value:String) {tap("task-create-form","task-set-$key");input(key,value)}
    private fun await()=compose.waitUntil(15000) {!vm.state.value.busy}
    private fun readInventory() {tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-confirm").performClick();await()}
    private fun readBoard() {tap("host-kanban-list","kanban-board-read-garden");compose.onNodeWithTag("kanban-read-confirm").performClick();await()}
    private fun capture(name:String) {compose.waitForIdle();val ui=InstrumentationRegistry.getInstrumentation().uiAutomation;ui.waitForIdle(250,5000);val bitmap=checkNotNull(ui.takeScreenshot());val file=File(compose.activity.getExternalFilesDir(null),"qa/task-create-$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
    private fun exercise(mode:String) {
        val full=mode=="full"
        val linked=mode in setOf("project-inherit","project-unread","scratch-unread")
        val canonical=mode in setOf("project-inherit","project-explicit","before-project")
        val explicit=mode in setOf("project-explicit","before-project")
        val parent=full || mode=="before-parent"
        val empty=mode=="empty"
        val kind=when {full->"dir";mode in setOf("project-explicit","before-project","scratch-unread")->"scratch";else->null}
        val projectId=when {full || mode=="project-unread"->"";explicit->"p_bind";else->null}
        val expected=buildJsonObject {
            put("title","Created task");if(full || empty || mode=="bilingual") put("body",if(empty) "" else "Private creation body");if(full) {put("assignee","other");put("tenant","tenant-a")};put("priority",if(full) -7 else 0)
            kind?.let {put("workspace_kind",it)};if(full) put("workspace_path","~/private/workspace");put("parents",JsonArray(if(parent) listOf(JsonPrimitive("t_parent")) else emptyList()));put("triage",full || mode=="triage-ignored");put("goal_mode",full)
            if(full) {put("max_runtime_seconds",90);put("skills",buildJsonArray {add("skill-a");add("skill-b")});put("goal_max_turns",12);put("model_override","model-a");put("provider_override","provider-a");put("reasoning_effort","none")};if(empty) put("skills",JsonArray(emptyList()));projectId?.let {put("project_id",it)}
        }
        val project=if(explicit || mode=="project-inherit") "p_bind" else null
        val created=JsonObject(HostKanbanTaskFixture.card("t_new",if(mode=="normalized") "Host normalized title" else "Created task",if(full) "triage" else "ready")+buildJsonObject {
            put("body",expected["body"] ?: JsonNull);put("priority",expected["priority"]!!);put("assignee",expected["assignee"] ?: JsonNull);put("tenant",expected["tenant"] ?: JsonNull);put("workspace_kind",if(project!=null) "worktree" else kind ?: "scratch");project?.let {put("project_id",it)}
            for(key in listOf("workspace_path","max_runtime_seconds","skills","goal_mode","goal_max_turns","model_override","provider_override","reasoning_effort")) expected[key]?.let {put(key,it)}
        })
        credentials.managementServer().use {server->
            val f=HostKanbanTaskFixture(server,if(mode in setOf("lost","held","home-after")) mode else "normal")
            f.method="POST";f.tail="";f.body=expected;f.ack.set(buildJsonObject {put("task",created);if(full) put("warning","Private host hint")});f.afterDetail.set(HostKanbanTaskFixture.detailJson(created,if(parent) listOf("t_parent") else emptyList()))
            if(linked) {val catalog=HostKanbanBoardFixture.catalog(listOf(HostKanbanBoardFixture.metadata("default"),JsonObject(HostKanbanBoardFixture.metadata()+mapOf("project_id" to JsonPrimitive("p_bind"))),HostKanbanBoardFixture.metadata("archive",true)));f.delegate.beforeOverride.set(catalog);f.catalogAfter.set(catalog)}
            f.start();val original=api.preferences.value;val local=runBlocking {app.database.dao().tasks().first()}
            try {
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(server.url("prefix/inference/").toString(),credentials.uniqueToken("task-create-inference"),true);api.updatePreferences(api.preferences.value.copy(theme="STARHAVEN",chinese=mode=="bilingual",pinyin=mode=="bilingual"))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==(mode=="bilingual") && api.preferences.value.pinyin==(mode=="bilingual")}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",f.delegate.base);put("profile","worker");put("debugHttp",true);put("tokens",f.tokens.json())})
                compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(10000) {vm.state.value.signedIn};compose.runOnUiThread {vm.refresh()};await()
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,vm)}}}
                compose.onNodeWithTag("management-open").performClick();tap("management-list","host-kanban-open");compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("task-create-open"));compose.onNodeWithTag("task-create-open").assertIsNotEnabled();readInventory()
                if(mode=="missing-board") {compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("task-create-open"));compose.onNodeWithTag("task-create-open").assertIsNotEnabled();assertNull(vm.prepareKanbanTaskCreate());assertEquals(0,f.writes.get());return@use}
                readBoard();if(canonical) {tap("host-kanban-list","board-read-projects");compose.onNodeWithTag("board-projects-cancel").performClick();assertEquals(0,f.delegate.projectReads.get());tap("host-kanban-list","board-read-projects");compose.onNodeWithTag("board-projects-confirm").performClick();await();assertEquals(1,f.delegate.projectReads.get())}
                tap("host-kanban-list","task-create-open");compose.onNodeWithTag("task-create-review").assertIsNotEnabled();input("title","  Created task  ")
                if(full || empty || mode=="bilingual") supply("body",if(empty) "" else "Private creation body")
                if(full) input("priority","-7");if(full || mode=="triage-ignored") tap("task-create-form","task-set-triage")
                if(parent) {tap("task-create-form","task-create-parents");input("parent-search","t_parent");tap("task-create-form","task-create-parent-t_parent");tap("task-create-form","task-create-parents")}
                if(full || empty || canonical || mode in setOf("project-unread","scratch-unread","invalid")) {
                    tap("task-create-form","task-create-advanced")
                    if(full) {supply("assignee","  OTHER  ");supply("tenant","tenant-a")}
                    kind?.let {tap("task-create-form","task-create-workspace-$it")}
                    if(full) supply("path","~/private/workspace")
                    if(mode=="project-unread") {compose.onNodeWithTag("task-create-review").assertIsNotEnabled();assertNull(vm.state.value.kanban.projects);assertEquals(0,f.delegate.projectReads.get())}
                    projectId?.let {tap("task-create-form",if(it.isEmpty()) "task-create-project-none" else "task-create-project-$it")}
                    if(full) {supply("runtime","90");supply("skills","skill-a\nskill-b\nskill-a");tap("task-create-form","task-set-goal");supply("turns","12");supply("model","model-a");supply("provider","provider-a");tap("task-create-form","task-create-effort-none")}
                    if(empty) supply("skills","")
                    if(mode=="invalid") {
                        supply("runtime","0");compose.onNodeWithTag("task-create-review").assertIsNotEnabled();input("runtime","-1");compose.onNodeWithTag("task-create-review").assertIsNotEnabled();tap("task-create-form","task-set-runtime")
                        supply("turns","2147483648");compose.onNodeWithTag("task-create-review").assertIsNotEnabled();tap("task-create-form","task-set-turns")
                        supply("provider","provider-a");compose.onNodeWithTag("task-create-review").assertIsNotEnabled();supply("model","model-a");compose.onNodeWithTag("task-create-review").assertIsEnabled();tap("task-create-form","task-set-model");tap("task-create-form","task-set-provider")
                        input("priority","2147483648");compose.onNodeWithTag("task-create-review").assertIsNotEnabled();input("priority","0")
                    }
                }
                if(mode=="cancel-form") {compose.onNodeWithTag("task-create-cancel").performClick();tap("host-kanban-list","task-create-open");compose.onNodeWithTag("task-create-form").performScrollToNode(hasTestTag("task-field-title"));compose.onNodeWithTag("task-field-title").assert(hasText(""));assertEquals(0,f.writes.get());return@use}
                if(mode=="bilingual") {compose.onNodeWithTag("task-create-form").performScrollToNode(hasTestTag("task-input-body"));val labelBounds=compose.onNodeWithTag("task-label-body").assertIsDisplayed().fetchSemanticsNode().boundsInRoot;val fieldBounds=compose.onNodeWithTag("task-field-body").assertIsDisplayed().assertContentDescriptionEquals(Words.KanbanBody.localized(api.preferences.value)).fetchSemanticsNode().boundsInRoot;assertTrue(labelBounds.bottom<=fieldBounds.top);compose.onNodeWithTag("task-create-review").assertIsDisplayed();compose.onNodeWithTag("task-create-cancel").assertIsDisplayed();capture("bilingual-form")}
                compose.onNodeWithTag("task-create-review").assertIsEnabled().performClick();compose.onNodeWithTag("task-change-confirm").assertIsNotEnabled();compose.onNodeWithTag("task-reviewed-idempotency_key").assertDoesNotExist();assertEquals(0,f.writes.get())
                val v=vm.state.value.kanban;val frozen=if(mode=="stale") vm.prepareKanbanTaskChange(HostKanbanTaskChange.create(v.inventory!!,v.board!!,HostKanbanTaskDraft("Created task"),v.projects)) else null
                if(mode=="stale") {assertNotNull(frozen);compose.runOnUiThread {vm.chooseProfile("other")};await();compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.runOnUiThread {vm.performKanbanTaskChange(frozen!!)};assertEquals(0,f.writes.get());return@use}
                if(mode=="closed") {val raw=vm.state.value;compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {HermesAppTheme(AppPreferences()) {HostKanbanDialog(vm,raw) {}}}};compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.onNodeWithText("Created task",substring=true).assertDoesNotExist();assertNull(vm.visibleHostKanban(raw).board);assertEquals(0,f.writes.get());return@use}
                if(mode=="pending") {val owner=v.inventory!!.owner.copy(apiScope="e".repeat(64),profile="other",actor="password · private-other");runBlocking {app.database.dao().insertManagementOperation(ManagementOperation(UUID.randomUUID().toString(),owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,"KanbanTaskCreate","private-target","private-request","unknown","private-result",System.currentTimeMillis()))};compose.waitUntil(10000) {vm.hostProjectPending(vm.state.value)};compose.onNodeWithTag("task-change-review").assertDoesNotExist();compose.onNodeWithText("Created task",substring=true).assertDoesNotExist();compose.onNodeWithText("private-other",substring=true,useUnmergedTree=true).assertDoesNotExist();assertEquals(0,f.writes.get());return@use}
                if(mode=="cancel-review") {compose.onNodeWithTag("task-change-cancel").performClick();assertEquals(0,f.writes.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                tap("task-change-review","task-set-grant");compose.onNodeWithTag("task-change-confirm").assertIsEnabled().assertHeightIsAtLeast(48.dp)
                if(mode=="bilingual") {compose.onNodeWithTag("task-change-review").performScrollToNode(hasTestTag("task-reviewed-title"));capture("bilingual-review")}
                if(mode=="before-parent") f.parentDetail.set(HostKanbanTaskFixture.detailJson(HostKanbanTaskFixture.card("t_parent",title="Changed parent",status="done")))
                if(mode=="before-project") f.delegate.projects.set(HostKanbanBoardFixture.obj("""{"projects":[{"id":"p_bind","slug":"binding","name":"Changed project","primary_path":"/different/path","created_at":1,"folders":[]}],"active_id":null}"""))
                compose.onNodeWithTag("task-change-confirm").performClick()
                if(mode=="held") {compose.waitUntil(10000) {f.entered.count==0L};compose.onNodeWithTag("kanban-cancel-work").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick();f.release.countDown();await();compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first().singleOrNull()?.state}=="unknown"};assertEquals(1,f.writes.get());assertNull(vm.state.value.kanban.taskReport);return@use}
                await()
                if(mode in setOf("before-parent","before-project")) {assertEquals(0,f.writes.get());assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                assertEquals(1,f.writes.get());val receipt=runBlocking {app.database.dao().managementOperations(scope).first().single()};assertEquals(receipt,vm.state.value.kanban.taskReport!!.receipt)
                val durable=receipt.target+receipt.requestSummary+receipt.resultSummary;for(value in listOf("Created task","Private creation body","tenant-a","~/private/workspace","skill-a","model-a","provider-a","t_new","t_parent","p_bind","Private host hint","idempotency_key")) assertFalse(value,durable.contains(value))
                if(mode=="lost") {assertEquals("unknown",receipt.state);readInventory();readBoard();assertNull(vm.prepareKanbanTaskCreate());assertEquals(receipt,runBlocking {app.database.dao().managementOperation(receipt.key)});assertEquals(1,f.writes.get());return@use}
                if(mode=="home-after") {assertEquals("acknowledged",receipt.state);assertEquals(Words.KanbanBoardReadbackPending,vm.state.value.error);return@use}
                assertNull(vm.state.value.error);assertEquals(if(mode in setOf("normalized","triage-ignored")) "acknowledged" else "confirmed",receipt.state);val summary=Json.parseToJsonElement(receipt.resultSummary).jsonObject
                if(mode=="triage-ignored") assertEquals(JsonPrimitive(false),summary["effect_reported"])
                for(key in listOf("created_identity_verified","worker_stop_verified","file_erasure_verified","runtime_adoption_verified","path_mapping_verified")) assertEquals(JsonPrimitive(false),summary[key])
                if(project!=null) {assertEquals(2,f.delegate.projectReads.get());assertEquals(project,vm.state.value.kanban.detail!!.task.fields["project_id"]);assertEquals("worktree",vm.state.value.kanban.detail!!.task.fields["workspace_kind"])}
                if(full || mode=="bilingual") {compose.onNodeWithTag("kanban-detail-close").performClick();compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("task-change-report"));if(full) compose.onNodeWithText("Private host hint").assertExists() else {capture("bilingual-result");compose.onNodeWithTag("kanban-close").assertIsDisplayed()}}
            } finally {f.release.countDown();compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(theme=original.theme,chinese=original.chinese,pinyin=original.pinyin))};compose.waitUntil(10000) {api.preferences.value.theme==original.theme && api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin};assertEquals(local,runBlocking {app.database.dao().tasks().first()});f.assertClean()}
        }
    }
    @Test fun ignoredTriageHoldRetainsAckWithoutConfirmingRequestedEffect()=exercise("triage-ignored")
    @Test fun minimalCreationReviewsHostDefaultsAndDoesNotClaimExclusiveCreation()=exercise("minimal")
    @Test fun allFieldsIncludeNormalizedSkillsParentsAndPrivateRuntimeChoices()=exercise("full")
    @Test fun explicitProjectPromotesRequestedScratchUsingFreshCanonicalMetadata()=exercise("project-explicit")
    @Test fun omittedProjectInheritsTheBoardBindingAfterSeparatelyApprovedCatalog()=exercise("project-inherit")
    @Test fun missingProjectCatalogExplainsTheBlockAndAllowsExplicitOptOut()=exercise("project-unread")
    @Test fun scratchExplicitlyOptsOutOfBoardInheritanceWithoutProjectRead()=exercise("scratch-unread")
    @Test fun invalidCapsPriorityAndProviderWithoutModelCannotReachReview()=exercise("invalid")
    @Test fun explicitEmptyDescriptionAndSkillListRemainDistinctFromOmission()=exercise("empty")
    @Test fun cancelledFormDropsPrivateDraftWithoutSending()=exercise("cancel-form")
    @Test fun cancelledReviewNeverClaimsOrSends()=exercise("cancel-review")
    @Test fun changedAccessInvalidatesCreationReviewAndFrozenCall()=exercise("stale")
    @Test fun closedBrowserCannotRestoreDraftFromOldRawState()=exercise("closed")
    @Test fun pendingForeignTaskClearsPrivateCreationApproval()=exercise("pending")
    @Test fun changedParentRefusesBeforeClaimOrWrite()=exercise("before-parent")
    @Test fun changedCanonicalProjectRefusesBeforeClaimOrWrite()=exercise("before-project")
    @Test fun lostReplyNeverAutomaticallyReplaysWithAnotherNonce()=exercise("lost")
    @Test fun cancellationRetainsUnknownAndDiscardsLateCreationResult()=exercise("held")
    @Test fun postWriteHomeChangeRetainsAcknowledgement()=exercise("home-after")
    @Test fun differentReportedValuesRemainAcknowledgedWithoutFalseConfirmation()=exercise("normalized")
    @Test fun bilingualCreationFieldsReviewAndReceiptKeepFootersReachable()=exercise("bilingual")
    @Test fun missingBoardObservationDisablesCreationWithExplanation()=exercise("missing-board")
}
