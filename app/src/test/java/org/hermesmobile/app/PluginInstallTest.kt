package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class PluginInstallTest {
    private val pin="b".repeat(40)
    private fun inspected(kind:PluginSourceKind=PluginSourceKind.Repository,agent:Boolean=true,insecure:Boolean=false,sha:String=pin,warnings:List<String> = emptyList())=PluginInspection(PluginSource(kind,if(kind==PluginSourceKind.Catalog) "fixture-plugin" else "https://git.example/fixture?key=private-source-fixture"),true,agent,true,"fixture-agent","fixture-ui",sha,warnings,insecure)
    private fun catalog()=PluginCatalogEntry("fixture-plugin","https://github.com/example/fixture","a".repeat(40),"plugin","community")
    private fun ack(enable:Boolean=false)=buildJsonObject {put("ok",true);put("plugin_name","fixture-agent");put("enabled",enable);put("restart_required",true);put("gateway_reloaded",false);put("python_dependencies",buildJsonArray {add("private-package @ https://user:pass@example/?key=private-dependency")});put("missing_env",buildJsonArray {add("FIXTURE_KEY")});put("after_install_path","/private-after-install");put("activation",buildJsonObject {put("activated_now",buildJsonObject {put("hooks",buildJsonArray {add("fixture-hook")})});put("deferred",buildJsonObject {put("tools",buildJsonArray {add("fixture_tool")})});put("live_now",buildJsonObject {put("mcp_servers",buildJsonArray {add(buildJsonObject {put("name","fixture-server");put("connected",false);put("tools",buildJsonArray {add("fixture_tool")});put("error","/private-host-error?token=private-error")})});put("skills",buildJsonArray {add(buildJsonObject {put("name","fixture:skill");put("description","private-skill-description")})})});put("missing_dependencies",buildJsonArray {add("private-requirement @ https://example/?secret=private-dependency")});put("missing_in",buildJsonArray {add("backend")})})}
    @Test fun inspectedShaIsSentWithoutRefForBothSourceKindsAndForceIsAlwaysFalse() {
        for(kind in PluginSourceKind.entries) {
            val q=PluginInstallRequest(inspected(kind),if(kind==PluginSourceKind.Catalog) catalog() else null,true);val p=q.params("worker")
            assertEquals(JsonPrimitive(pin),p["inspected_sha"]);assertFalse(p.containsKey("ref"));assertEquals(JsonPrimitive(false),p["force"]);assertEquals(JsonPrimitive(true),p["enable"])
            assertEquals(setOf("profile","action","force","enable","inspected_sha",if(kind==PluginSourceKind.Catalog) "catalog_name" else "identifier"),p.keys)
            assertFalse(q.summary().toString().contains("private-source-fixture"));assertEquals("PluginInstallRequest(redacted)",q.toString());assertTrue(runCatching {q.params("current")}.isFailure)
        }
    }
    @Test fun componentSchemeCommitAndCatalogIdentityGateTheReviewedRequest() {
        assertTrue(PluginInstallRequest.supported(inspected(),null))
        val warnings=mutableListOf("before");val frozen=PluginInstallRequest(inspected(warnings=warnings),null,false);warnings[0]="after";assertFalse(frozen.same(inspected(warnings=warnings)))
        for(i in listOf(inspected(agent=false),inspected(insecure=true),inspected(sha="c".repeat(64)))) assertFalse(PluginInstallRequest.supported(i,null))
        assertFalse(PluginInstallRequest.supported(inspected(PluginSourceKind.Catalog),null))
        assertTrue(runCatching {PluginInstallRequest(inspected(PluginSourceKind.Catalog),PluginCatalogEntry("other","repo","a".repeat(40),"","community"),false)}.isFailure)
    }
    @Test fun mandatoryAcknowledgementRequiresTypedExactNameAndRequestedEnableButNoInventedSha() {
        val q=PluginInstallRequest(inspected(),null,false);q.acknowledge(ack());assertFalse(ack().containsKey("sha"))
        for(change in listOf(mapOf("plugin_name" to JsonPrimitive("other")),mapOf("enabled" to JsonPrimitive(true)),mapOf("enabled" to JsonPrimitive("false")),mapOf("ok" to JsonPrimitive("true")))) assertTrue(runCatching {q.acknowledge(JsonObject(ack()+change))}.isFailure)
        q.acknowledge(JsonObject(ack()+mapOf("activation" to JsonPrimitive("unsupported optional details"))))
    }
    @Test fun activationStagesRemainDistinctAndDurableSummaryOmitsErrorsPathsAndDependencyUrls() {
        val report=PluginInstallReport.parse(ack(),"fixture-agent");assertTrue(report.activationReported);assertTrue(report.liveReported);assertEquals(listOf("fixture-hook"),report.activated["hooks"]);assertEquals(listOf("fixture_tool"),report.deferred["tools"]);assertEquals(false,report.servers.single().connected);assertTrue(report.servers.single().hasError);assertEquals(listOf("fixture:skill"),report.skills)
        for(text in listOf(report.summary().toString(),report.toString())) for(secret in listOf("private-dependency","private-after-install","private-host-error","private-error","private-skill-description","user:pass")) assertFalse(text.contains(secret))
        assertEquals(1,report.dependencyCount);assertEquals(listOf("FIXTURE_KEY"),report.missingEnv)
    }
    @Test fun absentActivationStaysUnreportedAndMalformedOptionalDetailsAreRefusedSeparately() {
        val report=PluginInstallReport.parse(JsonObject(ack()+mapOf("activation" to JsonNull)),"fixture-agent");assertFalse(report.activationReported);assertFalse(report.liveReported);assertEquals(JsonNull,report.summary()["reported_live_servers"])
        assertTrue(runCatching {PluginInstallReport.parse(JsonObject(ack()+mapOf("restart_required" to JsonPrimitive("false"))),"fixture-agent")}.isFailure)
        assertTrue(runCatching {PluginInstallReport.parse(JsonObject(ack()+mapOf("missing_env" to buildJsonArray {add("not an env key")})),"fixture-agent")}.isFailure)
    }
    @Test fun onlyExactSourceMovedMarkerIsExposedWithoutAnyRawRpcError()=runBlocking {
        for(reason in listOf(JsonPrimitive("source_moved"),JsonPrimitive("other"),JsonPrimitive(true))) {
            MockWebServer().use {server ->
                server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"ticket":"${"i".repeat(43)}","ttl_seconds":30}"""))
                server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(s:WebSocket,r:Response) {s.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onMessage(s:WebSocket,text:String) {val j=Json.parseToJsonElement(text).jsonObject;val params=j["params"]!!.jsonObject;assertEquals("plugins.manage",j.text("method"));assertEquals("install",params.text("action"));assertFalse(params.containsKey("ref"));s.send(buildJsonObject {put("jsonrpc","2.0");put("id",j["id"]!!);put("error",buildJsonObject {put("code",5026);put("message","private-rpc-error");put("data",buildJsonObject {put("reason",reason);put("secret","private-error-data")})})}.toString())}
                }).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1"))
                val api=ManagementClient(server.url("prefix/").toString(),true,true);val tokens=ManagementTokens("plugin-install-fixture","refresh-fixture","password","fixture",4102444800)
                api.gateway(tokens).use {socket->val failure=runCatching {socket.installPlugin("worker",PluginInstallRequest(inspected(),null,false))}.exceptionOrNull();assertTrue(failure is GatewayRpcException);failure as GatewayRpcException;assertEquals(reason==JsonPrimitive("source_moved"),failure.sourceMoved);assertFalse(failure.toString().contains("private-rpc-error"));assertFalse(failure.toString().contains("private-error-data"))}
                assertEquals(2,server.requestCount)
            }
        }
    }
}
