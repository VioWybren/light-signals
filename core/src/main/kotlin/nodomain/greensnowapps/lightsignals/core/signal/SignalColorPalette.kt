package nodomain.greensnowapps.lightsignals.core.signal

import kotlin.math.abs
import kotlin.math.roundToInt

object SignalColorPalette {
    private const val STEP = 255
    const val DEFAULT_PROGRESS = 6f * STEP
    const val LEGACY_MAX_PROGRESS = 7 * STEP
    private const val PREVIOUS_ORANGE_PROGRESS = 8 * STEP
    private const val PREVIOUS_MAX_PROGRESS = 9 * STEP

    // Distinguish the reordered slider from positions already saved by either earlier track.
    private const val ORDERED_OFFSET = PREVIOUS_MAX_PROGRESS + 1
    const val MAX_SLIDER_PROGRESS = 9 * STEP
    const val MAX_PROGRESS = ORDERED_OFFSET + MAX_SLIDER_PROGRESS

    private const val ORANGE_SLIDER_PROGRESS = 2 * STEP
    private const val VIOLET_SLIDER_PROGRESS = 7 * STEP
    private const val SNAP_RADIUS = 20f

    private const val LEGACY_ORANGE_PROGRESS = 1467f
    private const val LEGACY_VIOLET_PROGRESS = 1275f

    private const val BLACK = 0xFF000000.toInt()
    private const val RED = 0xFFFF0000.toInt()
    private const val ORANGE = 0xFFFF8000.toInt()
    private const val YELLOW = 0xFFFFFF00.toInt()
    private const val GREEN = 0xFF00FF00.toInt()
    private const val CYAN = 0xFF00FFFF.toInt()
    private const val BLUE = 0xFF0000FF.toInt()
    private const val VIOLET = 0xFF8F00FF.toInt()
    private const val MAGENTA = 0xFFFF00FF.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    private val legacyStops = listOf(BLACK, BLUE, GREEN, CYAN, RED, MAGENTA, YELLOW, WHITE)
    private val previousStops = legacyStops + listOf(ORANGE, VIOLET)

    val argbStops: List<Int> = listOf(BLACK, RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, VIOLET, MAGENTA, WHITE)

    fun argbAt(progress: Int): Int {
        val bounded = progress.coerceIn(0, MAX_PROGRESS)
        return if (bounded >= ORDERED_OFFSET) {
            colorAt(argbStops, bounded - ORDERED_OFFSET)
        } else {
            colorAt(previousStops, bounded)
        }
    }

    fun clampProgress(progress: Float): Float = when {
        !progress.isFinite() -> DEFAULT_PROGRESS
        progress < ORDERED_OFFSET -> progress.coerceIn(0f, PREVIOUS_MAX_PROGRESS.toFloat())
        else -> progress.coerceAtMost(MAX_PROGRESS.toFloat())
    }

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

    fun decodeProgress(legacy: Float, extended: Float): Float = when {
        extended > LEGACY_MAX_PROGRESS && extended <= PREVIOUS_MAX_PROGRESS -> extended
        extended in ORDERED_OFFSET.toFloat()..MAX_PROGRESS.toFloat() -> extended
        else -> clampProgress(legacy).coerceAtMost(LEGACY_MAX_PROGRESS.toFloat())
    }

    private fun colorAt(stops: List<Int>, progress: Int): Int {
        val segment = (progress / STEP).coerceAtMost(stops.lastIndex - 1)
        return interpolate(stops[segment], stops[segment + 1], progress - segment * STEP)
    }

    private fun interpolate(start: Int, end: Int, offset: Int): Int {
        fun channelAt(shift: Int): Int =
            (channel(start, shift) * (STEP - offset) + channel(end, shift) * offset) / STEP

        return (0xFF shl 24) or (channelAt(16) shl 16) or (channelAt(8) shl 8) or channelAt(0)
    }

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

    private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF
}
