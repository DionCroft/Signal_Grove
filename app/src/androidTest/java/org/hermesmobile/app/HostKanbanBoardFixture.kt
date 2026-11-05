package org.hermesmobile.app

import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Literal before/ACK/after envelopes. No local emulation of a board store or dispatcher. */
class HostKanbanBoardFixture(val server:MockWebServer,val mode:String,val method:String,val tail:String,val body:JsonObject?,val before:JsonObject,val ack:JsonObject,val after:JsonObject) {
    val home=AtomicReference("/srv/access-worker")
    val writes=AtomicInteger();val catalogs=AtomicInteger();val tickets=AtomicInteger();val projectReads=AtomicInteger()
    val phase=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
    val errors=java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
    val beforeOverride=AtomicReference<JsonObject?>(null)
    val projects=AtomicReference(projectList())
    var catalogHook:(()->Unit)?=null
    val base get()=server.url("prefix/dashboard/").toString()
    val tokens=ManagementTokens("board-fixture-access","refresh-fixture","password","fixture-user",4102444800)
    val owner get()=ManagementTarget("a".repeat(64),base,"worker","password · fixture-user")
    val homeId get()=PluginHome.project(JsonPrimitive("/srv/access-worker"))!!.identity()
    fun expected()=HostKanbanProtocol.inventory(before,owner,homeId)
    fun api()=ManagementClient(base,true,true)
    private fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
    fun start() {server.dispatcher=object:Dispatcher() {
        override fun dispatch(q:RecordedRequest):MockResponse=try {
            val u=q.requestUrl!!;val path=u.encodedPath
            if(path.startsWith("/prefix/dashboard/") && path!="/prefix/dashboard/api/ws") assertEquals("Bearer board-fixture-access",q.getHeader("Authorization"))
            when {
                path=="/prefix/inference/v1/capabilities"->json(obj("""{"features":{},"endpoints":{}}"""))
                path=="/prefix/inference/api/jobs"->json(obj("""{"jobs":[]}"""))
                path=="/prefix/dashboard/api/auth/me"->json(obj("""{"provider":"password","user_id":"fixture-user"}"""))
                path=="/prefix/dashboard/api/profiles"->json(buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path",home.get())});add(obj("""{"name":"other","path":"/srv/access-other"}"""));add(obj("""{"name":"alias","path":"/srv/access-worker"}"""))})})
                path=="/prefix/dashboard/api/mcp/servers"->json(obj("""{"servers":[]}"""))
                path=="/prefix/dashboard/api/auth/ws-ticket"->{tickets.incrementAndGet();json(obj("""{"ticket":"${"p".repeat(42)}${tickets.get()}","ttl_seconds":30}"""))}
                path=="/prefix/dashboard/api/ws"->MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(socket:WebSocket,response:Response) {socket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready"}}""")}
                    override fun onMessage(socket:WebSocket,text:String) {
                        try {val request=Json.parseToJsonElement(text).jsonObject;assertEquals(JsonPrimitive("projects.list"),request["method"]);assertEquals(obj("""{"profile":"worker"}"""),request["params"]);projectReads.incrementAndGet();socket.send(buildJsonObject {put("jsonrpc","2.0");put("id",request["id"]!!);put("result",projects.get())}.toString())}
                        catch(e:Throwable) {errors.add(e);socket.close(1000,"fixture failure")}
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                path=="/prefix/dashboard/api/plugins/kanban/boards" && q.method=="GET"->{
                    catalogs.incrementAndGet();assertEquals("worker",u.queryParameter("profile"));assertEquals(setOf("profile","include_archived"),u.queryParameterNames);assertEquals("true",u.queryParameter("include_archived"));catalogHook?.invoke()
                    if(phase.get()>0 && mode=="readback-fail") MockResponse().setResponseCode(503)
                    else {var value=if(phase.get()>0) after else beforeOverride.get() ?: before
                        if(phase.get()>0 && mode=="root-after") {val rows=value["boards"]!!.jsonArray.map {v->val row=v.jsonObject;if(row.text("slug")=="default") JsonObject(row+mapOf("db_path" to JsonPrimitive("/other/root/kanban.db"))) else row};value=JsonObject(value+mapOf("boards" to JsonArray(rows)))}
                        json(value)}
                }
                path=="/prefix/dashboard/api/plugins/kanban/boards"+tail && q.method==method->{
                    assertEquals("worker",u.queryParameter("profile"));assertNull(q.getHeader("Cookie"));assertNull(u.queryParameter("token"))
                    if(method=="DELETE") {assertEquals(setOf("profile","delete"),u.queryParameterNames);assertEquals((ack["result"]?.jsonObject?.text("action")=="deleted").toString(),u.queryParameter("delete"));assertEquals("",q.body.readUtf8())}
                    else {assertEquals(setOf("profile"),u.queryParameterNames);assertEquals(body,Json.parseToJsonElement(q.body.readUtf8()))}
                    writes.incrementAndGet();phase.set(1)
                    if(mode=="held") {entered.countDown();assertTrue(release.await(20,TimeUnit.SECONDS));returned.countDown()}
                    if(mode=="home-after") home.set("/srv/access-remapped")
                    when(mode) {"lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);"http400"->MockResponse().setResponseCode(400);else->json(ack)}
                }
                else->error("Unapproved board fixture request")
            }
        } catch(e:Throwable) {errors.add(e);MockResponse().setResponseCode(500)}
    }}
    fun assertClean()=assertTrue(errors.joinToString {it.javaClass.simpleName+":"+it.message},errors.isEmpty())
    companion object {
        fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
        fun metadata(slug:String="garden",archived:Boolean=false,name:String="Private board")=obj("""{"slug":"$slug","name":"$name","description":"Private description","icon":"leaf","color":"green","db_path":"${if(slug=="default") "/shared/kanban.db" else "/shared/kanban/boards/$slug/kanban.db"}","default_workdir":"/srv/private/repository","project_id":null,"archived":$archived,"created_at":1}""")
        fun catalog(rows:List<JsonObject> = listOf(metadata("default"),metadata(),metadata("archive",true)),current:String="garden")=buildJsonObject {put("boards",JsonArray(rows.map {JsonObject(it+mapOf("counts" to obj("""{"running":1}"""),"total" to JsonPrimitive(1)))}));put("current",current)}
        fun projectList()=obj("""{"projects":[{"id":"p_bind","slug":"binding","name":"Private binding project","primary_path":"/srv/project-primary","created_at":1,"folders":[]}],"active_id":null}""")
    }
}
