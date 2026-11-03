package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SkillHubProtocolTest {
    private fun entry(id:String="official/general/fixture",source:String="official")=buildJsonObject {put("name","Fixture skill");put("description","Public metadata");put("source",source);put("identifier",id);put("trust_level","builtin");put("tags",buildJsonArray {add("tag")})}
    private fun skill(id:String="official/general/fixture",source:String="official")=SkillHubProtocol.official(buildJsonObject {put("skills",buildJsonArray {add(entry(id,source))})}).single()
    private fun json(s:String)=MockResponse().setHeader("Content-Type","application/json").setBody(s)
    @Test fun clawHubCanonicalIdsAndUnknownInstalledMetadataStayDistinct() {
        val selected=skill("fixture-slug","clawhub");assertEquals("clawhub/fixture-slug",selected.canonicalId)
        val preview=SkillHubProtocol.preview(JsonObject(entry("clawhub/fixture-slug","clawhub")+mapOf("skill_md" to JsonPrimitive("# source"),"files" to buildJsonArray {add("SKILL.md")})),selected)
        assertEquals(selected.canonicalId,preview.skill.canonicalId)
        val search=SkillHubProtocol.search(buildJsonObject {put("results",JsonArray(emptyList()))});assertNull(search.installed)
        val sources=SkillHubProtocol.sources(buildJsonObject {put("sources",buildJsonArray {add(buildJsonObject {put("id","github");put("label","GitHub")})});put("featured",JsonArray(emptyList()));put("installed",JsonObject(emptyMap()))})
        assertNull(sources.sources.single().available);assertNull(sources.sources.single().searchable);assertNotNull(sources.installed);assertTrue(sources.installed!!.isEmpty())
        assertNull(selected.installed)
    }
    @Test fun changedPreviewSourceOrIdentityCannotBeDisplayedAsTheSelectedBundle() {
        val selected=skill()
        for(changed in listOf(entry("official/other"),entry(source="github"))) {
            val value=JsonObject(changed+mapOf("skill_md" to JsonPrimitive("private-other-source"),"files" to buildJsonArray {add("SKILL.md")}))
            assertTrue(runCatching {SkillHubProtocol.preview(value,selected)}.exceptionOrNull() is SkillHubChanged)
        }
        val oversized=JsonObject(entry()+mapOf("skill_md" to JsonPrimitive("x".repeat(131073)),"files" to JsonArray(emptyList())))
        assertTrue(runCatching {SkillHubProtocol.preview(oversized,selected)}.exceptionOrNull() is SkillHubInvalid)
        val duplicates=buildJsonObject {put("skills",buildJsonArray {add(entry("slug","clawhub"));add(entry("clawhub/slug","clawhub"))})}
        assertTrue(runCatching {SkillHubProtocol.official(duplicates)}.exceptionOrNull() is SkillHubInvalid)
    }
    @Test fun optionalScanEvidencePreservesIncompleteChecksAndMasksSensitiveMessagesAndHostPaths() {
        val value=buildJsonObject {
            put("identifier","official/general/fixture");put("name","Fixture skill");put("source","official");put("trust_level","builtin");put("verdict","safe");put("policy","allow");put("policy_reason","Reported policy")
            put("findings",buildJsonArray {add(buildJsonObject {put("severity","low");put("category","fixture");put("file","SKILL.md");put("line",0);put("description","Core finding")})})
            put("tier1",buildJsonObject {put("passed",true);put("incomplete_checks",buildJsonArray {add("license")});put("findings",buildJsonArray {add(buildJsonObject {put("check","pii");put("severity","info");put("message","private-scan-secret");put("file","/private-host-home/file");put("line",0);put("secrets_class",true)})})})
        }
        val scan=SkillHubProtocol.scan(value,skill());assertEquals(true,scan.tier1Passed);assertEquals(listOf("license"),scan.incomplete);assertNull(scan.findings.single().line)
        assertTrue(scan.tier1.single().sensitive);assertEquals("",scan.tier1.single().description);assertEquals("",scan.tier1.single().displayFile());assertFalse(scan.toString().contains("private-scan-secret"));assertFalse(scan.tier1.single().toString().contains("private-host-home"))
        assertTrue(runCatching {SkillHubProtocol.scan(JsonObject(value+mapOf("identifier" to JsonPrimitive("other"))),skill())}.isFailure)
        assertTrue(runCatching {SkillHubProtocol.scan(JsonObject(value+mapOf("policy" to JsonPrimitive(true))),skill())}.isFailure)
    }
    @Test fun identifiersDoNotExposeUrlCredentialsAndTagsAreDefensivelyCopied() {
        val unsafe=skill("https://user:private-value@skills.example/SKILL.md?key=private-query","url")
        assertFalse(unsafe.addressable);assertFalse(unsafe.displayIdentifier().contains("private"));assertFalse(unsafe.toString().contains("private"))
        val safe=skill("https://skills.example/SKILL.md?key=private-query","url");assertTrue(safe.addressable);assertFalse(safe.displayIdentifier().contains("private-query"));assertTrue(safe.displayIdentifier(true).contains("private-query"))
        val tags=mutableListOf("before");val meta=HubSkill("Name","Description","official","official/fixture","builtin","",tags,"",null);tags[0]="after";assertEquals(listOf("before"),meta.tags)
        assertTrue(runCatching {SkillHubProtocol.search(buildJsonObject {put("results",JsonArray(List(21) {entry("official/$it")}))})}.isFailure)
    }
    @Test fun nativeHubRequestsPreservePrefixExactQueryEncodingAndOnlyManagementCredentials()=runBlocking {
        MockWebServer().use {server ->
            repeat(5) {server.enqueue(json("{}"))}
            val api=ManagementClient(server.url("prefix/").toString(),true,true);val tokens=ManagementTokens("hub-management-fixture","refresh-fixture","password","fixture",4102444800)
            api.hubSources(tokens,"worker");api.hubOfficial(tokens,"worker");api.hubSearch(tokens,"worker","a & b","github");val selected=skill("fixture-slug","clawhub");api.hubPreview(tokens,"worker",selected);api.hubScan(tokens,"worker",selected)
            val expected=listOf("sources","official","search","preview","scan")
            expected.forEach {part->val request=server.takeRequest();assertEquals("GET",request.method);assertEquals("/prefix/api/skills/hub/$part",request.requestUrl!!.encodedPath);assertEquals("worker",request.requestUrl!!.queryParameter("profile"));assertEquals("Bearer hub-management-fixture",request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"))
                if(part=="search") {assertEquals("a & b",request.requestUrl!!.queryParameter("q"));assertEquals("github",request.requestUrl!!.queryParameter("source"));assertEquals("20",request.requestUrl!!.queryParameter("limit"))}
                if(part in setOf("preview","scan")) assertEquals("clawhub/fixture-slug",request.requestUrl!!.queryParameter("identifier"))
            }
        }
    }
    @Test fun hubReadRejectsCredentialUrlsAndRedirectsWithoutForwardingManagementTokens()=runBlocking {
        MockWebServer().use {destination ->MockWebServer().use {server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",destination.url("trap")))
            val api=ManagementClient(server.url("/").toString(),true,true);val tokens=ManagementTokens("hub-management-fixture","refresh-fixture","password","fixture",4102444800)
            assertTrue(runCatching {api.hubPreview(tokens,"worker",skill("https://user:private-value@skills.example/","url"))}.isFailure)
            assertTrue(runCatching {api.hubPreview(tokens,"all",skill())}.isFailure);assertEquals(0,server.requestCount)
            val failure=runCatching {api.hubPreview(tokens,"worker",skill())}.exceptionOrNull();assertTrue(failure is HermesHttpException);assertEquals(302,(failure as HermesHttpException).code);assertEquals(0,destination.requestCount);assertEquals(1,server.requestCount)
        }}
    }
    @Test fun cancellingAHostScanStopsWaitingWithoutAnotherReadOrCredentialFallback()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val api=ManagementClient(server.url("/").toString(),true,true);val tokens=ManagementTokens("hub-management-fixture","refresh-fixture","password","fixture",4102444800)
            val task=launch(Dispatchers.Default) {api.hubScan(tokens,"worker",skill())}
            val request=withContext(Dispatchers.IO) {server.takeRequest(5,TimeUnit.SECONDS)};assertNotNull(request);task.cancelAndJoin();assertEquals(1,server.requestCount)
        }
    }
}
