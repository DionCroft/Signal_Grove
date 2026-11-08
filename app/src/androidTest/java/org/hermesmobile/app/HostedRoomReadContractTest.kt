package org.hermesmobile.app

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Actual Android HTTP/ticket/WebSocket read contract; fixture tokens only, no login or saved connection. */
class HostedRoomReadContractTest {
    private class Cookies:CookieJar {
        private val values=mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>) {
            cookies.forEach {cookie->values.removeAll {it.name==cookie.name && it.domain==cookie.domain && it.path==cookie.path};values+=cookie}
        }
        @Synchronized override fun loadForRequest(url:HttpUrl)=values.filter {it.matches(url) && it.expiresAt>System.currentTimeMillis()}
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
    private suspend fun actualReadContract(external:String,label:String,revision:String) {
        require(revision in setOf("stable","prior","main"))
        require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val base=external.toHttpUrl()
        require(base.scheme=="http" && base.host=="127.0.0.1" && base.port in 1024..65535 && base.encodedPath=="/dashboard/")
        require(base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0)
        val before=credentials.all.mapValues {(_,value)->if(value is Set<*>) value.toSet() else value}
        val checks=mutableListOf<String>()
        withTimeout(180_000) {
            val api=ManagementClient(base.toString(),true,true);val tokens=signIn(api)
            assertEquals("stub",tokens.provider);assertEquals("stub-user-1",tokens.userId);checks+="actual_native_pkce_and_actor"
            val profiles=api.profiles(tokens)
            fun home(profile:String)=checkNotNull(profiles.single {it.name==profile}.pluginHome).identity()
            assertNotEquals(home("default"),home("worker"));checks+="distinct_verified_profile_homes"
            fun binding(profile:String)=HostedRoomScope(ManagementTarget("c".repeat(64),base.toString(),profile,tokens.provider+" · "+tokens.userId),home(profile))
            val repo=HostedRoomsRepository();val first=repo.page(binding("default"),api,tokens,0,false)
            assertEquals(100,first.page.rooms.size);assertEquals(100,first.page.nextOffset);assertFalse(first.capabilities.driver)
            val selected=first.page.rooms.single {it.id=="fixture-shared-room"};val detail=repo.detail(first,selected,api,tokens)
            assertEquals("Installation shared fixture room",detail.room.name);assertEquals("Fixture member",detail.room.members.single().displayName)
            assertEquals(selected.authorityId,detail.room.authorityId);assertEquals(selected.authorityEpoch,detail.room.authorityEpoch);assertNull(detail.driver)
            val next=repo.page(binding("default"),api,tokens,first.page.nextOffset!!,false)
            assertEquals(listOf("fixture-page-000"),next.page.rooms.map {it.id});assertNull(next.page.nextOffset)
            assertEquals(101,(first.page.rooms+next.page.rooms).map {it.id}.toSet().size);checks+="default_bounded_pagination_and_exact_detail"
            val worker=repo.page(binding("worker"),api,tokens,0,false)
            assertEquals(binding("worker"),worker.scope);assertEquals(first.page.rooms.map {it.id},worker.page.rooms.map {it.id})
            val other=worker.page.rooms.single {it.id=="fixture-shared-room"};val workerDetail=repo.detail(worker,other,api,tokens)
            assertEquals(detail.room.name,workerDetail.room.name);assertEquals(detail.room.authorityId,workerDetail.room.authorityId)
            val workerNext=repo.page(binding("worker"),api,tokens,worker.page.nextOffset!!,false)
            assertEquals(next.page.rooms.map {it.id},workerNext.page.rooms.map {it.id});checks+="standalone_rooms_are_installation_shared_not_profile_private_copies"
            val all=repo.page(binding("worker"),api,tokens,0,true);assertEquals(100,all.page.rooms.size);assertEquals(100,all.page.nextOffset)
            val tombstone=all.page.rooms.single {it.id=="fixture-disbanded-room"};assertNotNull(tombstone.disbandedAtSeconds)
            val tombstoneDetail=repo.detail(all,tombstone,api,tokens);assertNotNull(tombstoneDetail.room.disbandedAtSeconds);assertNull(tombstoneDetail.context);assertNull(tombstoneDetail.driver)
            val tail=repo.page(binding("worker"),api,tokens,all.page.nextOffset!!,true);assertEquals(2,tail.page.rooms.size);assertNull(tail.page.nextOffset)
            assertEquals(102,(all.page.rooms+tail.page.rooms).map {it.id}.toSet().size);checks+="explicit_disbanded_filter_and_continuation"
            val missing=HostedRoomScope(ManagementTarget("c".repeat(64),base.toString(),"missing-fixture",tokens.provider+" · "+tokens.userId),home("worker"))
            assertTrue(runCatching {repo.page(missing,api,tokens,0,false)}.exceptionOrNull() is HostedRoomScopeChanged);checks+="unregistered_profile_refused_before_room_socket"
            val actor=HostedRoomScope(binding("worker").owner.copy(actor="stub · other"),home("worker"))
            assertTrue(runCatching {repo.page(actor,api,tokens,0,false)}.exceptionOrNull() is HostedRoomScopeChanged);checks+="foreign_actor_refused_before_transport"
        }
        assertEquals(before,credentials.all);checks+="saved_credentials_unchanged";assertEquals(8,checks.size)
        val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
        File(qa,"hosted-room-actual-contract-"+label+".json").writeText(buildJsonObject {
            put("state","PASS_ACTUAL_STANDALONE_ROOM_NATIVE_READS");put("api",Build.VERSION.SDK_INT);put("revision",revision)
            put("checks",JsonArray(checks.map(::JsonPrimitive)));put("expectedReadRpcCalls",27);put("expectedTicketedSockets",9)
            put("storageScope","installation-shared-standalone");put("savedCredentialsUnchanged",true)
            put("backgroundLifespanClaim",false);put("profilePrivateOrForwardedRoomClaim",false);put("workOrAdoptionClaim",false)
        }.toString())
    }

    private class Fixture:Closeable {
        val server=MockWebServer();val requests=CopyOnWriteArrayList<RecordedRequest>();val calls=CopyOnWriteArrayList<JsonObject>()
        val homes=AtomicInteger();val caps=AtomicInteger();val sockets=AtomicInteger()
        val tokens=ManagementTokens("room-management-fixture","refresh-fixture","password","fixture",4102444800)
        val home=PluginHome.project(JsonPrimitive("/fixture/profiles/worker"))!!.identity()
        var mode="normal";var roomAuthority="gateway-1";var roomEpoch=1;var detailId="room-1";var stateAdvertised=true
        val api:ManagementClient;val binding:HostedRoomScope
        private fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
        private fun room(id:String="room-1")=buildJsonObject {
            put("room_id",id);put("name","Fixture room");put("members",JsonArray(emptyList()));put("authority_gateway_id",roomAuthority)
            put("authority_epoch",roomEpoch);put("revision",1);put("created_at",1.25);put("updated_at",2.75)
        }
        private fun capability()=buildJsonObject {
            val n=caps.incrementAndGet();put("protocol_version",2);put("driver",false);put("persistent_process",false)
            put("authority_gateway_id",if(mode=="authority" && n%2==0 || mode=="changed-install") "gateway-2" else "gateway-1")
            put("features",JsonArray(emptyList()));put("methods",JsonArray((listOf("groups.capabilities")+(if(mode=="no-list") emptyList() else listOf("groups.list"))+(if(stateAdvertised) listOf("groups.state") else emptyList())).map(::JsonPrimitive)))
            put("room_link",buildJsonObject {put("grant","fixture-private-grant");put("endpoint","https://private.invalid/")})
        }
        private fun listener()=object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {
                sockets.incrementAndGet();ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{}}}}""")
            }
            override fun onMessage(ws:WebSocket,text:String) {
                val j=Json.parseToJsonElement(text).jsonObject;calls.add(j);val method=j["method"]!!.jsonPrimitive.content;val params=j["params"]!!.jsonObject
                check(params["profile"]==JsonPrimitive("worker"))
                if(mode=="unsupported") {ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("error",buildJsonObject {put("code",-32601);put("message","fixture private error")})}.toString());return}
                if(mode=="drop" && method=="groups.list") {ws.close(1000,"fixture-close");return}
                val result=when(method) {
                    "groups.capabilities"->capability()
                    "groups.list"->buildJsonObject {put("rooms",JsonArray(listOf(room())));put("next_offset",if(params["offset"]==JsonPrimitive(0)) JsonPrimitive(731) else JsonNull)}
                    "groups.state"->buildJsonObject {put("room",room(detailId));put("context",buildJsonObject {put("purpose","Fixture purpose");put("notes","Fixture notes");put("routing","everyone");put("lead_member_id","")})}
                    else->error("Unreviewed fixture method.")
                }
                ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
            }
        }
        init {
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    requests.add(request)
                    return when(request.path) {
                        "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")}).setHeader("Set-Cookie","ignored=fixture-private; Path=/")
                        "/prefix/api/profiles"->{val n=homes.incrementAndGet();json(buildJsonObject {put("profiles",buildJsonArray {
                            if(mode!="missing") {add(buildJsonObject {put("name","worker");put("path",if(mode=="before" || mode=="after" && n>1) "/fixture/remapped" else "/fixture/profiles/worker")});if(mode=="duplicate") add(buildJsonObject {put("name","worker");put("path","/fixture/other")})}
                        })})}
                        "/prefix/api/auth/ws-ticket"->json(buildJsonObject {put("ticket","r".repeat(43));put("ttl_seconds",30)})
                        "/prefix/api/ws"->MockResponse().withWebSocketUpgrade(listener()).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                        else->MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start();val url=server.url("/prefix/").newBuilder().host("127.0.0.1").build().toString();api=ManagementClient(url,true,true)
            binding=HostedRoomScope(ManagementTarget("a".repeat(64),url,"worker","password · fixture"),home)
        }
        override fun close()=server.close()
    }

    @Test fun nativeRoomReadsVerifyHomeAndExactProfileWithoutAdoptingOrSubmittingWork()=runBlocking {
        val arguments=InstrumentationRegistry.getArguments();val external=arguments.getString("signalGroveRoomBase")
        if(external!=null) {
            actualReadContract(external,checkNotNull(arguments.getString("signalGroveRoomLabel")),checkNotNull(arguments.getString("signalGroveRoomRevision")))
            return@runBlocking
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0);val before=credentials.all.toMap()
        Fixture().use {f->
            val repo=HostedRoomsRepository();val listing=repo.page(f.binding,f.api,f.tokens,0,false)
            assertEquals(731,listing.page.nextOffset);assertEquals(f.binding,listing.scope);assertFalse(listing.capabilities.driver)
            val selected=listing.page.rooms.single();val detail=repo.detail(listing,selected,f.api,f.tokens)
            assertEquals(f.binding,detail.scope);assertEquals("Fixture purpose",detail.context!!.purpose)
            assertEquals(listOf("groups.capabilities","groups.list","groups.capabilities","groups.capabilities","groups.state","groups.capabilities"),f.calls.map {it["method"]!!.jsonPrimitive.content})
            assertEquals(Json.parseToJsonElement("""{"profile":"worker","limit":100,"offset":0,"include_disbanded":false}"""),f.calls[1]["params"])
            assertEquals(Json.parseToJsonElement("""{"profile":"worker","room_id":"room-1","include_disbanded":false}"""),f.calls[4]["params"])
            assertEquals(2,f.sockets.get());assertEquals(4,f.homes.get())
            for(request in f.requests) {
                assertNull(request.getHeader("Cookie"))
                if(request.path=="/prefix/api/ws") {assertNull(request.getHeader("Authorization"));assertEquals("hermes-gateway-v1, hermes-gateway-ticket."+"r".repeat(43),request.getHeader("Sec-WebSocket-Protocol"))}
                else assertEquals("Bearer room-management-fixture",request.getHeader("Authorization"))
            }
            assertEquals(before,credentials.all)
            val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
            File(qa,"hosted-room-read-contract.json").writeText(buildJsonObject {
                put("state","PASS_ANDROID_ROOM_READ_ONLY_CONTRACT");put("api",Build.VERSION.SDK_INT);put("readRpcCalls",6);put("ticketedSockets",2);put("homeChecks",4)
                put("onlyReadMethods",true);put("savedCredentialsUnchanged",true);put("workOrAdoptionClaim",false);put("actualHermesSourceClaim",false)
            }.toString())
        }
    }
}
