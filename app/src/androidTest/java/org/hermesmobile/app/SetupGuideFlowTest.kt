package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.data.AppPreferences
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
    private fun heading(word:Words,p:AppPreferences) {
        compose.onNodeWithText(word.en).assertExists()
        if(p.chinese) compose.onNodeWithText(word.zh).assertExists() else compose.onNodeWithText(word.zh).assertDoesNotExist()
        if(p.chinese && p.pinyin) compose.onNodeWithText(word.pin).assertExists() else compose.onNodeWithText(word.pin).assertDoesNotExist()
    }
    private fun withLanguage(chinese:Boolean,pinyin:Boolean,block:()->Unit) {
        compose.waitUntil(15000) {!vm.connecting.value}
        val app=compose.activity.application as HermesApplication
        val before=vm.preferences.value
        try {
            val expected=before.copy(chinese=chinese,pinyin=pinyin)
            runBlocking {app.preferences.update(expected)}
            compose.waitUntil(10000) {vm.preferences.value==expected}
            block()
        } finally {
            runBlocking {app.preferences.update(before)}
            compose.waitUntil(10000) {vm.preferences.value==before}
        }
    }
    private fun traverseGuide(prefix:String) {
        compose.waitUntil(15000) {!vm.connecting.value}
        val before=vm.preferences.value
        val scope=vm.profileScope.value
        open()
        heading(Words.SetupHost,before)
        compose.onNodeWithTag("setup-guide-next").performClick()
        heading(Words.SetupApi,before)
        compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextContains("API_SERVER_HOST=127.0.0.1",substring=true)
        capture("$prefix-api")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) {!vm.connecting.value && vm.preferences.value==before && vm.profileScope.value==scope}
        heading(Words.SetupApi,before)
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextContains("https://",substring=true)
        capture("$prefix-phone")
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextEquals("tailscale serve status")
        compose.onNodeWithTag("setup-guide-config").assertDoesNotExist()
        compose.onNodeWithTag("setup-guide-dashboard-start").assertDoesNotExist()
        compose.onNodeWithText(Words.SetupManagementSteps.en,substring=true).assertExists()
        compose.onNodeWithTag("setup-guide-next").performClick()
        heading(Words.SetupModels,before)
        capture("$prefix-models")
        compose.onNodeWithTag("setup-guide-next").performClick()
        heading(Words.SetupChats,before)
        compose.onNodeWithTag("setup-guide-next").performClick()
        heading(Words.SetupProjects,before)
        capture("$prefix-projects")
        compose.onNodeWithTag("setup-guide-next").performClick()
        compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextEquals("Reply only: Signal Grove connection OK. Do not use tools.")
        compose.onNodeWithText(Words.SetupTroubleshoot.en).performScrollTo()
        compose.onNodeWithTag("setup-guide-next").assertDoesNotExist()
        compose.onNodeWithTag("setup-guide-previous").performClick()
        heading(Words.SetupProjects,before)
        compose.onNodeWithTag("setup-guide-close").performClick()
        compose.onNodeWithTag("setup-guide").assertDoesNotExist()
        assertEquals(before,vm.preferences.value);assertEquals(scope,vm.profileScope.value)
    }
    @Test fun offlineGuideTraversesWithoutChangingConnectionAndRetainsPageOnRecreation() {
        withLanguage(false,false) {traverseGuide("setup-guide-english")}
    }
    @Test fun bilingualGuideTraversesAllPagesAndRetainsLanguageAndConnectionAfterRecreation() {
        withLanguage(true,true) {traverseGuide("setup-guide-bilingual")}
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
        } finally {runBlocking {app.preferences.update(before)};compose.waitUntil(10000) {vm.preferences.value==before}}
    }

    @Test fun newHostChoiceIsExplicitPersistsAndDoesNotChangeConnection() {
        withLanguage(false,false) {
            val before=vm.preferences.value;val scope=vm.profileScope.value
            open()
            compose.onNodeWithTag("setup-guide-existing").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextEquals("hermes --version\nhermes doctor")
            compose.onNodeWithTag("setup-guide-new").performScrollTo().performClick()
            compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextContains("hermes setup",substring=true)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) {!vm.connecting.value && vm.preferences.value==before}
            compose.onNodeWithTag("setup-guide-new").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("setup-guide-next").performClick()
            compose.onNodeWithTag("setup-guide-next").performClick()
            compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextContains("--https=443",substring=true)
            compose.onNodeWithTag("setup-guide-next").performClick()
            compose.onNodeWithTag("setup-guide-next").performClick()
            compose.onNodeWithTag("setup-guide-config").performScrollTo().assertTextContains("HERMES_DASHBOARD_BASIC_AUTH_SECRET=",substring=true)
            compose.onNodeWithTag("setup-guide-dashboard-start").performScrollTo().assertTextContains("hermes dashboard --host 127.0.0.1",substring=true)
            compose.onNodeWithTag("setup-guide-example").performScrollTo().assertTextContains("http://127.0.0.1:9119",substring=true)
            compose.onNodeWithTag("setup-guide-close").performClick()
            assertEquals(before,vm.preferences.value);assertEquals(scope,vm.profileScope.value)
        }
    }
    @Test fun contentsJumpsToHistoryProjectsAndFinishWithFullTranslatedActions() {
        withLanguage(true,true) {
            val before=vm.preferences.value;val scope=vm.profileScope.value
            open()
            compose.onNodeWithTag("setup-guide-contents").performClick()
            compose.onNodeWithTag("setup-guide-step-6").performScrollTo().performClick()
            heading(Words.SetupChats,before)
            compose.onNodeWithTag("setup-guide-contents").performClick()
            compose.onNodeWithTag("setup-guide-step-7").performScrollTo().performClick()
            heading(Words.SetupProjects,before)
            compose.onNodeWithTag("setup-guide-next").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("setup-guide-next").performClick()
            heading(Words.SetupVerify,before)
            compose.assertDialogActionCaptionVisible("setup-guide-finish")
            compose.onNodeWithTag("setup-guide-finish").assertHeightIsAtLeast(48.dp)
            capture("setup-guide-bilingual-finish")
            compose.onNodeWithTag("setup-guide-finish").performClick()
            compose.onNodeWithTag("setup-guide").assertDoesNotExist()
            assertEquals(before,vm.preferences.value);assertEquals(scope,vm.profileScope.value)
        }
    }
}
