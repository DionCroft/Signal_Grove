package org.hermesmobile.app

import org.hermesmobile.app.data.NewSessionTitle
import org.junit.Assert.*
import org.junit.Test

class NewSessionTitleTest {
    @Test fun repeatedOpeningMessagesHaveDistinctStableSessionLabels() {
        val first=NewSessionTitle.create("Test","api_mobile_a","Image message")
        assertTrue(first.startsWith("Test · "))
        assertEquals(first,NewSessionTitle.create("Test","api_mobile_a","Image message"))
        assertNotEquals(first,NewSessionTitle.create("Test","api_mobile_b","Image message"))
    }
    @Test fun imageOnlySessionsHaveDifferentLabelsWithoutFakeMessageText() {
        assertTrue(NewSessionTitle.create("","a","Image message").startsWith("Image message · "))
        assertNotEquals(NewSessionTitle.create("","a","Image message"),NewSessionTitle.create("","b","Image message"))
    }
    @Test fun whitespaceControlsAndInvisibleCharactersMatchSingleLineTitleRules() {
        val title=NewSessionTitle.create(" \tHello\n\r  world\u00a0again\u0000\u200B\u202E\u2066\uFEFF ","a","Image message")
        assertTrue(title.startsWith("Hello world again · "));assertFalse(title.contains('\n'));assertFalse(title.contains('\u202E'))
    }
    @Test fun truncationNeverSplitsSupplementaryCharactersAndStaysUnderTheWireLimit() {
        val title=NewSessionTitle.create("🌱".repeat(100),"a","Image message")
        val base=title.substringBefore(" · ")
        assertEquals(60,base.codePointCount(0,base.length));assertFalse(base.codePoints().anyMatch {it in 0xD800..0xDFFF})
        assertTrue(title.length<=200)
    }
    @Test fun isolatedSurrogatesAreRemovedFromMetadata() {
        val title=NewSessionTitle.create("A\uD83DB\uDC00C","a","Image message")
        assertTrue(title.startsWith("ABC · "))
    }
    @Test fun multilingualOpeningTextRemainsReadable() {
        assertTrue(NewSessionTitle.create("你好，世界！ café","a","Image message").startsWith("你好，世界！ café · "))
    }
    @Test fun emptyAfterSanitizationUsesTheProvidedFallback() {
        assertTrue(NewSessionTitle.create("\u0000\u200B\n","a","Image message").startsWith("Image message · "))
    }
    @Test fun missingIdentityAndEmptyFallbackRefuseLocally() {
        assertTrue(runCatching {NewSessionTitle.create("Test","","Image message")}.isFailure)
        assertTrue(runCatching {NewSessionTitle.create("","a","\u0000")}.isFailure)
    }
}
