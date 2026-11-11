package org.hermesmobile.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic local state only: no downloads, real model load, credentials or server writes. */
class LocalAiFlowTest {
    @get:Rule val compose = createComposeRule()
    private var downloads = 0; private var imports = 0; private var sends = 0; private var stops = 0
    private var pending by mutableStateOf(false)
    private var state by mutableStateOf(LocalAiState())
    private val sha = "a".repeat(64)
    private val model = InstalledLocalModel(sha, GgufModelMetadata("Fixture", "lfm3", 2048, "fixture", 1024))
    private fun show(supported: Boolean = true) {
        val actions = LocalAiActions({sends++}, {stops++}, {}, {_, _ ->}, {}, {}, {downloads++}, {},
            {imports++; pending = true}, {pending = false}, {pending = false}, {}, {}, {})
        compose.setContent {
            HermesAppTheme(AppPreferences(chinese = false, pinyin = false)) {
                LocalAiScreen(state, supported, "Fresh local draft", {}, pending, "Separate model licence fixture", actions) {}
            }
        }
    }
    @Test fun noLoadDisablesSendAndSetupGuideWorksWithoutModel() {
        show(false)
        compose.onNodeWithTag("phone-send").assertIsNotEnabled()
        compose.onNodeWithTag("phone-setup-guide").performClick()
        compose.onNodeWithTag("setup-guide").assertExists()
        compose.onNodeWithText(Words.SetupHost.en).assertExists()
        assertEquals(0, sends); assertEquals(0, downloads); assertEquals(0, imports)
    }
    @Test fun downloadWaitsForSeparateLicenceConsent() {
        show(); compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-download-review").performScrollTo().performClick()
        compose.onNodeWithTag("phone-licence-confirm").assertIsNotEnabled()
        assertEquals(0, downloads)
        compose.onNodeWithTag("phone-licence-consent").performScrollTo().performClick()
        compose.onNodeWithTag("phone-licence-confirm").performClick()
        assertEquals(1, downloads); assertEquals(0, sends)
    }
    @Test fun importIsExplicitAndCancelDoesNotDownload() {
        show(); compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-import").performScrollTo().performClick()
        compose.onNodeWithText(Words.PhoneImportLicence.en).assertExists()
        compose.onNodeWithTag("phone-licence-confirm").assertIsNotEnabled()
        compose.onNodeWithText(Words.Cancel.en).performClick()
        assertFalse(pending); assertEquals(1, imports); assertEquals(0, downloads)
    }
    @Test fun wrongModelBlocksSendAndGenerationExposesStop() {
        state = LocalAiState(loaded = model, chat = LocalConversation(modelSha256 = "b".repeat(64), updatedAt = 1))
        show(); compose.onNodeWithTag("phone-send").assertIsNotEnabled()
        compose.onNodeWithText(Words.PhoneMismatch.en).assertExists()
        compose.runOnIdle {state = LocalAiState(loaded = model, busy = true, status = "generating")}
        compose.onNodeWithTag("phone-stop").performClick()
        assertEquals(1, stops); assertEquals(0, sends)
    }
    @Test fun completedLocalReplyRequiresReviewBeforeClipboardCopy() {
        state = LocalAiState(loaded = model, chat = LocalConversation(modelSha256 = sha, updatedAt = 1,
            exchanges = listOf(LocalExchange("Question", "Local advice", LocalReplyState.COMPLETE))))
        show()
        compose.onNodeWithText(Words.PhoneReview.en).performScrollTo().performClick()
        compose.onNodeWithTag("phone-handoff-draft").assertTextContains("Local advice")
        compose.onNodeWithText(Words.PhoneReviewHelp.en).assertExists()
        compose.onNodeWithText(Words.Cancel.en).performClick()
        assertEquals(0, sends); assertEquals(0, downloads)
    }
}
