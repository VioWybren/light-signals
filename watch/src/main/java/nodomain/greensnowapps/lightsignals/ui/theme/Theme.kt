package nodomain.greensnowapps.lightsignals.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Applies the app's Material colours and typography to the composable content slot.
 *
 * @param content Composable content slot rendered with this theme's colours and typography.
 */
@Composable
fun LightSignalNewTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}
