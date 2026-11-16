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
 * Displays the phone SOS selection and forwards toggle events to the main ViewModel.
 *
 * @param toggleBtnWidth Width of the toggle button.
 * @param toggleBtnHeight Height of the toggle button.
 * @param checkedSOS Whether SOS mode is selected.
 * @param viewModel ViewModel supplying observable signal state and handling control events.
 * @param iconSize Size of the icon inside the control.
 */
@Composable
fun PhoneSosButton(
    toggleBtnWidth: Dp,
    toggleBtnHeight: Dp,
    checkedSOS: Boolean,
    viewModel: PhoneMainViewModel,
    iconSize: Dp
) {
    LSToggleButton(
        toggleBtnWidth,
        toggleBtnHeight,
        checkedBtn = checkedSOS,
        onCheckedChange = { viewModel.toggleSos(!checkedSOS) },
        icon = if (checkedSOS) ImageVector.vectorResource(R.drawable.ic_filled_sos_24) else ImageVector.vectorResource(R.drawable.ic_filled_sos_24),
        contentDescriptionRes = R.string.sos_signal,
        modifier = Modifier.size(iconSize)
    )
}
