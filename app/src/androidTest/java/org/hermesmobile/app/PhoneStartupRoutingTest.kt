package org.hermesmobile.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uses the normal activity and intent routes with demo credentials isolated by the fixture. */
class PhoneStartupRoutingTest {
    @get:Rule(order = 0) val credentials = CredentialFixtureRule()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as HermesApplication
    private val created = mutableListOf<Activity>()
    private var original: Boolean? = null
    private val lifecycle = ActivityLifecycleMonitorRegistry.getInstance()
    private val record = ActivityLifecycleCallback { activity, stage ->
        if (stage == Stage.CREATED && (activity is MainActivity || activity is LocalAiActivity)) {
            created.add(activity)
        }
    }

    @Before fun trackActivities() {
        original = readStartup()
        instrumentation.runOnMainSync { lifecycle.addLifecycleCallback(record) }
    }

    @After fun restore() {
        instrumentation.runOnMainSync {
            lifecycle.removeLifecycleCallback(record)
            created.asReversed().forEach { if (!it.isFinishing) it.finish() }
        }
        original?.let(::setStartup)
    }

    @Test fun defaultLauncherAndRecreatedHermesRemainOnHermes() {
        setStartup(false)
        start(launcher())
        val main = awaitActivity<MainActivity>()
        selected("home")

        setStartup(true)
        instrumentation.runOnMainSync { main.recreate() }
        awaitActivity<MainActivity> { it !== main }
        selected("home")
        compose.onNodeWithTag("phone-open-hermes").assertDoesNotExist()
    }

    @Test fun setupSwitchPersistsAndFreshLauncherOpensAnUnloadedPhone() {
        setStartup(false)
        start(launcher())
        val main = awaitActivity<MainActivity>()
        selected("home")
        instrumentation.runOnMainSync { main.startActivity(Intent(main, LocalAiActivity::class.java)) }
        awaitActivity<LocalAiActivity>()
        compose.onNodeWithTag("phone-messages").performScrollToNode(hasTestTag("phone-offline-setup-chat-entry"))
        compose.onNodeWithTag("phone-offline-setup-chat-entry").performClick()
        val toggle = compose.onNodeWithTag("phone-startup-toggle").performScrollTo().assertIsOff()
        toggle.assertIsEnabled().performClick()
        runBlocking { withTimeout(10_000) { app.preferences.phoneStartup.first { it } } }
        toggle.assertIsOn()
        compose.onNodeWithTag("phone-offline-setup-close").performClick()

        start(launcher())
        awaitActivity<LocalAiActivity>()
        assertTrue(readStartup())
        assertNull(app.localAi.state.value.loaded)
        compose.onNodeWithTag("phone-send").assertIsNotEnabled()
        compose.onNodeWithTag("nav-home").assertDoesNotExist()
        compose.onNodeWithTag("phone-open-hermes").assertIsDisplayed().performClick()

        awaitActivity<MainActivity>()
        selected("home")
        assertTrue(readStartup())
    }

    @Test fun repeatedLauncherEntryPreservesAnUnsentPhoneDraft() {
        setStartup(true)
        start(launcher())
        val first = awaitActivity<LocalAiActivity>()
        compose.waitUntil(10_000) { !app.localAi.state.value.busy }
        val localDraft = "Offline startup fixture"
        compose.onNodeWithTag("phone-draft").performTextReplacement(localDraft)

        start(launcher(), fresh = false)
        compose.waitForIdle()
        assertSame(first, awaitActivity<LocalAiActivity>())
        compose.onNodeWithTag("phone-draft").assertTextContains(localDraft)
        assertNull(app.localAi.state.value.loaded)
        compose.onNodeWithTag("phone-send").assertIsNotEnabled()
    }

    @Test fun widgetAndNotificationOpenTheirHermesDestinationsWithLocalStartupEnabled() {
        setStartup(true)
        start(launcher())
        awaitActivity<LocalAiActivity>()

        sendDestination("Tasks", 5241)
        val main = awaitActivity<MainActivity>()
        selected("tasks")
        sendDestination("Chat", 5242)
        assertSame(main, awaitActivity<MainActivity>())
        selected("chat")
    }

    @Test fun singleAndMultipleImageIntentsRetainTheHermesReview() {
        setStartup(true)
        start(launcher())
        awaitActivity<LocalAiActivity>()

        val first = Uri.parse("content://signal-grove-fixture/startup-one")
        val second = Uri.parse("content://signal-grove-fixture/startup-two")
        start(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, first), fresh = false)
        awaitActivity<MainActivity>()
        compose.onNodeWithTag("share-image-review").assertIsDisplayed()
        compose.onNodeWithTag("use-shared-images").assertExists()

        start(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_SEND_MULTIPLE).setType("image/png")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second)))
        awaitActivity<MainActivity>()
        compose.onNodeWithTag("share-image-review").assertIsDisplayed()
        compose.onNodeWithTag("use-shared-images").assertExists()
        assertTrue(readStartup())
    }

    private fun readStartup() = runBlocking { withTimeout(10_000) { app.preferences.phoneStartup.first() } }

    private fun setStartup(enabled: Boolean) = runBlocking {
        withTimeout(10_000) { app.preferences.setPhoneStartup(enabled) }
    }

    private fun launcher() = Intent(app, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)

    private fun start(intent: Intent, fresh: Boolean = true) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (fresh) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.runOnMainSync { app.startActivity(intent) }
    }

    private fun sendDestination(route: String, requestCode: Int) {
        val intent = Intent(app, MainActivity::class.java).putExtra("destination", route)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        PendingIntent.getActivity(app, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT).send()
    }

    private fun selected(destination: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("nav-$destination") and isSelected()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("nav-$destination").assertIsSelected()
    }

    private inline fun <reified T : Activity> awaitActivity(crossinline keep: (T) -> Boolean = { true }): T {
        var resumed: T? = null
        compose.waitUntil(10_000) {
            instrumentation.runOnMainSync {
                resumed = lifecycle.getActivitiesInStage(Stage.RESUMED).filterIsInstance<T>().singleOrNull { keep(it) }
            }
            resumed != null
        }
        compose.waitForIdle()
        return checkNotNull(resumed)
    }
}
