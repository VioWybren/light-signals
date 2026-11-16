package nodomain.greensnowapps.lightsignals.core.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Verifies shared palette interpolation, saved-colour compatibility and slider bounds.
 */
class SignalColorPaletteTest {
    /**
     * Verifies that every previously saved position renders exactly as before.
     */
    @Test
    fun everyPreviouslySavedPositionRendersExactlyAsBefore() {
        for (progress in 0..2295) {
            assertEquals(
                "Historical RGB at $progress",
                historicalColorAt(progress),
                SignalColorPalette.argbAt(progress)
            )
        }
        for (progress in 0..1785) {
            assertEquals(
                "Legacy wire position at $progress",
                progress.toFloat(),
                SignalColorPalette.legacyProgress(progress.toFloat()),
                0f
            )
        }
        assertEquals(621.5f, SignalColorPalette.legacyProgress(621.5f), 0f)
        assertEquals(1530f, SignalColorPalette.DEFAULT_PROGRESS, 0f)
    }

    /**
     * Verifies that the visible track follows the color sequence and ends at white.
     */
    @Test
    fun theVisibleTrackFollowsTheColorSequenceAndEndsAtWhite() {
        val expected = listOf(
            0xFF000000.toInt(),
            0xFFFF0000.toInt(),
            0xFFFF8000.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(),
            0xFF00FFFF.toInt(),
            0xFF0000FF.toInt(),
            0xFF8F00FF.toInt(),
            0xFFFF00FF.toInt(),
            0xFFFFFFFF.toInt()
        )
        assertEquals(2295, SignalColorPalette.MAX_SLIDER_PROGRESS)
        assertEquals(4591, SignalColorPalette.MAX_PROGRESS)
        expected.forEachIndexed { index, argb ->
            val slider = (index * 255).toFloat()
            val stored = SignalColorPalette.fromSliderProgress(slider)
            assertEquals("Encoded anchor $index", 2296f + slider, stored, 0f)
            assertEquals("Rendered anchor $index", argb, SignalColorPalette.argbAt(stored.toInt()))
        }
    }

    /**
     * Verifies that the new track remains opaque and continuous within its own range.
     */
    @Test
    fun theNewTrackRemainsOpaqueAndContinuousWithinItsOwnRange() {
        for (progress in 2297..4591) {
            val previous = SignalColorPalette.argbAt(progress - 1)
            val current = SignalColorPalette.argbAt(progress)
            assertEquals("Alpha at $progress", 255, current ushr 24)
            for (shift in listOf(16, 8, 0)) {
                val before = (previous ushr shift) and 0xff
                val after = (current ushr shift) and 0xff
                assertTrue("Channel jumped at $progress", abs(after - before) <= 1)
            }
        }
    }

    /**
     * Verifies that a saved color maps to the corresponding position on the new slider.
     */
    @Test
    fun aSavedColorMapsToTheCorrespondingPositionOnTheNewSlider() {
        for ((stored, slider) in listOf(
            0f to 0f,
            1530f to 765f,
            1785f to 2295f,
            2040f to 510f,
            2295f to 1785f,
            621f to 1131f
        )) {
            assertEquals("Historical color at $stored", slider, SignalColorPalette.toSliderProgress(stored), 0.001f)
        }
        assertEquals(0f, SignalColorPalette.toSliderProgress(2296f), 0f)
        assertEquals(512f, SignalColorPalette.toSliderProgress(2808f), 0f)
        assertEquals(1390.5f, SignalColorPalette.toSliderProgress(3686.5f), 0f)
        assertEquals(2295f, SignalColorPalette.toSliderProgress(4591f), 0f)
    }

    /**
     * Verifies that touches near orange and violet snap without quantizing the rest of the
     * track.
     */
    @Test
    fun touchesNearOrangeAndVioletSnapWithoutQuantizingTheRestOfTheTrack() {
        assertEquals(2806f, SignalColorPalette.fromSliderProgress(500f), 0f)
        assertEquals(2806f, SignalColorPalette.fromSliderProgress(520f), 0f)
        assertEquals(4081f, SignalColorPalette.fromSliderProgress(1775f), 0f)
        assertEquals(4081f, SignalColorPalette.fromSliderProgress(1795f), 0f)
        assertEquals(2917.5f, SignalColorPalette.fromSliderProgress(621.5f), 0f)
        assertEquals(2296f, SignalColorPalette.fromSliderProgress(-1f), 0f)
        assertEquals(4591f, SignalColorPalette.fromSliderProgress(9999f), 0f)
    }

    /**
     * Verifies that invalid stored positions clamp safely including the gap between encodings.
     */
    @Test
    fun invalidStoredPositionsClampSafelyIncludingTheGapBetweenEncodings() {
        assertEquals(0f, SignalColorPalette.clampProgress(-1f), 0f)
        assertEquals(4591f, SignalColorPalette.clampProgress(9000f), 0f)
        assertEquals(2295f, SignalColorPalette.clampProgress(2295.5f), 0f)
        for (invalid in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)) {
            assertEquals(1530f, SignalColorPalette.clampProgress(invalid), 0f)
        }
        assertEquals(0xFF000000.toInt(), SignalColorPalette.argbAt(Int.MIN_VALUE))
        assertEquals(0xFFFFFFFF.toInt(), SignalColorPalette.argbAt(Int.MAX_VALUE))
    }

    /**
     * Verifies that both extensions keep a usable fallback for legacy companions.
     */
    @Test
    fun bothExtensionsKeepAUsableFallbackForLegacyCompanions() {
        assertEquals(1785f, SignalColorPalette.legacyProgress(1785f), 0f)
        assertEquals(1467f, SignalColorPalette.legacyProgress(2040f), 0f)
        assertEquals(1275f, SignalColorPalette.legacyProgress(2295f), 0f)
        assertEquals(1785f, SignalColorPalette.legacyProgress(4591f), 0f)
        for (progress in 1786..4591) {
            assertTrue("Legacy fallback at $progress", SignalColorPalette.legacyProgress(progress.toFloat()) in 0f..1785f)
        }
    }

    /**
     * Verifies that decoding preserves both extension versions and rejects unknown
     * coordinates.
     */
    @Test
    fun decodingPreservesBothExtensionVersionsAndRejectsUnknownCoordinates() {
        assertEquals(2040f, SignalColorPalette.decodeProgress(1467f, 2040f), 0f)
        assertEquals(2295f, SignalColorPalette.decodeProgress(1275f, 2295f), 0f)
        assertEquals(2296f, SignalColorPalette.decodeProgress(0f, 2296f), 0f)
        assertEquals(2806f, SignalColorPalette.decodeProgress(1467f, 2806f), 0f)
        assertEquals(4591f, SignalColorPalette.decodeProgress(1785f, 4591f), 0f)
        val invalid = listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 1785f, 2295.5f, 4592f)
        for (value in invalid) {
            assertEquals(621.5f, SignalColorPalette.decodeProgress(621.5f, value), 0f)
        }
        assertEquals(1785f, SignalColorPalette.decodeProgress(2295f, Float.NaN), 0f)
        assertEquals(1530f, SignalColorPalette.decodeProgress(Float.NaN, Float.NaN), 0f)
    }

    /**
     * Calculates the independent pre-reordering RGB reference used by compatibility tests.
     *
     * Keep this reference separate from production palette helpers so a regression cannot change
     * both the implementation and its expected result.
     *
     * @param progress Previously supported saved integer palette position.
     * @return The historical opaque ARGB colour.
     */
    // Snapshot of the RGB mapping used before the slider and stored coordinates were separated.
    private fun historicalColorAt(progress: Int): Int {
        val segment = minOf(progress / 255, 8)
        val offset = progress - segment * 255
        val (red, green, blue) = when (segment) {
            0 -> Triple(0, 0, offset)
            1 -> Triple(0, offset, 255 - offset)
            2 -> Triple(0, 255, offset)
            3 -> Triple(offset, 255 - offset, 255 - offset)
            4 -> Triple(255, 0, offset)
            5 -> Triple(255, offset, 255 - offset)
            6 -> Triple(255, 255, offset)
            7 -> Triple(255, (255 * (255 - offset) + 128 * offset) / 255, 255 - offset)
            else -> Triple((255 * (255 - offset) + 143 * offset) / 255, 128 * (255 - offset) / 255, offset)
        }
        return (0xff shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
