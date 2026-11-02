package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class ManagedSkillsTest {
    private fun j(s:String)=Json.parseToJsonElement(s)
    private val skill=ManagedSkill("category/fixture","Public description",true,false,"hub")
    @Test fun projectionsRejectUntypedFlagsDuplicatesAndOversizedValuesWithoutInventingStates() {
        val unknown=SkillProtocol.inventory(j("""[{"name":"fixture"}]""").jsonArray).single()
        assertNull(unknown.enabled);assertNull(unknown.locked)
        assertTrue(runCatching {SkillRequest(unknown,SkillAction.Toggle)}.isFailure)
        listOf("""[{"name":"fixture","locked":"false"}]""","""[{"name":"fixture","enabled":1}]""","""[{"name":"fixture"},{"name":"fixture"}]""","""[{"name":" fixture"}]""").forEach {assertTrue(runCatching {SkillProtocol.inventory(j(it).jsonArray)}.isFailure)}
        assertTrue(runCatching {SkillProtocol.inventory(JsonArray(List(1001) {buildJsonObject {put("name","s$it")}}))}.isFailure)
    }
    @Test fun acknowledgementsRequireExactNameTypedValueAndOk() {
        assertTrue(runCatching {SkillRequest(skill.copy(name="hermes-agent"),SkillAction.Toggle)}.isFailure)
        assertTrue(SkillRequest(skill.copy(name="hermes-agent",enabled=false),SkillAction.Toggle).value)
        val toggle=SkillRequest(skill,SkillAction.Toggle);assertFalse(toggle.value)
        val result=toggle.acknowledge(j("""{"ok":true,"name":"category/fixture","enabled":false}""").jsonObject)
        assertEquals("""{"enabled":false}""",result);assertTrue(toggle.observed(listOf(skill.copy(enabled=false))))
        listOf("""{"ok":true,"name":"other","enabled":false}""","""{"ok":true,"name":"category/fixture","enabled":"false"}""","""{"ok":false,"name":"category/fixture","enabled":false}""").forEach {assertTrue(runCatching {toggle.acknowledge(j(it).jsonObject)}.isFailure)}
        val lock=SkillRequest(skill,SkillAction.Lock);assertTrue(lock.value);assertEquals("SkillLock",lock.receiptAction)
        assertEquals("""{"locked":true}""",lock.acknowledge(j("""{"ok":true,"name":"category/fixture","locked":true}""").jsonObject))
    }
    @Test fun contentIsBoundedAndBoundToTheRequestedNameWithoutReflectingHostPaths() {
        assertEquals("# public text",SkillProtocol.content(j("""{"name":"fixture","content":"# public text","path":"/private-host"}""").jsonObject,"fixture"))
        assertTrue(runCatching {SkillProtocol.content(j("""{"name":"other","content":"text"}""").jsonObject,"fixture")}.isFailure)
        assertTrue(runCatching {SkillProtocol.content(buildJsonObject {put("name","fixture");put("content","x".repeat(131073))},"fixture")}.isFailure)
    }
    @Test fun exactProfileQueryPrefixBodyAndManagementOnlyBearerArePreserved()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("[]"))
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"))
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"))
            val api=ManagementClient(server.url("prefix/").toString(),true,true);val tokens=ManagementTokens("skill-mgt-fixture","refresh","password","user",4102444800)
            listOf("","all"," ALL ","current"," worker").forEach {assertTrue(runCatching {api.skills(tokens,it)}.isFailure)}
            assertEquals(0,server.requestCount)
            api.skills(tokens,"worker");api.skillContent(tokens,"worker",skill.name);val request=SkillRequest(skill,SkillAction.Lock);api.skillControl(tokens,"worker",request)
            val read=server.takeRequest();assertEquals("/prefix/api/skills?profile=worker",read.path)
            val content=server.takeRequest();assertEquals("category/fixture",content.requestUrl!!.queryParameter("name"));assertEquals("worker",content.requestUrl!!.queryParameter("profile"))
            val control=server.takeRequest();assertEquals("PUT",control.method);assertEquals("/prefix/api/skills/lock?profile=worker",control.path);assertEquals(request.body("worker"),j(control.body.readUtf8()))
            listOf(read,content,control).forEach {assertEquals("Bearer skill-mgt-fixture",it.getHeader("Authorization"));assertNull(it.getHeader("Cookie"))}
        }
    }
}
