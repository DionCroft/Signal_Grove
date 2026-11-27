package org.hermesmobile.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.data.PreferenceStore
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
    private val queued = mutableListOf<CuratedPhoneModel>()
    private val resumed = mutableListOf<CuratedPhoneModel>()
    private var pauses = 0
    private val backgroundAttempts = mutableListOf<CuratedPhoneModel>()
    private var notificationsOpened = 0
    private var imports = 0; private var sends = 0; private var stops = 0
    private var newChats = 0
    private var pending by mutableStateOf(false)
    private var draft by mutableStateOf("Fresh local draft")
    private var state by mutableStateOf(LocalAiState())
    private var observationTime by mutableLongStateOf(100)
    private val helperRequests = mutableListOf<Pair<LocalHelpTopic, LocalHelpObservations?>>()
    private val benchmarks = mutableListOf<String>()
    private val performanceSaves = mutableListOf<LocalModelPerformance>()
    private val requestedLoads = mutableListOf<Pair<String, Int>>()
    private val sha = "a".repeat(64)
    private val model = InstalledLocalModel(sha, GgufModelMetadata("Fixture", "lfm3", 2048, "fixture", 1024))
    private fun show(supported: Boolean = true, bilingual: Boolean = false, copied: Boolean = false, queue: Boolean = false,
        networkStore: PreferenceStore? = null, background: Boolean = false,
        replacements: PhoneModelReplacementActions? = null, documents: PhoneDocumentActions? = null) {
        val actions = LocalAiActions({sends++}, {
                stops++
                if (state.status == "benchmarking") state = state.copy(busy = false, status = "idle")
            }, { newChats++ }, { model, context -> requestedLoads.add(model.sha256 to context) },
            {}, {}, { model -> downloads.add(model) }, { model -> discarded.add(model) }, {imports++; pending = true}, {pending = false}, {pending = false}, {}, {}, {},
            help = { topic, observations -> helperRequests.add(topic to observations) }, saveModelPerformance = { item, settings ->
                performanceSaves.add(settings)
                state = state.copy(modelPerformance = state.modelPerformance + (item.sha256 to settings))
            }, benchmark = { modelSha256 ->
                benchmarks.add(modelSha256)
                state = state.copy(busy = true, status = "benchmarking")
            }, downloads = if (queue) PhoneDownloadActions(
                enqueue = { queued.add(it); state = state.copy(queuedDownloads = state.queuedDownloads + it) },
                resume = {
                    resumed.add(it)
                    state = state.copy(activeDownload = it, busy = true, status = "downloading",
                        transfer = LocalTransferProgress(512, it.bytes, LocalTransferPhase.DOWNLOADING))
                },
                pause = {
                    pauses++
                    val active = checkNotNull(state.activeDownload)
                    state = state.copy(activeDownload = null, busy = false, status = "idle", transfer = null,
                        downloadPartials = state.downloadPartials + (active to 512L))
                },
                background = if (background) { option ->
                    backgroundAttempts.add(option)
                    state = state.copy(error = "download_background_notifications")
                } else null,
                notificationSettings = if (background) { { notificationsOpened++ } } else null
            ) else null, replacements = replacements, documents = documents)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (bilingual) 1.3f else density.fontScale)) {
            HermesAppTheme(AppPreferences(chinese = bilingual, pinyin = bilingual)) {
                LocalAiScreen(state, supported, draft, { draft = it }, pending, actions, {},
                    LocalHelpObservations(observationTime, false, LocalAddressKind.HTTPS, true, state.loaded != null), if(copied) "received" else "",
                    downloadNetworkSettings = networkStore?.let { store -> { close -> PhoneDownloadNetworkSettings(store, close) } })
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
    @Test fun replacementReviewShowsBothModelsAndKeepsAnExplicitInstallAction() {
        val candidate = InstalledLocalModel("b".repeat(64), model.metadata.copy(name = "Fixture replacement", bytes = 2_048))
        val review = PhoneModelReplacement(model, candidate, java.util.UUID.randomUUID(), 1024L * 1024 * 1024)
        val approved = mutableListOf<PhoneModelReplacement>()
        val cancelled = mutableListOf<PhoneModelReplacement>()
        state = LocalAiState(models = listOf(model))
        show(bilingual = true, replacements = PhoneModelReplacementActions(
            choose = { previous ->
                assertEquals(model, previous)
                state = state.copy(modelReplacement = review)
            },
            install = {
                approved.add(it)
                state = state.copy(models = state.models + it.candidate, modelReplacement = null)
            },
            discard = {
                cancelled.add(it)
                state = state.copy(modelReplacement = null)
            }
        ))
        compose.onNodeWithTag("phone-tab-1").performClick()
        val choose = compose.onNodeWithTag("phone-replace-${sha.take(8)}")
        choose.performScrollTo().performClick()
        compose.onNode(hasText(model.metadata.name) and hasAnyAncestor(hasTestTag("phone-replacement-details")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(candidate.metadata.name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-replacement-hash").performScrollTo()
            .assertTextContains(candidate.sha256, substring = true)
        val install = compose.onNodeWithTag("phone-replacement-install")
        install.assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { assertTrue(approved.isEmpty()); assertEquals(listOf(model), state.models) }
        compose.onNodeWithTag("phone-replacement-cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(review), cancelled); assertTrue(approved.isEmpty()) }
        choose.performScrollTo().performClick()
        install.assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(review), approved)
            assertEquals(listOf(model, candidate), state.models)
            assertTrue(requestedLoads.isEmpty())
            assertEquals(0, sends)
        }
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

    @Test fun phoneDocumentsReviewExactSourcesBeforeInvokingTheLoadedModel() {
        val document = PhoneDocument("fixture", "Museum.md", "a".repeat(64), 24, 2)
        val source = PhoneDocumentSource(document, 1, 2, "Opening hours.\nCloses at five.")
        val alternate = PhoneDocumentSource(document, 3, 3, "Entry is on the west side.")
        val requestedSelections = mutableListOf<Pair<Int, Boolean>>()
        val requests = mutableListOf<PhoneDocumentReview>()
        var sourceReviews = 0
        val settings = LocalGenerationSettings(1024, 1, 256)
        state = LocalAiState(models = listOf(model), loaded = model, loadedSettings = settings, documents = listOf(document))
        show(bilingual = true, documents = PhoneDocumentActions({}, {}, {}, {}, {}, { ids, question ->
                sourceReviews++
                assertEquals(listOf(document.id), ids)
                assertEquals("When does it close?", question)
                val measurement = PhoneDocumentContext(model.sha256, settings, LocalPromptBudget(420, 1024, 256), 2)
                state = state.copy(documentReview = PhoneDocumentReview(question, listOf(source), measurement,
                    candidates = listOf(source, alternate)))
            }, { current, index, include ->
                requestedSelections.add(index to include)
                val changed = PhoneDocumentQuestions.selectSource(current, index, include)
                val tokens = when (changed.sources) {
                    listOf(source) -> 420
                    listOf(alternate) -> 275
                    listOf(source, alternate) -> 900
                    else -> null
                }
                val context = tokens?.let {
                    PhoneDocumentContext(model.sha256, settings, LocalPromptBudget(it, 1024, 256), changed.sources.size)
                }
                state = state.copy(documentReview = PhoneDocumentReview(changed.question, changed.sources, context,
                    candidates = changed.candidates))
            }, {
                requests.add(it)
                state = state.copy(documentReview = null, documentAnswer = PhoneDocumentAnswer(it, "It closes at five [S1].", LocalReplyState.COMPLETE))
            }, { state = state.copy(documentReview = null, documentAnswer = null) }))
        compose.onNodeWithTag("phone-documents-open").performClick()
        compose.onNodeWithTag("phone-document-select-fixture").performScrollTo().performClick()
        compose.onNodeWithTag("phone-document-question").performScrollTo().performClick()
            .performTextInput("When does it close?")
        val review = compose.onNodeWithTag("phone-document-review")
        var readyBounds: androidx.compose.ui.geometry.Rect? = null
        var observation = "No focused dialog with the native keyboard visible"
        try {
            compose.waitUntil(10_000) {
                val viewport = focusedDialogViewport(requireKeyboard = false)
                    ?.takeIf { it.keyboardVisible } ?: return@waitUntil false
                val bounds = compose.onAllNodes(hasTestTag("phone-document-review") and isEnabled())
                    .fetchSemanticsNodes().singleOrNull()?.boundsInWindow ?: return@waitUntil false
                observation = "Control=$bounds, viewport=${viewport.visible}, offsetY=${viewport.screenOffsetY}"
                if (bounds.isEmpty || bounds.top + viewport.screenOffsetY < viewport.visible.top ||
                    bounds.bottom + viewport.screenOffsetY > viewport.visible.bottom) return@waitUntil false
                readyBounds = bounds
                true
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Review sources must stay above the actual keyboard. $observation", error)
        }
        compose.runOnIdle { assertEquals(0, sourceReviews); assertTrue(requests.isEmpty()) }
        review.assertIsDisplayed().assertIsEnabled()
        val bounds = checkNotNull(readyBounds)
        tapFocusedDialogWindowPoint(bounds.center.x, bounds.center.y, NativeDialogTapAction.PhoneDocumentReview)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("phone-document-source-0").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("phone-document-budget").performScrollTo()
            .assertTextContains("420 / 1024 prompt tokens", substring = true)
        compose.onNodeWithTag("phone-document-source-0").performScrollTo().assertIsDisplayed().assertTextEquals(source.text)
        compose.onNodeWithTag("phone-document-citation-0").performScrollTo().assertTextContains("Museum.md", substring = true)
        val ask = compose.onNodeWithTag("phone-document-answer-confirm")
        ask.assertIsDisplayed()
        val preferred = compose.onNodeWithTag("phone-document-excerpt-choice-0")
        val other = compose.onNodeWithTag("phone-document-excerpt-choice-1")
        preferred.performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("phone-document-empty-selection").performScrollTo().assertIsDisplayed()
        ask.assertIsNotEnabled()
        other.performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("phone-document-budget").performScrollTo()
            .assertTextContains("275 / 1024 prompt tokens", substring = true)
        compose.onNodeWithTag("phone-document-source-0").performScrollTo().assertTextEquals(alternate.text)
        compose.onNodeWithTag("phone-document-citation-0").performScrollTo().assertTextContains("[S1]", substring = true)
        preferred.performScrollTo().performClick()
        compose.onNodeWithTag("phone-document-budget").performScrollTo()
            .assertTextContains("900 / 1024 prompt tokens", substring = true)
        ask.assertIsNotEnabled()
        other.performScrollTo().performClick()
        compose.onNodeWithTag("phone-document-budget").performScrollTo()
            .assertTextContains("420 / 1024 prompt tokens", substring = true)
        compose.runOnIdle {
            assertEquals(listOf(0 to false, 1 to true, 0 to true, 1 to false), requestedSelections)
            assertEquals(1, sourceReviews)
            assertTrue(requests.isEmpty())
            assertEquals(0, sends)
        }
        compose.onNodeWithTag("phone-document-answer-confirm").performClick()
        compose.onNodeWithTag("phone-document-answer").performScrollTo().assertTextEquals("It closes at five [S1].")
        compose.onNodeWithTag("phone-document-citation-0").performScrollTo().assertTextContains("[S1]", substring = true)
        compose.runOnIdle { assertEquals(listOf(source), requests.single().sources); assertEquals(0, sends) }
    }

    @Test fun publisherUpdateComparesExactRevisionsAndQueuesOnlyTheSeparatelyReviewedFile() {
        val older = CuratedPhoneModel.QWEN
        val newer = older.copy(name = "QWEN_NEW_FIXTURE", revision = "e".repeat(40), sha256 = "b".repeat(64))
        val previous = model.copy(sha256 = older.sha256)
        state = LocalAiState(
            models = listOf(previous),
            modelCatalog = PhoneModelCatalog(listOf(newer), mapOf(newer to listOf(older))),
            modelStorageAvailableBytes = 1_500_000_000L,
            unavailableDownloadReviews = 2
        )
        show(bilingual = true, queue = true)
        compose.onNodeWithTag("phone-tab-1").performClick()
        val open = compose.onNodeWithTag("phone-catalogue-update-${older.sha256.take(8)}")
        open.performScrollTo().performClick()
        compose.onNodeWithTag("phone-licence-source").performScrollTo().assertTextContains(newer.revision, substring = true)
            .assertTextContains(newer.sha256, substring = true)
        compose.onNodeWithTag("phone-catalogue-update-previous-QWEN").performScrollTo()
            .assertTextContains(older.revision, substring = true).assertTextContains(older.sha256, substring = true)
        compose.onNodeWithTag("phone-catalogue-update-space").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-catalogue-update-free").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-licence-queue").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("phone-licence-confirm").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("phone-licence-cancel").performClick()
        compose.runOnIdle {
            assertTrue(queued.isEmpty())
            assertTrue(downloads.isEmpty())
        }

        open.performScrollTo().performClick()
        compose.onNodeWithTag("phone-licence-consent").assertIsOff().performClick()
        compose.onNodeWithTag("phone-licence-queue").assertIsEnabled().performClick()
        compose.onNodeWithTag("phone-downloads-unavailable-count").performScrollTo().assertTextContains("2", substring = true)
        compose.onNodeWithTag("phone-transfer-revision-QWEN_NEW_FIXTURE").performScrollTo()
            .assertTextContains(newer.revision, substring = true)
        compose.onNodeWithTag("phone-transfer-start-QWEN_NEW_FIXTURE").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf(newer), queued)
            assertTrue(resumed.isEmpty())
            assertTrue(downloads.isEmpty())
            assertEquals(listOf(previous), state.models)
        }
    }

    @Test fun reviewedQueueStartsAndPausesOnlyTheExplicitItem() {
        val qwen = CuratedPhoneModel.QWEN
        show(bilingual = true, queue = true)
        compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-catalogue-open").performScrollTo().performClick()
        compose.onNodeWithTag("phone-download-review-QWEN").performScrollTo().performClick()
        val add = compose.onNodeWithTag("phone-licence-queue")
        add.assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("phone-licence-consent").assertIsDisplayed().performClick()
        add.assertIsEnabled().performClick()
        val start = compose.onNodeWithTag("phone-transfer-start-QWEN")
        start.performScrollTo().assertTextContains(Words.PhoneDownloadStart.en, substring = true)
        compose.runOnIdle {
            assertEquals(listOf(qwen), queued)
            assertTrue(downloads.isEmpty())
            assertTrue(resumed.isEmpty())
        }
        start.performClick()
        compose.onNodeWithTag("phone-transfer-bytes-QWEN").performScrollTo()
            .assertTextContains("512 of ${qwen.bytes} bytes saved", substring = true)
        compose.onNodeWithTag("phone-transfer-pause").performScrollTo().assertIsEnabled().performClick()
        start.performScrollTo().assertTextContains(Words.PhoneDownloadResume.en, substring = true)
        compose.runOnIdle {
            assertEquals(listOf(qwen), resumed)
            assertEquals(1, pauses)
            assertEquals(512L, state.partialBytes(qwen))
            assertTrue(requestedLoads.isEmpty())
            assertEquals(0, sends)
        }
    }

    @Test fun backgroundChoiceIsPerReviewedItemAndNotificationSettingsDoNotStartIt() {
        val qwen = CuratedPhoneModel.QWEN
        val lfm = CuratedPhoneModel.LFM3
        state = state.copy(queuedDownloads = listOf(qwen), downloadPartials = mapOf(lfm to 512L))
        show(queue = true, background = true)
        compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-downloads-open").performScrollTo().performClick()
        compose.onNodeWithTag("phone-transfer-background-LFM3").assertDoesNotExist()
        val chosen = compose.onNodeWithTag("phone-transfer-background-QWEN")
        chosen.performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertTrue(backgroundAttempts.isEmpty()) }
        chosen.performClick()
        compose.onNodeWithText(Words.PhoneDownloadBackgroundNotifications.en).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-transfer-notification-settings").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(qwen), backgroundAttempts)
            assertEquals(1, notificationsOpened)
            assertTrue(downloads.isEmpty())
            assertTrue(resumed.isEmpty())
            assertTrue(requestedLoads.isEmpty())
            assertEquals(listOf(qwen), state.queuedDownloads)
        }
    }

    @Test fun networkChoicePersistsAndCannotStartTheReviewedQueue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val store = PreferenceStore(context)
        val original = runBlocking { store.phoneDownloadUnmetered.first() }
        try {
            runBlocking { store.setPhoneDownloadUnmetered(false) }
            state = state.copy(queuedDownloads = listOf(CuratedPhoneModel.QWEN))
            show(bilingual = true, queue = true, networkStore = store)
            compose.onNodeWithTag("phone-tab-1").performClick()
            compose.onNodeWithTag("phone-downloads-open").performScrollTo().performClick()
            val opens = compose.onNodeWithTag("phone-download-networks")
            opens.performScrollTo().performClick()
            compose.onNodeWithTag("phone-network-any").performScrollTo().assertIsSelected()
            val restricted = compose.onNodeWithTag("phone-network-unmetered")
            restricted.performScrollTo().assertIsNotSelected().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("phone-network-unmetered") and isSelected()).fetchSemanticsNodes().size == 1
            }
            assertTrue(runBlocking { store.phoneDownloadUnmetered.first() })
            compose.onNodeWithTag("phone-network-close").performClick()
            opens.performScrollTo().performClick()
            restricted.performScrollTo().assertIsSelected()
            compose.onNodeWithTag("phone-network-close").performClick()
            compose.runOnIdle {
                assertTrue(downloads.isEmpty())
                assertTrue(resumed.isEmpty())
                assertEquals(listOf(CuratedPhoneModel.QWEN), state.queuedDownloads)
                state = state.copy(busy = true, status = "downloading")
            }
            opens.performScrollTo().assertIsNotEnabled()
        } finally {
            runBlocking { store.setPhoneDownloadUnmetered(original) }
        }
    }

    @Test fun existingPartialOpensLicenceBeforeItCanResume() {
        val model = CuratedPhoneModel.LFM3
        state = state.copy(downloadPartials = mapOf(model to 512L))
        show(queue = true)
        compose.onNodeWithTag("phone-tab-1").performClick()
        compose.onNodeWithTag("phone-downloads-open").performScrollTo().performClick()
        compose.onNodeWithTag("phone-transfer-start-LFM3").performScrollTo()
            .assertTextContains(Words.PhoneDownloadNeedsReview.en, substring = true).performClick()
        compose.onNodeWithTag("phone-licence-model").assertTextEquals(model.displayName)
        compose.onNodeWithTag("phone-licence-cancel").performClick()
        compose.onNodeWithTag("phone-transfer-start-LFM3").assertExists()
        compose.runOnIdle {
            assertTrue(resumed.isEmpty())
            assertTrue(downloads.isEmpty())
            assertTrue(queued.isEmpty())
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
        search.assertIsDisplayed().performClick().performTextInput("no such model")
        compose.waitUntil(10_000) { focusedWindowKeyboardVisible() }
        compose.onNodeWithTag("phone-model-no-matches").assertIsDisplayed()
            .assertTextEquals(Words.PhoneModelNoMatches.en)
        search.assertIsDisplayed().assertTextContains("no such model").performTextReplacement("QWEN2")
        compose.onNodeWithTag("phone-model-no-matches").assertDoesNotExist()
        closeFocusedKeyboard()
        compose.waitUntil(10_000) { !focusedWindowKeyboardVisible() }
        compose.onNodeWithTag("phone-installed-${model.sha256}").assertDoesNotExist()
        compose.onNodeWithTag("phone-installed-${qwen.sha256}").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(requestedLoads.isEmpty()); assertTrue(downloads.isEmpty()) }
        compose.onNodeWithTag("phone-load-${qwen.sha256.take(8)}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(qwen.sha256 to 2048), requestedLoads); assertEquals(0, sends) }
    }

    @Test fun offlineSetupRoutesToExistingCatalogueAndImportReview() {
        show()
        compose.onNodeWithTag("phone-messages").performScrollToNode(hasTestTag("phone-offline-setup-chat-entry"))
        compose.onNodeWithTag("phone-offline-setup-chat-entry").assertIsDisplayed().performClick()
        compose.onNodeWithTag("phone-offline-setup-models").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("phone-offline-setup-catalogue").performScrollTo().performClick()
        compose.onNodeWithTag("phone-download-review-LFM3").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(downloads.isEmpty()); assertEquals(0, imports) }
        compose.onNodeWithTag("phone-catalogue-close").performClick()

        compose.onNodeWithTag("phone-offline-setup-models-entry").performScrollTo().performClick()
        compose.onNodeWithTag("phone-offline-setup-import").performScrollTo().performClick()
        compose.onNodeWithText(Words.PhoneImportLicence.en).assertExists()
        compose.onNodeWithTag("phone-licence-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("phone-licence-cancel").performClick()
        compose.runOnIdle {
            assertFalse(pending)
            assertEquals(1, imports)
            assertTrue(downloads.isEmpty())
            assertTrue(requestedLoads.isEmpty())
            assertEquals(0, sends)
        }
    }

    @Test fun offlineSetupReflectsLibraryAndOpensChatWithoutStartingWork() {
        state = LocalAiState(models = listOf(model))
        show()
        compose.onNodeWithTag("phone-messages").performScrollToNode(hasTestTag("phone-offline-setup-chat-entry"))
        compose.onNodeWithTag("phone-offline-setup-chat-entry").performClick()
        compose.onNodeWithTag("phone-offline-setup-installed").performScrollTo().assertTextContains("1", substring = true)
        compose.onNodeWithTag("phone-offline-setup-chat").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("phone-offline-setup-models").performScrollTo().performClick()
        compose.onNodeWithTag("phone-model-list").assertExists()
        compose.runOnIdle { assertTrue(requestedLoads.isEmpty()) }

        compose.runOnIdle {
            state = state.copy(loaded = model, history = listOf(LocalConversationSummary(
                "11111111-1111-4111-8111-111111111111", model.sha256, 0, "Saved", LocalReplyState.COMPLETE
            )))
        }
        compose.onNodeWithTag("phone-offline-setup-models-entry").performScrollTo().performClick()
        compose.onNodeWithTag("phone-offline-setup-loaded").performScrollTo().assertTextContains("Fixture", substring = true)
        compose.onNodeWithTag("phone-offline-setup-saved").performScrollTo().assertTextContains("1", substring = true)
        compose.onNodeWithTag("phone-offline-setup-chat").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("phone-draft").assertTextContains("Fresh local draft")
        compose.runOnIdle {
            assertTrue(requestedLoads.isEmpty())
            assertTrue(downloads.isEmpty())
            assertEquals(0, newChats)
            assertEquals(0, sends)
        }
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
    @Test fun offlineChecklistAndTopicSearchWorkBeforeAnyModelInstallation() {
        state = LocalAiState(modelStorageAvailableBytes = 1024L * 1024L)
        show()
        compose.onNodeWithTag("phone-messages").performScrollToNode(hasTestTag("phone-offline-setup-chat-entry"))
        compose.onNodeWithTag("phone-offline-setup-chat-entry").performClick()
        compose.onNodeWithTag("phone-offline-setup-installed").performScrollTo().assertTextContains("0", substring = true)
        compose.onNodeWithTag("phone-offline-setup-space").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-offline-setup-troubleshoot").performScrollTo().performClick()
        compose.onNodeWithTag("phone-helper-current-topic").performScrollTo().assertTextContains(Words.PhoneOfflineSetup.en)
        compose.onNodeWithText(Words.HelpOfflineStorage.en).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Words.HelpOfflineContext.en).performScrollTo().assertIsDisplayed()

        val query = compose.onNodeWithTag("phone-helper-query").performScrollTo()
        query.performTextInput("Earlier chats")
        compose.onNodeWithTag("phone-helper-topic-HISTORY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-helper-topic-NETWORK").assertDoesNotExist()
        compose.onNodeWithTag("phone-helper-current-topic").assertTextContains(Words.PhoneOfflineSetup.en)
        compose.onNodeWithTag("phone-helper-topic-HISTORY").performClick()
        compose.onNodeWithTag("phone-helper-current-topic").assertTextContains(Words.SetupChats.en)
        compose.onNodeWithText(Words.HelpChatHistory.en).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("phone-helper-run").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(helperRequests.isEmpty())
            assertTrue(downloads.isEmpty())
            assertTrue(requestedLoads.isEmpty())
            assertEquals(0, sends)
            assertEquals(0, imports)
        }
    }
    @Test fun helperObservationsRequireExplicitOptInForTheRequest() {
        state = LocalAiState(loaded = model)
        show(bilingual = true)
        compose.onNodeWithTag("phone-chat-options").assertIsDisplayed().performClick()
        compose.onNodeWithTag("phone-chat-helper").assertIsDisplayed().performClick()
        val include = compose.onNodeWithTag("phone-helper-observations")
        val run = compose.onNodeWithTag("phone-helper-run")
        include.performScrollTo().assertIsOff()
        tapHelperControl(run, NativeDialogTapAction.PhoneHelperRun)
        compose.runOnIdle { assertEquals(listOf(LocalHelpTopic.FIRST_SETUP to null), helperRequests) }

        tapHelperControl(include, NativeDialogTapAction.PhoneHelperConsent)
        include.assertIsOn()
        tapHelperControl(run, NativeDialogTapAction.PhoneHelperRun)
        compose.runOnIdle {
            assertEquals(2, helperRequests.size)
            assertEquals(LocalHelpTopic.FIRST_SETUP to
                LocalHelpObservations(100, false, LocalAddressKind.HTTPS, true, true), helperRequests.last())
        }

        include.performScrollTo().assertIsOff()
        tapHelperControl(run, NativeDialogTapAction.PhoneHelperRun)
        compose.runOnIdle {
            assertEquals(3, helperRequests.size)
            assertEquals(LocalHelpTopic.FIRST_SETUP to null, helperRequests.last())
            assertEquals(0, sends)
            assertTrue(downloads.isEmpty())
        }
    }

    @Test fun helperDismissesTheComposerKeyboardAndKeepsTheDraft() {
        state = LocalAiState(loaded = model)
        show()
        val composer = compose.onNodeWithTag("phone-draft")
        val editedDraft = "Keep this unsent phone draft"
        composer.assertIsDisplayed().performClick()
        val menu = compose.onNodeWithTag("phone-chat-options")
        try {
            compose.waitUntil(10_000) {
                nativeKeyboardVisible() && focusedWindowKeyboardVisible() &&
                    composer.isDisplayed() && menu.isDisplayed()
            }
        } catch (failure: ComposeTimeoutException) {
            val editor = runCatching { composer.fetchSemanticsNode().boundsInWindow }.getOrNull()
            val options = runCatching { menu.fetchSemanticsNode().boundsInWindow }.getOrNull()
            throw AssertionError("Composer and Chat options did not fit above the keyboard. Editor=$editor, options=$options", failure)
        }

        // Resizing for the IME can restart the editor input connection.
        composer.assertIsFocused().performTextReplacement(editedDraft)
        composer.assertTextContains(editedDraft)
        compose.runOnIdle { assertEquals(editedDraft, draft) }

        menu.assertIsDisplayed().assertIsEnabled()
        val editorBounds = composer.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val optionBounds = menu.fetchSemanticsNode().boundsInWindow
        assertTrue("Chat options must remain beside the draft with the keyboard open",
            optionBounds.center.y in editorBounds.top..editorBounds.bottom)
        menu.performClick()
        compose.onNodeWithTag("phone-chat-helper").assertIsDisplayed().performClick()
        waitForHelperWithoutKeyboard()

        val search = compose.onNodeWithTag("phone-helper-query")
        search.performScrollTo().assertIsDisplayed().performClick().performTextInput("Earlier chats")
        compose.waitUntil(10_000) { nativeKeyboardVisible() && focusedDialogKeyboardVisible() }
        compose.onNodeWithTag("phone-helper-topic-HISTORY").assertExists()
        compose.onNodeWithTag("phone-helper-close").assertIsDisplayed().performClick()
        composer.assertTextContains(editedDraft)
        compose.runOnIdle {
            assertEquals(editedDraft, draft)
            assertEquals(0, sends)
            assertTrue(helperRequests.isEmpty())
        }
    }

    @Test fun helperRequiresNewOptInAfterTheObservationPreviewChanges() {
        state = LocalAiState(loaded = model)
        show()
        compose.onNodeWithTag("phone-helper-open").assertIsDisplayed().performClick()
        val include = compose.onNodeWithTag("phone-helper-observations")
        val run = compose.onNodeWithTag("phone-helper-run")
        include.assertIsOff()
        tapHelperControl(include, NativeDialogTapAction.PhoneHelperConsent)
        include.assertIsOn()
        compose.runOnIdle { observationTime = 200 }
        include.performScrollTo().assertIsOff()
        tapHelperControl(run, NativeDialogTapAction.PhoneHelperRun)
        compose.runOnIdle { assertEquals(listOf(LocalHelpTopic.FIRST_SETUP to null), helperRequests) }

        include.assertIsOff()
        tapHelperControl(include, NativeDialogTapAction.PhoneHelperConsent)
        include.assertIsOn()
        tapHelperControl(run, NativeDialogTapAction.PhoneHelperRun)
        compose.runOnIdle {
            assertEquals(2, helperRequests.size)
            assertEquals(LocalHelpTopic.FIRST_SETUP to
                LocalHelpObservations(200, false, LocalAddressKind.HTTPS, true, true), helperRequests.last())
            assertEquals(0, sends)
        }
        compose.onNodeWithTag("phone-helper-close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("phone-draft").assertTextContains("Fresh local draft")
    }

    private fun waitForHelperWithoutKeyboard() {
        var observation = "No focused helper dialog"
        try {
            compose.waitUntil(8_000) {
                val viewport = focusedDialogViewport(requireKeyboard = false, onIncoherentIme = {
                    observation = "IME window=" + it.nativeImePresent + ", insets=" + it.deliveredImeVisible
                }) ?: return@waitUntil false
                observation = "keyboard=" + viewport.keyboardVisible + ", frame=" + viewport.visible
                !viewport.keyboardVisible
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("The helper keyboard did not settle. $observation", error)
        }
    }

    private fun tapHelperControl(control: SemanticsNodeInteraction, action: NativeDialogTapAction) {
        val body = compose.onNodeWithTag("phone-helper-body")
        control.performScrollTo().assertIsEnabled()
        var nativeViewport: FocusedDialogViewport? = null
        compose.waitUntil(8_000) {
            nativeViewport = focusedDialogViewport(requireKeyboard = false)
            nativeViewport != null
        }
        val viewport = checkNotNull(nativeViewport)
        val targetNode = control.fetchSemanticsNode()
        val bodyBounds = body.fetchSemanticsNode().boundsInWindow
        val bodyTop = maxOf(bodyBounds.top, (viewport.visible.top - viewport.screenOffsetY).toFloat())
        val bodyBottom = minOf(bodyBounds.bottom, (viewport.visible.bottom - viewport.screenOffsetY).toFloat())
        check(bodyBottom > bodyTop) { "The helper scroll viewport is empty: $bodyBounds, native=" + viewport.visible }
        val centerY = targetNode.positionInWindow.y + targetNode.size.height / 2f
        val scrollBy = centerY - (bodyTop + bodyBottom) / 2f
        // Center within the part of the scroll body that native input can reach.
        if (kotlin.math.abs(scrollBy) >= 1f) {
            body.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                assertTrue("The helper scroll content rejected repositioning", scroll(0f, scrollBy))
            }
        }
        control.assertIsDisplayed()
        var point: androidx.compose.ui.geometry.Offset? = null
        var observation = "No focused dialog viewport"
        try {
            compose.waitUntil(5_000) {
                val viewport = focusedDialogViewport(requireKeyboard = false, onIncoherentIme = {
                    observation = "IME window=${it.nativeImePresent}, insets=${it.deliveredImeVisible}"
                }) ?: return@waitUntil false
                val node = control.fetchSemanticsNode()
                val bounds = node.boundsInWindow
                val visible = viewport.visible
                val left = maxOf(bounds.left, (visible.left - viewport.screenOffsetX).toFloat())
                val top = maxOf(bounds.top, (visible.top - viewport.screenOffsetY).toFloat())
                val right = minOf(bounds.right, (visible.right - viewport.screenOffsetX).toFloat())
                val bottom = minOf(bounds.bottom, (visible.bottom - viewport.screenOffsetY).toFloat())
                observation = "Control=$bounds, root=${node.boundsInRoot}, position=${node.positionInRoot}, " +
                    "size=${node.size}, viewport=$visible, keyboard=${viewport.keyboardVisible}, " +
                    "offset=(${viewport.screenOffsetX}, ${viewport.screenOffsetY})"
                if (right - left < 1f || bottom - top < 1f) return@waitUntil false
                point = androidx.compose.ui.geometry.Offset((left + right) / 2, (top + bottom) / 2)
                true
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("No visible native target for ${action.wire}. $observation", error)
        }
        val target = checkNotNull(point)
        tapFocusedDialogWindowPoint(target.x, target.y, action)
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
        show(bilingual = true)
        compose.onNodeWithTag("phone-helper-open").performClick()
        waitForHelperWithoutKeyboard()
        val network = compose.onNodeWithTag("phone-helper-topic-NETWORK")
        tapHelperControl(network, NativeDialogTapAction.PhoneHelperNetwork)
        network.assertIsSelected()
        compose.onNodeWithTag("phone-helper-current-topic").assertTextContains(Words.SetupNetwork.en, substring = true)
        val reviewedAnswer = hasAnyAncestor(hasTestTag("phone-helper-answer"))
        compose.onNode(hasText(Words.HelpHostServe.en, substring = true) and reviewedAnswer)
            .performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(Words.HelpHostServe.zh, substring = true) and reviewedAnswer).assertExists()
        compose.onNode(hasText(Words.HelpHostServe.pin, substring = true) and reviewedAnswer).assertExists()
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
