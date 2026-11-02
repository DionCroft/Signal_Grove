package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiscoveryUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun connect(server:MockWebServer,path:String) {
        compose.waitUntil(10000) {!vm.connecting.value}
        val url=server.url(path).toString()
        compose.runOnUiThread {vm.configure(url,"discovery-fixture",true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.url==url && vm.connectionStatus.value=="connected"}
    }
    @After fun reset() {compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {vm.preferences.value.demo && !vm.connecting.value}}
    private fun shot(name:String) {
        compose.waitForIdle();val file=File(compose.activity.getExternalFilesDir(null),"qa/discovery-$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun staleDiscoveryCannotReplaceTheNewConnectionsInventory() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path.orEmpty().endsWith("/v1/capabilities")->json("""{"endpoints":{"model_options":{"method":"GET","path":"/api/model/options"}}}""")
                    request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/a/api/model/options"->{entered.countDown();check(release.await(15,TimeUnit.SECONDS));json("""{"model":"a-model","provider":"a","providers":[]}""")}
                    request.path=="/b/api/model/options"->json("""{"model":"b-model","provider":"b","providers":[]}""")
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                var oldWork:Job?=null
                connect(server,"a/");compose.runOnUiThread {oldWork=vm.refreshDiscovery(DiscoveryResource.Models)}
                assertTrue(entered.await(10,TimeUnit.SECONDS))
                connect(server,"b/");compose.runOnUiThread {vm.refreshDiscovery(DiscoveryResource.Models)}
                compose.waitUntil(10000) {vm.discovery.value.results[DiscoveryResource.Models]?.text("model")=="b-model"}
                release.countDown();runBlocking {withTimeout(10000) {oldWork!!.join()}}
                compose.waitUntil(10000) {DiscoveryResource.Models !in vm.discovery.value.loading}
                assertEquals("b-model",vm.discovery.value.results[DiscoveryResource.Models]!!.text("model"))
            } finally {release.countDown()}
        }
    }
    @Test fun bilingualPinyinDiscoveryKeepsTheContentReachableOnSmallDisplays() {
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=true,pinyin=true))}
        compose.waitUntil(5000) {vm.preferences.value.chinese && vm.preferences.value.pinyin}
        compose.onAllNodesWithContentDescription(Words.Settings.localized(vm.preferences.value),useUnmergedTree=true).onLast().performClick()
        compose.onNodeWithText(Words.ServerDetails.localized(vm.preferences.value)).performScrollTo().performClick()
        compose.onNodeWithTag("discovery-tab-Readiness").performScrollTo().performClick()
        compose.waitUntil(5000) {vm.discovery.value.results.containsKey(DiscoveryResource.Readiness)}
        compose.onNodeWithTag("readiness-status").performScrollTo().assertIsDisplayed();shot("bilingual-pinyin")
    }
    @Test fun readinessAndDemoCatalogsRenderWithoutEquatingHttpSuccessWithReadiness() {
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when {
                    request.path=="/v1/capabilities"->json("""{"features":{"audio_api":false},"endpoints":{"health_detailed":{"method":"GET","path":"/health/detailed"}}}""")
                    request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/health/detailed"->json("""{"status":"degraded","version":"fixture","readiness":{"status":"degraded","reason":"Fixture provider unavailable"}}""")
                    else->json("{}").setResponseCode(404)
                }
            }
            connect(server,"/")
            val p=vm.preferences.value.copy(chinese=false,pinyin=false)
            compose.runOnUiThread {vm.updatePreferences(p)};compose.waitUntil(5000) {!vm.preferences.value.chinese}
            compose.onAllNodesWithContentDescription(Words.Settings.en,useUnmergedTree=true).onLast().performClick()
            compose.onNodeWithText(Words.ServerDetails.en).performScrollTo().performClick()
            compose.onNodeWithTag("capability-list").assertExists()
            compose.onNodeWithTag("discovery-tab-Readiness").performScrollTo().performClick()
            compose.waitUntil(10000) {vm.discovery.value.results.containsKey(DiscoveryResource.Readiness)}
            compose.onNodeWithTag("readiness-status").performScrollTo().assertTextEquals("degraded");shot("degraded")
            compose.runOnUiThread {vm.setDemo(true)};compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
            DiscoveryResource.entries.forEach {resource ->
                compose.onNodeWithTag("server-details-list").performScrollToIndex(1)
                compose.onNodeWithTag("discovery-tab-${resource.name}").performScrollTo().performClick()
                compose.waitUntil(5000) {vm.discovery.value.results.containsKey(resource)}
                compose.onNodeWithTag("server-details-list").assertExists();shot(resource.name.lowercase())
            }
        }
    }
}
