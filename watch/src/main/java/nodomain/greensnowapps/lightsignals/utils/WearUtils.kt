package nodomain.greensnowapps.lightsignals.utils

import androidx.compose.ui.graphics.Color
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

/**
 * Adapts shared palette positions and bounds to the watch UI.
 */
object WearUtils {
    /**
     * Upper encoded shared-palette position supported by the watch.
     */
    const val MAX_COLOR_PROGRESS = SignalColorPalette.MAX_PROGRESS

    /**
     * Converts an encoded shared palette position into the Compose colour used by the UI.
     *
     * @param progress Encoded saved palette position; convert visible slider coordinates before
     *     calling.
     * @return The interpolated Compose colour.
     */
    fun computeSpecialColor(progress: Int): Color = Color(SignalColorPalette.argbAt(progress))
}
