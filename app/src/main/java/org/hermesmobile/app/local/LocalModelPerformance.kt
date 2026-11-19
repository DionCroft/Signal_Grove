package org.hermesmobile.app.local

/** Settings for the next explicit load. Thread caps are resolved against the current device. */
data class LocalModelPerformance(val contextTokens: Int, val threadCap: Int, val outputTokens: Int) {
    internal fun allowedBy(trainedContext: Int): Boolean =
        contextTokens in 512..minOf(4096, trainedContext) && threadCap in 1..4 &&
            outputTokens in 1..minOf(512, contextTokens - 1)

    internal fun resolve(processors: Int): LocalGenerationSettings =
        LocalGenerationSettings(contextTokens, minOf(threadCap, processors.coerceAtLeast(1)), outputTokens)
}

enum class LocalPerformancePreset(
    private val context: Int, private val threads: Int
) {
    BALANCED(2048, 4),
    FAST(1024, 4),
    BATTERY_SAVER(1024, 1);

    fun forModel(trainedContext: Int): LocalModelPerformance =
        // Shorter generation caps can exhaust the budget before reasoning yields visible text.
        LocalModelPerformance(minOf(context, trainedContext), threads, 384)
}
