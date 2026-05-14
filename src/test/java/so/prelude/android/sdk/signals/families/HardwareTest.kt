package so.prelude.android.sdk.signals.families

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import so.prelude.android.sdk.Hardware

/**
 * Pins [Hardware.collect] to the StrictMode-safe DisplayManager code path.
 *
 * Regression context: 0.5.1 read display metrics via WINDOW_SERVICE from the
 * Application context, which fires StrictMode#IncorrectContextUseViolation
 * on API 30+ and can kill apps that run with `penaltyDeath()`. Any future
 * change that reintroduces WINDOW_SERVICE here will be caught by the
 * "never queries WINDOW_SERVICE" assertion below.
 */
class HardwareTest {
    @Test
    fun `populates display resolution from DisplayManager default display`() {
        val context = contextWith(display(width = 1080, height = 2340, dens = 2.75f, realW = 1080, realH = 2400))

        val hw = Hardware.collect(context)

        assertEquals(1080, hw.displayResolution?.width)
        assertEquals(2340, hw.displayResolution?.height)
        assertEquals(2.75f, hw.displayScale!!, 0.001f)
        assertEquals(1080, hw.displayPhysicalResolution?.width)
        assertEquals(2400, hw.displayPhysicalResolution?.height)
    }

    @Test
    fun `returns null resolutions when DisplayManager is unavailable`() {
        val context = mockk<Context>()
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns null

        val hw = Hardware.collect(context)

        assertNull(hw.displayResolution)
        assertNull(hw.displayPhysicalResolution)
        assertNull(hw.displayScale)
        assertNull(hw.displayPhysicalScale)
    }

    @Test
    fun `returns null resolutions when default display is missing`() {
        val context = mockk<Context>()
        val dm = mockk<DisplayManager>()
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns dm
        every { dm.getDisplay(Display.DEFAULT_DISPLAY) } returns null

        val hw = Hardware.collect(context)

        assertNull(hw.displayResolution)
        assertNull(hw.displayPhysicalResolution)
    }

    @Test
    fun `never queries the visual WINDOW_SERVICE`() {
        val context = contextWith(display(width = 1, height = 1, dens = 1f, realW = 1, realH = 1))

        Hardware.collect(context)

        verify(exactly = 0) { context.getSystemService(Context.WINDOW_SERVICE) }
    }

    /**
     * `android.os.Build.MANUFACTURER`/`MODEL` are stubbed to null in JVM unit
     * tests; only the runtime-derived fields can be asserted here.
     */
    @Test
    fun `populates runtime hardware fields from System and Runtime`() {
        val hw = Hardware.collect(contextWith(display(1, 1, 1f, 1, 1)))

        assertNotNull(hw.architecture)
        assertNotNull(hw.cpuCount)
        assertNotNull(hw.memorySize)
        assertNull(hw.cpuFrequency)
    }

    private fun contextWith(display: Display): Context {
        val context = mockk<Context>(relaxed = true)
        val dm = mockk<DisplayManager>()
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns dm
        every { dm.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        return context
    }

    private fun display(
        width: Int,
        height: Int,
        dens: Float,
        realW: Int,
        realH: Int,
    ): Display {
        val display = mockk<Display>()
        every { display.getMetrics(any()) } answers {
            firstArg<DisplayMetrics>().apply {
                widthPixels = width
                heightPixels = height
                density = dens
            }
            Unit
        }
        every { display.getRealMetrics(any()) } answers {
            firstArg<DisplayMetrics>().apply {
                widthPixels = realW
                heightPixels = realH
                density = dens
            }
            Unit
        }
        return display
    }
}
