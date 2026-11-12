package org.hermesmobile.app.local

import org.junit.Assert.*
import org.junit.Test

class LocalCheckChoicesTest {
    private fun refused(ids: List<String>) {
        try { LocalCheckChoices.from(ids); fail("Unsafe helper choices were admitted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun reviewedChoicesAreCopiedAndCannotBeMutatedAfterReview() {
        val source = mutableListOf("phone.api_key", "host.api")
        val choices = LocalCheckChoices.from(source); source.clear()
        assertEquals(listOf("phone.api_key", "host.api"), choices.ids)
        try { (choices.ids as MutableList<String>).clear(); fail("Review could be changed") }
        catch (_: UnsupportedOperationException) { }
    }
    @Test fun emptyDuplicateAndExcessiveAlternativesAreRefused() {
        refused(emptyList()); refused(listOf("phone.one", "phone.one")); refused((1..6).map { "phone.$it" })
    }
    @Test fun schemaControlTextPathsAndOversizedIdsAreRefused() {
        for (id in listOf("", "PHONE.ONE", "phone.\"one", "host/api", "../file", "a\nb", "x".repeat(65))) refused(listOf(id))
    }
    @Test fun fullReviewedBoundaryStaysBounded() {
        val ids = (1..5).map { "phone.check_$it" }; assertEquals(ids, LocalCheckChoices.from(ids).ids)
    }
}
