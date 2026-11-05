package org.hermesmobile.app

import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Literal responses and a one-write phase switch, not an emulated Kanban database/dispatcher. */
class HostKanbanTaskFixture(val server:MockWebServer,val mode:String="normal") {
    val writes=AtomicInteger();val reads=AtomicInteger();val errors=java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
    val entered=CountDownLatch(1);val release=CountDownLatch(1)
    val parentDetail=AtomicReference(detailJson(card("t_parent",status="done")))
    val beforeDetail=AtomicReference(detailJson(card()));val afterDetail=AtomicReference(detailJson(card(title="Reviewed title")))
    val ack=AtomicReference(buildJsonObject {put("task",card(title="Reviewed title"))})
    val projects=HostKanbanBoardFixture.projectList()
    val catalogBefore=HostKanbanBoardFixture.catalog();val catalogAfter=AtomicReference(catalogBefore)
    val delegate=HostKanbanBoardFixture(server,"normal","PATCH","/unused",null,catalogBefore,JsonObject(emptyMap()),catalogBefore)
    val owner get()=delegate.owner
    val tokens get()=delegate.tokens
    val home get()=delegate.homeId
    val currentHome get()=delegate.home
    fun api()=delegate.api()
    var method="PATCH";var tail="/t_child";var body:JsonObject?=buildJsonObject {put("title","Reviewed title")}
    var query:Map<String,String> = emptyMap();var link=false
    fun inventory()=delegate.expected()
    fun snapshot()=HostKanbanProtocol.snapshot(boardJson(),owner,home,inventory().boards.single {it.slug=="garden"})
    fun detail(id:String="t_child"):HostKanbanDetail {val j=if(id=="t_child") beforeDetail.get() else parentDetail.get();return HostKanbanProtocol.detail(j,owner,home,snapshot().board,id)}
    fun request()=HostKanbanTaskChange.metadata(inventory(),snapshot(),detail(),"Reviewed title")
    fun start() {
        delegate.start();val fallback=server.dispatcher
        server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=try {
            val u=q.requestUrl!!;val path=u.encodedPath;val taskPath="/prefix/dashboard/api/plugins/kanban/tasks";val linkPath="/prefix/dashboard/api/plugins/kanban/links"
            if(path.startsWith(taskPath) || path==linkPath || path=="/prefix/dashboard/api/plugins/kanban/board") {
                assertEquals("Bearer board-fixture-access",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));assertEquals("worker",u.queryParameter("profile"));assertEquals("garden",u.queryParameter("board"))
                if(q.method!="GET") {
                    assertEquals(method,q.method);assertEquals((if(link) linkPath else taskPath)+tail,path)
                    assertEquals(setOf("profile","board")+query.keys,u.queryParameterNames)
                    for((k,v) in query) assertEquals(v,u.queryParameter(k))
                    if(body==null) assertEquals(0L,q.bodySize) else {val actual=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;if(method=="POST" && tail.isEmpty() && !link) {val nonce=actual["idempotency_key"]!!.jsonPrimitive.content;assertEquals(nonce,java.util.UUID.fromString(nonce).toString());assertEquals(body,JsonObject(actual.filterKeys {it!="idempotency_key"}))} else assertEquals(body,actual)}
                    writes.incrementAndGet();delegate.phase.set(1)
                    if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                    if(mode=="home-after") delegate.home.set("/other/home")
                    when(mode) {"http400"->MockResponse().setResponseCode(400).setBody("private-host-error");"lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);else->json(ack.get())}
                } else {
                    reads.incrementAndGet()
                    if(path=="/prefix/dashboard/api/plugins/kanban/board") json(boardJson(if(writes.get()>0 && method=="DELETE" && !link) listOf(card("t_parent",status="done")) else listOf(card(),card("t_parent",status="done"))))
                    else if(path==taskPath+"/t_parent") json(parentDetail.get())
                    else if(path in setOf(taskPath+"/t_child",taskPath+"/t_new")) {
                        if(writes.get()>0 && mode=="readback-fail") MockResponse().setResponseCode(500)
                        else {if(writes.get()>0 && mode=="held-readback") {entered.countDown();release.await(10,TimeUnit.SECONDS)};json(if(writes.get()>0) afterDetail.get() else beforeDetail.get())}
                    } else error("Unexpected task read")
                }
            } else if(path=="/prefix/dashboard/api/plugins/kanban/boards" && writes.get()>0) json(catalogAfter.get())
            else fallback.dispatch(q)
        } catch(t:Throwable) {errors.add(t);MockResponse().setResponseCode(500).setBody("fixture assertion")}}
    }
    fun assertClean() {delegate.assertClean();if(errors.isNotEmpty()) throw AssertionError("Task fixture assertion",errors.first())}
    companion object {
        private fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
        fun card(id:String="t_child",title:String="Private task",status:String="ready")=buildJsonObject {put("id",id);put("title",title);put("status",status);put("priority",0);put("assignee",JsonNull);put("tenant",JsonNull);put("body","Private body");put("workspace_kind","scratch");put("goal_mode",false)}
        fun boardJson(cards:List<JsonObject> = listOf(card(),card("t_parent",status="done")))=buildJsonObject {put("columns",buildJsonArray {add(buildJsonObject {put("name","ready");put("tasks",JsonArray(cards))})});put("tenants",JsonArray(emptyList()));put("assignees",JsonArray(emptyList()));put("latest_event_id",0);put("now",1)}
        fun detailJson(task:JsonObject,parents:List<String> = emptyList(),comments:JsonArray=JsonArray(emptyList()))=buildJsonObject {put("task",task);put("comments",comments);for(k in listOf("runs","events","attachments","link_tasks","child_results")) put(k,JsonArray(emptyList()));put("links",buildJsonObject {put("parents",JsonArray(parents.map(::JsonPrimitive)));put("children",JsonArray(emptyList()))})}
    }
}
