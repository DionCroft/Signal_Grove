package org.hermesmobile.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Ephemeral RFC 8252 callback; neither verifier nor authorization code is persisted. */
class NativeLogin:Closeable {
    private val server=ServerSocket().apply {try {reuseAddress=false;bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));soTimeout=1000} catch(e:Exception) {close();throw e}}
    val verifier=random()
    private val state=random()
    val redirect="http://127.0.0.1:${server.localPort}/callback"
    fun authorize(base:HttpUrl):HttpUrl=base.newBuilder().addPathSegments("auth/native/authorize")
        .addQueryParameter("code_challenge",challenge(verifier)).addQueryParameter("code_challenge_method","S256")
        .addQueryParameter("redirect_uri",redirect).addQueryParameter("state",state).build()
    suspend fun awaitCode(timeoutSeconds:Long=600):String=withContext(Dispatchers.IO) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while(System.nanoTime()<deadline) {
            currentCoroutineContext().ensureActive()
            val socket=try {server.accept()} catch(_:SocketTimeoutException) {continue}
            socket.use {client ->
                client.soTimeout=3000
                var code:String?=null
                runCatching {
                    require(client.inetAddress.isLoopbackAddress)
                    val input=client.getInputStream()
                    val first=line(input,8192).split(' ');require(first.size==3 && first[0]=="GET" && first[2] in setOf("HTTP/1.1","HTTP/1.0"))
                    val headers=mutableListOf<Pair<String,String>>();var total=0
                    while(true) {
                        val row=line(input,8192);total+=row.length;require(total<=16384)
                        if(row.isEmpty()) break
                        require(':' in row);headers+=row.substringBefore(':').lowercase() to row.substringAfter(':').trim()
                    }
                    require(headers.filter {it.first=="host"}.map {it.second}==listOf("127.0.0.1:${server.localPort}"))
                    code=callback(first[1],state)
                }
                runCatching {
                    val body=if(code!=null) "Sign-in received. You may return to Hermes Agent Mobile." else "Invalid sign-in callback. Return to the sign-in page or cancel in the app."
                    val status=if(code!=null) "200 OK" else "400 Bad Request"
                    val response="HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.toByteArray().size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'\r\nConnection: close\r\n\r\n$body"
                    client.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                }
                code?.let {return@withContext it}
            }
        }
        throw java.io.IOException("Sign-in timed out. Start a new sign-in.")
    }
    override fun close() {server.close()}
    companion object {
        private fun random()=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also {SecureRandom().nextBytes(it)})
        fun challenge(verifier:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        fun callback(target:String,state:String):String {
            require(target.length<=8192 && target.startsWith("/callback?") && target.none {it.code<=32 || it.code>=127 || it=='\\' || it=='#'})
            val url=HttpUrl.Builder().scheme("http").host("127.0.0.1").encodedPath("/callback").encodedQuery(target.substringAfter('?')).build()
            require(url.queryParameterValues("state").size==1 && url.queryParameterValues("code").size==1)
            require(url.queryParameterNames==setOf("state","code"))
            val received=url.queryParameter("state").orEmpty()
            require(MessageDigest.isEqual(received.toByteArray(Charsets.UTF_8),state.toByteArray(Charsets.UTF_8)))
            return url.queryParameter("code").orEmpty().also {require(it.matches(Regex("[A-Za-z0-9_-]{20,2048}")))}
        }
        private fun line(input:InputStream,max:Int):String {
            val out=StringBuilder()
            while(out.length<=max) {
                val byte=input.read();require(byte>=0)
                if(byte==10) {require(out.endsWith("\r"));return out.dropLast(1).toString()}
                require(byte==13 || byte in 32..126);out.append(byte.toChar())
            }
            throw java.io.IOException("Sign-in callback exceeds the input limit.")
        }
    }
}
