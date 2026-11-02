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
        val name="QA paused job "+java.util.UUID.randomUUID().toString().take(8)
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(5000) {vm.connectionStatus.value=="demo" && vm.jobsStatus.value=="ready"}
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=false,pinyin=false,automationScope="demo"))}
        compose.waitUntil(5000) {vm.preferences.value.automationScope=="demo" && !vm.preferences.value.chinese}
        compose.onAllNodesWithContentDescription(text(Words.Automation),useUnmergedTree=true).onLast().performClick()
        compose.onNodeWithText(text(Words.NewJob)).performClick()
        compose.onNodeWithText(text(Words.JobName)).performTextInput(name)
        compose.onNodeWithText(text(Words.Prompt)).performTextInput("Review harmless fixture")
        compose.onNode(hasSetTextAction() and hasText(text(Words.Schedule))).performTextInput("0 9 * * *")
        compose.onNodeWithText(text(Words.ReviewJobChange)).performClick()
        assertTrue(vm.jobs.value.none {it.name==name})
        compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(5000) {vm.jobs.value.any {it.name==name && !it.enabled}}
        fun details(action:Words) {
            compose.waitUntil(10000) {!vm.jobBusy.value && vm.jobsStatus.value=="ready"}
            val id=vm.jobs.value.single {it.name==name}.id
            compose.onNodeWithTag("job-list").performScrollToNode(hasTestTag("job-card-$id"))
            compose.onNode(hasText(text(Words.JobDetails)) and hasAnyAncestor(hasTestTag("job-card-$id"))).performClick()
            compose.waitUntil(5000) {compose.onAllNodesWithTag("job-detail-list").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("job-detail-name").assertTextEquals(name)
            compose.onNodeWithTag("job-detail-list").performScrollToNode(hasText(text(action)))
            compose.onNodeWithText(text(action)).assertIsEnabled()
        }
        details(Words.ResumeJob);compose.onNodeWithText(text(Words.ResumeJob)).performClick();compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(5000) {vm.jobs.value.any {it.name==name && it.enabled}}
        details(Words.PauseJob);compose.onNodeWithText(text(Words.PauseJob)).performClick();compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(5000) {vm.jobs.value.any {it.name==name && !it.enabled}}
        details(Words.DeleteJob);compose.onNodeWithText(text(Words.DeleteJob)).performClick();compose.onNodeWithText(text(Words.Cancel)).performClick()
        assertTrue(vm.jobs.value.any {it.name==name})
        details(Words.DeleteJob);compose.onNodeWithText(text(Words.DeleteJob)).performClick();compose.onNodeWithText(text(Words.ConfirmChange)).performClick()
        compose.waitUntil(5000) {vm.jobs.value.none {it.name==name}}
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(automationScope=""))}
    }
}
