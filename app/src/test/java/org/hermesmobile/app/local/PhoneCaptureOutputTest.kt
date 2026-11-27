package org.hermesmobile.app.local

import org.hermesmobile.app.data.CaptureSource
import org.hermesmobile.app.data.validCaptureText
import org.junit.Assert.*
import org.junit.Test

class PhoneCaptureOutputTest {
    @Test fun preservesOnlyBoundedReviewableTitles() {
        assertEquals(listOf("Check the build", "安排审阅"),
            parseCaptureTaskOutput("""["Check the build", "check the build", "安排审阅"]"""))
        assertEquals(emptyList<String>(), parseCaptureTaskOutput("[]"))
        assertEquals(listOf("Check the build"), parseCaptureTaskOutput("~~~json\n[\"Check the build\"]\n~~~"))
        assertNull(parseCaptureTaskOutput("[\"Check the build\","))
        assertNull(parseCaptureTaskOutput("{\"tasks\":[\"Check the build\"]}"))
        assertNull(parseCaptureTaskOutput("[\"Check the build\\nRun the review\"]"))
        assertNull(parseCaptureTaskOutput("[4]"))
        assertNull(parseCaptureTaskOutput(List(9) { "\"Action\"" }.joinToString(",", "[", "]")))
    }

    @Test fun labelsWebLinksButKeepsMixedInputAsText() {
        assertEquals(CaptureSource.SHARED_LINK, sharedCaptureSource(" https://example.invalid/a "))
        assertEquals(CaptureSource.SHARED_TEXT, sharedCaptureSource("Read https://example.invalid/a"))
        assertEquals(CaptureSource.SHARED_TEXT, sharedCaptureSource("content://test/file"))
        assertTrue(validCaptureText("  Keep exact text.\n"))
        assertFalse(validCaptureText("字".repeat(2049)))
    }
}
