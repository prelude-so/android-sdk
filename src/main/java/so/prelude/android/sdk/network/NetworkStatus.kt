package so.prelude.android.sdk.network

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI

internal fun Context.getLan(): Network? =
    getNetworkForTransports(
        listOf(
            TRANSPORT_WIFI,
            TRANSPORT_ETHERNET,
        ),
    )

internal fun Context.getCellular(): Network? = getNetworkForTransports(listOf(TRANSPORT_CELLULAR))

@Suppress("DEPRECATION")
private fun Context.getNetworkForTransports(transports: List<Int>): Network? {
    val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    return connectivityManager.allNetworks.firstOrNull { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        capabilities != null && transports.any { capabilities.hasTransport(it) }
    }
}
