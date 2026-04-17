package so.prelude.android.sdk.verification

/**
 * Server-driven quirks parsed from `X-SDK-Quirk-*` response headers.
 */
internal data class ServerQuirks(
    val rules: List<Rule> = emptyList(),
) {
    data class Rule(
        val hostPattern: String,
        val headerOverrides: Map<String, String> = emptyMap(),
        val maxTLSVersion: TlsVersion? = null,
    )

    val isEmpty: Boolean get() = rules.isEmpty()

    companion object {
        val EMPTY = ServerQuirks()

        /**
         * Parse `X-SDK-Quirk-*` headers from a response header map.
         *
         * @param headers Response headers as returned by OkHttp (key -> list of values).
         */
        fun fromHeaders(headers: Map<String, List<String>>): ServerQuirks {
            val rulesByHost = mutableMapOf<String, MutableRule>()

            for ((key, values) in headers) {
                val lowercasedKey = key.lowercase()

                if (lowercasedKey.startsWith("x-sdk-quirk-header-")) {
                    val headerName = key.drop("x-sdk-quirk-header-".length)
                    if (headerName.isEmpty()) continue

                    for (value in values) {
                        val parsed = parseDirective(value)
                        val host = parsed["host"] ?: continue
                        val headerValue = parsed["value"] ?: continue

                        val rule = rulesByHost.getOrPut(host) { MutableRule(host) }
                        rule.headerOverrides[headerName] = headerValue
                    }
                } else if (lowercasedKey == "x-sdk-quirk-tls") {
                    for (value in values) {
                        val parsed = parseDirective(value)
                        val host = parsed["host"] ?: continue
                        val versionStr = parsed["version"] ?: continue
                        val version = parseTlsVersion(versionStr) ?: continue

                        val rule = rulesByHost.getOrPut(host) { MutableRule(host) }
                        rule.maxTLSVersion = version
                    }
                }
            }

            return ServerQuirks(
                rules =
                    rulesByHost.values
                        .map { it.toRule() }
                        .sortedBy { hostSpecificity(it.hostPattern) },
            )
        }

        private fun parseDirective(directive: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            var remaining = directive

            while (remaining.isNotEmpty()) {
                remaining = remaining.trimStart(' ', ';')
                if (remaining.isEmpty()) break

                val eqIndex = remaining.indexOf('=')
                if (eqIndex < 0) break
                val key = remaining.substring(0, eqIndex).trim()
                remaining = remaining.substring(eqIndex + 1)

                val value: String
                if (remaining.startsWith("\"")) {
                    // Quoted value: find closing quote
                    remaining = remaining.substring(1)
                    val closeIndex = remaining.indexOf('"')
                    if (closeIndex >= 0) {
                        value = remaining.substring(0, closeIndex)
                        remaining = remaining.substring(closeIndex + 1)
                    } else {
                        // Unterminated quote: take the rest
                        value = remaining
                        remaining = ""
                    }
                } else {
                    // Unquoted value: read until semicolon
                    val semiIndex = remaining.indexOf(';')
                    if (semiIndex >= 0) {
                        value = remaining.substring(0, semiIndex).trim()
                        remaining = remaining.substring(semiIndex + 1)
                    } else {
                        value = remaining.trim()
                        remaining = ""
                    }
                }

                result[key] = value
            }

            return result
        }

        private fun hostSpecificity(pattern: String): Int {
            val lowered = pattern.lowercase()
            return when {
                lowered == "*" -> 0
                lowered.startsWith("*.") -> 1
                else -> 2
            }
        }

        private fun parseTlsVersion(version: String): TlsVersion? =
            when (version) {
                "1.2" -> TlsVersion.TLS_1_2
                "1.3" -> TlsVersion.TLS_1_3
                else -> null
            }
    }

    fun headersForHost(host: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (rule in rules) {
            if (hostMatches(rule.hostPattern, host)) {
                result.putAll(rule.headerOverrides)
            }
        }
        return result
    }

    /** Return TLS version override for a given host (null = use default). */
    fun tlsVersionForHost(host: String): TlsVersion? {
        var result: TlsVersion? = null
        for (rule in rules) {
            if (hostMatches(rule.hostPattern, host) && rule.maxTLSVersion != null) {
                result = rule.maxTLSVersion
            }
        }
        return result
    }

    private class MutableRule(
        val hostPattern: String,
        val headerOverrides: MutableMap<String, String> = mutableMapOf(),
        var maxTLSVersion: TlsVersion? = null,
    ) {
        fun toRule() =
            Rule(
                hostPattern = hostPattern,
                headerOverrides = headerOverrides.toMap(),
                maxTLSVersion = maxTLSVersion,
            )
    }
}

/** Host matching: `*` matches all, exact match, or wildcard suffix (`*.example.com`). */
internal fun hostMatches(
    pattern: String,
    host: String,
): Boolean {
    val lowerPattern = pattern.lowercase()
    val lowerHost = host.lowercase()

    if (lowerPattern == "*") return true

    if (lowerPattern == lowerHost) return true

    if (lowerPattern.startsWith("*.")) {
        val suffix = lowerPattern.drop(1)
        return lowerHost.endsWith(suffix)
    }

    return false
}

/** Supported TLS version overrides. */
internal enum class TlsVersion(
    val protocols: List<String>,
) {
    TLS_1_2(listOf("TLSv1.2")),
    TLS_1_3(listOf("TLSv1.2", "TLSv1.3")),
}
