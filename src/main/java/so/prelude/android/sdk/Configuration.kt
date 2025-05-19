package so.prelude.android.sdk

import android.content.Context

/**
 * Configuration is the configuration for the Prelude SDK.
 */
data class Configuration
    @JvmOverloads
    constructor(
        /**
         * The Android application context.
         */
        var context: Context,
        /**
         * The SDK key that identifies your app within the Prelude API.
         */
        var sdkKey: String,
        /**
         * The endpoint address of the Prelude API.
         */
        var endpoint: Endpoint = Endpoint.Default,
        /**
         * The timeout in milliseconds for the network requests.
         */
        var requestTimeout: Long = DEFAULT_REQUEST_TIMEOUT,
        /**
         * The maximum number of automatic retries for the network requests in case of server errors and timeouts.
         */
        var maxRetries: Int = DEFAULT_MAX_RETRY_COUNT,
    ) {
        /**
         * The endpoint address of the Prelude API.
         */
        val endpointAddress: String get() =
            when (endpoint) {
                is Endpoint.Default -> defaultEndpoint()
                is Endpoint.Custom -> (endpoint as Endpoint.Custom).address
            }

        companion object {
            /**
             * The default timeout.
             */
            const val DEFAULT_REQUEST_TIMEOUT = 2000L

            /**
             * The default automatic retry count for server errors and timeouts.
             */
            const val DEFAULT_MAX_RETRY_COUNT = 0
        }
    }

/**
 * Endpoint is the endpoint address of the Prelude API.
 */
sealed class Endpoint {
    /**
     * The default endpoint address.
     */
    data object Default : Endpoint()

    /**
     * Custom endpoint address.
     */
    data class Custom(
        val address: String,
    ) : Endpoint()
}
