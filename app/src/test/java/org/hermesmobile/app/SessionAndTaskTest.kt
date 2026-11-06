package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SessionAndTaskTest {
    private fun client(server: MockWebServer)=OkHttpHermesApiClient(ServerConfig(server.url("p/work/").toString(),"fixture",true),true)
    @Test fun sessionPaginationPreservesOffsetWithoutHidingOtherOrigins()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"api_a","title":"Saved","last_active":123,"source":"api_server"}],"has_more":true}"""))
            val p=client(server).sessionPage(50,50)
            assertEquals(100,p.nextOffset);assertTrue(p.hasMore);assertEquals(123000L,p.rows.first().updatedAt)
            assertEquals("/p/work/api/sessions?limit=50&offset=50",server.takeRequest().path)
        }
    }
    @Test fun renameAndDeleteUseDocumentedMethodsAndProfilePrefix()=runBlocking {
        MockWebServer().use {server ->
            repeat(2) {server.enqueue(MockResponse().setBody("{}"))}
            val api=client(server);api.renameSession("api_1","New title");api.deleteSession("api_1")
            val a=server.takeRequest();assertEquals("PATCH",a.method);assertEquals("/p/work/api/sessions/api_1",a.path);assertTrue(a.body.readUtf8().contains("New title"))
            assertEquals("DELETE",server.takeRequest().method)
        }
    }
    @Test fun historyPreservesStableIdsAndCompressionContinuation()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("""{"session_id":"api_tip","data":[{"id":7,"role":"assistant","content":"Corrected reply","timestamp":123},{"id":8,"role":"user","content":[{"type":"text","text":"你好"}],"timestamp":124}]}"""))
            val history=client(server).history("api_root")
            assertEquals("api_tip",history.sessionId);assertEquals(listOf("7","8"),history.rows.map {it.id});assertEquals("你好",history.rows.last().content)
        }
    }
    @Test fun structuredJobsIncludeScheduleDisabledStateAndRunTimes()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("""{"jobs":[{"id":"j1","name":"Report","prompt":"Review","schedule":{"display":"Every morning","cron":"0 9 * * *"},"enabled":false,"next_run_at":"later","last_run_at":"earlier"}]}"""))
            val job=client(server).jobs().single()
            assertEquals("Every morning",job.schedule);assertFalse(job.enabled);assertEquals("paused",job.state);assertEquals("later",job.nextRun);assertEquals("earlier",job.lastRun)
        }
    }
    @Test fun taskFiltersSearchNotesAndUseCalendarDates() {
        val tasks=listOf(LocalTask("a","Alpha","2026-10-31",2,notes="private notes"),LocalTask("b","Beta","2026-11-01",0),LocalTask("c","Done","2026-10-01",1,true))
        val today=LocalDate.of(2026,11,1)
        assertEquals(listOf("a"),filteredTasks(tasks,"",TaskFilter.OVERDUE,today).map {it.id})
        assertEquals(listOf("a"),filteredTasks(tasks,"PRIVATE",TaskFilter.ALL,today).map {it.id})
        assertEquals(listOf("c"),filteredTasks(tasks,"",TaskFilter.COMPLETED,today).map {it.id})
        assertEquals(2,filteredTasks(tasks,"",TaskFilter.OPEN,today).size)
    }
}
