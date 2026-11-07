package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HostCronRunsFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication

    private inner class Fixture:AutoCloseable {
        val server=credentials.managementServer()
        val writes=AtomicInteger()
        val requests=ConcurrentLinkedQueue<RecordedRequest>()
        val entered=CountDownLatch(1)
        val release=CountDownLatch(1)
        @Volatile var delayed=false
        @Volatile var history="""{"limit":20,"runs":[{"id":"conversation-run-fixture","source":"cron","profile":"worker","title":"Private run title fixture","preview":"Private run preview fixture","started_at":1794000000,"last_active":1794000020,"is_active":true,"model":"recorded-model-fixture","message_count":4,"tool_call_count":2},{"id":"cron_output:job-fixture:exec:0","source":"cron_output","profile":"worker","title":"Private output fixture","preview":"Private output error fixture","is_active":false}]}"""
        private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
        init {
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    requests.add(request)
                    if(request.method!="GET") {writes.incrementAndGet();return json("{}").setResponseCode(405)}
                    return when(request.path) {
                        "/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                        "/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password","display_name":"Run history fixture"}""")
                        "/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"},{"name":"other"}]}""")
                        "/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[]}""")
                        "/dashboard/api/cron/jobs?profile=worker"->json("""[{"id":"job-fixture","name":"History fixture","profile":"worker","state":"paused","enabled":false,"schedule_display":"Every hour"}]""")
                        "/dashboard/api/cron/jobs/job-fixture?profile=worker"->json("""{"id":"job-fixture","name":"History fixture","profile":"worker","state":"paused","enabled":false,"schedule_display":"Every hour"}""")
                        "/dashboard/api/cron/jobs/job-fixture/runs?limit=20&profile=worker"->{
                            entered.countDown()
                            if(delayed && !release.await(15,TimeUnit.SECONDS)) return json("{}").setResponseCode(504)
                            json(history)
                        }
                        else->if(request.path.orEmpty().startsWith("/inference/api/jobs?")) json("""{"jobs":[]}""") else json("{}").setResponseCode(404)
                    }
                }
            }
        }
        override fun close() {release.countDown();server.close()}
        fun verifyReads() {
            assertEquals(0,writes.get())
            val runRequests=requests.filter {it.path.orEmpty().contains("/runs?")}
            assertTrue(runRequests.isNotEmpty())
            runRequests.forEach {
                assertEquals("/dashboard/api/cron/jobs/job-fixture/runs?limit=20&profile=worker",it.path)
                assertEquals("Bearer management-runs-fixture",it.getHeader("Authorization"))
                assertNull(it.getHeader("Cookie"));assertEquals(0L,it.bodySize)
            }
            requests.filter {it.path.orEmpty().startsWith("/dashboard/")}.forEach {assertEquals("Bearer management-runs-fixture",it.getHeader("Authorization"))}
        }
    }

    private fun open(f:Fixture,multilingual:Boolean=false) {
        compose.waitUntil(10000) {!api.connecting.value}
        compose.runOnUiThread {
            api.configure(f.server.url("inference/").toString(),"inference-runs-fixture",true)
            api.updatePreferences(api.preferences.value.copy(chinese=multilingual,pinyin=multilingual))
        }
        compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && api.preferences.value.chinese==multilingual}
        val scope=api.profileScope.value
        app.credentials.saveManagement(scope,buildJsonObject {
            put("url",f.server.url("dashboard/").toString());put("profile","worker");put("debugHttp",true)
            put("tokens",ManagementTokens("management-runs-fixture","refresh-fixture","password","fixture-user",4102444800).json())
        })
        compose.runOnUiThread {management.bind(scope)}
        compose.waitUntil(5000) {management.state.value.signedIn}
        compose.runOnUiThread {management.refresh()}
        compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
        compose.runOnUiThread {compose.activity.setContent {
            val preferences by api.preferences.collectAsStateWithLifecycle()
            HermesAppTheme(preferences) {ManagementEntry(api,management)}
        }}
        compose.onNodeWithTag("management-open").performClick()
        compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("host-cron-open"))
        compose.onNodeWithTag("host-cron-open").performClick()
        compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.loaded}
        compose.onNodeWithTag("host-cron-list").performScrollToNode(hasTestTag("host-cron-detail-job-fixture"))
        compose.onNodeWithTag("host-cron-detail-job-fixture").performClick()
        compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.detail!=null}
    }
    private fun read() {
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-runs-load"))
        compose.onNodeWithTag("host-cron-runs-load").performClick()
    }
    private fun waitHistory()=compose.waitUntil(10000) {!management.state.value.busy && management.state.value.cron.runs!=null}
    private fun firstRow() {
        compose.onNodeWithTag("host-cron-detail").performScrollToNode(hasTestTag("host-cron-run-reveal-0"))
    }
    private fun screenshot(name:String) {
        val file=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }

    @Test fun scopedHistoryRequiresRevealAndRefreshRemovesPrivateText() {
        Fixture().use {f ->
            open(f,multilingual=true);read();waitHistory();firstRow()
            compose.onNodeWithText("Private run preview fixture").assertDoesNotExist()
            compose.onNodeWithText("recorded-model-fixture",substring=true).assertDoesNotExist()
            compose.onNodeWithTag("host-cron-run-reveal-0").assertIsDisplayed().performClick()
            compose.onNodeWithText("Private run title fixture").assertExists()
            compose.onNodeWithText("Private run preview fixture").assertExists()
            compose.onNodeWithText("Private run preview fixture").performScrollTo().assertIsDisplayed()
            screenshot("cron-runs-revealed")
            read();waitHistory();firstRow()
            compose.onNodeWithText("Private run preview fixture").assertDoesNotExist()
            assertEquals(listOf(false,true),management.state.value.cron.runs!!.rows.map {it.scriptOutput})
            screenshot("cron-runs-hidden")
            compose.onNodeWithTag("host-cron-detail-close").performClick()
            assertNull(management.state.value.cron.runs)
            compose.runOnUiThread {management.bind("unconfigured")}
            compose.waitUntil(5000) {management.state.value.apiScope=="unconfigured"}
            assertNull(management.state.value.cron.detail);assertTrue(management.state.value.cron.jobs.isEmpty());f.verifyReads()
        }
    }

    @Test fun foreignAndUnscopedRowsClearPreviouslyRevealedHistory() {
        Fixture().use {f ->
            open(f);read();waitHistory();firstRow()
            compose.onNodeWithTag("host-cron-run-reveal-0").assertIsDisplayed().performClick()
            compose.onNodeWithText("Private run preview fixture").assertExists()
            val foreign="""{"limit":20,"runs":[{"id":"foreign-run-fixture","profile":"other","source":"cron","preview":"Foreign private fixture"}]}"""
            val unscoped="""{"limit":20,"runs":[{"id":"cron_output:job-fixture:exec:0","source":"cron_output","preview":"Unscoped private fixture"}]}"""
            for(payload in listOf(foreign,unscoped)) {
                f.history=payload;read()
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.HostCronRunsInvalid}
                assertNull(management.state.value.cron.runs)
                compose.onNodeWithText("Private run preview fixture").assertDoesNotExist()
                compose.onNodeWithText("Foreign private fixture").assertDoesNotExist()
                compose.onNodeWithText("Unscoped private fixture").assertDoesNotExist()
            }
            screenshot("cron-runs-owner-refused");f.verifyReads()
        }
    }

    @Test fun delayedReplyCannotRestoreClosedOrChangedScope() {
        Fixture().use {f ->
            open(f);f.delayed=true;read()
            assertTrue(f.entered.await(5,TimeUnit.SECONDS));assertTrue(management.state.value.busy)
            compose.onNodeWithTag("host-cron-detail-close").performClick()
            assertNull(management.state.value.cron.detail);assertNull(management.state.value.cron.runs)
            f.release.countDown()
            compose.waitUntil(10000) {!management.state.value.busy}
            assertNull(management.state.value.cron.detail);assertNull(management.state.value.cron.runs)
            compose.runOnUiThread {management.bind("unconfigured")}
            compose.waitUntil(5000) {management.state.value.apiScope=="unconfigured"}
            assertNull(management.state.value.identity);assertNull(management.state.value.cron.runs)
            compose.onNodeWithText("Private run preview fixture").assertDoesNotExist();f.verifyReads()
        }
    }
}
