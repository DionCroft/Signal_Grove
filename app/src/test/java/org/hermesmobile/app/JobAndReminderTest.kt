package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.background.ReminderPolicy
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class JobAndReminderTest {
    @Test fun createIsPausedAndLocalWhileEditPreservesUnchangedSchedule() {
        val create=JobProtocol.body("Review","Read notes","0 9 * * *",true)
        assertEquals("local",create.text("deliver"));assertTrue(create["paused"]!!.jsonPrimitive.boolean)
        val update=JobProtocol.body("Review","New prompt","",false)
        assertEquals(setOf("name","prompt"),update.keys)
        try {JobProtocol.validate("create","",JsonObject(create+mapOf("shell" to JsonPrimitive("bad"))));fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun allJobMutationsRetainPrefixAndExactMethodsWithoutExtraRequests()=runBlocking {
        MockWebServer().use {server ->
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/work/").toString(),"fixture",true),true)
            for(op in listOf("create","update","pause","resume","run","delete")) {
                server.enqueue(MockResponse().setBody(if(op=="delete") """{"ok":true}""" else """{"job":{"id":"j1","name":"Review","state":"paused","enabled":false}}"""))
                api.mutateJob(op,if(op=="create") "" else "j1",if(op in setOf("create","update")) JobProtocol.body("Review","Read notes",if(op=="create") "0 9 * * *" else "",op=="create") else buildJsonObject {})
                val request=server.takeRequest(2,TimeUnit.SECONDS)!!
                assertEquals("Bearer fixture",request.getHeader("Authorization"))
                assertEquals(when(op) {"delete"->"DELETE";"update"->"PATCH";else->"POST"},request.method)
                assertEquals("/p/work/api/jobs"+if(op=="create") "" else "/j1"+if(op in setOf("pause","resume","run")) "/$op" else "",request.path)
            }
            assertEquals(6,server.requestCount)
        }
    }
    @Test fun mismatchedAcknowledgementFailsWithoutRepeatingRun()=runBlocking {
        MockWebServer().use {server ->
            val api=OkHttpHermesApiClient(ServerConfig(server.url("/").toString(),"fixture",true),true)
            server.enqueue(MockResponse().setBody("""{"job":{"id":"different"}}"""))
            try {api.mutateJob("run","j1");fail()} catch(_:IllegalArgumentException) {}
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun dueRemindersUseLocalCalendarAndIgnoreCompletedOrInvalidDates() {
        val day=LocalDate.of(2026,11,2)
        val rows=listOf(LocalTask("a","Past","2026-11-01"),LocalTask("b","Today","2026-11-02"),LocalTask("c","Future","2026-11-03"),LocalTask("d","Done","2026-11-02",completed=true),LocalTask("e","Invalid","bad"))
        assertEquals(listOf("a","b"),ReminderPolicy.due(rows,day).map {it.id})
        assertNotEquals(ReminderPolicy.taskKey(rows[0],day),ReminderPolicy.taskKey(rows[0],day.plusDays(1)))
    }
    @Test fun onlyAuthoritativeOrActionNeededStatesProduceRunNotices() {
        assertTrue(ReminderPolicy.noteworthy("completed"));assertTrue(ReminderPolicy.noteworthy("waiting_for_approval"))
        assertFalse(ReminderPolicy.noteworthy("response_finished"));assertFalse(ReminderPolicy.noteworthy("unknown"));assertFalse(ReminderPolicy.noteworthy("running"))
        assertNotEquals(ReminderPolicy.runKey("a","r","completed"),ReminderPolicy.runKey("b","r","completed"))
    }
    @Test fun identifiersCannotNavigateOutOfTheConfiguredPrefix() {
        for(value in listOf(".","..","../x","a/b","%2e%2e")) try {OkHttpHermesApiClient.segment(value);fail(value)} catch(_:IllegalArgumentException) {}
        assertEquals("api-id.1",OkHttpHermesApiClient.segment("api-id.1"))
    }
}
