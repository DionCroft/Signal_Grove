package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SetupGuideFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private fun open() {
        compose.waitUntil(15000) {!vm.connecting.value}
        compose.onNodeWithContentDescription(Words.Settings.localized(vm.preferences.value),useUnmergedTree=true).performClick()
        compose.onNodeWithTag("setup-guide-open").performScrollTo().performClick()
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val out=File(compose.activity.getExternalFilesDir(null),"qa/$name.png");out.parentFile!!.mkdirs()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        out.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun offlineGuideTraversesWithoutChangingConnectionAndRetainsPageOnRecreation() {
        compose.waitUntil(15000) {!vm.connecting.value}
        val before=vm.preferences.value
        val scope=vm.profileScope.value
        open()
        compose.onNodeWithText(Words.SetupHost.localized(before)).assertExists()
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithText(Words.SetupApi.localized(before)).assertExists()
        compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextContains("API_SERVER_HOST=127.0.0.1",substring=true)
        capture("setup-guide-api")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(Words.SetupApi.localized(before)).assertExists()
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextEquals("https://your-host.your-tailnet.ts.net/")
        capture("setup-guide-phone")
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextContains("http://127.0.0.1:9119",substring=true)
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithText(Words.SetupTroubleshoot.en).performScrollTo()
        compose.onNodeWithTag("setup-guide-next").assertDoesNotExist()
        compose.onNodeWithTag("setup-guide-previous").performClick()
        compose.onNodeWithText(Words.SetupDashboard.localized(before)).assertExists()
        compose.onNodeWithTag("setup-guide-close").performClick()
        compose.onNodeWithTag("setup-guide").assertDoesNotExist()
        assertEquals(before,vm.preferences.value);assertEquals(scope,vm.profileScope.value)
    }
    @Test fun chinesePinyinGuideKeepsNavigationReachableAndCanBeDismissed() {
        compose.waitUntil(15000) {!vm.connecting.value}
        val app=compose.activity.application as HermesApplication
        val before=vm.preferences.value
        try {
            runBlocking {app.preferences.update(before.copy(chinese=true,pinyin=true))}
            compose.waitUntil(10000) {vm.preferences.value.chinese && vm.preferences.value.pinyin}
            open()
            compose.onNodeWithText(Words.SetupHost.zh).assertExists()
            compose.onNodeWithTag("setup-guide-next").performClick()
            compose.onNodeWithText(Words.SetupApi.pin).assertExists()
            compose.onNodeWithTag("setup-guide-example").performScrollTo().assertIsDisplayed()
            capture("setup-guide-chinese-api")
            compose.onNodeWithTag("setup-guide-next").performClick()
            compose.onNodeWithTag("setup-guide-next").performClick()
            compose.onNodeWithTag("setup-guide-example").performScrollTo().assertIsDisplayed()
            capture("setup-guide-chinese-phone")
            compose.onNodeWithTag("setup-guide-close").assertIsDisplayed().performClick()
            compose.onNodeWithTag("setup-guide").assertDoesNotExist()
        } finally {runBlocking {app.preferences.update(before)}}
    }
}
