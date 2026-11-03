package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import nodomain.greensnowapps.lightsignals.PhoneMainActivity
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

@Composable
fun PhoneFlashButton(
    toggleBtnWidth: Dp,
    toggleBtnHeight: Dp,
    checkedFlash: Boolean,
    viewModel: PhoneMainViewModel,
    activity: PhoneMainActivity,
    iconSize: Dp
) {
    LSToggleButton(
        toggleBtnWidth,
        toggleBtnHeight,
        checkedBtn = checkedFlash,
        onCheckedChange = { if (!checkedFlash) activity.tryEnableFlash() else viewModel.switchFlash(false) },
        icon = if (!checkedFlash) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
        contentDescriptionRes = R.string.flashlight,
        modifier = Modifier
            .size(iconSize)
            .rotate(90f)
    )
}
