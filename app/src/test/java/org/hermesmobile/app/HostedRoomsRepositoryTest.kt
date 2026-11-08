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

class HostedRoomsRepositoryTest {
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
    private suspend fun page(f:Fixture,offset:Int=0,include:Boolean=false)=HostedRoomsRepository().page(f.binding,f.api,f.tokens,offset,include)
    private suspend fun refused(block:suspend ()->Unit) {try {block();fail("Expected bounded room read refusal.")} catch(_:Exception) {}}

    @Test fun pageUsesOnlyExactReadMethodsAndRetainsOpaqueOffsetAndPrefix()=runBlocking {Fixture().use {f->
        val result=page(f);assertEquals(731,result.page.nextOffset);assertEquals(f.binding,result.scope);assertFalse(result.capabilities.driver)
        assertEquals(listOf("groups.capabilities","groups.list","groups.capabilities"),f.calls.map {it["method"]!!.jsonPrimitive.content})
        assertEquals(Json.parseToJsonElement("""{"profile":"worker","limit":100,"offset":0,"include_disbanded":false}"""),f.calls[1]["params"])
        assertEquals(1,f.sockets.get());assertEquals(2,f.homes.get())
        for(request in f.requests) {assertNull(request.getHeader("Cookie"));if(request.path=="/prefix/api/ws") {assertNull(request.getHeader("Authorization"));assertEquals("hermes-gateway-v1, hermes-gateway-ticket."+"r".repeat(43),request.getHeader("Sec-WebSocket-Protocol"))} else assertEquals("Bearer room-management-fixture",request.getHeader("Authorization"))}
    }}
    @Test fun explicitContinuationAndDisbandedFilterKeepExactValues()=runBlocking {Fixture().use {f->
        val result=page(f,731,true);assertEquals(731,result.page.offset);assertTrue(result.includeDisbanded)
        assertEquals(Json.parseToJsonElement("""{"profile":"worker","limit":100,"offset":731,"include_disbanded":true}"""),f.calls[1]["params"])
    }}
    @Test fun missingOrRemappedHomeRefusesBeforeAnyTicketOrGroupRpc()=runBlocking {
        for(mode in listOf("missing","duplicate","before")) Fixture().use {f->f.mode=mode;refused {page(f)};assertTrue(f.calls.isEmpty());assertTrue(f.requests.none {it.path=="/prefix/api/auth/ws-ticket"})}
    }
    @Test fun changedPostReadHomeCannotReturnItsPage()=runBlocking {Fixture().use {f->f.mode="after";refused {page(f)};assertEquals(3,f.calls.size);assertEquals(1,f.sockets.get())}}
    @Test fun changedPostReadCapabilityAuthorityCannotReturnItsPage()=runBlocking {Fixture().use {f->f.mode="authority";refused {page(f)};assertEquals(3,f.calls.size)}}
    @Test fun absentReadAdvertisementDoesNotSendAListRequest()=runBlocking {Fixture().use {f->f.mode="no-list";refused {page(f)};assertEquals(listOf("groups.capabilities"),f.calls.map {it["method"]!!.jsonPrimitive.content})}}
    @Test fun unavailableRoomProtocolCannotFallBackOrAdmitOtherMethods()=runBlocking {Fixture().use {f->f.mode="unsupported";refused {page(f)};assertEquals(1,f.calls.size);assertEquals(1,f.sockets.get())}}
    @Test fun droppedReadIsNotReconnectedOrReplayed()=runBlocking {Fixture().use {f->f.mode="drop";refused {page(f)};assertEquals(1,f.sockets.get());assertEquals(1,f.calls.count {it["method"]==JsonPrimitive("groups.list")});assertEquals(1,f.requests.count {it.path=="/prefix/api/auth/ws-ticket"})}}
    @Test fun actorOrEndpointMismatchIsRefusedBeforeNetwork()=runBlocking {Fixture().use {f->
        val other=f.binding.copy(owner=f.binding.owner.copy(actor="password · another"));refused {HostedRoomsRepository().page(other,f.api,f.tokens,0,false)}
        val address=f.binding.copy(owner=f.binding.owner.copy(url="https://other.invalid/"));refused {HostedRoomsRepository().page(address,f.api,f.tokens,0,false)};assertTrue(f.requests.isEmpty())
    }}
    @Test fun refreshedServerIdentityIsVerifiedBeforeGroupCalls()=runBlocking {Fixture().use {f->f.mode="identity";refused {page(f)};assertTrue(f.calls.isEmpty())}}
    @Test fun detailReadsTheExactSelectedRoomWithoutSubmittingWork()=runBlocking {Fixture().use {f->
        val listing=page(f);val selected=listing.page.rooms.single();val detail=HostedRoomsRepository().detail(listing,selected,f.api,f.tokens)
        assertEquals(f.binding,detail.scope);assertEquals("Fixture purpose",detail.context!!.purpose)
        assertEquals(Json.parseToJsonElement("""{"profile":"worker","room_id":"room-1","include_disbanded":false}"""),f.calls.single {it["method"]==JsonPrimitive("groups.state")}["params"])
        assertTrue(f.calls.all {it["method"]!!.jsonPrimitive.content in setOf("groups.capabilities","groups.list","groups.state")})
    }}
    @Test fun copiedOrUnlistedSelectionCannotProduceAnyNewNetworkRequest()=runBlocking {Fixture().use {f->
        val listing=page(f);val before=f.requests.size;refused {HostedRoomsRepository().detail(listing,listing.page.rooms.single().copy(),f.api,f.tokens)};assertEquals(before,f.requests.size)
    }}
    @Test fun anotherRoomOrAuthorityEpochIsNotSubstitutedOnDetail()=runBlocking {
        for(mode in listOf("id","epoch")) Fixture().use {f->val listing=page(f);if(mode=="id") f.detailId="room-other" else f.roomEpoch=2;refused {HostedRoomsRepository().detail(listing,listing.page.rooms.single(),f.api,f.tokens)}}
    }
    @Test fun forwardedRoomAuthorityNeedNotEqualTheServingGateway()=runBlocking {Fixture().use {f->
        f.roomAuthority="other-gateway";val listing=page(f);assertNotEquals(listing.capabilities.authorityId,listing.page.rooms.single().authorityId)
        assertEquals("other-gateway",HostedRoomsRepository().detail(listing,listing.page.rooms.single(),f.api,f.tokens).room.authorityId)
    }}
    @Test fun changedGatewayInstallationRequiresFreshInventoryBeforeDetail()=runBlocking {Fixture().use {f->
        val listing=page(f);f.mode="changed-install";refused {HostedRoomsRepository().detail(listing,listing.page.rooms.single(),f.api,f.tokens)};assertTrue(f.calls.none {it["method"]==JsonPrimitive("groups.state")})
    }}
    @Test fun withdrawnDetailAdvertisementSendsNoStateRequest()=runBlocking {Fixture().use {f->
        val listing=page(f);f.stateAdvertised=false;refused {HostedRoomsRepository().detail(listing,listing.page.rooms.single(),f.api,f.tokens)};assertTrue(f.calls.none {it["method"]==JsonPrimitive("groups.state")})
    }}
}
