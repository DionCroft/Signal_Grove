package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class CronCreationTest {
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private fun schema(paused:Boolean=true)=buildJsonObject {
        put("info",buildJsonObject {put("version","0.22.6")})
        put("paths",buildJsonObject {put("/api/cron/jobs",buildJsonObject {put("post",buildJsonObject {
            put("parameters",JsonArray(listOf(buildJsonObject {put("name","profile");put("in","query");put("schema",buildJsonObject {put("type","string")})})))
            put("requestBody",buildJsonObject {put("content",buildJsonObject {put("application/json",buildJsonObject {put("schema",buildJsonObject {put("\$ref","#/components/schemas/CronJobCreate")})})})})
        })})})
        put("components",buildJsonObject {put("schemas",buildJsonObject {put("CronJobCreate",buildJsonObject {
            put("required",JsonArray(listOf(JsonPrimitive("schedule"))))
            put("properties",buildJsonObject {for(key in listOf("name","schedule","prompt","repeat","deliver","skills","model","provider","base_url","script","context_from","enabled_toolsets","workdir","no_agent","timezone","grace_minutes","catch_up","paused_reason")) put(key,buildJsonObject {put("type","string")});if(paused) put("paused",buildJsonObject {put("type","boolean")})})
        })})})
    }
    private fun accepted(repeat:JsonElement=JsonPrimitive(1))=obj("""{"id":"job-fixture","name":"Fixture","profile":"worker","enabled":false,"state":"paused","next_run_at":null,"prompt":"Fixture prompt","deliver":"local","no_agent":false,"schedule":{"kind":"cron","expr":"0 9 * * *"},"repeat":{"times":$repeat,"completed":0}}""")
    @Test fun onlyADeclaredPausedCreationContractCanAuthorizeTheTypedRequest() {
        val request=CronCreation("Fixture","0 9 * * *","Fixture prompt")
        val contract=CronCreateContract.parse(schema());assertTrue(contract.accepts(request))
        assertEquals(contract.signature,CronCreateContract.parse(JsonObject(schema().entries.reversed().associate {it.toPair()})).signature)
        assertTrue(runCatching {CronCreateContract.parse(schema(false))}.exceptionOrNull() is CronCreationUnsupported)
        val unsupported=Json.parseToJsonElement(schema().toString().replace("\"paused\":{\"type\":\"boolean\"}","\"paused\":{\"type\":\"string\"}")).jsonObject
        assertTrue(runCatching {CronCreateContract.parse(unsupported)}.exceptionOrNull() is CronCreationUnsupported)
        val wrongProfile=obj(schema().toString().replaceFirst(buildJsonObject {put("type","string")}.toString(),buildJsonObject {put("type","integer")}.toString()))
        assertTrue(runCatching {CronCreateContract.parse(wrongProfile)}.exceptionOrNull() is CronCreationUnsupported)
        val falseOnly=obj(schema().toString().replace(buildJsonObject {put("type","boolean")}.toString(),buildJsonObject {put("type","boolean");put("const",false)}.toString()))
        assertTrue(runCatching {CronCreateContract.parse(falseOnly)}.exceptionOrNull() is CronCreationUnsupported)
        assertTrue(runCatching {(contract.fields as MutableSet<String>).add("unexpected")}.isFailure)
    }
    @Test fun exactProfileQueryAndLiteralPausedBodyUseOnlyManagementAuthentication()=runBlocking {
        MockWebServer().use {server ->
            repeat(3) {server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"))}
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val tokens=ManagementTokens("paused-cron-management-fixture","refresh-fixture","password","fixture",4102444800)
            api.cronSchema(tokens);api.cronDeliveries(tokens,"worker");api.createCron(tokens,"worker",CronCreation("Fixture","0 9 * * *","Fixture prompt"))
            assertEquals("/dashboard/openapi.json",server.takeRequest().path);assertEquals("/dashboard/api/cron/delivery-targets?profile=worker",server.takeRequest().path)
            val post=server.takeRequest();val body=obj(post.body.readUtf8());assertEquals("POST",post.method);assertEquals("/dashboard/api/cron/jobs?profile=worker",post.path);assertEquals("Bearer paused-cron-management-fixture",post.getHeader("Authorization"));assertEquals(true,body["paused"]!!.jsonPrimitive.boolean);assertFalse(body.containsKey("profile"));assertEquals(1,body["repeat"]!!.jsonPrimitive.int)
        }
    }
    @Test fun listsAreFrozenAndExplicitZeroToolAllowlistDiffersFromInheritedTools() {
        val skills=mutableListOf("fixture-skill");val tools=mutableListOf<String>()
        val request=CronCreation("Fixture","0 9 * * *",skills=skills,toolsets=tools,baseUrl="https://provider.example/?key=private-key-fixture",graceMinutes=0,catchUp=false)
        skills[0]="changed";tools.add("changed")
        assertEquals("fixture-skill",request.body()["skills"]!!.jsonArray.single().jsonPrimitive.content);assertEquals(JsonArray(emptyList()),request.body()["enabled_toolsets"])
        assertFalse(CronCreation("Fixture","0 9 * * *","prompt").body().containsKey("enabled_toolsets"));assertEquals(0,request.body()["grace_minutes"]!!.jsonPrimitive.int);assertEquals(false,request.body()["catch_up"]!!.jsonPrimitive.boolean)
        assertFalse(request.summary().contains("private-key-fixture"));assertFalse(request.toString().contains("private-key-fixture"));assertEquals("https://provider.example/",request.reviewBaseUrl())
        assertTrue(runCatching {(request.skills as MutableList<String>).add("late")}.isFailure)
    }
    @Test fun acceptedConfigurationMustBePausedAndReadbackCannotSubstituteAnotherJobOrActivateIt() {
        val request=CronCreation("Fixture","0 9 * * *","Fixture prompt");val row=accepted()
        assertTrue(request.acknowledge(row,"worker").contains("job-fixture"));assertTrue(request.matches(row));assertTrue(request.sameConfiguration(row,row))
        val active=obj(row.toString().replace("\"enabled\":false","\"enabled\":true"));assertTrue(runCatching {request.acknowledge(active,"worker")}.isFailure);assertFalse(request.sameConfiguration(row,active))
        val other=obj(row.toString().replace("\"job-fixture\"","\"other-job\""));assertFalse(request.sameConfiguration(row,other))
        val once = withSchedule(accepted(), """{"kind":"once","run_at":"2026-11-23T09:00:00+01:00"}""")
        assertTrue(CronCreation("Fixture","2026-11-23T08:00:00Z","Fixture prompt",repeat=null).matches(once))
        assertFalse(CronCreation("Fixture","tomorrow","Fixture prompt",repeat=null).matches(once))
    }
    private fun withSchedule(reply: JsonObject, schedule: String): JsonObject =
        JsonObject(reply + ("schedule" to obj(schedule)))

    @Test fun canonicalFieldsBindReviewedSchedulesWithoutRequiringADeviceForecast() {
        val row = accepted()
        fun request(text: String) = CronCreation("Fixture", text, "Fixture prompt")
        assertFalse(request("0 9 * * *").matches(withSchedule(row, """{"kind":"cron","expr":"0 11 * * *"}""")))
        assertFalse(request("0 9 * * *").matches(withSchedule(row, """{"kind":"cron","display":"0 9 * * *"}""")))
        assertTrue(request("weekdays at 9am").matches(withSchedule(row, """{"kind":"cron","expr":"0 9 * * 1-5"}""")))
        assertFalse(request("weekdays at 9am").matches(withSchedule(row, """{"kind":"cron","expr":"0 9 * * *"}""")))
        assertTrue(request("0   9 * JAN MON").matches(withSchedule(row, """{"kind":"cron","expr":"0 9 * jan mon"}""")))
        assertTrue(request("R 9 * * *").matches(withSchedule(row, """{"kind":"cron","expr":"R 9 * * *"}""")))
        assertTrue(request("every 2h").matches(withSchedule(row, """{"kind":"interval","minutes":120}""")))
        assertFalse(request("every 2h").matches(withSchedule(row, """{"kind":"interval","minutes":60}""")))
        assertFalse(request("every 2h").matches(withSchedule(row, """{"kind":"interval","minutes":"120"}""")))
        assertFalse(request("every 2h").matches(withSchedule(row, """{"kind":"once","run_at":"2026-11-23T11:00:00Z"}""")))
    }

    @Test fun fixedOneShotChecksTheReviewedInstantOrProfileWallTime() {
        val row = withSchedule(accepted(), """{"kind":"once","run_at":"2026-11-23T09:30:00.123456+01:00"}""")
        assertTrue(CronCreation("Fixture","2026-11-23T08:30:00.123456789Z","Fixture prompt").matches(row))
        assertTrue(CronCreation("Fixture","2026-11-23 09:30:00.123456","Fixture prompt").matches(row))
        assertFalse(CronCreation("Fixture","2026-11-23T09:30:00.123456Z","Fixture prompt").matches(row))
        assertFalse(CronCreation("Fixture","2026-11-23T08:30:00.123456","Fixture prompt").matches(row))
        val dateOnly = withSchedule(accepted(), """{"kind":"once","run_at":"2026-11-23T00:00:00-05:00"}""")
        assertTrue(CronCreation("Fixture","2026-11-23","Fixture prompt").matches(dateOnly))
    }

    @Test fun relativeOneShotUsesHostCreationTimeAndRejectsChangedDelays() {
        val request = CronCreation("Fixture","in 2 hours","Fixture prompt")
        val created = JsonObject(accepted() + ("created_at" to JsonPrimitive("2026-03-29T00:30:00.600000Z")))
        val correct = withSchedule(created, """{"kind":"once","run_at":"2026-03-29T03:30:00.500000+01:00"}""")
        assertTrue(request.matches(correct))
        assertFalse(request.matches(JsonObject(correct - "created_at")))
        assertFalse(request.matches(withSchedule(created, """{"kind":"once","run_at":"2026-03-29T02:31:00.500000Z"}""")))
        assertFalse(request.matches(withSchedule(created, """{"kind":"once","run_at":"2026-03-29T02:29:00.500000Z"}""")))
        assertFalse(request.matches(withSchedule(created, """{"kind":"once","run_at":"2026-03-29T02:30:00.600001Z"}""")))
        assertFalse(request.sameConfiguration(correct, JsonObject(correct + ("created_at" to JsonPrimitive("2026-03-29T00:30:00.7Z")))))
    }

    @Test fun unsafeOrUnsupportedDraftsAreRejectedLocallyWithoutInventingNoAgentEffects() {
        val invalid=listOf<()->CronCreation>(
            {CronCreation("","0 9 * * *","prompt")},
            {CronCreation("Fixture","0 9 * * *","x".repeat(5001))},
            {CronCreation("Fixture","0 9 * * *","prompt",repeat=0)},
            {CronCreation("Fixture","0 9 * * *","prompt",graceMinutes=1441)},
            {CronCreation("Fixture","0 9 * * *",script="../outside.py",noAgent=true)},
            {CronCreation("Fixture","0 9 * * *","ignored prompt",script="safe.py",noAgent=true)},
            {CronCreation("Fixture","0 9 * * *","prompt",baseUrl="https://user:secret@provider.example/")}
        );invalid.forEach {assertTrue(runCatching(it).exceptionOrNull() is InvalidCronCreation)}
        val script=CronCreation("Fixture","0 9 * * *",script="safe.py",noAgent=true);assertEquals(true,script.body()["no_agent"]!!.jsonPrimitive.boolean)
    }
}
