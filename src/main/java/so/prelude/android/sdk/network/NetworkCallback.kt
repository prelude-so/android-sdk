package so.prelude.android.sdk.network

import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CompletableDeferred

internal class NetworkCallback : ConnectivityManager.NetworkCallback() {
    val network = CompletableDeferred<Network?>()

    override fun onAvailable(network: Network) {
        this.network.complete(network)
    }

    override fun onUnavailable() {
        this.network.complete(null)
    }
}
