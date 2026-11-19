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
import androidx.compose.ui.Modifier
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
import java.text.NumberFormat

@Composable
internal fun PhonePerformanceDiagnosticsDialog(
    state: LocalAiState, run: () -> Unit, stop: () -> Unit, close: () -> Unit
) {
    val running = state.busy && state.status == "benchmarking"
    val performance = state.performance
    val output = minOf(checkNotNull(state.loadedSettings).maxOutputTokens, LOCAL_BENCHMARK_OUTPUT_CAP)
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(label(Words.PhoneDiagnostics), style = MaterialTheme.typography.headlineSmall)
                    Text(performance.loadPssKiB?.let {
                        label(Words.PhoneLoadPss).replace("{number}", performanceNumber(it / 1024.0))
                    } ?: label(Words.PhoneLoadPssUnknown), Modifier.testTag("phone-diagnostics-memory"))
                    Text(label(Words.PhoneLastReplyPerformance), style = MaterialTheme.typography.titleMedium)
                    if (performance.lastReply?.end == LocalGenerationEnd.TOKEN_LIMIT) Text(label(Words.PhoneTokenLimit))
                    MeasurementRows(performance.lastReply?.measurement)
                    Text(label(Words.PhoneSampleScope), style = MaterialTheme.typography.bodySmall)
                    Text(label(Words.PhoneBenchmark), style = MaterialTheme.typography.titleMedium)
                    Text(label(Words.PhoneBenchmarkHelp).replace("{seconds}", (LOCAL_BENCHMARK_TIMEOUT_MILLIS / 1000).toString())
                        .replace("{tokens}", output.toString()))
                    if (running) Text(label(Words.PhoneBenchmarkRunning))
                    if (state.busy && !running) Text(label(Words.PhoneBenchmarkWait))
                    performance.benchmark?.let { result ->
                        Text(label(when (result.end) {
                            LocalBenchmarkEnd.COMPLETE -> Words.PhoneComplete
                            LocalBenchmarkEnd.TOKEN_LIMIT -> Words.PhoneTokenLimit
                            LocalBenchmarkEnd.STOPPED -> Words.PhoneCancelled
                            LocalBenchmarkEnd.TIMED_OUT -> Words.PhoneBenchmarkTimedOut
                            LocalBenchmarkEnd.FAILED -> Words.PhoneFailed
                        }), Modifier.testTag("phone-benchmark-result"))
                        if (result.end == LocalBenchmarkEnd.COMPLETE || result.end == LocalBenchmarkEnd.TOKEN_LIMIT) {
                            MeasurementRows(result.measurement)
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

@Composable
private fun MeasurementRows(measurement: LocalGenerationMeasurement?) {
    if (measurement == null) {
        Text(label(Words.PhoneTimingUnavailable))
        return
    }
    Text(measurement.firstAnswerNanos?.let {
        label(Words.PhoneFirstAnswer).replace("{number}", performanceNumber(it / 1_000_000_000.0))
    } ?: label(Words.PhoneFirstAnswerMissing))
    Text(label(Words.PhoneSampleRate).replace("{rate}", performanceNumber(measurement.sampledTokensPerSecond))
        .replace("{count}", measurement.sampledTokens.toString()))
}

private fun performanceNumber(value: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
