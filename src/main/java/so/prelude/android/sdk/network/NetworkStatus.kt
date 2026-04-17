package so.prelude.android.sdk.network

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

private const val NETWORK_ACQUIRE_TIMEOUT_MS = 50L

internal suspend fun Context.getLan(): Network? =
    requestNetworkForTransport(
        listOf(
            TRANSPORT_WIFI,
            TRANSPORT_ETHERNET,
        ),
    )

internal suspend fun Context.getCellular(): Network? = requestNetworkForTransport(listOf(TRANSPORT_CELLULAR))

private suspend fun Context.requestNetworkForTransport(transports: List<Int>): Network? {
    val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    val result = CompletableDeferred<Network>()

    // Issue one requestNetwork call per transport to get OR semantics.
    // On Android 12+, a single request with multiple transports requires ALL of them
    // simultaneously (AND semantics), so we race separate requests instead.
    val callbacks = mutableListOf<ConnectivityManager.NetworkCallback>()

    return try {
        for (transport in transports) {
            val request =
                NetworkRequest
                    .Builder()
                    .addTransportType(transport)
                    .build()

            val callback: ConnectivityManager.NetworkCallback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        result.complete(network)
                    }
                }

            connectivityManager.requestNetwork(request, callback)
            callbacks.add(callback)
        }

        withTimeoutOrNull(NETWORK_ACQUIRE_TIMEOUT_MS) { result.await() }
    } finally {
        callbacks.forEach { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
    }
}
