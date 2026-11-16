package nodomain.greensnowapps.lightsignals.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material3.TimeText
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearMainViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearSoundViewModel

/**
 * Displays the watch's selected screen signal and handles active-page rotary brightness.
 *
 * Collects ViewModel state with the lifecycle and restores the prior window brightness on stop
 * or disposal. The host owns pager navigation.
 *
 * @param viewModel ViewModel supplying observable signal state and handling control events.
 * @param soundViewModel Host-supplied sound ViewModel; this screen currently does not read it.
 * @param isActive Whether this control or pager page is currently selected.
 * @param onBack Host-provided back-navigation callback; this screen currently does not invoke
 *     it.
 */
@Composable
fun WearSignalScreen(
    viewModel: WearMainViewModel,
    soundViewModel: WearSoundViewModel,
    isActive: Boolean,
    onBack: () -> Unit
) {
    val color by viewModel.activeColor.collectAsStateWithLifecycle()
    val blinkOn by viewModel.blinkOn.collectAsStateWithLifecycle()
    val sosOn by viewModel.sosOn.collectAsStateWithLifecycle()
    val flashOn by viewModel.flashOn.collectAsStateWithLifecycle()
    val colorBlinkActive by viewModel.colorBlinkActive.collectAsStateWithLifecycle()

    val background = when {
        flashOn -> Color.Black
        blinkOn || sosOn -> if (colorBlinkActive) color else Color.Black
        else -> color
    }
    val screenText = stringResource(if (sosOn) R.string.sos_text else R.string.app_name)
    val focusRequester = remember { FocusRequester() }
    var brightness by remember { mutableFloatStateOf(1f) }
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LifecycleStartEffect(activity, isActive) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness
        if (isActive && window != null) {
            window.attributes = window.attributes.also { it.screenBrightness = brightness }
        }
        onStopOrDispose {
            if (isActive && window != null && previousBrightness != null) {
                window.attributes = window.attributes.also { it.screenBrightness = previousBrightness }
            }
        }
    }
    LaunchedEffect(brightness, isActive, activity) {
        if (isActive && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            activity?.window?.let { window ->
                window.attributes = window.attributes.also { it.screenBrightness = brightness }
            }
        }
    }

    Scaffold(timeText = { TimeText() }) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .onRotaryScrollEvent { event ->
                    if (!isActive) false else {
                        brightness = (brightness + event.verticalScrollPixels * 0.005f)
                            .coerceIn(0f, 1f)
                        true
                    }
                }
                .requestFocusOnHierarchyActive()
                .focusRequester(focusRequester)
                .focusable()
        ) {
            Text(
                text = screenText,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
