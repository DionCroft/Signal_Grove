package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PhoneCaptureStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun capturesAndReviewedTasksSurviveReopeningWithoutReplay() = runBlocking {
        val name = "capture-test-" + UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, HermesDatabase::class.java, name)
            .addMigrations(*ALL_HERMES_MIGRATIONS).build()
        val note = PhoneCapture(UUID.randomUUID().toString(), "Check calibration before the rehearsal. Keep 100% local.",
            CaptureSource.WIDGET.name, 120)
        val review = UUID.randomUUID().toString()
        val names = listOf("Check the calibration")
        try {
            val first = open()
            try {
                val captures = first.phoneCaptures()
                captures.save(note)
                captures.save(note.copy(createdAt = 121))
                assertEquals(note, captures.observe(note.id).first())
                assertEquals(note.id, captures.search("100%").first().single().id)
                assertTrue(captures.search("100%_").first().isEmpty())
                val save = async { captures.saveReviewedTasks(note.id, review, note.content, names, 122) }
                val replay = async { captures.saveReviewedTasks(note.id, review, note.content, names, 123) }
                assertEquals(1, save.await())
                assertEquals(1, replay.await())
            } finally { first.close() }
            val db = open()
            try {
                val captures = db.phoneCaptures()
                assertTrue(captures.observe(note.id).first()?.processedAt in setOf(122L, 123L))
                val saved = captures.linkedTasks(note.id).first().single()
                assertEquals("Check the calibration", saved.title)
                assertEquals(note.id, captures.taskSources().first().single().captureId)
                val edited = saved.copy(title = "Check calibration on Friday", completed = true)
                db.dao().save(edited)
                assertEquals(edited, captures.linkedTasks(note.id).first().single())
                db.dao().delete(edited)
                assertTrue(captures.taskSources().first().isEmpty())
                assertEquals(1, captures.saveReviewedTasks(note.id, review, note.content, names, 124))
                assertTrue(db.dao().tasks().first().isEmpty())
            } finally { db.close() }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun version14CapturesKeepTasksAndNotebookEntriesThroughEditsAndArchiving() = runBlocking {
        val name = "capture-migration-" + UUID.randomUUID() + ".db"
        val note = PhoneCapture(UUID.randomUUID().toString(), "Original calibration note", CaptureSource.NOTE.name, 120)
        val task = LocalTask(UUID.randomUUID().toString(), "Check calibration")
        val notebookId = UUID.randomUUID().toString()
        val item = NotebookItem(UUID.randomUUID().toString(), notebookId, NotebookItemKind.CAPTURE.name,
            note.id, "", note.content, "", 122)
        fun open() = Room.databaseBuilder(context, HermesDatabase::class.java, name)
            .addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {
            createVersion14Database(name)
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("INSERT INTO phone_captures (id, content, source, createdAt) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(note.id, note.content, note.source, note.createdAt))
                old.execSQL("INSERT INTO tasks VALUES (?, ?, '', 0, 0, '')", arrayOf(task.id, task.title))
                old.execSQL("INSERT INTO phone_capture_tasks (taskId, captureId) VALUES (?, ?)", arrayOf(task.id, note.id))
                old.execSQL("INSERT INTO phone_notebooks VALUES (?, 'Calibration', 121, 121)", arrayOf(notebookId))
                old.execSQL("INSERT INTO phone_notebook_items VALUES (?, ?, ?, ?, '', ?, '', 122)",
                    arrayOf(item.id, notebookId, item.kind, note.id, item.title))
            }
            val changed = "Updated calibration note"
            val first = open()
            try {
                val db = first
                val captures = db.phoneCaptures()
                assertEquals(note, captures.observe(note.id).first())
                assertEquals(1, db.today().unprocessedCaptures().first())
                captures.edit(note, changed)
                val updated = checkNotNull(captures.observe(note.id).first())
                captures.save(note.copy(createdAt = 500))
                assertEquals(updated, captures.observe(note.id).first())
                assertTrue(runCatching { captures.save(note.copy(content = "Different source")) }.isFailure)
                assertTrue(runCatching { captures.delete(note) }.isFailure)
                val request = UUID.randomUUID().toString()
                assertTrue(runCatching {
                    captures.saveReviewedTasks(note.id, request, note.content, listOf("Stale task"), 124)
                }.isFailure)
                assertEquals(listOf(task.id), captures.linkedTasks(note.id).first().map { it.id })
                assertEquals(item, db.phoneNotebooks().item(notebookId, item.id))
                captures.setArchived(updated, 125)
                assertEquals(0, db.today().unprocessedCaptures().first())
                assertTrue(captures.search(changed).first().isEmpty())
                assertEquals(note.id, captures.search(changed, archived = true).first().single().id)
                assertEquals(note.id, captures.search(changed, archived = null).first().single().id)
            } finally { first.close() }
            val reopened = open()
            try {
                val db = reopened
                val captures = db.phoneCaptures()
                val archived = checkNotNull(captures.observe(note.id).first())
                assertEquals(125L, archived.archivedAt)
                assertEquals(changed, archived.content)
                assertEquals(listOf(task.id), captures.linkedTasks(note.id).first().map { it.id })
                assertEquals(item, db.phoneNotebooks().item(notebookId, item.id))
                captures.setArchived(archived, null)
                assertEquals(1, db.today().unprocessedCaptures().first())
                assertEquals(note.id, captures.search(changed).first().single().id)
                captures.delete(checkNotNull(captures.observe(note.id).first()))
                assertNull(captures.observe(note.id).first())
                assertEquals(listOf(task.id), db.dao().tasks().first().map { it.id })
                assertTrue(captures.taskSources().first().isEmpty())
                assertEquals(item, db.phoneNotebooks().item(notebookId, item.id))
            } finally { reopened.close() }
        } finally {
            context.deleteDatabase(name)
        }
    }

    /** Create the fixture from the checked-in v14 Room schema used by the shipped app. */
    private fun createVersion14Database(name: String) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open("org.hermesmobile.app.data.HermesDatabase/14.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        context.openOrCreateDatabase(name, 0, null).use { sql ->
            val entities = schema.getJSONArray("entities")
            for (position in 0 until entities.length()) {
                val table = entities.getJSONObject(position)
                val namePlaceholder = "\${TABLE_NAME}"
                val tableName = table.getString("tableName")
                sql.execSQL(table.getString("createSql").replace(namePlaceholder, tableName))
                val indexes = table.optJSONArray("indices") ?: continue
                for (index in 0 until indexes.length()) {
                    sql.execSQL(indexes.getJSONObject(index).getString("createSql").replace(namePlaceholder, tableName))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) sql.execSQL(setup.getString(index))
            sql.version = 14
        }
    }
}
