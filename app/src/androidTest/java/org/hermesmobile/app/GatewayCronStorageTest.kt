package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GatewayCronStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("gateway-storage-fixture","refresh-fixture","password","fixture",4102444800)
    private fun db()=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
    private class Fixture(val dao:HermesDao,val target:ManagementTarget):Dispatcher() {
        @Volatile var rows:List<JsonObject> = listOf(GatewayCronFixture.job())
        @Volatile var mode="normal"
        @Volatile var proof="worker"
        val writes=AtomicInteger();val tickets=AtomicInteger();val claimSeen=AtomicBoolean(true);val actions=CopyOnWriteArrayList<JsonObject>()
        private fun json(value:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
        override fun dispatch(request:RecordedRequest):MockResponse=when {
            request.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("user_id","fixture");put("provider","password")})
            request.path=="/dashboard/api/auth/ws-ticket"->{tickets.incrementAndGet();json(buildJsonObject {put("ticket","gateway-fixture-ticket-long");put("ttl_seconds",30)})}
            request.path=="/dashboard/api/ws"->MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {webSocket.send(GatewayCronFixture.ready())}
                override fun onMessage(webSocket:WebSocket,text:String) {
                    val requestJson=Json.parseToJsonElement(text).jsonObject;val params=requestJson["params"]!!.jsonObject;actions.add(params)
                    if(params.text("action")=="list") {val shown=if(mode=="stale-readback" && writes.get()>0) listOf(GatewayCronFixture.job()) else rows;webSocket.send(GatewayCronFixture.result(requestJson,GatewayCronFixture.inventory(shown,proof)));return}
                    val pending=runBlocking {dao.unresolvedManagementOperations(target.resourceScope)}
                    if(pending.size!=1 || pending.single().state!="sending") claimSeen.set(false)
                    writes.incrementAndGet();val action=params.text("action");val before=rows.single {it.text("job_id")==params.text("name")}
                    val enabled=action=="resume";rows=if(action=="remove") emptyList() else listOf(JsonObject(before+mapOf("enabled" to JsonPrimitive(enabled),"state" to JsonPrimitive(if(enabled) "scheduled" else "paused"))))
                    if(mode=="lost") {webSocket.cancel();return}
                    val result=when {
                        mode=="partial-failure"->buildJsonObject {put("success",false);put("error","private-partial-error-fixture")}
                        action=="remove"->buildJsonObject {put("success",true);put("removed_job",buildJsonObject {put("id",before.text("job_id"));put("name",before.text("name"))})}
                        else->buildJsonObject {put("success",true);put("job",if(mode=="malformed") JsonObject(rows.single()+mapOf("job_id" to JsonPrimitive("wrong-job"))) else rows.single())}
                    }
                    webSocket.send(GatewayCronFixture.result(requestJson,result))
                }
            }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
            else->json(JsonObject(emptyMap())).setResponseCode(404)
        }
    }
    @Test fun eachActionClaimsBeforeOneRpcAndKeepsOnlyRedactedAcknowledgementAndReadback()=runBlocking {
        val database=db()
        MockWebServer().use {server ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("b".repeat(64),api.base.toString(),"worker","password · fixture");val fixture=Fixture(database.dao(),target);server.dispatcher=fixture
            val repo=GatewayCronRepository(database.dao(),CompletableDeferred(Unit))
            try {
                for(action in listOf(GatewayCronAction.Pause,GatewayCronAction.Resume,GatewayCronAction.Delete)) {
                    val request=GatewayCronRequest(GatewayCronJob.parse(fixture.rows.single()),action)
                    val outcome=repo.execute(target,request,api,tokens);assertEquals("confirmed",outcome.receipt!!.state);assertTrue(request.observed(outcome.inventory))
                    val record=requireNotNull(outcome.receipt);assertFalse(record.toString().contains("private-control-preview"));assertFalse(record.toString().contains("private-provider-key"));assertFalse(record.toString().contains(tokens.access));assertFalse(record.requestSummary.contains("fixture.py"))
                }
                assertTrue(fixture.claimSeen.get());assertEquals(3,fixture.writes.get());assertEquals(3,fixture.tickets.get())
                fixture.actions.filter {it.text("action")!="list"}.forEach {assertEquals("worker",it.text("profile"));assertEquals("gateway-control-fixture",it.text("name"));assertEquals(setOf("action","name","profile"),it.keys)}
                database.dao().clearChats();assertEquals(3,database.dao().managementResourceOperations(target.resourceScope).first().size)
            } finally {database.close()}
        }
    }
    @Test fun lostMutationReplySurvivesRecoveryAndPreventsAnyAutomaticReplay()=runBlocking {
        val database=db()
        MockWebServer().use {server ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("b".repeat(64),api.base.toString(),"worker","password · fixture");val fixture=Fixture(database.dao(),target);fixture.mode="lost";server.dispatcher=fixture
            val request=GatewayCronRequest(GatewayCronJob.parse(fixture.rows.single()),GatewayCronAction.Pause)
            try {
                assertTrue(runCatching {GatewayCronRepository(database.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.isFailure)
                assertEquals("unknown",database.dao().unresolvedManagementOperations(target.resourceScope).single().state);assertEquals(1,fixture.writes.get())
                database.dao().recoverManagementOperations();database.dao().clearChats()
                assertTrue(runCatching {GatewayCronRepository(database.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                assertEquals(1,fixture.tickets.get());assertEquals(1,fixture.writes.get())
            } finally {database.close()}
        }
    }
    @Test fun changedConfigurationCollisionAndWrongScopeRefuseBeforeTheLedgerOrMutation()=runBlocking {
        val database=db()
        MockWebServer().use {server ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("b".repeat(64),api.base.toString(),"worker","password · fixture");val fixture=Fixture(database.dao(),target);server.dispatcher=fixture
            val request=GatewayCronRequest(GatewayCronJob.parse(fixture.rows.single()),GatewayCronAction.Pause);val repo=GatewayCronRepository(database.dao(),CompletableDeferred(Unit))
            try {
                fixture.rows=listOf(GatewayCronFixture.job(model="changed-model"));assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is GatewayCronReviewChanged)
                fixture.rows=listOf(GatewayCronFixture.job(),GatewayCronFixture.job(id="other",name="GATEWAY-CONTROL-FIXTURE"));assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is GatewayCronReviewChanged)
                fixture.rows=listOf(GatewayCronFixture.job());fixture.proof="other";assertTrue(runCatching {repo.execute(target,request,api,tokens)}.exceptionOrNull() is GatewayScopeUnproven)
                assertEquals(0,fixture.writes.get());assertTrue(database.dao().managementResourceOperations(target.resourceScope).first().isEmpty())
                fixture.proof="worker";fixture.rows=emptyList();val absent=repo.execute(target,GatewayCronRequest(request.expected,GatewayCronAction.Delete),api,tokens);assertTrue(absent.observedOnly);assertNull(absent.receipt);assertEquals(0,fixture.writes.get())
            } finally {database.close()}
        }
    }
    @Test fun acknowledgedMutationCannotBeDowngradedWhenConfigurationReadbackFails()=runBlocking {
        val database=db()
        MockWebServer().use {server ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("b".repeat(64),api.base.toString(),"worker","password · fixture");val fixture=Fixture(database.dao(),target);fixture.mode="stale-readback";server.dispatcher=fixture
            try {
                val request=GatewayCronRequest(GatewayCronJob.parse(fixture.rows.single()),GatewayCronAction.Pause)
                assertTrue(runCatching {GatewayCronRepository(database.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is GatewayCronReadbackPending)
                val record=database.dao().managementResourceOperations(target.resourceScope).first().single();assertEquals("acknowledged",record.state);assertEquals("gateway-control-fixture",Json.parseToJsonElement(record.resultSummary).jsonObject.text("job_id"));assertEquals(1,fixture.writes.get())
            } finally {database.close()}
        }
    }
    @Test fun malformedAcknowledgementAndPartialFailureRemainUnknownEvenAfterAHostChange()=runBlocking {
        for(mode in listOf("malformed","partial-failure")) {
            val database=db()
            MockWebServer().use {server ->
                val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("b".repeat(64),api.base.toString(),"worker","password · fixture");val fixture=Fixture(database.dao(),target);fixture.mode=mode;server.dispatcher=fixture
                try {
                    val request=GatewayCronRequest(GatewayCronJob.parse(fixture.rows.single()),GatewayCronAction.Pause)
                    assertTrue(runCatching {GatewayCronRepository(database.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.isFailure)
                    val record=database.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",record.state);assertEquals("",record.resultSummary);assertFalse(record.toString().contains("private-partial-error-fixture"));assertEquals(false,fixture.rows.single()["enabled"]!!.jsonPrimitive.boolean)
                    assertTrue(runCatching {GatewayCronRepository(database.dao(),CompletableDeferred(Unit)).execute(target,request,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,fixture.writes.get())
                } finally {database.close()}
            }
        }
    }
}
