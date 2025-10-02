package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.google.android.gms.common.images.Size
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

//@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
//        modifier = Modifier.size(ButtonDefaults.iconSizeFor(iconSize))
        modifier = Modifier.size(iconSize)
    )
}


//ToggleButton(
//checked = checkedSOS,
//onCheckedChange = {
//    Log.d(TAG, " SOS ToggleButton checkedSOS = $checkedSOS")
//    viewModel.toggleSos(!checkedSOS)
//},
//colors = ToggleButtonDefaults.toggleButtonColors(
//containerColor = Color(COLOR_UNCHECKED_BUTTON),
//contentColor = Color.White,
//checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
//checkedContentColor = Color.White
//),
//contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
//modifier = Modifier
//.width(toggleBtnWidth)
//.height(toggleBtnHeight),
//shapes = ToggleButtonDefaults.shapes(
//shape = CircleShape,
//pressedShape = CircleShape,
//checkedShape = CircleShape
//),
//border = BorderStroke(width = 1.dp, color = Color.White)
//) {
//    Log.d(TAG, "  sosOn_1 = $sosOn")
//    Icon(
//        if (checkedSOS) Icons.Filled.Sos else Icons.Filled.Sos,
//        contentDescription = "Localized description",
//        modifier = Modifier.size(ButtonDefaults.iconSizeFor(size))
//    )
//}