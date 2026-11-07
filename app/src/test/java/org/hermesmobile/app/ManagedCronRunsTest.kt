package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class ManagedCronRunsTest {
    private fun obj(text:String)=Json.parseToJsonElement(text).jsonObject
    private val conversation=obj("""{"id":"local-run-fixture","source":"local","profile":"worker","title":"private-title-fixture","preview":"private-preview-fixture","started_at":1794000000.25,"last_active":1794000020,"ended_at":1794000030,"is_active":true,"model":"recorded-model-fixture","message_count":4,"tool_call_count":2,"hermes_home":"private-home-fixture","api_key":"private-key-fixture"}""")
    private val output=obj("""{"id":"cron_output:job-fixture:exec:0","source":"cron_output","profile":"worker","title":"private-script-title","preview":"private-script-error","started_at":1793990000,"last_active":1793990000,"ended_at":1793990000,"is_active":false,"message_count":0,"tool_call_count":0}""")
    private fun reply(vararg rows:JsonObject)=buildJsonObject {put("limit",20);put("runs",JsonArray(rows.toList()))}
    private fun parse(value:JsonObject)=ManagedCronRuns.parse(value,"worker","job-fixture")
    @Test fun conversationAndOutputRecordsRemainDistinctWithoutInferringCompletion() {
        val history=parse(reply(conversation,output));assertEquals("worker",history.profile);assertEquals("job-fixture",history.jobId)
        assertEquals(true,history.rows[0].active);assertTrue(history.rows[0].conversation);assertFalse(history.rows[0].scriptOutput)
        assertEquals("recorded-model-fixture",history.rows[0].model);assertEquals(4L,history.rows[0].messageCount)
        assertTrue(history.rows[1].scriptOutput);assertFalse(history.rows[1].conversation);assertEquals(false,history.rows[1].active)
        assertEquals(0L,history.rows[1].toolCallCount)
    }
    @Test fun rawPrivateTextIsNeverIncludedInDefaultDiagnosticStrings() {
        val history=parse(reply(conversation,output));val diagnostics=history.toString()+history.rows.toString()
        for(marker in listOf("private-title-fixture","private-preview-fixture","private-home-fixture","private-key-fixture","private-script-error","recorded-model-fixture")) assertFalse(diagnostics.contains(marker))
        assertEquals("private-preview-fixture",history.rows[0].preview);assertTrue(history.rows[0].hasPrivateText)
    }
    @Test fun exactProfileProofAndRequestedLimitAreRequired() {
        for(row in listOf(JsonObject(output-"profile"),JsonObject(conversation+("profile" to JsonPrimitive("other"))),JsonObject(conversation-"profile"))) assertTrue(runCatching {parse(reply(row))}.exceptionOrNull() is InvalidCronRunsReply)
        for(value in listOf(JsonObject(reply(conversation)+("limit" to JsonPrimitive("20"))),JsonObject(reply(conversation)+("limit" to JsonPrimitive(100))),reply(*Array(21){JsonObject(output+("id" to JsonPrimitive("row-$it")))}),reply(conversation,conversation))) assertTrue(runCatching {parse(value)}.exceptionOrNull() is InvalidCronRunsReply)
        assertTrue(runCatching {ManagedCronRuns.parse(reply(),"all","job-fixture")}.isFailure)
    }
    @Test fun malformedFlagsTimesCountsAndOversizedTextAreRejected() {
        for((key,bad) in listOf("is_active" to JsonPrimitive("true"),"is_active" to JsonPrimitive(1),"started_at" to JsonPrimitive("1794000000"),"started_at" to JsonPrimitive(-1),"started_at" to JsonPrimitive(1e20),"message_count" to JsonPrimitive(-1),"tool_call_count" to JsonPrimitive(1.5),"preview" to JsonPrimitive("x".repeat(8193)),"title" to JsonPrimitive("bad\ntitle"))) {
            assertTrue(key,runCatching {parse(reply(JsonObject(conversation+(key to bad))))}.exceptionOrNull() is InvalidCronRunsReply)
        }
        assertTrue(runCatching {parse(obj("""{"limit":20,"runs":[false]}"""))}.isFailure)
    }
    @Test fun absentFieldsRemainUnknownAndOpaqueRunIdsAreOnlyDisplayData() {
        val history=parse(reply(obj("""{"profile":"worker"}"""),JsonObject(output+("id" to JsonPrimitive("opaque-output + marker")))))
        val unknown=history.rows[0];assertNull(unknown.active);assertNull(unknown.startedAt);assertNull(unknown.messageCount);assertFalse(unknown.hasPrivateText)
        assertEquals("opaque-output + marker",history.rows[1].id)
    }
    @Test fun projectionsDoNotChangeWhenCallerOwnedJsonOrListsChange() {
        val row=conversation.toMutableMap();val input=mutableListOf<JsonElement>(JsonObject(row));val value=buildJsonObject {put("limit",20);put("runs",JsonArray(input))};val parsed=parse(value)
        row["preview"]=JsonPrimitive("replacement");input.clear();assertEquals("private-preview-fixture",parsed.rows.single().preview)
        assertTrue(runCatching {(parsed.rows as MutableList<ManagedCronRun>).clear()}.isFailure)
    }
    @Test fun requestsPreservePrefixExplicitProfileLimitAndIndependentAuthentication()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(reply(conversation,output).toString()))
            val api=ManagementClient(server.url("dashboard/custom/").toString(),true,true);val tokens=ManagementTokens("cron-runs-management-fixture","refresh-fixture","password","fixture-user",4102444800)
            assertEquals(2,parse(api.cronRuns(tokens,"worker","job-fixture")).rows.size)
            val request=server.takeRequest();assertEquals("GET",request.method);assertEquals("/dashboard/custom/api/cron/jobs/job-fixture/runs",request.requestUrl!!.encodedPath)
            assertEquals("worker",request.requestUrl!!.queryParameter("profile"));assertEquals("20",request.requestUrl!!.queryParameter("limit"));assertEquals("Bearer cron-runs-management-fixture",request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"));assertEquals(0,request.bodySize)
        }
    }
    @Test fun invalidSelectorsNeverSendAndRedirectsNeverLeakTokens()=runBlocking {
        MockWebServer().use {server ->MockWebServer().use {other ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val tokens=ManagementTokens("cron-runs-management-fixture","refresh-fixture","password","fixture-user",4102444800)
            for(profile in listOf("","all"," ALL ","worker\n")) assertTrue(runCatching {api.cronRuns(tokens,profile,"job-fixture")}.isFailure)
            for(id in listOf("unknown","../other","job/other")) assertTrue(runCatching {api.cronRuns(tokens,"worker",id)}.isFailure)
            for(limit in listOf(0,101)) assertTrue(runCatching {api.cronRuns(tokens,"worker","job-fixture",limit)}.isFailure)
            assertEquals(0,server.requestCount);server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",other.url("/")))
            assertTrue(runCatching {api.cronRuns(tokens,"worker","job-fixture")}.isFailure);assertEquals(1,server.requestCount);assertEquals(0,other.requestCount)
        }}
    }
    @Test fun emptyHistoryIsAValidReadAndUnsupportedEndpointsRemainErrors()=runBlocking {
        assertTrue(parse(reply()).rows.isEmpty())
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("{}"));val api=ManagementClient(server.url("dashboard/").toString(),true,true);val tokens=ManagementTokens("cron-runs-management-fixture","refresh-fixture","password","fixture-user",4102444800)
            assertTrue(runCatching {api.cronRuns(tokens,"worker","job-fixture")}.exceptionOrNull() is HermesHttpException)
        }
    }
}
