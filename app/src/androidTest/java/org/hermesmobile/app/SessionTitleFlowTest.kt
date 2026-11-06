package org.hermesmobile.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.HermesViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Exercise the real send path against Hermes' unique-title constraint, without owner credentials. */
class SessionTitleFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    @After fun reset() {
        compose.runOnUiThread {vm.forgetServer()}
        compose.waitUntil(10000) {vm.preferences.value.demo && !vm.connecting.value}
    }
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private class Fixture:AutoCloseable {
        val server=MockWebServer()
        val titles=linkedMapOf("existing-test" to "Test")
        val creates=CopyOnWriteArrayList<JsonObject>()
        val turns=CopyOnWriteArrayList<JsonObject>()
        val unexpected=CopyOnWriteArrayList<String>()
        val catalogReads=CopyOnWriteArrayList<String>()
        val defaultModel="Qwen3.8-27B-stock"
        init {
            server.dispatcher=object:Dispatcher() {
                private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
                override fun dispatch(request:RecordedRequest):MockResponse {
                    if(request.getHeader("Authorization")!="Bearer title-fixture" || request.getHeader("Cookie")!=null || !request.path.orEmpty().startsWith("/p/work/")) {
                        unexpected+=request.method+" "+request.path;return json("{}").setResponseCode(401)
                    }
                    val path=request.requestUrl!!.encodedPath.removePrefix("/p/work/")
                    return when {
                        request.method=="GET" && path=="v1/capabilities"->json("""{"model":"$defaultModel","features":{"session_resources":true,"session_chat":true,"run_events_sse":true,"model_options":true},"endpoints":{"runs":{},"model_options":{}}}""")
                        request.method=="GET" && path=="api/model/options"->{
                            catalogReads+=path
                            json("""{"model":"$defaultModel","provider":"spark-qwen","providers":[{"slug":"spark-qwen","name":"Stock fixture","authenticated":true,"models":["$defaultModel"]},{"slug":"spark-qwen-rvn","name":"RVN fixture","authenticated":true,"models":["Qwen3.8-27B-RVN-Q8_0"]}]}""")
                        }
                        request.method=="GET" && path=="api/jobs"->json("""{"jobs":[]}""")
                        request.method=="GET" && path=="api/sessions"->json("""{"data":[{"id":"existing-test","title":"Test"}],"has_more":false}""")
                        request.method=="POST" && path=="api/sessions"->{
                            val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;creates+=body
                            val id=body.text("id");val title=body.text("title")
                            synchronized(titles) {
                                if(title.isBlank() || title in titles.values || title.any {it=='\n' || it=='\r' || it=='\u0000'}) json("""{"error":{"code":"invalid_title"}}""").setResponseCode(400)
                                else {titles[id]=title;json(buildJsonObject {put("session",buildJsonObject {put("id",id);put("title",title)})}.toString())}
                            }
                        }
                        request.method=="POST" && path=="v1/runs"->{
                            val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                            if(!synchronized(titles) {titles.containsKey(body.text("session_id"))}) return json("{}").setResponseCode(404)
                            turns+=body;json("""{"run_id":"title-run-${turns.size}"}""")
                        }
                        request.method=="GET" && Regex("v1/runs/title-run-[0-9]+/events").matches(path)->{
                            val id=path.removePrefix("v1/runs/").removeSuffix("/events")
                            MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"event\":\"run.started\",\"run_id\":\"$id\",\"seq\":0}\n\ndata: {\"event\":\"message.delta\",\"run_id\":\"$id\",\"delta\":\"Fixture reply\",\"seq\":1}\n\ndata: {\"event\":\"run.completed\",\"run_id\":\"$id\",\"seq\":2}\n\n")
                        }
                        request.method=="GET" && Regex("v1/runs/title-run-[0-9]+").matches(path)->json("""{"run_id":"${path.substringAfterLast('/')}","status":"completed","pending_controls":[]}""")
                        else->{unexpected+=request.method+" "+path;json("{}").setResponseCode(404)}
                    }
                }
            }
        }
        override fun close()=server.close()
    }
    private fun connect(f:Fixture) {
        compose.waitUntil(10000) {!vm.connecting.value}
        val url=f.server.url("/p/work/").toString() // Lazy mock-server startup belongs off the UI thread.
        compose.runOnUiThread {vm.configure(url,"title-fixture",true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected"}
        compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice())}
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.isDefault}
        // Exercise the advertised read in every case; prior UI state may also request it.
        compose.runOnUiThread {vm.refreshDiscovery(DiscoveryResource.Models)}
        compose.waitUntil(10000) {DiscoveryResource.Models !in vm.discovery.value.loading && vm.discovery.value.results[DiscoveryResource.Models]!=null}
        assertTrue(f.catalogReads.isNotEmpty())
        assertEquals(f.defaultModel,vm.discovery.value.results.getValue(DiscoveryResource.Models).text("model"))
        assertEquals("spark-qwen",vm.discovery.value.results.getValue(DiscoveryResource.Models).text("provider"))
    }
    private fun send(f:Fixture,input:String,count:Int):Conversation {
        compose.runOnUiThread {vm.newChat();vm.send(input)}
        compose.waitUntil(15000) {!vm.busy.value && vm.runStatus.value=="completed" && f.turns.size==count}
        assertNull(vm.error.value)
        return vm.selectedConversation.value!!
    }
    @Test fun repeatedOpeningTextCreatesSeparateSessionsWithoutRenamingTheExistingConversation() {
        Fixture().use {f->
            connect(f);val first=send(f,"Test",1);val second=send(f,"Test",2)
            assertNotEquals(first.remoteId,second.remoteId);assertNotEquals(first.title,second.title)
            assertTrue(first.title.startsWith("Test · "));assertTrue(second.title.startsWith("Test · "))
            assertEquals("Test",f.titles["existing-test"]);assertEquals(3,f.titles.size)
            assertEquals(2,f.creates.size);assertEquals(listOf("Test","Test"),f.turns.map {it.text("input")})
            assertEquals(listOf(first.remoteId,second.remoteId),f.turns.map {it.text("session_id")})
            assertEquals("Test",runBlocking {app.database.dao().history(first.key)}.first {it.role=="user"}.content)
            assertTrue(f.unexpected.toString(),f.unexpected.isEmpty())
        }
    }
    @Test fun multilineUnicodeInputIsPreservedWhileOnlyTheAutomaticTitleIsSanitized() {
        Fixture().use {f->
            connect(f);val input="你好 🌱\n  Another line\twith spacing\u200B";val c=send(f,input,1)
            assertTrue(c.title.startsWith("你好 🌱 Another line with spacing · "))
            assertEquals(input,f.turns.single().text("input"));assertEquals(input,c.pendingInput)
            assertEquals(input,runBlocking {app.database.dao().history(c.key)}.first {it.role=="user"}.content)
            assertEquals(1,f.creates.size);assertTrue(f.unexpected.toString(),f.unexpected.isEmpty())
        }
    }
    @Test fun explicitServingModelIsFrozenIntoNewTurnsWithoutChangingTheSavedServerDefault() {
        Fixture().use {f->
            connect(f);val choice=RuntimeChoice("Qwen3.8-27B-RVN-Q8_0","spark-qwen-rvn")
            compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,choice)}
            compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value==choice}
            val c=send(f,"Test",1);val request=f.turns.single()
            assertEquals(choice.model,request.text("model"));assertEquals(choice.provider,request.text("provider"))
            assertEquals(f.defaultModel,vm.capabilities.value!!.model)
            assertFalse(f.creates.single().containsKey("model"));assertFalse(f.creates.single().containsKey("provider"))
            val frozen=app.requests.read(c.scope,c.key,c.pendingKey,c.pendingRequestHash)
            assertEquals(request,frozen.body);assertEquals(1,f.creates.size)
            assertTrue(f.unexpected.toString(),f.unexpected.isEmpty())
        }
    }
}
