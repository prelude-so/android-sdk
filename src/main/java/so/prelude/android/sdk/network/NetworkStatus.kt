package so.prelude.android.sdk.network

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest

private const val NETWORK_AVAILABLE_CHECK_TIMEOUT_MS: Int = 1000

internal suspend fun Context.getLan(): Network? =
    getNetworkForTransports(
        listOf(
            TRANSPORT_WIFI,
            TRANSPORT_ETHERNET,
        ),
    )

internal suspend fun Context.getCellular(): Network? = getNetworkForTransports(listOf(TRANSPORT_CELLULAR))

private suspend fun Context.getNetworkForTransports(transports: List<Int>): Network? {
    val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    val networkRequestBuilder = NetworkRequest.Builder()

    transports.forEach { transport ->
        networkRequestBuilder.addTransportType(transport)
    }

    val networkRequest = networkRequestBuilder.build()

    return getNetwork(connectivityManager, networkRequest)
}

private suspend fun getNetwork(
    connectivityManager: ConnectivityManager,
    networkRequest: NetworkRequest,
): Network? {
    val networkCallback = NetworkCallback()

    connectivityManager.requestNetwork(
        networkRequest,
        networkCallback,
        NETWORK_AVAILABLE_CHECK_TIMEOUT_MS,
    )

    return try {
        networkCallback.network.await()
    } catch (e: Exception) {
        null
    } finally {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
