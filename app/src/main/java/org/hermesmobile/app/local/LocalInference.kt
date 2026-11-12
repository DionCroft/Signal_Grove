package org.hermesmobile.app.local

import java.io.File

interface LocalInference {
    suspend fun load(file: File, context: Int, threads: Int)
    suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int = 384, onText: (String) -> Unit): LocalGenerationEnd
    suspend fun selectHelperChecks(messages: List<LocalChatMessage>, choices: LocalCheckChoices, maxTokens: Int = 96,
        onText: (String) -> Unit): LocalGenerationEnd = generate(messages, maxTokens, onText)
    fun cancel()
    suspend fun unload()
}

internal class NativeLocalInference(root: File, directory: String) : LocalInference {
    private val engine = NativeLocalEngine(root, directory)
    override suspend fun load(file: File, context: Int, threads: Int) = engine.load(file, context, threads)
    override suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit) = engine.generate(messages, maxTokens, onText)
    override suspend fun selectHelperChecks(messages: List<LocalChatMessage>, choices: LocalCheckChoices, maxTokens: Int,
        onText: (String) -> Unit) = engine.selectHelperChecks(messages, choices, maxTokens, onText)
    override fun cancel() = engine.cancel()
    override suspend fun unload() = engine.unload()
}
