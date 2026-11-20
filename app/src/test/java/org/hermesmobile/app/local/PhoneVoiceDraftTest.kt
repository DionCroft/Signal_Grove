package org.hermesmobile.app.local

import org.hermesmobile.app.voice.RecognitionMode
import org.hermesmobile.app.voice.SpeechPolicy
import org.junit.Assert.*
import org.junit.Test

class PhoneVoiceDraftTest {
    @Test fun phoneRecognitionRequiresPositiveInstalledLanguageSupport() {
        assertEquals(RecognitionMode.UNAVAILABLE, SpeechPolicy.phoneRecognition(32, true))
        assertEquals(RecognitionMode.UNAVAILABLE, SpeechPolicy.phoneRecognition(37, false))
        assertEquals(RecognitionMode.ON_DEVICE, SpeechPolicy.phoneRecognition(37, true))
        assertEquals("en-GB", SpeechPolicy.installedLanguage("en-gb", listOf("en-US", "en-GB", "zh-CN")))
        assertEquals("en-US", SpeechPolicy.installedLanguage("en-GB", listOf("zh-CN", "en-US")))
        assertNull(SpeechPolicy.installedLanguage("en-GB", listOf("zh-CN", "und")))
        assertNull(SpeechPolicy.installedLanguage("zh-Hans", listOf("zh-Hant")))
    }

    @Test fun transcriptPreservesExactDraftAndRejectsExcessOrInvalidText() {
        assertEquals("Existing draft  \n你好 🌱", appendPhoneTranscript("Existing draft  ", "  你好 🌱  "))
        assertEquals("Hello", appendPhoneTranscript("", "Hello"))
        assertNotNull(appendPhoneTranscript("x".repeat(8190), "a"))
        assertNull(appendPhoneTranscript("x".repeat(8191), "a"))
        assertNull(appendPhoneTranscript("", "花".repeat(2731)))
        assertNull(appendPhoneTranscript("", " \n "))
        assertNull(appendPhoneTranscript("", "a\u0000b"))
        assertNull(appendPhoneTranscript("", "\uD83D"))
    }

    @Test fun voiceTargetUsesACompletedLocalReplyAndBindsTheExactDraftAndChat() {
        val chat = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 1,
            exchanges = listOf(
                LocalExchange("first", "Earlier usable reply", LocalReplyState.COMPLETE),
                LocalExchange("second", "Latest usable reply", LocalReplyState.TOKEN_LIMIT),
                LocalExchange("third", "Partial reply", LocalReplyState.INTERRUPTED)
            ))
        val target = PhoneVoiceDraft(chat, "unsubmitted")
        assertEquals("Latest usable reply", target.reply)
        assertTrue(target.current(LocalAiState(chat=chat),"unsubmitted"))
        assertFalse(target.current(LocalAiState(chat=chat.copy(updatedAt=2)),"unsubmitted"))
        assertFalse(target.current(LocalAiState(chat=chat),"different"))
        assertEquals("",PhoneVoiceDraft(null,"").reply)
    }
}
