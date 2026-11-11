package org.hermesmobile.app

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher as MockDispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

/** Identical wire/refusal cases on JVM and Android; no session or mutation is admitted. */
abstract class CanonicalProfileTicketCases {
    private class Fixture(val mode:String="normal",val launch:String="default",val selected:String="field"):Closeable {
        val server=MockWebServer()
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        val calls=CopyOnWriteArrayList<JsonObject>()
        val sockets=AtomicInteger();val identities=AtomicInteger();val profiles=AtomicInteger()
        val prefix="/dashboard/nested/"
        val generic="generic-ticket-"+"a".repeat(32)
        val scoped="scoped-ticket-"+"b".repeat(32)
        val tokens=ManagementTokens("fixture-profile-access","fixture-profile-refresh","fixture-idp","owner",9_000_000_000_000)
        fun path(name:String)="/fixture/homes/"+name
        fun json(text:String,code:Int=200)=MockResponse().setResponseCode(code).setHeader("Content-Type","application/json").setBody(text)
        init {
            server.dispatcher=object:MockDispatcher() {
                override fun dispatch(q:RecordedRequest):MockResponse {
                    requests+=q
                    val url=checkNotNull(q.requestUrl)
                    if(url.encodedPath==prefix+"api/ws") {
                        check(q.method=="GET" && url.query==null)
                        for(name in listOf("Authorization","Cookie","Origin")) check(q.getHeader(name)==null)
                        val protocols=checkNotNull(q.getHeader("Sec-WebSocket-Protocol"))
                        check(protocols in listOf("hermes-gateway-v1, hermes-gateway-ticket.$generic","hermes-gateway-v1, hermes-gateway-ticket.$scoped"))
                        val named=protocols.endsWith(scoped);sockets.incrementAndGet()
                        val owner=if(named && mode!="wrong-scoped-home") selected else launch
                        return MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                            override fun onOpen(ws:WebSocket,response:Response) {
                                ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"replay_epoch":"fixture-profile-replay","heartbeat":true,"session_authority":true}}}""")
                            }
                            override fun onMessage(ws:WebSocket,text:String) {
                                val call=Json.parseToJsonElement(text).jsonObject;calls+=call
                                check(call["method"]==JsonPrimitive("runtime.describe") && call["params"]==JsonObject(emptyMap()))
                                if(mode=="legacy-disconnect" && !named) {ws.close(1000,"fixture");return}
                                val home=if(mode=="invalid-home" && !named) "not-absolute" else path(owner)
                                val epoch:JsonElement=if(mode=="bad-epoch" && !named) JsonPrimitive("7") else JsonPrimitive(7)
                                val features=if(mode=="missing-feature" && !named) listOf("durable-admission-v1") else listOf("durable-admission-v1","event-replay-v1")
                                val result=buildJsonObject {
                                    put("profile_id",home);put("instance_id","owner-"+owner);put("authority_epoch",epoch)
                                    put("capabilities",JsonArray(features.map(::JsonPrimitive)))
                                    put("session_create",buildJsonObject {put("sources",JsonArray(listOf(JsonPrimitive("cli"))));put("parameters",JsonArray(listOf(JsonPrimitive("title"))))})
                                }
                                ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",checkNotNull(call["id"]));put("result",result)}.toString())
                            }
                            override fun onClosing(ws:WebSocket,code:Int,reason:String) {ws.close(code,reason)}
                        }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                    }
                    check(q.getHeader("Authorization")=="Bearer fixture-profile-access" && q.getHeader("Cookie")==null)
                    return when(url.encodedPath) {
                        prefix+"api/auth/me" -> {
                            check(q.method=="GET" && url.query==null)
                            val user=if(identities.incrementAndGet()>1 && mode=="changed-actor") "foreign" else "owner"
                            json("""{"provider":"fixture-idp","user_id":"$user","display_name":"Fixture"}""")
                        }
                        prefix+"api/profiles" -> {
                            check(q.method=="GET" && url.query==null)
                            val n=profiles.incrementAndGet()
                            val changed=(mode=="changed-home-before" && n>=2)||(mode=="changed-home-after" && n>=3)
                            val field=if(changed) "/fixture/foreign" else path("field")
                            json("""{"profiles":[{"name":"default","path":"${path("default")}"},{"name":"field","path":"$field"}]}""")
                        }
                        prefix+"api/auth/ws-ticket" -> {
                            check(q.method=="POST" && url.query==null && q.body.readUtf8()=="{}")
                            if(mode=="legacy-mint-error") json("{}",503) else json("""{"ticket":"$generic","ttl_seconds":30}""")
                        }
                        prefix+"api/webapp/socket-ticket" -> {
                            check(q.method=="POST" && q.body.readUtf8()=="{}")
                            check(url.queryParameterNames==setOf("socket","profile") && url.queryParameter("socket")=="gateway" && url.queryParameter("profile")==selected)
                            when {
                                mode.startsWith("scoped-status-") -> json("{}",mode.substringAfterLast('-').toInt())
                                mode=="invalid-scoped-ticket" -> json("""{"ticket":"bad","ttl_seconds":30}""")
                                mode=="invalid-scoped-ttl" -> json("""{"ticket":"$scoped","ttl_seconds":"30"}""")
                                else -> json("""{"ticket":"$scoped","ttl_seconds":30}""")
                            }
                        }
                        else -> error("Unexpected fixture route")
                    }
                }
            }
        }
        val base get()=server.url(prefix)
        fun home()=checkNotNull(PluginHome.project(JsonPrimitive(path(selected)))).identity()
        fun target()=ManagementTarget("b".repeat(64),base.toString(),selected,"fixture-idp · owner")
        fun mints()=requests.count {it.requestUrl?.encodedPath==prefix+"api/webapp/socket-ticket"}
        override fun close()=server.close()
    }
    private fun attempt(mode:String="normal",launch:String="default",selected:String="field",check:(Fixture,Result<CanonicalGatewayChannel>)->Unit)=runBlocking {
        Fixture(mode,launch,selected).use {f->
            val result=runCatching {withTimeout(4000) {ManagementClient(f.base.toString(),true,true).canonicalGateway(f.tokens,f.target(),f.home())}}
            try {check(f,result)} finally {result.getOrNull()?.close()}
            assertTrue(f.calls.all {it["method"]==JsonPrimitive("runtime.describe") && it["params"]==JsonObject(emptyMap())})
            assertTrue(f.calls.size<=2);assertTrue(f.sockets.get()<=2);assertTrue(f.mints()<=1)
        }
    }
    @Test fun defaultLaunchUsesOnlyLegacyTicket()=attempt(selected="default") {f,r->assertEquals("owner-default",r.getOrThrow().binding.instanceId);assertEquals(1,f.sockets.get());assertEquals(0,f.mints())}
    @Test fun matchingNamedLaunchStillWorksWithoutWebapp()=attempt(launch="field") {f,r->assertEquals("owner-field",r.getOrThrow().binding.instanceId);assertEquals(1,f.sockets.get());assertEquals(0,f.mints())}
    @Test fun selectedSiblingUsesOneScopedHandshakeWithExactPrefixAndIdentity()=attempt {f,r->
        val binding=r.getOrThrow().binding;assertEquals(f.target(),binding.target);assertEquals(f.home(),binding.homeId);assertEquals(f.path("field"),binding.profileId);assertEquals("owner-field",binding.instanceId)
        assertEquals(2,f.sockets.get());assertEquals(2,f.identities.get());assertEquals(3,f.profiles.get());assertEquals(1,f.mints())
        assertEquals(listOf("api/auth/me","api/profiles","api/auth/ws-ticket","api/ws","api/auth/me","api/profiles","api/webapp/socket-ticket","api/ws","api/profiles"),f.requests.map {checkNotNull(it.requestUrl).encodedPath.removePrefix(f.prefix)})
    }
    @Test fun unavailableScopedSurfaceDoesNotBorrowDefault()=attempt("scoped-status-404") {f,r->assertTrue(r.exceptionOrNull() is CanonicalProfileTransportUnavailable);assertEquals(1,f.sockets.get());assertEquals(1,f.mints())}
    @Test fun expiredScopedAuthenticationRemainsAnHttp401()=attempt("scoped-status-401") {f,r->assertEquals(401,(r.exceptionOrNull() as? HermesHttpException)?.code);assertEquals(1,f.sockets.get());assertEquals(1,f.mints())}
    @Test fun forbiddenScopedTicketIsNotRetried()=attempt("scoped-status-403") {f,r->assertEquals(403,(r.exceptionOrNull() as? HermesHttpException)?.code);assertEquals(1,f.mints())}
    @Test fun failedScopedTicketIsNotRetried()=attempt("scoped-status-503") {f,r->assertEquals(503,(r.exceptionOrNull() as? HermesHttpException)?.code);assertEquals(1,f.mints())}
    @Test fun failedLegacyMintCannotStartScopedNegotiation()=attempt("legacy-mint-error") {f,r->assertEquals(503,(r.exceptionOrNull() as? HermesHttpException)?.code);assertEquals(0,f.mints());assertEquals(0,f.sockets.get())}
    @Test fun invalidEpochOnForeignHomeCannotRequestAnotherTicket()=attempt("bad-epoch") {f,r->assertTrue(r.exceptionOrNull() is GatewayProtocolException);assertEquals(0,f.mints())}
    @Test fun missingCapabilityOnForeignHomeCannotRequestAnotherTicket()=attempt("missing-feature") {f,r->assertTrue(r.exceptionOrNull() is CanonicalNotSupported);assertEquals(0,f.mints())}
    @Test fun invalidHomeCannotRequestAnotherTicket()=attempt("invalid-home") {f,r->assertTrue(r.exceptionOrNull() is CanonicalScopeChanged);assertEquals(0,f.mints())}
    @Test fun disconnectCannotRequestAnotherTicket()=attempt("legacy-disconnect") {f,r->assertTrue(r.exceptionOrNull() is GatewayUnavailable);assertEquals(0,f.mints())}
    @Test fun changedActorRefusesBeforeScopedMint()=attempt("changed-actor") {f,r->assertTrue(r.exceptionOrNull() is IllegalArgumentException);assertEquals(0,f.mints())}
    @Test fun changedHomeRefusesBeforeScopedMint()=attempt("changed-home-before") {f,r->assertTrue(r.exceptionOrNull() is CanonicalScopeChanged);assertEquals(0,f.mints())}
    @Test fun homeMustStillMatchAfterScopedHandshake()=attempt("changed-home-after") {f,r->assertTrue(r.exceptionOrNull() is CanonicalScopeChanged);assertEquals(2,f.sockets.get());assertEquals(1,f.mints())}
    @Test fun mismatchingScopedHomeCannotStartAThirdHandshake()=attempt("wrong-scoped-home") {f,r->assertTrue(r.exceptionOrNull() is CanonicalScopeChanged);assertEquals(2,f.sockets.get());assertEquals(1,f.mints())}
    @Test fun malformedScopedTicketCannotUpgrade()=attempt("invalid-scoped-ticket") {f,r->assertTrue(r.exceptionOrNull() is IllegalArgumentException);assertEquals(1,f.sockets.get())}
    @Test fun malformedScopedLifetimeCannotUpgrade()=attempt("invalid-scoped-ttl") {f,r->assertTrue(r.exceptionOrNull() is IllegalArgumentException);assertEquals(1,f.sockets.get())}
}