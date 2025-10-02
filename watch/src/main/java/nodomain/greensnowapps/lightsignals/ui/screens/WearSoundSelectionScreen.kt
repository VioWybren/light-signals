package nodomain.greensnowapps.lightsignals.ui.screens


import android.annotation.SuppressLint
import android.util.Log
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
//import androidx.hilt.navigation.compose.hiltViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

private const val TAG = "WearSoundSelectionScreen"

@SuppressLint("UnrememberedMutableState")
@Composable
fun WearSoundSelectionScreen(
    wearSoundViewModel: WearSoundViewModel = hiltViewModel<WearSoundViewModel>(),
    onSoundSelected: (Int) -> Unit,
    onHideSoundSelector: () -> Unit,
) {
    val context = LocalContext.current
    val listOfSounds: List<Int> = listOf(0, 1, 2, 3)
    val active by wearSoundViewModel.activeSound.collectAsState()
    Log.d("MY_LOG", "SoundSelectionScreen selectedSound_0: $active")
    // subscribe to volume state
    val volume by wearSoundViewModel.volumeFlow.collectAsState()

    val listState = rememberScalingLazyListState()
    val vignetteState = mutableStateOf(VignettePosition.TopAndBottom)
    val showVignette = mutableStateOf(true)

    // intercept back-button
    BackHandler(enabled = true) {
        onHideSoundSelector()
    }

    // In an Activity or top-level Composable, after viewModel created:
//    LaunchedEffect(Unit) {
//        // read the current persisted volume once and set SoundManager
//        val persisted = wearSoundViewModel.volume.first() // import kotlinx.coroutines.flow.first
////        SoundManager.setMasterVolume(persisted)
//        wearSoundViewModel.setVolume(context,persisted)
//    }

    Scaffold(
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState, modifier = Modifier)
        },
        vignette = {
            if (showVignette.value) {
                Vignette(vignettePosition = vignetteState.value)
            }
        },
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item { ListHeader { Text(text = stringResource(R.string.sound_settings), color = Color.White) } }

            item {
                InlineSlider(
                    modifier = Modifier.height(30.dp), // <- constrains the slider to screen width
                    value = volume,
                    onValueChange = {
                        wearSoundViewModel.setVolume(context,it)
                        Log.d(TAG,"volume: $it")
                                    }, // <- actually update state/viewmodel
                    increaseIcon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Increase", tint = Color.White) },
                    decreaseIcon = { Icon(Icons.AutoMirrored.Rounded.VolumeDown, "Decrease", tint = Color.White) },
                    valueRange = 0f..1f,
                    steps = 9,
//                    steps = 8,
                    segmented = false,
                )
            }
            // Items
            items(listOfSounds.size) { index ->
                val soundNumber = listOfSounds[index]
                SelectableChip(
                    modifier = Modifier.fillMaxWidth(),
                    selected = (active == soundNumber),
                    onClick = {
                        onSoundSelected(soundNumber)
                        wearSoundViewModel.setActiveSound(soundNumber)
                        onHideSoundSelector()       // <-- hide screen
                    },
                    label = {
                        Text(
                            text = if (soundNumber == 0) stringResource(R.string.no_sound) else "${stringResource(R.string.sound)} $soundNumber",
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    enabled = true,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            // A little space before the footer
            item {
                Spacer(modifier = Modifier.height(2.dp))
            }
            // Footer with a Cancel chip
            item {
                Chip(
                    onClick = { onHideSoundSelector() },  // <-- hide screen
                    label = { Text(stringResource(R.string._cancel), color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

