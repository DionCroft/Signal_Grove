package org.hermesmobile.app.local

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hermesmobile.app.updates.UpdateWorkGuard

internal class LocalHistoryBackupBusyException : IllegalStateException()

data class LocalAiState(val models: List<InstalledLocalModel> = emptyList(), val history: List<LocalConversationSummary> = emptyList(),
    val chat: LocalConversation? = null, val loaded: InstalledLocalModel? = null, val busy: Boolean = false,
    val status: String = "idle", val error: String? = null, val downloadPartials: Map<CuratedPhoneModel, Long> = emptyMap(),
    val transfer: LocalTransferProgress? = null,
    val helper: LocalHelpResult? = null, val historyQuery: LocalHistoryQuery? = null,
    val historyMatches: List<LocalConversationSummary> = emptyList(),
    val modelPerformance: Map<String, LocalModelPerformance> = emptyMap(),
    val loadedSettings: LocalGenerationSettings? = null,
    val performance: LocalPerformanceDiagnostics = LocalPerformanceDiagnostics(),
    val modelStorageAvailableBytes: Long? = null,
    val queuedDownloads: List<CuratedPhoneModel> = emptyList(),
    val activeDownload: CuratedPhoneModel? = null,
    val downloadQueueUnavailable: Boolean = false) {
    fun partialBytes(model: CuratedPhoneModel): Long = downloadPartials[model] ?: 0L
}

/** One instance per application process, shared across activity recreation. No Hermes client or credential store. */
class LocalAiController(private val modelRoot: File, historyRoot: File, private val nativeDirectory: String,
    val supported: Boolean, private val freeMemory: () -> Long, private val guard: UpdateWorkGuard,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readLoadPssKiB: () -> Int? = { null },
    private val engineFactory: () -> LocalInference = { NativeLocalInference(modelRoot, nativeDirectory) }) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Mutex()
    private val owner = Any()
    private val store = LocalModelStore(modelRoot)
    private val conversations = LocalConversationStore(historyRoot)
    private val organization = LocalHistoryOrganization(historyRoot.canonicalFile.let {
        File(it.parentFile, it.name + "-organization")
    }, conversations)
    private val historyRestore = LocalHistoryRestoreTransaction(conversations, organization)
    internal val historyBackupFiles = LocalHistoryBackupFiles(
        File(conversations.directory.parentFile, "history-backup-stages"), conversations, organization
    )
    private val downloadQueue = LocalDownloadQueue(store)
    private val downloads = CuratedPhoneModel.entries.associateWith { model ->
        LocalModelDownload(store, spec = model.spec, ioDispatcher = ioDispatcher)
    }
    private var engine: LocalInference? = null
    private var job: Job? = null
    private var leaving = false
    private var restoreRecoveryNeeded = true
    private var recovered = false
    private val mutable = MutableStateFlow(LocalAiState())
    val state = mutable.asStateFlow()

    init { action("reading") { refresh() } }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun cancel() { engine?.cancel(); job?.cancel() }
    fun refreshLibrary() = action("reading") { refresh() }
    /** Explicit read-only search. Keep unfiltered history for parent navigation and limits. */
    fun searchHistory(query: LocalHistoryQuery) = action("searching") {
        val result = io {
            val context = currentCoroutineContext()
            val all = organization.history { context.ensureActive() }
            val matches = LocalHistorySearch(conversations, organization).find(query) { context.ensureActive() }
            all to matches
        }
        mutable.update { it.copy(history = result.first, historyQuery = query, historyMatches = result.second) }
    }
    fun organizeChat(expected: LocalConversationSummary, preferences: LocalHistoryPreferences) = action("organizing") {
        io {
            val context = currentCoroutineContext()
            organization.update(expected.id, expected.modelSha256, expected.organization, preferences) {context.ensureActive()}
        }
        refresh()
    }
    /** Prepare a read-only snapshot of the selected row before showing the document review. */
    fun prepareMarkdownExport(expected: LocalConversationSummary, onPrepared: (LocalMarkdownExport) -> Unit) = action("preparing_export") {
        val document = io {
            val context = currentCoroutineContext()
            val checkpoint = { context.ensureActive() }
            val conversation = conversations.load(expected.id, checkpoint)
            val labels = organization.entries(checkpoint)[expected.id]
            if (conversation.updatedAt != expected.updatedAt || conversation.modelSha256 != expected.modelSha256 ||
                conversation.origin != expected.origin || conversation.exchanges.lastOrNull()?.state != expected.lastState ||
                (conversation.reviewedSummary != null) != expected.hasReviewedSummary || labels != expected.organization) {
                throw LocalMarkdownExportException(LocalMarkdownExportFailure.CHANGED)
            }
            conversations.markdownExport(conversation, labels?.preferences?.title, checkpoint)
        }
        onPrepared(document)
    }

    internal suspend fun prepareHistoryRestore(
        archive: LocalHistoryBackupArchive.Inspection,
        checkpoint: () -> Unit
    ): LocalHistoryBackupRestorePlan = historyBackupAction("preparing_restore") {
        io { historyRestore.prepare(archive, checkpoint) }
    }

    /** A failed refresh after the transaction returns cannot turn a committed restore into a retry. */
    internal suspend fun restoreHistory(plan: LocalHistoryBackupRestorePlan): Boolean =
        historyBackupAction("restoring_history") {
            restoreRecoveryNeeded = true
            io { historyRestore.restore(plan) }
            restoreRecoveryNeeded = false
            try {
                refresh()
                true
            } catch (e: Exception) {
                mutable.update { it.copy(error = failure(e)) }
                false
            }
        }

    private suspend fun <T> historyBackupAction(status: String, block: suspend () -> T): T {
        if (mutable.value.busy || leaving) throw LocalHistoryBackupBusyException()
        mutable.update { it.copy(busy = true, status = status, error = null) }
        guard.set(owner, true)
        return try {
            lock.withLock {
                ensureHistoryReady()
                block()
            }
        } finally {
            if (!leaving) {
                mutable.update { it.copy(busy = false, status = "idle") }
                guard.set(owner, false)
            }
        }
    }

    fun clearHistorySearch() {
        if (mutable.value.busy || leaving) return
        mutable.update { it.copy(historyQuery = null, historyMatches = emptyList(),
            error = it.error.takeUnless {code -> code == "history_search"}) }
    }

    fun enqueueDownload(model: CuratedPhoneModel, licenceReviewed: Boolean) = action("queuing_download") {
        check(supported)
        require(licenceReviewed)
        io { downloadQueue.add(model) }
        refresh()
    }

    fun download(model: CuratedPhoneModel, licenceReviewed: Boolean) = beginDownload(model) {
        require(licenceReviewed)
        downloadQueue.add(model)
    }

    fun resumeDownload(model: CuratedPhoneModel) = beginDownload(model) { downloadQueue.assertReviewed(model) }

    fun pauseDownload() {
        val current = mutable.value
        if (current.status == "downloading" && current.transfer?.phase != LocalTransferPhase.INSTALLING) job?.cancel()
    }

    private fun beginDownload(model: CuratedPhoneModel, authorize: () -> Unit) = action("downloading", model) {
        check(supported)
        io { authorize() }
        downloads.getValue(model).download(true) { value -> mutable.update { it.copy(transfer = value) } }
        refresh()
    }

    fun discardDownload(model: CuratedPhoneModel) = action("discarding_download") {
        io { downloadQueue.remove(model) }
        downloads.getValue(model).discardPartial()
        refresh()
    }
    fun importModel(licenceReviewed: Boolean, cancelOpen: () -> Unit = {}, open: () -> InputStream) = action("importing") {
        check(supported)
        require(licenceReviewed)
        io {
            val context = currentCoroutineContext()
            val source = java.util.concurrent.atomic.AtomicReference<InputStream?>()
            coroutineScope {
                val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { runCatching(cancelOpen); runCatching { source.get()?.close() } }
                }
                try {
                    val input = open(); source.set(input); context.ensureActive()
                    store.importModel(input, licenceReviewed, checkpoint = { context.ensureActive() })
                } catch (e: Exception) {
                    // A document provider may signal close/cancellation with its own exception.
                    context.ensureActive()
                    throw e
                } finally { watcher.cancel(); runCatching { source.get()?.close() } }
            }
        }
        refresh()
    }
    fun saveModelPerformance(model: InstalledLocalModel, performance: LocalModelPerformance) = action("saving_model_settings") {
        require(model in mutable.value.models)
        withContext(NonCancellable) {
            io { store.savePerformance(model, performance) }
            mutable.update { it.copy(modelPerformance = it.modelPerformance + (model.sha256 to performance)) }
        }
    }

    fun load(model: InstalledLocalModel, context: Int? = null) = action("loading") {
        check(supported)
        val saved = mutable.value.modelPerformance[model.sha256]
            ?: LocalPerformancePreset.BALANCED.forModel(model.metadata.trainedContext)
        val requested = context?.let { saved.copy(contextTokens = it) } ?: saved
        require(requested.allowedBy(model.metadata.trainedContext))
        val resolved = requested.resolve(Runtime.getRuntime().availableProcessors())
        unloadModel()
        currentCoroutineContext().ensureActive()
        // Measure after reclaiming the old weights. This check cannot guarantee native allocation.
        if (freeMemory() < model.minimumLoadMemoryBytes) throw LocalModelStorageException("insufficient_memory")
        val file = io { val current = currentCoroutineContext(); store.verifiedFile(model.sha256) { current.ensureActive() } }
        val target = engine ?: engineFactory().also { engine = it }
        try {
            target.load(file, resolved.contextTokens, resolved.threads)
            currentCoroutineContext().ensureActive()
            val settings = resolved.copy(runtime = target.runtimePolicy).also { it.validate() }
            val pss = io {
                try { readLoadPssKiB()?.takeIf { it > 0 } }
                catch (e: CancellationException) { throw e }
                catch (_: RuntimeException) { null }
            }
            mutable.update { it.copy(loaded = model, loadedSettings = settings,
                performance = LocalPerformanceDiagnostics(loadPssKiB = pss)) }
        } finally {
            // A native load can finish just before cancellation reaches its caller.
            if (mutable.value.loaded == null) unloadModel()
        }
    }

    private suspend fun unloadModel() = withContext(NonCancellable) {
        try {
            engine?.unload()
        } finally {
            mutable.update { it.copy(loaded = null, loadedSettings = null, performance = LocalPerformanceDiagnostics()) }
        }
    }

    fun unload() = action("unloading") { unloadModel() }
    fun deleteModel(model: InstalledLocalModel) = action("deleting") {
        if (mutable.value.loaded?.sha256 == model.sha256) unloadModel()
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            try { io { store.delete(model.sha256) } }
            finally { reconcileModelLibrary() }
        }
    }
    /** Select a fresh unsent question without deleting history, loading or generating. */
    fun startDraft():Boolean {
        if(mutable.value.busy || leaving) return false
        mutable.update {it.copy(chat=null,error=null)}
        return true
    }
    fun newChat() {
        if (mutable.value.busy) return
        val model = mutable.value.loaded ?: return
        mutable.update { it.copy(chat = LocalConversation(modelSha256 = model.sha256, updatedAt = System.currentTimeMillis()), error = null) }
    }
    fun openChat(id: String) = openChat(id) {}
    fun openChat(id: String, onSelected: () -> Unit) = action("reading") {
        val chat = io { conversations.load(id) }; mutable.update { it.copy(chat = chat) }; onSelected()
    }
    fun deleteChat(id: String) = deleteChat(id) {}
    fun deleteChat(id: String, onDeleted: () -> Unit) = action("deleting") {
        val labels = io { val context = currentCoroutineContext(); organization.entries {context.ensureActive()}[id] }
        currentCoroutineContext().ensureActive()
        // Finish the bounded explicit deletion once committed; never replay it after interruption.
        withContext(NonCancellable) {
            io { conversations.delete(id) }
            mutable.update { it.copy(chat = it.chat?.takeUnless {chat -> chat.id == id},
                history = it.history.filterNot {row -> row.id == id}, historyMatches = it.historyMatches.filterNot {row -> row.id == id}) }
            onDeleted()
            if (labels != null) io {organization.reset(id, labels)}
        }
        refresh()
    }
    internal suspend fun previewPrompt(expected: LocalConversation?, modelHash: String, question: String,
        beforeExchange: Int? = null): LocalPromptPreview =
        previewPromptFor(expected, modelHash, question, expected?.reviewedSummary?.text) {
        val exchanges = expected?.exchanges.orEmpty()
        when {
            beforeExchange == null -> exchanges
            beforeExchange in exchanges.indices -> exchanges.take(beforeExchange)
            else -> null
        }
    }

    internal suspend fun previewSelectedPrompt(source: LocalConversation, selected: List<Int>, question: String): LocalPromptPreview =
        previewPromptFor(source, source.modelSha256, question) { source.selectedContext(selected) }

    internal suspend fun previewSummaryPrompt(source: LocalConversation, summary: String, question: String): LocalPromptPreview =
        previewPromptFor(source, source.modelSha256, question, summary) {
            emptyList<LocalExchange>().takeIf { summary.isValidReviewedSummary() }
        }

    private suspend fun previewPromptFor(expected: LocalConversation?, modelHash: String, question: String,
        reviewedSummary: String? = null, history: () -> List<LocalExchange>?): LocalPromptPreview = lock.withLock {
        val state = mutable.value
        if (leaving || state.busy || state.chat != expected || state.loaded?.sha256 != modelHash ||
            expected?.let { it.modelSha256 != modelHash } == true ||
            question.isBlank() || question.toByteArray().size > 8192) return@withLock LocalPromptPreview.Unavailable
        try {
            val rows = history() ?: return@withLock LocalPromptPreview.Unavailable
            val budget = measurePrompt(localChatPrompt(rows, question, reviewedSummary), generationSettings())
            if (budget == null) LocalPromptPreview.Unavailable else LocalPromptPreview.Exact(budget)
        } catch (e: LocalEngineException) {
            if (e.code == 2) LocalPromptPreview.TooLarge else LocalPromptPreview.Unavailable
        } catch (_: IllegalArgumentException) {
            LocalPromptPreview.Unavailable
        } catch (_: LinkageError) {
            LocalPromptPreview.Unavailable
        }
    }

    private suspend fun measurePrompt(messages: List<LocalChatMessage>, settings: LocalGenerationSettings): LocalPromptBudget? {
        if (messages.sumOf { it.text.toByteArray().size } > LOCAL_PROMPT_BYTE_LIMIT) throw LocalEngineException(2)
        return checkNotNull(engine).countPromptTokens(messages)?.let {
            LocalPromptBudget(it, settings.contextTokens, settings.maxOutputTokens)
        }
    }

    private suspend fun validatePrompt(messages: List<LocalChatMessage>, settings: LocalGenerationSettings) {
        if (measurePrompt(messages, settings)?.fits == false) throw LocalEngineException(2)
        currentCoroutineContext().ensureActive()
    }

    fun send(question: String) = send(question) {}
    fun send(question: String, onPublished: (LocalConversation) -> Unit) = action("generating") {
        require(question.isNotBlank() && question.toByteArray().size <= 8192)
        val model = mutable.value.loaded ?: throw LocalEngineException(4)
        val prior = mutable.value.chat ?: LocalConversation(modelSha256 = model.sha256, updatedAt = System.currentTimeMillis())
        require(prior.modelSha256 == model.sha256 && prior.exchanges.size < LocalConversationStore.MAX_TURNS)
        val settings = generationSettings()
        val messages = localChatPrompt(prior.exchanges, question, prior.reviewedSummary?.text)
        validatePrompt(messages, settings)
        runGeneration(messages, {
            val pending = prior.copy(updatedAt = System.currentTimeMillis(),
                exchanges = prior.exchanges + LocalExchange(question, state = LocalReplyState.GENERATING, generation = settings))
            conversations.save(pending)
            pending
        }, onPublished)
    }

    /** Each explicit action creates a child; no original reply is overwritten or retried. */
    fun branch(expected: LocalConversation, exchangeIndex: Int, kind: LocalBranchKind,
        editedQuestion: String? = null, onSelected: (LocalConversation) -> Unit = {}) =
        action(if (kind == LocalBranchKind.FORK) "reading" else "generating") {
            require(mutable.value.chat == expected)
            if (kind == LocalBranchKind.FORK) {
                publishCopiedBranch(onSelected) {
                    conversations.createBranch(expected, exchangeIndex, kind, System.currentTimeMillis())
                }
            } else {
                val loaded = mutable.value.loaded ?: throw LocalEngineException(4)
                require(loaded.sha256 == expected.modelSha256)
                val question = kind.generationQuestion(expected.exchanges[exchangeIndex], editedQuestion)
                require(question.isNotBlank() && question.toByteArray().size <= 8192)
                val settings = generationSettings()
                val messages = localChatPrompt(expected.exchanges.take(exchangeIndex), question, expected.reviewedSummary?.text)
                validatePrompt(messages, settings)
                runGeneration(messages, {
                    conversations.createBranch(expected, exchangeIndex, kind, System.currentTimeMillis(), editedQuestion, settings)
                }, onSelected)
            }
        }

    internal fun continueWithSelectedTurns(expected: LocalConversation, selected: List<Int>, onSelected: (LocalConversation) -> Unit) {
        val snapshot = selected.toList()
        action("reading") {
            require(mutable.value.chat == expected)
            publishCopiedBranch(onSelected) {
                conversations.createSelectedContinuation(expected, snapshot, System.currentTimeMillis())
            }
        }
    }

    internal fun continueWithReviewedSummary(
        expected: LocalConversation, selected: List<Int>, text: String, onSelected: (LocalConversation) -> Unit
    ) {
        val snapshot = selected.toList()
        action("reading") {
            require(mutable.value.chat == expected)
            publishCopiedBranch(onSelected) {
                conversations.createSummaryContinuation(expected, snapshot, text, System.currentTimeMillis())
            }
        }
    }

    internal fun draftContinuationSummary(
        expected: LocalConversation, selected: List<Int>, onPrepared: (LocalSummaryDraft) -> Unit
    ) {
        val snapshot = selected.toList()
        action("summarizing") {
            require(mutable.value.chat == expected)
            if (mutable.value.loaded?.sha256 != expected.modelSha256) throw LocalEngineException(4)
            val source = io { conversations.load(expected.id) }
            require(source == expected)
            val messages = localSummaryDraftPrompt(source, snapshot)
            val settings = generationSettings().copy(maxOutputTokens = LOCAL_SUMMARY_OUTPUT_TOKENS)
            validatePrompt(messages, settings)
            var text = ""
            val end = checkNotNull(engine).generate(messages, settings.maxOutputTokens) { text = it }
            currentCoroutineContext().ensureActive()
            if (end == LocalGenerationEnd.CANCELLED) return@action
            if (text.isValidReviewedSummary()) {
                onPrepared(LocalSummaryDraft(text, end == LocalGenerationEnd.TOKEN_LIMIT))
            } else {
                mutable.update { it.copy(error = "summary_unusable") }
            }
        }
    }

    private suspend fun publishCopiedBranch(onSelected: (LocalConversation) -> Unit, create: () -> LocalConversation) {
        var child: LocalConversation? = null
        try { io { child = create() } }
        finally {
            // Publication may complete before cancellation reaches the main dispatcher.
            child?.let { published -> withContext(NonCancellable) {
                mutable.update { it.copy(chat = published) }
                onSelected(published)
                refresh()
            } }
        }
    }

    /** Atomically publish the pending question before calling native inference, including branches. */
    private suspend fun runGeneration(messages: List<LocalChatMessage>, createPending: () -> LocalConversation,
        onPublished: (LocalConversation) -> Unit) {
        var pending: LocalConversation? = null
        var notified = false
        var reply = ""
        var terminal = LocalReplyState.FAILED
        var performance: LocalGenerationResult? = null
        try {
            io { pending = createPending() }
            currentCoroutineContext().ensureActive()
            val active = checkNotNull(pending)
            mutable.update { it.copy(chat = active, performance = it.performance.copy(lastReply = null)) }
            notified = true; onPublished(active)
            currentCoroutineContext().ensureActive()
            val result = engine!!.generateMeasured(messages, active.exchanges.last().generation!!.maxOutputTokens) { text ->
                reply = text
                mutable.update { it.copy(chat = active.copy(exchanges = active.exchanges.dropLast(1) + active.exchanges.last().copy(reply = text))) }
            }
            terminal = when (result.end) {
                LocalGenerationEnd.COMPLETE -> LocalReplyState.COMPLETE
                LocalGenerationEnd.CANCELLED -> LocalReplyState.CANCELLED
                LocalGenerationEnd.TOKEN_LIMIT -> LocalReplyState.TOKEN_LIMIT
            }
            performance = result.takeIf { it.end != LocalGenerationEnd.CANCELLED && it.measurement != null }
        } catch (e: CancellationException) { terminal = LocalReplyState.CANCELLED; throw e }
        finally {
            pending?.let { published ->
                val completed = published.copy(exchanges = published.exchanges.dropLast(1) + published.exchanges.last().copy(reply = reply, state = terminal))
                withContext(NonCancellable) {
                    io { conversations.save(completed) }
                    mutable.update { it.copy(chat = completed, performance = it.performance.copy(lastReply = performance)) }
                    if (!notified) onPublished(completed)
                    refresh()
                }
            }
        }
    }

    fun benchmark(modelSha256: String) = action("benchmarking") {
        require(mutable.value.loaded?.sha256 == modelSha256)
        val settings = generationSettings()
        val output = minOf(settings.maxOutputTokens, LOCAL_BENCHMARK_OUTPUT_CAP)
        val prompt = localChatPrompt(emptyList(), LOCAL_BENCHMARK_PROMPT)
        mutable.update { it.copy(performance = it.performance.copy(benchmark = null)) }
        var outcome = LocalBenchmarkResult(LocalBenchmarkEnd.FAILED)
        try {
            val sample = withTimeoutOrNull(LOCAL_BENCHMARK_TIMEOUT_MILLIS) {
                validatePrompt(prompt, settings.copy(maxOutputTokens = output))
                checkNotNull(engine).generateMeasured(prompt, output) {}
            }
            outcome = when (sample?.end) {
                null -> LocalBenchmarkResult(LocalBenchmarkEnd.TIMED_OUT)
                LocalGenerationEnd.CANCELLED -> LocalBenchmarkResult(LocalBenchmarkEnd.STOPPED)
                LocalGenerationEnd.COMPLETE -> LocalBenchmarkResult(LocalBenchmarkEnd.COMPLETE, sample.measurement)
                LocalGenerationEnd.TOKEN_LIMIT -> LocalBenchmarkResult(LocalBenchmarkEnd.TOKEN_LIMIT, sample.measurement)
            }
        } catch (e: CancellationException) {
            outcome = LocalBenchmarkResult(LocalBenchmarkEnd.STOPPED)
            throw e
        } finally {
            mutable.update { it.copy(performance = it.performance.copy(benchmark = outcome)) }
        }
    }

    private fun generationSettings(): LocalGenerationSettings = checkNotNull(mutable.value.loadedSettings)

    fun help(topic: LocalHelpTopic, observations: LocalHelpObservations?) = action("generating") {
        val model = mutable.value.loaded ?: throw LocalEngineException(4)
        val pending = LocalHelpResult(topic, model.sha256, observations, LocalSetupHelper.sources(topic))
        mutable.update {it.copy(helper = pending)}
        var text = ""
        var terminal = LocalReplyState.FAILED
        try {
            // The untrusted output contains IDs only; displayed instructions come from the reviewed guide.
            terminal = when(engine!!.selectHelperChecks(LocalSetupHelper.messages(topic, observations),
                LocalCheckChoices.from(LocalSetupHelper.checks(topic, observations).map { it.id }), maxTokens = 96) {value -> text = value}) {
                LocalGenerationEnd.COMPLETE -> LocalReplyState.COMPLETE
                LocalGenerationEnd.CANCELLED -> LocalReplyState.CANCELLED
                LocalGenerationEnd.TOKEN_LIMIT -> LocalReplyState.TOKEN_LIMIT
            }
        } catch (e: CancellationException) {terminal = LocalReplyState.CANCELLED; throw e}
        finally {
            val checks = if(terminal == LocalReplyState.COMPLETE) LocalSetupHelper.selection(text, topic, observations) else null
            val accepted = checks != null
            val rejected = terminal !in setOf(LocalReplyState.CANCELLED, LocalReplyState.FAILED) && !accepted
            mutable.update {it.copy(helper = pending.copy(checks = checks.orEmpty(), state = if(rejected) LocalReplyState.FAILED else terminal),
                error = if(rejected) "helper_unverified" else it.error)}
        }
    }

    /** Leaving the UI cancels work and releases weights; it never submits a replacement request. */
    fun leave() {
        if (leaving) return
        leaving = true; guard.set(owner, true)
        mutable.update { it.copy(busy = true, status = "unloading") }; cancel()
        scope.launch {
            lock.withLock {
                try { unloadModel() }
                finally {
                    mutable.update { it.copy(busy = false, status = "idle", transfer = null, helper = null, activeDownload = null) }
                    leaving = false; guard.set(owner, false)
                }
            }
        }
    }

    private fun action(status: String, activeDownload: CuratedPhoneModel? = null, block: suspend () -> Unit) {
        if (mutable.value.busy || leaving) return
        mutable.update { it.copy(busy = true, status = status, error = null, transfer = null, activeDownload = activeDownload) }
        guard.set(owner, true)
        val next = scope.launch(start = CoroutineStart.LAZY) {
            lock.withLock {
                try {
                    ensureHistoryReady()
                    block()
                }
                catch (_: CancellationException) {
                    if (status == "downloading" || status == "importing" ||
                        status == "queuing_download" || status == "discarding_download") reconcileModelLibrary()
                    if (status == "organizing") withContext(NonCancellable) {
                        // Cancellation can lose the return after publication.
                        try {
                            ensureHistoryReady()
                            refresh()
                        } catch (e: Exception) { mutable.update { it.copy(error = failure(e)) } }
                    }
                }
                catch (e: Exception) {
                    if (status == "downloading" || status == "queuing_download" || status == "discarding_download") reconcileModelLibrary()
                    mutable.update { it.copy(error = when {
                        e is LocalMarkdownExportException && e.reason == LocalMarkdownExportFailure.CHANGED -> "history_export_changed"
                        status == "preparing_export" -> "history_export_prepare"
                        status == "searching" && e is LocalHistoryException -> "history_search"
                        e is LocalHistoryOrganizationChangedException -> "history_organization_changed"
                        else -> failure(e)
                    }) }
                }
                catch (_: LinkageError) { mutable.update { it.copy(error = "native_unavailable") } }
            }
        }
        job = next
        next.invokeOnCompletion {
            if (!leaving) {
                mutable.update { it.copy(busy = false, status = "idle", transfer = null, activeDownload = null) }
                guard.set(owner, false)
            }
        }
        next.start()
    }
    /** Cancellation can lose the return value after atomic publication. Read actual files before idle. */
    private suspend fun reconcileModelLibrary() = withContext(NonCancellable) {
        try {
            val library = readModelLibrary()
            mutable.update { library.applyTo(it) }
        } catch (e: Exception) {
            mutable.update { it.copy(error = failure(e)) }
        }
    }
    private fun failure(e: Exception): String = when (e) {
        is LocalModelStorageException -> e.reason
        is LocalModelFormatException -> e.reason
        is LocalEngineException -> "engine_${e.code}"
        is LocalHistoryException, is LocalHistoryRestoreRecoveryException -> "history"
        is LocalHistoryOrganizationException -> "history_organization"
        else -> "operation_failed"
    }

    private suspend fun ensureHistoryReady() {
        if (restoreRecoveryNeeded) {
            io { historyRestore.recover() }
            restoreRecoveryNeeded = false
        }
        if (!recovered) {
            io {
                val context = currentCoroutineContext()
                try {
                    historyBackupFiles.discardAbandonedDocuments()
                } catch (_: java.io.IOException) {
                    // Backup preparation reports staging errors. History remains readable.
                }
                conversations.recoverInterrupted { context.ensureActive() }
            }
            recovered = true
        }
    }

    private suspend fun refresh() {
        val library = readModelLibrary()
        val history = io { val context = currentCoroutineContext(); organization.history { context.ensureActive() } }
        val query = mutable.value.historyQuery
        val matches = if(query == null) emptyList() else io {
            val context = currentCoroutineContext()
            LocalHistorySearch(conversations, organization).find(query) { context.ensureActive() }
        }
        mutable.update { library.applyTo(it).copy(history = history, historyMatches = matches) }
    }

    private data class ModelLibrary(
        val models: List<InstalledLocalModel>,
        val performance: Map<String, LocalModelPerformance>,
        val partials: Map<CuratedPhoneModel, Long>,
        val availableBytes: Long,
        val queued: List<CuratedPhoneModel>?
    ) {
        fun applyTo(state: LocalAiState) = state.copy(models = models, modelPerformance = performance,
            downloadPartials = partials, modelStorageAvailableBytes = availableBytes,
            queuedDownloads = queued.orEmpty(), downloadQueueUnavailable = queued == null)
    }

    private suspend fun readModelLibrary() = io {
        val context = currentCoroutineContext()
        val models = store.models { context.ensureActive() }
        val performance = buildMap {
            for (model in models) {
                context.ensureActive()
                store.performance(model)?.let { put(model.sha256, it) }
            }
        }
        val partials = downloads.mapValues { (_, download) -> download.partialBytes() }
        val queued = try {
            downloadQueue.pending(models.mapTo(mutableSetOf()) { it.sha256 })
        } catch (e: LocalModelStorageException) {
            if (e.reason != "download_queue_unreadable") throw e
            null
        }
        ModelLibrary(models, performance, partials, store.usableBytes(), queued)
    }

    private suspend fun <T> io(block: suspend () -> T) = withContext(ioDispatcher) { block() }
}
