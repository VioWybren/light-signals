package nodomain.greensnowapps.lightsignals.ui.screens

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


@Composable
fun WearSignalScreen(
    viewModel: WearMainViewModel,
    soundViewModel: WearSoundViewModel,
    onBack: () -> Unit
) {

    val color by viewModel.activeColor.collectAsState()
    val blinkOn by viewModel.blinkOn.collectAsState(false)
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
    var brightness by remember { mutableFloatStateOf(1f) }

    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

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
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(color = background)
                .onRotaryScrollEvent { event ->
                    val offset: Float = event.verticalScrollPixels
                    brightness = (brightness + offset * 0.005f)
                        .coerceIn(0f, 1f)
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
                    .align(Alignment.TopCenter)
                    .padding(top = 30.dp),
                textAlign = TextAlign.Center,
                style = typography.bodyLarge
            )
        }
    }
}
