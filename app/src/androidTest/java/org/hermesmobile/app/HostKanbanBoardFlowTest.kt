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

class HostKanbanBoardFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun fields(j:JsonObject,vararg pairs:Pair<String,JsonElement>)=JsonObject(j+mapOf(*pairs))
    private fun tap(root:String,tag:String) {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun input(tag:String,value:String) {compose.onNodeWithTag("board-form").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performTextReplacement(value);closeDialogKeyboard()}
    private fun await() {compose.waitUntil(15000) {!vm.state.value.busy}}
    private fun read() {tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-confirm").performClick();await();assertNotNull(vm.state.value.kanban.inventory)}
    private fun grant() {compose.onNodeWithTag("board-change-review").performScrollToNode(hasTestTag("board-set-grant"));compose.onNodeWithTag("board-set-grant").assertContentDescriptionEquals(Words.KanbanBoardGrant.localized(api.preferences.value)).performClick();compose.onNodeWithTag("board-change-confirm").assertIsEnabled()}
    private fun capture(name:String) {
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun exercise(mode:String) {
        val meta=HostKanbanBoardFixture.metadata();val anchor=HostKanbanBoardFixture.metadata("default");val archive=HostKanbanBoardFixture.metadata("archive",true)
        val slug=if(mode in setOf("delete-archive","edit-archive")) "archive" else "garden"
        val action=when(mode) {"create","bilingual"->HostKanbanBoardAction.Create;"archive"->HostKanbanBoardAction.Archive;"delete","delete-archive"->HostKanbanBoardAction.Delete;"switch","override"->HostKanbanBoardAction.Switch;else->HostKanbanBoardAction.Update}
        val base=if(slug=="archive") archive else meta
        val changed=when(mode) {
            "create","bilingual"->fields(HostKanbanBoardFixture.metadata("new-board"),"name" to JsonPrimitive("New board"),"description" to JsonPrimitive("Private draft note"),"default_workdir" to JsonPrimitive("/srv/normalized/repository"))
            "edit"->fields(base,"description" to JsonPrimitive(""),"icon" to JsonPrimitive(""),"default_workdir" to JsonNull,"project_id" to JsonNull)
            "binding"->fields(base,"project_id" to JsonPrimitive("p_bind"),"default_workdir" to JsonPrimitive("/srv/project-primary"))
            else->fields(base,"description" to JsonPrimitive("Reviewed private note"))
        }
        val rows=listOf(anchor,meta,archive)
        val before=HostKanbanBoardFixture.catalog(if(mode=="no-anchor") listOf(meta,archive) else rows)
        val after=when(action) {
            HostKanbanBoardAction.Create->HostKanbanBoardFixture.catalog(rows+changed)
            HostKanbanBoardAction.Update->HostKanbanBoardFixture.catalog(rows.map {if(it["slug"]==JsonPrimitive(slug)) changed else it})
            HostKanbanBoardAction.Archive->HostKanbanBoardFixture.catalog(listOf(anchor,fields(meta,"archived" to JsonPrimitive(true)),archive),"default")
            HostKanbanBoardAction.Delete->HostKanbanBoardFixture.catalog(rows.filter {it["slug"]!=JsonPrimitive(slug)},if(slug=="garden") "default" else "garden")
            HostKanbanBoardAction.Switch->HostKanbanBoardFixture.catalog(rows,if(mode=="override") "default" else "garden")
        }
        val ack=when(action) {
            HostKanbanBoardAction.Create,HostKanbanBoardAction.Update->buildJsonObject {put("board",changed);if(action==HostKanbanBoardAction.Create) put("current","garden")}
            HostKanbanBoardAction.Switch->buildJsonObject {put("current","garden")}
            else->buildJsonObject {put("result",buildJsonObject {put("slug",slug);put("action",if(action==HostKanbanBoardAction.Archive) "archived" else "deleted");put("new_path",if(action==HostKanbanBoardAction.Archive) "/shared/private-archive/garden-123" else "")});put("current",if(action==HostKanbanBoardAction.Archive || slug=="garden") "default" else "garden")}
        }
        val body=when {
            action==HostKanbanBoardAction.Create->buildJsonObject {put("slug","new-board");put("switch",false);put("name","New board");put("description","Private draft note");put("icon","leaf");put("color","green");put("default_workdir","~/private/repository");put("project_id","")}
            action==HostKanbanBoardAction.Switch->JsonObject(emptyMap())
            action in setOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete)->null
            mode=="edit"->buildJsonObject {put("description","");put("icon","");put("default_workdir","");put("project_id","")}
            mode=="binding"->buildJsonObject {put("project_id","p_bind")}
            else->buildJsonObject {put("description","Reviewed private note")}
        }
        credentials.managementServer().use {server->
            val f=HostKanbanBoardFixture(server,if(mode in setOf("lost","held","home-after")) mode else "normal",action.method,if(action==HostKanbanBoardAction.Create) "" else "/$slug"+if(action==HostKanbanBoardAction.Switch) "/switch" else "",body,before,ack,after);f.start()
            val original=api.preferences.value;val local=runBlocking {app.database.dao().tasks().first()}
            try {
                compose.waitUntil(10000) {!api.connecting.value}
                compose.runOnUiThread {api.configure(server.url("prefix/inference/").toString(),credentials.uniqueToken("kanban-board-inference"),true);api.updatePreferences(api.preferences.value.copy(theme="STARHAVEN",chinese=mode=="bilingual",pinyin=mode=="bilingual"))}
                compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==(mode=="bilingual") && api.preferences.value.pinyin==(mode=="bilingual")}
                val scope=api.profileScope.value
                app.credentials.saveManagement(scope,buildJsonObject {put("url",f.base);put("profile","worker");put("debugHttp",true);put("tokens",f.tokens.json())})
                compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(10000) {vm.state.value.signedIn};compose.runOnUiThread {vm.refresh()};await();assertTrue(vm.state.value.loaded)
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,vm)}}}
                compose.onNodeWithTag("management-open").performClick();tap("management-list","host-kanban-open")
                compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("board-create"));compose.onNodeWithTag("board-create").assertIsNotEnabled();assertEquals(0,f.writes.get())
                if(mode in setOf("read-generation","review-generation")) {
                    if(mode=="review-generation") {tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-confirm").assertIsDisplayed()}
                    val visibleState=vm.state.value;val previousGeneration=vm.hostProjectReviewGeneration()
                    // Clearing an already-empty detail advances review identity without changing visible state.
                    compose.runOnUiThread {vm.closeHostKanbanDetail()}
                    assertSame(visibleState,vm.state.value);assertTrue(vm.hostProjectReviewGeneration()>previousGeneration)
                    if(mode=="review-generation") compose.waitUntil(5000) {compose.onAllNodesWithTag("kanban-read-confirm").fetchSemanticsNodes().isEmpty()}
                    assertNull(vm.state.value.kanban.inventory);assertEquals(0,f.writes.get())
                    read();assertNotNull(vm.state.value.kanban.inventory);assertEquals(0,f.writes.get());return@use
                }
                read()
                if(mode=="no-anchor") {compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("board-create"));compose.onNodeWithTag("board-create").assertIsNotEnabled();assertEquals(Words.KanbanAnchorRequired,vm.kanbanBoardBlock(vm.state.value));return@use}
                if(mode=="protected") {
                    for(target in listOf("default","archive")) {tap("host-kanban-list","board-controls-open-$target");compose.onNodeWithTag("board-controls").performScrollToNode(hasTestTag("board-action-$target-Archive"));compose.onNodeWithTag("board-action-$target-Archive").assertIsNotEnabled();compose.onNodeWithTag("board-controls").performScrollToNode(hasTestTag("board-action-$target-"+if(target=="default") "Delete" else "Switch"));compose.onNodeWithTag("board-action-$target-"+if(target=="default") "Delete" else "Switch").assertIsNotEnabled();compose.onNodeWithTag("board-controls-close").performClick()}
                    assertEquals(0,f.writes.get());return@use
                }
                if(mode=="binding") {tap("host-kanban-list","board-read-projects");compose.onNodeWithTag("board-projects-cancel").performClick();assertEquals(0,f.projectReads.get());tap("host-kanban-list","board-read-projects");compose.onNodeWithTag("board-projects-confirm").performClick();await();assertNotNull(vm.state.value.kanban.projects);assertEquals(1,f.projectReads.get())}
                if(action==HostKanbanBoardAction.Create) {
                    tap("host-kanban-list","board-create");compose.onNodeWithTag("board-form-review").assertIsNotEnabled()
                    input("board-field-slug","new-board");input("board-field-name","New board");input("board-field-description","Private draft note");input("board-field-icon","leaf");input("board-field-color","green");input("board-field-default_workdir","~/private/repository")
                    compose.onNodeWithTag("board-form-review").performClick()
                } else {
                    tap("host-kanban-list","board-controls-open-$slug");tap("board-controls","board-action-$slug-${action.name}")
                    if(action==HostKanbanBoardAction.Update) {
                        if(mode=="binding") {tap("board-form","board-set-project_id");tap("board-form","board-project-p_bind")}
                        else {tap("board-form","board-set-description");input("board-field-description",if(mode=="edit") "" else "Reviewed private note");if(mode=="edit") {for(key in listOf("icon","default_workdir","project_id")) tap("board-form","board-set-$key");input("board-field-icon","");input("board-field-default_workdir","");tap("board-form","board-project-none")}}
                        compose.onNodeWithTag("board-form-review").performClick()
                    }
                }
                compose.onNodeWithTag("board-change-confirm").assertIsNotEnabled();assertEquals(0,f.writes.get())
                val inventory=vm.state.value.kanban.inventory!!
                val frozen=vm.prepareKanbanBoardChange(when(action) {HostKanbanBoardAction.Create->HostKanbanBoardChange.create(inventory,HostKanbanBoardDraft("new-board",body!!.filterKeys {it!="slug" && it!="switch"}.mapValues {it.value.jsonPrimitive.content}));HostKanbanBoardAction.Update->HostKanbanBoardChange.update(inventory,slug,body!!.mapValues {it.value.jsonPrimitive.content},vm.state.value.kanban.projects);else->HostKanbanBoardChange.action(inventory,slug,action)})!!
                if(mode in setOf("pending","pending-project")) {
                    val other=f.owner.copy(apiScope="e".repeat(64),profile=if(mode=="pending-project") "alias" else "other",actor="password · private-other")
                    runBlocking {app.database.dao().insertManagementOperation(ManagementOperation(UUID.randomUUID().toString(),other.resourceScope,other.apiScope,other.url,other.profile,other.actor,if(mode=="pending-project") "ProjectUpdate" else "KanbanBoardDelete","private-target","private-request","unknown","private-result",System.currentTimeMillis(),if(mode=="pending-project") f.homeId else ""))}
                    compose.waitUntil(10000) {vm.hostProjectPending(vm.state.value)};compose.onNodeWithTag("board-change-review").assertDoesNotExist();compose.onNodeWithText("Reviewed private note",substring=true).assertDoesNotExist();compose.onNodeWithText("private-other",substring=true,useUnmergedTree=true).assertDoesNotExist()
                    compose.runOnUiThread {vm.performKanbanBoardChange(frozen)};assertEquals(0,f.writes.get());return@use
                }
                if(mode=="stale") {
                    compose.runOnUiThread {vm.chooseProfile("other")};await();compose.onNodeWithTag("board-change-review").assertDoesNotExist();compose.runOnUiThread {vm.performKanbanBoardChange(frozen)};assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,f.writes.get());return@use
                }
                if(mode=="closed") {
                    val raw=vm.state.value;compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {HermesAppTheme(AppPreferences()) {HostKanbanDialog(vm,raw) {}}}}
                    assertNull(vm.visibleHostKanban(raw).inventory);compose.onNodeWithTag("board-change-review").assertDoesNotExist();compose.onNodeWithText("Reviewed private note",substring=true).assertDoesNotExist();assertEquals(0,f.writes.get());return@use
                }
                if(action==HostKanbanBoardAction.Delete) {
                    tap("board-change-review","board-set-grant");compose.onNodeWithTag("board-change-confirm").assertIsNotEnabled();compose.onNodeWithTag("board-change-review").performScrollToNode(hasTestTag("board-delete-slug"));compose.onNodeWithTag("board-delete-slug").performTextReplacement("wrong");closeDialogKeyboard();compose.onNodeWithTag("board-change-confirm").assertIsNotEnabled();compose.onNodeWithTag("board-delete-slug").performTextReplacement(slug);closeDialogKeyboard()
                } else grant()
                compose.onNodeWithTag("board-change-confirm").assertIsEnabled().assertHeightIsAtLeast(48.dp)
                if(mode=="cancel") {
                    compose.onNodeWithTag("board-change-cancel").performClick();assertEquals(0,f.writes.get());compose.onNodeWithText("Reviewed private note",substring=true).assertDoesNotExist()
                    tap("board-controls","board-action-garden-Update");tap("board-form","board-set-description");compose.onNodeWithTag("board-field-description").assert(hasText("Private description"));compose.onNodeWithTag("board-form-cancel").performClick();return@use
                }
                if(mode=="bilingual") {compose.onNodeWithTag("board-change-review").performScrollToNode(hasText("~/private/repository"));capture("kanban-board-bilingual-review")}
                if(mode=="before-home") f.home.set("/srv/remapped")
                if(mode=="before-root") f.beforeOverride.set(HostKanbanBoardFixture.catalog(listOf(fields(anchor,"db_path" to JsonPrimitive("/other/root.db")),meta,archive)))
                compose.onNodeWithTag("board-change-confirm").performClick()
                if(mode=="held") {
                    compose.waitUntil(10000) {f.entered.count==0L};tap("host-kanban-list","kanban-cancel-work");f.release.countDown();await();compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperations(scope).first().singleOrNull()?.state}=="unknown"};assertNull(vm.state.value.kanban.report);assertNull(vm.state.value.kanban.inventory);assertEquals(1,f.writes.get());return@use
                }
                await()
                if(mode in setOf("before-home","before-root")) {assertEquals(0,f.writes.get());assertNull(vm.state.value.kanban.inventory);assertTrue(runBlocking {app.database.dao().managementOperations(scope).first().isEmpty()});return@use}
                assertEquals(1,f.writes.get());val receipt=runBlocking {app.database.dao().managementOperations(scope).first().single()};assertEquals(receipt,vm.state.value.kanban.report!!.receipt)
                if(mode=="lost") {
                    assertEquals("unknown",receipt.state);read();assertEquals(1,f.writes.get());compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("board-create"));compose.onNodeWithTag("board-create").assertIsNotEnabled()
                    tap("host-kanban-list","board-return-receipts");tap("management-list","management-review-${receipt.key}");compose.onNodeWithTag("management-review-confirm").assertIsNotEnabled();tap("management-own-review","management-instance-approve");compose.onNodeWithTag("management-review-confirm").performClick();await()
                    compose.waitUntil(10000) {runBlocking {app.database.dao().managementOperation(receipt.key)!!.state}=="reviewed"};assertEquals(receipt.copy(state="reviewed"),runBlocking {app.database.dao().managementOperation(receipt.key)!!});assertEquals(1,f.writes.get());return@use
                }
                if(mode=="home-after") {assertEquals("acknowledged",receipt.state);assertEquals(Words.KanbanBoardReadbackPending,vm.state.value.error);assertNull(vm.state.value.kanban.inventory);return@use}
                assertNull(vm.state.value.error);assertEquals(if(mode=="override") "acknowledged" else "confirmed",receipt.state)
                val summary=Json.parseToJsonElement(receipt.resultSummary).jsonObject;for(key in listOf("new_board_verified","file_erasure_verified","runtime_stop_verified","path_mapping_verified")) assertEquals(JsonPrimitive(false),summary[key])
                if(mode=="binding") {assertEquals(2,f.projectReads.get());assertEquals("/srv/project-primary",vm.state.value.kanban.inventory!!.boards.single {it.slug=="garden"}.workdir)}
                if(mode=="archive") {assertEquals("/shared/private-archive/garden-123",vm.state.value.kanban.report!!.archivePath);assertFalse(receipt.resultSummary.contains("/shared/"))}
                if(mode=="bilingual") {compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("board-change-report"));compose.onAllNodesWithText(Words.KanbanBoardObserved.en,substring=true).assertCountEquals(1);capture("kanban-board-bilingual-result");compose.onNodeWithTag("kanban-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)}
            } finally {
                f.release.countDown();compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(theme=original.theme,chinese=original.chinese,pinyin=original.pinyin))};compose.waitUntil(10000) {api.preferences.value.theme==original.theme && api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin};assertEquals(local,runBlocking {app.database.dao().tasks().first()});f.assertClean()
            }
        }
    }
    @Test fun createReviewsAllPrivateFieldsWithoutAutoSwitching()=exercise("create")
    @Test fun bilingualCreateAndReceiptsKeepFullReviewAndControlsReachable()=exercise("bilingual")
    @Test fun editPreservesOmittedFieldsAndExplicitlyClearsDirectoryAndBinding()=exercise("edit")
    @Test fun projectChoicesRequireSeparateReadAndFreshCanonicalPreflight()=exercise("binding")
    @Test fun archiveReportsTransientPathWithoutStoppingWorkersOrClaimingErasure()=exercise("archive")
    @Test fun deleteRequiresBothFreshGrantAndExactTypedSlug()=exercise("delete")
    @Test fun deletingAnArchivedTombstoneDoesNotClaimToDeleteTheEarlierArchive()=exercise("delete-archive")
    @Test fun archivedMetadataCanBeEditedWithoutPresentingRestore()=exercise("edit-archive")
    @Test fun alreadyReportedCurrentBoardCanStillPersistTheSharedPointer()=exercise("switch")
    @Test fun environmentOverridePreventsFalseCurrentBoardConfirmation()=exercise("override")
    @Test fun defaultRemovalAndArchivedSwitchOrRepeatArchiveAreDisabled()=exercise("protected")
    @Test fun missingDefaultAnchorDisablesBoardChangesWithExplanation()=exercise("no-anchor")
    @Test fun cancellingApprovalDropsPrivateDraftAndNeverSends()=exercise("cancel")
    @Test fun pendingBoardWorkInvalidatesDraftAcrossDifferentProfiles()=exercise("pending")
    @Test fun pendingProjectWorkInvalidatesDraftAcrossSameHomeAliases()=exercise("pending-project")
    @Test fun profileSwitchInvalidatesFrozenApprovalWithoutSending()=exercise("stale")
    @Test fun closingCannotRestorePrivateDraftFromStaleRawState()=exercise("closed")
    @Test fun changedAccessHomeBeforeSendDoesNotClaimOrWrite()=exercise("before-home")
    @Test fun changedSharedRootBeforeSendDoesNotClaimOrWrite()=exercise("before-root")
    @Test fun postWriteAccessChangeRetainsAcknowledgementWithoutPrivateReadback()=exercise("home-after")
    @Test fun lostReplyRemainsBlockedAfterReadAndRequiresExplicitLocalReview()=exercise("lost")
    @Test fun cancellationDropsLateReplyAndRetainsUnknownBarrier()=exercise("held")
    @Test fun inventoryReadApprovalRefreshesAfterGenerationOnlyChange()=exercise("read-generation")
    @Test fun inventoryReadApprovalClosesWhenGenerationChangesWithoutVisibleStateChange()=exercise("review-generation")

}
