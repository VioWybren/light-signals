package nodomain.greensnowapps.lightsignals.ui.screens


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SelectableChip
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.TimeText
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearSoundViewModel


@Composable
fun WearSoundSelectionScreen(
    wearSoundViewModel: WearSoundViewModel = hiltViewModel<WearSoundViewModel>(),
    onSoundSelected: (Int) -> Unit,
    onHideSoundSelector: () -> Unit,
) {

    val listOfSounds: List<Int> = listOf(0, 1, 2, 3)

    val active by wearSoundViewModel.activeSound.collectAsStateWithLifecycle()
    val volume by wearSoundViewModel.volumeFlow.collectAsStateWithLifecycle()

    val listState = rememberScalingLazyListState()

    BackHandler(enabled = true) {
        onHideSoundSelector()
    }

    Scaffold(
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState, modifier = Modifier)
        },
        vignette = {
            Vignette(vignettePosition = VignettePosition.TopAndBottom)
        },
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            item { ListHeader { Text(text = stringResource(R.string.sound_settings), color = Color.White) } }

            item {
                InlineSlider(
                    modifier = Modifier.height(30.dp),
                    value = volume,
                    onValueChange = wearSoundViewModel::setVolume,
                    increaseIcon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.control_increase_volume), tint = Color.White) },
                    decreaseIcon = { Icon(Icons.AutoMirrored.Rounded.VolumeDown, stringResource(R.string.control_decrease_volume), tint = Color.White) },
                    valueRange = 0f..1f,
                    steps = 9,
                    segmented = false,
                )
            }
            items(listOfSounds.size) { index ->
                val soundNumber = listOfSounds[index]
                SelectableChip(
                    modifier = Modifier.fillMaxWidth(),
                    selected = (active == soundNumber),
                    onClick = {
                        onSoundSelected(soundNumber)
                        wearSoundViewModel.setActiveSound(soundNumber)
                        onHideSoundSelector()
                    },
                    label = {
                        Text(
                            text = if (soundNumber == 0) stringResource(R.string.no_sound) else stringResource(R.string.sound_number, soundNumber),
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    enabled = true,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            item {
                Spacer(modifier = Modifier.height(2.dp))
            }
            item {
                Chip(
                    onClick = { onHideSoundSelector() },
                    label = { Text(stringResource(R.string._cancel), color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

