package org.hermesmobile.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hermesmobile.app.local.NativeLlmBindings
import org.hermesmobile.app.local.NativeTokenSink
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNativeBoundaryTest {
    @Test fun cancellationBeforeLoadAndDisposedHandlesStayBounded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (!NativeLlmBindings.available()) {
            assertFalse(android.os.Process.is64Bit())
            assertEquals(0L, NativeLlmBindings.create(context.applicationInfo.nativeLibraryDir))
            assertEquals(6, NativeLlmBindings.load(0, "/nonexistent-model.gguf", 512, 1, 1))
            return
        }
        assertTrue(android.os.Process.is64Bit())
        val handle = NativeLlmBindings.create(context.applicationInfo.nativeLibraryDir)
        assertTrue(handle > 0)
        try {
            NativeLlmBindings.cancel(handle, 1)
            assertEquals(1, NativeLlmBindings.load(handle, "/nonexistent-model.gguf", 512, 1, 1))
            assertEquals(4, NativeLlmBindings.load(handle, "/nonexistent-model.gguf", 65536, 1, 2))
        } finally { NativeLlmBindings.dispose(handle) }
        assertEquals(4, NativeLlmBindings.load(handle, "/nonexistent-model.gguf", 512, 1, 3))
        val sink = object : NativeTokenSink { override fun onBytes(bytes: ByteArray) = true }
        assertEquals(4, NativeLlmBindings.generate(handle, arrayOf("user"), arrayOf("test".toByteArray()), emptyArray(), 16, 4, sink))
        NativeLlmBindings.cancel(handle, 99); NativeLlmBindings.dispose(handle)
    }
}
