package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.hermesmobile.app.data.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RuntimeSelectionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun text(word:Words)=word.localized(vm.preferences.value)
    private fun capturePreset(tag:String,name:String) {
        val file=File(compose.activity.getExternalFilesDir(null),"qa/"+name+".png")
        file.parentFile!!.mkdirs()
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("The native screenshot was unavailable.")
        try {file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}
        finally {bitmap.recycle()}
    }

    @After fun reset() {
        compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice())}
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.isDefault}
    }
    @Test fun choicesStaySeparateAndStaleAppearanceUpdatesCannotEraseThem()=runBlocking {
        val before=app.preferences.flow.first()
        val a="runtime-a-${java.util.UUID.randomUUID()}";val b="runtime-b-${java.util.UUID.randomUUID()}"
        val first=RuntimeChoice("alpha","p");val second=RuntimeChoice("beta","q",reasoning=false)
        try {
            app.preferences.runtime(a,first);app.preferences.runtime(b,second)
            app.preferences.update(before.copy(theme="JADE"))
            val saved=app.preferences.flow.first()
            assertEquals(first,RuntimeChoice.saved(saved.runtimeChoices,a));assertEquals(second,RuntimeChoice.saved(saved.runtimeChoices,b))
        } finally {app.preferences.runtime(a,RuntimeChoice());app.preferences.runtime(b,RuntimeChoice());app.preferences.update(before)}
    }
    @Test fun scrollingBackKeepsTheReadersPositionWhenMoreReplyTextArrives()=runBlocking {
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)};compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
        val c=Conversation("follow-${java.util.UUID.randomUUID()}","demo","api-follow",pendingKey="follow",status="running",transport="demo")
        val last=ChatMessage("follow-last",c.key,"assistant","Streaming reply line with useful context.\n".repeat(8),status="running")
        try {
            app.database.dao().save(c)
            app.database.dao().saveMessages((0..14).map {ChatMessage("follow-$it",c.key,"user","Earlier note $it",timestamp=it.toLong())}+last)
            compose.onAllNodesWithContentDescription(text(Words.Chat),useUnmergedTree=true).onLast().performClick()
            compose.runOnUiThread {vm.select(c.key)}
            compose.waitUntil(5000) {vm.messages.value.size==16 && vm.selectedConversation.value?.key==c.key}
            compose.waitForIdle()
            fun position()=compose.onNodeWithTag("chat-messages").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            val initial=position()
            compose.onNodeWithTag("chat-messages").performTouchInput {swipeDown(durationMillis=500)}
            compose.waitForIdle();val before=position();assertTrue("The reader must actually move back",before<initial)
            val expanded=last.copy(content=last.content+"Additional server output.\n".repeat(20))
            app.database.dao().save(expanded)
            compose.waitUntil(5000) {vm.messages.value.last().content==expanded.content}
            compose.waitForIdle();assertTrue("Streaming must not jump back to the reply end",position()<=before+.05f)
        } finally {compose.runOnUiThread {vm.newChat()};app.database.dao().deleteConversation(c.key)}
    }
    @Test fun modelOptionsCanBeRequestedWithoutOverridingTheServerModel() {
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)};compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
        compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice())}
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.isDefault}
        compose.onAllNodesWithContentDescription(text(Words.Chat),useUnmergedTree=true).onLast().performClick()
        compose.onNodeWithContentDescription(text(Words.Models)).performClick()
        compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasText(text(Words.ReasoningSetting),substring=true))
        compose.onNodeWithText(text(Words.ReasoningSetting),substring=true).performClick()
        compose.onNodeWithText(text(Words.Disabled)).performClick()
        compose.onNodeWithText(text(Words.ApplySettings)).performClick()
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.reasoning==false}
        assertEquals("",vm.requestedRuntime.value.model);assertEquals("",vm.requestedRuntime.value.provider)
    }
    @Test fun reviewedManualChoicesAreSavedAndFrozenIntoTheNextTurn() {
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=true,pinyin=true))}
        compose.waitUntil(5000) {vm.preferences.value.chinese && vm.preferences.value.pinyin}
        compose.onAllNodesWithContentDescription(text(Words.Chat),useUnmergedTree=true).onLast().performClick()
        compose.onNodeWithContentDescription(text(Words.Models)).performClick()
        compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-manual"))
        compose.onNodeWithTag("runtime-manual").performClick()
        compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-model"))
        compose.onNodeWithTag("runtime-model").performTextReplacement("reviewed-model")
        compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-provider"))
        compose.onNodeWithTag("runtime-provider").performTextReplacement("reviewed-provider")
        val picker=File(compose.activity.getExternalFilesDir(null),"qa/runtime-picker.png");picker.parentFile!!.mkdirs()
        picker.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithText(text(Words.ApplySettings)).performClick()
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.model=="reviewed-model"}
        compose.runOnUiThread {vm.newChat();vm.send("Harmless model selection fixture")}
        compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
        val c=runBlocking {app.database.dao().conversation(vm.selected.value)!!}
        val request=app.requests.read(c.scope,c.key,c.pendingKey,c.pendingRequestHash)
        compose.onNodeWithTag("message-body-${c.pendingKey}_assistant").assertIsDisplayed()
        assertEquals("reviewed-model",request.body.text("model"));assertEquals("reviewed-provider",request.body.text("provider"))
        val file=File(compose.activity.getExternalFilesDir(null),"qa/runtime-selected.png");file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithContentDescription(text(Words.Models)).performClick()
        compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasText(text(Words.ServerDefault)))
        compose.onNodeWithText(text(Words.ServerDefault)).performClick()
        compose.onNodeWithText(text(Words.ApplySettings)).performClick()
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.isDefault}
    }

    @Test fun automaticModeFreezesTheVerifiedDemoPairWithoutPinningPreferences()=runBlocking {
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo}
        compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice.automatic())}
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.followServing}
        compose.runOnUiThread {vm.newChat();vm.send("Automatic selection fixture")}
        compose.waitUntil(15000) {!vm.busy.value && vm.runStatus.value=="completed"}
        val c=app.database.dao().conversation(vm.selected.value)!!
        val frozen=app.requests.read(c.scope,c.key,c.pendingKey,c.pendingRequestHash)
        assertEquals("demo-model",frozen.body.text("model"));assertEquals("demo",frozen.body.text("provider"))
        assertFalse(frozen.body.containsKey("_signal_grove_model_mode"))
        val stored=RuntimeChoice.saved(app.preferences.flow.first().runtimeChoices,c.scope)
        assertTrue(stored.followServing);assertTrue(stored.model.isBlank());assertTrue(stored.provider.isBlank())
    }
    @Test fun localServiceAndEffortControlsArePickersWithNoTextEntry() {
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
        compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice())}
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.isDefault}
        compose.onAllNodesWithContentDescription(text(Words.Chat),useUnmergedTree=true).onLast().performClick()
        compose.onNodeWithContentDescription(text(Words.Models)).performClick()
        compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-tier"))
        compose.onNodeWithTag("runtime-tier").assertHasClickAction().assert(hasSetTextAction().not()).performClick()
        compose.onNodeWithTag("runtime-tier-default").assertIsDisplayed()
        capturePreset("runtime-tier-options","runtime-tier-presets")
        compose.onNodeWithTag("runtime-tier-priority").assertDoesNotExist()
        compose.onNodeWithTag("runtime-tier-default").performClick()
        compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-effort"))
        compose.onNodeWithTag("runtime-effort").assertHasClickAction().assert(hasSetTextAction().not()).performClick()
        compose.onNodeWithTag("runtime-effort-default").assertIsDisplayed()
        compose.onNodeWithTag("runtime-effort-high").assertDoesNotExist()
        compose.onNodeWithTag("runtime-effort-default").performClick()
        compose.onNodeWithTag("runtime-apply").performClick()
    }
    @Test fun advertisedReasoningLevelsCanBePickedAndSavedWithoutTyping() {
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(5000) {!vm.connecting.value && vm.preferences.value.demo}
        compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice())}
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.isDefault}
        compose.onAllNodesWithContentDescription(text(Words.Chat),useUnmergedTree=true).onLast().performClick()
        compose.onNodeWithContentDescription(text(Words.Models)).performClick()
        compose.waitUntil(10000) {DiscoveryResource.Models !in vm.discovery.value.loading && vm.discovery.value.results.containsKey(DiscoveryResource.Models)}
        // Explicit UI metadata fixture on Demo only; no owner credentials or real model are used.
        compose.runOnUiThread {
            check(vm.preferences.value.demo)
            val old=vm.discovery.value
            val payload=kotlinx.serialization.json.Json.parseToJsonElement("""{"model":"demo-model","provider":"demo","providers":[{"slug":"demo","authenticated":true,"models":["demo-model"],"capabilities":{"demo-model":{"reasoning":true,"fast":false}}}]}""").jsonObject
            vm.discovery.value=old.copy(results=old.results+(DiscoveryResource.Models to payload))
        }
        compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-effort"))
        compose.onNodeWithTag("runtime-effort").performClick()
        capturePreset("runtime-effort-options","runtime-effort-presets")
        compose.onNodeWithTag("runtime-effort-options").performScrollToNode(hasTestTag("runtime-effort-high"))
        compose.onNodeWithTag("runtime-effort-high").performClick()
        compose.onNodeWithTag("runtime-apply").performClick()
        compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.effort=="high"}
        assertEquals(true,vm.requestedRuntime.value.reasoning)
        assertEquals("",vm.requestedRuntime.value.model);assertEquals("",vm.requestedRuntime.value.provider)
    }
}
