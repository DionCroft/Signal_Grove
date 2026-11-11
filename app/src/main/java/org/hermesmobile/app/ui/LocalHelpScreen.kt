package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.*
import java.time.Instant

@Composable internal fun LocalHelpScreen(state: LocalAiState, candidate: LocalHelpObservations?,
    start: (LocalHelpTopic, Boolean) -> Unit, stop: () -> Unit, close: () -> Unit) {
    var topic by remember {mutableStateOf(LocalHelpTopic.FIRST_SETUP)}
    var share by remember {mutableStateOf(false)}
    val uri = LocalUriHandler.current
    val result = state.helper?.takeIf {it.topic == topic}
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp).testTag("phone-helper")) {
                Text(label(Words.PhoneHelper), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label(Words.PhoneHelperBoundary))
                    LocalHelpTopic.entries.forEach { value ->
                        FilterChip(topic == value, {topic = value}, label = {Text(label(value.title))}, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().testTag("phone-helper-topic-${value.name}"))
                    }
                    Row {Checkbox(share, {share = it}, enabled = candidate != null && !state.busy, modifier = Modifier.testTag("phone-helper-observations")); Text(label(Words.PhoneHelperShare))}
                    if (share) candidate?.let {HelpObservations(it)}
                    if (state.loaded == null) Text(label(Words.PhoneNotLoaded))
                    Button({start(topic, share)}, enabled = state.loaded != null && !state.busy && (!share || candidate != null),
                        modifier = Modifier.testTag("phone-helper-run")) {Text(label(Words.PhoneHelperRun))}
                    if (state.busy) {
                        Text(label(Words.PhoneWorking))
                        if(state.helper?.state == LocalReplyState.GENERATING) TextButton(stop) {Text(label(Words.Stop))}
                    }
                    state.error?.let {Text(label(phoneError(it)), color = MaterialTheme.colorScheme.error)}
                    result?.let { answer ->
                        Text(label(Words.PhoneHelperAdvice), style = MaterialTheme.typography.titleMedium)
                        Text(label(Words.PhoneHelperModel).replace("{sha}", answer.modelSha256), style = MaterialTheme.typography.labelSmall)
                        answer.observations?.let {HelpObservations(it)} ?: Text(label(Words.PhoneHelperUnshared))
                        SelectionContainer {Text(answer.text, Modifier.testTag("phone-helper-answer"))}
                        Text(label(phoneReplyState(answer.state)))
                    }
                    Text("${label(Words.PhoneHelperSources)} · ${LocalSetupHelper.VERSION}", style = MaterialTheme.typography.titleMedium)
                    (result?.sources ?: LocalSetupHelper.sources(topic)).forEach { source -> HermesCard {
                        Text("[${source.id}] ${label(source.heading)}")
                        Text(label(source.body))
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
