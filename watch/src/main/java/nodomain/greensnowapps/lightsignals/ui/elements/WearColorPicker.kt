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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearColorPicker(
    progress: Float,
    onProgressChanged: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorLabel = stringResource(R.string.control_custom_light)

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
            .semantics { contentDescription = colorLabel }
            .fillMaxWidth()
            .padding(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 0.dp),
        thumb = {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .height(20.dp)
                    .width(10.dp)
                    .background(Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
            )
        },
        track = {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = gradientBrush,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )
        }
    )
}

