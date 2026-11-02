package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CronCreationStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("paused-cron-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun json(value:JsonElement)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private val identity=buildJsonObject {put("provider","password");put("user_id","fixture")}
    @Test fun pausedCreationIsClaimedBeforePostAndRetainsNoPrivateBodyOrDuplicateReplay()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger();var configured:JsonObject?=null
        MockWebServer().use {server ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/dashboard/api/auth/me"->json(identity)
                    request.path=="/dashboard/openapi.json"->json(CronCreationFixture.schema())
                    request.path=="/dashboard/api/cron/jobs?profile=worker" && request.method=="GET"->json(JsonArray(listOfNotNull(configured)))
                    request.path=="/dashboard/api/cron/jobs?profile=worker" && request.method=="POST"->{
                        assertEquals("CronCreate",runBlocking {db.dao().unresolvedManagementOperations(target.resourceScope).single().action})
                        assertEquals("Bearer paused-cron-management-fixture",request.getHeader("Authorization"));val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertEquals(true,body["paused"]!!.jsonPrimitive.boolean);assertFalse(body.containsKey("profile"));assertEquals(JsonArray(emptyList()),body["enabled_toolsets"]);writes.incrementAndGet();configured=CronCreationFixture.job(body);json(configured!!)
                    }
                    request.path=="/dashboard/api/cron/jobs/created-job-fixture?profile=worker"->json(configured!!)
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }
            try {
                val repo=CronCreationRepository(db.dao(),CompletableDeferred(Unit));val contract=CronCreateContract.parse(CronCreationFixture.schema())
                val creation=CronCreation("Fixture","0 9 * * *","private-prompt-fixture",toolsets=emptyList(),graceMinutes=0,catchUp=false)
                val outcome=repo.create(target,creation,contract,api,tokens);assertEquals("confirmed",outcome.receipt.state);assertFalse(outcome.job.enabled!!);assertTrue(outcome.job.noToolsAllowed)
                val record=db.dao().managementOperation(outcome.receipt.key)!!;assertFalse(record.toString().contains("private-prompt-fixture"));assertEquals("created-job-fixture",Json.parseToJsonElement(record.resultSummary).jsonObject.text("job_id"))
                assertTrue(runCatching {repo.create(target,creation,contract,api,tokens)}.exceptionOrNull() is CronCreationChanged);assertEquals(1,writes.get())
                db.dao().clearChats();assertEquals(record,db.dao().managementOperation(record.key))
            } finally {db.close()}
        }
    }
    @Test fun lostCreationReplyPersistsOnlyUncertaintyAndNeverGuessesOrResends()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/api/auth/me"->json(identity)
                    request.path=="/openapi.json"->json(CronCreationFixture.schema())
                    request.path=="/api/cron/jobs?profile=worker" && request.method=="GET"->json(JsonArray(emptyList()))
                    request.path=="/api/cron/jobs?profile=worker" && request.method=="POST"->{writes.incrementAndGet();MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)}
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val request=CronCreation("Fixture","0 9 * * *","private-prompt-fixture",baseUrl="https://provider.example/?key=private-key-fixture")
                val repo=CronCreationRepository(db.dao(),CompletableDeferred(Unit));val contract=CronCreateContract.parse(CronCreationFixture.schema())
                assertTrue(runCatching {repo.create(target,request,contract,api,tokens)}.isFailure)
                val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state);assertEquals("",receipt.resultSummary);assertFalse(receipt.toString().contains("private-prompt-fixture"));assertFalse(receipt.toString().contains("private-key-fixture"))
                db.dao().recoverManagementOperations();db.dao().clearChats()
                assertTrue(runCatching {CronCreationRepository(db.dao(),CompletableDeferred(Unit)).create(target,request,contract,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get())
            } finally {db.close()}
        }
    }
    @Test fun changedSchemaRefusesBeforePostAndReadbackFailureCannotDowngradeAcknowledgement()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger();var declared=false;var saved:JsonObject?=null
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/api/auth/me"->json(identity)
                    request.path=="/openapi.json"->json(CronCreationFixture.schema(declared))
                    request.path=="/api/cron/jobs?profile=worker" && request.method=="GET"->json(JsonArray(emptyList()))
                    request.path=="/api/cron/jobs?profile=worker" && request.method=="POST"->{writes.incrementAndGet();val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;saved=CronCreationFixture.job(body);json(saved!!)}
                    request.path=="/api/cron/jobs/created-job-fixture?profile=worker"->json(JsonObject(saved!!+("enabled" to JsonPrimitive(true))))
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val request=CronCreation("Fixture","0 9 * * *","private-prompt-fixture");val contract=CronCreateContract.parse(CronCreationFixture.schema());val repo=CronCreationRepository(db.dao(),CompletableDeferred(Unit))
                assertTrue(runCatching {repo.create(target,request,contract,api,tokens)}.exceptionOrNull() is CronCreationUnsupported);assertEquals(0,writes.get());assertTrue(db.dao().managementOperations(target.apiScope).first().isEmpty())
                declared=true;assertTrue(runCatching {repo.create(target,request,contract,api,tokens)}.exceptionOrNull() is CronCreationReadbackPending);assertEquals(1,writes.get())
                val receipt=db.dao().managementOperations(target.apiScope).first().single();assertEquals("acknowledged",receipt.state);assertTrue(receipt.resultSummary.contains("created-job-fixture"));assertTrue(db.dao().unresolvedManagementOperations(target.resourceScope).isEmpty())
            } finally {db.close()}
        }
    }
}
