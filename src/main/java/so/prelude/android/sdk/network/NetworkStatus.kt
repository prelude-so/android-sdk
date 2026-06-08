package so.prelude.android.sdk.network

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
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
 * Prefer INTERNET + VALIDATED routes so DNS is bound to a route Android has
 * proven usable. Fall back to INTERNET-only routes for fresh or captive
 * networks, then to plain transport-only matches as a last resort to avoid
 * capability-reporting regressions on older OEMs that under-report caps.
 */
internal fun ConnectivityManager.firstMatching(transports: List<Int>): Network? {
    // Fast path: when the active/default route is already a validated match, use it.
    activeNetwork?.let { active ->
        getNetworkCapabilities(active)?.let { caps ->
            if (caps.isInternetMatch(transports) && caps.hasCapability(NET_CAPABILITY_VALIDATED)) return active
        }
    }

    // Fallback: use the legacy synchronous snapshot so cold-start dispatch does not depend on callbacks.
    val active = activeNetwork
    val candidates = (listOfNotNull(active) + legacyRegisteredNetworks()).distinct()

    val matches =
        candidates.mapNotNull { network ->
            val caps = getNetworkCapabilities(network) ?: return@mapNotNull null
            if (caps.isTransportMatch(transports)) NetworkCandidate(network, caps) else null
        }

    // Prefer proven public Internet, then plausible Internet, then legacy transport-only.
    return matches.firstOrNull { it.capabilities.isInternetValidated() }?.network
        ?: matches.firstOrNull { it.capabilities.hasCapability(NET_CAPABILITY_INTERNET) }?.network
        ?: matches.firstOrNull()?.network
}

@Suppress("DEPRECATION")
private fun ConnectivityManager.legacyRegisteredNetworks(): List<Network> = allNetworks.toList()

private data class NetworkCandidate(
    val network: Network,
    val capabilities: NetworkCapabilities,
)

private fun NetworkCapabilities.isTransportMatch(transports: List<Int>): Boolean = transports.any { hasTransport(it) }

private fun NetworkCapabilities.isInternetMatch(transports: List<Int>): Boolean =
    isTransportMatch(transports) && hasCapability(NET_CAPABILITY_INTERNET)

private fun NetworkCapabilities.isInternetValidated(): Boolean =
    hasCapability(NET_CAPABILITY_INTERNET) && hasCapability(NET_CAPABILITY_VALIDATED)
