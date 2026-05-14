package so.prelude.android.sdk.request

import so.prelude.android.sdk.SystemInfo
import so.prelude.android.sdk.VersionInfo
import so.prelude.android.sdk.coreVersion

internal val userAgent = "Prelude/${VersionInfo.versionString} Core/${coreVersion()} (${SystemInfo.userAgentString})"

internal val commonHeaders =
    mapOf(
        "Connection" to "close",
        "User-Agent" to userAgent,
    )
