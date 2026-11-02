package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.hermesmobile.app.ui.*
import org.junit.Rule
import org.junit.Test

class LaunchRouteTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun notificationAndWidgetIntentsNavigateTheExistingActivity() {
        val original=Intent(compose.activity.intent)
        val vm=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
        try {
            compose.runOnUiThread {vm.setDemo(true)}
            compose.waitUntil(5000) {vm.connectionStatus.value=="demo"}
            for((route,word) in listOf("Tasks" to Words.TaskInfo,"Chat" to Words.DemoApproval)) {
                compose.runOnUiThread {InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(compose.activity,Intent(compose.activity,MainActivity::class.java).putExtra("destination",route))}
                compose.waitUntil(5000) {compose.onAllNodesWithText(word.localized(vm.preferences.value)).fetchSemanticsNodes().isNotEmpty()}
            }
        } finally {
            // ActivityScenario identifies its activity by the original launch Intent filters.
            compose.runOnUiThread {compose.activity.setIntent(original)}
        }
    }
}
