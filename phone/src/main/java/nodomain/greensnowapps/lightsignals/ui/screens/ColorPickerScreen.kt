package nodomain.greensnowapps.lightsignals.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
//import androidx.hilt.navigation.compose.hiltViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel

@Composable
fun ColorPickerScreen(
    viewModel: PhoneMainViewModel = hiltViewModel()
){
//    val context = LocalContext.current
////    val viewModel: MainViewModel = remember { MainViewModel(context) }
//
//    // The progress as read from DataStore (persisted state)
//    val persistedProgress by viewModel.progressColor.collectAsState(initial = 0f)
//    // Local slider state used during dragging
//    var localProgress by remember { mutableStateOf(persistedProgress) }
//
//    // Keep local slider value in sync when persisted progress changes externally
//    LaunchedEffect(persistedProgress) {
//        localProgress = persistedProgress
//    }
//
//    val selectedColor = computeSpecialColor(localProgress.toInt())
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        verticalArrangement = Arrangement.spacedBy(12.dp)
//    ) {
//        Text(text = "Choose a Color:")
//        ColorPickerSlider(
//            progress = localProgress,
//            onProgressChanged = { newValue ->
//                localProgress = newValue // update local state continuously during dragging
//            },
//            onValueChangeFinished = {
//                // Update the persisted state only when the lightsignals finishes sliding
//                viewModel.updateColorProgress(localProgress)
//            }
//        )
//        Spacer(modifier = Modifier.height(16.dp))
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(100.dp)
//                .background(selectedColor)
//        )
//        Text(text = "Selected Color: ${selectedColor.toHex()}")
//    }
}

/**
 * Extension function to convert a Compose Color to a Hex String.
 */
fun Color.toHex(): String {
    val a = (alpha * 255).toInt()
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return String.format("#%02X%02X%02X%02X", a, r, g, b)
}