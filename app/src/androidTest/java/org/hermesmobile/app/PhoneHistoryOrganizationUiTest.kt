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
    private data class ToggleFrame(val viewport:FocusedDialogViewport,val control:androidx.compose.ui.geometry.Rect,val caption:android.graphics.Rect)
    /** One real input after coherent/stable IME and complete native/Compose target observations. */
    private fun toggleOnce(tag:String,word:Words,action:NativeDialogTapAction,bilingual:Boolean=false,
        filterChip:Boolean=false) {
        fun unselected() = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().let {
            if(filterChip) it.assertIsNotSelected() else it.assertIsToggleable().assertIsOff()
        }
        val keyboardRequired = if (filterChip) null else true
        val started=android.os.SystemClock.uptimeMillis();val deadline=started+5000
        val caption=word.localized(AppPreferences(chinese=bilingual,pinyin=bilingual))
        var previousViewport:FocusedDialogViewport?=null;var viewportStableAt=started
        compose.waitUntil(5000) {
            val current=focusedDialogViewport(requireKeyboard=false)
            val now=android.os.SystemClock.uptimeMillis()
            if(current==null || (keyboardRequired!=null && current.keyboardVisible!=keyboardRequired) || current!=previousViewport) {
                previousViewport=current;viewportStableAt=now;false
            } else now-viewportStableAt>=250 && now<deadline
        }
        compose.onNodeWithTag(tag).performScrollTo()
        unselected()
        var previous:ToggleFrame?=null;var stableAt=android.os.SystemClock.uptimeMillis();var ready:ToggleFrame?=null
        fun observe():ToggleFrame? {
            val viewport=focusedDialogViewport(requireKeyboard=false) ?: return null
            if(keyboardRequired!=null && viewport.keyboardVisible!=keyboardRequired) return null
            val node=unselected()
            val bounds=node.fetchSemanticsNode().boundsInWindow
            val native=focusedDialogCaptionScreenBounds(caption,caption)
            val fresh=focusedDialogViewport(requireKeyboard=false) ?: return null
            if(viewport!=fresh || node.fetchSemanticsNode().boundsInWindow!=bounds) return null
            if(bounds.left+fresh.screenOffsetX<fresh.visible.left || bounds.right+fresh.screenOffsetX>fresh.visible.right ||
                bounds.top+fresh.screenOffsetY<fresh.visible.top || bounds.bottom+fresh.screenOffsetY>fresh.visible.bottom || !fresh.visible.contains(native)) return null
            return ToggleFrame(fresh,bounds,native)
        }
        compose.waitUntil((deadline-android.os.SystemClock.uptimeMillis()).coerceAtLeast(1)) {
            if(android.os.SystemClock.uptimeMillis()>=deadline) return@waitUntil false
            val current=observe();val now=android.os.SystemClock.uptimeMillis()
            if(current==null || current!=previous) {previous=current;stableAt=now;false}
            else if(now-stableAt<250) false
            else {
                val fresh=observe()
                if(fresh==null || fresh!=current) {previous=fresh;stableAt=android.os.SystemClock.uptimeMillis();false}
                else {ready=fresh;true}
            }
        }
        val checked=checkNotNull(ready);check(android.os.SystemClock.uptimeMillis()<deadline)
        val center=checked.control.center
        tapFocusedDialogWindowPoint(center.x,center.y,action)
        // No second input: a missed or double toggle fails here and at exact saved-value checks.
        if(filterChip) compose.onNodeWithTag(tag).assertIsSelected() else compose.onNodeWithTag(tag).assertIsOn()
    }
    @Test fun cancellingAnEditedOrganizationReviewPublishesNoLabelsOrGeneration() {
        show();open();compose.onNodeWithTag("phone-history-title").performScrollTo().performClick().performTextInput("Orchard 你好")
        toggleOnce("phone-history-pin",Words.PhoneHistoryPinned,NativeDialogTapAction.PhoneHistoryPin)
        toggleOnce("phone-history-archive",Words.PhoneHistoryArchived,NativeDialogTapAction.PhoneHistoryArchive)
        compose.onNodeWithTag("phone-history-organization-cancel").assertIsDisplayed().performClick()
        assertTrue(saves.isEmpty());assertTrue(searches.isEmpty());assertEquals(0,sends);assertEquals(listOf(first),state.history)
    }
    @Test fun savingDispatchesOneExactReviewedTitlePinArchiveAndModelIdentity() {
        show();open();compose.onNodeWithTag("phone-history-title").performScrollTo().performClick().performTextInput("  Quiet orchard 🌱  ")
        toggleOnce("phone-history-pin",Words.PhoneHistoryPinned,NativeDialogTapAction.PhoneHistoryPin)
        toggleOnce("phone-history-archive",Words.PhoneHistoryArchived,NativeDialogTapAction.PhoneHistoryArchive)
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
        toggleOnce("phone-history-view-ALL",Words.PhoneHistoryAll,NativeDialogTapAction.PhoneHistorySearchAll,filterChip=true)
        assertTrue(searches.isEmpty());compose.onNodeWithTag("phone-history-search-apply").assertIsDisplayed().performClick()
        assertEquals(1,searches.size);assertEquals(LocalHistoryView.ALL,searches.single().view);assertEquals(0,sends)
    }
    @Test fun bilingualLargeFontOrganizationHasLabeledTogglesAndCompleteActionsAboveTheActualIme() {
        show(bilingual=true);open()
        compose.onNodeWithTag("phone-history-pin").performScrollTo().assertIsToggleable()
        compose.onNodeWithTag("phone-history-archive").performScrollTo().assertIsToggleable()
        compose.onNodeWithTag("phone-history-title").performScrollTo().performClick().performTextInput("花园 Garden")
        compose.waitUntil(5000) {focusedDialogKeyboardVisible()}
        toggleOnce("phone-history-pin",Words.PhoneHistoryPinned,NativeDialogTapAction.PhoneHistoryPin,true)
        toggleOnce("phone-history-archive",Words.PhoneHistoryArchived,NativeDialogTapAction.PhoneHistoryArchive,true)
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
            compose.onNodeWithTag("phone-history").performScrollToNode(hasText(words[index].en))
            compose.onNodeWithText(words[index].en).assertIsDisplayed()
        }
        assertTrue(saves.isEmpty());assertTrue(searches.isEmpty());assertEquals(0,sends);assertEquals(rows,state.history)
    }
}
