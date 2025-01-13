package so.prelude.android.sdk

/**
 * System is a namespace for system-related information.
 */
object SystemInfo {
    /**
     * The system platform.
     */
    const val PLATFORM = "Android"

    /**
     * The system version.
     */
    val VERSION: String get() = android.os.Build.VERSION.RELEASE

    /**
     * The user agent string.
     */
    val userAgentString: String get() = "$PLATFORM $VERSION"
}
