package org.hermesmobile.app.updates

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Public GitHub only. No Hermes/GitHub credentials, cookies or backend HTTP client are used. */
class GitHubUpdateClient(private val policy:UpdateEndpointPolicy=OfficialUpdateEndpoints,private val http:OkHttpClient=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(300,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).cookieJar(CookieJar.NO_COOKIES).build()) {
    private suspend fun response(url:HttpUrl,accept:String):Pair<Call,Response> {
        val request=Request.Builder().url(url).header("Accept",accept).header("User-Agent","Signal-Grove-Updater").apply {if(url.host=="api.github.com") header("X-GitHub-Api-Version","2026-03-10")}.build()
        val call=http.newCall(request)
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:java.io.IOException) {if(continuation.isActive) continuation.resumeWith(Result.failure(e))}
                override fun onResponse(call:Call,response:Response) {if(continuation.isActive) continuation.resume(call to response) {_,r,_->r.second.close()} else response.close()}
            })
        }
    }
    private suspend fun <T> read(url:HttpUrl,accept:String,block:suspend (Response)->T):T=coroutineScope {
        val (call,response)=response(url,accept)
        // Keep cancellation attached until the body is consumed, not only until headers arrive.
        val cancellation=launch(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED) {try {awaitCancellation()} finally {call.cancel()}}
        try {withContext(Dispatchers.IO) {response.use {block(it)}}} finally {cancellation.cancel();response.close()}
    }
    suspend fun check(current:AppVersion,debug:Boolean,previews:Boolean):AppRelease? {
        val payload=read(policy.metadata(previews),"application/vnd.github+json") {
            if(it.code in setOf(403,429)) throw UpdateRateLimited()
            if(it.code==404) throw UpdateUnavailable()
            require(it.code==200)
            require(it.header("Content-Type").orEmpty().substringBefore(';').trim().equals("application/json",true))
            val buffer=okio.Buffer();val source=it.body.source()
            while(buffer.size<=1_000_000 && source.read(buffer,8192)!=-1L) {currentCoroutineContext().ensureActive()}
            require(buffer.size<=1_000_000)
            Json.parseToJsonElement(buffer.readUtf8())
        }
        return AppRelease.select(payload,current,debug,previews,policy)
    }
    suspend fun download(release:AppRelease,file:File,progress:(Long,Long)->Unit) {
        require(release.downloadable);var url=release.assetUrl!!;var redirects=0
        try {
            while(true) {
                currentCoroutineContext().ensureActive()
                val next=read(url,"application/octet-stream") {
                    if(it.code in setOf(301,302,303,307,308)) {
                        val redirect=it.header("Location")?.let(url::resolve) ?: throw UpdateIntegrityFailure()
                        if(++redirects>4 || !policy.redirect(redirect)) throw UpdateIntegrityFailure()
                        return@read redirect
                    }
                    require(it.code==200);if(it.body.contentLength()>release.bytes!!) throw UpdateIntegrityFailure()
                    val digest=MessageDigest.getInstance("SHA-256");var total=0L
                    file.outputStream().use {out->it.body.byteStream().use {input->
                        val bytes=ByteArray(32*1024)
                        while(true) {currentCoroutineContext().ensureActive();val n=input.read(bytes);if(n<0) break;total+=n;if(total>release.bytes!!) throw UpdateIntegrityFailure();out.write(bytes,0,n);digest.update(bytes,0,n);progress(total,release.bytes!!)}
                    };out.fd.sync()}
                    val actual=digest.digest().joinToString("") {b->"%02x".format(b)}
                    if(total!=release.bytes || actual!=release.sha256) throw UpdateIntegrityFailure()
                    null
                }
                if(next==null) return
                url=next
            }
        } catch(e:Exception) {file.delete();throw e}
    }
}
