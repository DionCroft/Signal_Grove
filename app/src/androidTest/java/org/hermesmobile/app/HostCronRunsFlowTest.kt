package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HostCronRunsFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication

    private val executionId = "1".repeat(32)
    private val projectOccurrence = "e".repeat(32)
    private val projectExecution = "7047c736b0b60bf43d79cd179f05a59b"
    private val projectDue = "2026-11-21T09:00:00+00:00"
    private val outputText = "Private exact scheduler result"

    private inner class Fixture(private val ledgerEnabled: Boolean = false):AutoCloseable {
        val server=credentials.managementServer()
        val writes=AtomicInteger()
        val requests=ConcurrentLinkedQueue<RecordedRequest>()
        val historyReads = AtomicInteger()
        val outputReads = AtomicInteger()
        val timingPrepares = AtomicInteger()
        val timingReviews = AtomicInteger()
        val timingApplies = AtomicInteger()
        val timingObserves = AtomicInteger()
        val runOncePrepares = AtomicInteger()
        val runOnceConfirms = AtomicInteger()
        val runOnceObserves = AtomicInteger()
        val runOnceDecisions = ConcurrentLinkedQueue<String>()
        val projectDecisions = ConcurrentLinkedQueue<String>()
        val projectReads = AtomicInteger()
        private val projectChoice = AtomicReference<String?>()
        @Volatile var loseProjectReply = false
        private val acceptedRunOnce = AtomicReference<String?>()
        @Volatile var loseRunOnceReply = false
        private fun runOnceExecution(request: String): String {
            val identity = """{"profile":"worker","job_id":"job-fixture","request_id":"$request"}"""
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(("signal-grove-cron-run-once-v1\u0000" + identity).encodeToByteArray())
                .take(16).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        @Volatile var changeReview = false
        @Volatile var loseEditReply = false
        private val timingReceipt = AtomicReference<JsonObject?>()
        private val version = "a".repeat(64)
        private val digest = "b".repeat(64)
        private fun effect(minutes: Int) = buildJsonObject {
            putJsonObject("timing") {
                putJsonObject("schedule") { put("kind","interval"); put("minutes",minutes) }
                put("timezone",JsonNull); put("catch_up",JsonNull); put("grace_minutes",JsonNull)
            }
            putJsonObject("repeat") { put("times",JsonNull); put("completed",2) }
        }
        private fun timingPayload(effect: JsonObject) = JsonObject(effect + mapOf(
            "profile" to JsonPrimitive("worker"), "job_id" to JsonPrimitive("job-fixture")))
        val socketCount = AtomicInteger()
        val unexpectedSocketFrames = AtomicInteger()
        val outputEntered = CountDownLatch(1)
        val heldOutput = AtomicReference<(() -> Unit)?>(null)
        @Volatile var holdOutput = false
        @Volatile var wrongExecution = false
        private val ticket = AtomicReference<String?>()
        val entered=CountDownLatch(1)
        val release=CountDownLatch(1)
        @Volatile var delayed=false
        @Volatile var history="""{"limit":20,"runs":[{"id":"conversation-run-fixture","source":"cron","profile":"worker","title":"Private run title fixture","preview":"Private run preview fixture","started_at":1794000000,"last_active":1794000020,"is_active":true,"model":"recorded-model-fixture","message_count":4,"tool_call_count":2},{"id":"cron_output:job-fixture:exec:0","source":"cron_output","profile":"worker","title":"Private output fixture","preview":"Private output error fixture","is_active":false}]}"""
        private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)

        private fun gateway(request: RecordedRequest): MockResponse? {
            if (!ledgerEnabled) return null
            return when (request.path) {
                "/dashboard/api/auth/ws-ticket" -> {
                    if (request.method != "POST" || request.getHeader("Authorization") != "Bearer management-runs-fixture" ||
                        request.getHeader("Cookie") != null || request.body.readUtf8() != "{}") {
                        writes.incrementAndGet()
                        return json("{}").setResponseCode(403)
                    }
                    val singleUse = "cron-fixture-archive-ticket-" + (socketCount.get() + 1)
                    ticket.set(singleUse)
                    json("""{"ticket":"$singleUse","ttl_seconds":30}""")
                }
                "/dashboard/api/ws" -> {
                    val expected = ticket.getAndSet(null)
                    val offered = request.getHeader("Sec-WebSocket-Protocol")?.split(',')?.map(String::trim)
                    if (request.method != "GET" || request.getHeader("Authorization") != null ||
                        request.getHeader("Cookie") != null || expected == null ||
                        offered != listOf("hermes-gateway-v1", "hermes-gateway-ticket.$expected")) {
                        unexpectedSocketFrames.incrementAndGet()
                        return json("{}").setResponseCode(403)
                    }
                    MockResponse().withWebSocketUpgrade(listener()).setHeader("Sec-WebSocket-Protocol", "hermes-gateway-v1")
                }
                else -> null
            }
        }

        private fun listener() = object : WebSocketListener() {
            private var preparedRunOnce: String? = null
            private var preparedProject = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketCount.incrementAndGet()
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"replay_epoch":"cron-fixture","session_authority":true}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val frame = Json.parseToJsonElement(text).jsonObject
                    val params = frame["params"]!!.jsonObject
                    fun send(json: String) {
                        webSocket.send(buildJsonObject {
                            put("jsonrpc", "2.0"); put("id", frame.getValue("id")); put("result", Json.parseToJsonElement(json))
                        }.toString())
                    }
                    val method = frame["method"]!!.jsonPrimitive.content
                    when (method) {
                        "runtime.describe" -> {
                            check(params.isEmpty())
                            send("""{"instance_id":"cron-fixture-native","profile_id":"/fixture/profiles/worker","authority_epoch":1,"capabilities":["durable-admission-v1","event-replay-v1"],"session_create":{"sources":["cli"],"parameters":["title"]}}""")
                        }
                        CronExecutionHistory.Method -> {
                            check(params == buildJsonObject {
                                put("expected_profile", "worker"); put("job_id", "job-fixture"); put("limit", 20)
                            })
                            historyReads.incrementAndGet()
                            send("""{"schema":1,"state":"ok","payload":{"profile":"worker","job_id":"job-fixture","ledger":"available","limited":false,"attempts":[{"execution_id":"$executionId","status":"unknown","claimed_at":"2026-11-21T02:00:00Z","started_at":null,"finished_at":null,"scheduled_instant":null,"delivery_outcome":null,"reported_error":false}]}}""")
                        }
                        CronExecutionOutput.Method -> {
                            check(params == buildJsonObject {
                                put("expected_profile", "worker"); put("job_id", "job-fixture"); put("execution_id", executionId)
                            })
                            outputReads.incrementAndGet()
                            val returned = if (wrongExecution) "2".repeat(32) else executionId
                            val reply = {
                                send("""{"schema":1,"state":"ok","payload":{"profile":"worker","job_id":"job-fixture","execution_id":"$returned","status":"unknown","archive_state":"available","limited":false,"text":"$outputText"}}""")
                            }
                            if (holdOutput) {
                                heldOutput.set(reply)
                                outputEntered.countDown()
                            } else {
                                reply()
                            }
                        }
                        in CronProjectProtocol.methods -> {
                            check(params["expected_profile"] == JsonPrimitive("worker"))
                            if (method != CronProjectProtocol.Inbox) {
                                check(params["occurrence_id"] == JsonPrimitive(projectOccurrence) &&
                                    params["job_id"] == JsonPrimitive("job-fixture"))
                            }
                            val result = when (method) {
                                CronProjectProtocol.Inbox -> {
                                    check(params.size == 1)
                                    buildJsonObject {
                                        putJsonArray("entries") {
                                            if (projectChoice.get() == null) addJsonObject {
                                                put("job_id", "job-fixture"); put("project_id", "registered-fixture")
                                                put("occurrence_id", projectOccurrence); put("scheduled_instant", projectDue)
                                            }
                                        }
                                    }
                                }
                                CronProjectProtocol.Prepare -> {
                                    check(params.size == 3 && projectChoice.get() == null)
                                    preparedProject = true
                                    buildJsonObject {
                                        put("review_digest", digest); put("expires_in_seconds", 179)
                                        put("scheduled_instant", projectDue)
                                        putJsonObject("scope") {
                                            put("project", "registered-fixture"); put("workspace", "verified/workspace")
                                            put("branch", "reviewed-branch"); put("worker", "REGISTERED-WORKER")
                                            put("commit", "a".repeat(40))
                                            put("worker_instance", "registered-instance"); put("access", "write")
                                            put("task", "Update notes in the verified fixture project.")
                                        }
                                    }
                                }
                                CronProjectProtocol.Decide -> {
                                    check(preparedProject && params.size == 5 && params["review_digest"] == JsonPrimitive(digest))
                                    preparedProject = false
                                    val choice = params.getValue("decision").jsonPrimitive.content
                                    check(choice in setOf("approve", "deny", "defer"))
                                    if (choice != "defer") {
                                        val durable = runBlocking {
                                            app.database.dao().managementOperations(api.profileScope.value).first()
                                                .single { it.action == CronProjectProtocol.Action && it.state == "sending" }
                                        }
                                        check(durable.target == "job-fixture")
                                        val stored = CronProjectProtocol.storedOccurrence(durable)
                                        check(stored.id == projectOccurrence && stored.scheduledAt == java.time.OffsetDateTime.parse(projectDue))
                                        check(durable.resultSummary.isEmpty() && !durable.requestSummary.contains("verified"))
                                        check(projectChoice.compareAndSet(null, choice))
                                    }
                                    projectDecisions.add(choice)
                                    if (loseProjectReply && choice == "approve") {
                                        webSocket.close(1011, "fixture lost occurrence reply")
                                        return
                                    }
                                    buildJsonObject {
                                        val resultState = when (choice) {
                                            "approve" -> "claimed"
                                            "deny" -> "denied"
                                            else -> "deferred"
                                        }
                                        put("state", resultState)
                                        if (choice != "defer") {
                                            put("execution_id", projectExecution); put("scheduled_instant", projectDue)
                                        }
                                    }
                                }
                                else -> {
                                    check(!preparedProject && params.size == 3)
                                    val choice = checkNotNull(projectChoice.get())
                                    val read = projectReads.incrementAndGet()
                                    buildJsonObject {
                                        put("decision", choice); put("execution_id", projectExecution)
                                        put("scheduled_instant", projectDue); put("execution", JsonNull)
                                        put("outcome", if (choice == "deny") "cancelled" else if (read == 1) "unknown" else "completed")
                                    }
                                }
                            }
                            val fields = if (method == CronProjectProtocol.Inbox) result else JsonObject(result + mapOf(
                                "job_id" to JsonPrimitive("job-fixture"), "occurrence_id" to JsonPrimitive(projectOccurrence)))
                            val scoped = JsonObject(fields + ("profile" to JsonPrimitive("worker")))
                            send(buildJsonObject { put("schema", 1); put("state", "ok"); put("payload", scoped) }.toString())
                        }
                        in CronRunOnceProtocol.methods -> {
                            check(params["expected_profile"] == JsonPrimitive("worker") && params["job_id"] == JsonPrimitive("job-fixture"))
                            val body = when (method) {
                                CronRunOnceProtocol.Prepare -> {
                                    check(params.size == 2)
                                    val original = "%032x".format(runOncePrepares.incrementAndGet())
                                    preparedRunOnce = original
                                    buildJsonObject {
                                        put("request_id", original); put("review_digest", digest); put("expires_in_seconds", 179)
                                        putJsonObject("scope") {
                                            put("project", "fixture-project"); put("workspace", "workspaces/fixture")
                                            put("branch", "fixture-branch"); put("worker", "FIXTURE-WORKER")
                                            put("commit", "b".repeat(64))
                                            put("worker_instance", "fixture-instance"); put("access", "write")
                                            put("task", "Write the fixture summary without changing the seed.")
                                        }
                                    }
                                }
                                CronRunOnceProtocol.Confirm -> {
                                    val original = checkNotNull(preparedRunOnce)
                                    check(params.size == 4 && params["request_id"] == JsonPrimitive(original) &&
                                        params["review_digest"] == JsonPrimitive(digest))
                                    preparedRunOnce = null
                                    val saved = runBlocking { app.database.dao().managementOperation(original) }
                                    check(saved?.state == "sending" && saved.action == CronRunOnceProtocol.Action &&
                                        saved.target == "job-fixture" && saved.requestSummary.isEmpty() && saved.resultSummary.isEmpty())
                                    check(acceptedRunOnce.compareAndSet(null, original))
                                    runOnceConfirms.incrementAndGet()
                                    if (loseRunOnceReply) {
                                        webSocket.close(1011, "fixture lost run response")
                                        return
                                    }
                                    buildJsonObject { put("request_id", original); put("execution_id", runOnceExecution(original)); put("state", "claimed") }
                                }
                                CronRunOnceProtocol.Dismiss -> {
                                    val original = checkNotNull(preparedRunOnce)
                                    check(params.size == 5 && params["request_id"] == JsonPrimitive(original) &&
                                        params["review_digest"] == JsonPrimitive(digest))
                                    preparedRunOnce = null
                                    val decision = params.getValue("decision").jsonPrimitive.content
                                    check(decision in setOf("deny", "defer"))
                                    runOnceDecisions.add(decision)
                                    buildJsonObject { put("request_id", original); put("decision", decision) }
                                }
                                else -> {
                                    val original = checkNotNull(acceptedRunOnce.get())
                                    check(preparedRunOnce == null && params.size == 3 && params["request_id"] == JsonPrimitive(original))
                                    val status = if (runOnceObserves.incrementAndGet() == 1) "running" else "completed"
                                    buildJsonObject {
                                        put("request_id", original)
                                        putJsonObject("execution") {
                                            put("execution_id", runOnceExecution(original)); put("status", status)
                                            put("claimed_at", "2026-11-21T02:00:00Z"); put("started_at", JsonNull)
                                            put("finished_at", JsonNull); put("scheduled_instant", JsonNull)
                                            put("delivery_outcome", JsonNull); put("reported_error", false)
                                        }
                                    }
                                }
                            }
                            val payload = JsonObject(body + mapOf("profile" to JsonPrimitive("worker"),
                                "job_id" to JsonPrimitive("job-fixture")))
                            send(buildJsonObject { put("schema", 1); put("state", "ok"); put("payload", payload) }.toString())
                        }
                        CronTimingProtocol.Prepare, CronTimingProtocol.Review, CronTimingProtocol.Apply, CronTimingProtocol.Observe -> {
                            check(params["expected_profile"] == JsonPrimitive("worker") && params["job_id"] == JsonPrimitive("job-fixture"))
                            if (method in setOf(CronTimingProtocol.Review, CronTimingProtocol.Apply)) {
                                check(params["expected_version"] == JsonPrimitive(version))
                                check(params["changes"] == buildJsonObject { put("schedule","every 2h") })
                            }
                            val body = when (method) {
                                CronTimingProtocol.Prepare -> {
                                    timingPrepares.incrementAndGet()
                                    check(params.size == 2)
                                    timingPayload(JsonObject(effect(60) + ("version" to JsonPrimitive(version))))
                                }
                                CronTimingProtocol.Review -> {
                                    val attempt = timingReviews.incrementAndGet()
                                    check(params.size == 4)
                                    timingPayload(buildJsonObject {
                                        put("version",version); put("profile_timezone","Europe/London")
                                        put("before",effect(60)); put("after",effect(120))
                                        put("review_digest", if (changeReview && attempt > 1) "c".repeat(64) else digest)
                                    })
                                }
                                CronTimingProtocol.Apply -> {
                                    timingApplies.incrementAndGet()
                                    val key = params.getValue("request_id").jsonPrimitive.content
                                    check(params.size == 6 && params["review_digest"] == JsonPrimitive(digest))
                                    val durable = runBlocking { app.database.dao().managementOperation(key) }
                                    check(durable?.state == "sending" && durable.action == CronTimingProtocol.Action)
                                    val receipt = timingPayload(JsonObject(effect(120) + mapOf(
                                        "request_id" to JsonPrimitive(key), "reviewed_version" to JsonPrimitive(version),
                                        "review_digest" to JsonPrimitive(digest))))
                                    timingReceipt.set(receipt)
                                    if (loseEditReply) {
                                        webSocket.close(1011, "fixture edit ACK lost")
                                        return
                                    }
                                    receipt
                                }
                                else -> {
                                    timingObserves.incrementAndGet()
                                    val exact = checkNotNull(timingReceipt.get())
                                    check(params.size == 3 && params["request_id"] == exact["request_id"])
                                    exact
                                }
                            }
                            send(buildJsonObject { put("schema",1); put("state","ok"); put("payload",body) }.toString())
                        }
                        else -> error("Unexpected RPC.")
                    }
                } catch (_: Exception) {
                    unexpectedSocketFrames.incrementAndGet()
                    webSocket.close(1008, "fixture")
                }
            }
        }

        init {
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    requests.add(request)
                    gateway(request)?.let { return it }
                    if(request.method!="GET") {writes.incrementAndGet();return json("{}").setResponseCode(405)}
                    return when(request.path) {
                        "/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                        "/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Run history fixture"}""")
                        "/dashboard/api/profiles" -> if (ledgerEnabled)
                            json("""{"profiles":[{"name":"worker","path":"/fixture/profiles/worker"},{"name":"other","path":"/fixture/profiles/other"}]}""")
                        else json("""{"profiles":[{"name":"worker"},{"name":"other"}]}""")
                        "/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[]}""")
                        "/dashboard/api/cron/jobs?profile=worker"->json("""[{"id":"job-fixture","name":"History fixture","profile":"worker","state":"paused","enabled":false,"schedule_display":"Every hour"}]""")
                        "/dashboard/api/cron/jobs/job-fixture?profile=worker"->json("""{"id":"job-fixture","name":"History fixture","profile":"worker","state":"paused","enabled":false,"schedule_display":"Every hour"}""")
                        "/dashboard/api/cron/jobs/job-fixture/runs?limit=20&profile=worker"->{
                            entered.countDown()
                            if(delayed && !release.await(15,TimeUnit.SECONDS)) return json("{}").setResponseCode(504)
                            json(history)
                        }
                        else->if(request.path.orEmpty().startsWith("/inference/api/jobs?")) json("""{"jobs":[]}""") else json("{}").setResponseCode(404)
                    }
                }
            }
        }
        override fun close() {release.countDown();server.close()}
        fun verifyReads() {
            assertEquals(0,writes.get())
            val runRequests=requests.filter {it.path.orEmpty().contains("/runs?")}
            assertTrue(runRequests.isNotEmpty())
            runRequests.forEach {
                assertEquals("/dashboard/api/cron/jobs/job-fixture/runs?limit=20&profile=worker",it.path)
                assertEquals("Bearer management-runs-fixture",it.getHeader("Authorization"))
                assertNull(it.getHeader("Cookie"));assertEquals(0L,it.bodySize)
            }
            requests.filter {it.path.orEmpty().startsWith("/dashboard/")}.forEach {assertEquals("Bearer management-runs-fixture",it.getHeader("Authorization"))}
        }
    }

    private fun open(f:Fixture,multilingual:Boolean=false) {
        compose.waitUntil(10000) {!api.connecting.value}
        compose.runOnUiThread {
            api.configure(f.server.url("inference/").toString(),"inference-runs-fixture",true)
            api.updatePreferences(api.preferences.value.copy(chinese=multilingual,pinyin=multilingual))
        }
        compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==multilingual}
        val scope=api.profileScope.value
        app.credentials.saveManagement(scope,buildJsonObject {
            put("url",f.server.url("dashboard/").toString());put("profile","worker");put("debugHttp",true)
            put("tokens",ManagementTokens("management-runs-fixture","refresh-fixture","password","fixture-user",4102444800).json())
        })
        compose.runOnUiThread {management.bind(scope)}
        compose.waitUntil(5000) {management.state.value.signedIn}
        compose.runOnUiThread {management.refresh()}
        compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
        compose.runOnUiThread {compose.activity.setContent {
            val preferences by api.preferences.collectAsStateWithLifecycle()
            HermesAppTheme(preferences) {ManagementEntry(api,management)}
        }}
        compose.onNodeWithTag("management-open").performClick()
        compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-cron-open"))
        compose.onNodeWithTag("host-cron-open").performClick()
        compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.loaded}
        compose.onNodeWithTag("host-cron-list").performScrollToNode(hasTestTag("host-cron-detail-job-fixture"))
        compose.onNodeWithTag("host-cron-detail-job-fixture").performClick()
        compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.detail!=null}
    }
    private fun read() {
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-runs-load"))
        compose.onNodeWithTag("host-cron-runs-load").performClick()
    }
    private fun waitHistory()=compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.runs!=null}
    private fun firstRow() {
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-run-reveal-0"))
    }
    private fun screenshot(name:String) {
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }

    private fun reviewNewTiming() {
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-timing-open"))
        compose.onNodeWithTag("host-cron-timing-open").performClick()
        compose.waitUntil(10000) { !management.state.value.busy && management.state.value.cron.timingEditor != null }
        compose.onNodeWithTag("cron-timing-review").assertIsNotEnabled()
        compose.onNodeWithTag("host-cron-timing").performScrollToNode(hasTestTag("cron-timing-schedule"))
        compose.onNodeWithTag("cron-timing-schedule").performTextReplacement("every 2h")
        compose.onNodeWithTag("cron-timing-review").performClick()
        compose.waitUntil(10000) { !management.state.value.busy && management.state.value.cron.timingEditor?.review != null }
    }

    private fun actionOnRunOnceReview(tag: String) {
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-run-once-open"))
        compose.onNodeWithTag("host-cron-run-once-open").performClick()
        compose.waitUntil(10000) { !management.state.value.busy && management.state.value.cron.runOnce != null }
        val review = compose.onNodeWithTag("host-cron-run-once-review")
        review.performScrollToNode(hasText("fixture-branch"))
        compose.onNodeWithText("fixture-branch").assertIsDisplayed()
        val task = "Write the fixture summary without changing the seed."
        review.performScrollToNode(hasText(task))
        compose.onNodeWithText(task).assertIsDisplayed()
        review.performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsEnabled().performClick()
    }

    @Test fun reviewedRunOnceRecoversLostReplyAndWaitsForNativeCompletion() {
        Fixture(ledgerEnabled = true).use { f ->
            f.loseRunOnceReply = true
            open(f, multilingual = true)
            actionOnRunOnceReview("host-cron-run-once-approve")
            compose.waitUntil(15000) { !management.state.value.busy && management.receipts.value.any {
                it.action == CronRunOnceProtocol.Action && it.state == "unknown"
            } }
            val row = management.receipts.value.single { it.action == CronRunOnceProtocol.Action }
            assertTrue(row.resultSummary.isEmpty())
            assertNull(management.prepareManagementUncertainty(row.key))
            runBlocking {
                app.database.dao().reviewManagementOperation(row.key, row.resourceScope, row.url, row.projectHome)
                assertEquals("unknown", app.database.dao().managementOperation(row.key)?.state)
            }
            compose.onNodeWithTag("host-cron-detail-close").performClick()
            val original = "management-observe-" + row.key
            fun observe() {
                compose.onNodeWithTag("host-cron-list").performScrollToNode(hasTestTag(original))
                compose.onNodeWithTag(original).performClick()
            }
            observe()
            compose.waitUntil(15000) { !management.state.value.busy && management.receipts.value.any {
                it.key == row.key && it.state == "unknown" && CronRunOnceProtocol.savedSummary(it)?.status == CronExecutionStatus.Running
            } }
            observe()
            compose.waitUntil(15000) { !management.state.value.busy && management.receipts.value.any {
                it.key == row.key && it.state == "confirmed" && CronRunOnceProtocol.savedSummary(it)?.status == CronExecutionStatus.Completed
            } }
            assertEquals(1, f.runOnceConfirms.get())
            assertEquals(2, f.runOnceObserves.get())
            assertEquals(0, f.writes.get())
            assertEquals(0, f.unexpectedSocketFrames.get())
        }
    }

    @Test fun reviewedRunOnceDefersAndDeniesOnTheReviewSocketWithoutSubmitting() {
        Fixture(ledgerEnabled = true).use { f ->
            open(f)
            actionOnRunOnceReview("host-cron-run-once-defer")
            compose.waitUntil(10000) { !management.state.value.busy && management.state.value.notice == Words.HostCronRunOnceDeferred }
            actionOnRunOnceReview("host-cron-run-once-deny")
            compose.waitUntil(10000) { !management.state.value.busy && management.state.value.notice == Words.HostCronRunOnceDenied }
            assertEquals(listOf("defer", "deny"), f.runOnceDecisions.toList())
            assertEquals(2, f.runOncePrepares.get())
            assertEquals(0, f.runOnceConfirms.get())
            assertTrue(management.receipts.value.none { it.action == CronRunOnceProtocol.Action })
            assertEquals(0, f.writes.get())
            assertEquals(0, f.unexpectedSocketFrames.get())
        }
    }

    private fun openProjectInbox(f: Fixture, multilingual: Boolean = false) {
        open(f, multilingual)
        compose.onNodeWithTag("host-cron-detail-close").performClick()
        compose.onNodeWithTag("host-cron-list").performScrollToNode(hasTestTag("host-cron-project-open"))
        compose.onNodeWithTag("host-cron-project-open").performClick()
        compose.waitUntil(10000) { !management.state.value.busy && management.state.value.cron.projectInbox != null }
    }

    private fun projectDecision(action: String) {
        val button = "project-review-" + projectOccurrence
        compose.onNodeWithTag("host-cron-project-list").performScrollToNode(hasTestTag(button))
        compose.onNodeWithTag(button).performClick()
        compose.waitUntil(10000) { !management.state.value.busy && management.state.value.cron.projectReview != null }
        val list = compose.onNodeWithTag("host-cron-project-review")
        list.performScrollToNode(hasText("reviewed-branch"))
        compose.onNodeWithText("reviewed-branch").assertIsDisplayed()
        val commit = "a".repeat(40)
        list.performScrollToNode(hasText(commit))
        compose.onNodeWithText(commit).assertIsDisplayed()
        val task = "Update notes in the verified fixture project."
        list.performScrollToNode(hasText(task))
        compose.onNodeWithText(task).assertIsDisplayed()
        val tag = "host-cron-project-" + action
        list.performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsEnabled().performClick()
    }

    private fun observeProject(row: ManagementOperation) {
        val tag = "management-observe-" + row.key
        compose.onNodeWithTag("host-cron-project-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsEnabled().performClick()
    }

    @Test fun projectApprovalsRecoverOriginalAfterDisconnectAndKeepUnknownHeld() {
        Fixture(ledgerEnabled = true).use { f ->
            f.loseProjectReply = true
            openProjectInbox(f, multilingual = true)
            projectDecision("approve")
            compose.waitUntil(15000) { !management.state.value.busy && management.receipts.value.any {
                it.action == CronProjectProtocol.Action && it.state == "unknown"
            } }
            val row = management.receipts.value.single { it.action == CronProjectProtocol.Action }
            assertEquals(projectOccurrence, CronProjectProtocol.storedOccurrence(row).id)
            assertNull(management.prepareManagementUncertainty(row.key))
            runBlocking {
                app.database.dao().reviewManagementOperation(row.key, row.resourceScope, row.url, row.projectHome)
                assertEquals("unknown", app.database.dao().managementOperation(row.key)?.state)
            }
            observeProject(row)
            compose.waitUntil(15000) { !management.state.value.busy && management.receipts.value.any {
                it.key == row.key && it.state == "unknown" && CronProjectProtocol.savedSummary(it)?.status == CronExecutionStatus.Unknown
            } }
            val button = "project-review-" + projectOccurrence
            compose.onNodeWithTag("host-cron-project-list").performScrollToNode(hasTestTag(button))
            compose.onNodeWithTag(button).assertIsNotEnabled()
            observeProject(row)
            compose.waitUntil(15000) { !management.state.value.busy && management.receipts.value.any {
                it.key == row.key && it.state == "confirmed" && CronProjectProtocol.savedSummary(it)?.status == CronExecutionStatus.Completed
            } }
            assertEquals(listOf("approve"), f.projectDecisions.toList())
            assertEquals(2, f.projectReads.get())
            assertEquals(0, f.writes.get())
            assertEquals(0, f.unexpectedSocketFrames.get())
        }
    }

    @Test fun projectApprovalsDeferAndDenyTheSameOccurrence() {
        Fixture(ledgerEnabled = true).use { f ->
            openProjectInbox(f)
            projectDecision("defer")
            compose.waitUntil(10000) { !management.state.value.busy && management.state.value.notice == Words.HostCronProjectDeferred }
            assertTrue(management.receipts.value.none { it.action == CronProjectProtocol.Action })
            projectDecision("deny")
            compose.waitUntil(15000) { !management.state.value.busy && management.receipts.value.any {
                it.action == CronProjectProtocol.Action && it.state == "unknown"
            } }
            val row = management.receipts.value.single { it.action == CronProjectProtocol.Action }
            observeProject(row)
            compose.waitUntil(15000) { !management.state.value.busy && management.receipts.value.any {
                it.key == row.key && it.state == "confirmed" && CronProjectProtocol.savedSummary(it)?.denied == true
            } }
            assertEquals(listOf("defer", "deny"), f.projectDecisions.toList())
            assertEquals(1, f.projectReads.get())
            assertEquals(0, f.writes.get())
            assertEquals(0, f.unexpectedSocketFrames.get())
        }
    }

    @Test fun reviewedTimingRequiresExplicitSaveAndRefusesChangedDigest() {
        Fixture(ledgerEnabled = true).use { f ->
            f.changeReview = true
            open(f, multilingual = true)
            reviewNewTiming()
            compose.onNodeWithTag("cron-timing-apply").assertIsDisplayed().assertIsEnabled()
            assertEquals(1, f.timingPrepares.get())
            assertEquals(1, f.timingReviews.get())
            assertEquals(0, f.timingApplies.get())
            compose.onNodeWithTag("cron-timing-apply").performClick()
            compose.waitUntil(10000) { !management.state.value.busy && management.state.value.error == Words.ManagementReviewChanged }
            assertEquals(2, f.timingReviews.get())
            assertEquals(0, f.timingApplies.get())
            assertEquals(0, f.timingObserves.get())
            assertEquals(0, f.writes.get())
            assertEquals(0, f.unexpectedSocketFrames.get())
        }
    }

    @Test fun lostTimingReplyCanBeCheckedOnlyByTheOriginalReceipt() {
        Fixture(ledgerEnabled = true).use { f ->
            f.loseEditReply = true
            open(f)
            reviewNewTiming()
            compose.onNodeWithTag("cron-timing-apply").performClick()
            compose.waitUntil(15000) {
                !management.state.value.busy && management.receipts.value.any {
                    it.action == CronTimingProtocol.Action && it.state == "unknown"
                }
            }
            val row = management.receipts.value.single { it.action == CronTimingProtocol.Action && it.state == "unknown" }
            assertNull(management.prepareManagementUncertainty(row.key))
            runBlocking {
                app.database.dao().reviewManagementOperation(row.key, row.resourceScope, row.url, row.projectHome)
                assertEquals("unknown", app.database.dao().managementOperation(row.key)?.state)
            }
            compose.onNodeWithTag("cron-timing-back").performClick()
            compose.onNodeWithTag("host-cron-detail-close").performClick()
            val observe = "management-observe-" + row.key
            compose.onNodeWithTag("host-cron-list").performScrollToNode(hasTestTag(observe))
            compose.onNodeWithTag(observe).performClick()
            compose.waitUntil(15000) {
                !management.state.value.busy && management.receipts.value.any { it.key == row.key && it.state == "confirmed" }
            }
            assertEquals(Words.HostCronTimingSaved, management.state.value.notice)
            assertEquals(1, f.timingApplies.get())
            assertEquals(1, f.timingObserves.get())
            assertEquals(0, f.writes.get())
            assertEquals(0, f.unexpectedSocketFrames.get())
        }
    }

    @Test fun scopedHistoryRequiresRevealAndRefreshRemovesPrivateText() {
        Fixture().use {f ->
            open(f,multilingual=true);read();waitHistory();firstRow()
            compose.onNodeWithText("Private run preview fixture").assertDoesNotExist()
            compose.onNodeWithText("recorded-model-fixture",substring=true).assertDoesNotExist()
            compose.onNodeWithTag("host-cron-run-reveal-0").assertIsDisplayed().performClick()
            compose.onNodeWithText("Private run title fixture").assertExists()
            compose.onNodeWithText("Private run preview fixture").assertExists()
            compose.onNodeWithText("Private run preview fixture").performScrollTo().assertIsDisplayed()
            screenshot("cron-runs-revealed")
            read();waitHistory();firstRow()
            compose.onNodeWithText("Private run preview fixture").assertDoesNotExist()
            assertEquals(listOf(false,true),management.state.value.cron.runs!!.rows.map {it.scriptOutput})
            screenshot("cron-runs-hidden")
            compose.onNodeWithTag("host-cron-detail-close").performClick()
            assertNull(management.state.value.cron.runs)
            compose.runOnUiThread {management.bind("unconfigured")}
            compose.waitUntil(5000) {management.state.value.apiScope=="unconfigured"}
            assertNull(management.state.value.cron.detail);assertTrue(management.state.value.cron.jobs.isEmpty());f.verifyReads()
        }
    }

    @Test fun foreignAndUnscopedRowsClearPreviouslyRevealedHistory() {
        Fixture().use {f ->
            open(f);read();waitHistory();firstRow()
            compose.onNodeWithTag("host-cron-run-reveal-0").assertIsDisplayed().performClick()
            compose.onNodeWithText("Private run preview fixture").assertExists()
            val foreign="""{"limit":20,"runs":[{"id":"foreign-run-fixture","profile":"other","source":"cron","preview":"Foreign private fixture"}]}"""
            val unscoped="""{"limit":20,"runs":[{"id":"cron_output:job-fixture:exec:0","source":"cron_output","preview":"Unscoped private fixture"}]}"""
            for(payload in listOf(foreign,unscoped)) {
                f.history=payload;read()
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.HostCronRunsInvalid}
                assertNull(management.state.value.cron.runs)
                compose.onNodeWithText("Private run preview fixture").assertDoesNotExist()
                compose.onNodeWithText("Foreign private fixture").assertDoesNotExist()
                compose.onNodeWithText("Unscoped private fixture").assertDoesNotExist()
            }
            screenshot("cron-runs-owner-refused");f.verifyReads()
        }
    }

    @Test fun delayedReplyCannotRestoreClosedOrChangedScope() {
        Fixture().use {f ->
            open(f);f.delayed=true;read()
            assertTrue(f.entered.await(5,TimeUnit.SECONDS));assertTrue(management.state.value.busy)
            compose.onNodeWithTag("host-cron-detail-close").performClick()
            assertNull(management.state.value.cron.detail);assertNull(management.state.value.cron.runs)
            f.release.countDown()
            compose.waitUntil(10000) {!management.state.value.busy}
            assertNull(management.state.value.cron.detail);assertNull(management.state.value.cron.runs)
            compose.runOnUiThread {management.bind("unconfigured")}
            compose.waitUntil(5000) {management.state.value.apiScope=="unconfigured"}
            assertNull(management.state.value.identity);assertNull(management.state.value.cron.runs)
            compose.onNodeWithText("Private run preview fixture").assertDoesNotExist();f.verifyReads()
        }
    }

    private fun openAttempts(fixture: Fixture, multilingual: Boolean = false) {
        open(fixture, multilingual)
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-ledger-load"))
        compose.onNodeWithTag("host-cron-ledger-load").performClick()
        compose.waitUntil(10000) { !management.state.value.busy && management.state.value.cron.executionHistory != null }
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-attempt-output-0"))
        assertEquals(0, fixture.outputReads.get())
        compose.onNodeWithText(outputText).assertDoesNotExist()
    }

    private fun openOutput() {
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-attempt-output-0"))
        compose.onNodeWithTag("host-cron-attempt-output-0").performClick()
    }

    @Test fun exactOutputIsExplicitAndForeignExecutionCannotRevealText() {
        Fixture(ledgerEnabled = true).use { fixture ->
            openAttempts(fixture, multilingual = true)
            openOutput()
            compose.waitUntil(10000) { !management.state.value.busy && management.state.value.cron.output?.preview != null }
            compose.onNodeWithTag("host-cron-output").performScrollToNode(hasTestTag("host-cron-output-text"))
            compose.onNodeWithText(outputText).assertIsDisplayed()
            compose.onNodeWithTag("host-cron-output-close").assertIsDisplayed().performClick()
            compose.onNodeWithText(outputText).assertDoesNotExist()

            fixture.wrongExecution = true
            openOutput()
            compose.waitUntil(10000) { !management.state.value.busy && management.state.value.error == Words.HostCronOutputInvalid }
            compose.onNodeWithTag("host-cron-output-text").assertDoesNotExist()
            assertEquals(1, fixture.historyReads.get())
            assertEquals(2, fixture.outputReads.get())
            assertEquals(3, fixture.socketCount.get())
            assertEquals(0, fixture.writes.get() + fixture.unexpectedSocketFrames.get())
            compose.onNodeWithTag("host-cron-output-close").performClick()
            assertNull(management.state.value.cron.output)
        }
    }

    @Test fun closingOrBackgroundingOutputDiscardsItsPrivateContent() {
        Fixture(ledgerEnabled = true).use { fixture ->
            openAttempts(fixture)
            fixture.holdOutput = true
            openOutput()
            assertTrue(fixture.outputEntered.await(5, TimeUnit.SECONDS))
            assertTrue(management.state.value.busy)
            compose.onNodeWithTag("host-cron-output-close").assertIsDisplayed().performClick()
            fixture.heldOutput.getAndSet(null)?.invoke()
            compose.waitUntil(5000) { !management.state.value.busy && management.state.value.cron.output == null }
            compose.onNodeWithText(outputText).assertDoesNotExist()

            fixture.holdOutput = false
            openOutput()
            compose.waitUntil(10000) { !management.state.value.busy && management.state.value.cron.output?.preview != null }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            try {
                assertNull(management.state.value.cron.output)
            } finally {
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            }
            compose.onNodeWithText(outputText).assertDoesNotExist()
            assertEquals(2, fixture.outputReads.get())
            assertEquals(0, fixture.writes.get() + fixture.unexpectedSocketFrames.get())
        }
    }

}
