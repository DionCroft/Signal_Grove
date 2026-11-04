package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.hermesmobile.app.updates.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class AppUpdateTest {
    private fun version(s:String)=AppVersion.parse(s)!!
    private val body="fixture-apk-content".toByteArray()
    private val sha=MessageDigest.getInstance("SHA-256").digest(body).joinToString("") {"%02x".format(it)}
    private fun asset(debug:Boolean=false,change:Map<String,JsonElement> = emptyMap()):JsonObject {
        val name="hermes-agent-mobile-1.2.0"+(if(debug) "-debug" else "")+".apk"
        return JsonObject(buildJsonObject {put("name",name);put("state","uploaded");put("size",body.size);put("digest","sha256:$sha");put("browser_download_url","https://github.com/DionCroft/hermes-agent-android/releases/download/v1.2.0/$name")}+change)
    }
    private fun release(assets:List<JsonObject> = listOf(asset()),tag:String="v1.2.0",preview:Boolean=false,draft:Boolean=false)=buildJsonObject {put("id",200);put("tag_name",tag);put("draft",draft);put("prerelease",preview);put("body","Fixture notes");put("assets",JsonArray(assets))}
    private fun candidate(j:JsonElement=release(),debug:Boolean=false,preview:Boolean=false)=AppRelease.select(j,version("1.1.0-beta.1"),debug,preview)
    @Test fun semanticVersionsUseNumericOrderingAndStableFollowsPreviews() {val ordered=listOf("1.0.0-alpha","1.0.0-alpha.1","1.0.0-alpha.beta","1.0.0-beta","1.0.0-beta.2","1.0.0-beta.11","1.0.0-rc.1","1.0.0","1.1.0","1.10.0","2.0.0").map(::version);ordered.zipWithNext().forEach {(a,b)->assertTrue(a.text+" < "+b.text,a<b)};assertEquals(0,version("1.0.0+build.1").compareTo(version("1.0.0+build.2")))}
    @Test fun arbitrarilyLongNumericPreviewIdentifiersRemainNumeric() {assertTrue(version("1.0.0-9999999999999999999")<version("1.0.0-10000000000000000000"));assertTrue(version("1.0.0-10000000000000000000")<version("1.0.0-alpha"))}
    @Test fun malformedAndUnboundedVersionsAreRejected() {for(s in listOf("1.0","v1.0.0","01.0.0","1.0.0-01","1.0.0-","1.0.0/evil","1.0.0\n","1000001.0.0","1.0.0-"+"a".repeat(33))) assertNull(s,AppVersion.parse(s))}
    @Test fun sameOlderAndDraftReleasesAreNotUpdates() {assertNull(candidate(release(tag="v1.0.0")));assertNull(candidate(release(draft=true)));assertNull(AppRelease.select(release(),version("1.2.0"),false,false))}
    @Test fun previewChannelNeedsExplicitSelectionIncludingMislabelledPreviewTags() {assertNull(candidate(release(tag="v1.3.0-beta.1",preview=true)));assertNull(candidate(release(tag="v1.3.0-beta.1",preview=false)));assertNotNull(candidate(release(tag="v1.3.0-beta.1",preview=true),preview=true))}
    @Test fun productionAndDebugAssetsNeverCrossVariants() {val j=release(listOf(asset(),asset(true)));assertEquals("hermes-agent-mobile-1.2.0.apk",candidate(j)!!.assetName);assertEquals("hermes-agent-mobile-1.2.0-debug.apk",candidate(j,true)!!.assetName);assertFalse(candidate(release(listOf(asset(true))))!!.downloadable)}
    @Test fun missingInvalidDigestOrSizeCannotEnableDownload() {for(change in listOf(mapOf("digest" to JsonNull),mapOf("digest" to JsonPrimitive("sha256:bad")),mapOf("size" to JsonPrimitive(0)),mapOf("size" to JsonPrimitive(AppRelease.MAX_APK_BYTES+1)),mapOf("size" to JsonPrimitive("42")),mapOf("state" to JsonPrimitive("new")))) assertFalse(candidate(release(listOf(asset(change=change))))!!.downloadable)}
    @Test fun duplicatesAndUntrustedAssetDestinationsAreUnavailableWithoutDowngradingReport() {for(url in listOf("http://github.com/DionCroft/hermes-agent-android/releases/download/v1.2.0/hermes-agent-mobile-1.2.0.apk","https://github.com.evil.test/DionCroft/hermes-agent-android/releases/download/v1.2.0/hermes-agent-mobile-1.2.0.apk","https://github.com/other/repo/releases/download/v1.2.0/hermes-agent-mobile-1.2.0.apk","https://github.com/DionCroft/hermes-agent-android/releases/download/v1.2.0/hermes-agent-mobile-1.2.0.apk?token=x")) {val c=candidate(release(listOf(asset(change=mapOf("browser_download_url" to JsonPrimitive(url))))))!!;assertEquals("1.2.0",c.version.text);assertFalse(c.downloadable)};assertFalse(candidate(release(listOf(asset(),asset())))!!.downloadable)}
    @Test fun highestVersionWinsInsteadOfArrayOrderAndMissingAssetsDoNotHideARelease() {val newest=release(emptyList(),tag="v1.4.0");val c=candidate(JsonArray(listOf(newest,release())))!!;assertEquals("1.4.0",c.version.text);assertFalse(c.downloadable)}
    @Test fun frozenPublicMetadataRestoresOnlyTheExpectedVariantAndRepository() {val original=candidate()!!;val restored=AppRelease.restore(original.json(),false);assertEquals(original.key,restored.key);assertTrue(restored.downloadable);assertTrue(runCatching {AppRelease.restore(original.json(),true)}.isFailure);assertTrue(runCatching {AppRelease.restore(JsonObject(original.json()+mapOf("asset_url" to JsonPrimitive("https://evil.test/update.apk"))),false)}.isFailure)}
    @Test fun unavailableAssetMetadataRetainsANoticeWithoutBecomingDownloadable() {val release=candidate(release(emptyList()))!!;val restored=AppRelease.restore(release.json(),false);assertEquals(release.key,restored.key);assertFalse(restored.downloadable);assertTrue(runCatching {AppRelease.restore(JsonObject(release.json()+mapOf("sha256" to JsonPrimitive(sha))),false)}.isFailure)}
    @Test fun strictRedirectsRejectPlaintextCredentialsLookalikesAndFragments() {for(url in listOf("http://release-assets.githubusercontent.com/a","https://release-assets.githubusercontent.com.evil.test/a","https://user:pass@github.com/a","https://github.com/a#fragment","https://example.com/a")) assertFalse(url,OfficialUpdateEndpoints.redirect(url.toHttpUrl()));assertTrue(OfficialUpdateEndpoints.redirect("https://release-assets.githubusercontent.com/a?signature=fixture".toHttpUrl()))}
    @Test fun workGuardKeepsOtherOwnersBusyAndNeverUnderflows() {val guard=UpdateWorkGuard();val a=Any();val b=Any();guard.set(a,true);guard.set(b,true);guard.set(a,false);assertTrue(guard.active.value);guard.set(a,false);assertTrue(guard.active.value);guard.set(b,false);assertFalse(guard.active.value)}
    private fun exercise(mode:String)=runBlocking {
        MockWebServer().use {server->
            val requests=AtomicInteger()
            server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
                requests.incrementAndGet();assertNull(request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"));assertNull(request.getHeader("Origin"));assertEquals("GET",request.method)
                if(request.path=="/repos/DionCroft/hermes-agent-android/releases/latest") return when(mode) {"rate"->MockResponse().setResponseCode(403);"html"->MockResponse().setBody("not-json");"metadata-redirect"->MockResponse().setResponseCode(302).setHeader("Location","https://evil.test");else->MockResponse().setHeader("Content-Type","application/json").setBody(release().toString())}
                if(request.path.orEmpty().startsWith("/DionCroft/")) return when(mode) {"bad-redirect"->MockResponse().setResponseCode(302).setHeader("Location","https://evil.test/update.apk");"plain-redirect"->MockResponse().setResponseCode(302).setHeader("Location","http://github.com/update.apk");"loop"->MockResponse().setResponseCode(302).setHeader("Location","https://github.com"+request.path);"redirect"->MockResponse().setResponseCode(302).setHeader("Location","https://release-assets.githubusercontent.com/fixture");"wrong"->MockResponse().setBody("changed-apk-content");"short"->MockResponse().setBody("x");"http"->MockResponse().setResponseCode(500);else->MockResponse().setBody(okio.Buffer().write(body))}
                if(request.path=="/fixture") return MockResponse().setBody(okio.Buffer().write(body))
                return MockResponse().setResponseCode(404)
            }}
            val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).addInterceptor {chain->val original=chain.request();chain.proceed(original.newBuilder().url(server.url(original.url.encodedPath+(original.url.encodedQuery?.let {"?$it"} ?: ""))).build())}.build()
            val updater=GitHubUpdateClient(http=client)
            val selected=runCatching {updater.check(version("1.1.0"),false,false)}
            if(mode in setOf("rate","html","metadata-redirect")) {assertTrue(selected.isFailure);if(mode=="rate") assertTrue(selected.exceptionOrNull() is UpdateRateLimited);assertEquals(1,requests.get());return@use}
            val file=File.createTempFile("hermes-update-",".apk")
            try {
                val result=runCatching {updater.download(selected.getOrThrow()!!,file) {_,_->}}
                if(mode in setOf("ok","redirect")) {result.getOrThrow();assertArrayEquals(body,file.readBytes())}
                else {assertTrue(result.isFailure);assertFalse(file.exists())}
                assertEquals(when(mode) {"redirect"->3;"loop"->6;else->2},requests.get())
            } finally {file.delete()}
        }
    }
    @Test fun cancellationAfterHeadersClosesAStalledBodyAndDeletesThePartialFile()=runBlocking {
        MockWebServer().use {server->
            val bytes=ByteArray(64*1024) {7};val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)).throttleBody(1024,10,java.util.concurrent.TimeUnit.SECONDS))
            val http=OkHttpClient.Builder().addInterceptor {chain->chain.proceed(chain.request().newBuilder().url(server.url("/fixture.apk")).build())}.build()
            val release=AppRelease(1,"v1.2.0",version("1.2.0"),"fixture","hermes-agent-mobile-1.2.0.apk","https://github.com/DionCroft/hermes-agent-android/releases/download/v1.2.0/hermes-agent-mobile-1.2.0.apk".toHttpUrl(),bytes.size.toLong(),digest,false)
            val file=File.createTempFile("hermes-update-cancel-",".apk");val entered=java.util.concurrent.CountDownLatch(1)
            val job=launch {runCatching {GitHubUpdateClient(http=http).download(release,file) {_,_->entered.countDown()}}}
            withContext(Dispatchers.IO) {assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))}
            withTimeout(3000) {job.cancelAndJoin()};assertFalse(file.exists());assertEquals(1,server.requestCount)
        }
    }
    @Test fun publicMetadataAndDownloadNeverUseHermesCredentials()=exercise("ok")
    @Test fun approvedGitHubAssetRedirectIsFollowed()=exercise("redirect")
    @Test fun unknownRedirectHostIsRejectedBeforeAnotherRequest()=exercise("bad-redirect")
    @Test fun plaintextRedirectIsRejectedBeforeAnotherRequest()=exercise("plain-redirect")
    @Test fun redirectLoopsAreBounded()=exercise("loop")
    @Test fun changedDigestNeverLeavesAnInstallableFile()=exercise("wrong")
    @Test fun truncatedDownloadNeverLeavesAnInstallableFile()=exercise("short")
    @Test fun downloadHttpFailureIsNotRetried()=exercise("http")
    @Test fun rateLimitDoesNotBecomeNoUpdates()=exercise("rate")
    @Test fun htmlMetadataDoesNotBecomeNoUpdates()=exercise("html")
    @Test fun metadataRedirectDoesNotLeakRequestsToAnotherOrigin()=exercise("metadata-redirect")
}
