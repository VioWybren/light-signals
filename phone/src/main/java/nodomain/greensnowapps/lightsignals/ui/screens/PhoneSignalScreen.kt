package nodomain.greensnowapps.lightsignals.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel


/**
 * Displays the selected phone screen signal, using Blink/SOS phases and a dark screen for torch
 * output.
 *
 * Collects signal state with the lifecycle; the host owns page navigation.
 *
 * @param viewModel ViewModel supplying observable signal state and handling control events.
 * @param onBack Host-provided back-navigation callback; this screen currently does not invoke
 *     it.
 */
@Composable
fun PhoneSignalScreen(
    viewModel: PhoneMainViewModel,
    onBack: () -> Unit
) {
    val color by viewModel.activeColor.collectAsStateWithLifecycle()
    val blinkOn by viewModel.blinkOn.collectAsStateWithLifecycle()
    val sosOn by viewModel.sosOn.collectAsStateWithLifecycle()
    val flashOn by viewModel.flashOn.collectAsStateWithLifecycle()

    val colorBlinkActive by viewModel.colorBlinkActive.collectAsStateWithLifecycle()

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
        stringResource(R.string.app_name)
    } else {
        stringResource(R.string.sos_label)
    }

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
                .align(Alignment.TopCenter)
                .padding(top = 100.dp),
            textAlign = TextAlign.Center,
            style = typography.bodyLarge
        )
    }
}
