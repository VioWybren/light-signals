package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nodomain.greensnowapps.lightsignals.EMPTY_STRING
import nodomain.greensnowapps.lightsignals.SOS_TEXT

@Composable
fun PhoneColorButton(
    sosOn: Boolean,
    flashOn: Boolean,
    color: Color,
    isActive: Boolean,
    blinkOn: Boolean,
    blinkActive: Boolean,
    onClick: () -> Unit,
    size: Dp = 130.dp
) {

    val backGround =
        if (isActive) {
            if (flashOn) {
                Color.Black
            } else {
                if (blinkOn) {
                    if (blinkActive) color else Color.Black
                } else {
                    if (sosOn) {
                        if (blinkActive) color else Color.Black
                    } else {
                        color
                    }
                }
            }
        } else {
            Color.Transparent
        }

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(backGround)
            .border(4.dp, if (isActive) Color.White else color, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = (if (sosOn && isActive) SOS_TEXT else EMPTY_STRING),
            fontSize = 20.sp,
            modifier = Modifier
                .align(Alignment.Center),
            textAlign = TextAlign.Center
        )
    }
}

