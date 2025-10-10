package nodomain.greensnowapps.lightsignals.ui.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneSoundViewModel

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
            modifier = Modifier.size(iconSize)
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.width(160.dp)
    ) {
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
