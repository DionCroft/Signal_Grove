package org.hermesmobile.app

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Actual Android HTTP/ticket/WebSocket read contract; fixture tokens only, no login or saved connection. */
class HostedRoomReadContractTest {
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
