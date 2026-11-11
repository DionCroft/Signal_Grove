package org.hermesmobile.app.local

import org.junit.Assert.*
import org.junit.Test

class PhoneDraftHandoffTest {
    @Test fun reviewRequiresBoundedNonblankUtf8Text() {
        val draft=UnsentDraft()
        for(text in listOf("", "  ", "界".repeat(2731))) {draft.change(text);assertNull(draft.review("s","c"))}
        draft.change("界".repeat(2730));assertNotNull(draft.review("s","c"))
        draft.change("a".repeat(8192));assertNotNull(draft.review("s","c"))
        draft.change("a".repeat(8193));assertNull(draft.review("s","c"))
    }
    @Test fun cancelledReviewDoesNotConsumeOrChangeTheSource() {
        val draft=UnsentDraft();draft.change("New question")
        val review=draft.review("s","c")!!
        assertTrue(draft.matches(review,"s","c"));assertEquals("New question",draft.text.value)
        assertNotNull(draft.review("s","c"))
    }
    @Test fun editsInvalidateExactReviewEvenWhenTextIsRestored() {
        val draft=UnsentDraft();draft.change("First");val review=draft.review("s","c")!!
        draft.change("Second");draft.change("First")
        assertFalse(draft.matches(review,"s","c"))
    }
    @Test fun attemptInvalidatesReviewAndIdenticalTextDoesNotResetProvenance() {
        val draft=UnsentDraft();draft.change("Work");val review=draft.review("s","c")!!
        draft.markAttempted();draft.change("Work")
        assertTrue(draft.isAttempted());assertNull(draft.review("s","c"));assertFalse(draft.matches(review,"s","c"))
        draft.change("An explicitly edited new question");assertNotNull(draft.review("s","c"))
    }
    @Test fun stagedRetryStaysAttemptedUntilAnExplicitEditOrReset() {
        val draft=UnsentDraft();draft.change("Old failed input");draft.markAttempted()
        assertNull(draft.review("s","c"));draft.reset();draft.change("New input");assertNotNull(draft.review("s","c"))
    }
    @Test fun anotherOwnerProfileConversationOrInvalidationCannotReuseReview() {
        val draft=UnsentDraft();draft.change("New");val review=draft.review("s","c")!!
        val other=UnsentDraft();other.change("New")
        assertFalse(other.matches(review,"s","c"));assertFalse(draft.matches(review,"other","c"));assertFalse(draft.matches(review,"s","other"))
        draft.invalidate();assertFalse(draft.matches(review,"s","c"));assertEquals("New",draft.text.value)
    }
    @Test fun oneUseInboxKeepsOnlyTheExplicitReviewedText() {
        val inbox=PhoneDraftHandoff();val token=inbox.offer("Edited new question")!!
        assertNotEquals("Edited new question",token);assertNull(inbox.take("wrong-token"))
        assertEquals("Edited new question",inbox.take(token));assertNull(inbox.take(token))
    }
    @Test fun pendingDeliveryCannotBeOverwrittenAndCancelIsExact() {
        val inbox=PhoneDraftHandoff();val token=inbox.offer("Keep this")!!
        assertNull(inbox.offer("Replace"));inbox.cancel("wrong");assertEquals("Keep this",inbox.take(token))
        val next=inbox.offer("Next")!!;inbox.cancel(next);assertNull(inbox.take(next));assertNotNull(inbox.offer("New"))
    }
    @Test fun expiredDeliveryIsNotReplayedOrKeptAcrossProcesses() {
        var clock=100L;val inbox=PhoneDraftHandoff {clock};val token=inbox.offer("Ephemeral")!!
        assertNull(PhoneDraftHandoff().take(token));clock+=300_000
        assertNull(inbox.take(token));assertNotNull(inbox.offer("Fresh"))
    }
    @Test fun backwardsTimeAndInvalidTextCannotDeliver() {
        var clock=100L;val inbox=PhoneDraftHandoff {clock};val token=inbox.offer("Ephemeral")!!;clock=99
        assertNull(inbox.take(token));assertNull(inbox.offer(" "));assertNull(inbox.offer("界".repeat(2731)))
    }
}
