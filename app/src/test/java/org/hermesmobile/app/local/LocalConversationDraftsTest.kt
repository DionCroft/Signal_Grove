package org.hermesmobile.app.local

import org.junit.Assert.*
import org.junit.Test

class LocalConversationDraftsTest {
    @Test fun openingAndReturningToChatsRestoresTheirSeparateDrafts() {
        val drafts = LocalConversationDrafts("parent"); drafts.edit("Parent unsent")
        drafts.select("child"); assertEquals("", drafts.text.value); drafts.edit("Child unsent")
        drafts.select("parent"); assertEquals("Parent unsent", drafts.text.value)
        drafts.select("child"); assertEquals("Child unsent", drafts.text.value)
    }
    @Test fun failedOrUnpublishedSubmissionDoesNotClearTheDraft() {
        val drafts = LocalConversationDrafts(null); drafts.edit("Keep until published")
        assertEquals("Keep until published", drafts.text.value)
        drafts.select("other"); drafts.select(null); assertEquals("Keep until published", drafts.text.value)
    }
    @Test fun durablePublicationClearsOnlyTheExactSubmittedSource() {
        val drafts = LocalConversationDrafts(null); drafts.edit("  Submitted  ")
        drafts.published(null, "  Submitted  ", "new")
        assertEquals("new", drafts.selectedId); assertEquals("", drafts.text.value)
        drafts.select(null); assertEquals("", drafts.text.value)
    }
    @Test fun aLaterEditSurvivesPublicationOfAnEarlierSnapshot() {
        val drafts = LocalConversationDrafts("parent"); drafts.edit("Later edit")
        drafts.published("parent", "Earlier submitted text", "child")
        assertEquals("", drafts.text.value); drafts.select("parent"); assertEquals("Later edit", drafts.text.value)
    }
    @Test fun copiedDraftCannotReplaceASavedFreshDraftWhileBrowsingHistory() {
        val drafts = LocalConversationDrafts(null); drafts.edit("Unsent fresh question")
        drafts.select("history"); assertEquals("", drafts.text.value); assertFalse(drafts.canReceiveFresh())
        drafts.select(null); assertEquals("Unsent fresh question", drafts.text.value)
    }
    @Test fun explicitDeletionForgetsOnlyThatChatsUnsentText() {
        val drafts = LocalConversationDrafts("parent"); drafts.edit("Parent private text")
        drafts.select("child"); drafts.edit("Child private text"); drafts.forget("parent")
        assertEquals("Child private text", drafts.text.value); drafts.select("parent"); assertEquals("", drafts.text.value)
        drafts.select("child"); drafts.forget("child"); drafts.select(null); drafts.select("child")
        assertEquals("", drafts.text.value); assertTrue(drafts.canReceiveFresh())
    }
}