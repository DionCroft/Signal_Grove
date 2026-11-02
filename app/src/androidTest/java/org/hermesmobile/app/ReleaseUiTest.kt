package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReleaseUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private fun text(w:Words)=w.localized(vm.preferences.value)
    private fun go(w:Words)=compose.onAllNodesWithContentDescription(text(w),useUnmergedTree=true).onLast().performClick()
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(compose.activity.getExternalFilesDir(null),"qa/stable-$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun communityScreensRenderAcrossPalettesWithExplicitControlAndPrivacyCopy() {
        compose.runOnUiThread {vm.setDemo(true);vm.newChat();vm.updatePreferences(AppPreferences(chinese=false,pinyin=false))}
        compose.waitUntil(5000) {!vm.preferences.value.chinese && vm.connectionStatus.value=="demo" && vm.jobsStatus.value=="ready"}
        HermesTheme.entries.forEach {theme ->
            compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(theme=theme.name))}
            compose.waitUntil(5000) {vm.preferences.value.theme==theme.name}
            go(Words.Home);compose.onNodeWithText("A little space to think.").assertExists();shot("home-${theme.name.lowercase()}")
        }
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(theme="LAVENDER"))}
        go(Words.Chat);compose.onNodeWithText(text(Words.DemoApproval)).performClick()
        compose.waitUntil(10000) {vm.controls.value.isNotEmpty()};shot("approval")
        compose.onNodeWithText(text(Words.Deny)).performClick();compose.waitUntil(10000) {!vm.busy.value}
        compose.onNodeWithContentDescription(text(Words.ToolActivity),substring=true).performClick();shot("tools");compose.onNodeWithText(text(Words.Close)).performClick()
        go(Words.Voice);compose.onNodeWithText(text(Words.LocalSpeech)).assertExists();shot("voice")
        go(Words.Automation);shot("automation")
        go(Words.Settings);compose.onNodeWithText(text(Words.TaskFiles)).performScrollTo();shot("task-files")
        compose.onNodeWithText(text(Words.Notifications)).performScrollTo();shot("notifications")
        compose.runOnUiThread {vm.updatePreferences(AppPreferences())}
    }
}
