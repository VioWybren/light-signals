package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import nodomain.greensnowapps.lightsignals.PhoneMainActivity
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

/**
 * Displays the phone flashlight toggle from checkedFlash.
 *
 * Enabling delegates to the Activity's notification-permission flow; disabling delegates to the
 * ViewModel.
 *
 * @param toggleBtnWidth Width of the toggle button.
 * @param toggleBtnHeight Height of the toggle button.
 * @param checkedFlash Whether phone torch output is selected.
 * @param viewModel ViewModel supplying observable signal state and handling control events.
 * @param activity Host Activity that enables the torch and coordinates the optional notification
 *     request.
 * @param iconSize Size of the icon inside the control.
 */
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
        icon = if (!checkedFlash) ImageVector.vectorResource(R.drawable.ic_filled_flashlight_on_24) else ImageVector.vectorResource(R.drawable.ic_filled_flashlight_off_24),
        contentDescriptionRes = R.string.flashlight,
        modifier = Modifier
            .size(iconSize)
            .rotate(90f)
    )
}
