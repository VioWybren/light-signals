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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val MAX_PROGRESS = 255 * 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearColorPicker(
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
                        Brush.horizontalGradient(
                            gradientColors
                        ), RoundedCornerShape(10.dp)
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

