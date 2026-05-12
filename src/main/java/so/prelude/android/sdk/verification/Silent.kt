package so.prelude.android.sdk.verification

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import so.prelude.android.sdk.Configuration
import so.prelude.android.sdk.Endpoint
import so.prelude.android.sdk.defaultEndpoint
import so.prelude.android.sdk.network.getCellular
import so.prelude.android.sdk.request.NetworkResponse
import so.prelude.android.sdk.request.Request
import so.prelude.android.sdk.request.userAgent
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import okhttp3.TlsVersion as OkHttpTlsVersion

private const val PER_HOP_TIMEOUT_MS = 5_000L
private const val MIN_TIMEOUT_MS = 1_000L

internal suspend fun performSilentVerification(
    url: URL,
    timeout: Duration = 20.seconds,
    configuration: Configuration,
): Result<String> {
    val host =
        url.host
            ?: return Result.failure(VerificationException("Invalid verification URL: missing host"))

    val quirks = ProviderQuirks.forURL(url)

    val cellular =
        configuration.context.getCellular()
            ?: return Result.failure(VerificationException("Can not connect to the cellular network."))

    val baseHeaders =
        mapOf(
            "connection" to "close",
            "user-agent" to userAgent,
            "accept" to "*/*",
        ) + quirks.headers

    val interceptors =
        when (val endpoint = configuration.endpoint) {
            is Endpoint.Custom -> endpoint.okHttpInterceptors
            Endpoint.Default -> emptyList()
        }

    val maxRetries = configuration.maxRetries
    val timeoutMillis = timeout.inWholeMilliseconds

    return withContext(IO) {
        try {
            withTimeout(timeout) {
                val json = Json { ignoreUnknownKeys = true }
                var serverQuirks = ServerQuirks.EMPTY
                var targetUrl = url
                var targetMethod = "GET"
                val startMark = TimeSource.Monotonic.markNow()

                // Extract server quirks from the first Prelude response (always a redirect).
                if (isTrustedHost(host, configuration.endpoint)) {
                    val firstHopTimeout = minOf(PER_HOP_TIMEOUT_MS, timeoutMillis)
                    val firstRequest =
                        Request(
                            url = url,
                            method = "GET",
                            timeout = firstHopTimeout,
                            readTimeout = firstHopTimeout,
                            maxRetries = maxRetries,
                            headers = baseHeaders,
                            includeRequestDateHeader = false,
                            vpnEnabled = false,
                            followRedirects = false,
                            okHttpInterceptors = interceptors,
                        )

                    when (val firstResponse = firstRequest.send(cellular)) {
                        is NetworkResponse.Redirect -> {
                            serverQuirks = ServerQuirks.fromHeaders(firstResponse.headers)
                            targetUrl =
                                resolveUrl(url, firstResponse.location)
                                    ?: return@withTimeout Result.failure(
                                        VerificationException("Invalid redirect URL: ${firstResponse.location}"),
                                    )
                            targetMethod = redirectMethod(firstResponse.code, "GET")
                        }

                        else -> {
                            return@withTimeout Result.failure(
                                VerificationException("Unexpected response from verification server"),
                            )
                        }
                    }
                }

                // Main request with quirks applied; OkHttp handles remaining redirects.
                val targetHost =
                    targetUrl.host
                        ?: return@withTimeout Result.failure(
                            VerificationException("Invalid redirect URL: missing host"),
                        )
                val quirkHeaders = serverQuirks.headersForHost(targetHost)
                val mergedHeaders = baseHeaders + quirkHeaders
                val tlsVersion = serverQuirks.tlsVersionForHost(targetHost)
                val tlsVersions = tlsVersion?.protocols?.map { OkHttpTlsVersion.forJavaName(it) }

                val elapsedMs = startMark.elapsedNow().inWholeMilliseconds
                val remainingMs = maxOf(timeoutMillis - elapsedMs, MIN_TIMEOUT_MS)
                val request =
                    Request(
                        url = targetUrl,
                        method = targetMethod,
                        timeout = remainingMs,
                        readTimeout = remainingMs,
                        maxRetries = maxRetries,
                        headers = mergedHeaders,
                        includeRequestDateHeader = false,
                        vpnEnabled = false,
                        followRedirects = true,
                        okHttpInterceptors = interceptors,
                        tlsVersions = tlsVersions,
                    )

                when (val response = request.send(cellular)) {
                    is NetworkResponse.Success -> {
                        response.body
                            ?.let { parseResponse(it, json) }
                            ?: Result.failure(VerificationException("Silent verification response is empty."))
                    }

                    is NetworkResponse.Error -> {
                        Result.failure(VerificationException("${response.code}, ${response.message}"))
                    }

                    is NetworkResponse.Redirect -> {
                        Result.failure(VerificationException("Too many redirects"))
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            Result.failure(VerificationException("Silent verification timed out."))
        }
    }
}

private fun isTrustedHost(
    host: String,
    endpoint: Endpoint,
): Boolean =
    when (endpoint) {
        is Endpoint.Custom -> {
            true
        }

        is Endpoint.Default -> {
            val defaultHost = URL(defaultEndpoint()).host
            defaultHost != null && shareSameTLD(host, defaultHost)
        }
    }

private fun shareSameTLD(
    host1: String,
    host2: String,
): Boolean {
    val components1 = host1.lowercase().split(".")
    val components2 = host2.lowercase().split(".")
    if (components1.size < 2 || components2.size < 2) return false
    return components1.takeLast(2) == components2.takeLast(2)
}

private fun redirectMethod(
    statusCode: Int,
    currentMethod: String,
): String =
    when (statusCode) {
        307, 308 -> currentMethod
        else -> "GET"
    }

private fun resolveUrl(
    base: URL,
    location: String,
): URL? = runCatching { URL(base, location) }.getOrNull()

private fun parseResponse(
    responseBody: ByteArray,
    json: Json,
): Result<String> =
    responseBody
        .tryParse<VerificationResponseSuccess>(json)
        .map { Result.success(it.code) }
        .getOrElse { parseError(responseBody, json) }

private fun parseError(
    responseBody: ByteArray,
    json: Json,
): Result<String> =
    responseBody
        .tryParse<VerificationResponseError>(json)
        .map { Result.failure<String>(VerificationException(it.reason)) }
        .getOrElse { Result.failure(VerificationException("Failed to parse silent verification response.")) }

private inline fun <reified T> ByteArray.tryParse(json: Json): Result<T> =
    try {
        Result.success(json.decodeFromString<T>(String(this)))
    } catch (e: Exception) {
        Result.failure(e)
    }
