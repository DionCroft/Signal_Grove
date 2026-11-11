package org.hermesmobile.app

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher as MockDispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Separate canonical negotiation over its real HTTP/ticket/WebSocket path. No saved credentials. */
class CanonicalGatewayContractTest {
    private class Cookies:CookieJar {
        private val values=mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>) {
            cookies.forEach {cookie->values.removeAll {it.name==cookie.name && it.domain==cookie.domain && it.path==cookie.path};values+=cookie}
        }
        @Synchronized override fun loadForRequest(url:HttpUrl)=values.filter {it.matches(url) && it.expiresAt>System.currentTimeMillis()}
    }
    private data class Pending(val challenge:String,val redirect:HttpUrl,val state:String)
    private class Fixture:AutoCloseable {
        val server=MockWebServer()
        val failures=AtomicInteger()
        private val pending=AtomicReference<Pending>()
        private val tickets=ConcurrentHashMap.newKeySet<String>()
        private val code="project-code-"+UUID.randomUUID().toString().replace("-","")
        private val envelope="""{"access_token":"project-access-fixture","refresh_token":"project-refresh-fixture","provider":"stub","user_id":"stub-user-1","expires_at":4102444800,"token_type":"Bearer"}"""
        private fun json(body:String,status:Int=200)=MockResponse().setHeader("Content-Type","application/json").setBody(body).setResponseCode(status)
        private fun listener()=object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {
                ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"replay_epoch":"fixture-replay","session_authority":true}}}""")
            }
            override fun onMessage(ws:WebSocket,text:String) {
                try {
                    val frame=Json.parseToJsonElement(text).jsonObject
                    check(frame==Json.parseToJsonElement("""{"jsonrpc":"2.0","id":"1","method":"runtime.describe","params":{}}"""))
                    ws.send("""{"jsonrpc":"2.0","id":"1","result":{"instance_id":"fixture-native-default","profile_id":"/fixture/homes/default","authority_epoch":1,"capabilities":["durable-admission-v1","event-replay-v1"],"session_create":{"sources":["cli"],"parameters":["title"]}}}""")
                } catch(_:Exception) {failures.incrementAndGet();ws.close(1011,"fixture-error")}
            }
        }
        init {
            server.start(InetAddress.getByName("127.0.0.1"),0)
            server.dispatcher=object:MockDispatcher() {
                override fun dispatch(q:RecordedRequest):MockResponse=try {
                    val url=checkNotNull(q.requestUrl)
                    when(url.encodedPath) {
                        "/dashboard/auth/native/authorize"->{
                            check(q.method=="GET" && q.getHeader("Authorization")==null && url.queryParameter("code_challenge_method")=="S256")
                            val redirect=checkNotNull(url.queryParameter("redirect_uri")).toHttpUrl()
                            check(redirect.scheme=="http" && redirect.host=="127.0.0.1" && redirect.encodedPath=="/callback")
                            pending.set(Pending(checkNotNull(url.queryParameter("code_challenge")),redirect,checkNotNull(url.queryParameter("state"))))
                            MockResponse().setResponseCode(302).setHeader("Location",server.url("/dashboard/auth/callback").newBuilder().host("127.0.0.1").addQueryParameter("code","stub_code").build())
                        }
                        "/dashboard/auth/callback"->{
                            check(q.method=="GET" && url.queryParameter("code")=="stub_code")
                            val p=checkNotNull(pending.get())
                            MockResponse().setResponseCode(302).setHeader("Location",p.redirect.newBuilder().addQueryParameter("code",code).addQueryParameter("state",p.state).build())
                        }
                        "/dashboard/auth/native/token"->{
                            check(q.method=="POST" && q.getHeader("Authorization")==null && q.getHeader("Cookie")==null)
                            val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                            check(body["code"]==JsonPrimitive(code))
                            val challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body["code_verifier"]!!.jsonPrimitive.content.toByteArray(Charsets.US_ASCII)))
                            check(challenge==pending.get().challenge);json(envelope)
                        }
                        "/dashboard/api/auth/me","/dashboard/api/profiles","/dashboard/api/auth/ws-ticket"->{
                            check(q.getHeader("Authorization")=="Bearer project-access-fixture" && q.getHeader("Cookie")==null)
                            when(url.encodedPath) {
                                "/dashboard/api/auth/me"->{check(q.method=="GET");json("""{"user_id":"stub-user-1","provider":"stub","display_name":"Project fixture"}""")}
                                "/dashboard/api/profiles"->{check(q.method=="GET");json("""{"profiles":[{"name":"default","path":"/fixture/homes/default"},{"name":"worker","path":"/fixture/homes/worker"}]}""")}
                                else->{
                                    check(q.method=="POST" && Json.parseToJsonElement(q.body.readUtf8())==JsonObject(emptyMap()))
                                    val ticket="project-ticket-"+UUID.randomUUID().toString().replace("-","");tickets+=ticket
                                    json(buildJsonObject {put("ticket",ticket);put("ttl_seconds",30)}.toString())
                                }
                            }
                        }
                        "/dashboard/api/webapp/socket-ticket"->{
                            check(q.method=="POST" && q.getHeader("Authorization")=="Bearer project-access-fixture" && q.getHeader("Cookie")==null)
                            check(url.queryParameterNames==setOf("socket","profile") && url.queryParameter("socket")=="gateway" && url.queryParameter("profile")=="worker")
                            check(q.body.readUtf8()=="{}")
                            json("{}",404) // This fixture models the gateway-only surface, not Webapp.
                        }
                        "/dashboard/api/ws"->{
                            check(q.method=="GET" && q.getHeader("Authorization")==null && q.getHeader("Cookie")==null && q.getHeader("Origin")==null)
                            val protocols=checkNotNull(q.getHeader("Sec-WebSocket-Protocol")).split(',').map(String::trim)
                            check(protocols.size==2 && protocols[0]=="hermes-gateway-v1" && protocols[1].startsWith("hermes-gateway-ticket."))
                            check(tickets.remove(protocols[1].removePrefix("hermes-gateway-ticket.")))
                            MockResponse().withWebSocketUpgrade(listener()).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                        }
                        else->{failures.incrementAndGet();json("{}",404)}
                    }
                } catch(_:Exception) {failures.incrementAndGet();json("""{"error":"fixture_expectation_failed"}""",500)}
            }
        }
        override fun close()=server.close()
    }

    private suspend fun signIn(api:ManagementClient):ManagementTokens {
        val browser=OkHttpClient.Builder().cookieJar(Cookies()).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(10,TimeUnit.SECONDS).build()
        fun redirect(url:HttpUrl)=browser.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(302,it.code);checkNotNull(url.resolve(checkNotNull(it.header("Location"))))
        }
        try {
            return coroutineScope {
                NativeLogin().use {login->
                    val waiting=async(Dispatchers.IO) {login.awaitCode(20)}
                    try {
                        val callback=redirect(login.authorize(api.base))
                        assertEquals(api.base.scheme,callback.scheme);assertEquals(api.base.host,callback.host);assertEquals(api.base.port,callback.port)
                        assertEquals("/dashboard/auth/callback",callback.encodedPath)
                        val destination=redirect(callback);val expected=login.redirect.toHttpUrl()
                        assertEquals(expected.scheme,destination.scheme);assertEquals(expected.host,destination.host);assertEquals(expected.port,destination.port);assertEquals(expected.encodedPath,destination.encodedPath)
                        browser.newCall(Request.Builder().url(destination).build()).execute().use {assertEquals(200,it.code)}
                        api.exchange(waiting.await(),login.verifier)
                    } finally {login.close();waiting.cancelAndJoin()}
                }
            }
        } finally {browser.connectionPool.evictAll();browser.dispatcher.executorService.shutdown()}
    }
    @Test fun canonicalNegotiationBindsAuthenticatedPrimaryAndRefusesSiblingIdentity() {
        val args=InstrumentationRegistry.getArguments();val external=args.getString("signalGroveCanonicalBase")
        val label=args.getString("signalGroveCanonicalLabel") ?: "mock";require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val fixture=if(external==null) Fixture() else null
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0)
        val before=credentials.all.mapValues {(_,value)->if(value is Set<*>) value.toSet() else value}
        val checks=mutableListOf<String>()
        try {
            val base=(external ?: fixture!!.server.url("/dashboard/").newBuilder().host("127.0.0.1").build().toString()).toHttpUrl()
            require(base.scheme=="http" && base.host=="127.0.0.1" && base.port in 1024..65535 && base.encodedPath=="/dashboard/")
            require(base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null)
            val api=ManagementClient(base.toString(),true,true)
            runBlocking {
                withTimeout(180_000) {
                    val tokens=signIn(api);assertEquals("stub",tokens.provider);assertEquals("stub-user-1",tokens.userId)
                    checks+="real_native_signin_for_canonical_connection"
                    val profiles=api.profiles(tokens)
                    fun home(profile:String)=checkNotNull(profiles.single {it.name==profile}.pluginHome).identity()
                    assertNotEquals(home("default"),home("worker"));checks+="authenticated_profile_homes_are_distinct"
                    fun target(profile:String)=ManagementTarget("b".repeat(64),base.toString(),profile,tokens.provider+" · "+tokens.userId)
                    val expected=target("default")
                    val channel=api.canonicalGateway(tokens,expected,home("default"))
                    val binding=channel.binding
                    assertEquals(expected,binding.target);assertEquals(home("default"),binding.homeId)
                    assertEquals(home("default"),checkNotNull(PluginHome.project(JsonPrimitive(binding.profileId))).identity())
                    checks+="runtime_identity_matches_selected_authenticated_profile_home"
                    assertEquals("fixture-native-default",binding.instanceId);assertTrue(binding.authorityEpoch>=1)
                    checks+="runtime_instance_and_integral_epoch_are_owner_reported"
                    assertEquals("CanonicalRuntimeBinding(redacted)",binding.toString());assertEquals("CanonicalGatewayChannel(redacted)",channel.toString())
                    channel.close()
                    assertTrue(runCatching {channel.binding}.exceptionOrNull() is GatewayUnavailable)
                    checks+="close_removes_access_to_runtime_identity"
                    assertTrue(runCatching {api.canonicalGateway(tokens,target("worker"),home("worker"))}.exceptionOrNull() is CanonicalProfileTransportUnavailable)
                    checks+="secondary_home_requires_supported_profile_ticket"
                    assertTrue(runCatching {api.canonicalGateway(tokens,expected,"0".repeat(64))}.exceptionOrNull() is CanonicalScopeChanged)
                    checks+="changed_home_is_refused_before_ticket_or_socket"
                    assertTrue(runCatching {api.canonicalGateway(tokens,target("missing-fixture"),home("default"))}.exceptionOrNull() is CanonicalScopeChanged)
                    checks+="unregistered_profile_is_refused_before_ticket_or_socket"
                    assertTrue(runCatching {api.canonicalGateway(tokens,expected.copy(actor="foreign-actor"),home("default"))}.exceptionOrNull() is IllegalArgumentException)
                    checks+="foreign_actor_is_refused_before_transport"
                }
            }
            fixture?.let {assertEquals(0,it.failures.get())}
            assertTrue("Canonical negotiation changed saved credentials.",before==credentials.all)
            checks+="saved_credentials_unchanged";assertEquals(10,checks.size)
            val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
            File(qa,"canonical-gateway-contract-$label.json").writeText(buildJsonObject {
                put("state","PASS_CANONICAL_GATEWAY_NEGOTIATION");put("backend",if(external==null) "local-mock" else "owned-real-hermes-loopback")
                put("api",Build.VERSION.SDK_INT);put("checks",JsonArray(checks.map(::JsonPrimitive)))
                put("savedCredentialsUnchanged",true);put("sessionAdoptionOrExecutionClaim",false);put("completedAtMillis",System.currentTimeMillis())
            }.toString())
        } finally {fixture?.close()}
    }
}
