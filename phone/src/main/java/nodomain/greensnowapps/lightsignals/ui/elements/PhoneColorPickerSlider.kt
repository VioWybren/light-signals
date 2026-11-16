package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

/**
 * Displays the ordered shared colour track and its current encoded selection.
 *
 * Hoists encoded drag values through onProgressChanged and completion through
 * onValueChangeFinished.
 *
 * @param progress Encoded shared palette position shown by the custom-colour slider.
 * @param onProgressChanged Hoisted callback receiving an encoded shared palette position while
 *     dragging.
 * @param onValueChangeFinished Hoisted callback invoked when the user finishes adjusting the
 *     slider.
 * @param modifier Modifier applied to the custom thumb and track elements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneColorPickerSlider(
    progress: Float,
    onProgressChanged: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val gradientBrush = remember(isRtl) {
        val colors = SignalColorPalette.argbStops.map { Color(it) }
        Brush.horizontalGradient(if (isRtl) colors.asReversed() else colors)
    }

    val sliderProgress = remember(progress) { SignalColorPalette.toSliderProgress(progress) }

    Slider(
        value = sliderProgress,
        onValueChange = { onProgressChanged(SignalColorPalette.fromSliderProgress(it)) },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = 0f..SignalColorPalette.MAX_SLIDER_PROGRESS.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp),
        colors = SliderDefaults.colors(thumbColor = Color.Red),
        thumb = {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .height(40.dp)
                    .width(15.dp)
                    .background(Color.Transparent)
                    .border(
                        width = 4.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
            )
        },
        track = {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(15.dp)
                    .background(
                        brush = gradientBrush,
                        shape = RoundedCornerShape(5.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(5.dp)
                    )
            )
        }
    )
}

