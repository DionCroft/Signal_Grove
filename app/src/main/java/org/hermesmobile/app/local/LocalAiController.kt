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

data class LocalAiState(val models: List<InstalledLocalModel> = emptyList(), val history: List<LocalConversationSummary> = emptyList(),
    val chat: LocalConversation? = null, val loaded: InstalledLocalModel? = null, val busy: Boolean = false,
    val status: String = "idle", val error: String? = null, val partialBytes: Long = 0, val transfer: LocalTransferProgress? = null,
    val helper: LocalHelpResult? = null, val historyQuery: LocalHistoryQuery? = null,
    val historyMatches: List<LocalConversationSummary> = emptyList())

/** One instance per application process, shared across activity recreation. No Hermes client or credential store. */
class LocalAiController(private val modelRoot: File, historyRoot: File, private val nativeDirectory: String,
    val supported: Boolean, private val freeMemory: () -> Long, private val guard: UpdateWorkGuard,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
    private val downloads = LocalModelDownload(store, ioDispatcher = ioDispatcher)
    private var engine: LocalInference? = null
    private var loadedConfiguration: Pair<Int, Int>? = null
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
                labels != expected.organization) {
                throw LocalMarkdownExportException(LocalMarkdownExportFailure.CHANGED)
            }
            conversations.markdownExport(conversation, labels?.preferences?.title, checkpoint)
        }
        onPrepared(document)
    }

    internal fun prepareHistoryRestore(
        archive: LocalHistoryBackupArchive.Inspection,
        onPrepared: (LocalHistoryBackupRestorePlan) -> Unit
    ) = action("preparing_restore") {
        val plan = io {
            val context = currentCoroutineContext()
            historyRestore.prepare(archive) { context.ensureActive() }
        }
        onPrepared(plan)
    }

    internal fun restoreHistory(plan: LocalHistoryBackupRestorePlan) = action("restoring_history") {
        restoreRecoveryNeeded = true
        io {
            val context = currentCoroutineContext()
            historyRestore.restore(plan) { context.ensureActive() }
        }
        ensureHistoryReady()
        refresh()
    }

    fun clearHistorySearch() {
        if (mutable.value.busy || leaving) return
        mutable.update { it.copy(historyQuery = null, historyMatches = emptyList(),
            error = it.error.takeUnless {code -> code == "history_search"}) }
    }

    fun download(licenceReviewed: Boolean) = action("downloading") {
        check(supported)
        downloads.download(licenceReviewed) { value -> mutable.update { it.copy(transfer = value) } }
        refresh()
    }
    fun discardDownload() = action("reading") { downloads.discardPartial(); refresh() }
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
    fun load(model: InstalledLocalModel, context: Int = 2048) = action("loading") {
        check(supported)
        require(context in 512..4096 && context <= model.metadata.trainedContext)
        loadedConfiguration = null
        engine?.unload(); mutable.update { it.copy(loaded = null) }
        // Measure after releasing the old weights so a switch can reclaim their RAM.
        // This conservative estimate cannot guarantee that native allocation will succeed.
        if (freeMemory() < model.metadata.bytes * 2 + 256L * 1024 * 1024) throw LocalModelStorageException("insufficient_memory")
        val file = io { val current = currentCoroutineContext(); store.verifiedFile(model.sha256) { current.ensureActive() } }
        val target = engine ?: engineFactory().also { engine = it }
        val threads = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        target.load(file, context, threads)
        loadedConfiguration = context to threads
        mutable.update { it.copy(loaded = model) }
    }
    fun unload() = action("unloading") { engine?.unload(); loadedConfiguration = null; mutable.update { it.copy(loaded = null) } }
    fun deleteModel(model: InstalledLocalModel) = action("deleting") {
        if (mutable.value.loaded?.sha256 == model.sha256) { engine?.unload(); loadedConfiguration = null; mutable.update { it.copy(loaded = null) } }
        io { store.delete(model.sha256) }; refresh()
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
    fun send(question: String) = send(question) {}
    fun send(question: String, onPublished: (LocalConversation) -> Unit) = action("generating") {
        require(question.isNotBlank() && question.toByteArray().size <= 8192)
        val model = mutable.value.loaded ?: throw LocalEngineException(4)
        val prior = mutable.value.chat ?: LocalConversation(modelSha256 = model.sha256, updatedAt = System.currentTimeMillis())
        require(prior.modelSha256 == model.sha256 && prior.exchanges.size < LocalConversationStore.MAX_TURNS)
        runGeneration({
            val pending = prior.copy(updatedAt = System.currentTimeMillis(),
                exchanges = prior.exchanges + LocalExchange(question, state = LocalReplyState.GENERATING, generation = generationSettings()))
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
                var child: LocalConversation? = null
                try { io { child = conversations.createBranch(expected, exchangeIndex, kind, System.currentTimeMillis()) } }
                finally {
                    // Publication may complete before cancellation reaches the main dispatcher.
                    child?.let { published -> withContext(NonCancellable) {
                        mutable.update { it.copy(chat = published) }; onSelected(published); refresh()
                    } }
                }
            } else {
                val loaded = mutable.value.loaded ?: throw LocalEngineException(4)
                require(loaded.sha256 == expected.modelSha256)
                runGeneration({conversations.createBranch(expected, exchangeIndex, kind, System.currentTimeMillis(), editedQuestion, generationSettings())}, onSelected)
            }
        }

    /** Atomically publish the pending question before calling native inference, including branches. */
    private suspend fun runGeneration(createPending: () -> LocalConversation, onPublished: (LocalConversation) -> Unit) {
        var pending: LocalConversation? = null
        var notified = false
        var reply = ""
        var terminal = LocalReplyState.FAILED
        try {
            io { pending = createPending() }
            currentCoroutineContext().ensureActive()
            val active = checkNotNull(pending)
            mutable.update { it.copy(chat = active) }
            notified = true; onPublished(active)
            val messages = buildList {
                add(LocalChatMessage("system", "You are an on-device text assistant in Signal Grove. You have no access to Hermes, servers, projects, credentials or tools. Never claim to have performed an action. Give advice as advice."))
                active.exchanges.dropLast(1).filter { it.state in setOf(LocalReplyState.COMPLETE, LocalReplyState.TOKEN_LIMIT) }.forEach {
                    add(LocalChatMessage("user", it.question)); if (it.reply.isNotBlank()) add(LocalChatMessage("assistant", it.reply))
                }
                add(LocalChatMessage("user", active.exchanges.last().question))
            }
            if (messages.sumOf {it.text.toByteArray().size} > 65_536) throw LocalEngineException(2)
            currentCoroutineContext().ensureActive()
            terminal = when (engine!!.generate(messages, active.exchanges.last().generation!!.maxOutputTokens) { text ->
                reply = text
                mutable.update { it.copy(chat = active.copy(exchanges = active.exchanges.dropLast(1) + active.exchanges.last().copy(reply = text))) }
            }) {
                LocalGenerationEnd.COMPLETE -> LocalReplyState.COMPLETE
                LocalGenerationEnd.CANCELLED -> LocalReplyState.CANCELLED
                LocalGenerationEnd.TOKEN_LIMIT -> LocalReplyState.TOKEN_LIMIT
            }
        } catch (e: CancellationException) { terminal = LocalReplyState.CANCELLED; throw e }
        finally {
            pending?.let { published ->
                val completed = published.copy(exchanges = published.exchanges.dropLast(1) + published.exchanges.last().copy(reply = reply, state = terminal))
                withContext(NonCancellable) {
                    io { conversations.save(completed) }
                    mutable.update { it.copy(chat = completed) }
                    if (!notified) onPublished(completed)
                    refresh()
                }
            }
        }
    }

    private fun generationSettings(): LocalGenerationSettings {
        val (context, threads) = checkNotNull(loadedConfiguration)
        return LocalGenerationSettings(context, threads, 384, engine?.runtimePolicy).also { it.validate() }
    }

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
                try { engine?.unload() }
                finally {
                    loadedConfiguration = null
                    mutable.update { it.copy(loaded = null, busy = false, status = "idle", transfer = null, helper = null) }
                    leaving = false; guard.set(owner, false)
                }
            }
        }
    }

    private fun action(status: String, block: suspend () -> Unit) {
        if (mutable.value.busy || leaving) return
        mutable.update { it.copy(busy = true, status = status, error = null, transfer = null) }; guard.set(owner, true)
        val next = scope.launch(start = CoroutineStart.LAZY) {
            lock.withLock {
                try {
                    ensureHistoryReady()
                    block()
                }
                catch (_: CancellationException) {
                    if (status == "downloading" || status == "importing") reconcileStoppedTransfer()
                    if (status == "organizing" || status == "restoring_history") withContext(NonCancellable) {
                        // Cancellation can lose the return after publication.
                        try {
                            ensureHistoryReady()
                            refresh()
                        } catch (e: Exception) { mutable.update { it.copy(error = failure(e)) } }
                    }
                }
                catch (e: Exception) { mutable.update { it.copy(error = when {
                    e is LocalMarkdownExportException && e.reason == LocalMarkdownExportFailure.CHANGED -> "history_export_changed"
                    status == "preparing_export" -> "history_export_prepare"
                    status == "searching" && e is LocalHistoryException -> "history_search"
                    e is LocalHistoryOrganizationChangedException -> "history_organization_changed"
                    else -> failure(e)
                }) } }
                catch (_: LinkageError) { mutable.update { it.copy(error = "native_unavailable") } }
            }
        }
        job = next
        next.invokeOnCompletion {
            if (!leaving) { mutable.update { it.copy(busy = false, status = "idle", transfer = null) }; guard.set(owner, false) }
        }
        next.start()
    }
    /** Cancellation can lose the return value after atomic publication. Read actual files before idle. */
    private suspend fun reconcileStoppedTransfer() = withContext(NonCancellable) {
        try {
            val models = io { val context = currentCoroutineContext(); store.models { context.ensureActive() } }
            val partial = downloads.partialBytes()
            mutable.update { it.copy(models = models, partialBytes = partial) }
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
        val models = io { val context = currentCoroutineContext(); store.models { context.ensureActive() } }
        val history = io { val context = currentCoroutineContext(); organization.history { context.ensureActive() } }
        val partial = downloads.partialBytes()
        val query = mutable.value.historyQuery
        val matches = if(query == null) emptyList() else io {
            val context = currentCoroutineContext()
            LocalHistorySearch(conversations, organization).find(query) { context.ensureActive() }
        }
        mutable.update { it.copy(models = models, history = history, partialBytes = partial, historyMatches = matches) }
    }
    private suspend fun <T> io(block: suspend () -> T) = withContext(ioDispatcher) { block() }
}
