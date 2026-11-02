package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class McpCreationTest {
    private fun obj(text:String)=Json.parseToJsonElement(text).jsonObject
    @Test fun bearerProvisioningUsesOnlyTheReviewedProfileAndKeepsSecretsOutOfReceiptMetadata()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"))
            val api=ManagementClient(server.url("dashboard/").toString(),true,true)
            val creation=McpCreation("fixture",McpTransport.Http,"https://mcp.example/mcp?token=query-secret-fixture&project=chosen",auth=McpAuth.Header,bearerToken="provisioned-token-fixture")
            api.createMcp(ManagementTokens("management-token-fixture","refresh-fixture","password","fixture",4102444800),"worker",creation)
            val request=server.takeRequest();assertEquals("POST",request.method);assertEquals("/dashboard/api/mcp/servers?profile=worker",request.path);assertEquals("Bearer management-token-fixture",request.getHeader("Authorization"))
            val body=obj(request.body.readUtf8());assertEquals(setOf("name","profile","url","auth","bearer_token"),body.keys);assertEquals("worker",body.text("profile"));assertEquals("provisioned-token-fixture",body.text("bearer_token"))
            assertEquals("https://mcp.example/mcp",creation.reviewUrl());assertEquals(listOf("token","project"),creation.queryKeys())
            listOf(creation.summary(),creation.toString()).forEach {text->assertFalse(text.contains("query-secret-fixture"));assertFalse(text.contains("provisioned-token-fixture"));assertFalse(text.contains("mcp.example"))}
        }
    }
    @Test fun stdioArgumentsAndEnvironmentAreCopiedBeforeReviewAndOnlyEncodedAsData() {
        val args=mutableListOf("--directory","/workspace/with spaces")
        val env=linkedMapOf("FIXTURE_KEY" to "environment-secret-fixture")
        val creation=McpCreation("stdio-fixture",McpTransport.Stdio,command="uvx",args=args,environment=env)
        args[1]="changed";env["FIXTURE_KEY"]="changed"
        val body=creation.body("worker");assertEquals("/workspace/with spaces",body["args"]!!.jsonArray[1].jsonPrimitive.content);assertEquals("environment-secret-fixture",body["env"]!!.jsonObject.text("FIXTURE_KEY"));assertEquals("uvx",body.text("command"))
        assertFalse(body.containsKey("url"));assertFalse(body.containsKey("bearer_token"));assertFalse(creation.summary().contains("environment-secret-fixture"));assertTrue(creation.summary().contains("FIXTURE_KEY"))
        assertTrue(runCatching {(creation.args as MutableList<String>).add("late")}.isFailure)
    }
    @Test fun acknowledgementsAndReadbackMustMatchPublicConfigurationWithoutComparingRedactedSecrets() {
        val creation=McpCreation("fixture",McpTransport.Http,"https://mcp.example/",auth=McpAuth.Header,bearerToken="header-fixture")
        val matching=obj("""{"name":"fixture","source":"config","transport":"http","url":"https://mcp.example/","auth":"header","enabled":true}""")
        assertTrue(creation.matches(matching))
        listOf(matching.toString().replace("\"fixture\"","\"other\""),matching.toString().replace("https://mcp.example/","https://other.example/"),matching.toString().replace("\"header\"","\"oauth\""),matching.toString().replace("\"config\"","\"plugin\""),matching.toString().replace("\"enabled\":true","\"enabled\":false")).forEach {assertFalse(creation.matches(obj(it)))}
        val stdio=McpCreation("stdio",McpTransport.Stdio,command="uvx",args=listOf("tool"),environment=mapOf("KEY" to "secret-fixture"))
        assertTrue(stdio.matches(obj("""{"name":"stdio","source":"config","transport":"stdio","command":"uvx","args":["tool"],"env":{"KEY":"***"},"enabled":true}""")))
        assertFalse(stdio.matches(obj("""{"name":"stdio","source":"config","transport":"stdio","command":"uvx","args":["different"],"env":{"KEY":"***"},"enabled":true}""")))
    }
    @Test fun incompatibleTransportsAndUnsafeOrOversizedInputAreRejectedBeforeAnyRequest() {
        val cases=listOf<()->McpCreation>(
            {McpCreation("../bad",McpTransport.Http,"https://mcp.example/")},
            {McpCreation("fixture",McpTransport.Http,"file:///private")},
            {McpCreation("fixture",McpTransport.Http,"https://user:password@mcp.example/")},
            {McpCreation("fixture",McpTransport.Http,"https://mcp.example/#fragment")},
            {McpCreation("fixture",McpTransport.Http,"https://mcp.example/",args=listOf("argument"))},
            {McpCreation("fixture",McpTransport.Http,"https://mcp.example/",auth=McpAuth.Header,bearerToken="Bearer")},
            {McpCreation("fixture",McpTransport.Http,"https://mcp.example/",auth=McpAuth.Header,bearerToken="bad\nsecret")},
            {McpCreation("fixture",McpTransport.Stdio,command="uvx",auth=McpAuth.OAuth)},
            {McpCreation("fixture",McpTransport.Stdio,command="uvx",args=List(65) {"x"})},
            {McpCreation("fixture",McpTransport.Stdio,command="uvx",environment=mapOf("bad key" to "value"))}
        )
        cases.forEach {assertTrue(runCatching(it).exceptionOrNull() is InvalidMcpSetup)}
    }
}
