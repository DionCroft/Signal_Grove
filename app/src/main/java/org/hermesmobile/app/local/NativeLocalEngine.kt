package org.hermesmobile.app.local

import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LocalChatMessage(val role: String, val text: String)
enum class LocalGenerationEnd { COMPLETE, CANCELLED, TOKEN_LIMIT }
class LocalEngineException(val code: Int) : IllegalStateException("local_engine_$code")

/** No HTTP client, credentials or remote-session access. Only an app-private model root. */
class NativeLocalEngine(private val modelRoot: File, private val nativeLibraryDirectory: String) {
    private val mutex = Mutex()
    private val handle = AtomicLong(0)
    private val sequence = AtomicLong(0)
    private val current = AtomicLong(0)

    suspend fun load(file: File, context: Int = 2048, threads: Int = 4) = operation { id ->
        require(context in 512..4096 && threads in 1..4)
        require(file.canonicalFile.parentFile == modelRoot.canonicalFile)
        if (!NativeLlmBindings.available()) throw LocalEngineException(6)
        val coroutineContext = currentCoroutineContext()
        val metadata = GgufModelReader.read(file) { coroutineContext.ensureActive() }
        require(context <= metadata.trainedContext)
        if (handle.get() == 0L) handle.set(NativeLlmBindings.create(nativeLibraryDirectory))
        currentCoroutineContext().ensureActive()
        checkResult(NativeLlmBindings.load(handle.get(), file.canonicalPath, context, threads, id))
    }

    suspend fun countPromptTokens(messages: List<LocalChatMessage>): Int = operation { id ->
        val request = encodeRequest(messages)
        val count = NativeLlmBindings.countPrompt(handle.get(), request.roles, request.contents, id)
        if (count < 0) checkResult(-count)
        check(count > 0)
        count
    }

    suspend fun generate(messages: List<LocalChatMessage>, maxTokens: Int = 384, onText: (String) -> Unit): LocalGenerationEnd =
        generateInternal(messages, maxTokens, emptyList(), measure = false, onText).end

    suspend fun generateMeasured(messages: List<LocalChatMessage>, maxTokens: Int, onText: (String) -> Unit): LocalGenerationResult =
        generateInternal(messages, maxTokens, emptyList(), measure = true, onText)

    suspend fun selectHelperChecks(messages: List<LocalChatMessage>, choices: LocalCheckChoices, maxTokens: Int = 96,
        onText: (String) -> Unit): LocalGenerationEnd = generateInternal(messages, maxTokens, choices.ids, measure = false, onText).end

    private suspend fun generateInternal(messages: List<LocalChatMessage>, maxTokens: Int, helperIds: List<String>,
        measure: Boolean, onText: (String) -> Unit): LocalGenerationResult = operation { id ->
        require(maxTokens in 1..512)
        val request = encodeRequest(messages)
        val decoder = Utf8TokenDecoder()
        val nativeSample = if (measure) LongArray(2) else null
        var firstAnswerNanos: Long? = null
        val started = if (measure) System.nanoTime() else 0L
        val callback = object : NativeTokenSink {
            override fun onBytes(bytes: ByteArray): Boolean {
                if (current.get() != id) return false
                decoder.append(bytes)?.let { text ->
                    if (measure && firstAnswerNanos == null && text.isNotBlank()) {
                        firstAnswerNanos = (System.nanoTime() - started).coerceAtLeast(0)
                    }
                    onText(text)
                }
                return true
            }
        }
        val code = NativeLlmBindings.generate(handle.get(), request.roles, request.contents, helperIds.toTypedArray(),
            maxTokens, id, callback, nativeSample)
        val end = when (code) {
            0 -> LocalGenerationEnd.COMPLETE
            1 -> LocalGenerationEnd.CANCELLED
            5 -> LocalGenerationEnd.TOKEN_LIMIT
            else -> throw LocalEngineException(code)
        }
        val measurement = nativeSample?.takeIf {
            end != LocalGenerationEnd.CANCELLED && it[0] in 1L..maxTokens.toLong() && it[1] > 0L
        }?.let { LocalGenerationMeasurement(it[0].toInt(), it[1], firstAnswerNanos) }
        LocalGenerationResult(end, measurement)
    }

    private data class EncodedRequest(val roles: Array<String>, val contents: Array<ByteArray>)

    private fun encodeRequest(messages: List<LocalChatMessage>): EncodedRequest {
        require(handle.get() != 0L && messages.isNotEmpty() && messages.size <= 64 && messages.last().role == "user")
        require(messages.all { it.role in setOf("system", "user", "assistant") })
        val contents = messages.map { it.text.toByteArray(Charsets.UTF_8) }.toTypedArray()
        require(contents.sumOf { it.size } <= LOCAL_PROMPT_BYTE_LIMIT)
        return EncodedRequest(messages.map { it.role }.toTypedArray(), contents)
    }

    fun cancel() { val h = handle.get(); val id = current.get(); if (h != 0L && id > 0) NativeLlmBindings.cancel(h, id) }
    suspend fun unload() { cancel(); mutex.withLock { withContext(Dispatchers.IO) { handle.getAndSet(0).takeIf { it != 0L }?.let(NativeLlmBindings::dispose) } } }

    private suspend fun <T> operation(block: suspend (Long) -> T): T = mutex.withLock {
        val id = sequence.incrementAndGet(); current.set(id)
        try {
            coroutineScope {
                val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() }
                    finally { handle.get().takeIf { it != 0L }?.let { NativeLlmBindings.cancel(it, id) } }
                }
                try { withContext(Dispatchers.IO) { ensureActive(); block(id) } }
                finally { watcher.cancel() }
            }
        } finally { current.compareAndSet(id, 0) }
    }
    private fun checkResult(code: Int) { if (code == 1) throw CancellationException("Local model load cancelled"); if (code != 0) throw LocalEngineException(code) }
}

internal interface NativeTokenSink { fun onBytes(bytes: ByteArray): Boolean }
internal object NativeLlmBindings {
    init { System.loadLibrary("signal_grove_llm") }
    external fun available(): Boolean
    external fun create(nativeLibraryDirectory: String): Long
    external fun load(handle: Long, path: String, context: Int, threads: Int, operation: Long): Int
    external fun countPrompt(handle: Long, roles: Array<String>, contents: Array<ByteArray>, operation: Long): Int
    external fun generate(handle: Long, roles: Array<String>, contents: Array<ByteArray>, helperChoices: Array<String>, maxTokens: Int, operation: Long, sink: NativeTokenSink, measurement: LongArray? = null): Int
    external fun cancel(handle: Long, operation: Long)
    external fun dispose(handle: Long)
}

/** Tokens can end in the middle of a Chinese character or emoji. Emit only complete UTF-8. */
internal class Utf8TokenDecoder {
    private val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = byteArrayOf()
    private val text = StringBuilder()
    fun append(bytes: ByteArray): String? {
        require(bytes.size <= 65_536 && text.length <= 131_072)
        val input = ByteBuffer.wrap(pending + bytes)
        val chars = CharBuffer.allocate(input.remaining() * 2 + 2)
        val result = decoder.decode(input, chars, false)
        check(!result.isOverflow)
        pending = ByteArray(input.remaining()).also(input::get)
        chars.flip()
        if (!chars.hasRemaining()) return null
        text.append(chars)
        return text.toString()
    }
}
