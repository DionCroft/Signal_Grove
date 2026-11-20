package org.hermesmobile.app.local

import org.junit.Assert.*
import org.junit.Test

class PhoneWritingTest {
    @Test fun cancelledAndCompetingSharesPreserveTheOriginalDraft() {
        val writing = PhoneWritingWorkspace()
        writing.editSource("Original draft")
        writing.choosePreset("Summarize this text.")
        writing.receive("First share")
        writing.receive("Second share")
        assertEquals("First share", writing.draft.value.incoming)
        assertTrue(writing.draft.value.intakeError)
        writing.dismissIncoming()
        assertEquals("Original draft", writing.draft.value.source)
        assertEquals("Summarize this text.", writing.draft.value.instructions)
        writing.receive("共享文本")
        writing.useIncoming()
        assertEquals("共享文本", writing.draft.value.source)
        assertNull(writing.draft.value.incoming)
    }

    @Test fun templateAndNotePersistenceRejectsAStaleEditAndDoesNotDuplicateASave() {
        val first = PhoneWritingTemplate("template-a", "Brief", "Summarize in one sentence.")
        val update = first.copy(instructions = "Summarize in two sentences.")
        val note = PhoneWritingNote("output-a", "Two short sentences.", false)
        val before = PhoneWritingLibrary().save(first, null).save(note)
        val roundTrip = PhoneWritingLibrary.decode(before.encode())
        assertEquals(before, roundTrip)
        val changed = roundTrip.save(update, first)
        assertEquals(listOf(update), changed.templates)
        assertTrue(runCatching { changed.save(first, first) }.isFailure)
        assertEquals(changed, changed.save(note))
        assertTrue(runCatching { changed.save(note.copy(text = "Changed content with reused ID.")) }.isFailure)
        assertFalse(validWritingSource("字".repeat(2049)))
        assertFalse(validInstructions("Invalid\u0000instruction"))
    }
}
