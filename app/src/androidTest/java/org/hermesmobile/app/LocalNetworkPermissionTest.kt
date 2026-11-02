package org.hermesmobile.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

@SdkSuppress(minSdkVersion=37)
class LocalNetworkPermissionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun systemButton(id:String) {
        val info=automation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.serviceInfo=info
        val deadline=SystemClock.elapsedRealtime()+8000
        while(SystemClock.elapsedRealtime()<deadline) {
            val root=automation.rootInActiveWindow
            for(pkg in listOf("com.android.permissioncontroller","com.google.android.permissioncontroller")) {
                val button=root?.findAccessibilityNodeInfosByViewId("$pkg:id/$id")?.firstOrNull()
                if(button!=null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            }
            SystemClock.sleep(100)
        }
        fail("Android permission button was not shown: $id")
    }
    @Test fun deniedLocalAccessSendsNothingThenExplicitGrantAllowsTheConnection() {
        val vm=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
        compose.waitUntil(10000) {!vm.connecting.value}
        // Run this case with pm revoke before instrumentation; never revoke a running test UID.
        assumeFalse("Requires an initially denied permission",LocalNetworkAccess.granted(compose.activity))
        val address=NetworkInterface.getNetworkInterfaces().toList().flatMap {it.inetAddresses.toList()}.first {it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress}
        MockWebServer().use {server ->
            server.start(InetAddress.getByName("0.0.0.0"),0)
            val config=ServerConfig("http://${address.hostAddress}:${server.port}/","lan-fixture",true)
            val api=HermesConnectionRepository(compose.activity).client(config)
            assertThrows(LocalNetworkPermissionException::class.java) {runBlocking {api.capabilities()}}
            assertEquals(0,server.requestCount)
            compose.onAllNodesWithContentDescription(Words.Settings.localized(vm.preferences.value),useUnmergedTree=true).onLast().performClick()
            compose.onNodeWithTag("lan-permission").performScrollTo().performClick()
            systemButton("permission_deny_button")
            compose.waitForIdle();assertFalse(LocalNetworkAccess.granted(compose.activity))
            compose.onNodeWithTag("lan-permission").performScrollTo().performClick()
            systemButton("permission_allow_button")
            compose.waitUntil(5000) {LocalNetworkAccess.granted(compose.activity)}
            compose.onNodeWithTag("lan-permission").assertIsNotEnabled()
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"model":"lan-fixture","features":{},"endpoints":{}}"""))
            assertEquals("lan-fixture",runBlocking {api.capabilities()}.model)
            assertEquals(1,server.requestCount)
            val file=File(compose.activity.getExternalFilesDir(null),"qa/android17-lan.png");file.parentFile!!.mkdirs()
            automation.takeScreenshot().let {bitmap -> file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        }
    }
}
