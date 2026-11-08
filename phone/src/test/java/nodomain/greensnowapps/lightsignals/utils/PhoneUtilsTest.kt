package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhoneUtilsTest {
    @Test
    fun historicalPaletteKeepsItsOriginalAnchors() {
        val stops = listOf(
            Color.Black, Color.Blue, Color.Green, Color.Cyan,
            Color.Red, Color.Magenta, Color.Yellow, Color.White,
            Color(0xFFFF8000), Color(0xFF8F00FF)
        )
        stops.forEachIndexed { index, expected ->
            assertEquals("Gradient stop $index", expected, PhoneUtils.computeSpecialColor(index * 255))
        }
    }

    @Test
    fun adjacentNewSliderValuesAreContinuous() {
        val firstStoredProgress = SignalColorPalette.MAX_PROGRESS - SignalColorPalette.MAX_SLIDER_PROGRESS
        for (progress in (firstStoredProgress + 1)..SignalColorPalette.MAX_PROGRESS) {
            val previous = PhoneUtils.computeSpecialColor(progress - 1)
            val current = PhoneUtils.computeSpecialColor(progress)
            val largestChange = maxOf(
                abs(current.red - previous.red),
                abs(current.green - previous.green),
                abs(current.blue - previous.blue)
            )
            assertTrue("Color jumped at $progress", largestChange <= 1f / 255f + 0.000001f)
            assertEquals(1f, current.alpha, 0f)
        }
    }

    @Test
    fun outOfRangeProgressClampsToEndpoints() {
        assertEquals(Color.Black, PhoneUtils.computeSpecialColor(Int.MIN_VALUE))
        assertEquals(Color.White, PhoneUtils.computeSpecialColor(Int.MAX_VALUE))
    }

    @Test
    fun theNewSliderUsesExactOrangeVioletAndWhite() {
        for ((slider, expected) in listOf(
            510f to Color(0xFFFF8000),
            1785f to Color(0xFF8F00FF),
            2295f to Color.White
        )) {
            val stored = SignalColorPalette.fromSliderProgress(slider)
            assertEquals(expected, PhoneUtils.computeSpecialColor(stored.toInt()))
        }
    }
}
