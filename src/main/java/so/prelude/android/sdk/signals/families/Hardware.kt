package so.prelude.android.sdk.signals.families

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import so.prelude.android.sdk.DisplayResolution
import so.prelude.android.sdk.Hardware

internal fun Hardware.Companion.collect(context: Context): Hardware {
    // DISPLAY_SERVICE is non-visual and safe from any context, unlike WINDOW_SERVICE
    // which trips StrictMode#detectIncorrectContextUse on Application context (API 30+).
    val display: Display? =
        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)

    val manufacturer: String? by lazy { android.os.Build.MANUFACTURER }
    val model: String? by lazy { android.os.Build.MODEL }
    val architecture: String? by lazy { System.getProperty("os.arch") }
    val cpuCount: Int? by lazy { Runtime.getRuntime().availableProcessors() }
    val cpuFrequency: Int? by lazy { null }
    val memorySize: Long? by lazy { Runtime.getRuntime().totalMemory() }

    val displayMetrics: DisplayMetrics? by lazy {
        display?.let { DisplayMetrics().also(it::getMetrics) }
    }
    val displayPhysicalMetrics: DisplayMetrics? by lazy {
        display?.let { DisplayMetrics().also(it::getRealMetrics) }
    }

    return Hardware(
        manufacturer,
        model,
        architecture,
        cpuCount,
        cpuFrequency,
        memorySize,
        displayMetrics?.let { DisplayResolution(it.widthPixels, it.heightPixels) },
        displayMetrics?.density,
        displayPhysicalMetrics?.let { DisplayResolution(it.widthPixels, it.heightPixels) },
        displayPhysicalMetrics?.density,
    )
}
