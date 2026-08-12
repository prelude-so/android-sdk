package so.prelude.android.sdk.request

import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import so.prelude.android.sdk.Configuration
import so.prelude.android.sdk.network.NetworkBoundDns
import so.prelude.android.sdk.request.NetworkResponse.Error
import so.prelude.android.sdk.request.NetworkResponse.Redirect
import so.prelude.android.sdk.request.NetworkResponse.Success
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.math.min
import kotlin.math.pow
import okhttp3.TlsVersion as OkHttpTlsVersion

/**
 * Request is a network HTTP request.
 *
 * @property url The request URL.
 * @property method The request method.
 * @property headers The HTTP headers to include in the request.
 * @property body The request body content.
 * @property timeout The request connection timeout in milliseconds.
 * @property readTimeout The request read timeout in milliseconds. -1 uses the OkHttp default (10s).
 * @property includeRequestDateHeader Whether to include the X-SDK-Request-Date header.
 * @property maxRetries The maximum number of automatic retries on timeout, failed lookup or server error.
 * @property vpnEnabled Whether the device is connected via VPN.
 * @property followRedirects Whether to follow HTTP redirects.
 * @property okHttpInterceptors Additional OkHttp network interceptors.
 * @property dns The resolver used when the request is not bound to [Network].
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
    private val tlsVersions: List<OkHttpTlsVersion>? = null,
    private val allowInsecureTLS: Boolean = false,
    // When false, do not bind the OkHttp socket factory / DNS to [network].
    // Reserved for SNA: Silent verification and the cellular OPTIONS fingerprint.
    // The generic signals POST opts out so it can ride Android's default
    // network, which the OS keeps validated and routes across handles for us.
    private val bindToNetwork: Boolean = true,
    private val dns: Dns = Dns.SYSTEM,
) {
    private var cachedClient: Pair<Network, OkHttpClient>? = null

    private fun getOrCreateClient(network: Network): OkHttpClient =
        cachedClient?.takeIf { it.first == network }?.second
            ?: buildOkHttpClient(
                network = network,
                headers = headers,
                timeout = timeout,
                bindToNetwork = bindToNetwork && !vpnEnabled,
                interceptors = okHttpInterceptors,
                tlsVersions = tlsVersions,
                allowInsecureTLS = allowInsecureTLS,
            ).also {
                cachedClient = Pair(network, it)
            }

    suspend fun send(
        network: Network,
        retryAttempt: Int = 0,
    ): NetworkResponse =
        try {
            val client = getOrCreateClient(network)

            val request =
                Request
                    .Builder()
                    .url(url)
                    .method(method, body?.toRequestBody(null, 0, body.size))
                    .apply {
                        header("x-sdk-retry-attempt", retryAttempt.toString())
                    }.build()

            when (val result = sendRequest(client, request, retryAttempt)) {
                is RequestResult.Ok -> {
                    Success(fromPayloadRequest = request.body != null, code = result.code, body = result.body)
                }

                is RequestResult.Redirect -> {
                    Redirect(
                        fromPayloadRequest = request.body != null,
                        code = result.code,
                        location = result.location,
                        headers = result.headers,
                    )
                }

                is RequestResult.Error -> {
                    Error(fromPayloadRequest = request.body != null, code = result.code, message = result.message)
                }

                is RequestResult.Retry -> {
                    val requestDelay = 2.0.pow(retryAttempt).toLong() * 250L
                    delay(min(requestDelay, 10_000L))
                    send(network, retryAttempt + 1)
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
        bindToNetwork: Boolean = true,
        interceptors: List<Interceptor>,
        tlsVersions: List<OkHttpTlsVersion>? = null,
        allowInsecureTLS: Boolean = false,
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
                            "x-sdk-request-date",
                            ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME),
                        )
                    }

                    for ((key, value) in headers) {
                        builder.header(key, value)
                    }

                    chain.proceed(builder.build())
                }

        if (allowInsecureTLS) {
            val trustAll =
                @Suppress("CustomX509TrustManager")
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String,
                    ) = Unit

                    override fun checkServerTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String,
                    ) = Unit

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }
            val sslContext =
                javax.net.ssl.SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trustAll), java.security.SecureRandom())
                }
            clientBuilder
                .sslSocketFactory(sslContext.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
        }

        if (tlsVersions != null) {
            val spec =
                ConnectionSpec
                    .Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(*tlsVersions.toTypedArray())
                    .build()
            clientBuilder.connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
        }

        interceptors.forEach {
            clientBuilder.addNetworkInterceptor(it)
        }

        // Bound networking is for SNA: verification must originate from
        // the carrier route, and the cellular OPTIONS preflight does the same to
        // give the backend a carrier-route fingerprint. The dispatch POST does
        // not need either, and binding it to a hand-picked Network handle can be
        // a source of UnknownHostException on flaky cellular.
        if (bindToNetwork) {
            clientBuilder.socketFactory(network.socketFactory)
            clientBuilder.dns(NetworkBoundDns(network))
        } else {
            clientBuilder.dns(dns)
        }

        return clientBuilder.build()
    }

    private fun sendRequest(
        client: OkHttpClient,
        request: Request,
        currentAttempt: Int,
    ): RequestResult =
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        RequestResult.Ok(response.code, response.body?.bytes())
                    }

                    response.isRedirect -> {
                        response.header("location")?.let { location ->
                            val quirkHeaders =
                                response.headers.toMultimap().filter { (key, _) ->
                                    key.lowercase().startsWith("x-sdk-quirk-")
                                }
                            RequestResult.Redirect(response.code, location, quirkHeaders)
                        } ?: RequestResult.Error(response.code, "Redirect without Location header")
                    }

                    response.code.isInternalServerError() && hasRemainingRetries(currentAttempt) -> {
                        RequestResult.Retry
                    }

                    else -> {
                        RequestResult.Error(response.code, response.message)
                    }
                }
            }
        } catch (e: IOException) {
            if (e.isTransient() && hasRemainingRetries(currentAttempt)) {
                RequestResult.Retry
            } else {
                RequestResult.Error(message = e.message ?: e.javaClass.simpleName, source = e)
            }
        } catch (e: IllegalStateException) {
            RequestResult.Error(message = e.message ?: "IllegalStateException", source = e)
        } catch (e: Exception) {
            RequestResult.Error(message = e.message ?: "Exception", source = e)
        }

    private sealed interface RequestResult {
        data class Ok(
            val code: Int,
            val body: ByteArray?,
        ) : RequestResult

        data class Error(
            val code: Int = -1,
            val message: String,
            val source: Exception? = null,
        ) : RequestResult

        data class Redirect(
            val code: Int,
            val location: String,
            val headers: Map<String, List<String>> = emptyMap(),
        ) : RequestResult

        data object Retry : RequestResult
    }

    private fun Int.isInternalServerError(): Boolean = this in (500..599)

    /**
     * A read that timed out or a lookup that failed points at the link rather than
     * the destination, and the next attempt often gets through. Anything else is final.
     */
    private fun IOException.isTransient(): Boolean = this is SocketTimeoutException || this is UnknownHostException

    private fun hasRemainingRetries(attempt: Int): Boolean = attempt < maxRetries
}
