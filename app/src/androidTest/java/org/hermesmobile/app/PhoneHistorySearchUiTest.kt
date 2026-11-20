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

/** Synthetic summaries and observed callbacks. No model, file write or network client. */
class PhoneHistorySearchUiTest {
    @get:Rule val compose = createComposeRule()
    private val first = LocalConversationSummary("00000000-0000-0000-0000-000000000001", "a".repeat(64), 2, "Garden title", LocalReplyState.COMPLETE)
    private val second = LocalConversationSummary("00000000-0000-0000-0000-000000000002", "b".repeat(64), 1, "Forest title", LocalReplyState.COMPLETE)
    private var state by mutableStateOf(LocalAiState(history = listOf(first, second)))
    private val searches = mutableListOf<LocalHistoryQuery>()
    private val opens = mutableListOf<String>()
    private var sends = 0
    private var clears = 0
    private fun show(bilingual: Boolean = false) {
        val actions = LocalAiActions({sends++}, {}, {}, {_, _ ->}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            searchHistory = {searches.add(it)}, clearHistorySearch = {clears++; state = state.copy(historyQuery = null, historyMatches = emptyList())})
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if(bilingual) 1.3f else density.fontScale)) {
                HermesAppTheme(AppPreferences(chinese = bilingual, pinyin = bilingual)) {
                    Column(Modifier.fillMaxSize()) {PhoneHistory(state, actions, {opens.add(it)}, {})}
                }
            }
        }
    }
    @Test fun cancellingTheReviewDoesNotSearchGenerateOrChangeTheInventory() {
        show(); compose.onNodeWithTag("phone-history-search-open").performClick()
        compose.onNodeWithTag("phone-history-search-text").performScrollTo().performTextInput("Garden 你好")
        assertTrue(searches.isEmpty()); compose.onNodeWithTag("phone-history-search-cancel").assertIsDisplayed().performClick()
        assertTrue(searches.isEmpty()); assertTrue(opens.isEmpty()); assertEquals(0, sends)
        assertEquals(listOf(first, second), state.history); assertNull(state.historyQuery)
    }
    @Test fun applyingLiteralTextModelAndPresetDatesDispatchesOneExactReadOnlyQuery() {
        show(); compose.onNodeWithTag("phone-history-search-open").performClick()
        compose.onNodeWithTag("phone-history-search-text").performScrollTo().performTextInput("[花园.*]")
        compose.onNodeWithTag("phone-history-model-menu").performScrollTo().performClick()
        compose.onNodeWithTag("phone-history-model-${second.modelSha256}").performClick()
        val dateMenu = compose.onNodeWithTag("phone-history-date-menu")
        dateMenu.performScrollTo().assertIsDisplayed().assertTextEquals(Words.PhoneHistoryAnyDate.en).performClick()
        compose.onNodeWithTag("phone-history-period-WEEK").assertIsDisplayed().performClick()
        dateMenu.assertTextEquals(Words.PhoneHistoryLastWeek.en)
        assertTrue(searches.isEmpty()); val before = System.currentTimeMillis()
        compose.onNodeWithTag("phone-history-search-apply").assertIsDisplayed().assertIsEnabled().performClick()
        val after = System.currentTimeMillis(); assertEquals(1, searches.size)
        val query = searches.single(); assertEquals("[花园.*]", query.text); assertEquals(second.modelSha256, query.modelSha256)
        assertTrue(query.untilExclusive!! in (before + 1)..(after + 1))
        assertEquals(query.untilExclusive!! - 1 - java.util.concurrent.TimeUnit.DAYS.toMillis(7), query.fromInclusive!!)
        compose.onNodeWithTag("phone-history-search-apply").assertDoesNotExist()
        assertEquals(0, sends); assertTrue(opens.isEmpty()); assertEquals(listOf(first, second), state.history)
    }
    @Test fun noMatchesHaveAnExplanationAndClearRestoresTheUnfilteredList() {
        state = state.copy(historyQuery = LocalHistoryQuery("absent"), historyMatches = emptyList()); show()
        compose.onNodeWithTag("phone-history-empty").assertTextEquals(Words.PhoneHistoryNoMatches.en)
        compose.onNodeWithTag("phone-history-open-${first.id}").assertDoesNotExist()
        compose.onNodeWithTag("phone-history-search-clear").assertIsDisplayed().performClick()
        assertEquals(1, clears); assertNull(state.historyQuery)
        compose.onNodeWithTag("phone-history").performScrollToNode(hasTestTag("phone-history-open-${first.id}"))
        compose.onNodeWithTag("phone-history-open-${first.id}").assertIsDisplayed().performClick()
        assertEquals(listOf(first.id), opens); assertEquals(0, sends); assertTrue(searches.isEmpty())
    }
    @Test fun bilingualLargeFontReviewKeepsBothActionsAboveTheActualKeyboard() {
        show(bilingual = true); compose.onNodeWithTag("phone-history-search-open").performClick()
        compose.onNodeWithTag("phone-history-search-text").performScrollTo().performClick().performTextInput("你好 Garden")
        var stableViewport: FocusedDialogViewport? = null
        var stableSince = android.os.SystemClock.uptimeMillis()
        compose.waitUntil(5_000) {
            val current = focusedDialogViewport(requireKeyboard = false)?.takeIf { it.keyboardVisible }
            val now = android.os.SystemClock.uptimeMillis()
            if (current != stableViewport) {
                stableViewport = current
                stableSince = now
            }
            current != null && now - stableSince >= 250
        }
        val viewport = checkNotNull(stableViewport)
        for (tag in listOf("phone-history-search-apply", "phone-history-search-cancel")) {
            val node = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
            assertTrue("Complete action must remain above the native IME", node.bottom + viewport.screenOffsetY <= viewport.visible.bottom)
            assertTrue(node.top + viewport.screenOffsetY >= viewport.visible.top)
        }
        compose.onNodeWithTag("phone-history-search-cancel").performClick()
        assertTrue(searches.isEmpty()); assertEquals(0, sends)
    }
}
