package nodomain.greensnowapps.lightsignals.ui.screens

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
import nodomain.greensnowapps.lightsignals.SOS_ON_SIGNAL_SCREEN
import nodomain.greensnowapps.lightsignals.TITLE_ON_SIGNAL_SCREEN
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel


@Composable
fun PhoneSignalScreen(
    viewModel: PhoneMainViewModel,
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
        TITLE_ON_SIGNAL_SCREEN
    } else {
        SOS_ON_SIGNAL_SCREEN
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