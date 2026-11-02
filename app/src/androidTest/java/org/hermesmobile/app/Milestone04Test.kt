package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.hermesmobile.app.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Milestone04Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private fun text(word:Words)=word.localized(vm.preferences.value)
    @Test fun demoJobCreateReviewPauseResumeAndDeleteRequireIndividualConfirmation() {
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(5000) {vm.connectionStatus.value=="demo" && vm.jobsStatus.value=="ready"}
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=false,pinyin=false,automationScope="demo"))}
        compose.waitUntil(5000) {vm.preferences.value.automationScope=="demo" && !vm.preferences.value.chinese}
        compose.onAllNodesWithContentDescription(text(Words.Automation),useUnmergedTree=true).onLast().performClick()
        compose.onNodeWithText(text(Words.NewJob)).performClick()
        compose.onNodeWithText(text(Words.JobName)).performTextInput("QA paused job")
        compose.onNodeWithText(text(Words.Prompt)).performTextInput("Review harmless fixture")
        compose.onNode(hasSetTextAction() and hasText(text(Words.Schedule))).performTextInput("0 9 * * *")
        compose.onNodeWithText(text(Words.ReviewJobChange)).performClick()
        assertTrue(vm.jobs.value.none {it.name=="QA paused job"})
        compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(5000) {vm.jobs.value.any {it.name=="QA paused job" && !it.enabled}}
        fun details() {compose.onNodeWithTag("job-list").performScrollToNode(hasText("QA paused job"));compose.onAllNodesWithText(text(Words.JobDetails)).onFirst().performClick()}
        details();compose.onNodeWithText(text(Words.ResumeJob)).performClick();compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(5000) {vm.jobs.value.any {it.name=="QA paused job" && it.enabled}}
        details();compose.onNodeWithText(text(Words.PauseJob)).performClick();compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(5000) {vm.jobs.value.any {it.name=="QA paused job" && !it.enabled}}
        details();compose.onNodeWithText(text(Words.DeleteJob)).performClick();compose.onNodeWithText(text(Words.Cancel)).performClick()
        assertTrue(vm.jobs.value.any {it.name=="QA paused job"})
        details();compose.onNodeWithText(text(Words.DeleteJob)).performClick();compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(5000) {vm.jobs.value.none {it.name=="QA paused job"}}
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(automationScope=""))}
    }
}
