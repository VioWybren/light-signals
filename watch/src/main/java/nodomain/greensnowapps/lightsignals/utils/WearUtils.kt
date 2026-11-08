package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

object WearUtils {
    const val MAX_COLOR_PROGRESS = SignalColorPalette.MAX_PROGRESS

    fun computeSpecialColor(progress: Int): Color = Color(SignalColorPalette.argbAt(progress))
}
