package so.prelude.android.sdk.network

import android.net.Network
import okhttp3.Dns
import java.net.InetAddress

/**
 * A custom [Dns] implementation that resolves hostnames through a specific Android [Network].
 *
 * @param network The Android [Network] to use for DNS resolution. Typically the cellular network
 *   obtained via [android.net.ConnectivityManager].
 * @see Dns
 * @see Network.getAllByName
 */
internal class NetworkBoundDns(
    private val network: Network,
) : Dns {
    /**
     * Resolves [hostname] to a list of IP addresses using the bound [Network].
     *
     * The returned addresses are in the order they will be attempted by OkHttp. If a connection
     * to an address fails, OkHttp will retry with the next address in the list.
     *
     * @param hostname The hostname to resolve.
     * @return A list of [InetAddress] instances for the hostname, potentially including both
     *   IPv4 and IPv6 addresses depending on the network's DNS server configuration.
     * @throws java.net.UnknownHostException If the hostname cannot be resolved.
     */
    override fun lookup(hostname: String): List<InetAddress> = network.getAllByName(hostname).toList()
}
