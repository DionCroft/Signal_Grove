package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class HostProjectsRepositoryTest {
    private class Fixture(val mode:String="normal"):Closeable {
        val server=MockWebServer()
        val tokens=ManagementTokens("project-management-fixture","refresh-fixture","password","fixture",4102444800)
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        val calls=CopyOnWriteArrayList<JsonObject>()
        val homes=AtomicInteger()
        val ticket="p".repeat(43)
        val home=PluginHome.project(JsonPrimitive("/srv/profiles/worker"))!!.identity()
        val api:ManagementClient
        val target:ManagementTarget
        fun project(id:String="p_00000001",slug:String="garden")=buildJsonObject {
            put("id",id);put("slug",slug);put("name","Private fixture project");put("created_at",1793820000L);put("archived",true)
            put("folders",buildJsonArray {add(buildJsonObject {put("path","/srv/private/garden");put("is_primary",true)})})
        }
        private fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
        init {
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    requests.add(request)
                    return when(request.path) {
                        "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture")}).setHeader("Set-Cookie","ignored=private-cookie; Path=/")
                        "/prefix/api/profiles"->{val n=homes.incrementAndGet();json(buildJsonObject {put("profiles",buildJsonArray {
                            if(mode!="missing") {
                                add(buildJsonObject {put("name","worker");put("path",if(mode=="before" || mode=="after" && n>1) "/srv/profiles/remapped" else "/srv/profiles/worker")})
                                if(mode=="duplicate") add(buildJsonObject {put("name","worker");put("path","/srv/profiles/other")})
                            }
                        })})}
                        "/prefix/api/auth/ws-ticket"->json(buildJsonObject {put("ticket",ticket);put("ttl_seconds",30)})
                        "/prefix/api/ws"->MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                            override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready"}}""")}
                            override fun onMessage(webSocket:WebSocket,text:String) {
                                val j=Json.parseToJsonElement(text).jsonObject;calls.add(j)
                                if(mode=="closed") {webSocket.close(1011,"private-close-fixture");return}
                                val result=when(j["method"]?.jsonPrimitive?.content) {
                                    "projects.list"->buildJsonObject {put("projects",buildJsonArray {add(project())});put("active_id","p_deleted")}
                                    "projects.get"->buildJsonObject {put("project",if(mode=="wrong-id") project("p_other","p_00000001") else project())}
                                    else->error("Unexpected RPC; no discovery or mutation is allowed")
                                }
                                webSocket.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                            }
                        }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                        else->MockResponse().setResponseCode(404)
                    }
                }
            }
            api=ManagementClient(server.url("prefix/").toString(),true,true)
            target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
        }
        override fun close() {server.close()}
    }
    @Test fun storedReadUsesExactProfilePrefixTicketAndNoBrowserCredentialsOrDiscovery()=runBlocking {
        Fixture().use {f->
            val s=withTimeout(5000) {HostProjectsRepository().read(f.target,f.home,f.api,f.tokens)}
            assertEquals(f.target,s.owner);assertEquals(f.home,s.homeId);assertEquals("p_deleted",s.activeId);assertNull(s.activeProject);assertTrue(s.projects.single().archived)
            assertEquals(5,f.requests.size);assertEquals(2,f.homes.get());assertEquals(1,f.calls.size)
            assertEquals(JsonPrimitive("projects.list"),f.calls.single()["method"]);assertEquals(buildJsonObject {put("profile","worker")},f.calls.single()["params"])
            assertEquals(listOf("/prefix/api/auth/me","/prefix/api/profiles","/prefix/api/auth/ws-ticket","/prefix/api/ws","/prefix/api/profiles"),f.requests.map {it.path})
            for(r in f.requests) {assertNull(r.getHeader("Cookie"));assertNull(r.getHeader("Sec-Fetch-Site"));assertNull(r.getHeader("Origin"))}
            val mint=f.requests[2];assertEquals("POST",mint.method);assertEquals("Bearer project-management-fixture",mint.getHeader("Authorization"));assertEquals("{}",mint.body.readUtf8())
            val socket=f.requests[3];assertNull(socket.getHeader("Authorization"));assertEquals("hermes-gateway-v1, hermes-gateway-ticket.${f.ticket}",socket.getHeader("Sec-WebSocket-Protocol"));assertFalse(socket.requestUrl.toString().contains(f.ticket))
        }
    }
    @Test fun freshDetailUsesCanonicalIdAndRepeatsTheHomeFence()=runBlocking {
        Fixture().use {f->
            val d=withTimeout(5000) {HostProjectsRepository().detail(f.target,f.home,"p_00000001",f.api,f.tokens)}
            assertEquals("p_00000001",d.project.id);assertEquals(2,f.homes.get());assertEquals(5,f.requests.size)
            assertEquals(JsonPrimitive("projects.get"),f.calls.single()["method"]);assertEquals(buildJsonObject {put("profile","worker");put("id","p_00000001")},f.calls.single()["params"])
        }
    }
    @Test fun foreignActorUrlAndUnsupportedProfileAreRejectedBeforeAnyNetwork()=runBlocking {
        Fixture().use {f->
            for(target in listOf(f.target.copy(actor="password · other"),f.target.copy(url=f.server.url("other/").toString()),f.target.copy(profile="all"),f.target.copy(profile=".hermes"))) {
                assertTrue(runCatching {HostProjectsRepository().read(target,f.home,f.api,f.tokens)}.isFailure)
            }
            assertEquals(0,f.requests.size);assertTrue(f.calls.isEmpty())
        }
    }
    @Test fun missingDuplicateOrRemappedProfileCannotMintAGatewayTicket()=runBlocking {
        for(mode in listOf("missing","duplicate","before")) Fixture(mode).use {f->
            val failure=withTimeout(5000) {runCatching {HostProjectsRepository().read(f.target,f.home,f.api,f.tokens)}.exceptionOrNull()}
            assertTrue(mode,failure is HostProjectsScopeChanged);assertEquals(2,f.requests.size);assertTrue(f.calls.isEmpty())
        }
    }
    @Test fun remappingAfterAReadRefusesToPublishThePrivateResult()=runBlocking {
        Fixture("after").use {f->
            val failure=withTimeout(5000) {runCatching {HostProjectsRepository().read(f.target,f.home,f.api,f.tokens)}.exceptionOrNull()}
            assertTrue(failure is HostProjectsScopeChanged);assertEquals(5,f.requests.size);assertEquals(1,f.calls.size)
            assertFalse(failure.toString().contains("/srv/"));assertFalse(failure.toString().contains("Private fixture"))
        }
    }
    @Test fun slugFallbackReplyCannotMasqueradeAsTheIntendedCanonicalProject()=runBlocking {
        Fixture("wrong-id").use {f->
            val failure=withTimeout(5000) {runCatching {HostProjectsRepository().detail(f.target,f.home,"p_00000001",f.api,f.tokens)}.exceptionOrNull()}
            assertTrue(failure is HostProjectsUnsupported);assertEquals(4,f.requests.size);assertEquals(1,f.calls.size)
        }
    }
    @Test fun interruptedGatewayReadDoesNotReconnectOrReplay()=runBlocking {
        Fixture("closed").use {f->
            val failure=withTimeout(5000) {runCatching {HostProjectsRepository().read(f.target,f.home,f.api,f.tokens)}.exceptionOrNull()}
            assertTrue(failure is GatewayUnavailable);assertEquals(4,f.requests.size);assertEquals(1,f.calls.size)
            assertFalse(failure.toString().contains("private-close-fixture"))
        }
    }
}
