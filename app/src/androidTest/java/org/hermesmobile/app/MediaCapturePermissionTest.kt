package org.hermesmobile.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.local.LocalModelStorageException
import org.hermesmobile.app.local.MediaCaptureEngines
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCapturePermissionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun recordingReportsPermissionDenialBeforeOpeningTheMicrophone() = runBlocking {
        assertEquals(PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO))
        val result = runCatching { MediaCaptureEngines(context).record({}, { true }) }
        assertEquals("media_microphone_denied", (result.exceptionOrNull() as? LocalModelStorageException)?.reason)
    }
}
