package org.hermesmobile.app.local

/** Settings for the next explicit load. Thread caps are resolved against the current device. */
data class LocalModelPerformance(val contextTokens: Int, val threadCap: Int, val outputTokens: Int) {
    internal fun allowedBy(trainedContext: Int): Boolean =
        contextTokens in 512..minOf(4096, trainedContext) && threadCap in 1..4 &&
            outputTokens in 1..minOf(512, contextTokens - 1)

    internal fun resolve(processors: Int, runtime: LocalRuntimePolicy? = null): LocalGenerationSettings =
        LocalGenerationSettings(contextTokens, minOf(threadCap, processors.coerceAtLeast(1)), outputTokens, runtime)
}

enum class LocalPerformancePreset(
    private val context: Int, private val threads: Int, private val output: Int
) {
    BALANCED(2048, 4, 384),
    FAST(1024, 4, 256),
    BATTERY_SAVER(1024, 1, 128);

    fun forModel(trainedContext: Int): LocalModelPerformance =
        LocalModelPerformance(minOf(context, trainedContext), threads, output)
}
