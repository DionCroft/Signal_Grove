package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class PluginRemovalTest {
    private val home=PluginHome.project(JsonPrimitive("/private-profile"))!!
    private fun row(path:String="/private-profile/plugins/install-name",name:String="Manifest name",key:String="manifest-key",source:String="git",version:String="1")=buildJsonObject {put("name",name);put("key",key);put("version",version);put("description","Public fixture");put("source",source);put("status","enabled");put("install_dir",path);put("servers",JsonArray(emptyList()))}
    private fun inventory(vararg rows:JsonObject,root:PluginHome?=home)=GatewayPluginInventory.parse(buildJsonObject {put("plugins",JsonArray(rows.toList()));put("bundled_count",rows.count {it.text("source")=="bundled"});put("user_count",rows.count {it.text("source")!="bundled"})},"worker",root)
    @Test fun exactProfileRootYieldsOnlyRelativeTargetsWithoutHostPathRetention() {
        val dir=home.directory(JsonPrimitive("/private-profile/plugins/install-name"))!!;assertEquals("install-name",dir.name);assertEquals("PluginDirectoryTarget(redacted)",dir.toString());assertEquals("PluginHome(redacted)",home.toString())
        assertEquals("category/folder",home.directory(JsonPrimitive("/private-profile/plugins/category/folder"))!!.name)
        assertTrue(home.directory(JsonPrimitive("/private-profile/plugins/install-name"))!!.same(dir));assertFalse(dir.belongsTo(PluginHome.project(JsonPrimitive("/other-home"))))
        val plugin=inventory(row()).plugins.single();assertEquals("install-name",plugin.directory!!.name);assertEquals("Manifest name",plugin.name);assertEquals("manifest-key",plugin.key);assertFalse(plugin.toString().contains("private-profile"))
    }
    @Test fun windowsAndUncPathsUseExactRootBoundariesWithoutCaseOrSiblingGuessing() {
        val win=PluginHome.project(JsonPrimitive("Q:\\Private\\Profile"))!!;assertEquals("folder",win.directory(JsonPrimitive("Q:\\Private\\Profile\\plugins\\folder"))!!.name)
        val unc=PluginHome.project(JsonPrimitive("\\\\host\\share\\profile"))!!;assertEquals("category/folder",unc.directory(JsonPrimitive("\\\\host\\share\\profile\\plugins\\category\\folder"))!!.name)
        assertNull(win.directory(JsonPrimitive("Q:/private/Profile/plugins/folder")));assertNull(home.directory(JsonPrimitive("/private-profile-2/plugins/folder")))
        val namedPlugins=PluginHome.project(JsonPrimitive("/private/plugins"))!!;assertEquals("folder",namedPlugins.directory(JsonPrimitive("/private/plugins/plugins/folder"))!!.name);assertEquals("plugins/folder",namedPlugins.directory(JsonPrimitive("/private/plugins/plugins/plugins/folder"))!!.name)
    }
    @Test fun missingMalformedOutsideDeepAndTraversalLocationsNeverEnableRemoval() {
        for(value in listOf<JsonElement>(JsonNull,JsonPrimitive(1),JsonPrimitive("relative"),JsonPrimitive("https://host/profile"),JsonPrimitive("/home/../other"),JsonPrimitive("/a\u0000b"),JsonPrimitive("x".repeat(8193)))) assertNull(PluginHome.project(value))
        for(path in listOf("/private-profile/plugins","/private-profile/plugins2/folder","/outside/plugins/folder","/private-profile/plugins/a/b/c","/private-profile/plugins/../folder","/private-profile/plugins/a..b","/private-profile/plugins/ folder","/private-profile/plugins/a\\b","/private-profile/plugins//folder")) assertNull(home.directory(JsonPrimitive(path)))
        // A replacement-encoded surrogate must not share a root fingerprint with a different path.
        val malformed="/profile-"+0xD800.toChar();assertNull(PluginHome.project(JsonPrimitive(malformed)));assertNull(home.directory(JsonPrimitive("/private-profile/plugins/"+0xDC00.toChar())))
        val unicode=PluginHome.project(JsonPrimitive("/资料/"+String(Character.toChars(0x1F30D))));assertNotNull(unicode);assertEquals("工具",unicode!!.directory(JsonPrimitive("/资料/"+String(Character.toChars(0x1F30D))+"/plugins/工具"))!!.name)
        assertNull(inventory(row(),root=null).plugins.single().directory);assertFalse(PluginRemovalRequest.supported(inventory(row(source="bundled")).plugins.single()))
    }
    @Test fun directoryIdentifierRatherThanManifestOrCanonicalKeyIsTheRemovalName() {
        val q=PluginRemovalRequest(inventory(row()).plugins.single());assertEquals(buildJsonObject {put("profile","worker");put("action","remove");put("name","install-name")},q.params("worker"));assertTrue(runCatching {q.params("current")}.isFailure)
        assertEquals("install-name",q.summary().text("install_name"));assertFalse(q.summary().toString().contains("private-profile"));assertEquals("PluginRemovalRequest(redacted)",q.toString())
        assertTrue(q.same(inventory(row()).plugins.single()));assertFalse(q.same(inventory(row(version="2")).plugins.single()));assertFalse(q.same(inventory(row(path="/private-profile/plugins/other")).plugins.single()))
    }
    @Test fun knownDirectoryAndBundledNameCollisionsRemainUnavailable() {
        val original=row();val inv=inventory(original,row(path="/private-profile/plugins/other",key="other",name="install-name",source="bundled"));assertTrue(PluginRemovalRequest.collision(inv.plugins.first(),inv));assertFalse(PluginRemovalRequest.collision(inventory(original).plugins.single(),inventory(original)))
    }
    @Test fun mandatoryAcknowledgementAndOptionalMemoryResetAreDistinct() {
        val q=PluginRemovalRequest(inventory(row()).plugins.single());val ack=buildJsonObject {put("ok",true);put("name","install-name")};assertEquals(JsonNull,q.acknowledge(ack)["memory_provider_cleared"])
        assertEquals(JsonPrimitive(true),q.acknowledge(JsonObject(ack+mapOf("cleared_memory_provider" to JsonPrimitive(true))))["memory_provider_cleared"]);assertEquals(JsonNull,q.acknowledge(JsonObject(ack+mapOf("cleared_memory_provider" to JsonPrimitive("true"))))["memory_provider_cleared"])
        for(j in listOf(JsonObject(ack+mapOf("ok" to JsonPrimitive("true"))),JsonObject(ack+mapOf("name" to JsonPrimitive("manifest-key"))))) assertTrue(runCatching {q.acknowledge(j)}.isFailure)
    }
    @Test fun inventoryAbsencePermitsKnownFallbackButDoesNotConfuseAnotherUserInstall() {
        val q=PluginRemovalRequest(inventory(row()).plugins.single());assertTrue(q.observed(inventory()));assertTrue(q.observed(inventory(row(path="/bundled/plugin",source="bundled"))));assertTrue(q.observed(inventory(row(path="/entrypoint/plugin",source="entrypoint"))))
        assertFalse(q.observed(inventory(row())));assertFalse(q.observed(inventory(row(key="new-key"))));assertFalse(q.observed(inventory(row(path="/private-profile/plugins/other"))));assertFalse(q.observed(inventory(row(source="unknown"))))
    }
    @Test fun profileRootsAreTransientAndDuplicateNamesCannotProveOneDeletionHome()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"profiles":[{"name":"worker","path":"/private-profile"}]}"""));server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"profiles":[{"name":"worker","path":"/private-profile"},{"name":"worker","path":"/other"}]}"""))
            val api=ManagementClient(server.url("prefix/").toString(),true,true);val tokens=ManagementTokens("fixture-profile-token","refresh-fixture","password","fixture",4102444800)
            val profile=api.profiles(tokens).single();assertNotNull(profile.pluginHome);assertFalse(profile.toString().contains("private-profile"));assertNull(api.profiles(tokens).single().pluginHome)
            val request=server.takeRequest();assertEquals("/prefix/api/profiles",request.path);assertEquals("Bearer fixture-profile-token",request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"))
        }
    }
}
