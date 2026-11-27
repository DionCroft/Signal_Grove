package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.HermesViewModel
import org.hermesmobile.app.ui.ManagementViewModel
import org.hermesmobile.app.ui.Words
import org.hermesmobile.app.ui.localized
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TodayLiveApprovalUiTest {
    @get:Rule(order = 0) val credentials = CredentialFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HermesApplication
    private val vm get() = ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get() = ViewModelProvider(compose.activity)[ManagementViewModel::class.java]

    private class GatewayFixture : AutoCloseable {
        val server = MockWebServer()
        val online = AtomicBoolean(true)
        val requestId = AtomicReference("desktop-request")
        val approvalSupported = AtomicBoolean(true)
        val calls = CopyOnWriteArrayList<JsonObject>()
        val refused = CopyOnWriteArrayList<String>()
        val routes = CopyOnWriteArrayList<String>()
        val tokens = ManagementTokens("today-management-fixture", "unused-fixture", "password", "today-owner", 4102444800)
        private val tickets = ConcurrentHashMap.newKeySet<String>()
        val address get() = server.url("/").toString()
        val dashboard get() = server.url("/dashboard/").toString()
        private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

        init {
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl?.encodedPath.orEmpty()
                    routes += path
                    if (path.startsWith("/dashboard/api/")) {
                        val expected = if (path == "/dashboard/api/ws") null else "Bearer ${tokens.access}"
                        if (request.getHeader("Authorization") != expected || request.getHeader("Cookie") != null) {
                            refused += "auth:$path"
                            return json("{}").setResponseCode(401)
                        }
                    }
                    return when (path) {
                        "/v1/capabilities" -> if (online.get()) json(
                            """{"features":{"session_resources":true,"session_chat":true}}""")
                        else json("{}").setResponseCode(503)
                        "/api/jobs" -> json("""{"jobs":[]}""")
                        "/api/sessions" -> json("""{"data":[],"has_more":false}""")
                        "/dashboard/api/auth/me" -> json("""{"user_id":"today-owner","provider":"password","display_name":"Owner fixture"}""")
                        "/dashboard/api/profiles" -> json("""{"profiles":[{"name":"default","path":"/fixture/default"},{"name":"worker","path":"/fixture/worker"}]}""")
                        "/dashboard/api/mcp/servers" -> json("""{"servers":[]}""")
                        "/dashboard/api/auth/ws-ticket" -> {
                            val ticket = "today-ticket-" + UUID.randomUUID().toString().replace("-", "")
                            tickets += ticket
                            json("""{"ticket":"$ticket","ttl_seconds":30}""")
                        }
                        "/dashboard/api/webapp/socket-ticket" -> json("{}").setResponseCode(404)
                        "/dashboard/api/ws" -> {
                            val subprotocols = request.getHeader("Sec-WebSocket-Protocol").orEmpty().split(',').map(String::trim)
                            if (subprotocols.size != 2 || subprotocols[0] != "hermes-gateway-v1" ||
                                !tickets.remove(subprotocols[1].removePrefix("hermes-gateway-ticket."))) {
                                refused += "ticket"
                                json("{}").setResponseCode(403)
                            } else MockResponse().withWebSocketUpgrade(listener())
                                .setHeader("Sec-WebSocket-Protocol", "hermes-gateway-v1")
                        }
                        else -> {
                            refused += path
                            json("{}").setResponseCode(404)
                        }
                    }
                }
            }
        }

        private fun listener() = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"replay_epoch":"fixture-epoch","session_authority":true}}}""")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val call = Json.parseToJsonElement(text).jsonObject
                calls += call
                val method = call.getValue("method").jsonPrimitive.content
                val id = call.getValue("id")
                val body = when (method) {
                    "runtime.describe" -> """"result":{"instance_id":"today-runtime-fixture","profile_id":"/fixture/default","authority_epoch":4,"capabilities":["durable-admission-v1","event-replay-v1"],"session_create":{"sources":["cli"],"parameters":["title"]}}"""
                    "session.list" -> """"result":{"scope":"live","sessions":[{"id":"desktop","session_id":"desktop","title":"Desktop fixture conversation","source":"cli","message_count":7,"running":false}]}"""
                    "approval.pending" -> if (approvalSupported.get()) {
                        """"result":{"approvals":[{"request_id":"${requestId.get()}","command":"fixture read","description":"Read fixture","choices":["once","deny"],"allow_permanent":false}]}"""
                    } else """"error":{"code":-32601,"message":"Method not found"}"""
                    else -> {
                        refused += method
                        """"error":{"code":-32601,"message":"Method not found"}"""
                    }
                }
                val reply = """{"jsonrpc":"2.0","id":$id,$body}"""
                check(Json.parseToJsonElement(reply).jsonObject["id"] == call["id"])
                if (!ws.send(reply)) refused += "response:$method"
            }
        }

        override fun close() = server.close()
    }

    @After fun restoreDemo() {
        compose.waitUntil(15_000) { !vm.connecting.value && !vm.controlBusy.value }
        compose.runOnUiThread { vm.forgetServer() }
        compose.waitUntil(15_000) { vm.preferences.value.demo && !vm.connecting.value }
    }

    @Test fun authenticatedInboxSeparatesCachedUnsupportedAndCurrentApprovals() {
        GatewayFixture().use { fixture ->
            val token = credentials.uniqueToken("today-live")
            val address = fixture.address
            compose.runOnUiThread { vm.configure(address, token, true) }
            compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "connected" }
            val scope = vm.profileScope.value
            app.credentials.saveManagement(scope, buildJsonObject {
                put("url", fixture.dashboard)
                put("profile", "default")
                put("debugHttp", true)
                put("tokens", fixture.tokens.json())
            })
            compose.runOnUiThread { management.bind("demo"); management.bind(scope) }
            compose.waitUntil(15_000) { management.state.value.signedIn }

            val verify = compose.onNodeWithTag("today-live-approval-verify")
            val list = compose.onNodeWithTag("today-live-approval-list")
            val review = compose.onNodeWithText(Words.SharedApprovalReview.localized(vm.preferences.value))
            val reason = compose.onNodeWithTag("today-live-approval-state")
            fun waitForList() {
                try {
                    compose.waitUntil(15_000) {
                        compose.onAllNodesWithTag("today-live-approval-list").fetchSemanticsNodes().isNotEmpty()
                    }
                } catch (failure: ComposeTimeoutException) {
                    val state = management.state.value
                    val outcome = compose.onAllNodesWithTag("today-live-approval-state").fetchSemanticsNodes().map { it.config }
                    val methods = fixture.calls.map { it["method"] }
                    throw AssertionError("Inbox did not load. Profile: ${state.profile}, signed in: ${state.signedIn}, " +
                        "identity verified: ${state.identity != null}, busy: ${state.busy}, error: ${state.error}, " +
                        "UI: $outcome, routes: ${fixture.routes}, RPC: $methods, refused: ${fixture.refused}", failure)
                }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("today-live-approval-verify").fetchSemanticsNodes().isNotEmpty()
            }
            verify.performScrollTo().performClick()
            waitForList()
            list.performScrollTo().performClick()
            compose.onNodeWithText("desktop-request").assertIsDisplayed()
            review.assertIsEnabled()

            compose.runOnUiThread { vm.controlBusy.value = true }
            try {
                review.assertIsNotEnabled()
                assertTrue(compose.onAllNodesWithText(Words.TodayApprovalCached.localized(vm.preferences.value))
                    .fetchSemanticsNodes().isNotEmpty())
            } finally {
                compose.runOnUiThread { vm.controlBusy.value = false }
            }
            fixture.online.set(false)
            compose.runOnUiThread { vm.refresh() }
            compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "offline" }
            compose.onNodeWithText("desktop-request").assertIsDisplayed()
            review.assertIsNotEnabled()

            fixture.requestId.set("reconnected-request")
            fixture.online.set(true)
            compose.runOnUiThread { vm.refresh() }
            compose.waitUntil(15_000) { !vm.connecting.value && vm.connectionStatus.value == "connected" }
            compose.waitUntil(15_000) { compose.onNodeWithText("reconnected-request").isDisplayed() }
            compose.onNodeWithText("desktop-request").assertDoesNotExist()
            review.assertIsEnabled()
            compose.onNodeWithText(Words.Close.localized(vm.preferences.value)).performClick()
            val originalPendingReads = fixture.calls.count { it["method"] == JsonPrimitive("approval.pending") }

            compose.runOnUiThread { management.chooseProfile("worker") }
            val unavailable = Words.ViewerProfileTransportUnavailable.localized(vm.preferences.value)
            compose.waitUntil(15_000) {
                !management.state.value.busy && management.state.value.profile == "worker" &&
                    compose.onAllNodesWithText(unavailable).fetchSemanticsNodes().isNotEmpty()
            }
            reason.assertTextContains(unavailable)
            list.assertDoesNotExist()
            assertEquals(originalPendingReads, fixture.calls.count { it["method"] == JsonPrimitive("approval.pending") })

            fixture.approvalSupported.set(false)
            compose.runOnUiThread { management.chooseProfile("default") }
            val unsupported = Words.ViewerUnsupported.localized(vm.preferences.value)
            compose.waitUntil(15_000) {
                !management.state.value.busy && management.state.value.profile == "default" &&
                    compose.onAllNodesWithText(unsupported).fetchSemanticsNodes().isNotEmpty()
            }
            reason.assertTextContains(unsupported)
            list.assertDoesNotExist()
            assertTrue(fixture.refused.toString(), fixture.refused.isEmpty())
            assertEquals(setOf("runtime.describe", "session.list", "approval.pending"),
                fixture.calls.map { it.getValue("method").jsonPrimitive.content }.toSet())
            assertTrue(fixture.calls.filter { it["method"] == JsonPrimitive("approval.pending") }.all {
                it["params"] == buildJsonObject { put("session_id", "desktop") }
            })
        }
    }
}
