package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.LOCAL_BENCHMARK_OUTPUT_CAP
import org.hermesmobile.app.local.LOCAL_BENCHMARK_TIMEOUT_MILLIS
import org.hermesmobile.app.local.LocalAiState
import org.hermesmobile.app.local.LocalBenchmarkEnd
import org.hermesmobile.app.local.LocalGenerationEnd
import org.hermesmobile.app.local.LocalGenerationMeasurement
import org.hermesmobile.app.local.LocalPerformanceDiagnostics
import java.text.NumberFormat

@Composable
internal fun PhonePerformanceDiagnosticsDialog(
    state: LocalAiState, run: () -> Unit, stop: () -> Unit, close: () -> Unit
) {
    val running = state.busy && state.status == "benchmarking"
    val performance = state.performance
    val settings = checkNotNull(state.loadedSettings)
    val output = minOf(settings.maxOutputTokens, LOCAL_BENCHMARK_OUTPUT_CAP)
    val context = LocalContext.current
    val heading = label(Words.PhoneDiagnostics)
    val memory = performance.loadPssKiB?.let {
        label(Words.PhoneLoadPss).replace("{number}", performanceNumber(it / 1024.0))
    } ?: label(Words.PhoneLoadPssUnknown)
    val latestHeading = label(Words.PhoneLastReplyPerformance)
    val latestLimit = label(Words.PhoneTokenLimit).takeIf { performance.lastReply?.end == LocalGenerationEnd.TOKEN_LIMIT }
    val latest = measurementLines(performance.lastReply?.measurement)
    val scope = label(Words.PhoneSampleScope)
    val benchmarkHeading = label(Words.PhoneBenchmark)
    val benchmark = performance.benchmark
    val benchmarkStatus = benchmark?.let { label(benchmarkStatusWord(it.end)) }
    val benchmarkNumbers = benchmark
        ?.takeIf { it.end == LocalBenchmarkEnd.COMPLETE || it.end == LocalBenchmarkEnd.TOKEN_LIMIT }
        ?.let { measurementLines(it.measurement) }.orEmpty()
    val limits = modelPerformanceLabel(Words.PhoneModelActiveLimits, settings.contextTokens, settings.threads, settings.maxOutputTokens)
    val report = if (state.busy || performance == LocalPerformanceDiagnostics()) null else buildList {
        add(heading)
        add(limits)
        add(memory)
        if (performance.lastReply?.measurement != null) {
            add("")
            add(latestHeading)
            latestLimit?.let(::add)
            addAll(latest)
        }
        if (benchmarkStatus != null) {
            add("")
            add(benchmarkHeading)
            add(benchmarkStatus)
            addAll(benchmarkNumbers)
        }
        if (performance.lastReply?.measurement != null || benchmark?.measurement != null) add(scope)
    }.joinToString("\n")
    var copied by remember(report) { mutableStateOf(false) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(heading, style = MaterialTheme.typography.headlineSmall)
                    Text(limits, style = MaterialTheme.typography.bodySmall)
                    Text(memory, Modifier.testTag("phone-diagnostics-memory"))
                    Text(latestHeading, style = MaterialTheme.typography.titleMedium)
                    latestLimit?.let { Text(it) }
                    latest.forEach { Text(it) }
                    Text(scope, style = MaterialTheme.typography.bodySmall)
                    Text(benchmarkHeading, style = MaterialTheme.typography.titleMedium)
                    Text(label(Words.PhoneBenchmarkHelp).replace("{seconds}", (LOCAL_BENCHMARK_TIMEOUT_MILLIS / 1000).toString())
                        .replace("{tokens}", output.toString()))
                    if (running) Text(label(Words.PhoneBenchmarkRunning))
                    if (state.busy && !running) Text(label(Words.PhoneBenchmarkWait))
                    benchmarkStatus?.let { Text(it, Modifier.testTag("phone-benchmark-result")) }
                    benchmarkNumbers.forEach { Text(it) }
                    report?.let { text ->
                        TextButton({
                            copyPhoneText(context, text)
                            copied = true
                        }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-diagnostics-copy")) {
                            Text(label(if (copied) Words.Copied else Words.PhoneCopyPerformance))
                        }
                    }
                }
                if (running) {
                    Button(stop, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-benchmark-stop")) {
                        Text(label(Words.Stop))
                    }
                } else {
                    Button(run, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-benchmark-run"), enabled = !state.busy) {
                        Text(label(Words.PhoneBenchmarkStart))
                    }
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-diagnostics-close")) {
                    Text(label(Words.Close))
                }
            }
        }
    }
}

private fun benchmarkStatusWord(end: LocalBenchmarkEnd) = when (end) {
    LocalBenchmarkEnd.COMPLETE -> Words.PhoneComplete
    LocalBenchmarkEnd.TOKEN_LIMIT -> Words.PhoneTokenLimit
    LocalBenchmarkEnd.STOPPED -> Words.PhoneCancelled
    LocalBenchmarkEnd.TIMED_OUT -> Words.PhoneBenchmarkTimedOut
    LocalBenchmarkEnd.FAILED -> Words.PhoneFailed
}

@Composable
private fun measurementLines(measurement: LocalGenerationMeasurement?): List<String> {
    if (measurement == null) return listOf(label(Words.PhoneTimingUnavailable))
    val first = measurement.firstAnswerNanos?.let {
        label(Words.PhoneFirstAnswer).replace("{number}", performanceNumber(it / 1_000_000_000.0))
    } ?: label(Words.PhoneFirstAnswerMissing)
    val rate = label(Words.PhoneSampleRate).replace("{rate}", performanceNumber(measurement.sampledTokensPerSecond))
        .replace("{count}", measurement.sampledTokens.toString())
    return listOf(first, rate)
}

private fun performanceNumber(value: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
