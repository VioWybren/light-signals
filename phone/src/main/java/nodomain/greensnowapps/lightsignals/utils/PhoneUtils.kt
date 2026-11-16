package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

/**
 * Adapts the shared signal palette to Compose colours for the phone UI.
 */
object PhoneUtils {
    /**
     * Converts an encoded shared palette position into the Compose colour used by the UI.
     *
     * @param progress Encoded saved palette position; convert visible slider coordinates before
     *     calling.
     * @return The interpolated Compose colour.
     */
    fun computeSpecialColor(progress: Int): Color = Color(SignalColorPalette.argbAt(progress))
}
