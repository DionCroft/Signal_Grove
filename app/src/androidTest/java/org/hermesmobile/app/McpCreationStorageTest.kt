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

class McpCreationStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("management-creation-fixture","refresh-fixture","password","fixture",4102444800)
    private fun json(value:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun identity()=json(buildJsonObject {put("provider","password");put("user_id","fixture")})
    @Test fun confirmedCreationSavesNoProvisionedSecretsAndNeverOverwritesAnExistingName()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger()
        var configured:JsonObject?=null
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/dashboard/api/auth/me"->identity()
                    request.path=="/dashboard/api/mcp/servers?profile=worker" && request.method=="GET"->json(buildJsonObject {put("servers",JsonArray(listOfNotNull(configured)))})
                    request.path=="/dashboard/api/mcp/servers?profile=worker" && request.method=="POST"->{
                        assertEquals("Bearer management-creation-fixture",request.getHeader("Authorization"));val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertEquals("worker",body.text("profile"));assertEquals("host-token-fixture",body.text("bearer_token"));writes.incrementAndGet()
                        configured=buildJsonObject {put("name",body.text("name"));put("url",body.text("url"));put("transport","http");put("auth","header");put("source","config");put("enabled",true)}
                        json(configured!!)
                    }
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val creation=McpCreation("new-fixture",McpTransport.Http,"https://mcp.example/?token=query-secret-fixture",auth=McpAuth.Header,bearerToken="host-token-fixture")
                val repo=McpCreationRepository(db.dao(),CompletableDeferred(Unit))
                val outcome=repo.create(target,creation,api,tokens);assertEquals("confirmed",outcome.receipt!!.state);assertEquals("new-fixture",outcome.servers.single().name)
                val record=db.dao().managementOperations(target.apiScope).first().single()
                listOf(record.requestSummary,record.resultSummary,record.toString()).forEach {text->assertFalse(text.contains("host-token-fixture"));assertFalse(text.contains("query-secret-fixture"));assertFalse(text.contains("mcp.example"))}
                assertTrue(runCatching {repo.create(target,creation,api,tokens)}.exceptionOrNull() is McpReviewChanged);assertEquals(1,writes.get())
            } finally {db.close()}
        }
    }
    @Test fun lostCreationReplyRetainsOnlyARedactedReceiptAndBlocksAnotherProvisioningAttempt()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger();var configured:JsonObject?=null
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/api/auth/me"->identity()
                    request.path=="/api/mcp/servers?profile=worker" && request.method=="GET"->json(buildJsonObject {put("servers",JsonArray(listOfNotNull(configured)))})
                    request.path=="/api/mcp/servers?profile=worker" && request.method=="POST"->{
                        val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;assertEquals("environment-secret-fixture",body["env"]!!.jsonObject.text("FIXTURE_KEY"));writes.incrementAndGet()
                        configured=buildJsonObject {put("name",body.text("name"));put("transport","stdio");put("command",body.text("command"));put("args",body["args"]!!);put("env",buildJsonObject {put("FIXTURE_KEY","***")});put("source","config");put("enabled",true)}
                        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    }
                    else->json(JsonObject(emptyMap())).setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val creation=McpCreation("new-stdio",McpTransport.Stdio,command="uvx",args=listOf("--key","argument-secret-fixture"),environment=mapOf("FIXTURE_KEY" to "environment-secret-fixture"))
                val repo=McpCreationRepository(db.dao(),CompletableDeferred(Unit))
                assertTrue(runCatching {repo.create(target,creation,api,tokens)}.isFailure)
                val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state)
                assertFalse(receipt.requestSummary.contains("environment-secret-fixture"));assertFalse(receipt.requestSummary.contains("argument-secret-fixture"));assertFalse(receipt.requestSummary.contains("uvx"));assertEquals("",receipt.resultSummary)
                assertTrue(runCatching {repo.create(target,creation,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get())
                db.dao().recoverManagementOperations();db.dao().clearChats();assertEquals(receipt,db.dao().managementOperation(receipt.key))
            } finally {db.close()}
        }
    }
}
