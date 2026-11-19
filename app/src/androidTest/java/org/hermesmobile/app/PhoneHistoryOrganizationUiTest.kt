package org.hermesmobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual history panel/reviews with observed callbacks; no model, remote client or user-file writes. */
class PhoneHistoryOrganizationUiTest {
    @get:Rule val compose=createComposeRule()
    private val first=LocalConversationSummary("00000000-0000-0000-0000-000000000001","a".repeat(64),1,"Garden",LocalReplyState.COMPLETE)
    private var state by mutableStateOf(LocalAiState(history=listOf(first)))
    private val saves=mutableListOf<Pair<LocalConversationSummary,LocalHistoryPreferences>>()
    private val searches=mutableListOf<LocalHistoryQuery>()
    private var sends=0
    private fun show(bilingual:Boolean=false) {
        val actions=LocalAiActions({sends++},{},{},{_,_->},{},{},{},{},{},{},{},{},{},{},
            organize={summary,prefs->saves.add(summary to prefs)},
            searchHistory={q->searches.add(q);state=state.copy(historyQuery=q,historyMatches=state.history.filter {it.organization?.preferences?.archived==true})},
            clearHistorySearch={state=state.copy(historyQuery=null,historyMatches=emptyList())})
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(bilingual)1.3f else density.fontScale)) {
                HermesAppTheme(AppPreferences(chinese=bilingual,pinyin=bilingual)) {
                    Column(Modifier.fillMaxSize()) {PhoneHistory(state,actions,{}, {})}
                }
            }
        }
    }
    private fun open() {
        compose.onNodeWithTag("phone-history").performScrollToNode(hasTestTag("phone-history-organize-${first.id}"))
        compose.onNodeWithTag("phone-history-organize-${first.id}").assertIsDisplayed().performClick()
    }
    @Test fun cancellingAnEditedOrganizationReviewPublishesNoLabelsOrGeneration() {
        show();open();compose.onNodeWithTag("phone-history-title").performScrollTo().performTextInput("Orchard 你好")
        compose.onNodeWithTag("phone-history-pin").performScrollTo().performClick()
        compose.onNodeWithTag("phone-history-archive").performScrollTo().performClick()
        compose.onNodeWithTag("phone-history-organization-cancel").assertIsDisplayed().performClick()
        assertTrue(saves.isEmpty());assertTrue(searches.isEmpty());assertEquals(0,sends);assertEquals(listOf(first),state.history)
    }
    @Test fun savingDispatchesOneExactReviewedTitlePinArchiveAndModelIdentity() {
        show();open();compose.onNodeWithTag("phone-history-title").performScrollTo().performTextInput("  Quiet orchard 🌱  ")
        compose.onNodeWithTag("phone-history-pin").performScrollTo().assertIsToggleable().performClick()
        compose.onNodeWithTag("phone-history-archive").performScrollTo().assertIsToggleable().performClick()
        assertTrue(saves.isEmpty());compose.onNodeWithTag("phone-history-organization-save").assertIsEnabled().performClick()
        assertEquals(listOf(first to LocalHistoryPreferences("Quiet orchard 🌱",true,true)),saves)
        compose.onNodeWithTag("phone-history-organization-save").assertDoesNotExist();assertEquals(0,sends)
    }
    @Test fun invalidTitleStaysVisibleAndCannotBeSaved() {
        show();open();val long="x".repeat(81)
        compose.onNodeWithTag("phone-history-title").performScrollTo().performTextInput(long)
        compose.onNodeWithTag("phone-history-title").assertTextContains(long)
        compose.onNodeWithTag("phone-history-organization-save").assertIsNotEnabled()
        compose.onNodeWithTag("phone-history-organization-cancel").performClick();assertTrue(saves.isEmpty());assertEquals(0,sends)
    }
    @Test fun clearingACustomTitleKeepsTheReviewedFlagsAndUsesTheQuestionAgain() {
        val labeled=first.copy(title="Custom",organization=LocalHistoryOrganizationEntry(first.modelSha256,LocalHistoryPreferences("Custom",pinned=true)))
        state=state.copy(history=listOf(labeled));show();open()
        compose.onNodeWithTag("phone-history-title").performScrollTo().performTextClearance()
        compose.onNodeWithTag("phone-history-organization-save").assertIsEnabled().performClick()
        assertEquals(listOf(labeled to LocalHistoryPreferences(null,pinned=true)),saves);assertEquals(0,sends)
    }
    @Test fun anAllArchivedLibraryExplainsTheEmptyActiveViewAndKeepsArchivedReadsReachable() {
        val archived=first.copy(organization=LocalHistoryOrganizationEntry(first.modelSha256,LocalHistoryPreferences(archived=true)))
        state=state.copy(history=listOf(archived));show()
        compose.onNodeWithTag("phone-history-empty").assertTextEquals(Words.PhoneHistoryNoActive.en)
        compose.onNodeWithTag("phone-history").performScrollToNode(hasTestTag("phone-history-show-archived"))
        compose.onNodeWithTag("phone-history-show-archived").performClick()
        assertEquals(1,searches.size);assertEquals(LocalHistoryView.ARCHIVED,searches.single().view)
        compose.onNodeWithTag("phone-history").performScrollToNode(hasTestTag("phone-history-open-${first.id}"))
        compose.onNodeWithTag("phone-history-open-${first.id}").assertIsDisplayed();assertEquals(listOf(archived),state.history);assertEquals(0,sends)
    }
    @Test fun searchReviewOffersExplicitActiveArchivedAndAllChoices() {
        show();compose.onNodeWithTag("phone-history-search-open").performClick()
        compose.onNodeWithTag("phone-history-view-ALL").performScrollTo().performClick()
        assertTrue(searches.isEmpty());compose.onNodeWithTag("phone-history-search-apply").assertIsDisplayed().performClick()
        assertEquals(1,searches.size);assertEquals(LocalHistoryView.ALL,searches.single().view);assertEquals(0,sends)
    }
    @Test fun bilingualLargeFontOrganizationHasLabeledTogglesAndCompleteActionsAboveTheActualIme() {
        show(bilingual=true);open()
        compose.onNodeWithTag("phone-history-pin").performScrollTo().assertIsToggleable()
        compose.onNodeWithTag("phone-history-archive").performScrollTo().assertIsToggleable()
        compose.onNodeWithTag("phone-history-title").performScrollTo().performClick().performTextInput("花园 Garden")
        compose.waitUntil(5000) {focusedDialogKeyboardVisible()}
        val viewport=checkNotNull(focusedDialogViewport())
        for(tag in listOf("phone-history-organization-save","phone-history-organization-cancel")) {
            val bounds=compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.bottom+viewport.screenOffsetY<=viewport.visible.bottom)
            assertTrue(bounds.top+viewport.screenOffsetY>=viewport.visible.top)
        }
        compose.onNodeWithTag("phone-history-organization-cancel").performClick();assertTrue(saves.isEmpty());assertEquals(0,sends)
    }

    @Test fun identicalQuestionBranchesShowTheirRecordedActivityAndDistinctKindBeforeOrganization() {
        val kinds=listOf(LocalBranchKind.FORK,LocalBranchKind.EDIT,LocalBranchKind.REGENERATE)
        val words=listOf(Words.PhoneHistoryCopiedBranch,Words.PhoneHistoryEditedBranch,Words.PhoneHistoryRegeneratedBranch)
        val rows=kinds.mapIndexed {i,kind -> first.copy(id=java.util.UUID(0,(i+1).toLong()).toString(),updatedAt=(i+1)*1000L,
            origin=LocalBranchOrigin("00000000-0000-0000-0000-000000000099",0,0,kind,"b".repeat(64))) }
        state=state.copy(history=rows);show()
        val format=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.MEDIUM)
        for ((index,row) in rows.withIndex()) {
            compose.onNodeWithTag("phone-history").performScrollToNode(hasTestTag("phone-history-time-${row.id}"))
            compose.onNodeWithTag("phone-history-time-${row.id}").assertIsDisplayed().assertTextEquals(format.format(java.util.Date(row.updatedAt)))
            compose.onNodeWithText(words[index].en).assertIsDisplayed()
        }
        assertTrue(saves.isEmpty());assertTrue(searches.isEmpty());assertEquals(0,sends);assertEquals(rows,state.history)
    }
}
