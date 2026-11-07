package org.hermesmobile.app

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher as MockDispatcher
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Same client contract against a local fixture in CI or an explicitly supplied owned Hermes fixture.
 * No app preferences, saved credentials, sessions or management receipts are changed.
 */
class NativeManagementContractTest {
    private class BrowserCookies:CookieJar {
        private val values=mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>) {
            cookies.forEach {cookie ->
                values.removeAll {it.name==cookie.name && it.domain==cookie.domain && it.path==cookie.path}
                if(cookie.expiresAt>System.currentTimeMillis()) values+=cookie
            }
        }
        @Synchronized override fun loadForRequest(url:HttpUrl)=values.filter {it.expiresAt>System.currentTimeMillis() && it.matches(url)}
    }
    private data class Pending(val challenge:String,val redirect:HttpUrl,val state:String)
    private class Fixture:AutoCloseable {
        val server=MockWebServer()
        val failures=AtomicInteger()
        private val pending=AtomicReference<Pending>()
        private val used=AtomicBoolean()
        private val code="native-fixture-"+UUID.randomUUID().toString().replace("-","")
        private fun json(body:String,status:Int=200)=MockResponse().setHeader("Content-Type","application/json").setBody(body).setResponseCode(status)
        private val envelope="""{"access_token":"native-access-fixture","refresh_token":"native-refresh-fixture","provider":"stub","user_id":"stub-user-1","expires_at":4102444800,"token_type":"Bearer"}"""
        init {
            server.start(InetAddress.getByName("127.0.0.1"),0)
            server.dispatcher=object:MockDispatcher() {
                override fun dispatch(q:RecordedRequest):MockResponse=try {
                    val url=checkNotNull(q.requestUrl)
                    when(url.encodedPath) {
                        "/dashboard/auth/native/authorize"->{
                            check(q.method=="GET" && q.getHeader("Authorization")==null)
                            check(url.queryParameter("code_challenge_method")=="S256")
                            val redirect=checkNotNull(url.queryParameter("redirect_uri")).toHttpUrl()
                            check(redirect.host=="127.0.0.1" && redirect.scheme=="http" && redirect.encodedPath=="/callback")
                            pending.set(Pending(checkNotNull(url.queryParameter("code_challenge")),redirect,checkNotNull(url.queryParameter("state"))))
                            MockResponse().setResponseCode(302).setHeader("Location",server.url("/dashboard/auth/callback").newBuilder().host("127.0.0.1").addQueryParameter("code","stub_code").addQueryParameter("state","fixture-idp-state").build())
                        }
                        "/dashboard/auth/callback"->{
                            check(q.method=="GET" && url.queryParameter("code")=="stub_code")
                            val p=checkNotNull(pending.get())
                            MockResponse().setResponseCode(302).setHeader("Location",p.redirect.newBuilder().addQueryParameter("code",code).addQueryParameter("state",p.state).build())
                        }
                        "/dashboard/auth/native/token"->{
                            check(q.method=="POST" && q.getHeader("Authorization")==null && q.getHeader("Cookie")==null)
                            val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                            if(body["code"]?.jsonPrimitive?.content!=code || used.get()) json("""{"detail":"Invalid or expired authorization code."}""",400)
                            else {
                                used.set(true)
                                val verifier=body["code_verifier"]!!.jsonPrimitive.content
                                if(Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))!=pending.get().challenge) json("""{"detail":"Invalid or expired authorization code."}""",400) else json(envelope)
                            }
                        }
                        "/dashboard/auth/native/refresh"->{
                            check(q.method=="POST" && q.getHeader("Authorization")==null && q.getHeader("Cookie")==null)
                            val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                            if(body["refresh_token"]?.jsonPrimitive?.content=="native-refresh-fixture" && body["provider"]?.jsonPrimitive?.content=="stub") json(envelope)
                            else json("""{"error":"session_expired"}""",401)
                        }
                        "/dashboard/api/auth/me","/dashboard/api/profiles","/dashboard/api/mcp/servers"->{
                            check(q.method=="GET" && q.getHeader("Cookie")==null)
                            if(q.getHeader("Authorization")!="Bearer native-access-fixture") json("""{"error":"unauthenticated"}""",401)
                            else when(url.encodedPath) {
                                "/dashboard/api/auth/me"->json("""{"user_id":"stub-user-1","provider":"stub","display_name":"Native fixture"}""")
                                "/dashboard/api/profiles"->json("""{"profiles":[{"name":"default"},{"name":"worker"}]}""")
                                else->{check(url.queryParameterNames==setOf("profile") && url.queryParameter("profile")=="worker");json("""{"servers":[]}""")}
                            }
                        }
                        else->{failures.incrementAndGet();json("{}",404)}
                    }
                } catch(_:Exception) {failures.incrementAndGet();json("""{"error":"fixture_expectation_failed"}""",500)}
            }
        }
        override fun close()=server.close()
    }
    private fun failureCode(error:Throwable?,expected:Int) {
        assertTrue(error is HermesHttpException)
        assertEquals(expected,(error as HermesHttpException).code)
    }

    @Test fun nativeLoopbackPkceBearerProfilesAndRefreshFollowTheServerContract() {
        val arguments=InstrumentationRegistry.getArguments()
        val external=arguments.getString("signalGroveNativeAuthBase")
        val label=arguments.getString("signalGroveNativeAuthLabel") ?: "mock"
        require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val fixture=if(external==null) Fixture() else null
        val checks=mutableListOf<String>()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val savedCredentials=context.getSharedPreferences("credentials",0)
        val before=savedCredentials.all.mapValues {(_,value)->if(value is Set<*>) value.toSet() else value}
        try {
            val base=(external ?: fixture!!.server.url("/dashboard/").newBuilder().host("127.0.0.1").build().toString()).toHttpUrl()
            require(base.scheme=="http" && base.host=="127.0.0.1" && base.port in 1024..65535)
            require(base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null && base.encodedPath=="/dashboard/")
            val api=ManagementClient(base.toString(),true,true)
            val browser=OkHttpClient.Builder().cookieJar(BrowserCookies()).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(5,TimeUnit.SECONDS).build()
            fun redirect(url:HttpUrl):HttpUrl=browser.newCall(Request.Builder().url(url).build()).execute().use {
                assertEquals(302,it.code)
                checkNotNull(url.resolve(checkNotNull(it.header("Location"))))
            }
            try {
                runBlocking {
                    withTimeout(45_000) {
                        NativeLogin().use {login ->
                            val waiting=async(Dispatchers.IO) {login.awaitCode(20)}
                            try {
                                val authorize=login.authorize(api.base)
                                assertEquals("/dashboard/auth/native/authorize",authorize.encodedPath)
                                assertEquals("S256",authorize.queryParameter("code_challenge_method"))
                                assertEquals(NativeLogin.challenge(login.verifier),authorize.queryParameter("code_challenge"))
                                checks+="native_authorize_prefix_and_s256"
                                val idpCallback=redirect(authorize)
                                assertEquals(base.scheme,idpCallback.scheme);assertEquals(base.host,idpCallback.host);assertEquals(base.port,idpCallback.port)
                                assertEquals("/dashboard/auth/callback",idpCallback.encodedPath)
                                val appCallback=redirect(idpCallback)
                                val expected=login.redirect.toHttpUrl()
                                assertEquals(expected.scheme,appCallback.scheme);assertEquals(expected.host,appCallback.host);assertEquals(expected.port,appCallback.port);assertEquals(expected.encodedPath,appCallback.encodedPath)
                                checks+="bounded_browser_redirects_to_exact_native_listener"
                                val wrong=appCallback.newBuilder().setQueryParameter("state","wrong-fixture-state").build()
                                browser.newCall(Request.Builder().url(wrong).build()).execute().use {
                                    assertEquals(400,it.code)
                                    val body=it.body.string();assertFalse(body.contains(login.verifier));assertFalse(body.contains(checkNotNull(appCallback.queryParameter("code"))))
                                }
                                checks+="wrong_state_callback_refused_without_echo"
                                browser.newCall(Request.Builder().url(appCallback).build()).execute().use {assertEquals(200,it.code)}
                                val code=waiting.await();assertEquals(appCallback.queryParameter("code"),code)
                                checks+="real_android_loopback_callback"
                                val tokens=api.exchange(code,login.verifier)
                                assertEquals("stub",tokens.provider);assertEquals("stub-user-1",tokens.userId);assertTrue(tokens.expiresAt>System.currentTimeMillis()/1000)
                                assertEquals("ManagementTokens(redacted)",tokens.toString())
                                checks+="production_client_parses_native_exchange"
                                val identity=api.identity(tokens);assertEquals(tokens.userId,identity.userId);assertEquals(tokens.provider,identity.provider)
                                checks+="cookieless_bearer_identity"
                                assertEquals(setOf("default","worker"),api.profiles(tokens).map {it.name}.toSet())
                                checks+="real_profile_projection"
                                assertEquals(JsonArray(emptyList()),api.mcp(tokens,"worker")["servers"])
                                checks+="explicit_profile_mcp_read"
                                val refreshed=api.refresh(tokens);assertEquals(tokens.userId,refreshed.userId);assertEquals(tokens.provider,refreshed.provider)
                                assertEquals(identity.userId,api.identity(refreshed).userId)
                                checks+="refresh_keeps_verified_actor"
                                val replay=runCatching {api.exchange(code,login.verifier)}.exceptionOrNull()
                                failureCode(replay,400);assertFalse(replay.toString().contains(code));assertFalse(replay.toString().contains(login.verifier))
                                checks+="used_code_replay_refused"
                                failureCode(runCatching {api.identity(ManagementTokens("invalid-fixture-token",tokens.refresh,tokens.provider,tokens.userId,tokens.expiresAt))}.exceptionOrNull(),401)
                                checks+="invalid_bearer_refused"
                                failureCode(runCatching {api.refresh(ManagementTokens(tokens.access,"invalid-fixture-refresh",tokens.provider,tokens.userId,tokens.expiresAt))}.exceptionOrNull(),401)
                                checks+="invalid_refresh_requires_signin"
                            } finally {login.close();waiting.cancelAndJoin()}
                        }
                    }
                }
            } finally {browser.connectionPool.evictAll();browser.dispatcher.executorService.shutdown()}
            fixture?.let {assertEquals(0,it.failures.get())}
            assertTrue("Native client conformance changed the saved credential store.",before==savedCredentials.all)
            checks+="saved_credentials_unchanged"
            assertEquals(13,checks.size)
            val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.exists() || qa.mkdirs())
            File(qa,"native-management-contract-$label.json").writeText(buildJsonObject {
                put("state","PASS_NATIVE_MANAGEMENT_CLIENT_CONTRACT");put("backend",if(external==null) "local-mock" else "owned-real-hermes-loopback")
                put("api",Build.VERSION.SDK_INT);put("checks",JsonArray(checks.map(::JsonPrimitive)))
                put("savedCredentialsUnchanged",true);put("tlsOrExternalBrowserClaim",false);put("completedAtMillis",System.currentTimeMillis())
            }.toString())
        } finally {fixture?.close()}
    }
}
