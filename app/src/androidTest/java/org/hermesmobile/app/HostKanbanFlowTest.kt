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
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HostKanbanFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun tap(root:String,tag:String) {compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun assertStackedText(first:String,second:String) {
        compose.onNodeWithTag("kanban-task-detail").performScrollToNode(hasText(second))
        val a=compose.onNodeWithText(first,useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithText(second,useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Selectable text must occupy separate readable rows",b.top>=a.bottom-1f)
    }
    private fun await() {compose.waitUntil(15000) {!vm.state.value.busy}}
    private fun capture(name:String) {
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("No actual screen capture")
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun exercise(mode:String) {
        credentials.managementServer().use {server->
            val f=HostKanbanFixture(server,mode);f.start()
            val original=api.preferences.value
            val localBefore=runBlocking {app.database.dao().tasks().first()}
            try {
                compose.waitUntil(10000) {!api.connecting.value}
                compose.runOnUiThread {api.configure(server.url("prefix/inference/").toString(),credentials.uniqueToken("kanban-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(theme="STARHAVEN",chinese=mode=="normal",pinyin=mode=="normal"))}
                compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==(mode=="normal")}
                val scope=api.profileScope.value
                app.credentials.saveManagement(scope,buildJsonObject {put("url",server.url("prefix/dashboard/").toString());put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("kanban-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
                compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(10000) {vm.state.value.signedIn}
                compose.runOnUiThread {vm.refresh()};await();assertTrue(vm.state.value.loaded)
                compose.runOnUiThread {compose.activity.setContent {val prefs by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(prefs) {ManagementEntry(api,vm)}}}
                compose.onNodeWithTag("management-open").performClick();tap("management-list","host-kanban-open")
                assertEquals(0,f.catalogs.get());assertEquals(0,f.boards.get());assertEquals(0,f.tasks.get())
                if(mode=="no-home") {
                    compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("kanban-inventory-read"));compose.onNodeWithTag("kanban-inventory-read").assertIsNotEnabled()
                    assertNull(vm.prepareHostKanbanRead());assertEquals(0,f.catalogs.get());return@use
                }
                tap("host-kanban-list","kanban-inventory-read");compose.onNodeWithTag("kanban-read-cancel").assertHeightIsAtLeast(48.dp).performClick();assertEquals(0,f.catalogs.get())
                tap("host-kanban-list","kanban-inventory-read")
                if(mode=="stale-inventory") {
                    val frozen=vm.prepareHostKanbanRead()!!;compose.runOnUiThread {vm.chooseProfile("other")};await()
                    compose.runOnUiThread {vm.loadHostKanban(frozen)};assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,f.catalogs.get());assertNull(vm.state.value.kanban.inventory);return@use
                }
                if(mode=="before") f.home.set("/srv/remapped-access")
                compose.onNodeWithTag("kanban-read-confirm").assertIsDisplayed().performClick();await()
                if(mode in setOf("before","after-inventory")) {
                    assertEquals(Words.KanbanScopeChanged,vm.state.value.error);assertNull(vm.state.value.kanban.inventory);assertEquals(if(mode=="before") 0 else 1,f.catalogs.get());return@use
                }
                if(mode in setOf("unavailable","redirect")) {assertEquals(Words.ManagementUnsupported.takeIf {mode=="unavailable"} ?: Words.ManagementFailed,vm.state.value.error);assertNull(vm.state.value.kanban.inventory);assertEquals(1,f.catalogs.get());return@use}
                val inventory=vm.state.value.kanban.inventory!!;assertEquals("default",inventory.current);assertEquals(2,inventory.boards.size)
                compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("kanban-board-read-archive"));compose.onNodeWithTag("kanban-board-read-archive").assertIsNotEnabled()
                val selected=inventory.boards.single {it.slug=="garden"}
                tap("host-kanban-list","kanban-board-read-garden");compose.onNodeWithTag("kanban-read-cancel").performClick();assertEquals(0,f.boards.get())
                tap("host-kanban-list","kanban-board-read-garden")
                if(mode=="stale-board") {
                    val frozen=vm.prepareHostKanbanRead(selected)!!;compose.runOnUiThread {vm.chooseProfile("other")};await();compose.runOnUiThread {vm.loadHostKanban(frozen)}
                    assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,f.boards.get());assertNull(vm.state.value.kanban.inventory);return@use
                }
                if(mode=="before-board") f.dbPath.set("/other/kanban.db")
                compose.onNodeWithTag("kanban-read-confirm").performClick()
                if(mode=="held") {
                    compose.waitUntil(10000) {f.entered.count==0L};compose.onNodeWithTag("kanban-cancel-work").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick();f.release.countDown();compose.waitUntil(10000) {f.returned.count==0L};compose.waitForIdle()
                    assertNull(vm.state.value.kanban.board);assertNull(vm.state.value.kanban.inventory);assertEquals(1,f.boards.get());return@use
                }
                await()
                if(mode in setOf("before-board","after-board")) {
                    assertEquals(Words.KanbanScopeChanged,vm.state.value.error);assertNull(vm.state.value.kanban.inventory);assertNull(vm.state.value.kanban.board);assertEquals(if(mode=="before-board") 0 else 1,f.boards.get());return@use
                }
                val snapshot=vm.state.value.kanban.board!!;assertEquals("garden",snapshot.board.slug);assertEquals("default",vm.state.value.kanban.inventory!!.current);assertEquals(3,f.catalogs.get());assertEquals(1,f.boards.get())
                if(mode=="filters") {
                    compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("kanban-search"));compose.onNodeWithTag("kanban-search").performTextReplacement("Nightly");closeDialogKeyboard()
                    compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasText("Nightly archive"));compose.onNodeWithText("Nightly archive").assertIsDisplayed()
                    compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasTestTag("kanban-filter-running"));compose.onNodeWithTag("kanban-filter-running").performClick()
                    compose.onNodeWithTag("host-kanban-list").performScrollToNode(hasText(Words.KanbanNoMatches.en,substring=true));compose.onNodeWithText(Words.KanbanNoMatches.en,substring=true).assertIsDisplayed()
                    assertSame(snapshot,vm.state.value.kanban.board);assertEquals(1,f.boards.get());assertEquals(0,f.tasks.get());assertEquals(3,f.catalogs.get());return@use
                }
                tap("host-kanban-list","kanban-task-read-t_123")
                if(mode=="stale-task") {
                    val frozen=vm.prepareHostKanbanRead(snapshot.board,"t_123")!!;compose.runOnUiThread {vm.chooseProfile("other")};await();compose.runOnUiThread {vm.loadHostKanban(frozen)}
                    assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,f.tasks.get());assertNull(vm.state.value.kanban.board);return@use
                }
                if(mode=="before-task") f.dbPath.set("/other/kanban.db")
                if(mode=="before-task-access") f.home.set("/srv/remapped-access")
                if(mode=="normal") {compose.onNodeWithTag("kanban-read-review").performScrollToNode(hasText(snapshot.board.dbPath));capture("kanban-bilingual-read-review")}
                compose.onNodeWithTag("kanban-read-confirm").performClick();await()
                if(mode in setOf("before-task","after-task","before-task-access","after-task-access")) {assertEquals(Words.KanbanScopeChanged,vm.state.value.error);assertNull(vm.state.value.kanban.inventory);assertNull(vm.state.value.kanban.board);assertNull(vm.state.value.kanban.detail);assertEquals(if(mode.startsWith("before")) 0 else 1,f.tasks.get());return@use}
                if(mode in setOf("wrong-task","foreign-history")) {assertEquals(Words.KanbanUnsupported,vm.state.value.error);assertNull(vm.state.value.kanban.detail);compose.onNodeWithText("Private wrong task").assertDoesNotExist();return@use}
                val detail=vm.state.value.kanban.detail!!;assertEquals("t_123",detail.task.id);assertEquals("running",detail.runs.single().status);assertNull(detail.runs.single().endedAt);assertNull(detail.runs.single().outcome)
                assertNull(vm.prepareHostKanbanRead(inventory.boards.single {it.slug=="archive"},"t_123"))
                val wrongBoard=selected.copy(slug="foreign");assertNull(vm.prepareHostKanbanRead(wrongBoard,"t_123"))
                if(mode=="child" || mode=="missing-link") {
                    val id=if(mode=="child") "t_child" else "t_missing";val word=if(mode=="child") Words.KanbanChildren else Words.KanbanParents
                    tap("kanban-task-detail","kanban-link-read-"+word.name+"-"+id);compose.onNodeWithTag("kanban-read-confirm").performClick();await()
                    if(mode=="child") {assertEquals("t_child",vm.state.value.kanban.detail!!.task.id);assertEquals("garden",vm.state.value.kanban.detail!!.board.slug)}
                    else {assertNull(vm.state.value.kanban.detail);assertEquals(Words.ManagementUnsupported,vm.state.value.error)}
                    assertEquals(2,f.tasks.get());return@use
                }
                if(mode=="closed") {
                    val raw=vm.state.value;compose.runOnUiThread {vm.closeHostKanban();compose.activity.setContent {HermesAppTheme(AppPreferences(chinese=false,pinyin=false)) {HostKanbanDialog(vm,raw) {}}}}
                    assertNull(vm.visibleHostKanban(raw).inventory);assertNull(vm.visibleHostKanban(raw).board);assertNull(vm.visibleHostKanban(raw).detail)
                    compose.onNodeWithText("Private shared board").assertDoesNotExist();compose.onNodeWithText("Private board task").assertDoesNotExist();return@use
                }
                for(text in listOf("Private task body\n第二行","Private comment body","Private full attempt summary","Private child handoff","Private attachment.txt")) {compose.onNodeWithTag("kanban-task-detail").performScrollToNode(hasText(text));compose.onNodeWithText(text).assertIsDisplayed()}
                for(secret in listOf("never-render-claim","never-render-idempotency","never-render-metadata","never-render-payload","/srv/never-render-attachment")) compose.onNodeWithText(secret,substring=true).assertDoesNotExist()
                assertStackedText("Private author","Private comment body");assertStackedText("Private attachment.txt","12 B")
                capture("kanban-bilingual-task-history");compose.onNodeWithTag("kanban-detail-close").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
                assertEquals(1,f.tasks.get());assertEquals(1,f.boards.get());assertEquals(5,f.catalogs.get())
            } finally {
                f.release.countDown();compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(theme=original.theme,chinese=original.chinese,pinyin=original.pinyin))}
                compose.waitUntil(10000) {api.preferences.value.theme==original.theme && api.preferences.value.chinese==original.chinese && api.preferences.value.pinyin==original.pinyin}
                assertEquals(localBefore,runBlocking {app.database.dao().tasks().first()});f.assertClean()
            }
        }
    }
    @Test fun reviewedBilingualBoardAndCompleteTaskHistoryKeepSharedHostAndLocalTasksDistinct()=exercise("normal")
    @Test fun columnAndTextFiltersStayLocalWithoutRefreshingOrDispatching()=exercise("filters")
    @Test fun childTaskNavigationRetainsExplicitBoardAndCanonicalId()=exercise("child")
    @Test fun missingDependencyDetailsRemainAnExplicitFailedRead()=exercise("missing-link")
    @Test fun staleInventoryReviewCannotUseAnotherProfile()=exercise("stale-inventory")
    @Test fun staleBoardReviewCannotUseAnotherProfile()=exercise("stale-board")
    @Test fun staleTaskReviewCannotUseAnotherProfile()=exercise("stale-task")
    @Test fun remappedAccessBeforeInventoryDoesNotReadBoards()=exercise("before")
    @Test fun remappedAccessAfterInventoryCannotPublishPrivateMetadata()=exercise("after-inventory")
    @Test fun remappedBoardBeforeReadDoesNotReadTasks()=exercise("before-board")
    @Test fun remappedBoardAfterReadCannotPublishPrivateTasks()=exercise("after-board")
    @Test fun remappedBoardBeforeTaskDetailsPreventsTheTaskRead()=exercise("before-task")
    @Test fun remappedBoardAfterTaskDetailsDropsAllPrivateViews()=exercise("after-task")
    @Test fun remappedAccessBeforeTaskDetailsPreventsTheTaskRead()=exercise("before-task-access")
    @Test fun remappedAccessAfterTaskDetailsDropsAllPrivateViews()=exercise("after-task-access")
    @Test fun canonicalTaskMismatchCannotRenderAnotherTask()=exercise("wrong-task")
    @Test fun foreignTaskHistoryCannotBecomeTheRequestedTaskHistory()=exercise("foreign-history")
    @Test fun cancellationDropsLateBoardReplyWithoutReplay()=exercise("held")
    @Test fun closedPrivateViewCannotBeReconstitutedFromAStaleRawState()=exercise("closed")
    @Test fun unavailablePluginDoesNotBecomeAnEmptyBoard()=exercise("unavailable")
    @Test fun redirectsAreNotFollowedWithTheManagementCredential()=exercise("redirect")
    @Test fun missingProfileHomeDisablesTheReadWithAnExplanation()=exercise("no-home")
}
