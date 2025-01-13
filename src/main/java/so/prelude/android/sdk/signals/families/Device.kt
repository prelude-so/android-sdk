package so.prelude.android.sdk.signals.families

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.fonts.SystemFonts
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import so.prelude.android.sdk.BatteryState
import so.prelude.android.sdk.Device
import so.prelude.android.sdk.Platform
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

internal fun Device.Companion.collect(context: Context): Device {
    val uname = android.system.Os.uname()
    val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    val bootTime: Instant? by lazy {
        Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
    }

    val hostname: String? by lazy {
        uname.nodename
    }

    val kernelVersion: String? by lazy {
        uname.version
    }

    val osBuild: String? by lazy {
        null
    }

    val osRelease: String? by lazy {
        uname.release
    }

    val osType: String? by lazy {
        uname.sysname
    }

    val systemName: String? by lazy {
        "Android"
    }

    val systemVersion: String? by lazy {
        android.os.Build.VERSION.RELEASE
    }

    @SuppressLint("HardwareIds")
    val vendorID: String? by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    val name: String? by lazy {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }

    val localeCurrent: String? by lazy {
        context.resources.configuration.locales[0]
            .toString()
    }

    val localePreferred: List<String>? by lazy {
        val configurationLocales = context.resources.configuration.locales
        val locales = mutableListOf<Locale>()
        for (i in 0 until configurationLocales.size()) {
            locales.add(configurationLocales[i])
        }
        locales.map { it.toString() }
    }

    val timeZoneCurrent: String? by lazy {
        TimeZone.getDefault().id
    }

    val batteryLevel: Float? by lazy {
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        if (level in 0..scale) {
            level.toFloat() / scale.toFloat()
        } else {
            null
        }
    }

    val batteryState: BatteryState? by lazy {
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryState.UNPLUGGED
            BatteryManager.BATTERY_STATUS_FULL -> BatteryState.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryState.UNPLUGGED
            BatteryManager.BATTERY_STATUS_UNKNOWN -> BatteryState.UNKNOWN
            else -> null
        }
    }

    val fontsDigest: String? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val md = MessageDigest.getInstance("SHA-256")
            for (font in SystemFonts.getAvailableFonts()) {
                md.update(font.buffer)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } else {
            null
        }
    }

    val simulator: Boolean? by lazy {
        // See: https://stackoverflow.com/a/21505193/2443512
        (
            Build.MANUFACTURER == "Google" &&
                Build.BRAND == "google" &&
                (
                    (
                        Build.FINGERPRINT.startsWith("google/sdk_gphone_") &&
                            Build.FINGERPRINT.endsWith(":user/release-keys") &&
                            Build.PRODUCT.startsWith("sdk_gphone_") &&
                            Build.MODEL.startsWith("sdk_gphone_")
                    ) ||
                        (
                            Build.FINGERPRINT.startsWith("google/sdk_gphone64_") &&
                                (
                                    Build.FINGERPRINT.endsWith(":userdebug/dev-keys") ||
                                        Build.FINGERPRINT.endsWith(":user/release-keys")
                                ) &&
                                Build.PRODUCT.startsWith("sdk_gphone64_") &&
                                Build.MODEL.startsWith("sdk_gphone64_")
                        )
                )
        ) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||

            // Bluestacks
            (
                Build.BOARD.equals("QC_Reference_Phone") &&
                    !Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
            ) ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HOST.startsWith("Build") ||

            // MSI App Player
            (
                Build.BRAND.startsWith("generic") &&
                    Build.DEVICE.startsWith("generic")
            ) ||
            Build.PRODUCT.equals("google_sdk")
    }

    return Device(
        Platform.ANDROID,
        bootTime,
        hostname,
        kernelVersion,
        osBuild,
        osRelease,
        osType,
        systemName,
        systemVersion,
        vendorID,
        name,
        localeCurrent,
        localePreferred,
        timeZoneCurrent,
        batteryLevel,
        batteryState,
        fontsDigest,
        simulator,
    )
}
