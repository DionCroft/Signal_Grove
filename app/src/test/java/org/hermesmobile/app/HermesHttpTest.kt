package org.hermesmobile.app

import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HermesHttpTest {
    private fun client(server: MockWebServer,prefix: String="p/mobile/")=OkHttpHermesApiClient(ServerConfig(server.url(prefix).toString(),"test-fixture-token",true),true)
    @Test fun preservesProfilePrefixAndBearerAuth()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("""{"model":"hermes","features":{"session_resources":true,"session_chat":true}}"""))
            assertTrue(client(server).capabilities().sessions)
            val request=server.takeRequest(2,TimeUnit.SECONDS)!!
            assertEquals("/p/mobile/v1/capabilities",request.path)
            assertEquals("Bearer test-fixture-token",request.getHeader("Authorization"))
        }
    }
    @Test fun onlyMissingCapabilitiesFallsBackToModels()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setResponseCode(404));server.enqueue(MockResponse().setBody("""{"data":[{"id":"legacy"}]}"""))
            assertEquals("legacy",client(server).capabilities().model);assertEquals(2,server.requestCount)
        }
    }
    @Test fun authFailureNeverFallsBack()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setResponseCode(401))
            try {client(server).capabilities();fail("Expected authentication error")} catch(e:HermesHttpException) {assertEquals(401,e.code)}
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun redirectCannotForwardCredential()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","https://untrusted.invalid/"))
            try {client(server).capabilities();fail("Expected redirect rejection")} catch(e:HermesHttpException) {assertEquals(302,e.code)}
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun runSubmissionReusesPersistedKey()=runBlocking {
        MockWebServer().use {server ->
            repeat(2) {server.enqueue(MockResponse().setBody("""{"run_id":"r1"}"""))}
            val api=client(server);repeat(2) {assertEquals("r1",api.submitRun("api_1","Hello","same-turn-key"))}
            val a=server.takeRequest();val b=server.takeRequest()
            assertEquals("same-turn-key",a.getHeader("Idempotency-Key"));assertEquals(a.body.readUtf8(),b.body.readUtf8())
        }
    }
    @Test fun fragmentedUnicodeStreamEndsOnlyWithActualTerminalEvent()=runBlocking {
        MockWebServer().use {server ->
            val fixture="""
                : heartbeat

                event: assistant.delta
                data: {"delta":"你好","run_id":"r1"}

                event: assistant.completed
                data: {"content":"你好"}

                event: run.cancelled
                data: {"run_id":"r1"}


            """.trimIndent()
            server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(fixture).throttleBody(3,1,TimeUnit.MILLISECONDS))
            val updates=client(server).stream("v1/runs/r1/events").toList()
            assertEquals("你好",updates.first().delta);assertNull(updates[1].status);assertEquals("cancelled",updates.last().status)
        }
    }
    @Test fun truncatedStreamHasNoInventedSuccess()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody("""event: assistant.delta
data: {"delta":"partial"}


"""))
            val updates=client(server).stream("api/sessions/s/chat/stream").toList()
            assertTrue(updates.none {it.status=="completed"})
        }
    }
    @Test fun releaseRejectsCleartextEvenIfDebugFlagRequested() {
        try {OkHttpHermesApiClient(ServerConfig("http://localhost:8642","fixture",true),false);fail("HTTP must be rejected")} catch(_:IllegalArgumentException) {}
    }
    @Test fun rejectsEmbeddedCredentialsAndQuery() {
        listOf("https://user:secret@example.invalid","https://example.invalid/?token=x").forEach {url ->
            try {OkHttpHermesApiClient(ServerConfig(url,"fixture"));fail("Unsafe URL")} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun partitionsHistoryByEndpointAndCredential() {
        assertNotEquals(ServerConfig("https://a.invalid","a").scope,ServerConfig("https://a.invalid","b").scope)
        assertEquals(ServerConfig("https://a.invalid/","a").scope,ServerConfig("https://a.invalid","a").scope)
    }
}
