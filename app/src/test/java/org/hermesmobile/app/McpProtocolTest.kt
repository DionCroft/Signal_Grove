package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class McpProtocolTest {
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private val tokens=ManagementTokens("management-only-fixture","refresh-fixture","password","fixture",4102444800)
    @Test fun scopedControlsUseExactMethodsEncodedSegmentsAndMatchingProfileFields()=runBlocking {
        MockWebServer().use {server ->
            repeat(3) {server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"))}
            val api=ManagementClient(server.url("dashboard/").toString(),true,true)
            val toggle=McpRequest(McpAction.Toggle,"MCP space",true,"config",false)
            api.mcpControl(tokens,"worker",toggle)
            api.mcpControl(tokens,"worker",McpRequest(McpAction.Delete,"MCP space",true,"config"))
            api.mcpControl(tokens,"worker",McpRequest(McpAction.Probe,"MCP space",true,"plugin"))
            val first=server.takeRequest();assertEquals("PUT",first.method);assertEquals("/dashboard/api/mcp/servers/MCP%20space/enabled?profile=worker",first.path)
            assertEquals(obj("""{"enabled":false,"profile":"worker"}"""),obj(first.body.readUtf8()))
            val second=server.takeRequest();assertEquals("DELETE",second.method);assertEquals("/dashboard/api/mcp/servers/MCP%20space?profile=worker",second.path);assertEquals(0L,second.bodySize)
            val third=server.takeRequest();assertEquals("POST",third.method);assertEquals("/dashboard/api/mcp/servers/MCP%20space/test?profile=worker",third.path);assertEquals(obj("{}"),obj(third.body.readUtf8()))
            listOf(first,second,third).forEach {assertEquals("Bearer management-only-fixture",it.getHeader("Authorization"));assertNull(it.getHeader("Idempotency-Key"))}
        }
    }
    @Test fun acknowledgementsRequireTheReviewedTargetAndNeverPersistRawServerSecrets() {
        val toggle=McpRequest(McpAction.Toggle,"fixture",true,"config",false)
        assertEquals(obj("""{"ok":true,"enabled":false}"""),obj(McpProtocol.acknowledge(toggle,obj("""{"ok":true,"name":"fixture","enabled":false,"secret":"never-store"}"""))))
        listOf("""{"ok":true,"name":"other","enabled":false}""","""{"ok":true,"name":"fixture","enabled":true}""","""{"name":"fixture","enabled":false}""").forEach {assertTrue(runCatching {McpProtocol.acknowledge(toggle,obj(it))}.isFailure)}
        val probe=McpRequest(McpAction.Probe,"fixture",true,"config")
        val result=McpProtocol.acknowledge(probe,obj("""{"ok":false,"error":"never-store","tools":[]}"""))
        assertFalse(obj(result)["ok"]!!.jsonPrimitive.boolean);assertFalse(result.contains("never-store"))
        val good=McpProtocol.acknowledge(probe,obj("""{"ok":true,"tools":[{"name":"search","description":"never-store","inputSchema":{"secret":"never-store"}}]}"""))
        assertEquals(1,obj(good)["tool_count"]!!.jsonPrimitive.int);assertEquals("search",obj(good)["tools"]!!.jsonArray.single().jsonPrimitive.content);assertFalse(good.contains("never-store"))
    }
    @Test fun pluginMutationsAmbiguousNamesAndMalformedInventoryAreRefusedLocally() {
        assertTrue(runCatching {McpRequest(McpAction.Toggle,"fixture",true,"plugin",false)}.isFailure)
        assertTrue(runCatching {McpRequest(McpAction.Delete,"fixture",true,"plugin")}.isFailure)
        listOf(".","..","a/b","a\\b","bad\nname").forEach {assertFalse(McpProtocol.addressable(it))}
        assertTrue(McpProtocol.addressable("MCP space"))
        assertTrue(runCatching {McpProtocol.inventory(obj("""{"servers":[{"name":"fixture","source":"config"}]}"""))}.isFailure)
        assertTrue(runCatching {McpProtocol.inventory(obj("""{"servers":[{"name":"fixture","enabled":true},{"name":"fixture","enabled":false}]}"""))}.isFailure)
    }
    @Test fun resourceIdentityAndReadbackStaySeparateFromCredentialRefreshAndAcknowledgement() {
        val scope="a".repeat(64)
        val original=ManagementTarget(scope,"https://fixture.example/dashboard/","worker","password · first")
        assertEquals(original.resourceScope,original.copy(actor="password · second").resourceScope)
        assertNotEquals(original.resourceScope,original.copy(profile="default").resourceScope)
        assertNotEquals(original.resourceScope,original.copy(apiScope="b".repeat(64)).resourceScope)
        assertNotEquals(original.resourceScope,original.copy(url="https://other.example/").resourceScope)
        val row=ManagedServer("fixture","http","oauth",false,"config",emptyList())
        assertTrue(McpProtocol.observed(McpRequest(McpAction.Toggle,"fixture",true,"config",false),listOf(row)))
        assertFalse(McpProtocol.observed(McpRequest(McpAction.Probe,"fixture",false,"config"),listOf(row)))
        assertTrue(McpProtocol.observed(McpRequest(McpAction.Delete,"fixture",true,"config"),emptyList()))
    }
}
