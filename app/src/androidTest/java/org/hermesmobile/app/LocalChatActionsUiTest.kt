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
    private fun show(bilingual: Boolean = false,
        preview: (suspend (LocalConversation?, String, String, Int?) -> LocalPromptPreview)? = null,
        copyDraft: (() -> Unit)? = null,
        continuation: ((LocalConversation, List<Int>, String, Boolean) -> Unit)? = null) {
        val actions = LocalAiActions({sends++}, {}, {}, {_, _ ->}, {}, {}, {}, {}, {}, {}, {},
            {opened.add(it)}, {}, {}, branch = {source, index, kind, edited -> requests.add(Request(source, index, kind, edited))},
            previewPrompt = preview, newChatWithDraft = copyDraft, continueWithSelectedTurns = continuation)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (bilingual) 1.3f else density.fontScale)) {
                HermesAppTheme(AppPreferences(chinese = bilingual, pinyin = bilingual)) {
                    LocalAiScreen(state, true, draft, {draft = it}, false, "Fixture licence", actions, {})
                }
            }
        }
    }
    @Test fun contextMeterReportsReservedOutputAndKeepsAnOverflowingDraft() {
        var freshDraft: String? = null
        show(preview = { chat, modelHash, question, index ->
            assertEquals(original, chat)
            assertEquals(hash, modelHash)
            assertEquals("Unsent parent draft", question)
            assertNull(index)
            LocalPromptPreview.Exact(LocalPromptBudget(1792, 2048, 384))
        }, copyDraft = { freshDraft = draft })
        compose.waitUntil(5000) { compose.onAllNodesWithTag("phone-prompt-overflow").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("phone-send").assertIsNotEnabled()
        compose.onNodeWithTag("phone-prompt-budget").performClick()
        compose.onNodeWithTag("phone-prompt-output-room").assertTextEquals("256 tokens available for output. This request reserves 384.")
        compose.onNodeWithText(Words.Close.en).performClick()
        compose.onNodeWithTag("phone-fresh-draft").performClick()
        assertEquals("Unsent parent draft", freshDraft)
        assertEquals("Unsent parent draft", draft)
        assertEquals(0, sends)
    }

    @Test fun reviewedSelectedTurnsStayInSourceOrderAndNeverSendTheDraft() {
        val source = original.copy(exchanges = original.exchanges + listOf(
            LocalExchange("Interrupted turn", "Partial", LocalReplyState.INTERRUPTED),
            LocalExchange("Later turn", "Later reply", LocalReplyState.TOKEN_LIMIT)))
        state = state.copy(chat = source, loaded = null)
        data class Continuation(val chat: LocalConversation, val indices: List<Int>, val draft: String, val copyDraft: Boolean)
        val confirmed = mutableListOf<Continuation>()
        show(continuation = { chat, indices, draft, copy ->
            confirmed.add(Continuation(chat, indices, draft, copy))
        })
        compose.onNodeWithTag("phone-select-context").performClick()
        compose.onNodeWithTag("phone-context-confirm").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("phone-context-selection").performScrollToNode(hasTestTag("phone-context-turn-2"))
        compose.onNodeWithTag("phone-context-turn-2").assertIsToggleable().performClick()
        compose.onNodeWithTag("phone-context-selection").performScrollToNode(hasTestTag("phone-context-turn-0"))
        compose.onNodeWithTag("phone-context-turn-0").assertIsToggleable().performClick()
        assertTrue(confirmed.isEmpty())
        compose.onNodeWithTag("phone-context-confirm").assertIsEnabled().performClick()
        assertEquals(listOf(Continuation(source, listOf(0, 2), "Unsent parent draft", true)), confirmed)
        assertEquals(0, sends)
        assertEquals(source, state.chat)
        assertEquals("Unsent parent draft", draft)
    }

    @Test fun changedDraftCannotBeCopiedFromAnEarlierReviewAndCancelDoesNotCreateAChat() {
        var confirms = 0
        show(bilingual = true, continuation = { _, _, _, _ -> confirms++ })
        val earlier = compose.onNodeWithTag("phone-select-context").assertIsDisplayed()
        val fresh = compose.onNodeWithTag("phone-new-chat").assertIsDisplayed()
        assertEquals(fresh.fetchSemanticsNode().boundsInRoot.height, earlier.fetchSemanticsNode().boundsInRoot.height, 1f)
        earlier.performClick()
        compose.onNodeWithTag("phone-context-selection").performScrollToNode(hasTestTag("phone-context-turn-0"))
        compose.onNodeWithTag("phone-context-turn-0").performClick()
        compose.onNodeWithTag("phone-context-confirm").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { draft = "Updated while the review was open" }
        compose.onNodeWithTag("phone-context-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("phone-context-cancel").assertIsDisplayed().performClick()
        assertEquals(0, confirms)
        assertEquals(0, sends)
        assertEquals("Updated while the review was open", draft)
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
        show(); compose.onNodeWithTag("phone-open-parent").performScrollTo().performClick()
        assertEquals(listOf(original.id), opened); assertTrue(requests.isEmpty()); assertEquals(0, sends)
    }

    @Test fun olderRepliesShowUnknownSettingsWithoutInventingValues() {
        show(); compose.onNodeWithTag("phone-generation-0").performScrollTo().performClick()
        compose.onNodeWithTag("phone-generation-unknown").assertExists()
        compose.onNodeWithTag("phone-generation-context").assertDoesNotExist()
        compose.onNodeWithTag("phone-runtime-source").assertDoesNotExist()
        compose.onNodeWithTag("phone-generation-close").assertIsDisplayed().performClick()
        assertTrue(requests.isEmpty()); assertEquals(0, sends)
    }
    @Test fun recordedSettingsRemainReadableWithoutLoadingAModel() {
        val recorded = LocalGenerationSettings(1024, 2, 384, LocalRuntimePolicy("b".repeat(64), LocalSamplingPolicy.GREEDY_MODEL_METADATA_V1))
        state = state.copy(loaded = null, chat = original.copy(exchanges = listOf(original.exchanges.single().copy(generation = recorded))))
        show(bilingual = true)
        // The unloaded-model notice can fill the viewport; materialize this message through the list first.
        compose.onNodeWithTag("phone-messages").performScrollToNode(hasTestTag("phone-generation-0"))
        compose.onNodeWithTag("phone-generation-0").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("phone-generation-context").performScrollTo().assertIsDisplayed().assertTextEquals(
            listOf(Words.PhoneGenerationContext.en, Words.PhoneGenerationContext.zh, Words.PhoneGenerationContext.pin)
                .joinToString("\n").replace("{number}", "1024"))
        compose.onNodeWithTag("phone-runtime-source").performScrollTo().assertIsDisplayed().assertTextEquals("b".repeat(64))
        compose.onNodeWithTag("phone-generation-close").assertIsDisplayed().performClick()
        assertNull(state.loaded); assertTrue(requests.isEmpty()); assertEquals(0, sends)
    }
    @Test fun bilingualBranchOriginScrollsWithHistoryAndLeavesTheComposerReachable() {
        val child = original.copy(id = java.util.UUID.randomUUID().toString(), origin = LocalBranchOrigin(original.id, 1, 0, LocalBranchKind.FORK, "b".repeat(64)))
        state = state.copy(loaded = null, chat = child, history = listOf(LocalConversationSummary(original.id, hash, 1, "Original", LocalReplyState.COMPLETE)))
        show(bilingual = true)
        compose.onNodeWithTag("phone-draft").assertIsDisplayed(); compose.onNodeWithTag("phone-send").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("phone-open-parent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-messages").performScrollToNode(hasTestTag("phone-branch-0"))
        compose.onNodeWithTag("phone-branch-0").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("phone-branch-cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("phone-draft").assertIsDisplayed(); assertTrue(requests.isEmpty()); assertEquals(0, sends)
    }
}
