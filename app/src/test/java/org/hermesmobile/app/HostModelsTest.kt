package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HostModelsTest {
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private val owner=ManagementTarget("a".repeat(64),"https://fixture.test/dashboard/","worker","password · fixture-user")
    private val home=PluginHome.project(JsonPrimitive("/private-model-home"))
    private val aux=obj("""{"main":{"provider":"first","model":"main-model"},"tasks":[{"task":"vision","provider":"auto","model":"","base_url":"https://private.example/?key=hidden","local_endpoint":false,"reasoning_effort":null},{"task":"plugin:summary","provider":"auto","model":"","label":"Summary","hint":"Plugin task","plugin":"fixture","inherit_from":"vision","effective":{"provider":"first","model":"vision-model","base_url":"private-effective-endpoint"},"api_key":"private-key"}]}""")
    private val catalog=obj("""{"provider":"first","model":"main-model","providers":[{"slug":"first","name":"First","source":"user-config","is_current":true,"is_user_defined":true,"models":["main-model","other-model"],"total_models":3,"warning":"private-host-error","api_url":"private-catalog-url","api_key":"private-catalog-key","pricing":{"private":"private-price"}},{"slug":"second","name":"Second","models":[],"authenticated":false}]}""")
    private fun parse(a:JsonObject=aux,c:JsonObject=catalog)=HostModelInventory.parse(a,c,owner,home)
    private fun response(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
    private fun profiles(path:String="/private-model-home")=buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path",path)})})}
    private fun tokens()=ManagementTokens("model-access-fixture","refresh-fixture","password","fixture-user",4102444800)
    @Test fun projectsStoredAndInheritedRoutesWhileDiscardingPrivateFieldsAndUnknownAuthentication() {
        val state=parse();assertEquals(HostModelRoute("first","main-model"),state.main);val vision=state.auxiliary.first();assertTrue(vision.endpointPresent);assertEquals(false,vision.localEndpoint);assertNull(vision.reasoning)
        val plugin=state.auxiliary.last();assertEquals("vision",plugin.inheritFrom);assertEquals(HostModelRoute("first","vision-model"),plugin.effective);assertTrue(plugin.effectiveEndpointPresent!!);assertEquals("auto",plugin.route.provider)
        assertNull(state.providers.first().authenticated);assertEquals(false,state.providers.last().authenticated);assertEquals(3,state.providers.first().totalModels);assertTrue(state.providers.first().warningPresent)
        assertFalse(state.toString().contains("private"));assertFalse(state.providers.toString().contains("private"));assertFalse(state.auxiliary.toString().contains("private"));assertFalse(state.homeId.contains("private"))
    }
    @Test fun malformedMissingDuplicateOrUnboundedRowsNeverBecomeSuccessfulEmptyInventory() {
        val provider=catalog["providers"]!!.jsonArray.first().jsonObject
        fun providers(vararg rows:JsonElement)=JsonObject(catalog+mapOf("providers" to JsonArray(rows.toList())))
        val malformed=listOf(JsonObject(catalog-"providers"),JsonObject(catalog+mapOf("providers" to JsonPrimitive("bad"))),providers(provider,provider),providers(JsonObject(provider+mapOf("models" to buildJsonArray {add(1)}))),providers(JsonObject(provider+mapOf("authenticated" to JsonPrimitive("true")))))
        for(c in malformed) assertTrue(runCatching {parse(c=c)}.exceptionOrNull() is HostModelsUnavailable)
        for(field in listOf("provider","model")) assertTrue(runCatching {parse(c=JsonObject(catalog-field))}.isFailure)
        val task=aux["tasks"]!!.jsonArray.first();assertTrue(runCatching {parse(JsonObject(aux+mapOf("tasks" to buildJsonArray {add(task);add(task)})))}.isFailure)
        assertTrue(runCatching {parse(c=JsonObject(catalog+mapOf("provider" to JsonPrimitive("changed"))))}.isFailure)
        assertTrue(runCatching {parse(c=JsonObject(catalog+mapOf("providers" to JsonArray(List(501) {provider}))))}.isFailure)
        assertTrue(runCatching {HostModelInventory.parse(aux,catalog,owner,null)}.isFailure)
    }
    @Test fun collectionsAreFrozenAndEmptyCatalogIsAnExplicitTypedSnapshot() {
        val state=parse();assertTrue(runCatching {(state.providers as MutableList).clear()}.isFailure);assertTrue(runCatching {(state.auxiliary as MutableList).clear()}.isFailure);assertTrue(runCatching {(state.providers.first().models as MutableList).clear()}.isFailure)
        val empty=parse(JsonObject(aux+mapOf("tasks" to JsonArray(emptyList()))),JsonObject(catalog+mapOf("providers" to JsonArray(emptyList()))));assertTrue(empty.providers.isEmpty());assertTrue(empty.auxiliary.isEmpty())
    }
    @Test fun scopedReadUsesOnlyExactGetRoutesPrefixProfileAndManagementAuthentication()=runBlocking {
        for(refresh in listOf(false,true)) MockWebServer().use {server->
            listOf(obj("""{"user_id":"fixture-user","provider":"password"}"""),profiles(),aux,catalog,profiles()).forEach {server.enqueue(response(it))}
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget(owner.apiScope,api.base.toString(),owner.profile,owner.actor)
            val state=HostModelsRepository().read(target,api,tokens(),refresh);assertEquals(refresh,state.refreshed);assertEquals(5,server.requestCount)
            val expected=listOf("/dashboard/api/auth/me","/dashboard/api/profiles","/dashboard/api/model/auxiliary?profile=worker","/dashboard/api/model/options?refresh=$refresh&include_unconfigured=true&explicit_only=false&profile=worker","/dashboard/api/profiles")
            for(path in expected) {val q=server.takeRequest();assertEquals(path,q.path);assertEquals("GET",q.method);assertEquals(0,q.bodySize);assertEquals("Bearer model-access-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
        }
    }
    @Test fun changedHomeAndIdentityAreRefusedWithoutFallbackOrMutation()=runBlocking {
        MockWebServer().use {server->
            listOf(obj("""{"user_id":"fixture-user","provider":"password"}"""),profiles(),aux,catalog,profiles("/changed-home")).forEach {server.enqueue(response(it))}
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget(owner.apiScope,api.base.toString(),owner.profile,owner.actor)
            assertTrue(runCatching {HostModelsRepository().read(target,api,tokens())}.exceptionOrNull() is HostModelsChanged);assertEquals(5,server.requestCount)
        }
        MockWebServer().use {server->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true)
            for(profile in listOf("all","hermes",".hermes","")) assertTrue(runCatching {HostModelsRepository().read(ManagementTarget(owner.apiScope,api.base.toString(),profile,owner.actor),api,tokens())}.isFailure)
            assertTrue(runCatching {HostModelsRepository().read(ManagementTarget(owner.apiScope,api.base.toString(),"worker","password · other"),api,tokens())}.isFailure);assertEquals(0,server.requestCount)
        }
    }
    @Test fun failedOrCancelledCatalogReadNeverRetriesOrContinuesWithOtherProfile()=runBlocking {
        MockWebServer().use {server->
            listOf(obj("""{"user_id":"fixture-user","provider":"password"}"""),profiles(),aux).forEach {server.enqueue(response(it))};server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/other"))
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget(owner.apiScope,api.base.toString(),owner.profile,owner.actor)
            assertTrue(runCatching {HostModelsRepository().read(target,api,tokens())}.exceptionOrNull() is HermesHttpException);assertEquals(4,server.requestCount)
        }
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse=when(q.requestUrl!!.encodedPath) {
                "/dashboard/api/auth/me"->response(obj("""{"user_id":"fixture-user","provider":"password"}"""))
                "/dashboard/api/profiles"->response(profiles())
                "/dashboard/api/model/auxiliary"->response(aux)
                else->{entered.countDown();release.await(5,TimeUnit.SECONDS);response(catalog)}
            }}
            val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget(owner.apiScope,api.base.toString(),owner.profile,owner.actor)
            val read=async {HostModelsRepository().read(target,api,tokens())}
            try {assertTrue(withContext(Dispatchers.IO) {entered.await(5,TimeUnit.SECONDS)});read.cancelAndJoin();assertTrue(read.isCancelled);assertEquals(4,server.requestCount)} finally {release.countDown()}
        }
    }
}
