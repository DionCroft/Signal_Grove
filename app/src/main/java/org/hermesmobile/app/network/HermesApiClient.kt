package org.hermesmobile.app.network

import org.hermesmobile.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HermesHttpException(val code: Int) : IOException(when (code) {
    401,403 -> "Authentication failed. Check your token and server permissions."
    404,405,501 -> "This feature is unavailable on this server."
    409 -> "The server cannot safely start this turn. Check its session ownership and run status."
    429 -> "The server is busy. Wait, then check the run status."
    else -> "The server returned HTTP $code."
})
fun connectionError(e: Throwable): String = when(e) {
    is HermesHttpException -> e.message.orEmpty()
    is javax.net.ssl.SSLException -> "The server's HTTPS certificate could not be trusted."
    is java.net.SocketTimeoutException -> "The connection timed out. The agent may still be working."
    is java.net.UnknownHostException -> "The server could not be found. Check the address and your VPN."
    is IllegalArgumentException -> e.message ?: "Check the server address."
    else -> "Connection interrupted. Check your network and refresh the run status before retrying."
}
interface HermesApiClient {
    suspend fun capabilities(): HermesCapabilities
    suspend fun sessions(): List<RemoteSession>
    suspend fun sessionPage(offset: Int = 0, limit: Int = 50): SessionPage = SessionPage(sessions().drop(offset).take(limit),offset+limit,false)
    suspend fun history(id: String): MessageHistory = MessageHistory(id,messages(id))
    suspend fun renameSession(id: String,title: String) { throw UnsupportedOperationException("Session renaming is unavailable.") }
    suspend fun deleteSession(id: String) { throw UnsupportedOperationException("Session deletion is unavailable.") }
    suspend fun createSession(id: String, title: String): RemoteSession
    suspend fun messages(id: String): List<RemoteMessage>
    suspend fun jobs(): List<HermesJob>
    suspend fun mutateJob(operation: String,id: String = "",body: JsonObject = buildJsonObject {}): HermesJob? {throw UnsupportedOperationException("Job management is unavailable.")}
    suspend fun job(id: String): HermesJob
    suspend fun submitRun(session: String, input: String, key: String): String
    suspend fun run(id: String): JsonObject
    fun stream(path: String, body: JsonObject? = null, key: String = "",lastEventId: Long? = null): Flow<StreamUpdate>
    suspend fun stop(id: String)
    suspend fun approve(approval: Approval, choice: String)
    suspend fun respondControl(control: PendingControl,response: String) {
        if(control.kind=="approval") approve(control.approval(),response)
        else throw UnsupportedOperationException("Clarification responses are unavailable.")
    }
    suspend fun resolveUnknown(run: String,admission: String,generation: Long): JsonObject {throw UnsupportedOperationException("Unknown-run resolution is unavailable.")}
}

class OkHttpHermesApiClient(private val config: ServerConfig, debugBuild: Boolean = false) : HermesApiClient {
    private val base = validateUrl(config, debugBuild)
    // Redirects are disabled so credentials cannot follow a redirect to a different host.
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private fun request(path: String, body: JsonObject? = null, key: String = ""): Request {
        // Never resolve a leading slash: multi-profile /p/name/ prefixes must survive.
        val url = base.newBuilder().addPathSegments(path.substringBefore('?').trimStart('/')).apply {
            if ('?' in path) encodedQuery(path.substringAfter('?'))
        }.build()
        return Request.Builder().url(url).header("Authorization", "Bearer ${config.token}")
            .header("Accept", if (path.endsWith("stream") || path.endsWith("events") || body?.get("stream")?.jsonPrimitive?.booleanOrNull == true) "text/event-stream" else "application/json")
            .apply { if(key.isNotBlank()) header("Idempotency-Key", key); if(body != null) post(body.toString().toRequestBody(jsonType)) }.build()
    }
    private suspend fun json(path: String, body: JsonObject? = null, key: String = "", method: String = ""): JsonObject = suspendCancellableCoroutine { continuation ->
        val req=request(path,body,key)
        val call = client.newCall(if(method.isBlank()) req else req.newBuilder().method(method,if(method=="DELETE") null else body.toString().toRequestBody(jsonType)).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if(!it.isSuccessful) throw HermesHttpException(it.code)
                    require(it.body.contentLength() <= 10_000_000) { "Server response is too large" }
                    val source=it.body.source()
                    val buffer=okio.Buffer()
                    while(buffer.size <= 10_000_000 && source.read(buffer,8192) != -1L) { }
                    require(buffer.size <= 10_000_000) { "Server response is too large" }
                    Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                } }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
    override suspend fun capabilities(): HermesCapabilities = try { HermesCapabilities.parse(json("v1/capabilities")) }
        catch(e: HermesHttpException) { if(e.code !in listOf(404,405)) throw e
            val models = json("v1/models"); HermesCapabilities(model = models.items("data").firstOrNull()?.text("id").orEmpty().ifBlank { "hermes-agent" }) }
    override suspend fun sessionPage(offset: Int,limit: Int): SessionPage {
        require(offset>=0 && limit in 1..200)
        val page=json("api/sessions?source=api_server&limit=$limit&offset=$offset")
        val rows=page.items("data").map { RemoteSession(it.text("id"),it.text("title"),((it["last_active"] as? JsonPrimitive)?.doubleOrNull?.times(1000))?.toLong() ?: 0) }
        return SessionPage(rows,offset+limit,page["has_more"]?.jsonPrimitive?.booleanOrNull==true && rows.isNotEmpty())
    }
    override suspend fun sessions(): List<RemoteSession> {
        val rows=mutableListOf<RemoteSession>(); var offset=0
        do { val page=sessionPage(offset,100);rows+=page.rows;offset=page.nextOffset
            if(!page.hasMore) break
            require(offset<100_000) { "Too many sessions. Use the paginated conversation browser." }
        } while(true)
        return rows.distinctBy {it.id}
    }
    override suspend fun renameSession(id: String,title: String) {
        require(title.isNotBlank() && title.length<=200)
        json("api/sessions/${segment(id)}",buildJsonObject {put("title",title)},method="PATCH")
    }
    override suspend fun deleteSession(id: String) { json("api/sessions/${segment(id)}",method="DELETE") }
    override suspend fun createSession(id: String, title: String): RemoteSession {
        val j = json("api/sessions", buildJsonObject { put("id",id); put("title",title); put("source","api_server") })
        val s = j["session"] as? JsonObject ?: j
        return RemoteSession(s.text("id").ifBlank { id }, s.text("title").ifBlank { title })
    }
    override suspend fun messages(id: String) = history(id).rows
    override suspend fun history(id: String): MessageHistory {
        val all=mutableListOf<RemoteMessage>();var offset=0;var resolved=id
        do {
            val j=json("api/sessions/${segment(resolved)}/messages?inline_images=false&limit=500&offset=$offset&order=oldest")
            resolved=j.text("session_id").ifBlank {resolved}
            val rows=j.items("data")
            all+=rows.filter {it.text("role") in listOf("user","assistant")}.mapIndexed {index,m ->
                val ts=(m["timestamp"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
                val content=when(val value=m["content"]) {
                    is JsonPrimitive -> value.contentOrNull.orEmpty()
                    is JsonArray -> value.mapNotNull {it as? JsonObject}.filter {it.text("type")=="text"}.joinToString("\n") {it.text("text")}
                    else -> ""
                }
                RemoteMessage(m.text("role"),content,(ts*1000).toLong(),m.text("id").ifBlank {"legacy_${offset+index}"})
            }
            offset+=rows.size
            if(rows.size<500) break
            require(offset<100_000) { "History exceeds the mobile import limit. Saved messages were kept." }
        } while(true)
        return MessageHistory(resolved,all.distinctBy {it.id})
    }
    private fun parseJob(j: JsonObject): HermesJob {
        val schedule=when(val value=j["schedule"]) {is JsonObject -> value.text("display").ifBlank {value.text("cron")}.ifBlank {value.toString()}; is JsonPrimitive -> value.contentOrNull.orEmpty();else -> ""}
        val enabled=(j["enabled"]?.jsonPrimitive?.booleanOrNull ?: true) && j.text("state")!="paused"
        return HermesJob(j.text("id"),j.text("name"),j.text("prompt"),schedule,j.text("last_status").ifBlank {j.text("state")}.ifBlank {if(enabled) "scheduled" else "paused"},enabled,j.text("next_run_at").ifBlank {j.text("next_run")},j.text("last_run_at").ifBlank {j.text("last_run")} )
    }
    override suspend fun jobs() = json("api/jobs?include_disabled=true").items("jobs").map(::parseJob)
    override suspend fun job(id: String): HermesJob { val j=json("api/jobs/${segment(id)}"); return parseJob(j["job"] as? JsonObject ?: j) }
    override suspend fun mutateJob(operation: String,id: String,body: JsonObject): HermesJob? {
        JobProtocol.validate(operation,id,body)
        val path=if(operation=="create") "api/jobs" else "api/jobs/${segment(id)}"+if(operation in setOf("pause","resume","run")) "/$operation" else ""
        val method=when(operation) {"update"->"PATCH";"delete"->"DELETE";else->"POST"}
        val ack=json(path,if(method=="DELETE") null else body,method=method)
        if(operation=="delete") {require(ack["ok"]?.jsonPrimitive?.booleanOrNull==true) {"Deletion was not confirmed."};return null}
        val job=parseJob(ack["job"] as? JsonObject ?: throw IllegalArgumentException("Job acknowledgement is incomplete."))
        require(job.id.isNotBlank() && (operation=="create" || job.id==id)) {"Job acknowledgement identity does not match."}
        return job
    }
    override suspend fun submitRun(session: String, input: String, key: String) = json("v1/runs", buildJsonObject { put("session_id",session); put("input",input) }, key).text("run_id").also { require(it.isNotBlank()) { "The server did not return a run identifier." } }
    override suspend fun run(id: String) = json("v1/runs/${segment(id)}")
    override suspend fun stop(id: String) { json("v1/runs/${segment(id)}/stop", buildJsonObject {}) }
    override suspend fun approve(approval: Approval,choice: String) {
        respondControl(ControlRequest(approval.runId,approval.requestId,approval.generation,"approval",approval.description,approval.choices).stored(""),choice)
    }
    override suspend fun respondControl(control: PendingControl,response: String) {
        val ack=json("v1/runs/${segment(control.runId)}/${if(control.kind=="approval") "approval" else "clarify"}",ControlProtocol.responseBody(control,response))
        val request=ack.text("prompt_id").ifBlank {ack.text("request_id")}
        val resolved=ack.text("status") in listOf("resolved","already_resolved") ||
            (ack["resolved"] as? JsonPrimitive)?.booleanOrNull==true || ((ack["resolved"] as? JsonPrimitive)?.intOrNull ?: 0)>0
        require(resolved && request==control.requestId && ack.text("run_id")==control.runId) {"The server did not confirm this exact response. Refresh run status before trying again."}
    }
    override suspend fun resolveUnknown(run: String,admission: String,generation: Long) = json("v1/runs/${segment(run)}/resolve-unknown",buildJsonObject {put("admission_id",admission);put("execution_generation",generation)})
    override fun stream(path: String, body: JsonObject?, key: String,lastEventId: Long?): Flow<StreamUpdate> = callbackFlow {
        val call = client.newBuilder().readTimeout(3,TimeUnit.MINUTES).build().newCall(request(path,body,key).newBuilder().apply {if(lastEventId!=null && lastEventId>=0 && body==null) header("Last-Event-ID",lastEventId.toString())}.build())
        val worker = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw HermesHttpException(response.code)
                    require(response.header("Content-Type").orEmpty().startsWith("text/event-stream")) { "The server did not return an event stream." }
                    val source = response.body.source()
                    val parser = HermesStreamParser()
                    while(isActive) {
                        val line = source.readUtf8LineStrict(2_000_000)
                        parser.line(line)?.let { send(StreamDecoder.decode(it)) }
                    }
                }
                close()
            } catch(e: java.io.EOFException) { close() }
            catch(e: Exception) { close(e) }
        }
        awaitClose { call.cancel(); worker.cancel() }
    }
    companion object {
        fun segment(id: String): String { require(id !in setOf(".","..") && id.matches(Regex("[A-Za-z0-9_.:-]+"))) { "Unsupported server identifier" }; return id }
        fun validateUrl(config: ServerConfig, debugBuild: Boolean): HttpUrl {
            val b=config.url.trim().trimEnd('/').plus("/").toHttpUrl()
            require(b.username.isBlank() && b.password.isBlank() && b.query == null && b.fragment == null) { "Use a server URL without credentials, query parameters or fragments." }
            require(b.isHttps || (debugBuild && config.allowDebugHttp && b.scheme == "http")) { "Use HTTPS. Local HTTP testing is available only when explicitly enabled in a debug build." }
            require(config.token.isNotBlank() && config.token.all { it.code in 33..126 }) { "Enter your Hermes bearer token." }
            return b
        }
    }
}
