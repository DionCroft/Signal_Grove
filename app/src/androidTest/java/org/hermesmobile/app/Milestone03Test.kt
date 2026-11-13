package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Milestone03Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as HermesApplication
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    @Before fun reset() {runBlocking {app.preferences.update(AppPreferences());app.database.dao().clearChats()}
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="demo" && vm.preferences.value.demo}
        compose.waitForIdle()
    }
    private fun go(w:Words)=compose.onAllNodesWithContentDescription(w.localized(AppPreferences()),useUnmergedTree=true).onLast().performClick()
    private fun demoExercise(word:Words) {
        // Keep the original interaction and assertion timing. On failure capture
        // safe fixture state so a transient missing node can be distinguished
        // from a language, connection or message-list mismatch. Never retry it.
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
        compose.onNodeWithText("New conversation").performScrollTo().performClick()
        demoExercise(Words.DemoApproval)
        compose.waitUntil(5000) {vm.controls.value.isNotEmpty()}
        assertTrue(vm.busy.value);assertEquals("pending",vm.controls.value.single().state)
        compose.onNodeWithText(Words.Deny.localized(AppPreferences())).performClick()
        compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
        assertTrue(runBlocking {app.database.dao().events(vm.selected.value).first().any {it.kind=="failed"}})
        assertTrue(vm.controls.value.isEmpty())
        compose.onNodeWithContentDescription(Words.ToolActivity.localized(AppPreferences()),substring=true).performClick()
        compose.onNodeWithTag("activity-timeline").performScrollToNode(hasText("The simulated action was denied."))
        compose.onNodeWithText("The simulated action was denied.").assertExists()
    }
    @Test fun demoClarificationChoiceIsReviewedBeforeSubmission() {
        compose.onNodeWithText("New conversation").performScrollTo().performClick()
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
