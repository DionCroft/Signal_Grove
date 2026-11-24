package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Milestone03Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val caseName=org.junit.rules.TestName()
    private var inputTrace:AppRouteTrace?=null
    private val app get()=compose.activity.application as HermesApplication
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    @Before fun reset() {runBlocking {app.preferences.update(AppPreferences());app.database.dao().clearChats()}
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="demo" && vm.preferences.value.demo}
        compose.waitForIdle()
        compose.runOnUiThread {inputTrace=AppRouteTrace(compose.activity)}
    }
    @After fun retainInputTrace() {
        val trace=inputTrace ?: return
        var captured:org.json.JSONObject?=null
        compose.runOnUiThread {trace.close();captured=trace.snapshot(caseName.methodName)}
        val record=checkNotNull(captured);val text=record.toString()
        check(text.toByteArray(Charsets.UTF_8).size<=65536)
        val file=java.io.File(compose.activity.getExternalFilesDir(null),"qa/app-route-trace-"+java.util.UUID.randomUUID()+".json")
        file.parentFile!!.mkdirs();file.writeText(text)
        assertTrue("The original route observer must be restored",record.getBoolean("originalObserverRestored"))
        assertEquals(0,record.getInt("observationFailures"))
    }
    private fun go(w:Words)=compose.onAllNodesWithContentDescription(w.localized(AppPreferences()),useUnmergedTree=true).onLast().performClick()
    private fun demoExercise(word:Words) {
        // Keep the original interaction and assertion timing. On failure capture
        // safe fixture state so a transient missing node can be distinguished
        // from a language, connection or message-list mismatch. Never retry it.
        inputTrace?.mark("demo-exercise")
        try {
            compose.onNodeWithText(word.localized(AppPreferences())).performScrollTo().performClick()
        } catch(original:AssertionError) {
            fun nodeCount(text:String,substring:Boolean=false)=runCatching {
                compose.onAllNodesWithText(text,substring=substring).fetchSemanticsNodes().size
            }.getOrDefault(-1)
            val preferences=vm.preferences.value
            val diagnostic=org.json.JSONObject().apply {
                put("version",2);put("exercise",word.name)
                put("rendered",runCatching {captureUiFixtureSnapshot(compose,compose.activity,vm,expectedMessageCount=0)}
                    .getOrElse {org.json.JSONObject().put("snapshotUnavailable",true)})
                put("demo",preferences.demo);put("chinese",preferences.chinese);put("pinyin",preferences.pinyin)
                put("preferencesAreDefault",preferences==AppPreferences())
                put("connectionIsDemo",vm.connectionStatus.value=="demo");put("connecting",vm.connecting.value)
                put("busy",vm.busy.value);put("controlling",vm.controlBusy.value)
                put("selectionIsBlank",vm.selected.value.isBlank());put("messageCount",vm.messages.value.size)
                put("controlCount",vm.controls.value.size)
                put("expectedExactNodes",nodeCount(word.localized(AppPreferences())))
                put("englishSubstringNodes",nodeCount(word.en,true))
                put("homeNewConversationNodes",nodeCount("New conversation"))
                put("activityLifecycle",compose.activity.lifecycle.currentState.name)
                put("imeBottomPx",androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: -1)
            }.toString()
            runCatching {
                val file=java.io.File(compose.activity.getExternalFilesDir(null),
                    "qa/demo-exercise-failure-"+java.util.UUID.randomUUID()+".json")
                file.parentFile!!.mkdirs();file.writeText(diagnostic)
            }
            throw AssertionError("Demo exercise interaction failed; safe fixture snapshot=$diagnostic",original)
        }
    }
    @Test fun demoApprovalWaitsForAnExplicitDenyAndRecordsToolFailure() {
        inputTrace?.mark("home-new-chat")
        compose.openHomeNewChatOnce()
        inputTrace?.mark("after-home-new-chat")
        demoExercise(Words.DemoApproval)
        compose.waitUntil(5000) {vm.controls.value.isNotEmpty()}
        assertTrue(vm.busy.value);assertEquals("pending",vm.controls.value.single().state)
        compose.onNodeWithText(Words.Deny.localized(AppPreferences())).performClick()
        compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
        val failedCall = runBlocking {
            app.database.dao().events(vm.selected.value).first().single { it.callId == "demo-note" && it.kind == "failed" }
        }
        assertEquals("Demo note", failedCall.tool)
        assertTrue(vm.controls.value.isEmpty())
        assertTrue(vm.messages.value.last().content.contains("Demo decision: **deny**."))

        compose.onNodeWithContentDescription(Words.ToolActivity.localized(AppPreferences()), substring = true).performClick()
        val timeline = hasTestTag("activity-timeline")
        val tool = hasText("Demo note")
        val failed = hasText(Words.Failed.localized(AppPreferences()))
        compose.onNode(timeline).performScrollToNode(tool)
        compose.onNode(tool and hasAnyAncestor(timeline)).assertExists()
        compose.onNode(timeline).performScrollToNode(failed)
        compose.onNode(failed and hasAnyAncestor(timeline)).assertExists()
        compose.onNode(hasText("The simulated action was denied.", substring = true) and hasAnyAncestor(timeline))
            .assertDoesNotExist()
    }
    @Test fun activityShowsThePendingApprovalWithoutRepeatingThePrivatePrompt() {
        compose.openHomeNewChatOnce()
        demoExercise(Words.DemoApproval)
        compose.waitUntil(5000) { vm.controls.value.singleOrNull()?.state == "pending" }
        val prompt = vm.controls.value.single().prompt
        assertTrue(prompt.isNotBlank())
        compose.onNodeWithText(Words.Later.localized(AppPreferences())).performClick()
        compose.onNodeWithTag("chat-current-activity").assertIsDisplayed()
            .assertTextEquals(Words.ActivityApprovalWait.localized(AppPreferences()).replace("\n", " · "))
        compose.onNodeWithContentDescription(Words.ToolActivity.localized(AppPreferences()), substring = true).performClick()

        val timeline = hasTestTag("activity-timeline")
        compose.onNode(timeline).performScrollToNode(hasTestTag("activity-wait"))
        compose.onNode(hasText(Words.ActivityApprovalWait.localized(AppPreferences())) and hasAnyAncestor(timeline))
            .assertIsDisplayed()
        compose.onNode(hasText(prompt) and hasAnyAncestor(timeline)).assertDoesNotExist()

        compose.onNodeWithText(Words.Close.localized(AppPreferences())).performClick()
        compose.onNodeWithContentDescription(Words.ReviewRequest.localized(AppPreferences()), substring = true).performClick()
        compose.onNodeWithText(Words.Deny.localized(AppPreferences())).performClick()
        compose.waitUntil(10000) { !vm.busy.value && vm.runStatus.value == "completed" }

        compose.onNodeWithContentDescription(Words.ToolActivity.localized(AppPreferences()), substring = true).performClick()
        compose.onNodeWithTag("activity-wait").assertDoesNotExist()
        compose.onNodeWithText(Words.Close.localized(AppPreferences())).performClick()
        compose.onNodeWithTag("chat-current-activity").assertDoesNotExist()
    }

    @Test fun demoClarificationChoiceIsReviewedBeforeSubmission() {
        inputTrace?.mark("home-new-chat")
        compose.openHomeNewChatOnce()
        inputTrace?.mark("after-home-new-chat")
        demoExercise(Words.DemoClarification)
        compose.waitUntil(5000) {vm.controls.value.isNotEmpty()}
        compose.onNodeWithText("Brief").performClick();assertTrue(vm.busy.value)
        compose.onNodeWithText(Words.SubmitAnswer.localized(AppPreferences())).performClick()
        compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
        assertTrue(vm.messages.value.last().content.contains("Brief"))
    }
    @Test fun voicePrivacyPreferencesPersistAndTypedTranscriptionTransfersWithoutActivatingAudio() {
        go(Words.Voice)
        assertTrue(vm.preferences.value.onDeviceSpeechOnly);assertFalse(vm.preferences.value.allowNetworkSpeech)
        compose.onNode(hasSetTextAction() and hasText(Words.Transcript.localized(AppPreferences()))).performScrollTo().performTextInput("Typed transcription sample")
        compose.onNodeWithText(Words.Chat.localized(AppPreferences())).performScrollTo().performClick()
        assertEquals("Typed transcription sample",vm.draft.value)
        runBlocking {app.preferences.update(vm.preferences.value.copy(onDeviceSpeechOnly=false,allowNetworkSpeech=false,speechRate=1.1f))}
        val stored=runBlocking {app.preferences.flow.first()};assertFalse(stored.onDeviceSpeechOnly);assertEquals(1.1f,stored.speechRate,.001f)
    }
}
