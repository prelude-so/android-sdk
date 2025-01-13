package so.prelude.android.sdk.request

import android.net.Network
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import so.prelude.android.sdk.request.NetworkResponse.Error
import so.prelude.android.sdk.request.NetworkResponse.Success
import java.net.URL
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit.MILLISECONDS

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
) {
    fun send(network: Network): NetworkResponse {
        val clientBuilder =
            OkHttpClient
                .Builder()
                .connectTimeout(timeout, MILLISECONDS)
                .socketFactory(network.socketFactory)
                .addNetworkInterceptor { chain ->
                    val builder =
                        chain
                            .request()
                            .newBuilder()
                            .header(
                                "X-SDK-Request-Date",
                                ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME),
                            )
                    for ((key, value) in headers) {
                        builder.header(key, value)
                    }

                    chain.proceed(builder.build())
                }

        val client = clientBuilder.build()

        val request =
            Request
                .Builder()
                .url(url)
                .method(method, body?.toRequestBody(null, 0, body.size))
                .build()

        val response: Response = client.newCall(request).execute()
        return if (response.isSuccessful) {
            Success(response.code, response.body?.bytes())
        } else {
            Error(response.code)
        }
    }
}
