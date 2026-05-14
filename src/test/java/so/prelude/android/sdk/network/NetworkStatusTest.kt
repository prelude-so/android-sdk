package so.prelude.android.sdk.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two-step contract of [firstMatching]:
 *  - prefer the non-deprecated `activeNetwork` when it matches the requested transport;
 *  - fall back to `allNetworks` only when it doesn't.
 *
 * Regression context: 0.5.0 swapped this code path to an async `requestNetwork`
 * callback with a 50ms timeout, which silently dropped most cold-start dispatches.
 * The mocks here never invoke any `NetworkCallback.onAvailable`; any future
 * change that reintroduces a callback-timing dependency will hang or return
 * null and the test will fail.
 */
class NetworkStatusTest {
    @Test
    fun `returns the wifi network when only wifi is registered`() {
        val wifi = network(TRANSPORT_WIFI)
        val cm = cmWith(active = wifi)
        assertSame(wifi.network, cm.firstMatching(listOf(TRANSPORT_WIFI, TRANSPORT_ETHERNET)))
    }

    @Test
    fun `returns the ethernet network when only ethernet is registered`() {
        val eth = network(TRANSPORT_ETHERNET)
        val cm = cmWith(active = eth)
        assertSame(eth.network, cm.firstMatching(listOf(TRANSPORT_WIFI, TRANSPORT_ETHERNET)))
    }

    @Test
    fun `returns the cellular network when only cellular is registered`() {
        val cell = network(TRANSPORT_CELLULAR)
        val cm = cmWith(active = cell)
        assertSame(cell.network, cm.firstMatching(listOf(TRANSPORT_CELLULAR)))
    }

    @Test
    fun `returns null when no registered network has a matching transport`() {
        val cm = cmWith(active = network(TRANSPORT_CELLULAR))
        assertNull(cm.firstMatching(listOf(TRANSPORT_WIFI, TRANSPORT_ETHERNET)))
    }

    @Test
    fun `returns null when both activeNetwork and allNetworks are empty`() {
        val cm = mockk<ConnectivityManager>()
        every { cm.activeNetwork } returns null
        every { cm.allNetworks } returns emptyArray()
        assertNull(cm.firstMatching(listOf(TRANSPORT_WIFI)))
    }

    @Test
    fun `skips networks whose getNetworkCapabilities returned null`() {
        val capless = mockk<Network>()
        val wifi = network(TRANSPORT_WIFI)
        val cm = mockk<ConnectivityManager>()
        every { cm.activeNetwork } returns null
        every { cm.allNetworks } returns arrayOf(capless, wifi.network)
        every { cm.getNetworkCapabilities(capless) } returns null
        every { cm.getNetworkCapabilities(wifi.network) } returns wifi.caps
        assertSame(wifi.network, cm.firstMatching(listOf(TRANSPORT_WIFI)))
    }

    @Test
    fun `regression - completes synchronously without depending on a callback`() {
        // 0.5.0's broken code used ConnectivityManager.requestNetwork with a 50ms
        // timeout. These mocks never invoke a NetworkCallback; if any future
        // change reintroduces async dependency, this test will hang or return null.
        val wifi = network(TRANSPORT_WIFI)
        val cm = cmWith(active = wifi)
        val start = System.nanoTime()
        val result = cm.firstMatching(listOf(TRANSPORT_WIFI))
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertSame(wifi.network, result)
        assertTrue(
            "Expected sub-50ms; took ${elapsedMs}ms — async path reintroduced?",
            elapsedMs < 50,
        )
    }

    @Test
    fun `uses activeNetwork without consulting allNetworks when active matches`() {
        val wifi = network(TRANSPORT_WIFI)
        val cm = cmWith(active = wifi)
        // If the fallback ran, allNetworks would be queried. Make it explode so
        // we know for certain we stayed on the modern path.
        every { cm.allNetworks } answers { error("allNetworks should not be queried when activeNetwork already matches") }
        assertSame(wifi.network, cm.firstMatching(listOf(TRANSPORT_WIFI)))
    }

    @Test
    fun `falls back to allNetworks when active does not match the requested transport`() {
        // Wi-Fi is the default route, but we're asking for cellular.
        val wifi = network(TRANSPORT_WIFI)
        val cell = network(TRANSPORT_CELLULAR)
        val cm = cmWith(active = wifi, cell)
        assertSame(cell.network, cm.firstMatching(listOf(TRANSPORT_CELLULAR)))
    }

    // --- test helpers --------------------------------------------------------

    private data class FakeNetwork(
        val network: Network,
        val caps: NetworkCapabilities,
    )

    private fun network(vararg transports: Int): FakeNetwork {
        val net = mockk<Network>()
        val caps = mockk<NetworkCapabilities>()
        // Mock every transport bit we ever query so unmatched ones return false.
        for (t in listOf(TRANSPORT_WIFI, TRANSPORT_ETHERNET, TRANSPORT_CELLULAR)) {
            every { caps.hasTransport(t) } returns (t in transports)
        }
        return FakeNetwork(net, caps)
    }

    private fun cmWith(
        active: FakeNetwork? = null,
        vararg nets: FakeNetwork,
    ): ConnectivityManager {
        val cm = mockk<ConnectivityManager>()
        every { cm.activeNetwork } returns active?.network
        every { cm.allNetworks } returns nets.map { it.network }.toTypedArray()
        (nets.toList() + listOfNotNull(active)).forEach { fn ->
            every { cm.getNetworkCapabilities(fn.network) } returns fn.caps
        }
        return cm
    }
}
