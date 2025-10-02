package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
//import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
//import androidx.hilt.navigation.compose.hiltViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneSoundViewModel
import nodomain.greensnowapps.lightsignals.R

//@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhoneSoundMenuButton(
    toggleBtnWidth: Dp,
    toggleBtnHeight: Dp,
    iconSize: Dp,
    phoneSoundViewModel: PhoneSoundViewModel = hiltViewModel<PhoneSoundViewModel>(),
) {

    var expanded by remember { mutableStateOf(false) }
    val active by phoneSoundViewModel.activeSound.collectAsState()

    Box(
        modifier = Modifier
    ) {
        LSToggleButton(
            toggleBtnWidth,
            toggleBtnHeight,
            checkedBtn = expanded,
            onCheckedChange = { expanded = it },
            icon = if (active == 0) Icons.Filled.MusicOff else Icons.Filled.MusicNote,
//            modifier = Modifier.size(ButtonDefaults.iconSizeFor(iconSize))
            modifier = Modifier.size(iconSize)
        )
    }

//    Box(
//        modifier = Modifier
//    ) {
//        ToggleButton(
//            checked = expanded,
//            onCheckedChange = {
//                expanded = it
//            },
//            colors = ToggleButtonDefaults.toggleButtonColors(
//                containerColor = Color(COLOR_UNCHECKED_BUTTON),
//                contentColor = Color.White,
//                checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
//                checkedContentColor = Color.White
//            ),
//            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
//            modifier = Modifier
//                .width(toggleBtnWidth)
//                .height(toggleBtnHeight),
//            shapes = ToggleButtonDefaults.shapes(
//                shape = CircleShape,
//                pressedShape = CircleShape,
//                checkedShape = CircleShape
//            ),
//            border = BorderStroke(width = 1.dp, color = Color.White),
////            colors = ToggleButtonDefaults.toggleButtonColors(
////                containerColor = Color(COLOR_UNCHECKED_BUTTON),
////                contentColor = Color.White,
////                checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
////                checkedContentColor = Color.White
////            ),
////            shapes = ToggleButtonDefaults.shapes(
////                shape = CircleShape,
////                pressedShape = CircleShape,
////                checkedShape = CircleShape
////            ) ,
////            border = BorderStroke(width = 1.dp, color = Color.White),
////            modifier = Modifier
////                .size(btnSize)
//        ) {
//            Icon(
//                if (active == 0) Icons.Filled.MusicOff else Icons.Filled.MusicNote,
//                contentDescription = "Localized description",
////                modifier = Modifier.size(btnIconSize)
//                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.LargeContainerHeight))
//
//            )
//        }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.width(160.dp)  // menu width
    ) {
        // Off entry
        DropdownMenuItem(
            text = { Text(stringResource(R.string.no_sound)) },
            leadingIcon = {
                Icon(
                    imageVector = if (active == 0) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (active == 0) "On" else "Off"
                )
            },
            onClick = {
                phoneSoundViewModel.setActiveSound(0)
                expanded = false
            }
        )
        HorizontalDivider()
        // Sound 1
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sound_1)) },
            leadingIcon = {
                Icon(
                    imageVector = if (active == 1) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (active == 1) "On" else "Off"
                )
            },
            onClick = {
                phoneSoundViewModel.setActiveSound(1)
                expanded = false
            }
        )
        // Sound 2
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sound_2)) },
            leadingIcon = {
                Icon(
                    imageVector = if (active == 2) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (active == 2) "On" else "Off"
                )
            },
            onClick = {
                phoneSoundViewModel.setActiveSound(2)
                expanded = false
            }
        )
        // Sound 3
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sound_3)) },
            leadingIcon = {
                Icon(
                    imageVector = if (active == 3) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (active == 3) "On" else "Off"
                )
            },
            onClick = {
                phoneSoundViewModel.setActiveSound(3)
                expanded = false
            }
        )
    }
}
//}
