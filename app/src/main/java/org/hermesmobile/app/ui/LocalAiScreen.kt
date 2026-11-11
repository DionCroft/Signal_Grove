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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.LocalAiActivity
import org.hermesmobile.app.local.*

@Composable fun LocalAiEntry() {
    val context = LocalContext.current
    TextButton({context.startActivity(Intent(context, LocalAiActivity::class.java))}, Modifier.testTag("phone-ai-open")) {Text(label(Words.PhoneAi))}
}

data class LocalAiActions(val send: () -> Unit, val stop: () -> Unit, val newChat: () -> Unit,
    val load: (InstalledLocalModel, Int) -> Unit, val unload: () -> Unit, val remove: (InstalledLocalModel) -> Unit,
    val download: () -> Unit, val discard: () -> Unit, val chooseFile: () -> Unit,
    val acceptImport: () -> Unit, val cancelImport: () -> Unit, val openChat: (String) -> Unit,
    val deleteChat: (String) -> Unit, val dismissError: () -> Unit, val refresh: () -> Unit = {},
    val help: (LocalHelpTopic, Boolean) -> Unit = {_, _ ->})

@Composable fun LocalAiRoute(vm: LocalAiViewModel, close: () -> Unit) {
    val state by vm.controller.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences by (context.applicationContext as org.hermesmobile.app.HermesApplication).preferences.flow
        .collectAsStateWithLifecycle<org.hermesmobile.app.data.AppPreferences?>(initialValue = null)
    val observations = remember(preferences, state.loaded?.sha256) {preferences?.let {
        LocalHelpObservations.capture(it.demo, it.url, android.os.Process.is64Bit(), state.loaded != null, System.currentTimeMillis())
    }}
    val licence = remember {context.assets.open("LFM3-LICENSE.txt").bufferedReader().use {it.readText()}}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {vm.pendingImport.value = it}
    val c = vm.controller
    LocalAiScreen(state, c.supported, draft, {vm.draft.value = it}, pending != null, licence,
        LocalAiActions(vm::send, c::cancel, c::newChat, c::load, c::unload, c::deleteModel,
            {c.download(true)}, c::discardDownload, {picker.launch(arrayOf("*/*"))}, vm::importSelected,
            {vm.pendingImport.value = null}, c::openChat, c::deleteChat, c::dismissError, c::refreshLibrary,
            {topic, share -> c.help(topic, observations.takeIf {share})}), close, observations)
}

/** UI consumes only local state and callbacks. It has no Hermes client or profile credentials. */
@Composable fun LocalAiScreen(state: LocalAiState, supported: Boolean, draft: String, changeDraft: (String) -> Unit,
    pendingImport: Boolean, licenceText: String, actions: LocalAiActions, close: () -> Unit, observations: LocalHelpObservations? = null) {
    var tab by rememberSaveable {mutableIntStateOf(0)}
    var guide by rememberSaveable {mutableStateOf(false)}
    var helper by remember {mutableStateOf(false)}
    var downloadReview by remember {mutableStateOf(false)}
    var deleteModel by remember {mutableStateOf<InstalledLocalModel?>(null)}
    var deleteChat by remember {mutableStateOf<String?>(null)}
    var discardReview by remember {mutableStateOf(false)}
    var review by remember {mutableStateOf<String?>(null)}
    var boundary by remember {mutableStateOf(false)}
    var errorDetails by remember {mutableStateOf(false)}
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label(Words.PhoneAi), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton({boundary = true}) {Icon(Icons.Outlined.Info, label(Words.PhoneBoundary))}
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
                0 -> PhoneChat(state, draft, changeDraft, actions) {review = it}
                1 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("phone-model-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label(Words.PhoneLibraryHelp), style = MaterialTheme.typography.bodySmall)
                    TextButton(actions.refresh, enabled = !state.busy) {Text(label(Words.Refresh))}
                    Button(actions.chooseFile, enabled = supported && !state.busy, modifier = Modifier.testTag("phone-import")) {Text(label(Words.PhoneImport))}
                    HermesCard {
                        Text(RecommendedLocalModel.NAME, style = MaterialTheme.typography.titleMedium)
                        Text(label(Words.PhoneDownloadInfo), style = MaterialTheme.typography.bodySmall)
                        if (state.partialBytes > 0) {
                            Text(label(Words.PhonePartial).replace("{bytes}", (state.partialBytes / 1048576).toString()))
                            TextButton({discardReview = true}, enabled = !state.busy) {Text(label(Words.PhoneDiscard))}
                        }
                        Button({downloadReview = true}, enabled = supported && !state.busy, modifier = Modifier.testTag("phone-download-review")) {Text(label(Words.PhoneDownload))}
                    }
                    state.models.forEach { model -> PhoneModel(model, state, supported, actions) {deleteModel = model} }
                }
                2 -> LazyColumn(Modifier.weight(1f).testTag("phone-history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.history.isEmpty()) item {Text(label(Words.PhoneNoHistory))}
                    items(state.history, key = {it.id}) { item -> HermesCard {
                        Text(item.title)
                        Text("SHA-256: ${item.modelSha256}", style = MaterialTheme.typography.labelSmall)
                        item.lastState?.let {Text(label(phoneReplyState(it)), style = MaterialTheme.typography.labelSmall)}
                        TextButton({actions.openChat(item.id); tab = 0}, enabled = !state.busy) {Text(label(Words.Chat))}
                        TextButton({deleteChat = item.id}, enabled = !state.busy) {Text(label(Words.Delete))}
                    } }
                }
            }
            Row {
                TextButton({guide = true}, Modifier.weight(1f).testTag("phone-setup-guide")) {Text(label(Words.SetupGuide))}
                TextButton({helper = true}, Modifier.weight(1f).testTag("phone-helper-open")) {Text(label(Words.PhoneHelper))}
            }
        }
    }
    if (guide) SetupGuide {guide = false}
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
    if (downloadReview || pendingImport) {
        val importing = pendingImport
        PhoneLicence(if(importing) label(Words.PhoneImportLicence) else licenceText, state.busy,
            accept = {if(importing) actions.acceptImport() else {downloadReview = false; actions.download()}},
            close = {if(importing) actions.cancelImport() else downloadReview = false})
    }
    deleteModel?.let { model -> PhoneConfirm(Words.PhoneDeleteModel, state.busy, {actions.remove(model); deleteModel = null}) {deleteModel = null} }
    deleteChat?.let { id -> PhoneConfirm(Words.PhoneDeleteChat, state.busy, {actions.deleteChat(id); deleteChat = null}) {deleteChat = null} }
    if (discardReview) PhoneConfirm(Words.PhoneDiscard, state.busy, {actions.discard(); discardReview = false}) {discardReview = false}
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

@Composable private fun ColumnScope.PhoneChat(state: LocalAiState, draft: String, change: (String) -> Unit, actions: LocalAiActions, review: (String) -> Unit) {
    val loaded = state.loaded
    val mismatch = loaded != null && state.chat?.let {it.modelSha256 != loaded.sha256} == true
    val full = (state.chat?.exchanges?.size ?: 0) >= 30
    Row(Modifier.fillMaxWidth()) {
        Text(loaded?.metadata?.name.orEmpty(), Modifier.weight(1f).testTag("phone-loaded"), style = MaterialTheme.typography.labelMedium)
        IconButton(actions.newChat, enabled = loaded != null && !state.busy, modifier = Modifier.testTag("phone-new-chat")) {Icon(Icons.Outlined.Add, label(Words.NewChat))}
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("phone-messages"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val exchanges = state.chat?.exchanges.orEmpty()
        if (loaded == null) item {Text(label(Words.PhoneNotLoaded))}
        if (mismatch || full) item {Text(label(if(mismatch) Words.PhoneMismatch else Words.PhoneTurnLimit), style = MaterialTheme.typography.bodySmall)}
        if (exchanges.isEmpty()) item {Text(label(Words.PhoneEmpty))}
        items(exchanges.size) { index -> val row = exchanges[index]
            HermesCard {
                SelectionContainer {Text(row.question)}
                HorizontalDivider()
                SelectionContainer {Text(row.reply)}
                Text(label(phoneReplyState(row.state)), style = MaterialTheme.typography.labelSmall)
                if (row.reply.isNotBlank() && row.state != LocalReplyState.GENERATING) TextButton({review(row.reply)}) {Text(label(Words.PhoneReview))}
            }
        }
    }
    if (draft.toByteArray().size > 8192) Text(label(Words.PhoneQuestionLimit), color = MaterialTheme.colorScheme.error)
    Row {
        OutlinedTextField(draft, change, Modifier.weight(1f).testTag("phone-draft"), placeholder = {Text(label(Words.PhoneMessage))}, maxLines = 4, enabled = !state.busy)
        IconButton(actions.send, enabled = loaded != null && !state.busy && !mismatch && !full && draft.isNotBlank() && draft.toByteArray().size <= 8192,
            modifier = Modifier.testTag("phone-send")) {Icon(Icons.AutoMirrored.Outlined.Send, label(Words.Send))}
    }
}

@Composable private fun PhoneModel(model: InstalledLocalModel, state: LocalAiState, supported: Boolean, actions: LocalAiActions, remove: () -> Unit) {
    var context by rememberSaveable(model.sha256) {mutableIntStateOf(minOf(2048, model.metadata.trainedContext))}
    HermesCard {
        Text(model.metadata.name, style = MaterialTheme.typography.titleMedium)
        Text("${model.metadata.architecture} · ${model.metadata.bytes / 1048576} MiB", style = MaterialTheme.typography.bodySmall)
        Text("SHA-256: ${model.sha256}", style = MaterialTheme.typography.labelSmall)
        Text(label(if(model.sha256 == RecommendedLocalModel.SHA256) Words.PhonePublisher else Words.PhoneImported), style = MaterialTheme.typography.bodySmall)
        Text(label(Words.PhoneContext), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(512, 1024, 2048, 4096).filter {it <= model.metadata.trainedContext}.forEach { size ->
            FilterChip(context == size, {context = size}, label = {Text(size.toString())}, enabled = !state.busy)
        } }
        if (state.loaded?.sha256 == model.sha256) Text(label(Words.PhoneLoaded).replace("{model}", model.metadata.name))
        Button({actions.load(model, context)}, enabled = supported && !state.busy, modifier = Modifier.testTag("phone-load-${model.sha256.take(8)}")) {Text(label(Words.PhoneLoad))}
        if (state.loaded?.sha256 == model.sha256) TextButton(actions.unload, enabled = !state.busy) {Text(label(Words.PhoneUnload))}
        TextButton(remove, enabled = !state.busy) {Text(label(Words.Delete))}
    }
}

@Composable private fun PhoneLicence(text: String, busy: Boolean, accept: () -> Unit, close: () -> Unit) {
    var approved by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest = close, title = {Text(label(Words.PhoneLicence))}, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            SelectionContainer {Text(text, style = MaterialTheme.typography.bodySmall)}
            Row {Checkbox(approved, {approved = it}, modifier = Modifier.testTag("phone-licence-consent")); Text(label(Words.PhoneLicenceAccept))}
        }
    }, confirmButton = {TextButton(accept, enabled = approved && !busy, modifier = Modifier.testTag("phone-licence-confirm")) {Text(label(Words.Confirm))}},
        dismissButton = {TextButton(close) {Text(label(Words.Cancel))}})
}

@Composable private fun PhoneConfirm(word: Words, busy: Boolean, accept: () -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, text = {Text(label(word))}, confirmButton = {TextButton(accept, enabled = !busy) {Text(label(Words.Delete))}},
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
    "loading" -> Words.PhoneLoading
    "unloading" -> Words.PhoneUnloading
    "generating" -> Words.PhoneGenerating
    "importing" -> Words.PhoneImporting
    else -> Words.PhoneWorking
}
internal fun phoneError(code: String) = when {
    code == "helper_unverified" -> Words.PhoneHelperRejected
    code == "insufficient_memory" -> Words.PhoneMemoryError
    code == "engine_2" -> Words.PhoneContextError
    code == "engine_6" || code == "native_unavailable" -> Words.PhoneUnsupported
    code in setOf("insufficient_space", "checksum_mismatch", "partial_size", "download_size") -> Words.PhoneStorageError
    else -> Words.PhoneError
}
