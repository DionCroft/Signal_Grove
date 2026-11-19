package org.hermesmobile.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic local state only: no downloads, real model load, credentials or server writes. */
class LocalAiFlowTest {
    @get:Rule val compose = createComposeRule()
    private val downloads = mutableListOf<CuratedPhoneModel>()
    private val discarded = mutableListOf<CuratedPhoneModel>()
    private var imports = 0; private var sends = 0; private var stops = 0
    private var pending by mutableStateOf(false)
    private var state by mutableStateOf(LocalAiState())
    private val helperRequests = mutableListOf<Pair<LocalHelpTopic, Boolean>>()
    private val benchmarks = mutableListOf<String>()
    private val performanceSaves = mutableListOf<LocalModelPerformance>()
    private val requestedLoads = mutableListOf<Pair<String, Int>>()
    private val sha = "a".repeat(64)
    private val model = InstalledLocalModel(sha, GgufModelMetadata("Fixture", "lfm3", 2048, "fixture", 1024))
    private fun show(supported: Boolean = true, bilingual: Boolean = false, copied: Boolean = false) {
        val actions = LocalAiActions({sends++}, {
                stops++
                if (state.status == "benchmarking") state = state.copy(busy = false, status = "idle")
            }, {}, { model, context -> requestedLoads.add(model.sha256 to context) },
            {}, {}, { model -> downloads.add(model) }, { model -> discarded.add(model) }, {imports++; pending = true}, {pending = false}, {pending = false}, {}, {}, {},
            help = {topic, share -> helperRequests.add(topic to share)}, saveModelPerformance = { item, settings ->
                performanceSaves.add(settings)
                state = state.copy(modelPerformance = state.modelPerformance + (item.sha256 to settings))
            }, benchmark = { modelSha256 ->
                benchmarks.add(modelSha256)
                state = state.copy(busy = true, status = "benchmarking")
            })
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (bilingual) 1.3f else density.fontScale)) {
            HermesAppTheme(AppPreferences(chinese = bilingual, pinyin = bilingual)) {
                LocalAiScreen(state, supported, "Fresh local draft", {}, pending, actions, {},
                    LocalHelpObservations(100, false, LocalAddressKind.HTTPS, true, state.loaded != null), if(copied) "received" else "")
            }
            }
        }
    }
    @Test fun modelPerformanceReviewsAndSavesTheNextLoadWithoutChangingTheActiveModel() {
        state = LocalAiState(models = listOf(model), loaded = model, loadedSettings = LocalGenerationSettings(2048, 3, 384))
        show(bilingual = true)
        compose.onNodeWithTag("phone-tab-1").performClick()
        val settings = compose.onNodeWithTag("phone-model-settings-${sha.take(8)}")
        settings.performScrollTo().performClick()
        compose.onNodeWithTag("phone-performance-preset-FAST").performScrollTo().performClick()
        compose.onNodeWithTag("phone-performance-cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(performanceSaves.isEmpty()); assertTrue(requestedLoads.isEmpty()) }

        settings.performScrollTo().performClick()
        compose.onNodeWithTag("phone-performance-preset-BATTERY_SAVER").performScrollTo().performClick()
        compose.onNodeWithTag("phone-performance-preview").assertTextContains("thread cap 1", substring = true)
        compose.onNodeWithTag("phone-performance-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("phone-performance-output-192").performScrollTo().performClick()
        compose.onNodeWithTag("phone-performance-save").assertIsDisplayed().performClick()
        compose.onNodeWithTag("phone-model-active-limits").performScrollTo().assertTextContains("CPU threads 3", substring = true)
        compose.onNodeWithTag("phone-model-next-limits").performScrollTo().assertTextContains("192 reply tokens", substring = true)
        compose.runOnIdle { assertEquals(listOf(LocalModelPerformance(1024, 1, 192)), performanceSaves); assertTrue(requestedLoads.isEmpty()) }

        compose.onNodeWithTag("phone-load-${sha.take(8)}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(sha to 1024), requestedLoads) }
    }

    @Test fun performanceDetailsWaitForExplicitBenchmarkAndKeepStopReachable() {
        state = LocalAiState(models = listOf(model), loaded = model,
            loadedSettings = LocalGenerationSettings(2048, 3, 384),
            performance = LocalPerformanceDiagnostics(loadPssKiB = 262_144))
        show(bilingual = true)
        compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-diagnostics-aaaaaaaa").performScrollTo().performClick()
        compose.onNodeWithTag("phone-diagnostics-memory").performScrollTo().assertTextContains("256", substring = true)
        compose.runOnIdle { assertTrue(benchmarks.isEmpty()); assertEquals(0, sends) }
        compose.onNodeWithTag("phone-benchmark-run").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(sha), benchmarks) }
        compose.onNodeWithTag("phone-benchmark-stop").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, stops); assertEquals(0, sends) }
        compose.onNodeWithTag("phone-diagnostics-close").assertIsDisplayed().performClick()
    }

    @Test fun copiedPerformanceReportContainsOnlySettingsAndMeasurements() {
        val custom = model.copy(metadata = model.metadata.copy(name = "Private model label"))
        state = LocalAiState(models = listOf(custom), loaded = custom,
            loadedSettings = LocalGenerationSettings(2048, 3, 384),
            chat = LocalConversation(modelSha256 = sha, updatedAt = 1, exchanges = listOf(
                LocalExchange("Private question marker", "Private answer marker", LocalReplyState.COMPLETE))))
        show(bilingual = true)
        compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-diagnostics-aaaaaaaa").performScrollTo().performClick()
        val copy = compose.onNodeWithTag("phone-diagnostics-copy")
        copy.assertDoesNotExist()
        val clipboard = compose.runOnIdle {
            InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(ClipboardManager::class.java)
        }
        val original = compose.runOnIdle { clipboard.primaryClip }
        val sample = LocalGenerationMeasurement(20, 1_000_000_000, 2_000_000_000)
        compose.runOnIdle {
            state = state.copy(performance = LocalPerformanceDiagnostics(loadPssKiB = 262_144,
                benchmark = LocalBenchmarkResult(LocalBenchmarkEnd.COMPLETE, sample)))
            assertEquals(original?.getItemAt(0)?.text, clipboard.primaryClip?.getItemAt(0)?.text)
        }
        try {
            copy.performScrollTo().assertIsDisplayed().performClick()
            copy.assertTextContains(Words.Copied.en, substring = true)
            compose.runOnIdle {
                val clip = checkNotNull(clipboard.primaryClip)
                val value = clip.getItemAt(0).text.toString()
                assertTrue(value.contains("2048 context") && value.contains("CPU threads 3"))
                assertTrue(value.contains("256 MiB") && value.contains("Time to first visible text · 2 s"))
                assertTrue(value.contains("20 tokens/s (20 sampled)") && value.contains("Completed on phone"))
                assertTrue(value.contains("Rate excludes prompt processing."))
                assertTrue(listOf("Private model label", "Private question marker", "Private answer marker", sha)
                    .none(value::contains))
                assertTrue(benchmarks.isEmpty()); assertEquals(0, sends)
            }
        } finally {
            compose.runOnIdle {
                original?.let(clipboard::setPrimaryClip) ?: if (Build.VERSION.SDK_INT >= 28) {
                    clipboard.clearPrimaryClip()
                } else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    @Test fun noLoadDisablesSendAndSetupGuideWorksWithoutModel() {
        show(false)
        compose.onNodeWithTag("phone-send").assertIsNotEnabled()
        compose.onNodeWithTag("phone-setup-guide").performClick()
        compose.onNodeWithTag("setup-guide").assertExists()
        compose.onNodeWithText(Words.SetupHost.en).assertExists()
        assertEquals(0, sends); assertTrue(downloads.isEmpty()); assertEquals(0, imports)
    }
    @Test fun downloadWaitsForSeparateLicenceConsent() {
        show(); compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-catalogue-open").performScrollTo().performClick()
        compose.onNodeWithTag("phone-download-review-LFM3").performScrollTo().performClick()
        compose.onNodeWithTag("phone-licence-confirm").assertIsNotEnabled()
        assertTrue(downloads.isEmpty())
        compose.onNodeWithTag("phone-licence-consent").assertIsDisplayed().assertIsOff().performClick()
        compose.onNodeWithTag("phone-licence-confirm").performClick()
        assertEquals(listOf(CuratedPhoneModel.LFM3), downloads); assertEquals(0, sends)
    }
    @Test fun curatedModelReviewDispatchesOnlyTheSelectedPin() {
        val qwen = CuratedPhoneModel.QWEN
        state = state.copy(downloadPartials = mapOf(qwen to 2L * 1024 * 1024))
        show(bilingual = true)
        compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-catalogue-open").performScrollTo().performClick()
        compose.onNodeWithTag("phone-catalogue-LFM3").performScrollTo().assertTextContains(CuratedPhoneModel.LFM3.displayName)
        compose.onNodeWithTag("phone-catalogue-QWEN").performScrollTo().assertTextContains(qwen.displayName)
        compose.onNodeWithTag("phone-discard-LFM3").assertDoesNotExist()
        compose.onNodeWithTag("phone-discard-QWEN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-download-review-QWEN").performScrollTo().performClick()
        compose.onNodeWithTag("phone-licence-model").assertTextEquals(qwen.displayName)
        val review = compose.onNodeWithTag("phone-licence-source")
        listOf(qwen.repository, qwen.filename, qwen.revision, qwen.sha256, qwen.licence).forEach {
            review.assertTextContains(it, substring = true)
        }
        compose.onNodeWithTag("phone-licence-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("phone-licence-cancel").performClick()
        compose.runOnIdle { assertTrue(downloads.isEmpty()); assertTrue(discarded.isEmpty()); assertTrue(requestedLoads.isEmpty()) }

        compose.onNodeWithTag("phone-download-review-QWEN").performScrollTo().performClick()
        compose.onNodeWithTag("phone-licence-consent").assertIsDisplayed().assertIsOff().performClick()
        compose.onNodeWithTag("phone-licence-confirm").performClick()
        compose.runOnIdle {
            assertEquals(listOf(qwen), downloads)
            assertTrue(requestedLoads.isEmpty())
            assertEquals(0, sends)
        }
    }

    @Test fun licenceConsentAndActionsRemainVisibleWhileTheLongTextScrolls() {
        val qwen = CuratedPhoneModel.QWEN
        val licence = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open(qwen.licenceAsset).bufferedReader().use { it.readText() }
        var shown by mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                HermesAppTheme(AppPreferences(chinese = true, pinyin = true)) {
                    if (shown) PhoneLicence(licence, false, { downloads.add(qwen) }, { shown = false }, qwen)
                }
            }
        }
        val consent = compose.onNodeWithTag("phone-licence-consent")
        val confirm = compose.onNodeWithTag("phone-licence-confirm")
        val cancel = compose.onNodeWithTag("phone-licence-cancel")
        val body = compose.onNodeWithTag("phone-licence-body")
        consent.assertIsDisplayed().assertIsOff()
        confirm.assertIsDisplayed().assertIsNotEnabled()
        cancel.assertIsDisplayed()
        body.performTouchInput { swipeUp() }
        consent.assertIsDisplayed().assertIsOff()
        confirm.assertIsDisplayed().assertIsNotEnabled()
        consent.performClick()
        confirm.assertIsDisplayed().assertIsEnabled()
        cancel.assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(shown); assertTrue(downloads.isEmpty()) }
    }

    @Test fun installedModelSearchAndStorageKeepSelectionExplicit() {
        val qwen = model.copy(sha256 = "b".repeat(64),
            metadata = model.metadata.copy(name = "Small Qwen", architecture = "qwen2"))
        state = state.copy(models = listOf(model, qwen),
            downloadPartials = mapOf(CuratedPhoneModel.LFM3 to 1_048_576),
            modelStorageAvailableBytes = 3_000_000_000)
        show()
        compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-model-storage").performScrollTo().assertTextContains("Free storage", substring = true)
        compose.onNodeWithTag("phone-model-saved-downloads").performScrollTo().assertIsDisplayed()
        val search = compose.onNodeWithTag("phone-model-search")
        search.performScrollTo().performTextInput("no such model")
        compose.onNodeWithTag("phone-model-no-matches").performScrollTo().assertIsDisplayed()
        search.performScrollTo().performTextReplacement("QWEN2")
        compose.onNodeWithTag("phone-installed-${model.sha256}").assertDoesNotExist()
        compose.onNodeWithTag("phone-installed-${qwen.sha256}").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(requestedLoads.isEmpty()); assertTrue(downloads.isEmpty()) }
        compose.onNodeWithTag("phone-load-${qwen.sha256.take(8)}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(qwen.sha256 to 2048), requestedLoads); assertEquals(0, sends) }
    }

    @Test fun importIsExplicitAndCancelDoesNotDownload() {
        show(); compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-import").performScrollTo().performClick()
        compose.onNodeWithText(Words.PhoneImportLicence.en).assertExists()
        compose.onNodeWithTag("phone-licence-confirm").assertIsNotEnabled()
        compose.onNodeWithText(Words.Cancel.en).performClick()
        assertFalse(pending); assertEquals(1, imports); assertTrue(downloads.isEmpty())
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
        assertEquals(0, sends); assertTrue(downloads.isEmpty())
    }
    @Test fun helperSourcesRemainAvailableWithoutAModelOrServer() {
        show(false); compose.onNodeWithTag("phone-helper-open").performClick()
        compose.onNodeWithTag("phone-helper-run").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(Words.PhoneHelperSources.en, substring = true).performScrollTo().assertExists()
        assertTrue(helperRequests.isEmpty()); assertTrue(downloads.isEmpty())
    }
    @Test fun helperObservationsRequireExplicitOptInForTheRequest() {
        state = LocalAiState(loaded = model)
        show(); compose.onNodeWithTag("phone-helper-open").performClick()
        compose.onNodeWithTag("phone-helper-run").performScrollTo().performClick()
        assertEquals(listOf(LocalHelpTopic.FIRST_SETUP to false), helperRequests)
        compose.onNodeWithTag("phone-helper-observations").performScrollTo().performClick()
        compose.onNodeWithTag("phone-helper-run").performScrollTo().performClick()
        assertEquals(LocalHelpTopic.FIRST_SETUP to true, helperRequests.last())
        assertEquals(0, sends); assertTrue(downloads.isEmpty())
    }
    @Test fun bilingualLargeTextKeepsTheConversationAndComposerReachable() {
        state = LocalAiState(loaded = model)
        show(bilingual = true)
        val bounds = compose.onNodeWithTag("phone-messages").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 72.dp)
        compose.onNodeWithTag("phone-draft").assertIsDisplayed()
        compose.onNodeWithTag("phone-send").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("phone-helper-open").assertIsDisplayed()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/local-ai-bilingual.png")
        file.parentFile!!.mkdirs(); file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG, 100, it)}; image.recycle()
    }
    @Test fun helperDisplaysExactReviewedTextAndItsExecutionLocation() {
        state = LocalAiState(loaded = model, helper = LocalHelpResult(LocalHelpTopic.NETWORK, sha, null,
            LocalSetupHelper.sources(LocalHelpTopic.NETWORK),
            LocalSetupHelper.selection("[\"host.serve\",\"phone.tailnet\"]", LocalHelpTopic.NETWORK)!!, LocalReplyState.COMPLETE))
        show(bilingual = true); compose.onNodeWithTag("phone-helper-open").performClick()
        compose.onNodeWithTag("phone-helper-topic-NETWORK").performScrollTo().performClick()
        compose.onNodeWithText(Words.HelpHostServe.en, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Words.HelpHostServe.zh, substring = true).assertExists()
        compose.onNodeWithText(Words.HelpHostServe.pin, substring = true).assertExists()
        compose.onNodeWithText(listOf(Words.PhoneHelperOnHost.en, Words.PhoneHelperOnHost.zh,
            Words.PhoneHelperOnHost.pin).joinToString("\n")).assertExists()
        assertEquals(0, sends); assertTrue(downloads.isEmpty())
    }
    @Test fun bilingualCopiedDraftKeepsComposerAndModelChoiceReachable() {
        show(bilingual=true,copied=true)
        compose.onNodeWithTag("phone-draft-notice").assertIsDisplayed()
        compose.onNodeWithTag("phone-draft").assertIsDisplayed()
        compose.onNodeWithTag("phone-tab-1").assertIsDisplayed()
        val bounds=compose.onNodeWithTag("phone-messages").getUnclippedBoundsInRoot()
        assertTrue("Copied-draft transcript height: ${bounds.bottom-bounds.top}",bounds.bottom-bounds.top>=72.dp)
        compose.onNodeWithTag("phone-draft-notice").performClick()
        compose.onNodeWithTag("phone-draft-info-text").assertTextEquals(listOf(Words.PhoneDraftReceived.en,Words.PhoneDraftReceived.zh,Words.PhoneDraftReceived.pin).joinToString("\n"))
        compose.onNodeWithTag("phone-draft-info-close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("phone-draft").assertIsDisplayed()
        assertEquals(0,sends);assertTrue(downloads.isEmpty())
    }

}
