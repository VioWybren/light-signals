package lightsignalnew.example.user.lightsignalnew.lightsignalnew


import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
//import androidx.wear.compose.material.Slider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.setValue

import androidx.compose.ui.graphics.Color
import nodomain.greensnowapps.lightsignals.ui.elements.WearColorPicker


@Composable
fun ControlScreen() {
    // The progress as read from DataStore (persisted state)
//    val persistedColorProgress by viewModel.progressColor.collectAsState()
//    val persistedColorProgress by viewModel.progressColor.collectAsState(initial = WearMainViewModel.DEFAULT_COLOR_PROGRESS)
    // Local slider state used during dragging
//    var sliderPositionColor by remember { mutableStateOf(persistedColorProgress) }
    // Keep local slider value in sync when persisted progress changes externally
//    LaunchedEffect(persistedColorProgress) {
//        sliderPositionColor = persistedColorProgress
//    }
    // **Compute the live color** on every sliderPositionColor change
//    val liveMiddleColor = remember(sliderPositionColor) {
//        nodomain.greensnowapps.lightsignals.computeSpecialColor(sliderPositionColor.toInt())
//    }


    var hue by remember { mutableStateOf(180f) }    // 0°–360°
    var freq by remember { mutableStateOf(5f) }     // 1–10



    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
        ){

        // --- FULL-WIDTH TOP SLIDER ---
        WearColorPicker(
            progress = 100f,
            onProgressChanged = { newValue ->
//                sliderPositionColor = 10f
                newValue  // update local state continuously during dragging
            },
            onValueChangeFinished = {
                // Update the persisted state only when the lightsignals finishes sliding
//                viewModel.updateColorProgress(sliderPositionColor)
            }
        )
    }
}


//@Composable
//fun ControlScreen() {
//
//    var hue by remember { mutableStateOf(180f) }    // 0°–360°
//    var freq by remember { mutableStateOf(5f) }     // 1–10
//    ConstraintLayout(
//        modifier = Modifier
//            .fillMaxWidth()
////            .height(80.dp)
//    ) {
//        val (leftSliderRef, firstBtnRef, secondBtnRef, rightSliderRef) = createRefs()
//
//        // Left slider 16dp from parent.start
//        Box(
//            modifier = Modifier
//                .constrainAs(leftSliderRef) {
//                    start.linkTo(parent.start, margin = 16.dp)
//                    centerVerticallyTo(parent)
////                    width = Dimension.value(80.dp)
////                    height = Dimension.value(80.dp)
//                }
//        ) {
//                    RadialSlider(
//            modifier    = Modifier.align(Alignment.CenterStart),
//            value       = hue,
//            onValueChange = { hue = it },
//            valueRange  = 0f..10f,
//            startAngle  = 140f,
//            sweepAngle  = 80f,
//            strokeWidth = 10.dp,
//            thumbRadius = 10.dp
//        )
//        }
//
//        // First button, to the right of left slider
//        Button(
//            onClick = { /* TODO */ },
//            modifier = Modifier.constrainAs(firstBtnRef) {
//                start.linkTo(leftSliderRef.end, margin = 8.dp)
//                centerVerticallyTo(parent)
//            }
//        ) { Text("First") }
//
//        // Second button, to the right of first button
//        Button(
//            onClick = { /* TODO */ },
//            modifier = Modifier.constrainAs(secondBtnRef) {
//                start.linkTo(firstBtnRef.end, margin = 8.dp)
//                centerVerticallyTo(parent)
//            }
//        ) { Text("Second") }
//
//        // Right slider 16dp from parent.end
//        Box(
//            modifier = Modifier
//                .constrainAs(rightSliderRef) {
//                    end.linkTo(parent.end, margin = 16.dp)
//                    centerVerticallyTo(parent)
////                    width = Dimension.value(80.dp)
////                    height = Dimension.value(80.dp)
//                }
//        ) {
//            RadialSlider1(
//            modifier    = Modifier.align(Alignment.CenterEnd),
//            value       = freq,
//            onValueChange = { freq = it },
//            valueRange  = 0f..10f,
//            startAngle  = 320f,
//            sweepAngle  = 80f,
//            strokeWidth = 10.dp,
//            thumbRadius = 10.dp
//        )
//        }
//    }
//}




//@Composable
//fun ControlScreen() {
//    var hue by remember { mutableStateOf(180f) }
//    var freq by remember { mutableStateOf(5f) }
//    var isLeftActive by remember { mutableStateOf(true) }
//
//    Row(Modifier.fillMaxSize()) {
//        // ─── Left half ───────────────────────────────
////        Box(
////            modifier = Modifier
////                .weight(1f)
////                .fillMaxHeight()
////                .pointerInput(Unit) {
////                    detectTapGestures { isLeftActive = true }
////                }
//////                .onRotaryScrollEvent { ev ->
//////                    if (isLeftActive) {
//////                        hue = (hue + ev.verticalScrollPixels / 100f)
//////                            .coerceIn(0f, 360f)
//////                    }
//////                    true
//////                }
////        ) {
//            RadialSlider(
////                modifier    = Modifier
//////                    .size(200.dp)
////                    // ← change here: start of the left half = actual left bezel
////                    .align(Alignment.CenterStart),
//                value       = hue,
//                onValueChange = { hue = it },
//                valueRange  = 0f..360f,
//                startAngle  = 140f,
//                sweepAngle  = 80f,
//                strokeWidth = 10.dp,
//                thumbRadius = 10.dp
//            )
////        }
//
//        // ─── Right half ──────────────────────────────
////        Box(
////            modifier = Modifier
////                .weight(1f)
////                .fillMaxHeight()
////                .pointerInput(Unit) {
////                    detectTapGestures { isLeftActive = false }
////                }
////                .onRotaryScrollEvent { ev ->
////                    if (!isLeftActive) {
////                        freq = (freq + ev.verticalScrollPixels / 100f)
////                            .coerceIn(1f, 10f)
////                    }
////                    true
////                }
////        ) {
//            RadialSlider(
////                modifier    = Modifier
//////                    .size(200.dp)
////                    // ← change here: end of the right half = actual right bezel
////                    .align(Alignment.CenterEnd),
//                value       = freq,
//                onValueChange = { freq = it },
//                valueRange  = 1f..10f,
//                startAngle  = 0f,
//                sweepAngle  = 180f,
//                strokeWidth = 10.dp,
//                thumbRadius = 10.dp
//            )
//        }
////    }
//}



//@Composable
//fun ControlScreen() {
//
//        var hue by remember { mutableStateOf(180f) }    // 0°–360°
//    var freq by remember { mutableStateOf(5f) }     // 1–10
//
//    Row(
//        modifier = Modifier
//            .fillMaxWidth(),
////            .height(80.dp),                         // enough height for your RadialSlider
//        verticalAlignment = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.SpaceBetween
//    ) {
//        // 1) Left Rotary (Radial) Slider
//        Box(
////            modifier = Modifier.width(80.dp)        // allocate 80dp for the arc
//        ) {
//            RadialSlider(
//                modifier    = Modifier.fillMaxSize(),
//                value       = hue,
//                onValueChange = { hue = it },
//                valueRange  = 0f..360f,
//                startAngle  = 140f,
//                sweepAngle  = 80f,
//                strokeWidth = 8.dp,
//                thumbRadius = 6.dp
//            )
//        }
//
//        // 2) Buttons in the center
//        Row(
//            modifier = Modifier
//                .weight(1f)                          // take all the space between sliders
//                .padding(horizontal = 8.dp),
//            horizontalArrangement = Arrangement.spacedBy(8.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Button(onClick = { /* TODO */ }) {
//                Text("First")
//            }
//            Button(onClick = { /* TODO */ }) {
//                Text("Second")
//            }
//        }
//
//        // 3) Right Rotary Slider
//        Box(
////            modifier = Modifier.width(80.dp)
//        ) {
//            RadialSlider(
//                modifier    = Modifier.fillMaxSize(),
//                value       = freq,
//                onValueChange = { freq = it },
//                valueRange  = 1f..10f,
//                startAngle  =  320f,
//                sweepAngle  = 80f,
//                strokeWidth = 8.dp,
//                thumbRadius = 6.dp
//            )
//        }
//    }
//}



//@Composable
//fun ControlScreen() {
//    var hue by remember { mutableStateOf(180f) }    // 0°–360°
//    var freq by remember { mutableStateOf(5f) }     // 1–10
//
//    Row(
//        Modifier.fillMaxSize(),
//        horizontalArrangement = Arrangement.SpaceBetween,
//        verticalAlignment = Alignment.Top
//        ){
//
//    Box(Modifier.fillMaxSize()) {
////         Color slider on the left edge (9 o’clock → 180° start, 180° sweep)
//        RadialSlider(
////            modifier    = Modifier.align(Alignment.CenterStart),
//            value       = hue,
//            onValueChange = { hue = it },
//            valueRange  = 0f..10f,
//            startAngle  = 140f,
//            sweepAngle  = 80f,
//            strokeWidth = 10.dp,
//            thumbRadius = 10.dp
//        )
//
//        // Frequency slider on the right edge (3 o’clock → 0° start, 180° sweep)
//        RadialSlider(
////            modifier    = Modifier.align(Alignment.CenterEnd),
//            value       = freq,
//            onValueChange = { freq = it },
//            valueRange  = 0f..10f,
//            startAngle  = 320f,
//            sweepAngle  = 80f,
//            strokeWidth = 10.dp,
//            thumbRadius = 10.dp
//        )
//        }
//    }
//}


//@Composable
//fun ControlScreen() {
//    var leftVal by remember { mutableStateOf(50f) }
//    var rightVal by remember { mutableStateOf(50f) }
//
//    Box(
//        Modifier
//            .fillMaxSize()
//            .onRotaryScrollEvent { ev ->
//                // route crown deltas to whichever slider has focus…
//                true
//            }
//    ) {
//        // ─── Left edge (9 o'clock) ────────────────────────────────────────
//        CurvedLayout(
//            modifier = Modifier.fillMaxSize(),
//            anchor = 180f,                           // 9 o’clock
//            radialAlignment = CurvedAlignment.Radial.Outer,
//            angularDirection = CurvedDirection.Angular.Normal
//        ) {
//
//            CurvedRow(
//                curvedModifier = CurvedModifier
//                    .angularSize(120f)    // 120° arc span
//                    .radialSize(12.dp)    // 12dp of radial thickness
//            )
//
//            // curvedComposable can host any Composable along the arc
//            curvedComposable(
//                modifier = CurvedModifier
//                    .angularSize(120f)
//                    .radialSize(5.dp)
//            ) {
//                // this content block is laid out along that arc
////                Slider(
////                    value = leftVal,
////                    onValueChange = { leftVal = it },
////                    valueRange = 0f..100f,
////                    modifier = Modifier.fillMaxSize()  // fills the curvedComposable bounds
////                )
//                Slider(
//                    value            = leftVal,
//                    onValueChange    = { leftVal = it },
//                    steps            = 0,              // 3rd parameter
//                    modifier         = Modifier.fillMaxSize(),
//                    // then any of the optional named params:
//                    valueRange       = 0f..100f,
//                    decreaseIcon     = { /*…*/ },
//                    increaseIcon     = { /*…*/ },
//                    enabled          = true,
//                    segmented        = false,
////                    shape            = /* your Shape */,
////                    colors           = SliderDefaults.colors()
//                )
//            }
//        }
//
//        // ─── Right edge (3 o'clock) ───────────────────────────────────────
//        CurvedLayout(
//            modifier = Modifier.fillMaxSize(),
//            anchor = 0f,                           // 3 o’clock
//            radialAlignment = CurvedAlignment.Radial.Outer,
//            angularDirection = CurvedDirection.Angular.Reversed
//        ) {
//            curvedComposable(
//                modifier = CurvedModifier
//                    .angularSize(120f)
//                    .radialSize(5.dp)
//            ) {
////                Slider(
////                    value         = rightVal,
////                    onValueChange = { rightVal = it },
////                    valueRange    = 0f..100f,
////                    modifier      = Modifier.fillMaxSize()
////                )
//                Slider(
//                    value            = rightVal,
//                    onValueChange    = { rightVal = it },
//                    steps            = 0,              // 3rd parameter
//                    modifier         = Modifier.fillMaxSize(),
//                    // then any of the optional named params:
//                    valueRange       = 0f..100f,
//                    decreaseIcon     = { /*…*/ },
//                    increaseIcon     = { /*…*/ },
//                    enabled          = true,
//                    segmented        = true,
////                    shape            = /* your Shape */,
////                    colors           = SliderDefaults.colors()
//                )
//            }
//        }
//    }
//}
//

//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.input.rotary.RotaryScrollEvent
//import androidx.compose.ui.input.rotary.onRotaryScrollEvent
//import androidx.compose.ui.unit.dp
//import androidx.wear.compose.foundation.CurvedLayout
//import androidx.wear.compose.foundation.CurvedModifier
//import androidx.wear.compose.foundation.CurvedAlignment
//import androidx.wear.compose.foundation.CurvedDirection
//import androidx.wear.compose.foundation.angularSize
//import androidx.wear.compose.foundation.curvedRow
//import androidx.wear.compose.foundation.radialSize
//import androidx.wear.compose.material3.Slider
//
//@Composable
//fun ControlScreen() {
//    // Two state values
//    var hue by remember { mutableStateOf(180f) }
//    var freq by remember { mutableStateOf(5f) }
//
//    Box(
//        Modifier
//            .fillMaxSize()
//            // Intercept all rotary events here:
//            .onRotaryScrollEvent { event: RotaryScrollEvent ->
//                // event.verticalScrollPixelsDelta is positive for CW, negative for CCW
////                val deltaPx = event.verticalScrollPixelsDelta
//                val deltaPx = event.verticalScrollPixels
//
//                // Decide target: e.g. if lightsignals last touched the left slider, or both
//                // For demo, we’ll alternate: even seconds → hue, odd → freq
//                if (System.currentTimeMillis() / 1000 % 2 == 0L) {
//                    hue = (hue + deltaPx * 0.1f).coerceIn(0f, 360f)
//                } else {
//                    freq = (freq + deltaPx * 0.01f).coerceIn(1f, 10f)
//                }
//                true // we consumed the event
//            }
//    ) {
//        // Left‐edge slider at 9 o’clock
//        CurvedLayout(
//            modifier        = Modifier.fillMaxSize(),
//            anchor          = 180f,
//            radialAlignment = CurvedAlignment.Radial.Outer,
//            angularDirection= CurvedDirection.Angular.Reversed
//        ) {
//            curvedRow {
//                CurvedModifier
//                    .angularSize(120f)
////                    .radialSize(20f)
//                    .radialSize(20.dp)
//                    .let { cm ->
//                        Slider(
//                            value         = hue,
//                            onValueChange = { hue = it },
//                            valueRange    = 0f..360f,
//                            steps         = 359,
//                            modifier      = Modifier.fillMaxSize()
//                        )
//                    }
//            }
//        }
//
//        // Right‐edge slider at 3 o’clock
//        CurvedLayout(
//            modifier        = Modifier.fillMaxSize(),
//            anchor          = 0f,
//            radialAlignment = CurvedAlignment.Radial.Outer,
//            angularDirection= CurvedDirection.Angular.Reversed
//        ) {
//            curvedRow {
//                CurvedModifier
//                    .angularSize(120f)
////                    .radialSize(20f)
//                    .radialSize(20.dp)
//                    .let { cm ->
//                        Slider(
//                            value         = freq,
//                            onValueChange = { freq = it },
//                            valueRange    = 1f..10f,
//                            steps         = 9,
//                            modifier      = Modifier.fillMaxSize()
//                        )
//                    }
//            }
//        }
//    }
//}
//


//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.input.rotary.RotaryScrollEvent
//import androidx.compose.ui.input.rotary.onRotaryScrollEvent
//import androidx.wear.compose.foundation.CurvedAlignment
//import androidx.wear.compose.foundation.CurvedDirection
//import androidx.wear.compose.foundation.CurvedLayout
//import androidx.wear.compose.foundation.CurvedModifier
//import androidx.wear.compose.foundation.angularSize
//import androidx.wear.compose.foundation.curvedRow
//import androidx.wear.compose.foundation.radialSize
//import androidx.wear.compose.material3.Slider
//
//
//@Composable
//fun ControlScreen() {
//    // Two state values
//    var hue by remember { mutableStateOf(180f) }
//    var freq by remember { mutableStateOf(5f) }
//
//    Box(
//        Modifier
//            .fillMaxSize()
//            // Intercept all rotary events here:
//            .onRotaryScrollEvent { event: RotaryScrollEvent ->
//                // event.verticalScrollPixelsDelta is positive for CW, negative for CCW
//                val deltaPx = event.verticalScrollPixels
//
//                // Decide target: e.g. if lightsignals last touched the left slider, or both
//                // For demo, we’ll alternate: even seconds → hue, odd → freq
//                if (System.currentTimeMillis() / 1000 % 2 == 0L) {
//                    hue = (hue + deltaPx * 0.1f).coerceIn(0f, 360f)
//                } else {
//                    freq = (freq + deltaPx * 0.01f).coerceIn(1f, 10f)
//                }
//                true // we consumed the event
//            }
//    ) {
//        // Left‐edge slider (anchored at 180°)
//        CurvedLayout(
//            modifier        = Modifier.fillMaxSize(),
//            anchor          = 180f,                    // 9 o’clock
//            radialAlignment = CurvedAlignment.Outside,
//            angularDirection= CurvedDirection.Normal
//        ) {
//            curvedRow {
//                // Use a narrower arc—e.g. 120°—so it doesn’t wrap too far
//                CurvedModifier
//                    .angularSize(120f)
//                    .radialSize(20f)
//                    .let { cm ->
//                        Slider(
//                            value       = leftValue,
//                            onValueChange = { leftValue = it },
//                            valueRange  = 0f..100f,
//                            steps       = 0,
//                            modifier    = Modifier.curvedLayoutModifier(cm)
//                        )
//                    }
//            }
//        }
//
//        // Right‐edge slider (anchored at 0°)
//        CurvedLayout(
//            modifier        = Modifier.fillMaxSize(),
//            anchor          = 0f,                      // 3 o’clock
//            radialAlignment = CurvedAlignment.Outside,
//            angularDirection= CurvedDirection.Reversed  // flip so drag feels natural
//        ) {
//            curvedRow {
//                CurvedModifier
//                    .angularSize(120f)
//                    .radialSize(20f)
//                    .let { cm ->
//                        Slider(
//                            value         = rightValue,
//                            onValueChange = { rightValue = it },
//                            valueRange    = 0f..100f,
//                            steps         = 0,
//                            modifier      = Modifier.curvedLayoutModifier(cm)
//                        )
//                    }
//            }
//        }
//    }
//}

private const val FULL = 255

/**
 * Computes a "special" color based on the current progress.
 */
fun computeSpecialColor(progr: Int): Color {
    var r = 0
    var g = 0
    var b = 0
    when {
        progr < 256 -> {
            b = progr
        }
        progr < 256 * 2 -> {
            g = progr % 256
            b = 256 - progr % 256
        }
        progr < 256 * 3 -> {
            g = FULL
            b = progr % 256
        }
        progr < 256 * 4 -> {
            r = progr % 256
            g = 256 - progr % 256
            b = 256 - progr % 256
        }
        progr < 256 * 5 -> {
            r = FULL
            g = 0
            b = progr % 256
        }
        progr < 256 * 6 -> {
            r = FULL
            g = progr % 256
            b = 256 - progr % 256
        }
        progr < 256 * 7 -> {
            r = FULL
            g = FULL
            b = progr % 256
        }
        else -> {
            // default value when out of bounds
            r = 0
            g = 0
            b = 0
        }
    }
    return Color(r, g, b, FULL)
}

