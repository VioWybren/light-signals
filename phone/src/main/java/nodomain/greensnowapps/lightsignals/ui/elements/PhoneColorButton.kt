package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nodomain.greensnowapps.lightsignals.EMPTY_STRING
import nodomain.greensnowapps.lightsignals.R

/**
 * Displays a circular phone colour control, including selection, Blink/SOS phases and torch
 * masking.
 *
 * Hoists colour selection through onClick; the caller owns all signal state.
 *
 * @param sosOn Whether SOS mode is active.
 * @param flashOn Whether phone torch output is selected.
 * @param color Screen colour rendered by this control during a lit phase.
 * @param isActive Whether this colour control is selected.
 * @param blinkOn Whether Blink mode is active.
 * @param blinkActive Current lit phase used to render timed screen signals.
 * @param labelRes String resource describing the colour control for accessibility.
 * @param onClick Hoisted callback invoked when the user selects the control.
 * @param size Diameter of the circular colour control.
 */
@Composable
fun PhoneColorButton(
    sosOn: Boolean,
    flashOn: Boolean,
    color: Color,
    isActive: Boolean,
    blinkOn: Boolean,
    blinkActive: Boolean,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    size: Dp = 130.dp
) {
    val label = stringResource(labelRes)

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
            .semantics {
                contentDescription = label
                selected = isActive
            }
    ) {
        Text(
            text = (if (sosOn && isActive) stringResource(R.string.sos_label) else EMPTY_STRING),
            fontSize = 20.sp,
            modifier = Modifier
                .align(Alignment.Center),
            textAlign = TextAlign.Center
        )
    }
}

