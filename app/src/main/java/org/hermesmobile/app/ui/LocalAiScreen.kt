package org.hermesmobile.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.hermesmobile.app.LocalAiActivity
import org.hermesmobile.app.local.*

@Composable fun LocalAiEntry(compact:Boolean=false) {
    val context = LocalContext.current
    val open={context.startActivity(Intent(context, LocalAiActivity::class.java))}
    if(compact) IconButton(open, Modifier.size(48.dp).testTag("phone-ai-open")) {Icon(Icons.Outlined.PhoneAndroid,label(Words.PhoneAi))}
    else TextButton(open, Modifier.testTag("phone-ai-open")) {Text(label(Words.PhoneAi))}
}

/** Keep the composer usable on short screens; the complete translated explanation stays one tap away. */
@Composable private fun PhoneDraftNotice(state:String) {
    var details by remember {mutableStateOf(false)}
    val title=if(state=="received") Words.PhoneDraftReady else Words.PhoneDraftNotReady
    val body=if(state=="received") Words.PhoneDraftReceived else Words.PhoneDraftExpired
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    IconButton({focus.clearFocus();keyboard?.hide();details=true},Modifier.testTag("phone-draft-notice")) {
        Icon(if(state=="received") Icons.Outlined.CheckCircle else Icons.Outlined.Info,label(title),tint=if(state=="received") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    }
    if(details) Dialog(onDismissRequest={details=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(label(title),style=MaterialTheme.typography.headlineSmall)
                    Text(label(body),Modifier.testTag("phone-draft-info-text"))
                    Text(label(Words.PhoneBoundary))
                    Text(label(Words.PhoneEntry))
                }
                TextButton({details=false},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("phone-draft-info-close")) {Text(label(Words.Close))}
            }
        }
    }
}

data class LocalAiActions(val send: () -> Unit, val stop: () -> Unit, val newChat: () -> Unit,
    val load: (InstalledLocalModel, Int) -> Unit, val unload: () -> Unit, val remove: (InstalledLocalModel) -> Unit,
    val download: (CuratedPhoneModel) -> Unit, val discard: (CuratedPhoneModel) -> Unit, val chooseFile: () -> Unit,
    val acceptImport: () -> Unit, val cancelImport: () -> Unit, val openChat: (String) -> Unit,
    val deleteChat: (String) -> Unit, val dismissError: () -> Unit, val refresh: () -> Unit = {},
    val help: (LocalHelpTopic, Boolean) -> Unit = {_, _ ->},
    val branch: (LocalConversation, Int, LocalBranchKind, String?) -> Unit = {_, _, _, _ ->},
    val searchHistory: (LocalHistoryQuery) -> Unit = {}, val clearHistorySearch: () -> Unit = {},
    val organize: (LocalConversationSummary, LocalHistoryPreferences) -> Unit = {_, _ ->},
    val exportMarkdown: ((LocalConversationSummary) -> Unit)? = null,
    val exportBackup: (() -> Unit)? = null, val importBackup: (() -> Unit)? = null,
    val previewPrompt: (suspend (LocalConversation?, String, String, Int?) -> LocalPromptPreview)? = null,
    val newChatWithDraft: (() -> Unit)? = null,
    val continueWithSelectedTurns: ((LocalConversation, List<Int>, String, Boolean) -> Unit)? = null,
    val previewSelectedPrompt: (suspend (LocalConversation, List<Int>, String) -> LocalPromptPreview)? = null,
    val continueWithSummary: ((LocalConversation, List<Int>, String, String, Boolean) -> Unit)? = null,
    val draftSummary: ((LocalConversation, List<Int>, (LocalSummaryDraft) -> Unit) -> Unit)? = null,
    val previewSummaryPrompt: (suspend (LocalConversation, String, String) -> LocalPromptPreview)? = null,
    val saveModelPerformance: ((InstalledLocalModel, LocalModelPerformance) -> Unit)? = null,
    val benchmark: ((String) -> Unit)? = null)

@Composable fun LocalAiRoute(vm: LocalAiViewModel, close: () -> Unit) {
    val state by vm.controller.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val handoff by vm.handoffState.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences by (context.applicationContext as org.hermesmobile.app.HermesApplication).preferences.flow
        .collectAsStateWithLifecycle<org.hermesmobile.app.data.AppPreferences?>(initialValue = null)
    val observations = remember(preferences, state.loaded?.sha256) {preferences?.let {
        LocalHelpObservations.capture(it.demo, it.url, android.os.Process.is64Bit(), state.loaded != null, System.currentTimeMillis())
    }}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {vm.pendingImport.value = it}
    val c = vm.controller
    LocalAiScreen(state, c.supported, draft, vm::changeDraft, pending != null,
        LocalAiActions(vm::send, c::cancel, vm::newChat, c::load, c::unload, c::deleteModel,
            { model -> c.download(model, licenceReviewed = true) }, c::discardDownload, {picker.launch(arrayOf("*/*"))}, vm::importSelected,
            {vm.pendingImport.value = null}, vm::openChat, vm::deleteChat, c::dismissError, c::refreshLibrary,
            {topic, share -> c.help(topic, observations.takeIf {share})}, branch = vm::branch,
            searchHistory = c::searchHistory, clearHistorySearch = c::clearHistorySearch, organize = c::organizeChat,
            exportMarkdown = vm::prepareMarkdownExport, exportBackup = vm.historyBackup::selectExport,
            importBackup = vm.historyBackup::selectImport, previewPrompt = c::previewPrompt,
            newChatWithDraft = vm::newChatWithDraft, continueWithSelectedTurns = vm::continueWithSelectedTurns,
            previewSelectedPrompt = c::previewSelectedPrompt, continueWithSummary = vm::continueWithReviewedSummary,
            draftSummary = c::draftContinuationSummary, previewSummaryPrompt = c::previewSummaryPrompt,
            saveModelPerformance = c::saveModelPerformance, benchmark = c::benchmark), close, observations, handoff)
    PhoneMarkdownExportFlow(vm.markdownExport, busy = state.busy)
    PhoneHistoryBackupFlow(vm.historyBackup, busy = state.busy)
}

/** UI consumes only local state and callbacks. It has no Hermes client or profile credentials. */
@Composable fun LocalAiScreen(state: LocalAiState, supported: Boolean, draft: String, changeDraft: (String) -> Unit,
    pendingImport: Boolean, actions: LocalAiActions, close: () -> Unit, observations: LocalHelpObservations? = null, handoffState:String = "") {
    var tab by rememberSaveable {mutableIntStateOf(0)}
    var guide by rememberSaveable {mutableStateOf(false)}
    var offlineSetup by rememberSaveable { mutableStateOf(false) }
    var helper by remember {mutableStateOf(false)}
    var catalogueOpen by rememberSaveable { mutableStateOf(false) }
    var downloadReview by remember { mutableStateOf<CuratedPhoneModel?>(null) }
    var deleteModel by remember {mutableStateOf<InstalledLocalModel?>(null)}
    var deleteChat by remember {mutableStateOf<String?>(null)}
    var discardReview by remember { mutableStateOf<CuratedPhoneModel?>(null) }
    var review by remember {mutableStateOf<String?>(null)}
    var boundary by remember {mutableStateOf(false)}
    var errorDetails by remember {mutableStateOf(false)}
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label(Words.PhoneAi), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if(handoffState.isNotEmpty()) PhoneDraftNotice(handoffState)
                else IconButton({boundary = true}) {Icon(Icons.Outlined.Info, label(Words.PhoneBoundary))}
                IconButton(close, Modifier.testTag("phone-close")) {Icon(Icons.Outlined.Close, label(Words.Close))}
            }
            Row(Modifier.fillMaxWidth()) {
                listOf(Words.Chat, Words.PhoneModels, Words.PhoneHistory).forEachIndexed { index, word ->
                    Tab(selected = tab == index, onClick = {tab = index}, modifier = Modifier.weight(1f).testTag("phone-tab-$index"),
                        text = {Text(label(word))})
                }
            }
            if (!supported) Text(label(Words.PhoneUnsupported), style = MaterialTheme.typography.bodySmall)
            if (state.busy) {
                Text(label(phoneStatus(state)), Modifier.testTag("phone-working"), style = MaterialTheme.typography.labelSmall)
                state.transfer?.let { p ->
                    LinearProgressIndicator(progress = {(p.bytes.toDouble() / p.total).toFloat().coerceIn(0f, 1f)}, modifier = Modifier.fillMaxWidth())
                    Text("${p.bytes / 1048576} / ${p.total / 1048576} MiB", style = MaterialTheme.typography.labelSmall)
                }
                if (state.status != "unloading") TextButton(actions.stop, Modifier.testTag("phone-stop")) {Text(label(Words.Stop))}
            }
            state.error?.let {
                TextButton({errorDetails = true}, Modifier.testTag("phone-error")) {Text(label(Words.PhoneIssue), color = MaterialTheme.colorScheme.error)}
            }
            when (tab) {
                0 -> PhoneChat(state, draft, changeDraft, actions, { offlineSetup = true }) { review = it }
                1 -> PhoneModelLibrary(state, supported, actions,
                    openCatalogue = { catalogueOpen = true },
                    openSetup = { offlineSetup = true },
                    remove = { deleteModel = it })
                2 -> PhoneHistory(state, actions, {actions.openChat(it); tab = 0}, {deleteChat = it})
            }
            Row {
                TextButton({guide = true}, Modifier.weight(1f).testTag("phone-setup-guide")) {Text(label(Words.SetupGuide))}
                TextButton({helper = true}, Modifier.weight(1f).testTag("phone-helper-open")) {Text(label(Words.PhoneHelper))}
            }
        }
    }
    if (guide) SetupGuide {guide = false}
    if (offlineSetup) {
        PhoneOfflineSetup(
            state = state,
            supported = supported,
            openCatalogue = {
                offlineSetup = false
                tab = 1
                catalogueOpen = true
            },
            chooseFile = {
                offlineSetup = false
                tab = 1
                actions.chooseFile()
            },
            openModels = {
                offlineSetup = false
                tab = 1
            },
            openChat = {
                offlineSetup = false
                tab = 0
            },
            close = { offlineSetup = false }
        )
    }
    if (boundary) AlertDialog(onDismissRequest = {boundary = false}, title = {Text(label(Words.PhoneAi))},
        text = {Text(label(Words.PhoneBoundary) + "\n\n" + label(Words.PhoneEntry), Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))},
        confirmButton = {TextButton({boundary = false}) {Text(label(Words.Close))}})
    if (errorDetails && state.error != null) AlertDialog(onDismissRequest = {errorDetails = false}, title = {Text(label(Words.PhoneIssue))},
        text = {Text(label(phoneError(state.error)), Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))},
        confirmButton = {TextButton({errorDetails = false; actions.dismissError()}) {Text(label(Words.Close))}})
    if (helper) LocalHelpScreen(state, observations, actions.help, actions.stop) {
        helper = false
        if(state.busy && state.helper?.state == LocalReplyState.GENERATING) actions.stop()
    }
    if (catalogueOpen && downloadReview == null && discardReview == null && !pendingImport) {
        PhoneModelCatalogue(state, supported, { downloadReview = it }, { discardReview = it }) {
            catalogueOpen = false
        }
    }
    if (pendingImport) {
        PhoneLicence(label(Words.PhoneImportLicence), state.busy, actions.acceptImport, actions.cancelImport)
    } else downloadReview?.let { model ->
        val licence = remember(model, context) {
            context.assets.open(model.licenceAsset).bufferedReader().use { it.readText() }
        }
        key(model) {
            PhoneLicence(licence, state.busy, accept = {
                downloadReview = null
                catalogueOpen = false
                actions.download(model)
            }, close = { downloadReview = null }, model = model)
        }
    }
    deleteModel?.let { model -> PhoneConfirm(label(Words.PhoneDeleteModel), state.busy, {actions.remove(model); deleteModel = null}) {deleteModel = null} }
    deleteChat?.let { id -> PhoneConfirm(label(Words.PhoneDeleteChat), state.busy, {actions.deleteChat(id); deleteChat = null}) {deleteChat = null} }
    discardReview?.let { model ->
        PhoneConfirm(label(Words.PhoneDiscard) + "\n" + model.displayName, state.busy, {
            discardReview = null
            actions.discard(model)
        }) { discardReview = null }
    }
    review?.let { initial ->
        var text by remember(initial) {mutableStateOf(initial.take(8192))}
        val prefix = label(Words.PhoneReviewPrefix)
        AlertDialog(onDismissRequest = {review = null}, title = {Text(label(Words.PhoneReview))}, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(label(Words.PhoneReviewHelp)); OutlinedTextField(text, {text = it.take(8192)}, Modifier.testTag("phone-handoff-draft"))
            }
        }, confirmButton = {TextButton({
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(prefix, "$prefix\n$text")); review = null
        }, enabled = text.isNotBlank(), modifier = Modifier.testTag("phone-handoff-copy")) {Text(label(Words.PhoneCopy))}},
            dismissButton = {TextButton({review = null}) {Text(label(Words.Cancel))}})
    }
}

@Composable private fun ColumnScope.PhoneChat(
    state: LocalAiState,
    draft: String,
    change: (String) -> Unit,
    actions: LocalAiActions,
    openSetup: () -> Unit,
    review: (String) -> Unit
) {
    var branchReview by remember { mutableStateOf<PhoneBranchReview?>(null) }
    var continuation by remember { mutableStateOf<PhoneContinuationReview?>(null) }
    var generationReview by remember { mutableStateOf<PhoneGenerationReview?>(null) }
    val loaded = state.loaded
    val mismatch = loaded != null && state.chat?.let {it.modelSha256 != loaded.sha256} == true
    val full = (state.chat?.exchanges?.size ?: 0) >= LocalConversationStore.MAX_TURNS
    val budget = if (branchReview == null && continuation == null) rememberPhonePromptPreview(state, draft.trim(), actions) else null
    val contextFull = budget.blocksGeneration()
    val copyDraft = actions.newChatWithDraft?.takeIf {
        !state.busy && loaded != null && state.chat?.hasSavedContent == true &&
            state.history.size < LocalConversationStore.MAX_CHATS && draft.isNotBlank()
    }
    Row(Modifier.fillMaxWidth()) {
        Text(loaded?.metadata?.name.orEmpty(), Modifier.weight(1f).testTag("phone-loaded"), style = MaterialTheme.typography.labelMedium)
        val source = state.chat
        if (actions.continueWithSelectedTurns != null && source != null && source.exchanges.any { it.retainedForPrompt }) {
            IconButton({ continuation = PhoneContinuationReview(source, draft) },
                enabled = !state.busy && state.history.size < LocalConversationStore.MAX_CHATS,
                modifier = Modifier.testTag("phone-select-context")) {
                Icon(Icons.Outlined.AccountTree, label(Words.PhoneSelectContext))
            }
        }
        IconButton(actions.newChat, enabled = loaded != null && !state.busy, modifier = Modifier.testTag("phone-new-chat")) {Icon(Icons.Outlined.Add, label(Words.NewChat))}
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("phone-messages"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val exchanges = state.chat?.exchanges.orEmpty()
        state.chat?.origin?.let { origin -> item {
            Column {
                val indices = origin.selectedExchanges
                if (indices == null && origin.kind != LocalBranchKind.REVIEWED_SUMMARY) {
                    Text(label(Words.PhoneBranchedAt).replace("{number}", (origin.exchangeIndex + 1).toString()),
                        style = MaterialTheme.typography.labelSmall)
                } else if (indices != null) {
                    val numbers = indices.joinToString(", ") { (it + 1).toString() }
                    Text(label(Words.PhoneContextFromTurns).replace("{turns}", numbers),
                        Modifier.testTag("phone-context-origin"), style = MaterialTheme.typography.labelSmall)
                }
                val linkedParent = origin.linkedParentId
                if (linkedParent != null && state.history.any { it.id == linkedParent }) TextButton({actions.openChat(linkedParent)}, enabled = !state.busy,
                    modifier = Modifier.testTag("phone-open-parent")) { Text(label(Words.PhoneOpenParent)) }
            }
        } }
        state.chat?.reviewedSummary?.let { summary -> item(key = "reviewed-summary") {
            PhoneReviewedSummary(summary)
        } }
        if (loaded == null) item {
            Column {
                Text(label(Words.PhoneNotLoaded))
                TextButton(openSetup, Modifier.testTag("phone-offline-setup-chat-entry")) {
                    Text(label(Words.PhoneOfflineSetup))
                }
            }
        }
        if (mismatch || full) item {Text(label(if(mismatch) Words.PhoneMismatch else Words.PhoneTurnLimit), style = MaterialTheme.typography.bodySmall)}
        if (state.chat?.hasSavedContent != true) item {Text(label(Words.PhoneEmpty))}
        if (state.history.size >= LocalConversationStore.MAX_CHATS) item { Text(label(Words.PhoneBranchHistoryFull)) }
        items(exchanges.size) { index -> val row = exchanges[index]
            HermesCard {
                SelectionContainer {Text(row.question)}
                HorizontalDivider()
                PhoneReply(row.reply, row.state, index)
                Text(label(phoneReplyState(row.state)), style = MaterialTheme.typography.labelSmall)
                if (row.reply.isNotBlank() && row.state != LocalReplyState.GENERATING) TextButton({review(row.reply)}) {Text(label(Words.PhoneReview))}
                TextButton({generationReview = PhoneGenerationReview(state.chat!!.modelSha256, row.generation)},
                    modifier = Modifier.testTag("phone-generation-$index")) {Text(label(Words.PhoneGenerationSettings))}
                if (row.state !in setOf(LocalReplyState.COMPLETE, LocalReplyState.TOKEN_LIMIT, LocalReplyState.GENERATING))
                    Text(label(Words.PhoneForkNeedsReply), style = MaterialTheme.typography.bodySmall)
                val source = state.chat!!
                val allowed = !state.busy && row.state != LocalReplyState.GENERATING && state.history.size < LocalConversationStore.MAX_CHATS
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({branchReview = PhoneBranchReview(source, index, LocalBranchKind.EDIT)},
                        enabled = allowed && loaded != null && !mismatch, modifier = Modifier.testTag("phone-edit-$index")) {Text(label(Words.PhoneEditSend))}
                    TextButton({branchReview = PhoneBranchReview(source, index, LocalBranchKind.REGENERATE)},
                        enabled = allowed && loaded != null && !mismatch, modifier = Modifier.testTag("phone-regenerate-$index")) {Text(label(Words.PhoneRegenerate))}
                    TextButton({branchReview = PhoneBranchReview(source, index, LocalBranchKind.FORK)},
                        enabled = allowed && row.state in setOf(LocalReplyState.COMPLETE, LocalReplyState.TOKEN_LIMIT),
                        modifier = Modifier.testTag("phone-branch-$index")) {Text(label(Words.PhoneBranchHere))}
                }
            }
        }
    }
    continuation?.let { selected ->
        PhoneContinuationDialog(selected, state, draft, actions) { continuation = null }
    }
    generationReview?.let { PhoneGenerationDialog(it) { generationReview = null } }
    branchReview?.let { selected ->
        PhoneBranchDialog(selected, state, actions, { edited ->
            branchReview = null
            actions.branch(selected.source, selected.index, selected.kind, edited)
        }) { branchReview = null }
    }
    if (draft.toByteArray().size > 8192) Text(label(Words.PhoneQuestionLimit), color = MaterialTheme.colorScheme.error)
    if (budget != null || (!state.busy && state.error == "engine_2")) {
        PhonePromptUse(budget, state.error == "engine_2")
    }
    if ((contextFull || full || state.error == "engine_2") && copyDraft != null) {
        TextButton(copyDraft, modifier = Modifier.testTag("phone-fresh-draft")) { Text(label(Words.PhoneUseDraftInNewChat)) }
    }
    Row {
        OutlinedTextField(draft, change, Modifier.weight(1f).testTag("phone-draft"), placeholder = {Text(label(Words.PhoneMessage))}, maxLines = 4, enabled = !state.busy)
        IconButton(actions.send, enabled = loaded != null && !state.busy && !mismatch && !full && !contextFull && draft.isNotBlank() && draft.toByteArray().size <= 8192,
            modifier = Modifier.testTag("phone-send")) {Icon(Icons.AutoMirrored.Outlined.Send, label(Words.Send))}
    }
}

@Composable
private fun rememberPhonePromptPreview(state: LocalAiState, question: String, actions: LocalAiActions,
    beforeExchange: Int? = null): LocalPromptPreview? {
    val model = state.loaded ?: return null
    val preview = actions.previewPrompt ?: return null
    if (state.busy || state.chat?.let { it.modelSha256 != model.sha256 } == true ||
        question.isBlank() || question.toByteArray().size > 8192) return null
    var result by remember(preview, state.chat, model, question, beforeExchange) {
        mutableStateOf<LocalPromptPreview?>(null)
    }
    LaunchedEffect(preview, state.chat, model, question, beforeExchange) {
        delay(300)
        result = preview(state.chat, model.sha256, question, beforeExchange)
    }
    return result
}

private fun LocalPromptPreview?.blocksGeneration() = when (this) {
    is LocalPromptPreview.Exact -> !budget.fits
    LocalPromptPreview.TooLarge -> true
    else -> false
}

@Composable
internal fun PhonePromptUse(preview: LocalPromptPreview?, fallbackOverflow: Boolean = false) {
    var detail by remember { mutableStateOf(false) }
    when (preview) {
        is LocalPromptPreview.Exact -> {
            val budget = preview.budget
            TextButton({ detail = true }, modifier = Modifier.testTag("phone-prompt-budget")) {
                Text(label(Words.PhoneContextMeter).replace("{prompt}", budget.promptTokens.toString())
                    .replace("{context}", budget.contextTokens.toString()))
            }
            if (!budget.fits) Text(label(Words.PhoneContextWouldOverflow), Modifier.testTag("phone-prompt-overflow"),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (detail) AlertDialog(onDismissRequest = { detail = false },
                title = { Text(label(Words.PhoneContextDetails)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label(Words.PhoneContextMeter).replace("{prompt}", budget.promptTokens.toString())
                            .replace("{context}", budget.contextTokens.toString()))
                        Text(label(Words.PhoneContextRoom).replace("{remaining}", budget.remainingTokens.toString())
                            .replace("{reserved}", budget.reservedTokens.toString()), Modifier.testTag("phone-prompt-output-room"))
                        Text(label(Words.PhoneContextScope))
                    }
                },
                confirmButton = { TextButton({ detail = false }) { Text(label(Words.Close)) } })
        }
        LocalPromptPreview.TooLarge -> Text(label(Words.PhonePromptFormatLimit), Modifier.testTag("phone-prompt-overflow"),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        LocalPromptPreview.Unavailable -> Text(label(Words.PhoneContextUnmeasured), style = MaterialTheme.typography.bodySmall)
        null -> if (fallbackOverflow) Text(label(Words.PhoneContextWouldOverflow), Modifier.testTag("phone-prompt-overflow"),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

private data class PhoneGenerationReview(val modelSha256: String, val settings: LocalGenerationSettings?)

@Composable private fun PhoneGenerationDialog(review: PhoneGenerationReview, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label(Words.PhoneGenerationSettings), style = MaterialTheme.typography.headlineSmall)
                    Text(label(Words.PhoneHelperModel).replace("{sha}", review.modelSha256))
                    val settings = review.settings
                    if (settings == null) Text(label(Words.PhoneGenerationUnknown), Modifier.testTag("phone-generation-unknown"))
                    else {
                        Text(label(Words.PhoneGenerationRequestHelp))
                        Text(label(Words.PhoneGenerationContext).replace("{number}", settings.contextTokens.toString()), Modifier.testTag("phone-generation-context"))
                        Text(label(Words.PhoneGenerationThreads).replace("{number}", settings.threads.toString()))
                        Text(label(Words.PhoneGenerationOutput).replace("{number}", settings.maxOutputTokens.toString()))
                        settings.runtime?.let { runtime ->
                            Text(label(Words.PhoneGreedyPolicy))
                            Text(label(Words.PhoneRuntimeSource))
                            SelectionContainer {Text(runtime.sourceSha256, Modifier.testTag("phone-runtime-source"))}
                        } ?: Text(label(Words.PhoneRuntimeUnknown))
                    }
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-generation-close")) {Text(label(Words.Close))}
            }
        }
    }
}

private data class PhoneBranchReview(val source: LocalConversation, val index: Int, val kind: LocalBranchKind)

@Composable private fun PhoneBranchDialog(review: PhoneBranchReview, state: LocalAiState, actions: LocalAiActions,
    accept: (String?) -> Unit, close: () -> Unit) {
    var question by remember(review) { mutableStateOf(review.source.exchanges[review.index].question) }
    val editing = review.kind == LocalBranchKind.EDIT
    val title = when(review.kind) {
        LocalBranchKind.EDIT -> Words.PhoneEditSend
        LocalBranchKind.REGENERATE -> Words.PhoneRegenerate
        LocalBranchKind.FORK -> Words.PhoneBranchHere
        LocalBranchKind.SELECTED_TURNS, LocalBranchKind.REVIEWED_SUMMARY -> error("This branch has a separate review")
    }
    val unchanged = state.chat == review.source
    val compatible = review.kind == LocalBranchKind.FORK || state.loaded?.sha256 == review.source.modelSha256
    val checksContext = review.kind != LocalBranchKind.FORK && actions.previewPrompt != null
    val validQuestion = question.isNotBlank() && question.toByteArray().size <= 8192
    val budget = if (checksContext && unchanged && compatible && validQuestion) {
        rememberPhonePromptPreview(state, question, actions, beforeExchange = review.index)
    } else null
    val pendingCheck = checksContext && validQuestion && unchanged && compatible && !state.busy && budget == null
    val valid = !state.busy && unchanged && compatible && state.history.size < LocalConversationStore.MAX_CHATS &&
        (!editing || validQuestion) && !pendingCheck && !budget.blocksGeneration()
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label(title), style = MaterialTheme.typography.headlineSmall)
                    Text(label(if (review.kind == LocalBranchKind.FORK) Words.PhoneForkReviewHelp else Words.PhoneBranchReviewHelp))
                    if (editing) OutlinedTextField(question, {question = it}, Modifier.fillMaxWidth().testTag("phone-edit-question"),
                        label = {Text(label(Words.PhoneMessage))}, enabled = !state.busy, maxLines = 8)
                    else SelectionContainer {Text(question)}
                    if (pendingCheck) Text(label(Words.PhoneContextChecking), style = MaterialTheme.typography.bodySmall)
                    if (budget != null) PhonePromptUse(budget)
                    if (!unchanged) Text(label(Words.PhoneBranchChanged), color = MaterialTheme.colorScheme.error)
                    if (!compatible) Text(label(Words.PhoneMismatch))
                    if (state.history.size >= LocalConversationStore.MAX_CHATS) Text(label(Words.PhoneBranchHistoryFull))
                    if (editing && question.toByteArray().size > 8192) Text(label(Words.PhoneQuestionLimit), color = MaterialTheme.colorScheme.error)
                }
                Button({accept(question.takeIf {editing})}, enabled = valid,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-branch-confirm")) {
                    Text(label(if (review.kind == LocalBranchKind.FORK) Words.PhoneCreateBranch else Words.PhoneCreateGenerate))
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-branch-cancel")) { Text(label(Words.Cancel)) }
            }
        }
    }
}

@Composable private fun PhoneConfirm(text: String, busy: Boolean, accept: () -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, text = {Text(text)}, confirmButton = {TextButton(accept, enabled = !busy) {Text(label(Words.Delete))}},
        dismissButton = {TextButton(close) {Text(label(Words.Cancel))}})
}

internal fun phoneReplyState(state: LocalReplyState) = when(state) {
    LocalReplyState.GENERATING -> Words.PhoneGenerating
    LocalReplyState.COMPLETE -> Words.PhoneComplete
    LocalReplyState.CANCELLED -> Words.PhoneCancelled
    LocalReplyState.TOKEN_LIMIT -> Words.PhoneTokenLimit
    LocalReplyState.FAILED -> Words.PhoneFailed
    LocalReplyState.INTERRUPTED -> Words.PhoneInterrupted
}
private fun phoneStatus(state: LocalAiState) = when(state.status) {
    "downloading" -> if(state.transfer?.phase == LocalTransferPhase.DOWNLOADING) Words.PhoneDownloading else Words.PhoneVerifying
    "reading" -> Words.PhoneReading
    "searching" -> Words.PhoneHistorySearching
    "organizing" -> Words.PhoneHistoryOrganizing
    "preparing_export" -> Words.PhoneMarkdownPreparing
    "saving_model_settings" -> Words.PhoneModelSettingsSaving
    "loading" -> Words.PhoneLoading
    "unloading" -> Words.PhoneUnloading
    "generating" -> Words.PhoneGenerating
    "summarizing" -> Words.PhoneSummaryDrafting
    "benchmarking" -> Words.PhoneBenchmarkRunning
    "importing" -> Words.PhoneImporting
    else -> Words.PhoneWorking
}
internal fun phoneError(code: String) = when {
    code == "history_search" -> Words.PhoneHistorySearchError
    code == "history_organization" -> Words.PhoneHistoryOrganizationError
    code == "history_organization_changed" -> Words.PhoneHistoryOrganizationChanged
    code == "history_export_changed" -> Words.PhoneMarkdownChanged
    code == "history_export_prepare" -> Words.PhoneMarkdownPrepareError
    code == "helper_unverified" -> Words.PhoneHelperRejected
    code == "summary_unusable" -> Words.PhoneSummaryUnusable
    code == "insufficient_memory" -> Words.PhoneMemoryError
    code == "engine_2" -> Words.PhoneContextError
    code == "engine_6" || code == "native_unavailable" -> Words.PhoneUnsupported
    code in setOf("insufficient_space", "checksum_mismatch", "partial_size", "download_size") -> Words.PhoneStorageError
    else -> Words.PhoneError
}
