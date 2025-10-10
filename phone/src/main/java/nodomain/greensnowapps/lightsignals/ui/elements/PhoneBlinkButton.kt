package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

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
        icon = if (checkedBtn) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        modifier = Modifier.size(iconSize)
    )
}

