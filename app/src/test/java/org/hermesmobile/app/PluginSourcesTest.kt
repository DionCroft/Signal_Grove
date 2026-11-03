package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginSourcesTest {
    private val pin="a".repeat(40)
    private fun entry(sha:String=pin)=buildJsonObject {put("name","fixture-plugin");put("repo","https://github.com/example/fixture");put("sha",sha);put("subdir","plugin");put("tier","community")}
    private fun inspect(sha:String=pin)=buildJsonObject {put("ok",true);put("agent",true);put("desktop",true);put("agent_name","fixture-agent");put("desktop_name","fixture-ui");put("sha",sha);put("warnings",buildJsonArray {add("Host fixture warning")});put("insecure",false)}
    private fun found()=PluginSourcesProtocol.lookup(buildJsonObject {put("status","found");put("entry",entry())},"fixture-plugin").entry!!
    @Test fun sourceGrammarRejectsLocalCredentialAndControlInputsWhileQueryDisplayIsExplicit() {
        for(value in listOf("owner/repo","owner/repo/subdir","git@github.com:owner/repo.git","https://git.example/repo?key=private-source-fixture")) assertTrue(PluginSource.valid(PluginSourceKind.Repository,value))
        for(value in listOf("","../repo","/host/path","file:///host/repo","http://git.example/repo","https://user:pass@git.example/repo","https://git.example/repo#fragment","owner/repo\n")) assertFalse(PluginSource.valid(PluginSourceKind.Repository,value))
        val source=PluginSource(PluginSourceKind.Repository,"https://git.example/repo?key=private-source-fixture")
        assertFalse(source.display().contains("private-source-fixture"));assertTrue(source.display(true).contains("private-source-fixture"));assertEquals("PluginSource(redacted)",source.toString())
    }
    @Test fun lookupStatusesAreDistinctAndFoundEntriesMustMatchTheRequestedNameAndPinShape() {
        for(status in listOf("unknown","unavailable","invalid_name")) {val result=PluginSourcesProtocol.lookup(buildJsonObject {put("status",status)},"fixture-plugin");assertEquals(status,result.status);assertNull(result.entry)}
        assertEquals(pin,found().sha)
        assertTrue(runCatching {PluginSourcesProtocol.lookup(buildJsonObject {put("status","found");put("entry",entry())},"another-plugin")}.exceptionOrNull() is PluginSourceChanged)
        assertTrue(runCatching {PluginSourcesProtocol.lookup(buildJsonObject {put("status","found");put("entry",entry("not-a-commit"))},"fixture-plugin")}.isFailure)
        assertTrue(runCatching {PluginSourcesProtocol.lookup(buildJsonObject {put("status","unknown");put("entry",entry())},"fixture-plugin")}.isFailure)
    }
    @Test fun successfulInspectionSeparatesTagObjectPinsFromCommitsAndChecksCatalogFreshness() {
        val source=PluginSource(PluginSourceKind.Catalog,"fixture-plugin");val parsed=PluginSourcesProtocol.inspection(inspect(),source,found());assertTrue(parsed.ok);assertEquals(pin,parsed.sha);assertEquals(true,parsed.agent)
        assertTrue(runCatching {(parsed.warnings as MutableList).add("other")}.isFailure)
        assertEquals("b".repeat(40),PluginSourcesProtocol.inspection(inspect("b".repeat(40)),source,found()).sha)
        val changed=PluginSourcesProtocol.lookup(buildJsonObject {put("status","found");put("entry",entry("b".repeat(40)))},"fixture-plugin")
        assertTrue(runCatching {PluginSourcesProtocol.requireCatalog(found(),changed)}.exceptionOrNull() is PluginSourceChanged)
        assertTrue(runCatching {PluginSourcesProtocol.inspection(JsonObject(inspect()+mapOf("agent" to JsonPrimitive("true"))),source)}.isFailure)
        assertTrue(PluginSourcesProtocol.sha("b".repeat(64)));assertFalse(PluginSourcesProtocol.sha("b".repeat(41)))
    }
    @Test fun failedInspectionRetainsNoRawHostErrorAndFeaturedSelectionsAreBoundedAndUnique() {
        val failure=PluginSourcesProtocol.inspection(buildJsonObject {put("ok",false);put("error","/private-host-path?key=private-fixture");put("warnings",JsonArray(emptyList()));put("insecure",false)},PluginSource(PluginSourceKind.Catalog,"fixture-plugin"))
        assertFalse(failure.ok);assertNull(failure.agent);assertFalse(failure.toString().contains("private-fixture"));assertEquals("",failure.sha)
        val row=buildJsonObject {put("name","fixture-plugin");put("title","Fixture");put("description","Public metadata");put("tier","community");put("platforms",buildJsonArray {add("linux")});put("app_state","present");put("sentence","")}
        assertEquals(1,PluginSourcesProtocol.featured(buildJsonObject {put("onboarding",buildJsonArray {add(row)})}).size)
        for(rows in listOf(listOf(row,row),List(201) {row})) assertTrue(runCatching {PluginSourcesProtocol.featured(buildJsonObject {put("onboarding",JsonArray(rows))})}.isFailure)
    }
    @Test fun featuredLookupAndInspectionUseOnlyTheirExactScopedRpcFields()=runBlocking {
        val calls=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when(q.path) {
                "/prefix/api/auth/ws-ticket"->{assertEquals("Bearer source-management-fixture",q.getHeader("Authorization"));MockResponse().setHeader("Content-Type","application/json").setBody("""{"ticket":"${"s".repeat(43)}","ttl_seconds":30}""")}
                "/prefix/api/ws"->{assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {
                        val j=Json.parseToJsonElement(text).jsonObject;val p=j["params"]!!.jsonObject;assertEquals("worker & profile",p.text("profile"));calls.incrementAndGet()
                        val result=when(j.text("method")) {
                            "plugins.catalog.lookup"->{assertEquals(setOf("profile","name"),p.keys);assertEquals("fixture-plugin",p.text("name"));buildJsonObject {put("status","found");put("entry",entry())}}
                            "plugins.manage"->when(p.text("action")) {
                                "onboarding"->{assertEquals(setOf("profile","action"),p.keys);buildJsonObject {put("onboarding",JsonArray(emptyList()))}}
                                "inspect"->{assertEquals(setOf("profile","action","catalog_name"),p.keys);assertEquals("fixture-plugin",p.text("catalog_name"));inspect()}
                                else->error("Unexpected mutation")
                            }
                            else->error("Unexpected method")
                        };s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("result",result)}.toString())
                    }
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")}
                else->MockResponse().setResponseCode(404)
            }}
            val api=ManagementClient(server.url("prefix/").toString(),true,true);val tokens=ManagementTokens("source-management-fixture","refresh-fixture","password","fixture",4102444800)
            api.gateway(tokens).use {socket->assertTrue(PluginSourcesProtocol.featured(socket.featuredPlugins("worker & profile")).isEmpty());val e=PluginSourcesProtocol.lookup(socket.lookupPlugin("worker & profile","fixture-plugin"),"fixture-plugin").entry!!;val source=PluginSource(PluginSourceKind.Catalog,e.name);assertEquals(pin,PluginSourcesProtocol.inspection(socket.inspectPlugin("worker & profile",source),source,e).sha)}
            assertEquals(3,calls.get());assertEquals(2,server.requestCount)
        }
    }
    @Test fun aCancelledInspectionWaitNeverRetriesOrFallsBackToAnotherCredential()=runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val calls=AtomicInteger()
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"ticket":"${"s".repeat(43)}","ttl_seconds":30}"""));server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                override fun onMessage(s:WebSocket,text:String) {calls.incrementAndGet();entered.countDown();release.await(10,TimeUnit.SECONDS)}
            }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1"))
            val api=ManagementClient(server.url("/").toString(),true,true);val tokens=ManagementTokens("source-management-fixture","refresh-fixture","password","fixture",4102444800)
            try {api.gateway(tokens).use {socket->val job=async {socket.inspectPlugin("worker",PluginSource(PluginSourceKind.Repository,"owner/repo"))};withContext(Dispatchers.IO) {assertTrue(entered.await(5,TimeUnit.SECONDS))};job.cancelAndJoin();assertTrue(job.isCancelled);assertEquals(1,calls.get());assertEquals(2,server.requestCount)}} finally {release.countDown()}
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun gatewayReplyTimeoutIsAnUnavailableResultWhileUserCancellationStaysCancellation()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"ticket":"${"s".repeat(43)}","ttl_seconds":30}"""))
            server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
            }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1"))
            val api=ManagementClient(server.url("/").toString(),true,true);val tokens=ManagementTokens("source-management-fixture","refresh-fixture","password","fixture",4102444800)
            api.gateway(tokens).use {socket ->runTest {
                val result=async {runCatching {socket.plugins("worker")}};runCurrent();advanceTimeBy(45001);runCurrent()
                assertTrue(result.await().exceptionOrNull() is GatewayUnavailable)
            }}
        }
    }
}
