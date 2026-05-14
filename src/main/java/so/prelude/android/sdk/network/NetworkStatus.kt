package so.prelude.android.sdk.network

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI

private val LAN_TRANSPORTS = listOf(TRANSPORT_WIFI, TRANSPORT_ETHERNET)
private val CELLULAR_TRANSPORTS = listOf(TRANSPORT_CELLULAR)

internal fun Context.getLan(): Network? = connectivityManager().firstMatching(LAN_TRANSPORTS)

internal fun Context.getCellular(): Network? = connectivityManager().firstMatching(CELLULAR_TRANSPORTS)

private fun Context.connectivityManager(): ConnectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

/**
 * Synchronous lookup of a Network matching one of [transports].
 *
 * Two-step strategy:
 * 1. Modern path (non-deprecated): check `activeNetwork`. Hits the common case
 *    where the requested transport is the device's default network — most users
 *    most of the time.
 * 2. Fallback (deprecated `allNetworks`): only used when the active network is
 *    a different transport (e.g. asking for cellular while Wi-Fi is default).
 *    `allNetworks` is the only synchronous "enumerate all known networks" API
 *    Android offers; the deprecation has no announced removal date and the
 *    callback-based replacement has different semantics (async, requires
 *    long-lived registration) which previously caused the regression this
 *    code path is fixing.
 *
 * Mirrors iOS `NWPathMonitor`'s initial path emit.
 */
internal fun ConnectivityManager.firstMatching(transports: List<Int>): Network? {
    activeNetwork?.let { active ->
        getNetworkCapabilities(active)?.let { caps ->
            if (transports.any { caps.hasTransport(it) }) return active
        }
    }
    @Suppress("DEPRECATION")
    return allNetworks.firstOrNull { network ->
        getNetworkCapabilities(network)?.let { caps ->
            transports.any { caps.hasTransport(it) }
        } ?: false
    }
}
