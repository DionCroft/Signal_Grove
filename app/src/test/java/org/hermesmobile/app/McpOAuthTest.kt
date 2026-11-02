package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class McpOAuthTest {
    private val id="unique_flow_fixture_1234567890"
    private fun obj(text:String)=Json.parseToJsonElement(text).jsonObject
    private fun snapshot(status:String="authorization_required",url:String="https://provider.example/authorize?state=secret-state-fixture&code=secret-code-fixture")=buildJsonObject {put("flow_id",id);put("server_name","OAuth fixture");put("status",status);put("authorization_url",url);put("error","raw-secret-error-fixture")}
    @Test fun flowRequestsUseEncodedExactHandlesAndNoGuessedProfileOnStatusOrCancel()=runBlocking {
        MockWebServer().use {server ->
            repeat(3) {server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"))}
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val tokens=ManagementTokens("oauth-management-fixture","refresh-fixture","password","fixture",4102444800)
            api.startMcpOAuth(tokens,"worker","OAuth fixture");api.mcpOAuthFlow(tokens,id);api.cancelMcpOAuth(tokens,id)
            val start=server.takeRequest();assertEquals("POST",start.method);assertEquals("/dashboard/api/mcp/servers/OAuth%20fixture/auth?profile=worker",start.path);assertEquals("{}",start.body.readUtf8())
            val read=server.takeRequest();assertEquals("GET",read.method);assertEquals("/dashboard/api/mcp/oauth/flows/$id",read.path)
            val cancel=server.takeRequest();assertEquals("DELETE",cancel.method);assertEquals("/dashboard/api/mcp/oauth/flows/$id",cancel.path)
            listOf(start,read,cancel).forEach {assertEquals("Bearer oauth-management-fixture",it.getHeader("Authorization"));assertNull(it.getHeader("Cookie"))}
        }
    }
    @Test fun browserDestinationsAndIdentityAreValidatedAndSecretsNeverEnterSummaries() {
        val accepted=McpOAuthSnapshot.parse(snapshot(),"OAuth fixture",id)
        assertEquals("https://provider.example/authorize",accepted.destination())
        listOf(accepted.summary(),accepted.toString()).forEach {assertFalse(it.contains("secret-state-fixture"));assertFalse(it.contains("secret-code-fixture"));assertFalse(it.contains("raw-secret-error-fixture"));assertFalse(it.contains("provider.example"))}
        listOf("javascript:alert(1)","https://user:secret@provider.example/","https://provider.example/#fragment","http://provider.example/","https://provider.example/\\evil").forEach {url->assertTrue(runCatching {McpOAuthSnapshot.parse(snapshot(url=url),"OAuth fixture",id)}.isFailure)}
        assertTrue(runCatching {McpOAuthSnapshot.parse(snapshot(),"different",id)}.isFailure)
        assertTrue(runCatching {McpOAuthSnapshot.parse(snapshot(),"OAuth fixture","different_flow_fixture_1234")}.isFailure)
        assertNotNull(McpOAuthSnapshot.parse(snapshot(url="http://localhost/authorize?state=fixture"),"OAuth fixture",id,allowHttp=true).authorizationUrl)
    }
    @Test fun reportedApprovalAndErrorsCanReopenAndCancellationDoesNotAssertRevocation() {
        val approved=McpOAuthSnapshot.parse(snapshot("approved"),"OAuth fixture",id);assertNull(approved.authorizationUrl)
        val reopened=McpOAuthSnapshot.parse(snapshot(),"OAuth fixture",approved.flowId);assertEquals("authorization_required",reopened.status)
        val cancel=McpOAuthSnapshot.cancellation(obj("""{"ok":true,"status":"approved"}"""),id)
        assertTrue(cancel.contains("\"cancel_acknowledged\":true"));assertTrue(cancel.contains("\"status\":\"approved\""));assertFalse(cancel.contains("revoked"))
        assertTrue(runCatching {McpOAuthSnapshot.cancellation(obj("""{"ok":false,"status":"error"}"""),id)}.isFailure)
    }
}
