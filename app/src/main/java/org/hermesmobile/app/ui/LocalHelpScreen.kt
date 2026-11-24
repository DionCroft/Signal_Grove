package org.hermesmobile.app.ui

import android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.hermesmobile.app.local.*
import java.time.Instant

@Composable internal fun LocalHelpScreen(state: LocalAiState, initialTopic: LocalHelpTopic, candidate: LocalHelpObservations?,
    start: (LocalHelpTopic, LocalHelpObservations?) -> Unit, stop: () -> Unit, close: () -> Unit) {
    var topic by remember(initialTopic) {mutableStateOf(initialTopic)}
    var query by remember {mutableStateOf("")}
    var share by remember(topic, candidate) { mutableStateOf(false) }
    val reviewed = candidate.takeIf { share }
    val uri = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val topics = remember(query) { if (query.isBlank()) LocalHelpTopic.entries else LocalSetupHelper.matchingTopics(query) }
    val result = state.helper?.takeIf {it.topic == topic}
    val checks = result?.checks?.takeIf { it.isNotEmpty() } ?: LocalSetupHelper.checks(topic)
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        var dismissedEntryKeyboard by remember { mutableStateOf(false) }
        SideEffect {
            window?.let {
                val mode = it.attributes.softInputMode
                val hidden = (mode and SOFT_INPUT_MASK_STATE.inv()) or SOFT_INPUT_STATE_ALWAYS_HIDDEN
                if (hidden != mode) it.setSoftInputMode(hidden)
            }
        }
        LaunchedEffect(window, windowFocused) {
            // Android 8 can retain the old IME even after assigning focus to this dialog.
            if (window != null && windowFocused && !dismissedEntryKeyboard) {
                WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
                dismissedEntryKeyboard = true
            }
        }
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp).testTag("phone-helper")) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("phone-helper-body"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(label(Words.PhoneHelper), style = MaterialTheme.typography.titleLarge)
                    Text(label(Words.PhoneHelperBoundary))
                    OutlinedTextField(query, { query = it.take(LocalSetupHelper.MAX_QUERY_LENGTH) },
                        label = { Text(label(Words.PhoneHelperQuery)) },
                        modifier = Modifier.fillMaxWidth().testTag("phone-helper-query"),
                        enabled = !state.busy, singleLine = true)
                    Text(label(Words.PhoneHelperQueryHelp), style = MaterialTheme.typography.bodySmall)
                    if (topics.isEmpty()) Text(label(Words.PhoneHelperQueryEmpty), Modifier.testTag("phone-helper-no-topics"))
                    if (query.isNotEmpty()) TextButton({ query = ""; focus.clearFocus() },
                        Modifier.testTag("phone-helper-clear-query"), enabled = !state.busy) {
                        Text(label(Words.PhoneHistoryClear))
                    }
                    topics.forEach { value ->
                        FilterChip(topic == value, {
                            topic = value
                            query = ""
                            focus.clearFocus()
                        }, label = {Text(label(value.title))}, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().testTag("phone-helper-topic-" + value.name))
                    }
                    Text(label(topic.title), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("phone-helper-current-topic"))
                    val canShare = candidate != null && !state.busy
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(share, enabled = canShare, role = Role.Checkbox, onValueChange = { share = it })
                            .testTag("phone-helper-observations"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(share, onCheckedChange = null, enabled = canShare)
                        Text(label(Words.PhoneHelperShare), Modifier.weight(1f).padding(start = 8.dp))
                    }
                    if (candidate == null) Text(label(Words.PhoneHelperObservationsPending), style = MaterialTheme.typography.bodySmall)
                    reviewed?.let { HelpObservations(it) }
                    if (state.loaded == null) Text(label(Words.PhoneNotLoaded))
                    Button(
                        onClick = {
                            val observations = reviewed
                            share = false
                            start(topic, observations)
                        },
                        enabled = state.loaded != null && !state.busy,
                        modifier = Modifier.testTag("phone-helper-run")
                    ) { Text(label(Words.PhoneHelperRun)) }
                    if (state.busy) {
                        Text(label(Words.PhoneWorking))
                        if(state.helper?.state == LocalReplyState.GENERATING) TextButton(stop) {Text(label(Words.Stop))}
                    }
                    state.error?.let {Text(label(phoneError(it)), color = MaterialTheme.colorScheme.error)}
                    result?.let { answer ->
                        Text(label(Words.PhoneHelperAdvice), style = MaterialTheme.typography.titleMedium)
                        Text(label(Words.PhoneHelperModel).replace("{sha}", answer.modelSha256), style = MaterialTheme.typography.labelSmall)
                        answer.observations?.let {HelpObservations(it)} ?: Text(label(Words.PhoneHelperUnshared))
                        Text(label(phoneReplyState(answer.state)))
                    }
                    if (result?.checks.isNullOrEmpty()) {
                        Text(label(Words.PhoneHelperChecks), style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.testTag(if (result?.checks.isNullOrEmpty()) "phone-helper-reviewed" else "phone-helper-answer"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        checks.forEach { check ->
                            HermesCard {
                                Text(label(when(check.scope) {
                                    LocalHelpScope.HERMES_HOST -> Words.PhoneHelperOnHost
                                    LocalHelpScope.PHONE_APP -> Words.PhoneHelperOnPhone
                                    LocalHelpScope.EXECUTION_WORKER -> Words.PhoneHelperOnWorker
                                    LocalHelpScope.REFERENCE -> Words.PhoneHelperSources
                                }), style = MaterialTheme.typography.labelMedium)
                                SelectionContainer {Text(label(check.word))}
                                Text("[${check.sourceId}]", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text("${label(Words.PhoneHelperSources)} · ${LocalSetupHelper.VERSION}", style = MaterialTheme.typography.titleMedium)
                    (result?.sources ?: LocalSetupHelper.sources(topic)).forEach { source -> HermesCard {
                        Text("[${source.id}] ${label(source.heading)}")
                        Text(label(source.body))
                        if(source.scope == LocalHelpScope.HERMES_HOST) Text(label(Words.PhoneHelperHostScope), style = MaterialTheme.typography.labelMedium)
                        if(source.scope == LocalHelpScope.PHONE_APP) Text(label(Words.PhoneHelperPhoneScope), style = MaterialTheme.typography.labelMedium)
                        source.example?.let {SelectionContainer {Text(it, style = MaterialTheme.typography.bodySmall)}}
                        TextButton({uri.openUri(source.url)}) {Text(label(Words.PhoneHelperReference))}
                    } }
                }
                TextButton(close, Modifier.fillMaxWidth().testTag("phone-helper-close")) {Text(label(Words.Close))}
            }
        }
    }
}

@Composable private fun HelpObservations(value: LocalHelpObservations) {
    fun yes(value: Boolean) = if(value) Words.Yes else Words.No
    val address = when(value.address) {LocalAddressKind.UNCONFIGURED -> Words.PhoneAddressMissing; LocalAddressKind.HTTPS -> Words.PhoneAddressHttps; LocalAddressKind.OTHER -> Words.PhoneAddressOther}
    Text(label(Words.PhoneHelperObservation).replace("{time}", Instant.ofEpochMilli(value.capturedAt).toString())
        .replace("{demo}", label(yes(value.demo))).replace("{address}", label(address))
        .replace("{abi}", label(yes(value.process64Bit))).replace("{model}", label(yes(value.modelLoaded))),
        style = MaterialTheme.typography.bodySmall)
}
