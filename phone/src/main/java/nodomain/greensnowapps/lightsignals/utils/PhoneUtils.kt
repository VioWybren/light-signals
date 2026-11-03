package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color

object PhoneUtils {
    private const val FULL = 255

    fun computeSpecialColor(progress: Int): Color {
        val boundedProgress = progress.coerceIn(0, FULL * 7)
        val segment = minOf(boundedProgress / FULL, 6)
        val value = boundedProgress - segment * FULL
        return when (segment) {
            0 -> Color(0, 0, value)
            1 -> Color(0, value, FULL - value)
            2 -> Color(0, FULL, value)
            3 -> Color(value, FULL - value, FULL - value)
            4 -> Color(FULL, 0, value)
            5 -> Color(FULL, value, FULL - value)
            else -> Color(FULL, FULL, value)
        }
    }
}
