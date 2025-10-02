package lightsignalnew.example.user.lightsignalnew.lightsignalnew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun RadialSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    startAngle: Float,       // degrees where the arc begins (0 = 3 o’clock)
    sweepAngle: Float,       // how many degrees the slider spans
    strokeWidth: Dp = 12.dp, // thickness of the arc
    thumbRadius: Dp = 8.dp   // size of the draggable thumb
) {
    // Normalize value to 0–1
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .onRotaryScrollEvent { ev ->
                // Map rotary delta to value change
                val delta = ev.verticalScrollPixels / 100f *
                        (valueRange.endInclusive - valueRange.start)
                onValueChange((value + delta).coerceIn(valueRange.start, valueRange.endInclusive))
                true
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
//                    val center = this.size.center
                    val touch = change.position
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    // compute angle from center to touch
                    val angle = atan2(touch.y - center.y, touch.x - center.x) * 180f / PI.toFloat()
                    // convert from (-180..180) to (0..360)
                    val positiveAngle = (angle + 360) % 360
                    // check if within our arc span
                    val relative = ((positiveAngle - startAngle + 360) % 360)
                    if (relative <= sweepAngle) {
                        val newFrac = (relative / sweepAngle).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + newFrac *
                                (valueRange.endInclusive - valueRange.start))
                    }
                }
            }
            .size(200.dp) // default size; override when you call it
    ) {
        val radius = (size.minDimension - strokeWidth.toPx()) / 2f
//        val center = size.center
        val center = Offset(size.width / 2f, size.height / 2f)

        // Draw track (background arc)
        drawArc(
            color = Color.Gray,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )

        // Draw filled portion
        drawArc(
            color = Color.Cyan,
            startAngle = startAngle,
            sweepAngle = sweepAngle * fraction,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )

        // Draw thumb at the end
        val theta = Math.toRadians((startAngle + sweepAngle * fraction).toDouble())
        val thumbX = center.x + cos(theta) * radius
        val thumbY = center.y + sin(theta) * radius
        drawCircle(
            color = Color.White,
            radius = thumbRadius.toPx(),
            center = Offset(thumbX.toFloat(), thumbY.toFloat())
        )
    }
}
