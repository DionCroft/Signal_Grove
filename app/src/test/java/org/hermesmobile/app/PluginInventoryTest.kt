package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PluginInventoryTest {
    private fun row()=buildJsonObject {put("name","Fixture plugin");put("key","tools/fixture");put("version","1.2.3");put("description","Public description");put("source","user");put("status","not enabled");put("install_dir","/private-install-fixture");put("settings_schema",buildJsonArray {add(buildJsonObject {put("value","private-setting-fixture")})});put("servers",buildJsonArray {add(buildJsonObject {put("name","fixture-server");put("state","missing_env");put("sentence","Missing FIXTURE_KEY")})});put("tools",buildJsonArray {add("fixture_tool")})}
    private fun result(rows:List<JsonObject> = listOf(row()))=buildJsonObject {put("plugins",JsonArray(rows));put("user_count",rows.size);put("bundled_count",0)}
    @Test fun reportsStatusWithoutInventingFlagsAndProjectsNoHostPathsOrSettings() {
        val parsed=GatewayPluginInventory.parse(result(),"worker");val plugin=parsed.plugins.single()
        assertEquals("worker",parsed.requestedProfile);assertEquals("not enabled",plugin.status);assertNull(plugin.portable);assertNull(plugin.desktopHalf);assertNull(plugin.updateAvailable)
        assertEquals("missing_env",plugin.servers.single().state);assertEquals(listOf("fixture_tool"),plugin.tools)
        for(text in listOf(plugin.toString(),parsed.toString(),plugin.description)) {assertFalse(text.contains("private-setting-fixture"));assertFalse(text.contains("private-install-fixture"))}
        assertTrue(runCatching {(plugin.tools as MutableList).add("other")}.isFailure)
    }
    @Test fun malformedCountsDuplicateKeysAndUnprovenAliasesAreRefused() {
        for(value in listOf(result(listOf(row(),row())),JsonObject(result()+mapOf("user_count" to JsonPrimitive("1"))),JsonObject(result()+mapOf("bundled_count" to JsonPrimitive(1))),JsonObject(result()+mapOf("plugins" to JsonNull)))) assertTrue(runCatching {GatewayPluginInventory.parse(value,"worker")}.exceptionOrNull() is GatewayPluginInvalid)
        for(profile in listOf("","all","current","hermes",".hermes","HeRmEs")) assertTrue(runCatching {GatewayPluginInventory.parse(result(),profile)}.isFailure)
    }
    @Test fun boundedTypedRowsDoNotSilentlyTruncateOrCoerceHostMetadata() {
        for(change in listOf(mapOf("description" to JsonPrimitive("x".repeat(8193))),mapOf("portable" to JsonPrimitive("false")),mapOf("tools" to JsonArray(List(2049) {JsonPrimitive("tool-$it")})),mapOf("servers" to JsonArray(List(129) {JsonObject(emptyMap())})))) assertTrue(runCatching {GatewayPluginInventory.parse(result(listOf(JsonObject(row()+change))),"worker")}.isFailure)
    }
    @Test fun onlyScopedPluginsManageListRunsOverReviewedTicketTransport()=runBlocking {
        val tickets=AtomicInteger();val calls=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when(q.path) {
                "/dashboard/prefix/api/auth/ws-ticket"->{assertEquals("POST",q.method);assertEquals("Bearer plugin-management-fixture",q.getHeader("Authorization"));tickets.incrementAndGet();MockResponse().setHeader("Content-Type","application/json").setBody("""{"ticket":"${"p".repeat(43)}","ttl_seconds":30}""")}
                "/dashboard/prefix/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(socket:WebSocket,response:Response) {socket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(socket:WebSocket,text:String) {val j=Json.parseToJsonElement(text).jsonObject;assertEquals("plugins.manage",j.text("method"));assertEquals(buildJsonObject {put("profile","worker");put("action","list")},j["params"]);calls.incrementAndGet();socket.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result())}.toString())}
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->MockResponse().setResponseCode(404)
            }}
            val api=ManagementClient(server.url("dashboard/prefix/").toString(),true,true);val tokens=ManagementTokens("plugin-management-fixture","refresh-fixture","password","fixture",4102444800)
            api.gateway(tokens).use {socket ->assertEquals("Fixture plugin",GatewayPluginInventory.parse(socket.plugins("worker"),"worker").plugins.single().name);assertTrue(runCatching {socket.plugins("hermes")}.isFailure)}
            assertEquals(1,tickets.get());assertEquals(1,calls.get());assertEquals(2,server.requestCount)
        }
    }
}
