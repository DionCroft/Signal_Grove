package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.InstalledLocalModel
import org.hermesmobile.app.local.LocalModelPerformance
import org.hermesmobile.app.local.LocalPerformancePreset

@Composable
internal fun modelPerformanceLabel(word: Words, context: Int, threads: Int, output: Int): String =
    label(word).replace("{context}", context.toString()).replace("{threads}", threads.toString())
        .replace("{output}", output.toString())

@Composable
internal fun PhonePerformanceSettings(
    model: InstalledLocalModel,
    saved: LocalModelPerformance,
    busy: Boolean,
    close: () -> Unit,
    save: (LocalModelPerformance) -> Unit
) {
    var context by rememberSaveable(model.sha256) { mutableIntStateOf(saved.contextTokens) }
    var threads by rememberSaveable(model.sha256) { mutableIntStateOf(saved.threadCap) }
    var output by rememberSaveable(model.sha256) { mutableIntStateOf(saved.outputTokens) }
    var advanced by rememberSaveable(model.sha256) { mutableStateOf(false) }
    val trainedContext = model.metadata.trainedContext
    val selection = LocalModelPerformance(context, threads, output)

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("phone-performance-options"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label(Words.PhoneModelPerformance), style = MaterialTheme.typography.headlineSmall)
                    Text(model.metadata.name, style = MaterialTheme.typography.titleMedium)
                    Text(label(Words.PhoneModelSettingsHelp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LocalPerformancePreset.entries.forEach { preset ->
                            val candidate = preset.forModel(trainedContext)
                            val word = when (preset) {
                                LocalPerformancePreset.BALANCED -> Words.PhoneModelBalanced
                                LocalPerformancePreset.FAST -> Words.PhoneModelFast
                                LocalPerformancePreset.BATTERY_SAVER -> Words.PhoneModelBattery
                            }
                            FilterChip(candidate == selection, {
                                context = candidate.contextTokens
                                threads = candidate.threadCap
                                output = candidate.outputTokens
                            }, { Text(label(word)) }, enabled = !busy,
                                modifier = Modifier.testTag("phone-performance-preset-${preset.name}"))
                        }
                    }
                    Text(modelPerformanceLabel(Words.PhoneModelNextLimits, context, threads, output),
                        Modifier.testTag("phone-performance-preview"))
                    TextButton({ advanced = !advanced }, modifier = Modifier.testTag("phone-performance-advanced")) {
                        Text(label(Words.PhoneModelAdvanced))
                    }
                    if (advanced) {
                        val sizes = (listOf(512, 1024, 2048, 4096) + minOf(4096, trainedContext) + context)
                            .filter { it <= trainedContext }.distinct().sorted()
                        PerformanceOptions(Words.PhoneContext, "context", sizes, context, busy) { size ->
                            context = size
                            if (output >= size) output = 384
                        }
                        PerformanceOptions(Words.PhoneModelThreadCap, "threads", (1..4).toList(), threads, busy) { threads = it }
                        val limits = (listOf(64, 128, 192, 256, 384, 512) + output).distinct().sorted().filter { it < context }
                        PerformanceOptions(Words.PhoneModelOutputCap, "output", limits, output, busy) { output = it }
                        Text(label(Words.PhoneGreedyPolicy), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button({ save(selection) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-performance-save"),
                    enabled = !busy && selection.allowedBy(trainedContext)) { Text(label(Words.Save)) }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-performance-cancel")) {
                    Text(label(Words.Cancel))
                }
            }
        }
    }
}

@Composable
private fun PerformanceOptions(title: Words, key: String, values: List<Int>, selected: Int, busy: Boolean, choose: (Int) -> Unit) {
    Text(label(title), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(value == selected, { choose(value) }, { Text(value.toString()) }, enabled = !busy,
                modifier = Modifier.testTag("phone-performance-$key-$value"))
        }
    }
}
