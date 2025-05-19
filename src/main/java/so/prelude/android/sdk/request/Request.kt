package so.prelude.android.sdk.request

import android.net.Network
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.IOException
import so.prelude.android.sdk.request.NetworkResponse.Error
import so.prelude.android.sdk.request.NetworkResponse.Success
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
 */
internal class Request(
    private val url: URL,
    private val method: String = "GET",
    private val headers: Map<String, String> = emptyMap(),
    private val body: ByteArray? = null,
    private val timeout: Long = 2000L, // in milliseconds
    private val includeRequestDateHeader: Boolean = true,
    private val maxRetries: Int = 0,
) {
    suspend fun send(
        network: Network,
        retryCount: Int = this.maxRetries,
    ): NetworkResponse {
        val requestDelay = 2.0.pow(this.maxRetries - retryCount).toLong() * 250L
        if (requestDelay > 0) {
            delay(requestDelay)
        }

        val client = buildOkHttpClient(network, headers, timeout)

        val request =
            Request
                .Builder()
                .url(url)
                .method(method, body?.toRequestBody(null, 0, body.size))
                .build()

        return when (val result = sendRequest(client, request, retryCount)) {
            is RequestResult.Ok -> Success(result.code, result.body?.bytes())
            is RequestResult.Error -> Error(result.code, result.message)
            is RequestResult.Retry -> send(network, retryCount - 1)
            is RequestResult.Throw -> throw result.error
        }
    }

    private fun buildOkHttpClient(
        network: Network,
        headers: Map<String, String>,
        timeout: Long,
    ): OkHttpClient {
        val clientBuilder =
            OkHttpClient
                .Builder()
                .connectTimeout(timeout, MILLISECONDS)
                .socketFactory(network.socketFactory)
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

        return clientBuilder.build()
    }

    private fun sendRequest(
        client: OkHttpClient,
        request: Request,
        currentRetries: Int,
    ): RequestResult =
        try {
            val response: Response = client.newCall(request).execute()
            if (response.isSuccessful) {
                RequestResult.Ok(response.code, response.body)
            } else {
                if (response.code.isInternalServerError() && hasRemainingRetries(currentRetries)) {
                    RequestResult.Retry
                } else {
                    RequestResult.Error(response.code, response.message)
                }
            }
        } catch (e: SocketTimeoutException) {
            if (hasRemainingRetries(currentRetries)) {
                RequestResult.Retry
            } else {
                RequestResult.Throw(e)
            }
        } catch (e: IOException) {
            RequestResult.Error(-1, e.message ?: "IOException")
        } catch (e: IllegalStateException) {
            RequestResult.Error(-1, e.message ?: "IllegalStateException")
        } catch (e: Exception) {
            RequestResult.Throw(e)
        }

    private sealed interface RequestResult {
        data class Ok(
            val code: Int,
            val body: ResponseBody?,
        ) : RequestResult

        data class Error(
            val code: Int,
            val message: String,
        ) : RequestResult

        data object Retry : RequestResult

        data class Throw(
            val error: Exception,
        ) : RequestResult
    }

    private fun Int.isInternalServerError(): Boolean = this in (500..599)

    private fun hasRemainingRetries(retryCount: Int): Boolean = retryCount > 0
}
