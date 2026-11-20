package org.hermesmobile.app

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.hermesmobile.app.local.AndroidModelDownloadNetwork
import org.hermesmobile.app.local.LocalModelStorageException
import org.junit.Assert.*
import org.junit.Test

/** Exercises the platform adapter without sending a publisher request. */
class ModelDownloadNetworkAndroidTest {
    private val connectivity = checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext
        .getSystemService(ConnectivityManager::class.java))

    @Test fun selectedRouteUsesTheAndroidDefaultClassification() = runBlocking {
        val client = OkHttpClient()
        assertSame(client, AndroidModelDownloadNetwork(connectivity) { false }.prepare(client).client)
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val eligible = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val route = runCatching { AndroidModelDownloadNetwork(connectivity) { true }.prepare(client) }
        if (eligible) {
            val bound = route.getOrThrow()
            assertNotSame(client, bound.client)
            assertNotSame(client.socketFactory, bound.client.socketFactory)
            assertNotSame(client.connectionPool, bound.client.connectionPool)
            bound.watch { fail("Network changed during the bounded observation") }.use { bound.check() }
        } else {
            assertEquals("download_unmetered_required", (route.exceptionOrNull() as LocalModelStorageException).reason)
        }
    }

    @Test fun unreadablePolicyCannotFallBackToAnUnrestrictedClient() = runBlocking {
        val result = runCatching {
            AndroidModelDownloadNetwork(connectivity) { throw IOException("Synthetic preference error") }.prepare(OkHttpClient())
        }
        assertEquals("download_network_settings", (result.exceptionOrNull() as LocalModelStorageException).reason)
    }
}
