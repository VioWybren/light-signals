package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sos
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

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
        icon = if (checkedSOS) Icons.Filled.Sos else Icons.Filled.Sos,
        modifier = Modifier.size(iconSize)
    )
}
