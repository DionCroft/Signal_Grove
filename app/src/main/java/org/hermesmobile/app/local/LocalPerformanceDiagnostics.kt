package org.hermesmobile.app.local

/** Sampled tokens include reasoning, control and end tokens. Timing uses monotonic clocks. */
data class LocalGenerationMeasurement(val sampledTokens: Int, val generationNanos: Long, val firstAnswerNanos: Long?) {
    init {
        require(sampledTokens in 1..512 && generationNanos > 0 && (firstAnswerNanos == null || firstAnswerNanos >= 0))
    }

    val sampledTokensPerSecond: Double get() = sampledTokens * 1_000_000_000.0 / generationNanos
}

data class LocalGenerationResult(val end: LocalGenerationEnd, val measurement: LocalGenerationMeasurement? = null)

enum class LocalBenchmarkEnd { COMPLETE, TOKEN_LIMIT, STOPPED, TIMED_OUT, FAILED }

data class LocalBenchmarkResult(val end: LocalBenchmarkEnd, val measurement: LocalGenerationMeasurement? = null)

/** Numbers for the current model load, held only in memory. */
data class LocalPerformanceDiagnostics(
    val loadPssKiB: Int? = null,
    val lastReply: LocalGenerationResult? = null,
    val benchmark: LocalBenchmarkResult? = null
)

internal const val LOCAL_BENCHMARK_TIMEOUT_MILLIS = 60_000L
internal const val LOCAL_BENCHMARK_OUTPUT_CAP = 384
internal const val LOCAL_BENCHMARK_PROMPT = "Write one short sentence about a garden."
