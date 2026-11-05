package org.hermesmobile.app

import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Literal official response envelopes; this fixture does not emulate a Kanban store or dispatcher. */
class HostKanbanFixture(val server:MockWebServer,val mode:String) {
    val home=AtomicReference("/srv/access-worker")
    val dbPath=AtomicReference("/shared/kanban/boards/garden/kanban.db")
    val catalogs=AtomicInteger();val boards=AtomicInteger();val tasks=AtomicInteger();val mutations=AtomicInteger()
    val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
    val secondRequests=AtomicInteger()
    val errors=java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
    private fun meta(slug:String="garden",archived:Boolean=false)=buildJsonObject {
        put("slug",slug);put("name",if(archived) "Archived board" else "Private shared board");put("description","Private board description")
        put("db_path",if(archived) "/shared/kanban/boards/archive/kanban.db" else dbPath.get());put("default_workdir","/srv/private/repository")
        put("project_id","p_private");put("project_name","Private project");put("created_at",JsonNull);put("archived",archived);put("is_current",false)
        put("counts",obj("""{"scheduled":1,"running":1,"archived":1}"""));put("total",2);put("default_workspace_kind","worktree")
    }
    private fun card(id:String="t_123",status:String="running",title:String="Private board task")=buildJsonObject {
        put("id",id);put("title",title);put("status",status);put("priority",7);put("assignee","worker");put("tenant","private-tenant")
        put("body","Private task body\n第二行");put("created_at",JsonNull);put("started_at",1793820000);put("completed_at",JsonNull)
        put("current_run_started_at",1793820005);put("current_run_id",9);put("last_heartbeat_at",1793820006)
        put("latest_summary","Private latest summary");put("result",JsonNull);put("workspace_kind","worktree");put("workspace_path","/srv/private/repository")
        put("completion_contract","local-only");put("claim_lock","never-render-claim");put("idempotency_key","never-render-idempotency")
    }
    private fun board()=buildJsonObject {
        put("columns",buildJsonArray {
            add(buildJsonObject {put("name","scheduled");put("tasks",JsonArray(listOf(card("t_scheduled","scheduled","Nightly archive"))))})
            add(buildJsonObject {put("name","running");put("tasks",JsonArray(listOf(card())))})
            add(buildJsonObject {put("name","done");put("tasks",JsonArray(emptyList()))})
            add(buildJsonObject {put("name","archived");put("tasks",JsonArray(listOf(card("t_archive","archived","Old task"))))})
        });put("tenants",JsonArray(listOf(JsonPrimitive("private-tenant"))));put("assignees",JsonArray(listOf(JsonPrimitive("worker"))));put("latest_event_id",12);put("now",1793820010)
    }
    private fun detail(child:Boolean=false):JsonObject {
        if(child) return buildJsonObject {put("task",card("t_child","done","Private child task"));for(key in listOf("comments","runs","events","attachments","link_tasks","child_results")) put(key,JsonArray(emptyList()));put("links",obj("""{"parents":[],"children":[]}"""))}
        return obj("""{"task":${if(mode=="wrong-task") card("t_other","running","Private wrong task") else card()},"comments":[{"id":1,"task_id":"${if(mode=="foreign-history") "t_other" else "t_123"}","author":"Private author","body":"Private comment body","created_at":1793820000}],"runs":[{"id":9,"task_id":"t_123","status":"running","outcome":null,"profile":"worker","started_at":1793820005,"ended_at":null,"last_heartbeat_at":1793820006,"summary":"Private full attempt summary","metadata":{"secret":"never-render-metadata"}}],"events":[{"id":12,"task_id":"t_123","kind":"claimed","created_at":1793820005,"run_id":9,"payload":{"secret":"never-render-payload"}}],"attachments":[{"id":4,"task_id":"t_123","filename":"Private attachment.txt","size":12,"created_at":1793820000,"stored_path":"/srv/never-render-attachment"}],"links":{"parents":["t_missing"],"children":["t_child"]},"link_tasks":[{"id":"t_child","title":"Private child task","status":"done"}],"child_results":[{"id":"t_child","title":"Private child task","status":"done","latest_summary":"Private child handoff","result":null}]}""")
    }
    fun start() {
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(q:RecordedRequest):MockResponse=try {
                val u=q.requestUrl!!;val path=u.encodedPath
                if(path.startsWith("/prefix/dashboard/")) assertEquals("Bearer kanban-ui-fixture",q.getHeader("Authorization"))
                when {
                    path=="/prefix/inference/v1/capabilities"->json(obj("""{"features":{},"endpoints":{}}"""))
                    path=="/prefix/inference/api/jobs"->json(obj("""{"jobs":[]}"""))
                    path=="/prefix/dashboard/api/auth/me"->json(obj("""{"provider":"password","user_id":"fixture-user"}"""))
                    path=="/prefix/dashboard/api/profiles"->json(buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");if(mode!="no-home") put("path",home.get())});add(obj("""{"name":"other","path":"/srv/access-other"}"""))})})
                    path=="/prefix/dashboard/api/mcp/servers"->json(obj("""{"servers":[]}"""))
                    path.startsWith("/prefix/dashboard/api/plugins/kanban/")->{
                        assertEquals("GET",q.method);assertEquals("worker",u.queryParameter("profile"));assertNull(u.queryParameter("token"));assertNull(q.getHeader("Cookie"))
                        when(path.removePrefix("/prefix/dashboard/api/plugins/kanban/")) {
                            "boards"->{catalogs.incrementAndGet();assertEquals(setOf("profile","include_archived"),u.queryParameterNames);assertEquals("true",u.queryParameter("include_archived"))
                                if(mode=="unavailable") MockResponse().setResponseCode(404)
                                else if(mode=="redirect") MockResponse().setResponseCode(302).setHeader("Location",server.url("outside").toString())
                                else {if(mode=="after-inventory" && catalogs.get()==1) home.set("/srv/remapped-access");json(buildJsonObject {put("boards",JsonArray(listOf(meta(),meta("archive",true))));put("current","default")})}}
                            "board"->{boards.incrementAndGet();assertEquals(setOf("profile","board","include_archived"),u.queryParameterNames);assertEquals("garden",u.queryParameter("board"));assertEquals("true",u.queryParameter("include_archived"))
                                if(mode=="held") {entered.countDown();assertTrue(release.await(20,TimeUnit.SECONDS));returned.countDown()}
                                if(mode=="after-board") dbPath.set("/other/kanban.db")
                                json(board())}
                            "tasks/t_123"->{tasks.incrementAndGet();if(mode=="after-task") dbPath.set("/other/kanban.db");if(mode=="after-task-access") home.set("/srv/remapped-access");assertEquals(setOf("profile","board"),u.queryParameterNames);assertEquals("garden",u.queryParameter("board"));json(detail())}
                            "tasks/t_child"->{tasks.incrementAndGet();assertEquals("garden",u.queryParameter("board"));json(detail(true))}
                            "tasks/t_missing"->{tasks.incrementAndGet();MockResponse().setResponseCode(404)}
                            else->error("Unexpected Kanban fixture route")
                        }
                    }
                    path=="/outside"->{secondRequests.incrementAndGet();error("Redirect must not be followed")}
                    q.method!="GET"->{mutations.incrementAndGet();error("No mutation is approved")}
                    else->MockResponse().setResponseCode(404)
                }
            } catch(e:Throwable) {errors.add(e);MockResponse().setResponseCode(500)}
        }
    }
    fun assertClean() {assertTrue(errors.joinToString {it.javaClass.simpleName},errors.isEmpty());assertEquals(0,mutations.get());assertEquals(0,secondRequests.get())}
}
