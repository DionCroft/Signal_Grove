package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AppUpdateFlowTest {
    @get:Rule(order=0) val fixture=UpdateFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private fun exercise(mode:String) {
        MockWebServer().use {server->
            val downloads=AtomicInteger();val metadata=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
            val apk="invalid-APK-fixture".toByteArray();val digest=java.security.MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") {"%02x".format(it)}
            val payload=buildJsonObject {put("id",500);put("tag_name","v1.2.0");put("draft",false);put("prerelease",false);put("body","Fixture release notes");put("assets",buildJsonArray {add(buildJsonObject {put("name","hermes-agent-mobile-1.2.0-debug.apk");put("state","uploaded");put("size",apk.size);put("digest",if(mode=="missing") JsonNull else JsonPrimitive("sha256:$digest"));put("browser_download_url","https://github.com/DionCroft/hermes-agent-android/releases/download/v1.2.0/hermes-agent-mobile-1.2.0-debug.apk")})})}
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertNull(q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))
                return if(q.path.orEmpty().startsWith("/repos/")) {metadata.incrementAndGet();MockResponse().setHeader("Content-Type","application/json").setBody(payload.toString())}
                else {downloads.incrementAndGet();if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)};MockResponse().setBody(okio.Buffer().write(if(mode=="integrity") "changed-APK-fixture".toByteArray() else apk))}
            }}
            val base=server.url("/")
            val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).addInterceptor {chain->val q=chain.request();chain.proceed(q.newBuilder().url(base.newBuilder().encodedPath(q.url.encodedPath).encodedQuery(q.url.encodedQuery).build()).build())}.build()
            lateinit var vm:AppUpdateViewModel
            compose.runOnUiThread {
                vm=AppUpdateViewModel(compose.activity.application,AppUpdateRepository(compose.activity,GitHubUpdateClient(http=http)),false)
                compose.activity.setContent {HermesAppTheme(AppPreferences(chinese=false,pinyin=false,reducedMotion=true)) {AppUpdateHost(vm,mode=="busy") {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {AppUpdateSettings(mode=="busy")}}}}
            }
            try {
                compose.waitUntil(5000) {vm.state.value.phase==UpdatePhase.Idle};assertEquals(0,downloads.get());compose.onNodeWithTag("update-check").performScrollTo().performClick();compose.waitUntil(10000) {vm.state.value.phase==UpdatePhase.Available};assertEquals(1,metadata.get());assertEquals(0,downloads.get())
                if(mode=="missing") {compose.onNodeWithTag("update-review-open").performScrollTo().assertIsNotEnabled();return@use}
                compose.onNodeWithTag("update-review-open").performScrollTo().performClick();compose.onNodeWithTag("update-review-cancel").performClick();assertEquals(0,downloads.get());compose.onNodeWithTag("update-review-open").performScrollTo().performClick()
                val shot=File(compose.activity.getExternalFilesDir(null),"qa/app-update-$mode-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                compose.onNodeWithTag("update-download-confirm").performClick()
                if(mode=="cancel") {compose.waitUntil(10000) {entered.count==0L};compose.onNodeWithTag("update-cancel").performScrollTo().performClick();release.countDown();compose.waitUntil(10000) {vm.state.value.phase==UpdatePhase.Available};assertNull(vm.state.value.ready);assertFalse(vm.state.value.installRequested);return@use}
                compose.waitUntil(10000) {vm.state.value.phase==UpdatePhase.Available && vm.state.value.notice!=null};assertEquals(1,downloads.get());assertNull(vm.state.value.ready);assertFalse(vm.state.value.installerOpen);assertEquals(if(mode=="integrity") Words.UpdateIntegrity else Words.UpdatePackage,vm.state.value.notice)
            } finally {release.countDown();compose.runOnUiThread {vm.cancel()};kotlinx.coroutines.runBlocking {vm.awaitOperation()}}
        }
    }
    @Test fun releaseReviewCancelDoesNotDownloadAndInvalidApkCannotInstall()=exercise("invalid")
    @Test fun changedChecksumCannotInstall()=exercise("integrity")
    @Test fun cancellationRetainsNoInstallApproval()=exercise("cancel")
    @Test fun missingDigestExplainsUnavailableAutomaticDownload()=exercise("missing")
}
