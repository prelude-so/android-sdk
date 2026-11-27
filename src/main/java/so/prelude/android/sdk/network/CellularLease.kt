package so.prelude.android.sdk.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkRequest
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.ChecksSdkIntAtLeast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * A cellular network held for the duration of a silent verification.
 *
 * [network] is null when no cellular network could be obtained. Android disconnects
 * a network it brought up only for a request as soon as that request is released,
 * so [close] after the last request on it, not after picking it.
 */
internal class CellularLease(
    private val connectivityManager: ConnectivityManager,
    private val callback: NetworkCallback?,
    val network: Network?,
) : AutoCloseable {
    override fun close() {
        // The platform already released a request it answered with onUnavailable.
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
    }
}

internal suspend fun Context.requestCellular(timeout: Duration): CellularLease {
    val connectivityManager = connectivityManager()
    return if (mobileDataEnabled()) {
        connectivityManager.requestCellular(timeout)
    } else {
        // Nothing to bring up: answer at once from the snapshot instead of waiting out the timeout.
        CellularLease(connectivityManager, callback = null, network = connectivityManager.firstMatching(CELLULAR_TRANSPORTS))
    }
}

// False when mobile data is off, there is no SIM, or the device has no telephony.
// Below API 26 the SDK cannot tell, so it asks the platform anyway.
private fun Context.mobileDataEnabled(): Boolean =
    if (canReadMobileDataSwitch()) {
        runCatching { getSystemService(TelephonyManager::class.java)?.isDataEnabled }.getOrNull() ?: false
    } else {
        true
    }

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
internal fun canReadMobileDataSwitch(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

/**
 * Asks Android for a cellular network that routes to the Internet, bringing one up
 * when the device is on Wi-Fi (the counterpart of iOS `requiredInterfaceType = .cellular`).
 *
 * Falls back to the synchronous snapshot of [getCellular] when nothing answers within
 * [timeout], so devices that under-report capabilities keep their transport-only match.
 */
internal suspend fun ConnectivityManager.requestCellular(timeout: Duration): CellularLease {
    val available = CompletableDeferred<Network?>()
    val callback =
        object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                available.complete(network)
            }

            override fun onUnavailable() {
                available.complete(null)
            }
        }
    val request =
        NetworkRequest
            .Builder()
            .addTransportType(TRANSPORT_CELLULAR)
            .addCapability(NET_CAPABILITY_INTERNET)
            .build()

    // A host app can strip CHANGE_NETWORK_STATE from the merged manifest; keep today's lookup then.
    val requested = runCatching { requestNetwork(request, callback) }.isSuccess
    val network =
        try {
            if (requested) withTimeoutOrNull(timeout) { available.await() } else null
        } catch (e: CancellationException) {
            if (requested) runCatching { unregisterNetworkCallback(callback) }
            throw e
        }

    return CellularLease(
        connectivityManager = this,
        callback = callback.takeIf { requested },
        network = network ?: firstMatching(CELLULAR_TRANSPORTS),
    )
}
