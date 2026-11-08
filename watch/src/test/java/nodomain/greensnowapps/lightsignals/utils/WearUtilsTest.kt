package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color
import nodomain.greensnowapps.lightsignals.WearConstants
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WearUtilsTest {
    @Test
    fun historicalPaletteKeepsItsOriginalAnchors() {
        val anchors = listOf(
            Color.Black, Color.Blue, Color.Green, Color.Cyan,
            Color.Red, Color.Magenta, Color.Yellow, Color.White,
            Color(0xFFFF8000), Color(0xFF8F00FF)
        )
        anchors.forEachIndexed { index, expected ->
            assertEquals("anchor $index", expected, WearUtils.computeSpecialColor(index * 255))
        }
        assertEquals(
            Color.Yellow,
            WearUtils.computeSpecialColor(WearConstants.DEFAULT_COLOR_PROGRESS.toInt())
        )
    }

    @Test
    fun adjacentNewSliderValuesAreContinuous() {
        val firstStoredProgress = SignalColorPalette.MAX_PROGRESS - SignalColorPalette.MAX_SLIDER_PROGRESS
        var previous = WearUtils.computeSpecialColor(firstStoredProgress)
        for (progress in (firstStoredProgress + 1)..SignalColorPalette.MAX_PROGRESS) {
            val color = WearUtils.computeSpecialColor(progress)
            assertTrue("unexpected black at $progress", color != Color.Black)
            assertTrue(abs(color.red - previous.red) <= 1f / 255f + 0.00001f)
            assertTrue(abs(color.green - previous.green) <= 1f / 255f + 0.00001f)
            assertTrue(abs(color.blue - previous.blue) <= 1f / 255f + 0.00001f)
            assertEquals(1f, color.alpha, 0f)
            previous = color
        }
    }

    @Test
    fun invalidStoredProgressClampsToTheSliderEndpoints() {
        assertEquals(Color.Black, WearUtils.computeSpecialColor(Int.MIN_VALUE))
        assertEquals(Color.White, WearUtils.computeSpecialColor(Int.MAX_VALUE))
    }

    @Test
    fun theNewSliderUsesExactOrangeVioletAndWhite() {
        for ((slider, expected) in listOf(
            510f to Color(0xFFFF8000),
            1785f to Color(0xFF8F00FF),
            2295f to Color.White
        )) {
            val stored = SignalColorPalette.fromSliderProgress(slider)
            assertEquals(expected, WearUtils.computeSpecialColor(stored.toInt()))
        }
    }
}
