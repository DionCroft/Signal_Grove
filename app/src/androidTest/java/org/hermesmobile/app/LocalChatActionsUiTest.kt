package org.hermesmobile.app

import androidx.compose.runtime.*
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

/** Reviewed UI dispatch only. Native generation and actual storage have separate integration gates. */
class LocalChatActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val hash = "a".repeat(64)
    private val model = InstalledLocalModel(hash, GgufModelMetadata("Fixture", "lfm3", 2048, "fixture", 1024))
    private val original = LocalConversation(modelSha256 = hash, updatedAt = 1,
        exchanges = listOf(LocalExchange("Original question", "Original reply", LocalReplyState.COMPLETE)))
    private var state by mutableStateOf(LocalAiState(loaded = model, chat = original))
    private var draft by mutableStateOf("Unsent parent draft")
    private data class Request(val source: LocalConversation, val index: Int, val kind: LocalBranchKind, val edited: String?)
    private val requests = mutableListOf<Request>()
    private val opened = mutableListOf<String>()
    private var sends = 0
    private fun show(bilingual: Boolean = false) {
        val actions = LocalAiActions({sends++}, {}, {}, {_, _ ->}, {}, {}, {}, {}, {}, {}, {},
            {opened.add(it)}, {}, {}, branch = {source, index, kind, edited -> requests.add(Request(source, index, kind, edited))})
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (bilingual) 1.3f else density.fontScale)) {
                HermesAppTheme(AppPreferences(chinese = bilingual, pinyin = bilingual)) {
                    LocalAiScreen(state, true, draft, {draft = it}, false, "Fixture licence", actions, {})
                }
            }
        }
    }
    @Test fun regenerationWaitsForReviewAndCancelPreservesEverything() {
        show(); compose.onNodeWithTag("phone-regenerate-0").performScrollTo().performClick()
        compose.onNodeWithTag("phone-branch-confirm").assertIsDisplayed().assertIsEnabled()
        assertTrue(requests.isEmpty()); compose.onNodeWithTag("phone-branch-cancel").performClick()
        assertTrue(requests.isEmpty()); assertEquals(original, state.chat); assertEquals("Unsent parent draft", draft); assertEquals(0, sends)
    }
    @Test fun regenerationConfirmDispatchesOnlyTheReviewedMessageOnce() {
        show(); compose.onNodeWithTag("phone-regenerate-0").performScrollTo().performClick()
        compose.onNodeWithTag("phone-branch-confirm").performClick()
        compose.onNodeWithTag("phone-branch-confirm").assertDoesNotExist()
        assertEquals(listOf(Request(original, 0, LocalBranchKind.REGENERATE, null)), requests)
        assertEquals(original, state.chat); assertEquals("Unsent parent draft", draft); assertEquals(0, sends)
    }
    @Test fun editConfirmsTheExactChangedQuestionAndRejectsBlankText() {
        show(); compose.onNodeWithTag("phone-edit-0").performScrollTo().performClick()
        compose.onNodeWithTag("phone-edit-question").performScrollTo().performTextClearance()
        compose.onNodeWithTag("phone-branch-confirm").assertIsNotEnabled(); assertTrue(requests.isEmpty())
        compose.onNodeWithTag("phone-edit-question").performTextInput("Revised 你好 🌱")
        compose.onNodeWithTag("phone-branch-confirm").performClick()
        assertEquals(listOf(Request(original, 0, LocalBranchKind.EDIT, "Revised 你好 🌱")), requests)
        assertEquals(original, state.chat); assertEquals("Unsent parent draft", draft); assertEquals(0, sends)
    }
    @Test fun browsingBranchCanBeReviewedWithWeightsUnloaded() {
        state = state.copy(loaded = null); show()
        compose.onNodeWithTag("phone-edit-0").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("phone-regenerate-0").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("phone-branch-0").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(Words.PhoneForkReviewHelp.en).assertExists()
        compose.onNodeWithTag("phone-branch-confirm").performClick()
        assertEquals(listOf(Request(original, 0, LocalBranchKind.FORK, null)), requests); assertEquals(0, sends)
    }
    @Test fun reviewRefusesAChangedSourceAndDoesNotDispatch() {
        show(); compose.onNodeWithTag("phone-regenerate-0").performScrollTo().performClick()
        compose.runOnIdle {state = state.copy(chat = original.copy(updatedAt = 2))}
        compose.onNodeWithText(Words.PhoneBranchChanged.en).assertExists()
        compose.onNodeWithTag("phone-branch-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("phone-branch-cancel").performClick(); assertTrue(requests.isEmpty()); assertEquals(0, sends)
    }
    @Test fun wrongModelAndIncompleteRepliesCannotSilentlyForkOrRegenerate() {
        state = state.copy(loaded = model.copy(sha256 = "b".repeat(64)), chat = original.copy(
            exchanges = listOf(original.exchanges.single().copy(state = LocalReplyState.CANCELLED))))
        show(); compose.onNodeWithTag("phone-edit-0").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("phone-regenerate-0").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("phone-branch-0").performScrollTo().assertIsNotEnabled()
        assertTrue(requests.isEmpty()); assertEquals(0, sends)
    }
    @Test fun bilingualLargeFontReviewKeepsBothActionsReachable() {
        show(bilingual = true); compose.onNodeWithTag("phone-regenerate-0").performScrollTo().performClick()
        compose.onNodeWithTag("phone-branch-confirm").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("phone-branch-cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("phone-draft").assertIsDisplayed(); assertTrue(requests.isEmpty()); assertEquals(0, sends)
    }
    @Test fun parentNavigationIsReadOnlyAndUsesTheRecordedParentId() {
        val child = original.copy(id = java.util.UUID.randomUUID().toString(), origin = LocalBranchOrigin(original.id, 1, 0, LocalBranchKind.FORK, "b".repeat(64)))
        state = state.copy(chat = child, history = listOf(LocalConversationSummary(original.id, hash, 1, "Original", LocalReplyState.COMPLETE)))
        show(); compose.onNodeWithTag("phone-open-parent").performClick()
        assertEquals(listOf(original.id), opened); assertTrue(requests.isEmpty()); assertEquals(0, sends)
    }
}