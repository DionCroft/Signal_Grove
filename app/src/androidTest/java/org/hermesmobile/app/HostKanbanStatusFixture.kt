package org.hermesmobile.app

import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Literal authenticated HTTP observations; never executes a worker or changes a host task. */
class HostKanbanStatusFixture(val server: MockWebServer, val mode: String = "normal") {
    val base = HostKanbanTaskFixture(server)
    val owner get() = base.owner
    val home get() = base.home
    val tokens get() = base.tokens
    val writes get() = base.writes
    val entered = CountDownLatch(1); val release = CountDownLatch(1)
    val before = ConcurrentHashMap<String, JsonObject>()
    val after = ConcurrentHashMap<String, JsonObject>()
    val expectedBody = AtomicReference(HostKanbanBoardFixture.obj("""{"status":"triage"}"""))
    val acknowledgement = AtomicReference(buildJsonObject { put("task", card(status = "triage")) })
    var onGraphRead: (() -> Unit)? = null

    init { before["t_root"] = detail(card()); after["t_root"] = detail(card(status = "triage")) }
    private val client by lazy { base.api() }
    fun api() = client
    fun inventory() = base.inventory()
    fun board() = inventory().boards.single { it.slug == "garden" }
    fun root() = observation(before.getValue("t_root"))
    fun observation(value: JsonObject) = HostKanbanStatusObservation.parse(value, owner, home, board(), value.getValue("task").jsonObject.getValue("id").jsonPrimitive.content)
    fun snapshot() = HostKanbanProtocol.snapshot(boardJson(before), owner, home, board())
    fun plan(target: HostKanbanStatusTarget = HostKanbanStatusTarget.Triage, statement: String? = null) = HostKanbanStatusPlan.review(root().detail, target, statement)
    fun start() {
        base.start(); val fallback = server.dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(q: RecordedRequest): MockResponse = try {
                val url = q.requestUrl!!; val path = url.encodedPath
                val taskPath = "/prefix/dashboard/api/plugins/kanban/tasks/"
                if (path.startsWith(taskPath) || path == "/prefix/dashboard/api/plugins/kanban/board") {
                    assertEquals("Bearer board-fixture-access", q.getHeader("Authorization")); assertNull(q.getHeader("Cookie")); assertNull(q.getHeader("Origin")); assertNull(q.getHeader("Idempotency-Key"))
                    assertEquals(setOf("profile", "board"), url.queryParameterNames)
                    assertEquals("worker", url.queryParameter("profile")); assertEquals("garden", url.queryParameter("board"))
                    if (q.method == "PATCH") {
                        assertEquals(taskPath + "t_root", path); assertEquals(expectedBody.get(), Json.parseToJsonElement(q.body.readUtf8()))
                        writes.incrementAndGet(); base.delegate.phase.set(1)
                        if (mode == "held-write") { entered.countDown(); release.await(20, TimeUnit.SECONDS) }
                        if (mode == "home-after") base.currentHome.set("/changed/home")
                        when (mode) {
                            "http400", "http409", "http500" -> MockResponse().setResponseCode(mode.removePrefix("http").toInt()).setBody("private-host-diagnostic")
                            "lost" -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            else -> json(acknowledgement.get())
                        }
                    } else {
                        assertEquals("GET", q.method)
                        if (path == "/prefix/dashboard/api/plugins/kanban/board") json(boardJson(if (writes.get() == 0) before else after))
                        else {
                            val id = path.removePrefix(taskPath)
                            if (writes.get() == 0) onGraphRead?.invoke()
                            if (writes.get() > 0 && mode == "held-readback") { entered.countDown(); release.await(20, TimeUnit.SECONDS) }
                            if (writes.get() > 0 && mode == "readback-fail") MockResponse().setResponseCode(500)
                            else (if (writes.get() == 0) before else after)[id]?.let(::json) ?: MockResponse().setResponseCode(404)
                        }
                    }
                } else fallback.dispatch(q)
            } catch (error: Throwable) { base.errors.add(error); MockResponse().setResponseCode(500).setBody("status fixture assertion") }
        }
    }
    fun assertClean() = base.assertClean()

    companion object {
        private fun json(value: JsonObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
        fun card(id: String = "t_root", status: String = "ready") = buildJsonObject {
            put("id", id); put("title", "Private status task"); put("status", status); put("priority", 0)
            put("body", "Private task body"); put("assignee", JsonNull); put("tenant", JsonNull)
            put("workspace_kind", "scratch"); put("workspace_path", "/private/scratch/work")
            for (key in listOf("current_run_id", "claim_lock", "worker_pid", "result", "completion_contract", "model_override", "provider_override", "reasoning_effort")) put(key, JsonNull)
        }
        fun fields(task: JsonObject, vararg values: Pair<String, JsonElement>) = JsonObject(task + mapOf(*values))
        fun detail(task: JsonObject, parents: List<String> = emptyList(), children: List<String> = emptyList()) = buildJsonObject {
            put("task", task); for (key in listOf("comments", "runs", "events", "attachments", "link_tasks", "child_results")) put(key, JsonArray(emptyList()))
            put("links", buildJsonObject { put("parents", JsonArray(parents.map(::JsonPrimitive))); put("children", JsonArray(children.map(::JsonPrimitive))) })
        }
        private fun boardJson(nodes: Map<String, JsonObject>) = HostKanbanTaskFixture.boardJson(nodes.toSortedMap().values.map {
            val task = it.getValue("task").jsonObject
            val summary = (task["latest_summary"] as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
            if (summary == null) task else fields(task, "latest_summary" to JsonPrimitive(summary.take(200)))
        })
    }
}
