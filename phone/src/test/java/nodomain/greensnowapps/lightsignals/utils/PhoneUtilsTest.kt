package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhoneUtilsTest {
    @Test
    fun gradientReachesEveryAdvertisedStop() {
        val stops = listOf(
            Color.Black, Color.Blue, Color.Green, Color.Cyan,
            Color.Red, Color.Magenta, Color.Yellow, Color.White
        )
        stops.forEachIndexed { index, expected ->
            assertEquals("Gradient stop $index", expected, PhoneUtils.computeSpecialColor(index * 255))
        }
    }

    @Test
    fun adjacentSliderValuesDoNotJumpAtSegmentBoundaries() {
        for (progress in 1..(255 * 7)) {
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
}
