package so.prelude.android.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the VersionInfo contract:
 *   - `versionString` is whatever `build.gradle.kts` set via BuildConfig.SDK_VERSION.
 *   - `major` / `minor` / `patch` decompose that string into Ints so callers can
 *     gate behaviour on the SDK version (e.g. `if (VersionInfo.minor >= 5)`).
 *
 * The release path stays single-source: the only place to bump the version is
 * `val sdkVersion` in `build.gradle.kts`. These tests guard against a refactor
 * that silently drops the Int components again.
 */
class VersionInfoTest {
    @Test
    fun `versionString equals BuildConfig SDK_VERSION`() {
        assertEquals(BuildConfig.SDK_VERSION, VersionInfo.versionString)
    }

    @Test
    fun `major minor patch parse the release versionString`() {
        val parts = BuildConfig.SDK_VERSION.split('.')
        assertEquals(parts[0].toInt(), VersionInfo.major)
        assertEquals(parts[1].toInt(), VersionInfo.minor)
        assertEquals(parts[2].takeWhile { it.isDigit() }.toInt(), VersionInfo.patch)
    }
}
