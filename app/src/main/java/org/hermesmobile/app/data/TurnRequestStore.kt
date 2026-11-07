package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.hermesmobile.app.network.OkHttpHermesApiClient

data class TurnRequest(val transport: String,val path: String,val body: JsonObject,val channelKey: String) {
    fun validate() {
        require(channelKey.length<=256 && channelKey.all {it.code in 33..126}) {"Invalid conversation identity."}
        when(transport) {
            "runs" -> {require(path=="v1/runs");OkHttpHermesApiClient.segment(body.text("session_id"))}
            "session","demo" -> {val id=path.removePrefix("api/sessions/").removeSuffix("/chat/stream");OkHttpHermesApiClient.segment(id);require(path=="api/sessions/$id/chat/stream")}
            "compat" -> require(path=="v1/chat/completions" && body["messages"] is JsonArray)
            else -> error("Unsupported saved request transport.")
        }
    }
    companion object {
        fun create(c: Conversation,history: List<ChatMessage>,model: String,runtime: RuntimeChoice=RuntimeChoice(),content:(ChatMessage)->JsonElement={JsonPrimitive(it.content)}): TurnRequest {
            SessionOrigin.requireWritable(c)
            val path=when(c.transport) {"runs"->"v1/runs";"session","demo"->"api/sessions/${OkHttpHermesApiClient.segment(c.remoteId)}/chat/stream";else->"v1/chat/completions"}
            val input=history.lastOrNull {it.id=="${c.pendingKey}_user"}?.let(content) ?: JsonPrimitive(c.pendingInput)
            val body=buildJsonObject {
                when(c.transport) {
                    "runs" -> {put("session_id",c.remoteId);put("input",if(input is JsonArray) buildJsonArray {add(buildJsonObject {put("role","user");put("content",input)})} else input)}
                    "session","demo" -> put("input",input)
                    else -> {put("model",model);put("stream",true);put("messages",buildJsonArray {history.filter(RunStates::usableHistory).forEach {m -> add(buildJsonObject {put("role",m.role);put("content",content(m))})}})}
                }
            }
            val selected=JsonObject(body+runtime.fields())
            return TurnRequest(c.transport,path,selected,c.channelKey).also {it.validate()}
        }
    }
}
class RequestTooLarge: IllegalArgumentException("The saved request exceeds the mobile request limit.")

/** Immutable request blobs avoid Android's CursorWindow row limit when images are added.
 * App-private files contain request data only, never credentials. Freeze before committing the Room receipt.
 * The application is single-process; a shared directory monitor also serializes separate store instances. */
class TurnRequestStore(private val directory: File,private val maxBytes: Int=10_000_000) {
    private val lock=locks.getOrPut(directory.absolutePath) {Any()}
    init {require(maxBytes in 1..10_000_000)}
    private fun digest(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun partition(scope: String,conversation: String): File {
        require(scope.isNotBlank() && conversation.isNotBlank())
        return File(directory,digest((scope+"\u0000"+conversation).toByteArray(Charsets.UTF_8)))
    }
    private fun target(scope: String,conversation: String,key: String): File {
        require(key.isNotBlank())
        return File(partition(scope,conversation),digest(key.toByteArray(Charsets.UTF_8))+".json")
    }
    fun freeze(scope: String,conversation: String,key: String,request: TurnRequest): String = synchronized(lock) {
        request.validate()
        val bytes=buildJsonObject {put("version",1);put("scope",scope);put("conversation",conversation);put("key",key);put("transport",request.transport);put("path",request.path);put("channel_key",request.channelKey);put("body",request.body)}.toString().toByteArray(Charsets.UTF_8)
        if(bytes.size>maxBytes) throw RequestTooLarge()
        val hash=digest(bytes);val destination=target(scope,conversation,key)
        if(destination.exists()) {require(digest(readBounded(destination))==hash) {"This request identity is already bound to different content."};return@synchronized hash}
        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs()) {"Cannot save the request receipt."}
        val temporary=File.createTempFile("request-",".tmp",destination.parentFile)
        try {
            FileOutputStream(temporary).use {it.write(bytes);it.fd.sync()}
            try {Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE)}
            catch(_:AtomicMoveNotSupportedException) {Files.move(temporary.toPath(),destination.toPath())}
        } finally {temporary.delete()}
        hash
    }
    fun read(scope: String,conversation: String,key: String,expectedHash: String): TurnRequest = synchronized(lock) {
        require(expectedHash.matches(Regex("[a-f0-9]{64}"))) {"The saved request receipt is incomplete."}
        val bytes=readBounded(target(scope,conversation,key))
        require(digest(bytes)==expectedHash) {"The saved request changed. It will not be submitted."}
        val envelope=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(envelope["version"]?.jsonPrimitive?.intOrNull==1 && envelope.text("scope")==scope && envelope.text("conversation")==conversation && envelope.text("key")==key) {"The saved request belongs to another receipt."}
        TurnRequest(envelope.text("transport"),envelope.text("path"),envelope["body"]!!.jsonObject,envelope.text("channel_key")).also {it.validate()}
    }
    fun retainCurrent(scope: String,conversation: String,key: String) = synchronized(lock) {
        val keep=target(scope,conversation,key)
        partition(scope,conversation).listFiles().orEmpty().filter {it!=keep}.forEach {require(!it.isDirectory);check(it.delete()) {"Cannot remove an older request."}}
    }
    fun deleteConversation(scope: String,conversation: String) = synchronized(lock) {erasePartition(partition(scope,conversation))}
    fun clearAll() = synchronized(lock) {
        directory.listFiles().orEmpty().forEach {child ->
            if(child.isDirectory) {require(child.name.matches(Regex("[a-f0-9]{64}")));erasePartition(child)}
            else {require(child.name.matches(Regex("[a-f0-9]{64}\\.json|request-.*\\.tmp")));check(child.delete()) {"Cannot remove saved request data."}}
        }
    }
    private fun erasePartition(partition: File) {
        if(!partition.exists()) return
        require(!Files.isSymbolicLink(partition.toPath()))
        partition.listFiles().orEmpty().forEach {file ->require(!file.isDirectory);check(file.delete()) {"Cannot remove saved request data."}}
        check(partition.delete()) {"Cannot remove the saved request directory."}
    }
    private fun readBounded(file: File): ByteArray {
        require(file.isFile && file.length()<=maxBytes) {"The saved request is missing or too large. Check the server; it will not be rebuilt."}
        return file.inputStream().use {input ->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val count=input.read(buffer);if(count<0) break;if(out.size()+count>maxBytes) throw RequestTooLarge();out.write(buffer,0,count)}
            out.toByteArray()
        }
    }
    companion object {private val locks=ConcurrentHashMap<String,Any>()}
}
