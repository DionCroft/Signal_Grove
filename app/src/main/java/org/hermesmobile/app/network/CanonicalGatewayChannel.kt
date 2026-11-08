package org.hermesmobile.app.network

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import okhttp3.*
import okio.ByteString
import org.hermesmobile.app.data.ManagementTarget
import org.hermesmobile.app.data.PluginHome

internal class CanonicalScopeChanged : Exception("Canonical profile identity changed.")
internal class CanonicalNotSupported : Exception("Canonical session transport is unavailable.")

/** Runtime identity only. Features are protocol support, never actor grants or execution state. */
internal class CanonicalRuntimeBinding(
    val target: ManagementTarget,
    val homeId: String,
    val profileId: String,
    val instanceId: String,
    val authorityEpoch: Long
) {
    override fun toString() = "CanonicalRuntimeBinding(redacted)"

    companion object {
        internal fun parse(result: JsonObject, target: ManagementTarget, homeId: String): CanonicalRuntimeBinding {
            fun text(key: String, limit: Int) = (result[key] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content
                ?.also { require(it.isNotBlank() && it.length <= limit && it.none(Char::isISOControl) && it.codePoints().noneMatch { c -> c in 0xD800..0xDFFF }) }
                ?: throw GatewayProtocolException()
            val profile = text("profile_id", 8192)
            if (PluginHome.project(JsonPrimitive(profile))?.identity() != homeId) throw CanonicalScopeChanged()
            val instance = text("instance_id", 512)
            val epoch = (result["authority_epoch"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
                ?.takeIf { it >= 0 } ?: throw GatewayProtocolException()
            fun strings(value: JsonElement?, limit: Int): Set<String> {
                val rows = value as? JsonArray ?: throw GatewayProtocolException()
                require(rows.size <= limit)
                val items = rows.map {
                    (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                        ?.also { s -> require(s.isNotBlank() && s.length <= 256 && s.none(Char::isISOControl)) }
                        ?: throw GatewayProtocolException()
                }
                require(items.size == items.toSet().size)
                return items.toSet()
            }
            val features = strings(result["capabilities"], 128)
            if (!features.containsAll(setOf("durable-admission-v1", "event-replay-v1"))) throw CanonicalNotSupported()
            val create = result["session_create"] as? JsonObject ?: throw GatewayProtocolException()
            strings(create["sources"], 128); strings(create["parameters"], 256)
            if (result["skin"] != null && result["skin"] != JsonNull && result["skin"] !is JsonObject) throw GatewayProtocolException()
            return CanonicalRuntimeBinding(target, homeId, profile, instance, epoch)
        }
    }
}

/**
 * Separate canonical socket. This stage negotiates identity only: no session read,
 * restore/adoption, subscription, server-request capability, work admission or retry.
 */
internal class CanonicalGatewayChannel private constructor() : Closeable {
    private val opened = CompletableDeferred<Unit>()
    private val ready = CompletableDeferred<Unit>()
    private val runtime = CompletableDeferred<JsonObject>()
    private val stopped = AtomicBoolean(false)
    private val firstFailure = AtomicReference<Exception?>()
    private var readySeen = false
    private var describeSent = false
    private var describeAnswered = false
    private var notificationCount = 0
    private var receivedBytes = 0L
    @Volatile private var socket: WebSocket? = null
    @Volatile private var verified: CanonicalRuntimeBinding? = null

    val binding: CanonicalRuntimeBinding
        get() = verified?.takeUnless { stopped.get() } ?: throw GatewayUnavailable()

    private fun fail(error: Exception) {
        firstFailure.compareAndSet(null, error)
        stopped.set(true); verified = null
        val cause = firstFailure.get() ?: error
        opened.completeExceptionally(cause); ready.completeExceptionally(cause); runtime.completeExceptionally(cause)
        socket?.cancel()
    }

    /** Bound depth before invoking the JSON parser, including escaped object keys. */
    private fun frame(text: String): JsonObject {
        require(text.length <= 262144)
        val bytes = text.toByteArray(Charsets.UTF_8).size
        receivedBytes += bytes
        require(bytes <= 262144 && receivedBytes <= 2097152)
        val containers = ArrayList<MutableSet<String>?>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{' -> { require(containers.size < 32); containers.add(mutableSetOf()) }
                '[' -> { require(containers.size < 32); containers.add(null) }
                '}', ']' -> { require(containers.isNotEmpty()); containers.removeAt(containers.lastIndex) }
                '"' -> {
                    val start = index++
                    while (index < text.length && text[index] != '"') {
                        if (text[index] == '\\') index++
                        index++
                    }
                    require(index < text.length)
                    var next = index + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') {
                        val keys = containers.lastOrNull() ?: throw GatewayProtocolException()
                        val raw = text.substring(start, index + 1)
                        require(raw.length <= 4096)
                        require(keys.add(Json.decodeFromString<String>(raw)))
                    }
                }
            }
            index++
        }
        require(containers.isEmpty())
        return Json.parseToJsonElement(text) as? JsonObject ?: throw GatewayProtocolException()
    }

    @Synchronized private fun accept(text: String) {
        if (stopped.get()) return
        try {
            val j = frame(text)
            if (j["jsonrpc"] != JsonPrimitive("2.0")) throw GatewayProtocolException()
            if (!j.containsKey("id")) {
                if (j.keys != setOf("jsonrpc", "method", "params") || j["method"] != JsonPrimitive("event")) throw GatewayProtocolException()
                val params = j["params"] as? JsonObject ?: throw GatewayProtocolException()
                val type = (params["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw GatewayProtocolException()
                if (type == "gateway.ready") {
                    if (readySeen) throw GatewayProtocolException()
                    readySeen = true
                    val payload = params["payload"] as? JsonObject ?: throw GatewayProtocolException()
                    if (payload["session_authority"] != JsonPrimitive(true)) throw CanonicalNotSupported()
                    if (payload["skin"] !is JsonObject || payload["change_events"] !is JsonPrimitive ||
                        payload["change_events"]!!.jsonPrimitive.takeIf { !it.isString }?.booleanOrNull == null) throw GatewayProtocolException()
                    val epoch = (payload["replay_epoch"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw GatewayProtocolException()
                    require(epoch.isNotBlank() && epoch.length <= 256 && epoch.none(Char::isISOControl))
                    payload["heartbeat"]?.let { require(it is JsonPrimitive && !it.isString && it.booleanOrNull != null) }
                    ready.complete(Unit)
                } else {
                    // Host notifications are observations. A transcript event before attachment is unsolicited.
                    if (!readySeen || params.containsKey("session_id") || ++notificationCount > 64 || type.isBlank() || type.length > 256) throw GatewayProtocolException()
                }
                return
            }
            if (!readySeen || !describeSent || describeAnswered || j["id"] != JsonPrimitive("1") ||
                j.containsKey("method") || j.containsKey("result") == j.containsKey("error") ||
                j.keys.any { it !in setOf("jsonrpc", "id", "result", "error") }) throw GatewayProtocolException()
            describeAnswered = true
            if (j.containsKey("error")) {
                val error = j["error"] as? JsonObject ?: throw GatewayProtocolException()
                val code = (error["code"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: throw GatewayProtocolException()
                throw GatewayRpcException(code)
            }
            runtime.complete(j["result"] as? JsonObject ?: throw GatewayProtocolException())
        } catch (error: Exception) {
            fail(if (error is GatewayRpcException || error is CanonicalNotSupported) error else GatewayProtocolException())
        }
    }

    @Synchronized private fun describe() {
        if (stopped.get()) throw firstFailure.get() ?: GatewayUnavailable()
        check(readySeen && !describeSent)
        describeSent = true
        if (socket?.send("""{"jsonrpc":"2.0","id":"1","method":"runtime.describe","params":{}}""") != true) throw GatewayUnavailable()
    }

    override fun close() = fail(GatewayUnavailable())
    override fun toString() = "CanonicalGatewayChannel(redacted)"

    companion object {
        internal suspend fun connect(
            http: OkHttpClient, base: HttpUrl, ticket: String, target: ManagementTarget, homeId: String
        ): CanonicalGatewayChannel {
            require(ticket.matches(Regex("[A-Za-z0-9_-]{16,8192}")))
            require(base.toString() == target.url && homeId.matches(Regex("[a-f0-9]{64}")))
            val connection = CanonicalGatewayChannel()
            try {
                val request = Request.Builder().url(base.newBuilder().addPathSegments("api/ws").build())
                    .header("Sec-WebSocket-Protocol", "hermes-gateway-v1, hermes-gateway-ticket.$ticket").build()
                connection.socket = http.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (response.header("Sec-WebSocket-Protocol") != "hermes-gateway-v1") connection.fail(GatewayProtocolException())
                        else connection.opened.complete(Unit)
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) = connection.accept(text)
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = connection.fail(GatewayProtocolException())
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = connection.fail(GatewayUnavailable())
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = connection.fail(GatewayUnavailable())
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = connection.fail(GatewayUnavailable())
                })
                val result = withTimeoutOrNull(30000) {
                    connection.opened.await(); connection.ready.await(); connection.describe(); connection.runtime.await()
                } ?: throw GatewayUnavailable()
                val binding = try { CanonicalRuntimeBinding.parse(result, target, homeId) }
                catch (error: IllegalArgumentException) { throw GatewayProtocolException() }
                synchronized(connection) {
                    if (connection.stopped.get()) throw connection.firstFailure.get() ?: GatewayUnavailable()
                    connection.verified = binding
                }
                return connection
            } catch (error: Exception) { connection.close(); throw error }
        }
    }
}
