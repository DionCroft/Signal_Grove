package org.hermesmobile.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.HermesViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Diagnostic probes assert observed 1.0.0 defects, not desired fixed behavior.
 * Synthetic fixture traffic only. Kept outside production test sources after the audit. */
class AuditProbeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    @After fun reset() {compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {vm.preferences.value.demo && !vm.controlBusy.value}}
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun connect(server:MockWebServer,path:String,token:String) {
        val url=server.url(path).toString()
        compose.runOnUiThread {vm.configure(url,token,true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected" && vm.preferences.value.url==url}
    }
    @Test fun compatibilityFollowUpActuallyOmitsThePreviousAssistantAnswer() {
        val bodies=mutableListOf<JsonObject>()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when(request.path) {
                    "/compat/v1/capabilities"->json("""{"model":"fixture","features":{},"endpoints":{}}""")
                    "/compat/api/jobs?include_disabled=true"->json("""{"jobs":[]}""")
                    "/compat/v1/chat/completions"->{
                        synchronized(bodies) {bodies+=Json.parseToJsonElement(request.body.readUtf8()).jsonObject}
                        MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"The color is teal.\"},\"finish_reason\":null}]}\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                    }
                    else->json("{}").setResponseCode(404)
                }
            }
            connect(server,"compat/","audit-compat-fixture")
            compose.runOnUiThread {vm.newChat();vm.send("Name one color")}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="response_finished"}
            assertTrue(runBlocking {app.database.dao().history(vm.selected.value).any {it.role=="assistant" && it.content=="The color is teal."}})
            compose.runOnUiThread {vm.send("Translate your previous answer")}
            compose.waitUntil(10000) {!vm.busy.value && synchronized(bodies) {bodies.size==2}}
            val roles=bodies.last()["messages"]!!.jsonArray.map {it.jsonObject.text("role")}
            // Observed defect: the saved assistant answer is absent from the next HTTP body.
            assertEquals(listOf("user","user"),roles)
        }
    }
    @Test fun changingConnectionDuringUnknownAcknowledgementSendsControlToTheNewProfile() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val wrongProfilePosts=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    val path=request.path.orEmpty()
                    return when {
                        path.endsWith("/v1/capabilities")->json("""{"features":{"run_unknown_resolution":true,"run_status":true},"endpoints":{"run_unknown_resolution":{"method":"POST","path":"/v1/runs/{run_id}/resolve-unknown"}}}""")
                        path.contains("/api/jobs")->json("""{"jobs":[]}""")
                        path=="/a/v1/runs/audit_run"->{entered.countDown();check(release.await(15,TimeUnit.SECONDS));json("""{"run_id":"audit_run","status":"unknown","admission_id":"cloned-admission","execution_generation":1,"pending_controls":[]}""")}
                        path=="/b/v1/runs/audit_run/resolve-unknown"->{
                            assertEquals("Bearer audit-b-fixture",request.getHeader("Authorization"))
                            val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                            assertEquals("cloned-admission",body.text("admission_id"));wrongProfilePosts.incrementAndGet()
                            json("""{"run_id":"audit_run","status":"interrupted"}""")
                        }
                        path=="/b/v1/runs/audit_run"->json("""{"run_id":"audit_run","status":"interrupted","output":"B-scope fixture output","pending_controls":[]}""")
                        else->json("{}").setResponseCode(404)
                    }
                }
            }
            try {
                connect(server,"a/","audit-a-fixture")
                val aScope=vm.profileScope.value
                val c=Conversation("audit-switch:$aScope",aScope,"api-a",runId="audit_run",status="unknown",admissionId="cloned-admission",executionGeneration=1)
                runBlocking {app.database.dao().save(c);app.database.dao().save(ChatMessage("audit-switch-answer",c.key,"assistant","A-scope fixture output"))}
                compose.runOnUiThread {vm.selected.value=c.key;vm.acknowledgeUnknown()}
                assertTrue(entered.await(10,TimeUnit.SECONDS));assertTrue(vm.controlBusy.value)
                connect(server,"b/","audit-b-fixture")
                assertNotEquals(aScope,vm.profileScope.value)
                release.countDown()
                compose.waitUntil(10000) {!vm.controlBusy.value && wrongProfilePosts.get()==1}
                // Same cloned run identities prove a valid operation can hit the wrong server/profile.
                assertEquals(1,wrongProfilePosts.get())
                assertEquals("B-scope fixture output",runBlocking {app.database.dao().history(c.key).single().content})
                runBlocking {app.database.dao().deleteConversation(c.key)}
            } finally {release.countDown()}
        }
    }
    @Test fun delayedStreamPromptResurrectsUnknownAndResolvedControlRows()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(app,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();val c=Conversation("audit-control","scope","api",runId="r",status="waiting_for_approval",lastEventSeq=4)
            dao.save(c)
            val prompt=PendingControl("p",c.key,"r","request",1,"approval","Read fixture file","[\"once\",\"deny\"]",false)
            for(state in listOf("unknown","resolved")) {
                dao.save(prompt.copy(state=state))
                dao.saveTurn(c.copy(lastEventSeq=5),ChatMessage("a",c.key,"assistant","Waiting"),prompt,null,"")
                // Observed defect: saveTurn ignores the protective state merge used by replaceControls.
                assertEquals("pending",dao.control(prompt.key)!!.state)
            }
        } finally {db.close()}
    }

    @Test fun jobPromptAcceptedByMobileButRejectedByServerIsRecordedAsUncertain()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(app,HermesDatabase::class.java).build()
        try {
            MockWebServer().use {server ->
                val body=org.hermesmobile.app.network.JobProtocol.body("Fixture", "x".repeat(6000),"0 9 * * *",true)
                assertEquals(6000,body.text("prompt").length)
                // Matches the 5000-character guard in both pinned upstream API snapshots.
                server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"Prompt must be at most 5000 characters"}"""))
                val api=org.hermesmobile.app.network.OkHttpHermesApiClient(ServerConfig(server.url("/").toString(),"audit-job-fixture",true),true)
                try {JobOperationsRepository(db.dao()).perform(api,"scope","create","",body);fail("Expected HTTP 400")}
                catch(e:org.hermesmobile.app.network.HermesHttpException) {assertEquals(400,e.code)}
                assertEquals("unknown",db.dao().unresolvedOperations("scope").single().state)
                assertEquals(1,server.requestCount)
            }
        } finally {db.close()}
    }
}
