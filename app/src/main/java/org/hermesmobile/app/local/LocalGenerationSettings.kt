package org.hermesmobile.app.local

/** Request parameters, not measured context use or a claim about engine allocation. */
data class LocalGenerationSettings(val contextTokens: Int, val threads: Int, val maxOutputTokens: Int,
    val runtime: LocalRuntimePolicy? = null) {
    fun validate() {
        require(contextTokens in 512..4096 && threads in 1..4 && maxOutputTokens in 1..512)
        runtime?.validate()
    }
}
enum class LocalSamplingPolicy { GREEDY_MODEL_METADATA_V1 }
data class LocalRuntimePolicy(val sourceSha256: String, val sampling: LocalSamplingPolicy) {
    fun validate() { require(sourceSha256.matches(Regex("[a-f0-9]{64}"))) }
}
