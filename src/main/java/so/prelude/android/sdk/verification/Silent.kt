package so.prelude.android.sdk.verification

import android.net.Network
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import so.prelude.android.sdk.Configuration
import so.prelude.android.sdk.Endpoint
import so.prelude.android.sdk.network.getCellular
import so.prelude.android.sdk.request.NetworkResponse
import so.prelude.android.sdk.request.Request
import so.prelude.android.sdk.request.userAgent
import java.net.URL
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val PER_HOP_TIMEOUT = 5.seconds

private data class HopConfig(
    val headers: Map<String, String>,
    val operationTimeout: Duration,
    val startMark: TimeMark,
    val maxRetries: Int,
    val interceptors: List<Interceptor>,
    val cellular: Network,
) {
    fun remainingMillis(): Long = (operationTimeout - startMark.elapsedNow()).inWholeMilliseconds.coerceAtLeast(1)

    fun hopTimeoutMillis(): Long = minOf(PER_HOP_TIMEOUT.inWholeMilliseconds, remainingMillis())
}

internal suspend fun performSilentVerification(
    url: URL,
    timeout: Duration = 20.seconds,
    configuration: Configuration,
): Result<String> {
    val quirks = ProviderQuirks.forURL(url)

    val cellular =
        configuration.context.getCellular()
            ?: return Result.failure(VerificationException("Can not connect to the cellular network."))

    val config =
        HopConfig(
            headers =
                mapOf(
                    "Connection" to "close",
                    "User-Agent" to userAgent,
                    "Accept" to "*/*",
                ) + quirks.headers,
            operationTimeout = timeout,
            startMark = TimeSource.Monotonic.markNow(),
            maxRetries = configuration.maxRetries,
            interceptors =
                when (val endpoint = configuration.endpoint) {
                    is Endpoint.Custom -> endpoint.okHttpInterceptors
                    Endpoint.Default -> emptyList()
                },
            cellular = cellular,
        )

    return withContext(IO) {
        try {
            withTimeout(timeout) {
                val json = Json { ignoreUnknownKeys = true }

                followRedirects(url, "GET", config).fold(
                    onSuccess = { response ->
                        response.body
                            ?.let { parseResponse(it, json) }
                            ?: Result.failure(VerificationException("Silent verification response is empty."))
                    },
                    onFailure = { Result.failure(it) },
                )
            }
        } catch (_: TimeoutCancellationException) {
            Result.failure(VerificationException("Silent verification timed out."))
        }
    }
}

private suspend fun followRedirects(
    url: URL,
    method: String,
    config: HopConfig,
    remainingRedirects: Int = 20,
): Result<NetworkResponse.Success> =
    when (val response = executeHopWithRetry(url, method, config)) {
        is NetworkResponse.Success -> {
            Result.success(response)
        }

        is NetworkResponse.Error -> {
            Result.failure(VerificationException("${response.code}, ${response.message}"))
        }

        is NetworkResponse.Redirect -> {
            if (remainingRedirects <= 0) {
                Result.failure(VerificationException("Too many redirects"))
            } else {
                resolveUrl(url, response.location)
                    ?.let { nextUrl ->
                        followRedirects(
                            url = nextUrl,
                            method = redirectMethod(response.code, method),
                            config = config,
                            remainingRedirects = remainingRedirects - 1,
                        )
                    }
                    ?: Result.failure(VerificationException("Invalid redirect URL: ${response.location}"))
            }
        }
    }

private suspend fun executeHopWithRetry(
    url: URL,
    method: String,
    config: HopConfig,
    attempt: Int = 0,
): NetworkResponse {
    if (attempt > 0) {
        delay(2.0.pow(attempt - 1).toLong() * 250L)
    }

    val response = buildHopRequest(url, method, config).send(config.cellular)

    return when {
        response.isRetryable() && attempt < config.maxRetries -> {
            executeHopWithRetry(url, method, config, attempt + 1)
        }

        else -> {
            response
        }
    }
}

private fun buildHopRequest(
    url: URL,
    method: String,
    config: HopConfig,
): Request {
    val hopTimeout = config.hopTimeoutMillis()
    return Request(
        url = url,
        method = method,
        timeout = hopTimeout,
        readTimeout = hopTimeout,
        maxRetries = 0,
        headers = config.headers,
        includeRequestDateHeader = false,
        vpnEnabled = false,
        followRedirects = false,
        okHttpInterceptors = config.interceptors,
    )
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

private fun NetworkResponse.isRetryable(): Boolean = code in 500..599 || code == -1

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
