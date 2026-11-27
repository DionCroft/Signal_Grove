package org.hermesmobile.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.PhoneNotebook
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhoneNotebookLifecycleUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun renameAndDeleteOnlyTheReviewedNotebookWithoutAModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as HermesApplication
        val notebooks = app.database.phoneNotebooks()
        val now = System.currentTimeMillis()
        val project = PhoneNotebook(UUID.randomUUID().toString(), "Greenhouse notes", now, now)
        val other = PhoneNotebook(UUID.randomUUID().toString(), "Unrelated notebook", now, now)
        runBlocking {
            notebooks.create(project)
            notebooks.create(other)
        }
        val intent = LocalAiActivity.notebookIntent(app, project.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val scenario = ActivityScenario.launch<LocalAiActivity>(intent)
            try {
                await("notebook-entries")
                assertNull(app.localAi.state.value.loaded)
                clickAction("notebook-rename")
                compose.onNodeWithTag("notebook-rename-field").performTextReplacement("Cancelled title")
                compose.onNodeWithTag("notebook-manage-cancel").performClick()
                await("notebook-entries")
                assertEquals(project.name, runBlocking { notebooks.notebook(project.id)?.name })

                clickAction("notebook-rename")
                val newName = "Greenhouse records"
                compose.onNodeWithTag("notebook-rename-field").performTextReplacement(newName)
                compose.onNodeWithTag("notebook-rename-confirm").performClick()
                await("notebook-entries")
                assertEquals(newName, runBlocking { notebooks.notebook(project.id)?.name })

                clickAction("notebook-delete")
                compose.onNodeWithTag("notebook-delete-name").assertTextEquals(newName)
                compose.onNodeWithTag("notebook-delete-impact").assertTextContains("Original captures", substring = true)
                compose.onNodeWithTag("notebook-manage-cancel").performClick()
                await("notebook-entries")
                assertNotNull(runBlocking { notebooks.notebook(project.id) })
                clickAction("notebook-delete")
                compose.onNodeWithTag("notebook-delete-confirm").performClick()
                await("notebook-index")
                assertNull(runBlocking { notebooks.notebook(project.id) })
                assertEquals(other, runBlocking { notebooks.notebook(other.id) })
                assertNull(app.localAi.state.value.loaded)
            } finally {
                scenario.close()
            }
        } finally {
            runBlocking {
                notebooks.notebook(project.id)?.let { notebooks.delete(it) }
                notebooks.notebook(other.id)?.let { notebooks.delete(it) }
            }
        }
    }

    private fun clickAction(tag: String) {
        compose.onNodeWithTag("notebook-entries").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }

    private fun await(tag: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }
}
