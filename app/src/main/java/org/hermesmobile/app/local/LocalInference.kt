package org.hermesmobile.app.local

import java.io.File

interface LocalInference {
    // Wrappers/custom fixtures without an identity must report unknown, never a fabricated native policy.
    val runtimePolicy: LocalRuntimePolicy? get() = null
    suspend fun load(file: File, context: Int, threads: Int)
    /** A source without a tokenizer can report no measurement. */
    suspend fun countPromptTokens(messages: List<LocalChatMessage>): Int? = null
    suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int = 384, onText: (String) -> Unit): LocalGenerationEnd
    suspend fun generateMeasured(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit): LocalGenerationResult =
        LocalGenerationResult(generate(messages, maxTokens, onText))
    suspend fun selectHelperChecks(messages: List<LocalChatMessage>, choices: LocalCheckChoices, maxTokens: Int = 96,
        onText: (String) -> Unit): LocalGenerationEnd = generate(messages, maxTokens, onText)
    fun cancel()
    suspend fun unload()
}

internal class NativeLocalInference(root: File, directory: String) : LocalInference {
    private val engine = NativeLocalEngine(root, directory)
    override val runtimePolicy = LocalRuntimePolicy(org.hermesmobile.app.BuildConfig.LOCAL_RUNTIME_SOURCE_SHA256,
        LocalSamplingPolicy.GREEDY_MODEL_METADATA_V1)
    override suspend fun load(file: File, context: Int, threads: Int) = engine.load(file, context, threads)
    override suspend fun countPromptTokens(messages: List<LocalChatMessage>) = engine.countPromptTokens(messages)
    override suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit) = engine.generate(messages, maxTokens, onText)
    override suspend fun generateMeasured(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit) =
        engine.generateMeasured(messages, maxTokens, onText)
    override suspend fun selectHelperChecks(messages: List<LocalChatMessage>, choices: LocalCheckChoices, maxTokens: Int,
        onText: (String) -> Unit) = engine.selectHelperChecks(messages, choices, maxTokens, onText)
    override fun cancel() = engine.cancel()
    override suspend fun unload() = engine.unload()
}
