package so.prelude.android.sdk.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkRequest
import android.telephony.TelephonyManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the contract of [requestCellular]: the network the platform brings up wins,
 * the request outlives the lookup (it is released by [CellularLease.close], or by
 * cancellation), and the synchronous snapshot is the floor when the platform does
 * not answer or refuses the request.
 */
class CellularLeaseTest {
    @Before
    fun stubNetworkRequestBuilder() {
        // android.jar stubs return null from the chained builder calls.
        mockkConstructor(NetworkRequest.Builder::class)
        every { anyConstructed<NetworkRequest.Builder>().addTransportType(any()) } answers { self as NetworkRequest.Builder }
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } answers { self as NetworkRequest.Builder }
        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockk()
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `uses the network the platform brings up and holds the request until closed`() =
        runTest {
            val broughtUp = mockk<Network>()
            val cm = cmAnswering { it.onAvailable(broughtUp) }

            val lease = cm.requestCellular(1.seconds)

            assertSame(broughtUp, lease.network)
            verify(exactly = 0) { cm.unregisterNetworkCallback(any<NetworkCallback>()) }
            lease.close()
            verify(exactly = 1) { cm.unregisterNetworkCallback(any<NetworkCallback>()) }
        }

    @Test
    fun `falls back to the snapshot when the platform reports the request unavailable`() =
        runTest {
            val snapshot = cellularNetwork()
            val cm = cmAnswering(snapshot) { it.onUnavailable() }

            assertSame(snapshot.network, cm.requestCellular(1.seconds).network)
        }

    @Test
    fun `falls back to the snapshot when the platform stays silent past the timeout`() =
        runTest {
            val snapshot = cellularNetwork()
            val cm = cmAnswering(snapshot) { }

            assertSame(snapshot.network, cm.requestCellular(1.seconds).network)
        }

    @Test
    fun `has no network when neither the platform nor the snapshot has cellular`() =
        runTest {
            val cm = cmAnswering { }

            assertNull(cm.requestCellular(1.seconds).network)
        }

    @Test
    fun `keeps the snapshot lookup when the platform refuses the request`() =
        runTest {
            val snapshot = cellularNetwork()
            val cm = cmWith(snapshot)
            every { cm.requestNetwork(any<NetworkRequest>(), any<NetworkCallback>()) } throws SecurityException("CHANGE_NETWORK_STATE")

            val lease = cm.requestCellular(1.seconds)

            assertSame(snapshot.network, lease.network)
            lease.close()
            verify(exactly = 0) { cm.unregisterNetworkCallback(any<NetworkCallback>()) }
        }

    @Test
    fun `releases the request when the caller is cancelled while waiting`() =
        runTest {
            val cm = cmAnswering { }

            val waiting = launch { cm.requestCellular(10.seconds) }
            runCurrent()
            verify(exactly = 1) { cm.requestNetwork(any<NetworkRequest>(), any<NetworkCallback>()) }
            waiting.cancelAndJoin()

            verify(exactly = 1) { cm.unregisterNetworkCallback(any<NetworkCallback>()) }
        }

    @Test
    fun `asks the platform for cellular when mobile data is on`() =
        runTest {
            val broughtUp = mockk<Network>()
            val cm = cmAnswering { it.onAvailable(broughtUp) }

            assertSame(broughtUp, contextWith(cm, dataEnabled = true).requestCellular(1.seconds).network)
        }

    @Test
    fun `answers from the snapshot at once when mobile data is off`() =
        runTest {
            val snapshot = cellularNetwork()
            val cm = cmWith(snapshot)

            val lease = contextWith(cm, dataEnabled = false).requestCellular(1.seconds)

            assertSame(snapshot.network, lease.network)
            verify(exactly = 0) { cm.requestNetwork(any<NetworkRequest>(), any<NetworkCallback>()) }
            lease.close()
            verify(exactly = 0) { cm.unregisterNetworkCallback(any<NetworkCallback>()) }
        }

    @Test
    fun `asks the platform below API 26, where the mobile data switch cannot be read`() =
        runTest {
            val broughtUp = mockk<Network>()
            val cm = cmAnswering { it.onAvailable(broughtUp) }

            val context = contextWith(cm, dataEnabled = false, canReadSwitch = false)

            assertSame(broughtUp, context.requestCellular(1.seconds).network)
        }

    // --- test helpers --------------------------------------------------------

    private fun contextWith(
        cm: ConnectivityManager,
        dataEnabled: Boolean,
        canReadSwitch: Boolean = true,
    ): Context {
        mockkStatic(::canReadMobileDataSwitch)
        every { canReadMobileDataSwitch() } returns canReadSwitch
        val telephony = mockk<TelephonyManager>()
        every { telephony.isDataEnabled } returns dataEnabled
        val context = mockk<Context>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns cm
        every { context.getSystemService(TelephonyManager::class.java) } returns telephony
        return context
    }

    private data class FakeNetwork(
        val network: Network,
        val caps: NetworkCapabilities,
    )

    private fun cellularNetwork(): FakeNetwork {
        val caps = mockk<NetworkCapabilities>()
        every { caps.hasTransport(TRANSPORT_CELLULAR) } returns true
        every { caps.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { caps.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        return FakeNetwork(mockk(), caps)
    }

    private fun cmWith(snapshot: FakeNetwork? = null): ConnectivityManager {
        val cm = mockk<ConnectivityManager>()
        every { cm.activeNetwork } returns null
        every { cm.allNetworks } returns listOfNotNull(snapshot?.network).toTypedArray()
        snapshot?.let { every { cm.getNetworkCapabilities(it.network) } returns it.caps }
        every { cm.unregisterNetworkCallback(any<NetworkCallback>()) } just runs
        return cm
    }

    private fun cmAnswering(
        snapshot: FakeNetwork? = null,
        answer: (NetworkCallback) -> Unit,
    ): ConnectivityManager {
        val cm = cmWith(snapshot)
        val callback = slot<NetworkCallback>()
        every { cm.requestNetwork(any<NetworkRequest>(), capture(callback)) } answers { answer(callback.captured) }
        return cm
    }
}
