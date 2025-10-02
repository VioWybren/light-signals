package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
//import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

//@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhoneBlinkButton(
    toggleBtnWidth: Dp,
    toggleBtnHeight: Dp,
    checkedBtn: Boolean,
    sosOn: Boolean,
    viewModel: PhoneMainViewModel,
    iconSize: Dp
) {
    //lambda for blink handling
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

//                        ToggleButton(
//                            checked = checkedBlink,
//                            onCheckedChange = {
//                                Log.d(TAG, "ToggleButton checkedBlink: $checkedBlink; blinkOn= $blinkOn")
//                                if (!sosOn) {
//                                    checkedBlink
//                                }
//                                if (checkedBlink) {
//                                    viewModel.toggleControl(false)
//                                } else {
//                                    viewModel.toggleControl(true)
//                                }
//                            },
//                            colors = ToggleButtonDefaults.toggleButtonColors(
//                                containerColor = Color(COLOR_UNCHECKED_BUTTON),
//                                contentColor = Color.White,
//                                checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
//                                checkedContentColor = Color.White
//                            ),
//                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
//                            modifier = Modifier
//                                .width(toggleBtnWidth)
//                                .height(toggleBtnHeight),
//                            shapes = ToggleButtonDefaults.shapes(
//                                shape = CircleShape,
//                                pressedShape = CircleShape,
//                                checkedShape = CircleShape
//                            ),
//                            border = BorderStroke(width = 1.dp, color = Color.White)
//                        ) {
//                            Icon(
//                                if (checkedBlink) Icons.Filled.Pause else Icons.Filled.PlayArrow,
//                                contentDescription = "Localized description",
////                                modifier = Modifier.size(roundToggleBtnIconSize)
//                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.LargeContainerHeight))
//                            )
//                        }

