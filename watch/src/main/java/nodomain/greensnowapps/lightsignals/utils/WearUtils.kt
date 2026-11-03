package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color

object WearUtils {
    const val MAX_COLOR_PROGRESS = 255 * 7

    fun computeSpecialColor(progress: Int): Color {
        val p = progress.coerceIn(0, MAX_COLOR_PROGRESS)
        val segment = minOf(p / 255, 6)
        val t = p - segment * 255
        return when (segment) {
            0 -> Color(0, 0, t)
            1 -> Color(0, t, 255 - t)
            2 -> Color(0, 255, t)
            3 -> Color(t, 255 - t, 255 - t)
            4 -> Color(255, 0, t)
            5 -> Color(255, t, 255 - t)
            else -> Color(255, 255, t)
        }
    }
}
