package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class HubInstallRequestTest {
    private fun preview(id:String="clawhub/fixture-slug",content:String="# Fixture")=HubPreview(HubSkill("fixture-skill","Description",if(id.startsWith("https://")) "url" else "clawhub",id,"community","",listOf("tag"),"",false),content,listOf("SKILL.md"))
    private fun scan(id:String="clawhub/fixture-slug",policy:String="allow",incomplete:List<String> = emptyList())=HubScan(id,"fixture-skill","clawhub/fixture-slug","community","safe",policy,"Reported policy",emptyList(),true,incomplete,emptyList())
    @Test fun policyQualifiedIdentifierAndPreviewGateInstallationWithoutInventedForce() {
        assertTrue(HubInstallRequest.supported(preview(),scan()))
        for(policy in listOf("ask","block","","ALLOW","unknown")) assertFalse(HubInstallRequest.supported(preview(),scan(policy=policy)))
        for(id in listOf("fixture","NousResearch/hermes-agent/skills/general/fixture","skills-sh/nousresearch/hermes-agent/fixture")) assertFalse(HubInstallRequest.supported(preview(id),scan(id)))
        assertFalse(HubInstallRequest.supported(preview(content=""),scan()))
        assertFalse(HubInstallRequest.supported(preview(),scan("clawhub/other")))
    }
    @Test fun everyDisplayedSourceAndScanChangeRequiresNewReview() {
        val request=HubInstallRequest(preview(),scan())
        assertTrue(request.same(preview(),scan()))
        assertFalse(request.same(preview(content="# Changed"),scan()))
        assertFalse(request.same(preview(),scan(incomplete=listOf("license"))))
        assertFalse(request.same(preview(),scan(policy="ask")))
        assertEquals("HubInstallRequest(redacted)",request.toString())
    }
    @Test fun onlyExactTypedProcessStartupAckIsAcceptedAndReceiptsContainNoSourceOrProcessAlias() {
        val id="https://skills.example/fixture?credential=private-fixture";val request=HubInstallRequest(preview(id),scan(id))
        val valid=buildJsonObject {put("ok",true);put("pid",123);put("name",HubInstallRequest.actionName(id))}
        val result=request.acknowledge(valid)
        assertEquals(JsonPrimitive(true),Json.parseToJsonElement(result).jsonObject["accepted"])
        for(change in listOf(mapOf("ok" to JsonPrimitive("true")),mapOf("pid" to JsonPrimitive("123")),mapOf("pid" to JsonPrimitive(0)),mapOf("name" to JsonPrimitive("wrong-action")))) assertTrue(runCatching {request.acknowledge(JsonObject(valid+change))}.isFailure)
        for(text in listOf(result,request.summary(),request.toString())) {assertFalse(text.contains("private-fixture"));assertFalse(text.contains("skills-install-"));assertFalse(text.contains("123"))}
    }
    @Test fun installUsesOnePrefixedProfileBoundPostWithNoUnsupportedFields()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"ok":true}"""))
            val api=ManagementClient(server.url("dashboard/prefix/").toString(),true,true);val tokens=ManagementTokens("management-fixture","refresh-fixture","password","user",4102444800)
            val request=HubInstallRequest(preview(),scan());api.installHubSkill(tokens,"worker & profile",request)
            val sent=server.takeRequest();assertEquals("POST",sent.method);assertEquals("/dashboard/prefix/api/skills/hub/install",sent.requestUrl!!.encodedPath);assertEquals("worker & profile",sent.requestUrl!!.queryParameter("profile"));assertEquals("Bearer management-fixture",sent.getHeader("Authorization"));assertNull(sent.getHeader("Cookie"))
            assertEquals(buildJsonObject {put("identifier","clawhub/fixture-slug");put("profile","worker & profile")},Json.parseToJsonElement(sent.body.readUtf8()))
            assertTrue(runCatching {api.installHubSkill(tokens,"all",request)}.isFailure);assertEquals(1,server.requestCount)
        }
    }
}
