package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.data.ServerConfig
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class HistoryReadErrorTest {
    private val english=AppPreferences(chinese=false,pinyin=false)
    @Test fun documentedMissingSessionExplainsPreservedMessages() {
        val message=historyReadError(HermesHttpException(404,"session_not_found"),english)
        assertEquals("This chat is unavailable on this Hermes connection. Saved messages were kept.",message)
        assertFalse(message.contains("feature is unavailable"))
    }
    @Test fun otherStatusesAndCodesKeepTheirActualFailure() {
        for(error in listOf(HermesHttpException(404),HermesHttpException(405,"session_not_found"),
            HermesHttpException(403,"session_not_found"),HermesHttpException(500,"session_not_found"),
            HermesHttpException(404,"session_not_found_elsewhere"))) {
            assertEquals(connectionError(error),historyReadError(error,english))
            assertNotEquals(Words.HistoryUnavailable.en,historyReadError(error,english))
        }
    }
    @Test fun newExplanationRespectsIndependentLanguagePreferences() {
        val error=HermesHttpException(404,"session_not_found")
        val both=historyReadError(error,AppPreferences(chinese=true,pinyin=true))
        assertTrue(both.contains(Words.HistoryUnavailable.en))
        assertTrue(both.contains(Words.HistoryUnavailable.zh))
        assertTrue(both.contains(Words.HistoryUnavailable.pin))
        assertFalse(historyReadError(error,AppPreferences(chinese=true,pinyin=false)).contains(Words.HistoryUnavailable.pin))
        assertFalse(historyReadError(error,AppPreferences(chinese=false,pinyin=true)).contains(Words.HistoryUnavailable.zh))
    }
    @Test fun realMissingHistoryReadNeverDisplaysServerTextOrRetries()=runBlocking {
        MockWebServer().use {server->
            val secret="private-fixture-path-and-server-text"
            server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type","application/json")
                .setBody("""{"error":{"code":"session_not_found","message":"$secret"}}"""))
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/fixture/").toString(),"history-fixture-token",true),true)
            val failure=runCatching {api.history("saved_id")}.exceptionOrNull()
            assertTrue(failure is HermesHttpException)
            val message=historyReadError(failure!!,english)
            assertEquals(Words.HistoryUnavailable.en,message)
            assertFalse(message.contains(secret));assertFalse(message.contains("saved_id"))
            assertEquals(1,server.requestCount)
            val request=server.takeRequest()
            assertEquals("GET",request.method)
            assertEquals("/p/fixture/api/sessions/saved_id/messages?inline_images=false&limit=500&offset=0&order=oldest",request.path)
            assertEquals("Bearer history-fixture-token",request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
        }
    }
    @Test fun invalidIdentifiersRemainLocalRefusalsWithoutHistoryRequests()=runBlocking {
        MockWebServer().use {server->
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/fixture/").toString(),"history-fixture-token",true),true)
            for(id in listOf("saved/id",".","..","bad?query")) {
                val failure=runCatching {api.history(id)}.exceptionOrNull()
                assertTrue(failure is IllegalArgumentException)
                assertEquals("Unsupported server identifier",historyReadError(failure!!,english))
            }
            assertEquals(0,server.requestCount)
        }
    }
}
