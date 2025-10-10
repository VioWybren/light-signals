package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color

object PhoneUtils {

    private const val FULL = 255

    fun computeSpecialColor(progress: Int): Color {
        var r = 0
        var g = 0
        var b = 0
        when {
            progress < 256 -> {
                b = progress
            }
            progress < 256 * 2 -> {
                g = progress % 256
                b = 256 - progress % 256
            }
            progress < 256 * 3 -> {
                g = FULL
                b = progress % 256
            }
            progress < 256 * 4 -> {
                r = progress % 256
                g = 256 - progress % 256
                b = 256 - progress % 256
            }
            progress < 256 * 5 -> {
                r = FULL
                g = 0
                b = progress % 256
            }
            progress < 256 * 6 -> {
                r = FULL
                g = progress % 256
                b = 256 - progress % 256
            }
            progress < 256 * 7 -> {
                r = FULL
                g = FULL
                b = progress % 256
            }
            else -> {
                r = 0
                g = 0
                b = 0
            }
        }
        return Color(r, g, b, FULL)
    }
}