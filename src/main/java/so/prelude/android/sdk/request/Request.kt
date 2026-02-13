package so.prelude.android.sdk.request

import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.IOException
import so.prelude.android.sdk.Configuration
import so.prelude.android.sdk.network.NetworkBoundDns
import so.prelude.android.sdk.request.NetworkResponse.Error
import so.prelude.android.sdk.request.NetworkResponse.Redirect
import so.prelude.android.sdk.request.NetworkResponse.Success
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.math.pow

/**
 * Request is a network HTTP request.
 *
 * @property url The request URL.
 * @property method The request method.
 * @property headers The HTTP headers to include in the request.
 * @property body The request body content.
 * @property timeout The request connection timeout in milliseconds.
 * @property readTimeout The request read timeout in milliseconds. -1 uses the OkHttp default (10s).
 */
internal class Request(
    private val url: URL,
    private val method: String = "GET",
    private val headers: Map<String, String> = emptyMap(),
    private val body: ByteArray? = null,
    private val timeout: Long = Configuration.DEFAULT_REQUEST_TIMEOUT, // in milliseconds
    private val readTimeout: Long = -1,
    private val includeRequestDateHeader: Boolean = true,
    private val maxRetries: Int = Configuration.DEFAULT_MAX_RETRY_COUNT,
    private val vpnEnabled: Boolean,
    private val followRedirects: Boolean = true,
    private val okHttpInterceptors: List<Interceptor> = emptyList(),
) {
    private var cachedClient: Pair<Network, OkHttpClient>? = null

    private fun getOrCreateClient(network: Network): OkHttpClient =
        cachedClient?.takeIf { it.first == network }?.second
            ?: buildOkHttpClient(network, headers, timeout, vpnEnabled, okHttpInterceptors).also {
                cachedClient = Pair(network, it)
            }

    suspend fun send(
        network: Network,
        retryCount: Int = this.maxRetries,
    ): NetworkResponse =
        try {
            val requestDelay =
                if (retryCount == this.maxRetries) {
                    0L
                } else {
                    2.0.pow(this.maxRetries - retryCount - 1).toLong() * 250L
                }
            if (requestDelay > 0) {
                delay(requestDelay)
            }

            val client = getOrCreateClient(network)

            val request =
                Request
                    .Builder()
                    .url(url)
                    .method(method, body?.toRequestBody(null, 0, body.size))
                    .build()

            when (val result = sendRequest(client, request, retryCount)) {
                is RequestResult.Ok -> {
                    Success(fromPayloadRequest = request.body != null, code = result.code, body = result.body?.bytes())
                }

                is RequestResult.Redirect -> {
                    Redirect(
                        fromPayloadRequest = request.body != null,
                        code = result.code,
                        location = result.location,
                    )
                }

                is RequestResult.Error -> {
                    Error(fromPayloadRequest = request.body != null, code = result.code, message = result.message)
                }

                is RequestResult.Retry -> {
                    send(network, retryCount - 1)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Error(fromPayloadRequest = body != null, code = -1, message = e.message ?: "Unknown error")
        }

    private fun buildOkHttpClient(
        network: Network,
        headers: Map<String, String>,
        timeout: Long,
        usingVpn: Boolean,
        interceptors: List<Interceptor>,
    ): OkHttpClient {
        val clientBuilder =
            OkHttpClient
                .Builder()
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .connectTimeout(timeout, MILLISECONDS)
                .apply { if (readTimeout >= 0) readTimeout(readTimeout, MILLISECONDS) }
                .proxy(Proxy.NO_PROXY)
                .addNetworkInterceptor { chain ->
                    val builder = chain.request().newBuilder()

                    if (includeRequestDateHeader) {
                        builder.header(
                            "X-SDK-Request-Date",
                            ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME),
                        )
                    }

                    for ((key, value) in headers) {
                        builder.header(key, value)
                    }

                    chain.proceed(builder.build())
                }

        interceptors.forEach {
            clientBuilder.addNetworkInterceptor(it)
        }

        // Use the default socket factory for VPN requests, bind to the network for non-VPN requests.
        if (!usingVpn) {
            clientBuilder.socketFactory(network.socketFactory)
            clientBuilder.dns(NetworkBoundDns(network))
        }

        return clientBuilder.build()
    }

    private fun sendRequest(
        client: OkHttpClient,
        request: Request,
        currentRetries: Int,
    ): RequestResult =
        try {
            val response: Response = client.newCall(request).execute()
            when {
                response.isSuccessful -> {
                    RequestResult.Ok(response.code, response.body)
                }

                response.isRedirect -> {
                    response.header("Location")?.let { RequestResult.Redirect(response.code, it) }
                        ?: RequestResult.Error(response.code, "Redirect without Location header")
                }

                response.code.isInternalServerError() && hasRemainingRetries(currentRetries) -> {
                    RequestResult.Retry
                }

                else -> {
                    RequestResult.Error(response.code, response.message)
                }
            }
        } catch (e: SocketTimeoutException) {
            if (hasRemainingRetries(currentRetries)) {
                RequestResult.Retry
            } else {
                RequestResult.Error(message = e.message ?: "SocketTimeoutException", source = e)
            }
        } catch (e: IOException) {
            RequestResult.Error(message = e.message ?: "IOException", source = e)
        } catch (e: IllegalStateException) {
            RequestResult.Error(message = e.message ?: "IllegalStateException", source = e)
        } catch (e: Exception) {
            RequestResult.Error(message = e.message ?: "Exception", source = e)
        }

    private sealed interface RequestResult {
        data class Ok(
            val code: Int,
            val body: ResponseBody?,
        ) : RequestResult

        data class Error(
            val code: Int = -1,
            val message: String,
            val source: Exception? = null,
        ) : RequestResult

        data class Redirect(
            val code: Int,
            val location: String,
        ) : RequestResult

        data object Retry : RequestResult
    }

    private fun Int.isInternalServerError(): Boolean = this in (500..599)

    private fun hasRemainingRetries(retryCount: Int): Boolean = retryCount > 0
}
