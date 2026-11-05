package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class HostProjectManagementFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val vm get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private val id="p_00000001"
    private val path="/srv/private/garden"
    private fun fields(j:JsonObject,vararg pairs:Pair<String,JsonElement>)=JsonObject(j+mapOf(*pairs))
    private fun folder(path:String,primary:Boolean,label:String?=null)=buildJsonObject {put("path",path);put("is_primary",primary);put("added_at",101);label?.let {put("label",it)}}
    private fun original()=fields(HostProjectChangeFixture.row(),"icon" to JsonPrimitive("sprout"),"color" to JsonPrimitive("forest"),"board_slug" to JsonPrimitive("board"))
    private fun listing(rows:List<JsonObject>,active:String?=id)=HostProjectChangeFixture.listing(rows,active)
    private fun answer(j:JsonObject)=HostProjectChangeFixture.answer(j)
    private fun wire(vararg pairs:Pair<String,JsonElement>)=JsonObject(mapOf("profile" to JsonPrimitive("worker"))+mapOf(*pairs))
    private fun tap(list:String,tag:String) {compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun input(tag:String,value:String) {compose.onNodeWithTag("project-form").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performTextReplacement(value);closeDialogKeyboard()}
    private fun read() {
        tap("host-projects-list","host-projects-read");compose.onNodeWithTag("host-projects-read-confirm").performClick()
        compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.projects.inventory!=null}
    }
    private fun details() {tap("host-projects-list","host-project-details-$id");compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.projects.detail!=null}}
    private fun editName(value:String="Reviewed garden") {tap("host-project-detail","project-action-Update");tap("project-form","project-form-name-set");input("project-form-name",value)}
    private fun review() {compose.onNodeWithTag("project-form-review").assertIsEnabled().performClick();compose.onNodeWithTag("project-change-confirm").assertIsNotEnabled()}
    private fun approve() {tap("project-change-review","project-change-approve");compose.onNodeWithTag("project-change-confirm").assertIsEnabled()}
    private fun capture(name:String) {
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }

    private fun exercise(mode:String) {
        val base=original();val alternate=folder("/srv/next",false)
        val both=fields(base,"folders" to JsonArray(listOf(base["folders"]!!.jsonArray.single(),alternate)))
        val primaryNext=fields(base,"primary_path" to JsonPrimitive("/srv/next"),"folders" to JsonArray(listOf(folder(path,false),folder("/srv/next",true))))
        val detached=fields(base,"primary_path" to JsonPrimitive("/srv/next"),"folders" to JsonArray(listOf(folder("/srv/next",true))))
        val updated=fields(base,"name" to JsonPrimitive("Reviewed garden"),"description" to JsonPrimitive(""),"icon" to JsonNull,"board_slug" to JsonNull)
        val named=fields(base,"name" to JsonPrimitive("Reviewed garden"))
        val created=buildJsonObject {
            put("id","p_00000002");put("slug","garden-2");put("name","New garden");put("description","Private draft note");put("icon","sprout");put("color","forest");put("board_slug","board-two");put("created_at",1001);put("archived",false)
            put("primary_path","/srv/primary");put("folders",JsonArray(listOf(folder("/srv/new",false),folder("/srv/primary",true))))
        }
        val added=fields(base,"primary_path" to JsonPrimitive("/srv/next"),"folders" to JsonArray(listOf(folder(path,false),folder("/srv/next",true,"Next folder"))))
        val labelled=fields(base,"folders" to JsonArray(listOf(folder(path,true,"Original folder label"))))
        val unmarked=fields(labelled,"primary_path" to JsonNull,"folders" to JsonArray(listOf(folder(path,false,"Original folder label"))))
        val emptyLabel=fields(labelled,"folders" to JsonArray(listOf(folder(path,true,""))))
        val archived=fields(base,"archived" to JsonPrimitive(true))
        val beforeRow=when(mode) {"restore"->archived;"primary","noop","remove"->both;"no-primary"->unmarked;"label-empty"->labelled;else->base}
        val beforeRows=if(mode=="collision") listOf(base,HostProjectChangeFixture.row("p_00000002",id)) else listOf(beforeRow)
        val before=listing(beforeRows,if(mode=="active") null else if(mode=="clear") "p_missing" else id)
        val after=when(mode) {
            "create","bilingual"->listing(listOf(base,created))
            "update"->listing(listOf(updated))
            "add"->listing(listOf(added))
            "no-primary"->listing(listOf(labelled))
            "label-empty"->listing(listOf(emptyLabel))
            "primary"->listing(listOf(primaryNext))
            "noop"->listing(listOf(both))
            "remove"->listing(listOf(detached))
            "archive"->listing(listOf(archived))
            "restore","active"->listing(listOf(base))
            "delete"->listing(emptyList())
            "clear"->listing(listOf(base),null)
            "null-create"->before
            else->listing(listOf(named))
        }
        val ack=when(mode) {
            "create","bilingual"->answer(created);"update"->answer(updated);"add"->answer(added);"no-primary"->answer(labelled);"label-empty"->answer(emptyLabel);"primary"->answer(primaryNext);"noop"->answer(both);"remove"->answer(detached)
            "archive","restore","delete"->after
            "active"->buildJsonObject {put("active_id",id)}
            "clear","null-create"->JsonObject(emptyMap())
            else->answer(named)
        }
        val serverMode=when(mode) {"lost"->"disconnect";"held"->"held";"after-home"->"ui-after-home";else->"normal"}
        val detail=answer(if(mode=="detail-changed") named else beforeRow)
        HostProjectChangeFixture(serverMode,before,ack,after,beforeReads=2,detail=detail,prefix="ui-project-${UUID.randomUUID()}/").use {f->
            val delegate=f.server.dispatcher
            f.server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val body=when {
                    q.path=="/inference/v1/capabilities"->"""{"features":{},"endpoints":{}}"""
                    q.path.orEmpty().contains("/inference/api/jobs?")->"""{"jobs":[]}"""
                    q.path.orEmpty().startsWith("/${f.prefix}api/mcp/servers?profile=")->"""{"servers":[]}"""
                    else->null
                }
                return if(body==null) delegate.dispatch(q) else MockResponse().setHeader("Content-Type","application/json").setBody(body)
            }}
            val originalPreferences=api.preferences.value
            try {
                compose.waitUntil(10000) {!api.connecting.value}
                compose.runOnUiThread {api.configure(f.server.url("inference/").toString(),credentials.uniqueToken("project-forms-inference"),true);api.updatePreferences(api.preferences.value.copy(chinese=mode=="bilingual",pinyin=mode=="bilingual",theme="STARHAVEN"))}
                compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==(mode=="bilingual") && api.preferences.value.pinyin==(mode=="bilingual")}
                val scope=api.profileScope.value
                app.credentials.saveManagement(scope,buildJsonObject {put("url",f.api.base.toString());put("profile","worker");put("debugHttp",true);put("tokens",f.tokens.json())})
                compose.runOnUiThread {vm.bind(scope)};compose.waitUntil(5000) {vm.state.value.signedIn}
                compose.runOnUiThread {vm.refresh()};compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,vm)}}}
                compose.onNodeWithTag("management-open").performClick();tap("management-list","host-projects-open")
                compose.onNodeWithTag("host-projects-list").performScrollToNode(hasTestTag("project-create"));compose.onNodeWithTag("project-create").assertIsNotEnabled();assertEquals(0,f.tickets.get())
                read();assertEquals(1,f.lists.get());assertEquals(0,f.writes.get())
                if(mode !in setOf("create","null-create","bilingual","clear")) details()
                if(mode in setOf("collision","detail-changed")) {
                    compose.onNodeWithTag("host-project-detail").performScrollToNode(hasTestTag("project-action-Update"));compose.onNodeWithTag("project-action-Update").assertIsNotEnabled()
                    compose.onNodeWithTag("host-project-detail").performScrollToNode(hasText((if(mode=="collision") Words.ProjectCollision else Words.ProjectReloadInventory).en,substring=true))
                    assertEquals(0,f.writes.get());return@use
                }
                when(mode) {
                    "create","null-create","bilingual"->{
                        tap("host-projects-list","project-create");compose.onNodeWithTag("project-form-review").assertIsNotEnabled();input("project-form-name",if(mode=="null-create") "New" else "New garden")
                        if(mode!="null-create") {input("project-form-slug","garden");tap("project-form","project-form-description-set");input("project-form-description","Private draft note");input("project-form-icon","sprout");input("project-form-color","forest");input("project-form-board","board-two");input("project-form-paths","../new");input("project-form-primary-path","/srv/primary")}
                        review()
                    }
                    "update"->{editName();tap("project-form","project-form-description-set");input("project-form-description","");tap("project-form","project-form-icon-set");input("project-form-icon","");tap("project-form","project-form-board-set");input("project-form-board","");review()}
                    "add"->{tap("host-project-detail","project-action-AddFolder");input("project-form-path","../next");tap("project-form","project-form-label-set");input("project-form-label","Next folder");tap("project-form","project-form-primary");review()}
                    "no-primary","label-empty"->{tap("host-project-detail","project-action-AddFolder");input("project-form-path",path);if(mode=="label-empty") {tap("project-form","project-form-label-set");input("project-form-label","")};review()}
                    "primary","noop"->tap("host-project-detail","project-folder-primary-1")
                    "remove"->tap("host-project-detail","project-folder-remove-0")
                    "archive"->tap("host-project-detail","project-action-Archive")
                    "restore"->tap("host-project-detail","project-action-Restore")
                    "delete"->tap("host-project-detail","project-action-Delete")
                    "active"->tap("host-project-detail","project-action-SetActive")
                    "clear"->tap("host-projects-list","project-clear-active")
                    else->{editName(if(mode in setOf("cancel","pending","pending-instance","stale")) "Private unsent draft" else "Reviewed garden");review()}
                }
                compose.onNodeWithTag("project-change-confirm").assertIsNotEnabled();assertEquals(0,f.writes.get())
                if(mode in setOf("pending","pending-instance")) {
                    val owner=vm.reviewTarget()!!.copy(apiScope="e".repeat(64),profile="mirror",actor="password · private-other")
                    runBlocking {app.database.dao().insertManagementOperation(ManagementOperation(UUID.randomUUID().toString(),owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,if(mode=="pending-instance") "CustomEndpointProbe" else "ProjectUpdate","private-other-target","private-other-request","unknown","private-other-result",System.currentTimeMillis(),if(mode=="pending-instance") "" else f.home))}
                    compose.waitUntil(10000) {vm.hostProjectPending(vm.state.value)}
                    compose.onNodeWithTag("project-change-review").assertDoesNotExist();compose.onNodeWithText("Private unsent draft",substring=true).assertDoesNotExist();compose.onNodeWithText("private-other",substring=true,useUnmergedTree=true).assertDoesNotExist()
                    compose.onNodeWithTag("host-project-detail-close").performClick();compose.onNodeWithTag("host-projects-list").performScrollToNode(hasTestTag("project-create"));compose.onNodeWithTag("project-create").assertIsNotEnabled();assertEquals(0,f.writes.get());return@use
                }
                if(mode=="cancel") {
                    approve();compose.onNodeWithTag("project-change-cancel").performClick();assertEquals(0,f.writes.get());compose.onNodeWithText("Private unsent draft",substring=true).assertDoesNotExist()
                    tap("host-project-detail","project-action-Update");compose.onNodeWithTag("project-form-name").assertDoesNotExist();tap("project-form","project-form-name-set");compose.onNodeWithTag("project-form-name").assert(hasText("Private garden"))
                    compose.onNodeWithTag("project-form-cancel").performClick();return@use
                }
                if(mode=="stale") {
                    val snapshot=vm.state.value.projects.inventory!!;val frozen=vm.prepareHostProjectChange(HostProjectChange.update(snapshot,id,mapOf("name" to "Private unsent draft")))!!
                    approve();compose.runOnUiThread {vm.chooseProfile("mirror")};compose.waitUntil(10000) {!vm.state.value.busy && vm.state.value.profile=="mirror"}
                    compose.onNodeWithTag("project-change-review").assertDoesNotExist();compose.onNodeWithText("Private unsent draft",substring=true).assertDoesNotExist();val requests=f.requests.size
                    compose.runOnUiThread {vm.performHostProjectChange(frozen)};assertEquals(requests,f.requests.size);assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,f.writes.get());return@use
                }
                approve()
                if(mode=="bilingual") capture("project-create-bilingual-review")
                if(mode=="before-home") f.reportedHome.set("/srv/remapped")
                compose.onNodeWithTag("project-change-confirm").performClick()
                if(mode=="held") {
                    compose.waitUntil(10000) {f.entered.count==0L};tap("host-projects-list","host-projects-cancel-work");f.release.countDown()
                    compose.waitUntil(10000) {!vm.state.value.busy && runBlocking {app.database.dao().managementOperations(scope).first().singleOrNull()?.state}=="unknown"}
                    assertNull(vm.state.value.projects.inventory);assertNull(vm.state.value.projects.report);assertEquals(1,f.writes.get());return@use
                }
                compose.waitUntil(10000) {!vm.state.value.busy}
                if(mode=="before-home") {assertEquals(Words.ManagementReviewChanged,vm.state.value.error);assertEquals(0,f.writes.get());assertEquals(2,f.tickets.get());assertNull(vm.state.value.projects.inventory);return@use}
                assertEquals(1,f.writes.get())
                val receipt=runBlocking {app.database.dao().managementOperations(scope).first().single()}
                assertEquals(receipt,vm.state.value.projects.report!!.receipt)
                assertFalse((receipt.requestSummary+receipt.resultSummary).contains("Reviewed garden"));assertFalse((receipt.requestSummary+receipt.resultSummary).contains(path))
                if(mode=="lost") {
                    assertEquals("unknown",receipt.state);read();assertEquals(1,f.writes.get());assertEquals("Reviewed garden",vm.state.value.projects.inventory!!.projects.single().name)
                    compose.onNodeWithTag("host-projects-list").performScrollToNode(hasTestTag("project-create"));compose.onNodeWithTag("project-create").assertIsNotEnabled()
                    tap("host-projects-list","project-return-receipts");tap("management-list","management-review-"+receipt.key)
                    compose.onNodeWithTag("management-shared-review-confirm").assertIsNotEnabled();tap("management-shared-review","management-project-approve");compose.onNodeWithTag("management-shared-review-confirm").performClick()
                    compose.waitUntil(10000) {!vm.state.value.busy && runBlocking {app.database.dao().managementOperation(receipt.key)!!.state}=="reviewed"}
                    assertEquals(receipt.copy(state="reviewed"),runBlocking {app.database.dao().managementOperation(receipt.key)!!});assertEquals(1,f.writes.get());return@use
                }
                if(mode=="after-home") {assertEquals("acknowledged",receipt.state);assertEquals(Words.ManagementReadbackPending,vm.state.value.error);assertNull(vm.state.value.projects.inventory);compose.onNodeWithTag("host-projects-list").performScrollToNode(hasText(Words.ProjectAcknowledged.en,substring=true));return@use}
                assertNull(vm.state.value.error)
                assertEquals(if(mode in setOf("noop","null-create")) "acknowledged" else "confirmed",receipt.state)
                if(mode=="null-create") {compose.onNodeWithTag("host-projects-list").performScrollToNode(hasText(Words.ProjectNoReturnedProject.en,substring=true));assertEquals(beforeRows.size,vm.state.value.projects.inventory!!.projects.size)}
                if(mode=="noop") compose.onNodeWithTag("host-projects-list").performScrollToNode(hasText(Words.ProjectEffectUnreported.en,substring=true))
                val expected=when(mode) {
                    "create","bilingual"->"projects.create" to wire("name" to JsonPrimitive("New garden"),"slug" to JsonPrimitive("garden"),"description" to JsonPrimitive("Private draft note"),"icon" to JsonPrimitive("sprout"),"color" to JsonPrimitive("forest"),"board_slug" to JsonPrimitive("board-two"),"folders" to JsonArray(listOf(JsonPrimitive("../new"))),"primary_path" to JsonPrimitive("/srv/primary"),"use" to JsonPrimitive(false))
                    "null-create"->"projects.create" to wire("name" to JsonPrimitive("New"),"use" to JsonPrimitive(false))
                    "update"->"projects.update" to wire("id" to JsonPrimitive(id),"name" to JsonPrimitive("Reviewed garden"),"description" to JsonPrimitive(""),"icon" to JsonPrimitive(""),"board_slug" to JsonPrimitive(""))
                    "add"->"projects.add_folder" to wire("id" to JsonPrimitive(id),"path" to JsonPrimitive("../next"),"label" to JsonPrimitive("Next folder"),"is_primary" to JsonPrimitive(true))
                    "no-primary"->"projects.add_folder" to wire("id" to JsonPrimitive(id),"path" to JsonPrimitive(path),"is_primary" to JsonPrimitive(false))
                    "label-empty"->"projects.add_folder" to wire("id" to JsonPrimitive(id),"path" to JsonPrimitive(path),"label" to JsonPrimitive(""),"is_primary" to JsonPrimitive(false))
                    "primary","noop"->"projects.set_primary" to wire("id" to JsonPrimitive(id),"path" to JsonPrimitive("/srv/next"))
                    "remove"->"projects.remove_folder" to wire("id" to JsonPrimitive(id),"path" to JsonPrimitive(path))
                    "archive","restore"->"projects.archive" to wire("id" to JsonPrimitive(id),"restore" to JsonPrimitive(mode=="restore"))
                    "delete"->"projects.delete" to wire("id" to JsonPrimitive(id))
                    "active"->"projects.set_active" to wire("id" to JsonPrimitive(id))
                    "clear"->"projects.set_active" to wire("id" to JsonNull)
                    else->error("No expected mutation")
                }
                val call=f.calls.single {it["method"]!!.jsonPrimitive.content !in setOf("projects.list","projects.get")}
                assertEquals(expected.first,call["method"]!!.jsonPrimitive.content);assertEquals(expected.second,call["params"])
                assertEquals(3,f.lists.get());assertEquals(if(mode in setOf("create","bilingual","null-create","clear")) 2 else 3,f.tickets.get())
                if(mode in setOf("no-primary","label-empty")) {
                    val project=vm.state.value.projects.inventory!!.projects.single()
                    assertEquals(path,project.primaryPath);assertTrue(project.folders.single().primary)
                    assertEquals(if(mode=="no-primary") "Original folder label" else "",project.folders.single().label)
                    assertFalse((receipt.requestSummary+receipt.resultSummary).contains("Original folder label"))
                }
                if(mode in setOf("create","bilingual")) assertEquals(id,vm.state.value.projects.inventory!!.activeId)
                if(mode=="delete") {assertEquals(id,vm.state.value.projects.inventory!!.activeId);assertNull(vm.state.value.projects.inventory!!.activeProject)}
            } finally {
                f.release.countDown();compose.runOnUiThread {vm.cancel();api.updatePreferences(api.preferences.value.copy(chinese=originalPreferences.chinese,pinyin=originalPreferences.pinyin,theme=originalPreferences.theme))}
                compose.waitUntil(10000) {api.preferences.value.chinese==originalPreferences.chinese && api.preferences.value.pinyin==originalPreferences.pinyin && api.preferences.value.theme==originalPreferences.theme}
            }
        }
    }
    @Test fun createReviewsEveryPrivateFieldAndLeavesActivationSeparate()=exercise("create")
    @Test fun updateDistinguishesOmittedFieldsEmptyDescriptionAndClearValues()=exercise("update")
    @Test fun omittedFolderLabelStaysPrivateAndUnmarkedPrimaryCanBeChosenByHost()=exercise("no-primary")
    @Test fun explicitlyEmptyFolderLabelIsSentAndSavedAsEmpty()=exercise("label-empty")
    @Test fun addFolderSendsLiteralHostPathLabelAndPrimaryChoice()=exercise("add")
    @Test fun detachPrimaryObservesHostMetadataReassignment()=exercise("remove")
    @Test fun primarySelectionUsesExactAttachedPath()=exercise("primary")
    @Test fun archiveDoesNotInventAnActivePointerClear()=exercise("archive")
    @Test fun restoreUsesTheArchiveRestoreContract()=exercise("restore")
    @Test fun deleteLeavesAnUnmatchedActivePointerVisible()=exercise("delete")
    @Test fun activationDoesNotSendConversationOrCwdArguments()=exercise("active")
    @Test fun clearHandlesAnOrphanPointerAndOptionalNullResult()=exercise("clear")
    @Test fun noOpPrimaryReplyIsNeverPresentedAsAConfirmedChange()=exercise("noop")
    @Test fun optionalMissingCreateProjectDoesNotInventCreation()=exercise("null-create")
    @Test fun cancelDiscardsPrivateDraftAndRequiresFreshUncheckedFields()=exercise("cancel")
    @Test fun changedProfileDropsReviewAndRejectsItsOldFrozenRequest()=exercise("stale")
    @Test fun unrelatedInstancePendingKeepsItsGuardWithoutClaimingAProjectChange()=exercise("pending-instance")
    @Test fun sharedClaimDisposesReviewAndBlocksAnotherWrite()=exercise("pending")
    @Test fun lostReplyAllowsReadButNoReplayAndNeedsExactLocalReview()=exercise("lost")
    @Test fun cancelledSendKeepsUnknownAndCannotPublishALateResult()=exercise("held")
    @Test fun hostHomeChangeBeforeSendMakesNoMutation()=exercise("before-home")
    @Test fun knownAcknowledgementSurvivesAChangedHomeDuringReadback()=exercise("after-home")
    @Test fun canonicalIdSlugCollisionDisablesActionsWithAReason()=exercise("collision")
    @Test fun freshDetailDifferenceRequiresFullInventoryRefresh()=exercise("detail-changed")
    @Test fun bilingualCreateScrollsThroughExactValuesAndFreshGrant()=exercise("bilingual")
}
