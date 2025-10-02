//package nodomain.greensnowapps.lightsignals
//
//import android.annotation.SuppressLint
//import android.content.res.Configuration
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.interaction.MutableInteractionSource
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.BoxWithConstraints
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.WindowInsets
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.systemBarsPadding
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.layout.wrapContentHeight
//import androidx.compose.foundation.layout.wrapContentWidth
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.FlashlightOff
//import androidx.compose.material.icons.filled.FlashlightOn
//import androidx.compose.material.icons.filled.MoreVert
//import androidx.compose.material.icons.filled.Pause
//import androidx.compose.material.icons.filled.PlayArrow
//import androidx.compose.material.icons.filled.Sos
//import androidx.compose.material.icons.outlined.AllInclusive
//import androidx.compose.material.icons.outlined.Watch
//import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.DropdownMenu
//import androidx.compose.material3.DropdownMenuItem
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
//import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
//import androidx.compose.material3.Label
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Slider
//import androidx.compose.material3.SliderDefaults
//import androidx.compose.material3.Snackbar
//import androidx.compose.material3.SnackbarDuration
//import androidx.compose.material3.SnackbarHost
//import androidx.compose.material3.SnackbarHostState
//import androidx.compose.material3.Text
//
//import androidx.compose.material3.ToggleButton
//import androidx.compose.material3.ToggleButtonDefaults
//import androidx.compose.material3.TopAppBar
//import androidx.compose.material3.TopAppBarDefaults
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableFloatStateOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.draw.paint
//import androidx.compose.ui.draw.rotate
//import androidx.compose.ui.graphics.Brush
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.platform.LocalConfiguration
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.semantics.contentDescription
//import androidx.compose.ui.semantics.semantics
//import androidx.compose.ui.unit.Dp
//import androidx.compose.ui.unit.dp
//import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Active
//import kotlinx.coroutines.launch
//import kotlin.math.roundToInt
//
//
//// Main screen Composable that combines the color button area and the pickers.
//@SuppressLint("UnusedBoxWithConstraintsScope")
//@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
//@Composable
//fun MainScreen(
//    viewModel: PhoneMainViewModel,
//    onNext: () -> Unit
//) {
//
//
////    // Track the two permissions
////    var cameraGranted by remember { mutableStateOf(false) }
////    var notifGranted by remember { mutableStateOf(false) }
////
////
////    // Now on top, request both permissions in a bundle:
////    BundledPermissionRequester(
////        request = !cameraGranted || !notifGranted
////    ) { results ->
////        cameraGranted = results[Manifest.permission.CAMERA] == true
////        notifGranted = results[Manifest.permission.POST_NOTIFICATIONS] == true
////    }
//
////    var ready by remember { mutableStateOf(false) }
////
////    if (!ready) {
////        BundledPermissionRequester { ready = true }
//////        return
////    }
//
////    var cameraReady by remember { mutableStateOf(false) }
////    var notificationsReady by remember { mutableStateOf(false) }
////
////    // Kick off the permission flow once
////    // Request the camera permission first
////    if (!cameraReady) {
////        FlashPermissionRequest { cameraReady = true }
////        return
////    }
//
//    // Once camera’s granted, request notification permission
////    if (!notificationsReady) {
////        NotificationPermissionRequest { notificationsReady = true }
////        return
////    }
//
//    val context = LocalContext.current
//    val colorPickerViewModel: ColorPickerViewModel = remember { ColorPickerViewModel(context) }
//
//    // The progress as read from DataStore (persisted state)
////    val persistedFrequencyProgress by viewModel.progressFrequency.collectAsState(initial = 100f)
//    val persistedFrequencyProgress by viewModel.progressFrequency.collectAsState()
//    // Local slider state used during dragging
//    var sliderPositionFrequency by remember { mutableFloatStateOf(persistedFrequencyProgress) }
//
//    // Compute blink delay from frequency progress (e.g., direct mapping)
//    val blinkDelay = sliderPositionFrequency.toLong()
//
//    // Keep local slider value in sync when persisted progress changes externally
//    LaunchedEffect(persistedFrequencyProgress) {
//        sliderPositionFrequency = persistedFrequencyProgress
//    }
//
//    LaunchedEffect(blinkDelay) {
//        viewModel.updateBlinkDelay(blinkDelay)
//    }
//
//    // The progress as read from DataStore (persisted state)
////    val persistedColorProgress by viewModel.progressColor.collectAsState()
//    val persistedColorProgress by viewModel.progressColor.collectAsState(initial = DEFAULT_COLOR_PROGRESS)
//    // Local slider state used during dragging
//    var sliderPositionColor by remember { mutableFloatStateOf(persistedColorProgress) }
//    // Keep local slider value in sync when persisted progress changes externally
//    LaunchedEffect(persistedColorProgress) {
//        sliderPositionColor = persistedColorProgress
//    }
//    // **Compute the live color** on every sliderPositionColor change
//    val liveMiddleColor = remember(sliderPositionColor) {
//        PhoneUtils.computeSpecialColor(sliderPositionColor.toInt())
//    }
//
//    val activeButton by viewModel.activeButton.collectAsState()
////    val uiActiveButton by viewModel.uiActiveButton.collectAsState( initial = Active.RED)
//    Log.d("MY_LOG", "MainScreen !! active = $activeButton")
//    val flashOn by viewModel.flashOn.collectAsState()
//    Log.d("MY_LOG", "MainScreen !! flashOn = $flashOn")
//    val blinkOn by viewModel.blinkOn.collectAsState()
//    Log.d("MY_LOG", "MainScreen !!  blinkOn = $blinkOn")
//    val colorBlinkActive by viewModel.colorBlinkActive.collectAsState()
//    Log.d("MY_LOG", "MainScreen !!  colorBlinkActive  = $colorBlinkActive")
//    val sosOn by viewModel.sosOn.collectAsState()
//    Log.d("MY_LOG", "MainScreen !!  sosOn = $sosOn")
//    val controlMessage by viewModel.controlMessageFlow.collectAsState("")
//    Log.d("MY_LOG", "MainScreen !!  controlMessage = $controlMessage")
//    Log.d("MY_LOG", "MainScreen !!  -------")
//
//    var checkedFlash by remember { mutableStateOf(flashOn) }
//    LaunchedEffect(flashOn) { checkedFlash = flashOn }
//
//    var checkedBlink by remember { mutableStateOf(blinkOn && !sosOn) }
//    LaunchedEffect(blinkOn, sosOn) {
//        checkedBlink = when {
//            sosOn -> false
//            !sosOn -> blinkOn
//            else -> false
//        }
//    }
//    var checkedSOS by remember { mutableStateOf(sosOn) }
//    LaunchedEffect(sosOn) {
//        checkedSOS = sosOn
//    }
//
//    // State to hold the dialog message and control visibility
//    var showConnectToWatchDialog by remember { mutableStateOf(false) }
//
//    if (showConnectToWatchDialog) {
//        UniversalDialog(
//            onConfirm = {
//                viewModel.performWearOperations("start")
//                showConnectToWatchDialog = false
//            },
//            onDismiss = { showConnectToWatchDialog = false },
//            title = "Connect to watch",
//            message = "Do you want to connect to Light Signals on your watch?",
//            dismissText = "Cancel",
//            confirmText = "OK"
//        )
//    }
//    // State to hold the dialog message and control visibility
//    var showInstallOnWatchDialog by remember { mutableStateOf(false) }
//
//    // A single snackbarHostState for displaying update results
//    val snackbarHostState = remember { SnackbarHostState() }
//    val scope = rememberCoroutineScope()
//
//    fun showSnack(msg: String) {
//        scope.launch {
//            snackbarHostState.showSnackbar(
//                message = msg,
//                duration = SnackbarDuration.Short
//            )
//        }
//    }
//
//    LaunchedEffect(controlMessage) {
//        Log.d("MY_TAG", "MainScreen commandState: $controlMessage")
//        val inputCommand = Regex("[0-9]").replace(controlMessage, "")
//        if (inputCommand.isNotEmpty()) {
//            when (inputCommand) {
//                "The app isn't installed on connected watch. Do you want to install it?" -> {
//                    showInstallOnWatchDialog = true
//                }
//                "The app has been successfully installed on the watch" -> showSnack(inputCommand)
//                "Installation on watch has been failed. Please, try later" -> showSnack(inputCommand)
//                "Failed to launch the remote app on node" -> showSnack("Failed to launch the remote app on the watch")
//                "Light Signals has been launched on the watch. You can now control the app from it" -> showSnack(
//                    inputCommand
//                )
////           "" -> TODO()
//                else -> showSnack(inputCommand)
//            }
//        }
////        if (inputCommand.isNotEmpty()) {
////            if (inputCommand == "The app isn't installed on connected watch. Do you want to install it?") {
//////                showInstallOnWatchDialog = true
//////                showInstallOnWearDialog = true
//////                scope.launch {
//////                    snackbarHostState.showSnackbar(
//////                        message = "The app isn't installed on connected watch. Please install it",
//////                        duration = SnackbarDuration.Short
//////                    )
//////                }
////            } else {
////                scope.launch {
////                    snackbarHostState.showSnackbar(
////                        message = inputCommand,
////                        duration = SnackbarDuration.Short
////                    )
////                }
////            }
////        }
//    }
//
//    if (showInstallOnWatchDialog) {
//        UniversalDialog(
//            onConfirm = {
//                viewModel.performWearOperations("install")
//                showInstallOnWatchDialog = false
//            },
//            onDismiss = { showInstallOnWatchDialog = false },
//            title = "Light Signals on the watch",
//            message = "The app isn't installed on connected watch. Do you want to install it?",
//            dismissText = "Cancel",
//            confirmText = "OK"
//        )
//    }
//
//    val activity = context as ComponentActivity
//    // Handle transparent system bars
////    LaunchedEffect(Unit) {
////        // Set system bars to transparent (works on all Android versions >= 21)
////        val window = activity.window
////        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
////        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
////            // For Android 11 and higher, we can use this API
////            insetsController.isAppearanceLightStatusBars = true  // Make status bar icons dark
////            window.statusBarColor = Color.Transparent.toArgb()  // Make the status bar transparent
////            window.navigationBarColor = Color.Transparent.toArgb()  // Make the navigation bar transparent
////        } else {
////            // For Android versions below 11
////            window.statusBarColor = Color.Transparent.toArgb()
////            window.navigationBarColor = Color.Transparent.toArgb()
////        }
////    }
////
////    fun setStatusBarColor(window: Window, color: Int) {
////        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) { // Android 15+
////            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
////                val statusBarInsets = insets.getInsets()
////                view.setBackgroundColor(color)
////
////                // Adjust padding to avoid overlap
////                view.setPadding(0, statusBarInsets.top, 0, 0)
////                insets
////            }
////        } else {
////            // For Android 14 and below
////            window.statusBarColor = color
////        }
////    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Light Signals", color = Color.White) },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = Color.Transparent,  // no background
//                    scrolledContainerColor = Color.Transparent
//                ),
//                // remove the default shadow
//                scrollBehavior = null,
////                elevation = 0.dp
//                actions = {
//                    var expanded by remember { mutableStateOf(false) }
//
//                    // Icon button to open the menu
//                    IconButton(onClick = { expanded = true }) {
//                        Icon(
//                            imageVector = Icons.Default.MoreVert,
//                            contentDescription = "Menu",
//                            tint = Color.White
//                        )
//                    }
//                    // Dropdown menu for menu items
//                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
//                        //User account menu item
//                        DropdownMenuItem(
//                            text = { Text("Keep app in background") },
//                            leadingIcon = {
//                                Icon(
//                                    Icons.Outlined.AllInclusive,
//                                    contentDescription = "Infinity icon"
//                                )
//                            },
//                            onClick = { expanded = false }
//                        )
//                        DropdownMenuItem(
//                            text = { Text("Synchronize with watch") },
//                            leadingIcon = {
//                                Icon(
//                                    Icons.Outlined.Watch,
//                                    contentDescription = "Watch icon"
//                                )
//                            },
//                            onClick = {
//                                showConnectToWatchDialog = true
//                                expanded = false
//                            }
//                        )
//                    }
//                }
//            )
//        },
////        containerColor = Color.Transparent,   // Scaffold’s own background
//        contentWindowInsets = WindowInsets(0),
////        contentColor = MaterialTheme.colorScheme.onBackground
//
//        snackbarHost = {
//            Box(modifier = Modifier.fillMaxSize()) {
//                SnackbarHost(
//                    hostState = snackbarHostState,
//                    snackbar = { snackbarData ->
//                        Snackbar(
//                            snackbarData = snackbarData,
//                            shape = RoundedCornerShape(12.dp),
//                            containerColor = MaterialTheme.colorScheme.secondary,
//                            contentColor = MaterialTheme.colorScheme.onSecondary, // Set desired text color here
//                        )
//                    },
//                    modifier = Modifier
//                        .align(Alignment.TopCenter)
//                        .padding(top = 80.dp, start = 16.dp, end = 16.dp)
//                )
//            }
//        },
//    )
//    { innerPadding ->
//
//        // Use BoxWithConstraints to read maxWidth and decide sizes
//        BoxWithConstraints(
//            modifier = Modifier
//                .fillMaxSize()
//                .paint(
//                    painter = painterResource(R.drawable.street_2_blur),
//                    contentScale = ContentScale.Crop
//                )
//                .systemBarsPadding()
//                .padding(innerPadding)
//        ) {
//            val configuration = LocalConfiguration.current
//            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
//
//            // Decide button size by available width. Tune breakpoints as you need.
//            val colorButtonSize: Dp = when {
//                maxWidth >= 840.dp -> 100.dp
//                maxWidth >= 600.dp -> 80.dp
//                maxWidth >= 360.dp -> 56.dp
//                else -> 30.dp
//            }
//            Column(
//                Modifier
//                    .fillMaxSize()
//                    .paint(
//                        painter = painterResource(R.drawable.street_2_blur),
//                        contentScale = ContentScale.Crop
//                    )
//                    .systemBarsPadding()      // make sure content avoids system bars
//                    .padding(innerPadding)    // account for Scaffold’s topBar inset
//            ) {
//                // --- FULL-WIDTH TOP SLIDER ---
//                ColorPickerSlider(
//                    progress = sliderPositionColor,
//                    onProgressChanged = { newValue ->
//                        sliderPositionColor =
//                            newValue  // update local state continuously during dragging
//                    },
//                    onValueChangeFinished = {
//                        // Update the persisted state only when the lightsignals finishes sliding
//                        viewModel.updateColorProgress(sliderPositionColor)
//                    }
//                )
//
//                Box(
//                    Modifier
//                        .weight(1f)
//                        .fillMaxWidth()
//                )
//                {
//                    // Left toggles…
//                    Column(
//                        modifier = Modifier
//                            .padding(start = 10.dp)
//                            .wrapContentWidth()
//                            .align(Alignment.BottomStart),     // bottom–left corner
//                        verticalArrangement = Arrangement.SpaceBetween,
//                        horizontalAlignment = Alignment.CenterHorizontally,
//
//                        ) {
//                        Spacer(Modifier.weight(6f))
//
//                        ToggleButton(
//                            checked = checkedFlash,
//                            onCheckedChange = {
//                                Log.d("MY_LOG", "MainScreen  checkedFlash_0 = $checkedFlash")
//                                if (!checkedFlash) (activity as PhoneMainActivity).tryEnableFlash()
////                            if (!checkedFlash) viewModel.select(Active.FLASH)
//                                else viewModel.switchFlash(false)
//                            },
//                            modifier = Modifier
//                                .height(40.dp)
//                                .width(80.dp),
//                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
//                            colors = ToggleButtonDefaults.toggleButtonColors(
//                                containerColor = Color(0xffff6f00),
//                                contentColor = Color.White,
//                                checkedContainerColor = Color(0xFFc43e00),
//                                checkedContentColor = Color.White
//                            ),
//                        ) {
//                            Log.d(
//                                "MY_LOG",
//                                "MainScreen  flashOn (ICON) = $flashOn ; checkedFlash = $checkedFlash"
//                            )
//                            Icon(
//                                if (!checkedFlash) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
//                                contentDescription = "Localized description",
//                                modifier = Modifier
//                                    .size(50.dp)
//                                    .rotate(90f)
//                            )
//                        }
//                        Spacer(Modifier.height(50.dp))
//
//                        val size = ButtonDefaults.LargeContainerHeight
//
//                        ToggleButton(
//                            checked = checkedSOS,
//                            onCheckedChange = {
//                                Log.d(
//                                    "MY_LOG",
//                                    "MainScreen SOS ToggleButton checkedSOS = $checkedSOS"
//                                )
//                                viewModel.toggleSos(!checkedSOS)
//                            },
//                            colors = ToggleButtonDefaults.toggleButtonColors(
//                                containerColor = Color(0xffff6f00),
//                                contentColor = Color.White,
//                                checkedContainerColor = Color(0xFFc43e00),
//                                checkedContentColor = Color.White
//                            ),
//                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
//                            modifier = Modifier
//                                .height(40.dp)
//                                .width(80.dp),
////                    shapes = ToggleButtonDefaults.shapesFor(size),
////                    shapes          = ToggleButtonShapes(
////                        shape = RoundedCornerShape(16.dp),
////                        checkedShape   = RoundedCornerShape(16.dp),
////                        pressedShape   = RoundedCornerShape(16.dp)
////                    ),
//                        ) {
//                            Log.d("MY_LOG", "MainScreen  sosOn_1 = $sosOn")
//                            Icon(
//                                if (checkedSOS) Icons.Filled.Sos else Icons.Filled.Sos,
//                                contentDescription = "Localized description",
////                        modifier = Modifier.size(ButtonDefaults.IconSize)
//                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(size))
////                        modifier = Modifier.size(40.dp)
//                            )
////                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
//                        }
//                        Spacer(Modifier.weight(1f))
//                    }
//                    // Middle part: the three color buttons.
//                    Column(
//                        modifier = Modifier
//                            .align(Alignment.Center)
//                            .padding(16.dp),
//                        horizontalAlignment = Alignment.CenterHorizontally,
////                    verticalArrangement = Arrangement.spacedBy(16.dp)
//                        verticalArrangement = Arrangement.Center
//                    ) {
//
//                        // Middle part: the three color buttons.
//                        Box(
//                            modifier = Modifier.padding(16.dp),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            if (isLandscape) {
//                                Row(
//                                    verticalAlignment = Alignment.CenterVertically,
//                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
//                                    modifier = Modifier.wrapContentWidth()
//                                ) {
//                                    // Using Modifier.size to control ColorButton size
//                                    // Top button: Always Red.
//                                    ColorButton(
//                                        sosOn,
//                                        flashOn,
//                                        Color.Red,
//                                        activeButton == Active.RED,
//                                        blinkOn,
//                                        colorBlinkActive,
//                                        size = colorButtonSize,
//                                        onClick = { viewModel.select(Active.RED) }
//                                    )
//                                    Spacer(modifier = Modifier.height(16.dp))
//                                    // Middle button: Uses the lightsignals-selected color.
//                                    ColorButton(
//                                        sosOn,
//                                        flashOn,
//                                        liveMiddleColor,
//                                        activeButton == Active.MIDDLE,
//                                        blinkOn,
//                                        colorBlinkActive,
//                                        size = colorButtonSize,
//                                        onClick = { viewModel.select(Active.MIDDLE) }
//                                    )
//                                    Spacer(modifier = Modifier.height(16.dp))
//                                    // Bottom button: Always Green.
//                                    ColorButton(
//                                        sosOn,
//                                        flashOn,
//                                        Color.Green,
//                                        activeButton == Active.GREEN,
//                                        blinkOn,
//                                        colorBlinkActive,
//                                        size = colorButtonSize,
//                                        onClick = { viewModel.select(Active.GREEN) }
//                                    )
//                                }
//                            } else {
//                                Column(
//                                    horizontalAlignment = Alignment.CenterHorizontally,
//                                    verticalArrangement = Arrangement.spacedBy(16.dp),
//                                    modifier = Modifier.wrapContentHeight()
//                                ) {
//                                    // Top button: Always Red.
//                                    ColorButton(
//                                        sosOn,
//                                        flashOn,
//                                        Color.Red,
//                                        activeButton == Active.RED,
//                                        blinkOn,
//                                        colorBlinkActive,
//                                        size = colorButtonSize,
//                                        onClick = { viewModel.select(Active.RED) }
//                                    )
//                                    Spacer(modifier = Modifier.height(16.dp))
//                                    // Middle button: Uses the lightsignals-selected color.
//                                    ColorButton(
//                                        sosOn,
//                                        flashOn,
//                                        liveMiddleColor,
//                                        activeButton == Active.MIDDLE,
//                                        blinkOn,
//                                        colorBlinkActive,
//                                        size = colorButtonSize,
//                                        onClick = { viewModel.select(Active.MIDDLE) }
//                                    )
//                                    Spacer(modifier = Modifier.height(16.dp))
//                                    // Bottom button: Always Green.
//                                    ColorButton(
//                                        sosOn,
//                                        flashOn,
//                                        Color.Green,
//                                        activeButton == Active.GREEN,
//                                        blinkOn,
//                                        colorBlinkActive,
//                                        size = colorButtonSize,
//                                        onClick = { viewModel.select(Active.GREEN) }
//                                    )
//                                }
//                            }
//                        }
//                    }
//
//                    // Right toggles…
//                    Column(
//                        modifier = Modifier
//                            .padding(end = 10.dp)
//                            .wrapContentWidth()
//                            .align(Alignment.BottomEnd),    // bottom–right corner
//                        verticalArrangement = Arrangement.SpaceBetween,
////                    verticalArrangement = Arrangement.Top,
//                        horizontalAlignment = Alignment.End
//                    ) {
//                        Spacer(Modifier.weight(6f))
//                        //Sound button with sound menu
//                        PhoneSoundMenu()
//
//                        Spacer(Modifier.height(30.dp))
//                        //Blink button
//                        ToggleButton(
//                            checked = checkedBlink,
//                            onCheckedChange = {
//                                Log.d(
//                                    "MY_LOG",
//                                    "MainScreen ToggleButton checkedBlink = $checkedBlink ; blinkOn= $blinkOn"
//                                )
//                                if (!sosOn) {
//                                    checkedBlink
//                                }
//                                if (checkedBlink) {
//                                    viewModel.toggleControl(false)
//                                } else {
//                                    viewModel.toggleControl(true)
//                                }
//                            },
//                            colors = ToggleButtonDefaults.toggleButtonColors(
//                                containerColor = Color(0xffff6f00),
//                                contentColor = Color.White,
//                                checkedContainerColor = Color(0xFFc43e00),
//                                checkedContentColor = Color.White
//                            ),
//                            modifier = Modifier
//                                .size(60.dp)
//                                .clip(CircleShape) // Makes the button round
//                        ) {
//                            Log.d(
//                                "MY_LOG",
//                                "MainScreen checkedBlink (ICON): $checkedBlink ; blinkOn (ICON): $blinkOn"
//                            )
//                            Icon(
//                                if (checkedBlink) Icons.Filled.Pause else Icons.Filled.PlayArrow,
//                                contentDescription = "Localized description",
//                                modifier = Modifier.size(30.dp)
//                            )
//                        }
//
//                        Spacer(Modifier.weight(1f))
//                    }
//                }
//
//                val interactionSource: MutableInteractionSource =
//                    remember { MutableInteractionSource() }
//                // Lower picker for blinking frequency.
//                Slider(
//                    value = 100 + 1000 - sliderPositionFrequency,
//                    onValueChange = { newValue ->
//                        sliderPositionFrequency =
//                            100 + 1000 - newValue    //update local state continuously during dragging
//                    },
//                    interactionSource = interactionSource,
//                    valueRange = 100f..1000f,
//                    steps = 8,
//                    modifier = Modifier
//                        .padding(start = 10.dp, end = 10.dp)
//                        .semantics { contentDescription = "Localized Description" },
//                    colors = SliderDefaults.colors(
//                        thumbColor = Color.Cyan,
//                        activeTickColor = Color.Green
//                    ),
//                    onValueChangeFinished = {
//                        // Update the persisted state only when the lightsignals finishes sliding
//                        viewModel.updateFrequencyProgress(sliderPositionFrequency)
//                    },
//                    thumb = {
//                        Label(
//                            label = {
//                                // The little “tooltip” box above the thumb
//                                Box(
//                                    modifier = Modifier
//                                        .background(
//                                            color = MaterialTheme.colorScheme.surfaceVariant,
//                                            shape = RoundedCornerShape(4.dp)
//                                        )
//                                        .padding(horizontal = 6.dp, vertical = 2.dp)
//                                )
//                                {
//                                    Text(
////                                text = "%.0f".format(localFrequencyProgress) + " ms",
//                                        text = "${sliderPositionFrequency.roundToInt()} ms",
//                                        style = MaterialTheme.typography.bodyMedium
//                                    )
//                                }
//                            },
//                            interactionSource = interactionSource,
//                        ) {
//                            // The actual thumb content; any Icon/Box here
//                            Box(
//                                modifier = Modifier
//                                    .clip(CircleShape)
//                                    .size(35.dp)
//                                    .background(
//                                        Brush.horizontalGradient(
//                                            listOf(
//                                                Color(0xffffc27e),
//                                                Color(0xffed6100)
//                                            )
//                                        ), shape = CircleShape
//                                    )
//                                    .border(1.dp, color = Color.White, shape = CircleShape)
//                            )
//                        }
//                    },
//                    track = { sliderState ->
//                        Box(
//                            Modifier
//                                .fillMaxWidth()
//                                .height(15.dp)
//                                .background(
//                                    Brush.horizontalGradient(
//                                        listOf(
//                                            Color(0XFFf9f9f9),
//                                            Color(0xFF545454)
//                                        )
//                                    ),
//                                    shape = RoundedCornerShape(10.dp)
//                                )
//                        )
//                    },
//                )
//            }
//        }
//    }
//}
//
//
