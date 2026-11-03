package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class HubMaintenanceTest {
    private fun inventory(name:String="fixture-skill",trust:String="community",extra:Boolean=false)=HubInstalledInventory.from(HubSources(emptyList(),emptyList(),buildMap {
        put("fixture-slug",HubInstalled(name,trust,"safe"));put("clawhub/fixture-slug",HubInstalled(name,trust,"safe"));if(extra) put("official/extra",HubInstalled("extra-skill","builtin","safe"))
    }))
    @Test fun aliasesGroupByInstalledNameAndMissingMetadataCannotBecomeAnEmptyInventory() {
        val rows=inventory().entries;assertEquals(1,rows.size);assertEquals(2,rows.single().identifiers.size);assertTrue(rows.single().addressable)
        assertTrue(runCatching {HubInstalledInventory.from(HubSources(emptyList(),emptyList(),null))}.exceptionOrNull() is SkillHubUnsupported)
        val conflict=HubInstalledInventory.from(HubSources(emptyList(),emptyList(),mapOf("a" to HubInstalled("same","community","safe"),"b" to HubInstalled("same","trusted","safe"))))
        assertFalse(conflict.entries.single().addressable)
        for(name in listOf("../outside","--help","/absolute","..","bad\\path")) assertTrue(runCatching {HubMaintenanceRequest(HubMaintenanceAction.Remove,inventory(name),name)}.isFailure)
    }
    @Test fun removeChecksItsExactRecordAndBulkUpdateChecksTheWholeObservedInventory() {
        val remove=HubMaintenanceRequest(HubMaintenanceAction.Remove,inventory(),"fixture-skill");val update=HubMaintenanceRequest(HubMaintenanceAction.UpdateAll,inventory())
        assertTrue(remove.same(inventory(extra=true)));assertFalse(update.same(inventory(extra=true)))
        assertFalse(remove.same(inventory(trust="trusted")));assertFalse(update.same(inventory(trust="trusted")))
        assertTrue(runCatching {HubMaintenanceRequest(HubMaintenanceAction.UpdateAll,inventory(),"fixture-skill")}.isFailure)
    }
    @Test fun typedStartupAliasesNeverBecomeDurableProcessOrCompletionClaims() {
        for(action in HubMaintenanceAction.entries) {
            val q=HubMaintenanceRequest(action,inventory(),if(action==HubMaintenanceAction.Remove) "fixture-skill" else "")
            val alias=if(action==HubMaintenanceAction.Remove) HubInstallRequest.actionName("fixture-skill","uninstall") else "skills-update"
            val good=buildJsonObject {put("ok",true);put("pid",123);put("name",alias)}
            val result=q.acknowledge(good);assertTrue(result.contains("accepted"));assertFalse(result.contains(alias));assertFalse(result.contains("123"))
            for(change in listOf(mapOf("ok" to JsonPrimitive("true")),mapOf("pid" to JsonPrimitive(-1)),mapOf("name" to JsonPrimitive("other-action")))) assertTrue(runCatching {q.acknowledge(JsonObject(good+change))}.isFailure)
        }
    }
    @Test fun summariesAndDefaultDisplaysHideIdentifierQueryValues() {
        val sources=HubSources(emptyList(),emptyList(),mapOf("https://skills.example/item?key=private-fixture" to HubInstalled("fixture-skill","community","safe")))
        val rows=HubInstalledInventory.from(sources);val entry=rows.entries.single();val id=entry.identifiers.single()
        assertFalse(entry.displayIdentifier(id,false).contains("private-fixture"));assertTrue(entry.displayIdentifier(id,true).contains("private-fixture"))
        for(action in HubMaintenanceAction.entries) {val q=HubMaintenanceRequest(action,rows,if(action==HubMaintenanceAction.Remove) entry.name else "");assertFalse(q.summary().contains("private-fixture"));assertFalse(q.toString().contains("private-fixture"))}
    }
    @Test fun onlyActualUninstallNameOrProfileWideUpdateFieldsAreSent()=runBlocking {
        MockWebServer().use {server ->
            val tokens=ManagementTokens("hub-maintenance-fixture","refresh-fixture","password","fixture",4102444800);val api=ManagementClient(server.url("dashboard/prefix/").toString(),true,true)
            for(action in HubMaintenanceAction.entries) {
                server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{}"))
                val q=HubMaintenanceRequest(action,inventory(),if(action==HubMaintenanceAction.Remove) "fixture-skill" else "");api.maintainHubSkills(tokens,"worker & profile",q)
                val sent=server.takeRequest();assertEquals("POST",sent.method);assertEquals("/dashboard/prefix/api/skills/hub/"+q.endpoint,sent.requestUrl!!.encodedPath);assertEquals("worker & profile",sent.requestUrl!!.queryParameter("profile"));assertEquals("Bearer hub-maintenance-fixture",sent.getHeader("Authorization"));assertNull(sent.getHeader("Cookie"))
                val body=Json.parseToJsonElement(sent.body.readUtf8()).jsonObject;assertEquals(if(action==HubMaintenanceAction.Remove) setOf("profile","name") else setOf("profile"),body.keys);assertEquals("worker & profile",body.text("profile"))
                assertTrue(runCatching {api.maintainHubSkills(tokens,"current",q)}.isFailure)
            }
            assertEquals(2,server.requestCount)
        }
    }
}
