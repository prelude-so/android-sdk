package so.prelude.android.sdk.signals.families

import android.content.Context
import so.prelude.android.sdk.Application
import so.prelude.android.sdk.ApplicationAndroidPlatform
import so.prelude.android.sdk.ApplicationPlatform
import so.prelude.android.sdk.ApplicationSdkInfo
import so.prelude.android.sdk.VersionInfo
import so.prelude.android.sdk.coreVersion

internal fun Application.Companion.collect(context: Context): Application {
    val packageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)

    val name: String? by lazy {
        packageManager.getApplicationLabel(context.applicationInfo).toString()
    }

    val version: String? by lazy {
        packageInfo.versionName
    }

    val packageName: String? by lazy {
        packageInfo.packageName
    }

    @Suppress("DEPRECATION")
    val versionCode: Int? by lazy {
        packageInfo.versionCode
    }

    return Application(
        name,
        version,
        ApplicationPlatform.Android(
            ApplicationAndroidPlatform(
                packageName,
                versionCode,
            ),
        ),
        ApplicationSdkInfo(
            sdkVersion = VersionInfo.versionString,
            coreVersion = coreVersion(),
        ),
    )
}
