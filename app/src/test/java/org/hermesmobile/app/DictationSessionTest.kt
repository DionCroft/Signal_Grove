package org.hermesmobile.app

import org.hermesmobile.app.voice.*
import org.junit.Assert.*
import org.junit.Test

class DictationSessionTest {
    @Test fun unavailableLocalServiceCannotBeginOrFallBackToSystem() {
        val local=SpeechPolicy.recognition(true,37,false,true)
        val state=DictationSession(local)
        assertEquals(RecognitionMode.UNAVAILABLE,local);assertEquals(state,state.begin())
        assertEquals(RecognitionMode.SYSTEM,SpeechPolicy.recognition(false,37,false,true))
    }
    @Test fun cancellationRejectsEveryLateCallbackFromTheOldAttempt() {
        val first=DictationSession(RecognitionMode.ON_DEVICE).begin();val token=first.generation
        val stopped=first.cancel()
        assertFalse(stopped.accepts(token));assertEquals(stopped,stopped.processing(token))
        assertEquals(stopped,stopped.finish(token));assertEquals(stopped,stopped.fail(token,RecognitionFailure.NO_MATCH))
    }
    @Test fun oldErrorCannotOverwriteTheNextListeningAttempt() {
        val first=DictationSession(RecognitionMode.SYSTEM).begin()
        val second=first.cancel().begin()
        assertTrue(second.accepts(second.generation));assertFalse(second.accepts(first.generation))
        assertEquals(second,second.fail(first.generation,RecognitionFailure.PERMISSION))
    }
    @Test fun modeChangeClearsThePriorErrorAndInvalidatesPermissionSnapshot() {
        val snapshot=DictationSession(RecognitionMode.SYSTEM)
        val failed=snapshot.begin().let {it.fail(it.generation,RecognitionFailure.CLIENT)}
        val changed=failed.configure(RecognitionMode.UNAVAILABLE,"en-GB")
        assertNull(changed.failure);assertEquals(DictationPhase.IDLE,changed.phase)
        assertNotEquals(snapshot,changed);assertNotEquals(failed.generation,changed.generation)
    }
    @Test fun languageChangeStopsProcessingAndRejectsItsLateResult() {
        val active=DictationSession(RecognitionMode.ON_DEVICE).begin().let {it.processing(it.generation)}
        val changed=active.configure(RecognitionMode.ON_DEVICE,"zh-CN")
        assertFalse(changed.accepts(active.generation));assertNull(changed.failure)
        assertEquals(DictationPhase.IDLE,changed.phase);assertEquals("zh-CN",changed.language)
    }
    @Test fun terminalResultAcceptsOnlyOneCallback() {
        val active=DictationSession(RecognitionMode.ON_DEVICE).begin()
        val done=active.finish(active.generation)
        assertEquals(DictationPhase.IDLE,done.phase);assertFalse(done.accepts(active.generation))
        assertEquals(done,done.fail(active.generation,RecognitionFailure.CLIENT))
    }
    @Test fun errorsKeepTheirMeaningWithoutBlamingPermission() {
        val expected=mapOf(1 to RecognitionFailure.NETWORK,2 to RecognitionFailure.NETWORK,3 to RecognitionFailure.AUDIO,
            4 to RecognitionFailure.NETWORK,5 to RecognitionFailure.CLIENT,6 to RecognitionFailure.NO_MATCH,7 to RecognitionFailure.NO_MATCH,
            8 to RecognitionFailure.BUSY,9 to RecognitionFailure.PERMISSION,10 to RecognitionFailure.BUSY,11 to RecognitionFailure.SERVICE,
            12 to RecognitionFailure.LANGUAGE_UNSUPPORTED,13 to RecognitionFailure.LANGUAGE_UNAVAILABLE,999 to RecognitionFailure.CLIENT)
        expected.forEach {(code,failure)->assertEquals("Android error $code",failure,RecognitionFailure.fromAndroid(code))}
    }
    @Test fun explicitRefreshClearsErrorsAndInvalidatesOldCallbacks() {
        val active=DictationSession(RecognitionMode.ON_DEVICE).begin()
        val failed=active.fail(active.generation,RecognitionFailure.LANGUAGE_UNAVAILABLE)
        assertEquals(RecognitionFailure.LANGUAGE_UNAVAILABLE,failed.cancel().failure)
        assertNull(failed.cancel(clearFailure=true).failure)
        assertFalse(failed.cancel(clearFailure=true).accepts(active.generation))
    }
    @Test fun repeatedStartCannotCreateAnotherActiveAttempt() {
        val first=DictationSession(RecognitionMode.SYSTEM).begin()
        assertEquals(first,first.begin());val processing=first.processing(first.generation)
        assertEquals(processing,processing.begin());assertTrue(processing.accepts(first.generation))
    }
}
