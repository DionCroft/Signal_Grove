package org.hermesmobile.app

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic endpoints only. Never run these profile-changing fixtures on an owner's configured phone. */
class ModelStatusFlowTest {
    @get:Rule(order=0) val credentialFixture=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private fun json(value:String)=MockResponse().setHeader("Content-Type","application/json").setBody(value)
    private fun caps(path:String="/api/model/runtime-status",method:String="GET")=json("""{"features":{"model_runtime_status_v1":true},"endpoints":{"model_runtime_status":{"method":"$method","path":"$path"}}}""")
    private fun observed(model:String)=json("""{"object":"signalgrove.model_runtime","schema_version":1,"observed_at":123000,"status":"ready","models":[{"provider":"fixture","model":"$model","state":"ready","verified":true}]}""")
    private fun connect(server:MockWebServer,path:String) {
        compose.waitUntil(15000) {!vm.connecting.value}
        compose.runOnUiThread {compose.activity.setContent {Text("Synthetic model status fixture")}}
        val url=server.url(path).toString()
        compose.runOnUiThread {vm.configure(url,"model-status-fixture",true)}
        compose.waitUntil(15000) {!vm.connecting.value && vm.preferences.value.url==url && vm.connectionStatus.value=="connected"}
    }
    @After fun reset() {compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(15000) {vm.preferences.value.demo && !vm.connecting.value}}
    private fun waitRead() {compose.waitUntil(15000) {DiscoveryResource.RuntimeModels !in vm.discovery.value.loading}}
    @Test fun duplicateReadsShareOneJobAndForegroundRefreshHonorsTheInterval() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val calls=AtomicInteger()
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/work/v1/capabilities"->caps()
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path=="/work/api/model/runtime-status"->{assertEquals("GET",request.method);assertEquals("Bearer model-status-fixture",request.getHeader("Authorization"));if(calls.incrementAndGet()==1) {entered.countDown();check(release.await(20,TimeUnit.SECONDS))};observed("served")}
                else->json("{}").setResponseCode(404)
            }}
            try {
                connect(server,"work/");var first:Job?=null;var second:Job?=null
                compose.runOnUiThread {first=vm.refreshDiscovery(DiscoveryResource.RuntimeModels);second=vm.refreshDiscovery(DiscoveryResource.RuntimeModels,true)}
                assertNotNull(first);assertSame(first,second);assertTrue(entered.await(10,TimeUnit.SECONDS))
                release.countDown();runBlocking {withTimeout(15000) {first!!.join()}};waitRead();assertEquals(1,calls.get())
                val at=vm.discovery.value.requestedAt.getValue(DiscoveryResource.RuntimeModels)
                compose.runOnUiThread {vm.refreshModelStatus(now=at+1000)};assertEquals(1,calls.get())
                compose.runOnUiThread {vm.refreshModelStatus(true)};compose.waitUntil(15000) {calls.get()==2};waitRead()
                assertEquals(2,calls.get())
            } finally {release.countDown()}
        }
    }
    @Test fun unsupportedOrRedirectedCapabilityIsNotRequested() {
        val calls=AtomicInteger()
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path.orEmpty().endsWith("/v1/capabilities")->caps("/other-private-resource","POST")
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                else->{calls.incrementAndGet();json("{}").setResponseCode(404)}
            }}
            connect(server,"safe/");var work:Job?=null
            compose.runOnUiThread {work=vm.refreshDiscovery(DiscoveryResource.RuntimeModels,true);vm.refreshModelStatus(true)}
            assertNull(work);assertEquals(0,calls.get());assertFalse(vm.discovery.value.results.containsKey(DiscoveryResource.RuntimeModels))
        }
    }
    @Test fun lateModelObservationCannotReplaceAnotherConnectionsStatus() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path.orEmpty().endsWith("/v1/capabilities")->caps()
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path=="/a/api/model/runtime-status"->{entered.countDown();check(release.await(20,TimeUnit.SECONDS));observed("model-a")}
                request.path=="/b/api/model/runtime-status"->observed("model-b")
                else->json("{}").setResponseCode(404)
            }}
            try {
                connect(server,"a/");var old:Job?=null;compose.runOnUiThread {old=vm.refreshDiscovery(DiscoveryResource.RuntimeModels)};assertTrue(entered.await(10,TimeUnit.SECONDS))
                connect(server,"b/");compose.runOnUiThread {vm.refreshModelStatus(true)}
                compose.waitUntil(15000) {vm.discovery.value.results[DiscoveryResource.RuntimeModels]?.let {ModelRuntimeStatus.parse(it).verifiedModels.single().model}=="model-b"}
                release.countDown();runBlocking {withTimeout(15000) {old!!.join()}};waitRead()
                assertEquals("model-b",ModelRuntimeStatus.parse(vm.discovery.value.results.getValue(DiscoveryResource.RuntimeModels)).verifiedModels.single().model)
            } finally {release.countDown()}
        }
    }
    @Test fun failedOrMalformedRefreshRetainsTheLastObservationWithAnError() {
        val calls=AtomicInteger()
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path.orEmpty().endsWith("/v1/capabilities")->caps()
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path=="/api/model/runtime-status"->when(calls.incrementAndGet()) {1->observed("last-verified");2->json("{}").setResponseCode(503);else->json("""{"object":"signalgrove.model_runtime","schema_version":1,"observed_at":123000,"status":"ready","models":[]}""")}
                else->json("{}").setResponseCode(404)
            }}
            connect(server,"/");compose.runOnUiThread {vm.refreshModelStatus(true)};compose.waitUntil(15000) {calls.get()==1};waitRead()
            val previous=vm.discovery.value.results.getValue(DiscoveryResource.RuntimeModels);val at=vm.discovery.value.fetchedAt.getValue(DiscoveryResource.RuntimeModels)
            for(expected in 2..3) {
                compose.runOnUiThread {vm.refreshModelStatus(true)};compose.waitUntil(15000) {calls.get()==expected};waitRead()
                assertEquals(previous,vm.discovery.value.results[DiscoveryResource.RuntimeModels]);assertEquals(at,vm.discovery.value.fetchedAt[DiscoveryResource.RuntimeModels]);assertTrue(vm.discovery.value.errors.containsKey(DiscoveryResource.RuntimeModels))
            }
            assertTrue(RuntimeChoice.saved(vm.preferences.value.runtimeChoices,vm.profileScope.value).isDefault)
        }
    }
}
