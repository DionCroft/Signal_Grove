package org.hermesmobile.app

import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.HermesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class TodayOfflineTest {
    @get:Rule(order = 0) val credentials = CredentialFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HermesApplication
    private val vm get() = ViewModelProvider(compose.activity)[HermesViewModel::class.java]

    @Test fun localTodayIsAvailableInAirplaneMode() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("todayOffline") == "true")
        assertEquals(1, Settings.Global.getInt(app.contentResolver, "airplane_mode_on", 0))
        val db = app.database
        val oldCount = runBlocking { db.today().unprocessedCaptures().first() }
        val stamp = System.currentTimeMillis()
        val task = LocalTask(UUID.randomUUID().toString(), "Today offline fixture", "2020-01-01")
        val capture = PhoneCapture(UUID.randomUUID().toString(), "Offline inbox fixture", CaptureSource.NOTE.name, stamp)
        val notebook = PhoneNotebook(UUID.randomUUID().toString(), "Today offline project", stamp, stamp)
        try {
            runBlocking {
                db.dao().save(task)
                db.phoneCaptures().save(capture)
                db.phoneNotebooks().create(notebook)
            }
            compose.waitUntil(15_000) { vm.todayUnprocessed.value == oldCount + 1 && vm.todayNotebook.value?.id == notebook.id }
            compose.onNodeWithTag("today-overview").assertExists()
            compose.onNodeWithText(task.title + " · " + task.dueDate).assertExists()
            compose.onNodeWithTag("home-capture-open").assertTextContains((oldCount + 1).toString(), substring = true)
            compose.onNodeWithTag("today-notebook").assertTextContains(notebook.name)
        } finally {
            runBlocking { db.dao().delete(task) }
            val sqlite = db.openHelper.writableDatabase
            sqlite.execSQL("DELETE FROM phone_captures WHERE id = ?", arrayOf(capture.id))
            sqlite.execSQL("DELETE FROM phone_notebooks WHERE id = ?", arrayOf(notebook.id))
        }
    }
}
