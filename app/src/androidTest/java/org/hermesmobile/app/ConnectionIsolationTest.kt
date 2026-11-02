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

/** Regression tests for connection-scoped control operations. Synthetic traffic only. */
class ConnectionIsolationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    @After fun reset() {compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {vm.preferences.value.demo && !vm.controlBusy.value}}
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun connect(server:MockWebServer,path:String,token:String) {
        compose.waitUntil(10000) {!vm.connecting.value}
        val url=server.url(path).toString()
        compose.runOnUiThread {vm.configure(url,token,true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected" && vm.preferences.value.url==url}
    }
    @Test fun connectionChangesAreBlockedUntilUnknownAcknowledgementSettlesOnItsOriginalServer() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val wrongProfilePosts=AtomicInteger();val originalPosts=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    val path=request.path.orEmpty()
                    return when {
                        path.endsWith("/v1/capabilities")->json("""{"features":{"run_unknown_resolution":true,"run_status":true},"endpoints":{"run_unknown_resolution":{"method":"POST","path":"/v1/runs/{run_id}/resolve-unknown"}}}""")
                        path.contains("/api/jobs")->json("""{"jobs":[]}""")
                        path=="/a/v1/runs/audit_run"->{entered.countDown();check(release.await(15,TimeUnit.SECONDS));json(if(originalPosts.get()==0) """{"run_id":"audit_run","status":"unknown","admission_id":"cloned-admission","execution_generation":1,"pending_controls":[]}""" else """{"run_id":"audit_run","status":"interrupted","output":"A-scope fixture output","pending_controls":[]}""")}
                        path=="/a/v1/runs/audit_run/resolve-unknown"->{originalPosts.incrementAndGet();json("""{"run_id":"audit_run","status":"interrupted"}""")}
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
                compose.runOnUiThread {vm.configure(server.url("b/").toString(),"audit-b-fixture",true);vm.setDemo(true);vm.forgetServer();vm.newChat()}
                assertEquals(aScope,vm.profileScope.value);assertEquals(c.key,vm.selected.value)
                release.countDown()
                compose.waitUntil(10000) {!vm.controlBusy.value && originalPosts.get()==1}
                // No request or cached answer crosses the connection boundary.
                assertEquals(0,wrongProfilePosts.get())
                assertEquals("A-scope fixture output",runBlocking {app.database.dao().history(c.key).single().content})
                runBlocking {app.database.dao().deleteConversation(c.key)}
            } finally {release.countDown()}
        }
    }

    @Test fun restoringConnectionCannotRaceASecondConfiguration() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val calls=AtomicInteger();val otherCalls=AtomicInteger();val store=androidx.lifecycle.ViewModelStore()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/saved/v1/capabilities"->{if(calls.incrementAndGet()>1) {entered.countDown();check(release.await(15,TimeUnit.SECONDS))};json("""{"features":{},"endpoints":{}}""")}
                    request.path=="/other/v1/capabilities"->{otherCalls.incrementAndGet();json("{}")} 
                    request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                connect(server,"saved/","restore-fixture");val partition=vm.profileScope.value
                lateinit var restored:HermesViewModel
                compose.runOnUiThread {restored=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[HermesViewModel::class.java]}
                assertTrue(entered.await(10,TimeUnit.SECONDS));assertTrue(restored.connecting.value)
                compose.runOnUiThread {restored.configure(server.url("other/").toString(),"other-fixture",true)}
                release.countDown();compose.waitUntil(10000) {!restored.connecting.value}
                assertEquals(partition,restored.profileScope.value);assertEquals(0,otherCalls.get())
            } finally {release.countDown();compose.runOnUiThread {store.clear()}}
        }
    }
    @Test fun offlineActivationKeepsTheSavedConnectionsCacheVisible() {
        val server=MockWebServer()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path.orEmpty().endsWith("/v1/capabilities")->json("""{"features":{},"endpoints":{}}""")
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                else->json("{}").setResponseCode(404)
            }
        }
        try {
            connect(server,"saved/","offline-fixture")
            val partition=vm.profileScope.value;val cached=Conversation("offline:$partition",partition,"api_saved","Offline cached conversation")
            runBlocking {app.database.dao().save(cached)}
            compose.waitUntil(5000) {vm.jobsStatus.value!="loading"}
            compose.runOnUiThread {vm.setDemo(true)};compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
            server.close()
            compose.runOnUiThread {vm.setDemo(false)}
            compose.waitUntil(10000) {!vm.connecting.value && !vm.preferences.value.demo && vm.connectionStatus.value=="offline"}
            assertEquals(partition,vm.profileScope.value)
            compose.waitUntil(5000) {vm.conversations.value.any {it.key==cached.key}}
            runBlocking {app.database.dao().deleteConversation(cached.key)}
        } finally {server.close()}
    }
    @Test fun olderRunRepliesCannotOverwriteANewerSubmissionInTheSameConversation()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(app,HermesDatabase::class.java).build()
        try {
            val dao=db.dao()
            val old=Conversation("reused","scope","api",runId="old",pendingKey="old-key",pendingAt=1,status="running")
            val next=old.copy(runId="next",pendingKey="next-key",pendingAt=2,status="running")
            val answer=ChatMessage("next-key_assistant",next.key,"assistant","New output",status="running")
            dao.save(next);dao.save(answer)
            assertNull(dao.applyRunSnapshot(old,Json.parseToJsonElement("""{"status":"completed","output":"Old output"}""").jsonObject))
            dao.saveTurn(old.copy(status="completed"),ChatMessage("old-key_assistant",old.key,"assistant","Old stream output"),null,null,"")
            assertEquals(next,dao.conversation(next.key));assertEquals(listOf(answer),dao.history(next.key))
            dao.save(next.copy(status="completed"))
            assertNull(dao.applyRunSnapshot(next,Json.parseToJsonElement("""{"status":"running"}""").jsonObject))
            assertEquals("completed",dao.conversation(next.key)!!.status)
        } finally {db.close()}
    }
    @Test fun delayedJobDetailsAreDiscardedAfterAConnectionChange() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path.orEmpty().endsWith("/v1/capabilities")->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/a/api/jobs/abcdef123456"->{entered.countDown();check(release.await(15,TimeUnit.SECONDS));json("""{"job":{"id":"abcdef123456","name":"A only"}}""")}
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                connect(server,"a/","fixture-a")
                compose.waitUntil(10000) {vm.jobsStatus.value=="ready"}
                val result=java.util.concurrent.CompletableFuture.supplyAsync {runBlocking {runCatching {vm.jobDetail("abcdef123456")}}}
                assertTrue(entered.await(10,TimeUnit.SECONDS))
                connect(server,"b/","fixture-b");release.countDown()
                assertTrue(result.get(10,TimeUnit.SECONDS).exceptionOrNull() is IllegalStateException)
            } finally {release.countDown()}
        }
    }
}
