package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class ManagedCronTest {
    private fun obj(text:String)=Json.parseToJsonElement(text).jsonObject
    private val row=obj("""{"id":"job-fixture","name":"Fixture","profile":"worker","profile_name":"worker","enabled":false,"state":"paused","schedule_display":"0 9 * * *","repeat":{"times":null,"completed":2},"prompt":"private-prompt-fixture","last_error":"private-error-fixture","hermes_home":"/private-home-fixture","base_url":"https://provider.example/?key=private-key-fixture"}""")
    @Test fun managementArrayAndObjectRepliesKeepExactProfilePrefixAndIndependentAuthentication()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(JsonArray(listOf(row)).toString()))
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(row.toString()))
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val tokens=ManagementTokens("host-cron-management-fixture","refresh-fixture","password","fixture",4102444800)
            val jobs=ManagedCronJob.inventory(api.cronJobs(tokens,"worker"),"worker");assertEquals("job-fixture",jobs.single().id)
            assertEquals("job-fixture",ManagedCronJob.parse(api.cronJob(tokens,"worker",jobs.single().id),"worker").id)
            val first=server.takeRequest();val second=server.takeRequest();assertEquals("/dashboard/api/cron/jobs?profile=worker",first.path);assertEquals("/dashboard/api/cron/jobs/job-fixture?profile=worker",second.path)
            listOf(first,second).forEach {assertEquals("GET",it.method);assertEquals("Bearer host-cron-management-fixture",it.getHeader("Authorization"));assertNull(it.getHeader("Cookie"));assertEquals(0,it.bodySize)}
            for(profile in listOf("","all","ALL"," All ")) assertTrue(runCatching {api.cronJobs(tokens,profile)}.isFailure)
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun profileProofDuplicateIdentityAndBoundsAreCheckedBeforeDisplayingAnyRows() {
        assertTrue(runCatching {ManagedCronJob.parse(row,"other")}.isFailure)
        assertTrue(runCatching {ManagedCronJob.parse(JsonObject(row-"profile"),"worker")}.isFailure)
        assertTrue(runCatching {ManagedCronJob.inventory(JsonArray(listOf(row,row)),"worker")}.isFailure)
        assertTrue(runCatching {ManagedCronJob.inventory(JsonArray(List(501) {row}),"worker")}.isFailure)
        val unknown=ManagedCronJob.parse(JsonObject(row+("id" to JsonPrimitive("unknown"))),"worker");assertFalse(unknown.addressable)
    }
    @Test fun missingStateIsUnknownAndOnlyExplicitNullMeansUnlimitedWhileErrorsStayRedacted() {
        val parsed=ManagedCronJob.parse(row,"worker");assertTrue(parsed.hasRunError);assertEquals(false,parsed.enabled);assertEquals("__unlimited__",parsed.fields()["repeat"])
        assertFalse(parsed.toString().contains("private-prompt-fixture"));assertFalse(parsed.toString().contains("private-error-fixture"));assertFalse(parsed.fields().toString().contains("private-error-fixture"));assertFalse(parsed.fields().toString().contains("private-home-fixture"));assertFalse(parsed.fields().toString().contains("private-key-fixture"))
        val missing=ManagedCronJob.parse(JsonObject(row-"enabled"+("repeat" to obj("{}"))),"worker");assertNull(missing.enabled);assertFalse(missing.fields().containsKey("repeat"))
        assertTrue(ManagedCronJob.parse(JsonObject(row+("enabled_toolsets" to JsonArray(emptyList()))),"worker").noToolsAllowed)
        assertFalse(ManagedCronJob.parse(JsonObject(row+("enabled_toolsets" to JsonNull)),"worker").noToolsAllowed)
        val invalid=ManagedCronJob.parse(JsonObject(row+("repeat" to obj("""{"times":"__unlimited__","completed":-1}"""))),"worker");assertFalse(invalid.fields().containsKey("repeat"));assertFalse(invalid.fields().containsKey("repeat_completed"))

    }
    @Test fun addingArraySupportDoesNotLoosenExistingObjectContractsOrFollowRedirects()=runBlocking {
        MockWebServer().use {server ->MockWebServer().use {other ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val tokens=ManagementTokens("host-cron-management-fixture","refresh-fixture","password","fixture",4102444800)
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("[]"));assertTrue(runCatching {api.mcp(tokens,"worker")}.isFailure)
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"));assertTrue(runCatching {api.cronJobs(tokens,"worker")}.isFailure)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",other.url("/")));assertTrue(runCatching {api.cronJobs(tokens,"worker")}.isFailure);assertEquals(0,other.requestCount)
        }}
    }
}
