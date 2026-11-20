package org.hermesmobile.app.local

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/** Restricts only publisher requests. No process binding, platform proxy override or Hermes client. */
internal class AndroidModelDownloadNetwork(
    private val connectivity: ConnectivityManager,
    private val unmeteredOnly: suspend () -> Boolean
) {
    suspend fun prepare(client: OkHttpClient): ModelDownloadRoute {
        val restricted = try {
            unmeteredOnly()
        } catch (_: IOException) {
            throw LocalModelStorageException("download_network_settings")
        }
        if (!restricted) return ModelDownloadRoute(client)

        val network = connectivity.activeNetwork
            ?: throw LocalModelStorageException("download_unmetered_required")
        if (!eligible(connectivity.getNetworkCapabilities(network))) {
            throw LocalModelStorageException("download_unmetered_required")
        }

        val invalidated = AtomicBoolean(false)
        val pool = ConnectionPool()
        fun check() {
            if (invalidated.get()) throw LocalModelStorageException("download_network_changed")
        }
        fun checkCurrent() {
            check()
            if (connectivity.activeNetwork != network || !eligible(connectivity.getNetworkCapabilities(network))) {
                invalidated.set(true)
                check()
            }
        }

        if (client.proxy?.type() == Proxy.Type.SOCKS) throw LocalModelStorageException("download_network_proxy")
        val selector = client.proxySelector
        val bound = client.newBuilder()
            .connectionPool(pool)
            .socketFactory(network.socketFactory)
            .dns { host -> checkCurrent(); network.getAllByName(host).toList() }
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> {
                    checkCurrent()
                    // OkHttp uses its own socket for SOCKS. Keep the system proxy policy
                    // and refuse this attempt if it cannot be bound to this network.
                    val routes = selector.select(uri)
                    if (routes.any { it.type() == Proxy.Type.SOCKS }) {
                        throw LocalModelStorageException("download_network_proxy")
                    }
                    return routes
                }
                override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) {
                    selector.connectFailed(uri, address, error)
                }
            })
            .addNetworkInterceptor { chain -> checkCurrent(); chain.proceed(chain.request()) }
            .build()

        return ModelDownloadRoute(bound, ::check) { cancel ->
            fun invalidate() {
                if (invalidated.compareAndSet(false, true)) cancel()
            }
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(value: Network) {
                    if (value != network) invalidate()
                }
                override fun onLost(value: Network) {
                    if (value == network) invalidate()
                }
                override fun onCapabilitiesChanged(value: Network, capabilities: NetworkCapabilities) {
                    if (value == network && !eligible(capabilities)) invalidate()
                }
            }
            connectivity.registerDefaultNetworkCallback(callback)
            val registration = AutoCloseable {
                try {
                    connectivity.unregisterNetworkCallback(callback)
                } finally {
                    pool.evictAll()
                }
            }
            try {
                checkCurrent()
                registration
            } catch (e: Exception) {
                registration.close()
                throw e
            }
        }
    }

    private fun eligible(capabilities: NetworkCapabilities?) =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
