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
    val helper: LocalHelpResult? = null)

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
    private val downloads = LocalModelDownload(store, ioDispatcher = ioDispatcher)
    private var engine: LocalInference? = null
    private var job: Job? = null
    private var leaving = false
    private var recovered = false
    private val mutable = MutableStateFlow(LocalAiState())
    val state = mutable.asStateFlow()

    init { action("reading") { refresh() } }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun cancel() { engine?.cancel(); job?.cancel() }
    fun refreshLibrary() = action("reading") { refresh() }
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
        engine?.unload(); mutable.update { it.copy(loaded = null) }
        // Measure after releasing the old weights so a switch can reclaim their RAM.
        // This conservative estimate cannot guarantee that native allocation will succeed.
        if (freeMemory() < model.metadata.bytes * 2 + 256L * 1024 * 1024) throw LocalModelStorageException("insufficient_memory")
        val file = io { val current = currentCoroutineContext(); store.verifiedFile(model.sha256) { current.ensureActive() } }
        val target = engine ?: engineFactory().also { engine = it }
        target.load(file, context, minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)))
        mutable.update { it.copy(loaded = model) }
    }
    fun unload() = action("unloading") { engine?.unload(); mutable.update { it.copy(loaded = null) } }
    fun deleteModel(model: InstalledLocalModel) = action("deleting") {
        if (mutable.value.loaded?.sha256 == model.sha256) { engine?.unload(); mutable.update { it.copy(loaded = null) } }
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
    fun openChat(id: String) = action("reading") { val chat = io { conversations.load(id) }; mutable.update { it.copy(chat = chat) } }
    fun deleteChat(id: String) = action("deleting") {
        io { conversations.delete(id) }
        mutable.update { if (it.chat?.id == id) it.copy(chat = null) else it }; refresh()
    }
    fun send(question: String) = action("generating") {
        require(question.isNotBlank() && question.toByteArray().size <= 8192)
        val model = mutable.value.loaded ?: throw LocalEngineException(4)
        val prior = mutable.value.chat ?: LocalConversation(modelSha256 = model.sha256, updatedAt = System.currentTimeMillis())
        require(prior.modelSha256 == model.sha256 && prior.exchanges.size < 30)
        val pending = prior.copy(updatedAt = System.currentTimeMillis(), exchanges = prior.exchanges + LocalExchange(question, state = LocalReplyState.GENERATING))
        var reply = ""
        var terminal = LocalReplyState.FAILED
        var persisted = false
        try {
            // Cancellation can arrive after the atomic save but before dispatch back to Main.
            io { conversations.save(pending); persisted = true }
            mutable.update { it.copy(chat = pending) }
            val messages = buildList {
                add(LocalChatMessage("system", "You are an on-device text assistant in Signal Grove. You have no access to Hermes, servers, projects, credentials or tools. Never claim to have performed an action. Give advice as advice."))
                prior.exchanges.filter { it.state in setOf(LocalReplyState.COMPLETE, LocalReplyState.TOKEN_LIMIT) }.forEach {
                    add(LocalChatMessage("user", it.question)); if (it.reply.isNotBlank()) add(LocalChatMessage("assistant", it.reply))
                }
                add(LocalChatMessage("user", question))
            }
            if (messages.sumOf {it.text.toByteArray().size} > 65_536) throw LocalEngineException(2)
            terminal = when (engine!!.generate(messages) { text ->
                reply = text
                mutable.update { it.copy(chat = pending.copy(exchanges = pending.exchanges.dropLast(1) + pending.exchanges.last().copy(reply = text))) }
            }) {
                LocalGenerationEnd.COMPLETE -> LocalReplyState.COMPLETE
                LocalGenerationEnd.CANCELLED -> LocalReplyState.CANCELLED
                LocalGenerationEnd.TOKEN_LIMIT -> LocalReplyState.TOKEN_LIMIT
            }
        } catch (e: CancellationException) { terminal = LocalReplyState.CANCELLED; throw e }
        finally {
            val completed = pending.copy(exchanges = pending.exchanges.dropLast(1) + LocalExchange(question, reply, terminal))
            if (persisted) withContext(NonCancellable) {
                io { conversations.save(completed) }
                mutable.update { it.copy(chat = completed) }; refresh()
            }
        }
    }

    fun help(topic: LocalHelpTopic, observations: LocalHelpObservations?) = action("generating") {
        val model = mutable.value.loaded ?: throw LocalEngineException(4)
        val pending = LocalHelpResult(topic, model.sha256, observations, LocalSetupHelper.sources(topic))
        mutable.update {it.copy(helper = pending)}
        var text = ""
        var terminal = LocalReplyState.FAILED
        try {
            // The untrusted output contains IDs only; displayed instructions come from the reviewed guide.
            terminal = when(engine!!.generate(LocalSetupHelper.messages(topic, observations), maxTokens = 96) {value -> text = value}) {
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
                try { block() }
                catch (_: CancellationException) {
                    if (status == "downloading" || status == "importing") reconcileStoppedTransfer()
                }
                catch (e: Exception) { mutable.update { it.copy(error = failure(e)) } }
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
        is LocalHistoryException -> "history"
        else -> "operation_failed"
    }

    private suspend fun refresh() {
        if (!recovered) { io { val context = currentCoroutineContext(); conversations.recoverInterrupted { context.ensureActive() } }; recovered = true }
        val models = io { val context = currentCoroutineContext(); store.models { context.ensureActive() } }
        val history = io { val context = currentCoroutineContext(); conversations.list { context.ensureActive() } }
        val partial = downloads.partialBytes()
        mutable.update { it.copy(models = models, history = history, partialBytes = partial) }
    }
    private suspend fun <T> io(block: suspend () -> T) = withContext(ioDispatcher) { block() }
}
