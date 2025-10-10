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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val MAX_PROGRESS = 255 * 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneColorPickerSlider(
    progress: Float,
    onProgressChanged: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientColors = listOf(
        Color.Black,
        Color.Blue,
        Color.Green,
        Color.Cyan,
        Color.Red,
        Color.Magenta,
        Color.Yellow,
        Color.White
    )

    Slider(
        value = progress,
        onValueChange = onProgressChanged,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = 0f..MAX_PROGRESS.toFloat(),
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
                        brush = Brush.horizontalGradient(gradientColors),
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

