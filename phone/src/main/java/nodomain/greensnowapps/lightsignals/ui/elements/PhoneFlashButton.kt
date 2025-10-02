package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import nodomain.greensnowapps.lightsignals.PhoneMainActivity
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

//@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        modifier = Modifier
            .size(iconSize)
            .rotate(90f)
    )
}


//ToggleButton(
//checked = checkedFlash,
//onCheckedChange = {
//    Log.d(TAG, "  checkedFlash_0 = $checkedFlash")
//    if (!checkedFlash) (activity as PhoneMainActivity).tryEnableFlash()
//    else viewModel.switchFlash(false)
//},
//modifier = Modifier
//.width(toggleBtnWidth)
//.height(toggleBtnHeight),
//contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
//colors = ToggleButtonDefaults.toggleButtonColors(
//containerColor = Color(COLOR_UNCHECKED_BUTTON),
//contentColor = Color.White,
//checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
//checkedContentColor = Color.White
//),
//shapes = ToggleButtonDefaults.shapes(
//shape = CircleShape,
//pressedShape = CircleShape,
//checkedShape = CircleShape
//),
//border = BorderStroke(width = 1.dp, color = Color.White)
//) {
//    Icon(
//        if (!checkedFlash) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
//        contentDescription = "Localized description",
//        modifier = Modifier
//            .size(ButtonDefaults.iconSizeFor(size))
//            .rotate(90f)
//    )
//}