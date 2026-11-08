package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

object PhoneUtils {
    fun computeSpecialColor(progress: Int): Color = Color(SignalColorPalette.argbAt(progress))
}
