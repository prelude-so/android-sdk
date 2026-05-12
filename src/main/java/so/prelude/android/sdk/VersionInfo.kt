package so.prelude.android.sdk

/**
 * VersionInfo exposes SDK release metadata at runtime.
 *
 * The string is wired through `BuildConfig.SDK_VERSION`, which `build.gradle.kts`
 * sets from the same `sdkVersion` constant it uses for the Maven coordinates.
 * Bumping the version in `build.gradle.kts` updates this automatically.
 *
 * [major], [minor] and [patch] are derived from [versionString] for code that
 * needs to gate behaviour on the SDK version (e.g. `if (VersionInfo.major > 0
 * || (VersionInfo.major == 0 && VersionInfo.minor >= 5)) { … }`).
 */
object VersionInfo {
    /** Full version string, e.g. "0.5.1". */
    val versionString: String = BuildConfig.SDK_VERSION

    private val parts = versionString.split('.', limit = 4)

    /** Major version component (0 in "0.5.1"). */
    val major: Int = parts.getOrNull(0)?.toIntOrNull() ?: 0

    /** Minor version component (5 in "0.5.1"). */
    val minor: Int = parts.getOrNull(1)?.toIntOrNull() ?: 0

    /**
     * Patch version component (1 in "0.5.1").
     *
     * Any non-numeric suffix (e.g. "-SNAPSHOT", "-rc1") is dropped so a
     * pre-release build still parses to a sensible integer.
     */
    val patch: Int = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
}
