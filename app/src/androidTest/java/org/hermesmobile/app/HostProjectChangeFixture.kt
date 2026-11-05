package org.hermesmobile.app

import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Literal before/ack/after fixtures; does not implement the host project store. */
class HostProjectChangeFixture(val mode:String="normal",val before:JsonObject=listing(),val ack:JsonObject=answer(changed()),val after:JsonObject=listing(listOf(changed())),val beforeReads:Int=1,val detail:JsonObject=answer(row()),val prefix:String="prefix/"):Closeable {
    val reportedHome=AtomicReference("/srv/project-worker")
    val server=MockWebServer()
    val writes=AtomicInteger();val lists=AtomicInteger();val homes=AtomicInteger();val tickets=AtomicInteger()
    val requests=CopyOnWriteArrayList<RecordedRequest>();val calls=CopyOnWriteArrayList<JsonObject>()
    val entered=CountDownLatch(1);val release=CountDownLatch(1)
    val tokens=ManagementTokens("project-change-fixture","refresh-fixture","password","fixture",4102444800)
    val api:ManagementClient
    val target:ManagementTarget
    val home=PluginHome.project(JsonPrimitive("/srv/project-worker"))!!.identity()
    init {
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(q:RecordedRequest):MockResponse {
                requests.add(q)
                return when(q.path) {
                    "/${prefix}api/auth/me"->{check(q.getHeader("Authorization")=="Bearer project-change-fixture");json(buildJsonObject {put("provider","password");put("user_id","fixture")})}
                    "/${prefix}api/profiles"->{val n=homes.incrementAndGet();val mapped=if(mode=="before-home" && n>=2 || mode=="after-home" && n>=3) "/srv/remapped" else if(mode=="ui-after-home" && writes.get()>0) "/srv/remapped" else reportedHome.get();json(buildJsonObject {put("profiles",buildJsonArray {for(name in listOf("worker","mirror")) add(buildJsonObject {put("name",name);put("path",mapped)})})})}
                    "/${prefix}api/auth/ws-ticket"->{check(q.method=="POST");check(q.getHeader("Authorization")=="Bearer project-change-fixture");tickets.incrementAndGet();json(buildJsonObject {put("ticket","p".repeat(43));put("ttl_seconds",30)})}
                    "/${prefix}api/ws"->MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                        override fun onOpen(socket:WebSocket,response:Response) {socket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready"}}""")}
                        override fun onMessage(socket:WebSocket,text:String) {
                            val j=Json.parseToJsonElement(text).jsonObject;calls.add(j)
                            val method=j["method"]!!.jsonPrimitive.content
                            val result=if(method=="projects.list") {if(lists.incrementAndGet()<=beforeReads) before else after} else if(method=="projects.get") {detail} else {
                                writes.incrementAndGet();entered.countDown()
                                if(mode=="held") check(release.await(15,TimeUnit.SECONDS))
                                if(mode=="disconnect") {socket.close(1011,"private-result-lost");return}
                                if(mode in setOf("missing","late-error")) {socket.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("error",buildJsonObject {put("code",if(mode=="missing") 5062 else 5063);put("message","private-server-error")})}.toString());return}
                                if(mode=="malformed") buildJsonObject {put("project",42)} else ack
                            }
                            socket.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                        }
                    }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                    else->MockResponse().setResponseCode(404)
                }
            }
        }
        api=ManagementClient(server.url(prefix).toString(),true,true)
        target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
    }
    fun expected(owner:ManagementTarget=target)=HostProjectProtocol.inventory(listing(),owner,home,100)
    override fun close() {release.countDown();server.close()}
    companion object {
        fun row(id:String="p_00000001",slug:String="garden",name:String="Private garden",archived:Boolean=false,folderPath:String?="/srv/private/garden",primary:Boolean=true)=buildJsonObject {
            put("id",id);put("slug",slug);put("name",name);put("description","Private notes");put("created_at",100);put("archived",archived)
            put("primary_path",if(primary && folderPath!=null) JsonPrimitive(folderPath) else JsonNull)
            put("folders",buildJsonArray {folderPath?.let {add(buildJsonObject {put("path",it);put("is_primary",primary);put("added_at",100)})}})
        }
        fun changed()=row(name="Changed name")
        fun answer(project:JsonObject)=buildJsonObject {put("project",project)}
        fun listing(rows:List<JsonObject> = listOf(row()),active:String?="p_00000001")=buildJsonObject {put("projects",JsonArray(rows));put("active_id",active?.let(::JsonPrimitive) ?: JsonNull)}
        private fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
    }
}