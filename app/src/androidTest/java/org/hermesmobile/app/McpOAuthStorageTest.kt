package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class McpOAuthStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("oauth-management-fixture","refresh-fixture","password","fixture",4102444800)
    private val id="unique_flow_fixture_1234567890"
    private val inventory="""{"servers":[{"name":"OAuth fixture","transport":"http","auth":"oauth","source":"config","enabled":true}]}"""
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    private fun snapshot(status:String)=buildJsonObject {put("flow_id",id);put("server_name","OAuth fixture");put("status",status);put("authorization_url","https://provider.example/authorize?state=private-state-fixture");put("error","private-error-fixture")}.toString()
    @Test fun durableFlowBindingSurvivesRestartAndReopenedApprovalWithoutSavingBrowserSecrets()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val starts=AtomicInteger();val cancels=AtomicInteger();val reads=AtomicInteger();var status="approved";var expired=false
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    assertEquals("Bearer oauth-management-fixture",request.getHeader("Authorization"))
                    return when(request.path) {
                        "/dashboard/api/auth/me"->json("""{"provider":"password","user_id":"fixture"}""")
                        "/dashboard/api/mcp/servers?profile=worker"->json(inventory)
                        "/dashboard/api/mcp/servers/OAuth%20fixture/auth?profile=worker"->{starts.incrementAndGet();assertEquals("POST",request.method);assertEquals("OAuthStart",runBlocking {db.dao().unresolvedManagementOperations(targetScope(server)).single().action});json(snapshot("authorization_required"))}
                        "/dashboard/api/mcp/oauth/flows/$id"->if(request.method=="DELETE") {cancels.incrementAndGet();json("""{"ok":true,"status":"approved"}""")} else {reads.incrementAndGet();if(expired) json("{}").setResponseCode(404) else json(snapshot(status))}
                        else->json("{}").setResponseCode(404)
                    }
                }
            }
            try {
                val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val repo=McpOAuthRepository(db.dao(),CompletableDeferred(Unit));val selected=McpProtocol.inventory(Json.parseToJsonElement(inventory).jsonObject).single()
                val (record,initial)=repo.start(target,selected,api,tokens);assertEquals("authorization_required",initial.status);assertEquals("acknowledged",record.state)
                assertFalse(record.toString().contains("private-state-fixture"));assertFalse(record.toString().contains("private-error-fixture"));assertFalse(record.toString().contains("provider.example"))
                db.dao().recoverManagementOperations();db.dao().clearChats();val reopened=McpOAuthRepository(db.dao(),CompletableDeferred(Unit))
                assertEquals("approved",reopened.read(target,record.key,api,tokens).status)
                status="authorization_required";assertEquals(status,reopened.read(target,record.key,api,tokens).status);assertEquals(status,Json.parseToJsonElement(db.dao().managementOperation(record.key)!!.resultSummary).jsonObject.text("status"))
                assertTrue(runCatching {reopened.read(target.copy(profile="other"),record.key,api,tokens)}.isFailure)
                assertTrue(runCatching {reopened.read(target.copy(actor="password · other"),record.key,api,tokens)}.isFailure);assertEquals(2,reads.get())
                expired=true;assertTrue(runCatching {reopened.read(target,record.key,api,tokens)}.exceptionOrNull() is McpOAuthUnavailable);assertEquals(3,reads.get());assertEquals(1,starts.get());expired=false
                val cancellation=reopened.cancel(target,record.key,api,tokens);assertEquals("acknowledged",cancellation.state);assertTrue(cancellation.resultSummary.contains("\"status\":\"approved\""));assertEquals(1,cancels.get());assertEquals(1,starts.get())
            } finally {db.close()}
        }
    }
    private fun targetScope(server:MockWebServer)=ManagementTarget("a".repeat(64),server.url("dashboard/").toString(),"worker","password · fixture").resourceScope
    @Test fun lostStartAcknowledgementCannotReplayOrGuessAFlowHandle()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val starts=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when(request.path) {
                    "/api/auth/me"->json("""{"provider":"password","user_id":"fixture"}""")
                    "/api/mcp/servers?profile=worker"->json(inventory)
                    "/api/mcp/servers/OAuth%20fixture/auth?profile=worker"->{starts.incrementAndGet();MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)}
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val selected=McpProtocol.inventory(Json.parseToJsonElement(inventory).jsonObject).single();val repo=McpOAuthRepository(db.dao(),CompletableDeferred(Unit))
                assertTrue(runCatching {repo.start(target,selected,api,tokens)}.isFailure)
                val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state);assertEquals("",receipt.resultSummary)
                assertTrue(runCatching {repo.start(target,selected,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                assertTrue(runCatching {repo.read(target,receipt.key,api,tokens)}.isFailure);assertEquals(1,starts.get())
            } finally {db.close()}
        }
    }
}
