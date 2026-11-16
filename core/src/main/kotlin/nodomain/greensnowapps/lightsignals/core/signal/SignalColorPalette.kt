package nodomain.greensnowapps.lightsignals.core.signal

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Maps encoded saved colours to an ordered black-to-white slider while preserving older RGB
 * values.
 *
 * Pure functions accept historical encodings and the offset-based current encoding. Slider
 * conversion keeps stored coordinates separate from visible thumb positions; moving the slider
 * adopts the current encoding and snaps orange and violet anchors.
 */
object SignalColorPalette {
    /**
     * Number of integer interpolation positions in one palette segment.
     */
    private const val STEP = 255
    /**
     * Default encoded colour selection, retained for existing saved settings.
     */
    const val DEFAULT_PROGRESS = 6f * STEP
    /**
     * Upper palette position understood by the original colour track.
     */
    const val LEGACY_MAX_PROGRESS = 7 * STEP
    /**
     * Orange anchor in the earlier extended palette encoding.
     */
    private const val PREVIOUS_ORANGE_PROGRESS = 8 * STEP
    /**
     * Last position in the earlier extension, whose final stop is violet.
     */
    private const val PREVIOUS_MAX_PROGRESS = 9 * STEP

    /**
     * Start of the reordered encoding, kept separate from every previously saved position.
     */
    // Distinguish the reordered slider from positions already saved by either earlier track.
    private const val ORDERED_OFFSET = PREVIOUS_MAX_PROGRESS + 1
    /**
     * Upper position on the visible ordered colour-slider track.
     */
    const val MAX_SLIDER_PROGRESS = 9 * STEP
    /**
     * Upper encoded palette position, including the ordered-track offset.
     */
    const val MAX_PROGRESS = ORDERED_OFFSET + MAX_SLIDER_PROGRESS

    /**
     * Orange anchor on the visible black-to-white slider track.
     */
    private const val ORANGE_SLIDER_PROGRESS = 2 * STEP
    /**
     * Violet anchor on the visible black-to-white slider track.
     */
    private const val VIOLET_SLIDER_PROGRESS = 7 * STEP
    /**
     * Slider distance within which orange and violet selections snap to their exact anchors.
     */
    private const val SNAP_RADIUS = 20f

    /**
     * Original-track approximation used when sending orange to an older receiver.
     */
    private const val LEGACY_ORANGE_PROGRESS = 1467f
    /**
     * Original-track approximation used when sending violet to an older receiver.
     */
    private const val LEGACY_VIOLET_PROGRESS = 1275f

    /**
     * Opaque ARGB black anchor retained by the shared palette.
     */
    private const val BLACK = 0xFF000000.toInt()
    /**
     * Opaque ARGB red anchor retained by the shared palette.
     */
    private const val RED = 0xFFFF0000.toInt()
    /**
     * Opaque ARGB orange anchor retained by the shared palette.
     */
    private const val ORANGE = 0xFFFF8000.toInt()
    /**
     * Opaque ARGB yellow anchor retained by the shared palette.
     */
    private const val YELLOW = 0xFFFFFF00.toInt()
    /**
     * Opaque ARGB green anchor retained by the shared palette.
     */
    private const val GREEN = 0xFF00FF00.toInt()
    /**
     * Opaque ARGB cyan anchor retained by the shared palette.
     */
    private const val CYAN = 0xFF00FFFF.toInt()
    /**
     * Opaque ARGB blue anchor retained by the shared palette.
     */
    private const val BLUE = 0xFF0000FF.toInt()
    /**
     * Opaque ARGB violet anchor retained by the shared palette.
     */
    private const val VIOLET = 0xFF8F00FF.toInt()
    /**
     * Opaque ARGB magenta anchor retained by the shared palette.
     */
    private const val MAGENTA = 0xFFFF00FF.toInt()
    /**
     * Opaque ARGB white anchor retained by the shared palette.
     */
    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * Original colour-stop order used by saved positions and legacy receivers.
     */
    private val legacyStops = listOf(BLACK, BLUE, GREEN, CYAN, RED, MAGENTA, YELLOW, WHITE)
    /**
     * Original track followed by the earlier orange and violet extension.
     */
    private val previousStops = legacyStops + listOf(ORANGE, VIOLET)

    /**
     * Ordered opaque palette stops from black through the hue sequence to white.
     */
    val argbStops: List<Int> = listOf(BLACK, RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, VIOLET, MAGENTA, WHITE)

    /**
     * Resolves a saved palette position to an opaque ARGB colour, including older palette
     * encodings.
     *
     * @param progress Encoded saved palette position, not the visible slider coordinate.
     * @return The interpolated opaque ARGB colour.
     */
    fun argbAt(progress: Int): Int {
        val bounded = progress.coerceIn(0, MAX_PROGRESS)
        return if (bounded >= ORDERED_OFFSET) {
            colorAt(argbStops, bounded - ORDERED_OFFSET)
        } else {
            colorAt(previousStops, bounded)
        }
    }

    /**
     * Bounds a palette position and replaces non-finite input with the shared default.
     *
     * @param progress Encoded saved palette position, not the visible slider coordinate.
     * @return A valid encoded palette position.
     */
    fun clampProgress(progress: Float): Float = when {
        !progress.isFinite() -> DEFAULT_PROGRESS
        progress < ORDERED_OFFSET -> progress.coerceIn(0f, PREVIOUS_MAX_PROGRESS.toFloat())
        else -> progress.coerceAtMost(MAX_PROGRESS.toFloat())
    }

    /**
     * Maps an encoded saved colour to the current ordered slider track.
     *
     * Older colours use the nearest track position without rewriting their saved RGB value.
     *
     * @param progress Encoded saved palette position, not the visible slider coordinate.
     * @return The visible slider position in 0..MAX_SLIDER_PROGRESS.
     */
    fun toSliderProgress(progress: Float): Float {
        val bounded = clampProgress(progress)
        return if (bounded >= ORDERED_OFFSET) {
            bounded - ORDERED_OFFSET
        } else {
            // The thumb points to the closest shade on the new track. The saved color
            // itself keeps its original RGB value until the slider is moved.
            closestProgress(argbAt(bounded.toInt()), argbStops)
        }
    }

    /**
     * Encodes an ordered slider position and snaps nearby orange and violet selections.
     *
     * @param progress Visible slider coordinate in 0..MAX_SLIDER_PROGRESS; non-finite input uses
     *     the saved default.
     * @return The encoded position used for persistence and synchronization.
     */
    fun fromSliderProgress(progress: Float): Float {
        if (!progress.isFinite()) return DEFAULT_PROGRESS
        val bounded = progress.coerceIn(0f, MAX_SLIDER_PROGRESS.toFloat())
        val snapped = when {
            abs(bounded - ORANGE_SLIDER_PROGRESS) <= SNAP_RADIUS -> ORANGE_SLIDER_PROGRESS.toFloat()
            abs(bounded - VIOLET_SLIDER_PROGRESS) <= SNAP_RADIUS -> VIOLET_SLIDER_PROGRESS.toFloat()
            else -> bounded
        }
        return ORDERED_OFFSET + snapped
    }

    /**
     * Approximates an extended palette colour on the legacy receiver's track.
     *
     * @param progress Encoded saved palette position, not the visible slider coordinate.
     * @return A palette position understood by the legacy protocol.
     */
    fun legacyProgress(progress: Float): Float {
        val bounded = clampProgress(progress)
        return when {
            bounded <= LEGACY_MAX_PROGRESS -> bounded
            bounded >= ORDERED_OFFSET -> closestProgress(argbAt(bounded.toInt()), legacyStops)
            bounded <= PREVIOUS_ORANGE_PROGRESS -> {
                val fraction = (bounded - LEGACY_MAX_PROGRESS) / STEP
                LEGACY_MAX_PROGRESS + (LEGACY_ORANGE_PROGRESS - LEGACY_MAX_PROGRESS) * fraction
            }
            else -> {
                val fraction = (bounded - PREVIOUS_ORANGE_PROGRESS) / STEP
                LEGACY_ORANGE_PROGRESS + (LEGACY_VIOLET_PROGRESS - LEGACY_ORANGE_PROGRESS) * fraction
            }
        }
    }

    /**
     * Selects a supported extended palette position or falls back to the bounded legacy
     * position.
     *
     * @param legacy Palette position understood by the legacy receiver.
     * @param extended Extended palette position sent by a newer peer.
     * @return The decoded shared palette position.
     */
    fun decodeProgress(legacy: Float, extended: Float): Float = when {
        extended > LEGACY_MAX_PROGRESS && extended <= PREVIOUS_MAX_PROGRESS -> extended
        extended in ORDERED_OFFSET.toFloat()..MAX_PROGRESS.toFloat() -> extended
        else -> clampProgress(legacy).coerceAtMost(LEGACY_MAX_PROGRESS.toFloat())
    }

    /**
     * Interpolates an opaque colour on a supplied palette track.
     *
     * The caller supplies at least two stops and a position within that track. The final
     * position resolves to the final stop without reading past the list.
     *
     * @param stops Ordered opaque ARGB anchors, separated by STEP positions.
     * @param progress Bounded integer position relative to this track's first stop.
     * @return The interpolated opaque ARGB value.
     */
    private fun colorAt(stops: List<Int>, progress: Int): Int {
        val segment = (progress / STEP).coerceAtMost(stops.lastIndex - 1)
        return interpolate(stops[segment], stops[segment + 1], progress - segment * STEP)
    }

    /**
     * Interpolates RGB channels with integer arithmetic and forces an opaque alpha channel.
     *
     * @param start ARGB colour at the beginning of the segment.
     * @param end ARGB colour at the end.
     * @param offset Segment position from zero through STEP.
     * @return The opaque interpolated colour.
     */
    private fun interpolate(start: Int, end: Int, offset: Int): Int {
        /**
         * Interpolates one channel using the enclosing segment's endpoints and offset.
         *
         * @param shift Channel bit shift: 16 for red, 8 for green or zero for blue.
         * @return The integer channel intensity from zero through 255.
         */
        fun channelAt(shift: Int): Int =
            (channel(start, shift) * (STEP - offset) + channel(end, shift) * offset) / STEP

        return (0xFF shl 24) or (channelAt(16) shl 16) or (channelAt(8) shl 8) or channelAt(0)
    }

    /**
     * Projects a colour onto each track segment and chooses the nearest RGB approximation.
     *
     * Uses squared RGB distance after integer interpolation; equal distances retain the earlier
     * candidate. The supplied track must have distinct adjacent anchors.
     *
     * @param color ARGB colour whose RGB channels should be approximated.
     * @param stops Ordered opaque ARGB anchors defining the candidate track.
     * @return The nearest segment-relative position on the supplied track.
     */
    private fun closestProgress(color: Int, stops: List<Int>): Float {
        var bestProgress = 0
        var bestDistance = Int.MAX_VALUE
        for (segment in 0 until stops.lastIndex) {
            val start = stops[segment]
            val end = stops[segment + 1]
            val red = channel(end, 16) - channel(start, 16)
            val green = channel(end, 8) - channel(start, 8)
            val blue = channel(end, 0) - channel(start, 0)
            val lengthSquared = red * red + green * green + blue * blue
            val dot = (channel(color, 16) - channel(start, 16)) * red +
                (channel(color, 8) - channel(start, 8)) * green +
                (channel(color, 0) - channel(start, 0)) * blue
            val offset = (dot * STEP.toFloat() / lengthSquared).roundToInt().coerceIn(0, STEP)
            val sample = interpolate(start, end, offset)
            val dr = channel(color, 16) - channel(sample, 16)
            val dg = channel(color, 8) - channel(sample, 8)
            val db = channel(color, 0) - channel(sample, 0)
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                bestProgress = segment * STEP + offset
            }
        }
        return bestProgress.toFloat()
    }

    /**
     * Extracts one unsigned eight-bit component from an ARGB integer.
     *
     * @param color Packed ARGB value.
     * @param shift Bit shift locating the component.
     * @return The component intensity from zero through 255.
     */
    private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF
}
