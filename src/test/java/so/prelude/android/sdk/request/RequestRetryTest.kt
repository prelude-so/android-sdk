package so.prelude.android.sdk.request

import android.net.Network
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.UnknownHostException

private const val UNRESOLVED = "Unable to resolve host: No address associated with hostname"

class RequestRetryTest {
    private val maxRetries = 3

    @Test
    fun `retries a failed lookup up to maxRetries before giving up`() =
        runTest {
            val dns = FakeDns { throw UnknownHostException(UNRESOLVED) }

            val response = request(dns).send(mockk<Network>())

            assertEquals(maxRetries + 1, dns.lookups)
            assertTrue("expected an error, got $response", response is NetworkResponse.Error)
            assertEquals(UNRESOLVED, (response as NetworkResponse.Error).message)
            assertEquals(-1, response.code)
        }

    @Test
    fun `stops resolving once a lookup succeeds`() =
        runTest {
            val dns =
                FakeDns { attempt ->
                    if (attempt == 0) throw UnknownHostException(UNRESOLVED)
                    listOf(InetAddress.getLoopbackAddress())
                }

            val response = request(dns).send(mockk<Network>())

            // One failure, one success. The connect that follows is refused, which is not
            // a lookup failure, so it is not retried.
            assertEquals(2, dns.lookups)
            assertTrue("expected an error, got $response", response is NetworkResponse.Error)
        }

    private fun request(dns: Dns) =
        Request(
            url = URL("https://dispatch.test:${refusedPort()}/v1/signals"),
            method = "POST",
            body = byteArrayOf(0x01),
            timeout = 1_000,
            maxRetries = maxRetries,
            vpnEnabled = false,
            bindToNetwork = false,
            dns = dns,
        )

    // Claiming a port and letting it go leaves one nothing is listening on.
    private fun refusedPort(): Int = ServerSocket(0).use { it.localPort }
}

private class FakeDns(
    private val onLookup: (attempt: Int) -> List<InetAddress>,
) : Dns {
    var lookups = 0
        private set

    override fun lookup(hostname: String): List<InetAddress> = onLookup(lookups++)
}
