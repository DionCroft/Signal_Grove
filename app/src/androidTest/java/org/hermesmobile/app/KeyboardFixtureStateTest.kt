package org.hermesmobile.app

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test

/** Captured gap geometry is a preparation input, never proof that the IME is ready. */
class KeyboardFixtureStateTest {
    private fun gap()=KeyboardFixtureInstrumentation.Keyboard(Rect(0,827,1080,1794),1053,37,0,0,false)
    @Test fun capturedWindowGapCanBeReopenedButRemainsUnreadyUntilItsBoundaryMatches() {
        val initial=gap();assertTrue(initial.reopenableGap);assertFalse(initial.ready)
        val reopened=initial.copy(bounds=Rect(0,1053,1080,1794))
        assertFalse(reopened.reopenableGap);assertTrue(reopened.ready)
        assertFalse(initial.copy(hints=1,closes=1,exactText=true).reopenableGap)
    }
    @Test fun unknownHintsMissingKeysAndInvalidWindowGeometryDoNotAuthorizeGapPreparation() {
        val value=gap();val invalid=listOf(value.copy(keys=0),value.copy(keys=257),value.copy(hints=1),
            value.copy(closes=1),value.copy(exactText=true),value.copy(bounds=Rect(0,-1,1080,1794)),
            value.copy(bounds=Rect(0,1100,1080,1794)),value.copy(bounds=Rect(0,827,0,1794)),
            value.copy(keyTop=1794),value.copy(bounds=Rect(0,827,1080,827)))
        assertTrue(invalid.all {!it.reopenableGap})
    }
}
