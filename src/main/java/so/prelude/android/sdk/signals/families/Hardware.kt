package so.prelude.android.sdk.signals.families

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import so.prelude.android.sdk.DisplayResolution
import so.prelude.android.sdk.Hardware

internal fun Hardware.Companion.collect(context: Context): Hardware {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val manufacturer: String? by lazy {
        android.os.Build.MANUFACTURER
    }

    val model: String? by lazy {
        android.os.Build.MODEL
    }

    val architecture: String? by lazy {
        System.getProperty("os.arch")
    }

    val cpuCount: Int? by lazy {
        Runtime.getRuntime().availableProcessors()
    }

    val cpuFrequency: Int? by lazy {
        null
    }

    val memorySize: Long? by lazy {
        Runtime.getRuntime().totalMemory()
    }

    val displayMetrics: DisplayMetrics by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        metrics
    }

    val displayResolution: DisplayResolution? by lazy {
        DisplayResolution(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    val displayScale: Float? by lazy {
        displayMetrics.density
    }

    val displayPhysicalMetrics: DisplayMetrics by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        metrics
    }

    val displayPhysicalResolution: DisplayResolution? by lazy {
        DisplayResolution(displayPhysicalMetrics.widthPixels, displayPhysicalMetrics.heightPixels)
    }

    val displayPhysicalScale: Float? by lazy {
        displayPhysicalMetrics.density
    }

    return Hardware(
        manufacturer,
        model,
        architecture,
        cpuCount,
        cpuFrequency,
        memorySize,
        displayResolution,
        displayScale,
        displayPhysicalResolution,
        displayPhysicalScale,
    )
}
