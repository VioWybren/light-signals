package nodomain.greensnowapps.lightsignals.ui.screens

import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material3.TimeText
import nodomain.greensnowapps.lightsignals.WearConstants
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearMainViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearSoundViewModel

private const val TAG = "WearSignalScreen"

@Composable
fun WearSignalScreen(
    viewModel: WearMainViewModel,
    soundViewModel: WearSoundViewModel,  //don't delete !! need for sound
    onBack: () -> Unit
) {
    //  Collect the flows
    val color by viewModel.activeColor.collectAsState()     // the current button’s color
    Log.d(TAG,"color = $color")
    val blinkOn by viewModel.blinkOn.collectAsState(false)   // true when blinking is enabled
    Log.d(TAG,"blinkOn= $blinkOn")
    val delayMs by viewModel.blinkDelay.collectAsState()     // interval in ms
    Log.d(TAG,"delayMs = $delayMs ")
    val sosOn by viewModel.sosOn.collectAsState()
    val flashOn by viewModel.flashOn.collectAsState()

    val colorBlinkActive by viewModel.colorBlinkActive.collectAsState(true)

    val background =
        if (flashOn) {
            Color.Black
        } else {
            if (blinkOn) {
                if (colorBlinkActive) color else Color.Black
            } else {
                if (sosOn) {
                    if (colorBlinkActive) color else Color.Black
                } else {
                    color
                }
            }
        }

    val screenText = if (!sosOn) {
        WearConstants.TITLE_ON_SIGNAL_SCREEN
    } else {
        WearConstants.SOS_ON_SIGNAL_SCREEN
    }
    val focusRequester: FocusRequester = remember { FocusRequester() }
    var brightness by remember { mutableFloatStateOf(1f) } // 1 = max, 0 = min
    // Get the host Activity safely
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        // 1) Ask the window to stay on
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 2) And immediately grab focus for this Box
//        focusRequester.requestFocus()
    }

    // Whenever brightness changes, push it to the Window
    LaunchedEffect(brightness) {
        activity?.window?.attributes =
            activity.window.attributes
                .also { it.screenBrightness = brightness }
    }

    Scaffold(
        timeText = {
            TimeText()
        },
    ) {
        // Render full‐screen box that shows `color` when visible, or black when not
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(color = background)
                .onRotaryScrollEvent { event ->
                    Log.d(TAG,"event: $event")
                    val offset: Float = event.verticalScrollPixels
                    Log.d(TAG,"offset: $offset")
                    brightness = (brightness + offset * 0.005f)
                        .coerceIn(0f, 1f)
                    Log.d(TAG,"brightness $brightness")
                    true
                }
                .requestFocusOnHierarchyActive()
                .focusRequester(focusRequester)
                .focusable(),
        )
        {
            Text(
                text = screenText,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)      // Center horizontally at the top
                    .padding(top = 30.dp),           // Or use .offset(y = 30.dp)
                textAlign = TextAlign.Center,
                style = typography.bodyLarge
            )
        }
    }
}
