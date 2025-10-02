package nodomain.greensnowapps.lightsignals.ui.screens

import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel
import nodomain.greensnowapps.lightsignals.SOS_ON_SIGNAL_SCREEN
import nodomain.greensnowapps.lightsignals.TITLE_ON_SIGNAL_SCREEN

private const val TAG = "PhoneSignalScreen"

@Composable
fun PhoneSignalScreen(
    viewModel: PhoneMainViewModel,
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
        TITLE_ON_SIGNAL_SCREEN
    } else {
        SOS_ON_SIGNAL_SCREEN
    }

//    val view = LocalView.current
//    DisposableEffect(view) {
//        val activity = view.context as? Activity
//        val window = activity?.window
//        if (window != null) {
//            // let the app draw behind system bars
//            WindowCompat.setDecorFitsSystemWindows(window, false)
//            val controller = WindowInsetsControllerCompat(window, view)
//            // hide nav bar (and status bar if you want)
//            controller.hide(WindowInsetsCompat.Type.navigationBars())
//            // allow lightsignals to swipe to reveal transiently
//            controller.systemBarsBehavior =
//                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//
//            onDispose {
//                // show nav bar again and restore default behavior
//                controller.show(WindowInsetsCompat.Type.navigationBars())
//                WindowCompat.setDecorFitsSystemWindows(window, true)
//            }
//        } else {
//            onDispose { }
//        }
//    }

    // Render full‐screen box that shows `color` when visible, or black when not
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(color = background)
    ) {
        Text(
            text = screenText,
            color = Color.Gray,
            fontSize = 32.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)      // Center horizontally at the top
                .padding(top = 100.dp),           // Or use .offset(y = 50.dp)
            textAlign = TextAlign.Center,
            style = typography.bodyLarge
        )
    }
}