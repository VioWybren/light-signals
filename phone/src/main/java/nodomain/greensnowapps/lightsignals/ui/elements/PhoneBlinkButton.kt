package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

/**
 * Displays the phone Blink toggle and forwards taps to the main ViewModel using the current
 * selection.
 *
 * @param toggleBtnWidth Width of the toggle button.
 * @param toggleBtnHeight Height of the toggle button.
 * @param checkedBtn Whether the toggle currently appears selected.
 * @param sosOn Current SOS selection passed to the tap helper; repository events enforce mutual
 *     exclusion.
 * @param viewModel ViewModel supplying observable signal state and handling control events.
 * @param iconSize Size of the icon inside the control.
 */
@Composable
fun PhoneBlinkButton(
    toggleBtnWidth: Dp,
    toggleBtnHeight: Dp,
    checkedBtn: Boolean,
    sosOn: Boolean,
    viewModel: PhoneMainViewModel,
    iconSize: Dp
) {
    val blinkHandling: (Boolean, Boolean) -> Unit = { sosOn, checkedBtn ->
        if (!sosOn) {
            checkedBtn
        }
        if (checkedBtn) {
            viewModel.toggleControl(false)
        } else {
            viewModel.toggleControl(true)
        }
    }

    LSToggleButton(
        toggleBtnWidth,
        toggleBtnHeight,
        checkedBtn = checkedBtn,
        onCheckedChange = { blinkHandling(sosOn, checkedBtn) },
        icon = if (checkedBtn) ImageVector.vectorResource(R.drawable.ic_filled_pause_24) else ImageVector.vectorResource(R.drawable.ic_filled_play_arrow_24),
        contentDescriptionRes = R.string.blink_signal,
        modifier = Modifier.size(iconSize)
    )
}

