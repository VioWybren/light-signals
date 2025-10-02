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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nodomain.greensnowapps.lightsignals.WearConstants

// A composable for each round color button with a blinking effect if active.
@Composable
fun WearColorButton(
    sosOn: Boolean,
    flashOn: Boolean,
    color: Color,
    isActive: Boolean,
    blinkOn: Boolean,
    blinkActive: Boolean, // color is on
    onClick: () -> Unit
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
            .size(30.dp)
            .clip(CircleShape)
            .background(backGround)
            .border(2.dp, if (isActive) Color.White else color, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = (if (sosOn && isActive) WearConstants.SOS_TEXT else WearConstants.EMPTY_STRING),
            fontSize = 8.sp,
            modifier = Modifier
                .align(Alignment.Center),
            textAlign = TextAlign.Center
        )
    }
}

