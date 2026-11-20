package org.hermesmobile.app.local

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PhoneProfileBackupTest {
    private fun profile(name: String) = PhonePersonalProfile(UUID.randomUUID().toString(), name, "Use short paragraphs",
        listOf(PhonePersonalNote(UUID.randomUUID().toString(), "Private garden 花🌱")))

    @Test fun ciphertextRoundTripsTheSelectedCollectionAndUsesItsOwnType() {
        val selected = PhonePersonalProfiles(entries = listOf(profile("Garden")))
        val phrase = "fixture passphrase 花".toCharArray()
        try {
            val first = PhoneProfileBackup.encrypt(selected, phrase)
            val second = PhoneProfileBackup.encrypt(selected, phrase)
            assertFalse(first.contentEquals(second))
            assertFalse(first.toString(Charsets.ISO_8859_1).contains("Private garden"))
            assertTrue(LocalHistoryBackupProtection.recognizesProfileEnvelope(first))
            assertFalse(LocalHistoryBackupProtection.recognizesHeader(first.copyOf(8)))
            assertArrayEquals(first, PhoneProfileBackup.readFile(first.inputStream()) {})
            assertEquals(selected, PhoneProfileBackup.unlock(first, phrase))
        } finally {
            phrase.fill('\u0000')
        }
    }

    @Test fun damagedWrongPasswordAndOversizedFilesProduceNoProfiles() {
        val bytes = PhoneProfileBackup.encrypt(PhonePersonalProfiles(entries = listOf(profile("Garden"))), "fixture".toCharArray())
        val wrong = assertThrows(LocalHistoryBackupException::class.java) {
            PhoneProfileBackup.unlock(bytes, "wrong".toCharArray())
        }
        assertEquals(LocalHistoryBackupProblem.AUTHENTICATION, wrong.problem)
        val tampered = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val bad = assertThrows(LocalHistoryBackupException::class.java) {
            PhoneProfileBackup.unlock(tampered, "fixture".toCharArray())
        }
        assertEquals(LocalHistoryBackupProblem.AUTHENTICATION, bad.problem)
        assertThrows(LocalHistoryBackupException::class.java) {
            PhoneProfileBackup.readFile(ByteArray(PhoneProfileBackup.MAX_FILE + 1).inputStream()) {}
        }
        val wrongType = bytes.copyOf().also { "SGBKAE01".toByteArray().copyInto(it) }
        assertThrows(LocalHistoryBackupException::class.java) { PhoneProfileBackup.readFile(wrongType.inputStream()) {} }
    }

    @Test fun reviewedRestorePreservesTheExistingProfileAndOnlyCopiesSelectedConflicts() {
        val original = profile("Original")
        val duplicate = profile("Already stored")
        val baseline = PhonePersonalProfiles(entries = listOf(original, duplicate))
        val collision = original.copy(style = "Use a bulleted list")
        val unselected = profile("Unselected")
        val reviewed = ProfileRestorePlan(baseline, PhonePersonalProfiles(entries = listOf(collision, duplicate, unselected)))
        assertEquals(listOf(ProfileRestoreDisposition.COPY, ProfileRestoreDisposition.ALREADY_PRESENT,
            ProfileRestoreDisposition.ADD), reviewed.items.map { it.disposition })
        val restoredId = UUID.randomUUID().toString()
        val ids = listOf(original.id, restoredId).iterator()
        val result = reviewed.restore(baseline, setOf(original.id)) { ids.next() }
        assertEquals(listOf(original, duplicate, collision.copy(id = restoredId)), result.entries)
        val reopened = ProfileRestorePlan(result, PhonePersonalProfiles(entries = listOf(collision)))
        assertEquals(ProfileRestoreDisposition.ALREADY_PRESENT, reopened.items.single().disposition)
        assertThrows(LocalHistoryBackupException::class.java) {
            reviewed.restore(baseline, setOf(duplicate.id))
        }
    }

    @Test fun reviewedRestoreRejectsChangesAndCapacityBeforePublishing() {
        val profileCount = List(PHONE_PROFILE_MAX - 1) { profile("Existing " + it) }
        val baseline = PhonePersonalProfiles(entries = profileCount)
        val a = profile("First")
        val b = profile("Second")
        val plan = ProfileRestorePlan(baseline, PhonePersonalProfiles(entries = listOf(a, b)))
        assertEquals(1, plan.available)
        val capacity = assertThrows(LocalHistoryBackupException::class.java) {
            plan.restore(baseline, setOf(a.id, b.id))
        }
        assertEquals(LocalHistoryBackupProblem.CAPACITY, capacity.problem)
        val stale = assertThrows(LocalHistoryBackupException::class.java) {
            plan.restore(baseline.remove(profileCount.first()), setOf(a.id))
        }
        assertEquals(LocalHistoryBackupProblem.CHANGED, stale.problem)
        assertEquals(profileCount + a, plan.restore(baseline, setOf(a.id)).entries)
    }
}
