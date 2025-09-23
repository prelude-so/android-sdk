package so.prelude.android.sdk.verification

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import so.prelude.android.sdk.Configuration
import so.prelude.android.sdk.Endpoint
import so.prelude.android.sdk.network.getCellular
import so.prelude.android.sdk.request.NetworkResponse
import so.prelude.android.sdk.request.Request
import so.prelude.android.sdk.request.userAgent
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun performSilentVerification(
    url: URL,
    timeout: Duration = 10.seconds,
    configuration: Configuration,
): Result<String> {
    val request =
        Request(
            url = url,
            timeout = timeout.inWholeMilliseconds,
            headers =
                mapOf(
                    "Connection" to "close",
                    "User-Agent" to userAgent,
                    "Accept" to "text/html;q=0.9,application/xhtml+xml,application/xml,application/json,*/*;q=0.8",
                ),
            includeRequestDateHeader = false,
            vpnEnabled = false,
            okHttpInterceptors =
                when (val endpoint = configuration.endpoint) {
                    is Endpoint.Custom -> endpoint.okHttpInterceptors
                    Endpoint.Default -> emptyList()
                },
        )

    val cellular = configuration.context.getCellular()
    return withContext(IO) {
        val json = Json { ignoreUnknownKeys = true }
        cellular?.let {
            when (val response = request.send(cellular)) {
                is NetworkResponse.Error ->
                    Result.failure(
                        VerificationException("Silent verification error: ${response.code}, ${response.message}"),
                    )
                is NetworkResponse.Success -> {
                    if (response.body == null) {
                        Result.failure(VerificationException("Silent verification response is empty."))
                    } else {
                        parseResponse(response.body, json)
                    }
                }
            }
        } ?: Result.failure(VerificationException("Can not connect to the cellular network."))
    }
}

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
