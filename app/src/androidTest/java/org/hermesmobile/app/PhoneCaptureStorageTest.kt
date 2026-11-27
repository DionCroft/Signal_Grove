package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
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
                val save = async { captures.saveReviewedTasks(note.id, review, names, 122) }
                val replay = async { captures.saveReviewedTasks(note.id, review, names, 123) }
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
                assertEquals(1, captures.saveReviewedTasks(note.id, review, names, 124))
                assertTrue(db.dao().tasks().first().isEmpty())
            } finally { db.close() }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
