package so.prelude.android.sdk.verification

import java.net.URL

/**
 * Provider-specific quirks that modify request behavior based on the URL domain.
 *
 * Different carriers have different requirements for silent verification requests.
 * This data class encapsulates those provider-specific behaviors.
 *
 * @property headers Headers to include in the request.
 */
internal data class ProviderQuirks(
    val headers: Map<String, String>,
) {
    companion object {
        /** Default quirks used when no provider-specific quirks are found. */
        val DEFAULT =
            ProviderQuirks(
                headers = emptyMap(),
            )

        /**
         * Returns the appropriate quirks for the given URL based on its domain.
         *
         * @param url The request URL to analyze.
         * @return Provider-specific quirks for the URL's domain.
         */
        fun forURL(url: URL): ProviderQuirks {
            val host = url.host?.lowercase() ?: return DEFAULT

            // Match against known provider domains
            for ((pattern, quirks) in providerQuirksMap) {
                if (host == pattern || host.endsWith(".$pattern")) {
                    return quirks
                }
            }

            return DEFAULT
        }
    }
}

private val providerQuirksMap: Map<String, ProviderQuirks> =
    mapOf(
        // Bouygues Telecom (French carrier)
        "bouyguestelecom.fr" to
            ProviderQuirks(
                headers =
                    mapOf(
                        "Accept" to "text/html;q=0.9,application/xhtml+xml,application/xml,application/json,*/*;q=0.8",
                    ),
            ),
    )
