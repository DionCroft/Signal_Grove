package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class McpCatalogTest {
    private fun entry(background:Boolean=false,env:Boolean=true)=McpCatalogEntry.inventory(Json.parseToJsonElement("""{"entries":[{"name":"catalog-fixture","description":"Fixture","source":"reviewed source","transport":"stdio","auth_type":"env","command":"uvx","args":["public-package"],"bootstrap":[],"installed":false,"enabled":false,"needs_install":$background,"required_env":${if(env) """[{"name":"FIXTURE_KEY","prompt":"Fixture credential","required":true}]""" else "[]"}}]}""").jsonObject).single()
    @Test fun catalogRequestsPreservePrefixAndSelectedProfileWithOnlyManagementAuthentication()=runBlocking {
        MockWebServer().use {server ->
            repeat(2) {server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"))}
            val api=ManagementClient(server.url("dashboard/").toString(),true,true)
            val tokens=ManagementTokens("catalog-management-fixture","refresh-fixture","password","fixture",4102444800)
            api.catalog(tokens,"work profile");val get=server.takeRequest()
            assertEquals("GET",get.method);assertEquals("/dashboard/api/mcp/catalog?profile=work%20profile",get.path);assertEquals("Bearer catalog-management-fixture",get.getHeader("Authorization"))
            val values=mutableMapOf("FIXTURE_KEY" to "catalog-secret-fixture")
            val request=McpCatalogInstall(entry(),values,false);values["FIXTURE_KEY"]="changed"
            api.installCatalog(tokens,"work profile",request);val post=server.takeRequest();val body=Json.parseToJsonElement(post.body.readUtf8()).jsonObject
            assertEquals("POST",post.method);assertEquals("/dashboard/api/mcp/catalog/install?profile=work%20profile",post.path);assertEquals("work profile",body.text("profile"));assertEquals(false,body["enable"]!!.jsonPrimitive.boolean);assertEquals("catalog-secret-fixture",body["env"]!!.jsonObject.text("FIXTURE_KEY"))
            assertFalse(request.summary().contains("catalog-secret-fixture"));assertFalse(request.toString().contains("catalog-secret-fixture"));assertTrue(request.summary().contains("FIXTURE_KEY"))
        }
    }
    @Test fun undeclaredAndMissingInputsAndUnreliableBackgroundProvisioningAreRefusedLocally() {
        assertTrue(runCatching {McpCatalogInstall(entry(),emptyMap(),false)}.isFailure)
        assertTrue(runCatching {McpCatalogInstall(entry(),mapOf("FIXTURE_KEY" to "key","UNDECLARED" to "value"),false)}.isFailure)
        assertFalse(entry(background=true).supported)
        assertTrue(runCatching {McpCatalogInstall(entry(background=true),mapOf("FIXTURE_KEY" to "key"),true)}.isFailure)
        assertTrue(entry(background=true,env=false).supported)
        assertTrue(runCatching {McpCatalogInstall(entry(background=true,env=false),emptyMap(),false)}.isFailure)
    }
    @Test fun acknowledgementCannotChangeIdentityOrExecutionModeOrClaimBackgroundCompletion() {
        val request=McpCatalogInstall(entry(background=true,env=false),emptyMap(),true)
        fun ack(s:String)=Json.parseToJsonElement(s).jsonObject
        val accepted=request.acknowledge(ack("""{"ok":true,"name":"catalog-fixture","background":true,"action":"mcp-install-fixture"}"""))
        assertTrue(accepted.contains("\"background\":true"));assertFalse(accepted.contains("mcp-install-fixture"));assertFalse(accepted.contains("complete"))
        listOf("""{"ok":true,"name":"other","background":true,"action":"fixture"}""","""{"ok":true,"name":"catalog-fixture","background":false}""","""{"ok":true,"name":"catalog-fixture","background":true}""").forEach {assertTrue(runCatching {request.acknowledge(ack(it))}.isFailure)}
    }
}
