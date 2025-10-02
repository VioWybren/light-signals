package nodomain.greensnowapps.lightsignals.ui.screens

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.APP_FAILED_TO_LAUNCH_ON_WATCH
import nodomain.greensnowapps.lightsignals.APP_IS_NOT_INSTALLED_ON_WATCH
import nodomain.greensnowapps.lightsignals.APP_LAUNCHED_ON_WATCH
import nodomain.greensnowapps.lightsignals.COMMAND_INSTALL_ON_WATCH
import nodomain.greensnowapps.lightsignals.COMMAND_START_ON_WATCH
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_FAILED
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_STARTED
import nodomain.greensnowapps.lightsignals.NO_CONNECTED_WATCHES
import nodomain.greensnowapps.lightsignals.PhoneMainActivity
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.WEARABLE_API_UNAVAILABLE
import nodomain.greensnowapps.lightsignals.ui.elements.PhoneBlinkButton
import nodomain.greensnowapps.lightsignals.ui.elements.PhoneColorButton
import nodomain.greensnowapps.lightsignals.ui.elements.PhoneColorPickerSlider
import nodomain.greensnowapps.lightsignals.ui.elements.PhoneFlashButton
import nodomain.greensnowapps.lightsignals.ui.elements.PhoneSosButton
import nodomain.greensnowapps.lightsignals.ui.elements.PhoneSoundMenuButton
import nodomain.greensnowapps.lightsignals.ui.elements.UniversalDialog
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneTorchViewModel
import nodomain.greensnowapps.lightsignals.utils.PhoneUtils
import kotlin.math.roundToInt

private const val TAG = "PhoneMainScreen"

private const val MIN_TIME_MS = 100
private const val MAX_TIME_MS = 1000
private const val MIN_SLIDER_PROGRESS = 100f
private const val MAX_SLIDER_PROGRESS = 1000f


// Main screen Composable that combines the color button area and the pickers.
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
//    ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhoneMainScreen(
    viewModel: PhoneMainViewModel,
    onNext: () -> Unit
) {

    val phoneTorchViewModel: PhoneTorchViewModel = hiltViewModel()
    val hasTorch by phoneTorchViewModel.hasTorch.collectAsState()

//    // Track the two permissions
//    var cameraGranted by remember { mutableStateOf(false) }
//    var notifGranted by remember { mutableStateOf(false) }
//
//
//    // Now on top, request both permissions in a bundle:
//    BundledPermissionRequester(
//        request = !cameraGranted || !notifGranted
//    ) { results ->
//        cameraGranted = results[Manifest.permission.CAMERA] == true
//        notifGranted = results[Manifest.permission.POST_NOTIFICATIONS] == true
//    }

//    var ready by remember { mutableStateOf(false) }
//
//    if (!ready) {
//        BundledPermissionRequester { ready = true }
////        return
//    }

//    var cameraReady by remember { mutableStateOf(false) }
//    var notificationsReady by remember { mutableStateOf(false) }
//
//    // Kick off the permission flow once
//    // Request the camera permission first
//    if (!cameraReady) {
//        FlashPermissionRequest { cameraReady = true }
//        return
//    }

    // Once camera’s granted, request notification permission
//    if (!notificationsReady) {
//        NotificationPermissionRequest { notificationsReady = true }
//        return
//    }

    val context = LocalContext.current

    // The progress as read from DataStore (persisted state)
    val persistedFrequencyProgress by viewModel.progressFrequency.collectAsState()
    // Local slider state used during dragging
    var sliderPositionFrequency by remember { mutableFloatStateOf(persistedFrequencyProgress) }

    // Compute blink delay from frequency progress (e.g., direct mapping)
    val blinkDelay = sliderPositionFrequency.toLong()

    // Keep local slider value in sync when persisted progress changes externally
    LaunchedEffect(persistedFrequencyProgress) {
        sliderPositionFrequency = persistedFrequencyProgress
    }

    LaunchedEffect(blinkDelay) {
        viewModel.updateBlinkDelay(blinkDelay)
    }

    // The progress as read from DataStore (persisted state)
    val persistedColorProgress by viewModel.progressColor.collectAsState()
    // Local slider state used during dragging
    var sliderPositionColor by remember { mutableFloatStateOf(persistedColorProgress) }
    // Keep local slider value in sync when persisted progress changes externally
    LaunchedEffect(persistedColorProgress) {
        sliderPositionColor = persistedColorProgress
    }
    // **Compute the live color** on every sliderPositionColor change
    val liveMiddleColor = remember(sliderPositionColor) {
        PhoneUtils.computeSpecialColor(sliderPositionColor.toInt())
    }

    val activeButton by viewModel.activeButton.collectAsState()
//    val uiActiveButton by viewModel.uiActiveButton.collectAsState( initial = Active.RED)
    Log.d(TAG, "!! active = $activeButton")
    val flashOn by viewModel.flashOn.collectAsState()
    Log.d(TAG, "!! flashOn = $flashOn")
    val blinkOn by viewModel.blinkOn.collectAsState()
    Log.d(TAG, "!!  blinkOn = $blinkOn")
    val colorBlinkActive by viewModel.colorBlinkActive.collectAsState()
    Log.d(TAG, "!!  colorBlinkActive  = $colorBlinkActive")
    val sosOn by viewModel.sosOn.collectAsState()
    Log.d(TAG, "!!  sosOn = $sosOn")
    val controlMessage by viewModel.controlMessageFlow.collectAsState("")
    Log.d(TAG, "!!  controlMessage = $controlMessage")
    val isLaunchingOnWear by viewModel.isLaunchingOnWear.collectAsState()
    Log.d(TAG, "!!  isLaunchingOnPhone = $isLaunchingOnWear")
    Log.d(TAG, "!!  -------")

    var checkedFlash by remember { mutableStateOf(flashOn) }
    LaunchedEffect(flashOn) { checkedFlash = flashOn }

    var checkedBlink by remember { mutableStateOf(blinkOn && !sosOn) }
    LaunchedEffect(blinkOn, sosOn) {
        checkedBlink = when {
            sosOn -> false
            !sosOn -> blinkOn
            else -> false
        }
    }
    var checkedSOS by remember { mutableStateOf(sosOn) }
    LaunchedEffect(sosOn) {
        checkedSOS = sosOn
    }

    // State to hold the dialog message and control visibility
    var showConnectToWatchDialog by rememberSaveable { mutableStateOf(false) }
    // State to hold the dialog message and control visibility
    var showWearApiNotAvailableDialog by rememberSaveable { mutableStateOf(false) }

    if (showConnectToWatchDialog) {
        UniversalDialog(
            onConfirm = {
//                if (isWearApiAvailable == true) {
//                    viewModel.performWearOperations("start")
                viewModel.startWearOperations(COMMAND_START_ON_WATCH)
//                } else {
//                    showWearApiNotAvailableDialog = true
//                }
                showConnectToWatchDialog = false
            },
            onDismiss = { showConnectToWatchDialog = false },
            title = stringResource(R.string.dialog_title_connect_to_watch),
            message = stringResource(R.string.dialog_message_connect_to_watch),
            dismissText = stringResource(R.string._cancel),
            confirmText = stringResource(R.string._ok),
        )
    }

    if (showWearApiNotAvailableDialog) {
        UniversalDialog(
            onConfirm = { showWearApiNotAvailableDialog = false },
            title = stringResource(R.string.dialog_title_wear_api_unavailable),
            message = stringResource(R.string.dialog_message_wear_api_unavailable),
            confirmText = stringResource(R.string._ok),
        )
    }

    // State to hold the dialog message and control visibility
    var showInstallOnWatchDialog by rememberSaveable { mutableStateOf(false) }

    // A single snackbarHostState for displaying update results
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showSnack(msg: String) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(controlMessage) {
        Log.d(TAG, "commandState: $controlMessage")
        val inputCommand = Regex("[0-9]").replace(controlMessage, "")
        if (inputCommand.isNotEmpty()) {
            when (inputCommand) {
                APP_IS_NOT_INSTALLED_ON_WATCH -> {
                    showInstallOnWatchDialog = true
                }
                INSTALLATION_ON_WATCH_STARTED -> showSnack(context.getString(R.string.snackbar_app_installed_on_watch_success))
                INSTALLATION_ON_WATCH_FAILED -> showSnack(context.getString(R.string.snackbar_installation_on_watch_failed))
                APP_FAILED_TO_LAUNCH_ON_WATCH -> showSnack(context.getString(R.string.snackbar_app_failed_to_launch_on_watch))
                APP_LAUNCHED_ON_WATCH -> showSnack(context.getString(R.string.snackbar_app_launched_on_watch))
                WEARABLE_API_UNAVAILABLE -> showWearApiNotAvailableDialog = true
                NO_CONNECTED_WATCHES -> showSnack(context.getString(R.string.snackbar_no_connected_watches))
                else -> showSnack(inputCommand)
            }
        }
    }

    if (showInstallOnWatchDialog) {
        UniversalDialog(
            onConfirm = {
                viewModel.startWearOperations(COMMAND_INSTALL_ON_WATCH)
                showInstallOnWatchDialog = false
            },
            onDismiss = { showInstallOnWatchDialog = false },
            title = stringResource(R.string.dialog_title_install_on_watch),
            message = stringResource(R.string.dialog_message_install_on_watch),
            dismissText = stringResource(R.string._cancel),
            confirmText = stringResource(R.string._ok)
        )
    }

    val activity = context as ComponentActivity
//    val size = ButtonDefaults.MediumContainerHeight
//    val iconSize = IconButtonDefaults.LargeContainerHeight
    val iconSize = 30.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,  // no background
                    scrolledContainerColor = Color.Transparent
                ),
                expandedHeight = 50.dp,
//                modifier = Modifier.background(Color.Red), //for test
                // remove the default shadow
                scrollBehavior = null,
//                elevation = 0.dp
                actions = {
                    var expanded by remember { mutableStateOf(false) }

                    // Icon button to open the menu
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                    }
                    // Dropdown menu for menu items
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        //User account menu item
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_item_synchronize_with_watch)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Watch,
                                    contentDescription = "Watch icon"
                                )
                            },
                            onClick = {
                                showConnectToWatchDialog = true
                                expanded = false
                            }
                        )
                    }
                }
            )
        },
//        containerColor = Color.Transparent,   // Scaffold’s own background
        contentWindowInsets = WindowInsets(0),
//        contentColor = MaterialTheme.colorScheme.onBackground

        snackbarHost = {
            Box(modifier = Modifier.fillMaxSize()) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = { snackbarData ->
                        Snackbar(
                            snackbarData = snackbarData,
                            shape = RoundedCornerShape(12.dp),
//                            containerColor = MaterialTheme.colorScheme.secondary,
//                            contentColor = MaterialTheme.colorScheme.onSecondary, // Set desired text color here
                            containerColor = Color.Black,
                            contentColor = Color.White, // Set desired text color here
                        )
                    },
                    modifier = Modifier
//                        .align(Alignment.TopCenter)
//                        .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp, start = 10.dp, end = 10.dp)
                )
            }
        },
    )
    { innerPadding ->
        // Use BoxWithConstraints to read maxWidth maxHeight, and decide sizes
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .paint(
                    painter = painterResource(R.drawable.street_2_blur_1),
                    contentScale = ContentScale.Crop
                )
                .navigationBarsPadding()
                .padding(innerPadding)
        ) {

            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
//            val isLandscape = maxWidth > maxHeight
            val reference = min(maxWidth, maxHeight)   // <--- use the smaller side
            Log.d(TAG, "!! maxWidth: $maxWidth maxHeigh: $maxHeight reference: $reference")

            var colorButtonSize = 160.dp
            var toggleBtnHeight = 40.dp
            var toggleBtnWidth = 80.dp
            var roundToggleBtnSize = 60.dp
            var roundToggleBtnIconSize = 30.dp
            var leftColumnPaddingStart = 10.dp
            var rightColumnPaddingEnd = 10.dp

            when {
                reference >= 700.dp -> {
                    colorButtonSize = 300.dp
                    toggleBtnWidth = 100.dp
                    toggleBtnHeight = 50.dp
                    roundToggleBtnSize = 70.dp
                    roundToggleBtnIconSize = 30.dp
                    leftColumnPaddingStart = 20.dp
                    rightColumnPaddingEnd = 20.dp
                }
                reference >= 600.dp -> {
                    colorButtonSize = 200.dp
                    toggleBtnWidth = 100.dp
                    toggleBtnHeight = 50.dp
                    roundToggleBtnSize = 70.dp
                    roundToggleBtnIconSize = 30.dp
                    leftColumnPaddingStart = 20.dp
                    rightColumnPaddingEnd = 20.dp
                }
//                reference >= 360.dp -> {
//                    colorButtonSize = 160.dp
//                    toggleBtnWidth = 80.dp
//                    toggleBtnHeight = 40.dp
//                    roundToggleBtnSize = 55.dp
//                    roundToggleBtnIconSize = 30.dp
//                    leftColumnPaddingStart = 10.dp
//                    rightColumnPaddingEnd = 10.dp
//                }
                reference > 360.dp -> {
                    colorButtonSize = 160.dp
                    toggleBtnWidth = 80.dp
                    toggleBtnHeight = 40.dp
                    roundToggleBtnSize = 55.dp
                    roundToggleBtnIconSize = 30.dp
                    leftColumnPaddingStart = 10.dp
                    rightColumnPaddingEnd = 10.dp
                }
                reference <= 360.dp -> {
                    colorButtonSize = 120.dp
                    toggleBtnWidth = 80.dp
                    toggleBtnHeight = 40.dp
                    roundToggleBtnSize = 55.dp
                    roundToggleBtnIconSize = 30.dp
                    leftColumnPaddingStart = 10.dp
                    rightColumnPaddingEnd = 10.dp
                }
                else -> 160.dp
            }
            Log.d(TAG, "!! colorButtonSize: $colorButtonSize")

            Column(
                modifier = Modifier
                    //                    .background(Color.Yellow) //for test
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                // --- FULL-WIDTH TOP SLIDER ---
                PhoneColorPickerSlider(
                    progress = sliderPositionColor,
                    onProgressChanged = { newValue ->
                        sliderPositionColor = newValue  // update local state continuously during dragging
                    },
                    onValueChangeFinished = {
                        // Update the persisted state only when the lightsignals finishes sliding
                        viewModel.updateColorProgress(sliderPositionColor)
                    }
                )

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                {
                    // Left toggles…
                    Column(
                        modifier = Modifier
                            .padding(start = leftColumnPaddingStart)
                            .wrapContentWidth()
//                            .background(Color.Green) //for test
                            .align(Alignment.BottomStart),     // bottom–left corner
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(7f))
//
                        if (hasTorch == true) {
                            // Torch toggle
                            PhoneFlashButton(toggleBtnWidth, toggleBtnHeight, checkedFlash, viewModel, activity as PhoneMainActivity, iconSize)
                            // spacer
                            Spacer(Modifier.weight(1f))
                        }
                        // SOS toggle
                        PhoneSosButton(toggleBtnWidth, toggleBtnHeight, checkedSOS, viewModel, iconSize)
                        Spacer(Modifier.weight(1f))
                    }
                    // Middle part: the three color buttons.
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
//                            .background(Color.Yellow) //for test
                            .padding(0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Middle part: the three color buttons.
                        Box(
                            modifier = Modifier
                                .padding(0.dp),
//                                .background(Color.LightGray), //for test
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLandscape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    // Using Modifier.size to control ColorButton size
                                    // Top button: Always Red.
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        Color.Red,
                                        activeButton == Active.RED,
                                        blinkOn,
                                        colorBlinkActive,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.RED) }
                                    )
                                    // Middle button: Uses the lightsignals-selected color.
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        liveMiddleColor,
                                        activeButton == Active.MIDDLE,
                                        blinkOn,
                                        colorBlinkActive,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.MIDDLE) }
                                    )
                                    // Bottom button: Always Green.
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        Color.Green,
                                        activeButton == Active.GREEN,
                                        blinkOn,
                                        colorBlinkActive,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.GREEN) }
                                    )
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.wrapContentHeight()
                                ) {
                                    // Top button: Always Red.
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        Color.Red,
                                        activeButton == Active.RED,
                                        blinkOn,
                                        colorBlinkActive,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.RED) }
                                    )
                                    // Middle button: Uses the lightsignals-selected color.
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        liveMiddleColor,
                                        activeButton == Active.MIDDLE,
                                        blinkOn,
                                        colorBlinkActive,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.MIDDLE) }
                                    )
                                    // Bottom button: Always Green.
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        Color.Green,
                                        activeButton == Active.GREEN,
                                        blinkOn,
                                        colorBlinkActive,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.GREEN) }
                                    )
                                }
                            }
                        }
                    }

                    // Right toggles…
                    Column(
                        modifier = Modifier
                            .padding(end = rightColumnPaddingEnd)
//                            .safeContentPadding()
                            .wrapContentWidth()
//                            .background(Color.Green)// for test
                            .align(Alignment.BottomEnd),    // bottom–right corner
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Spacer(Modifier.weight(7f))
                        //Sound button with sound menu
                        PhoneSoundMenuButton(toggleBtnWidth, toggleBtnHeight, iconSize)
                        //spacer
                        Spacer(Modifier.weight(1f))
                        //Blink button
                        PhoneBlinkButton(toggleBtnWidth, toggleBtnHeight, checkedBlink, sosOn, viewModel, iconSize)
                        //spacer
                        Spacer(Modifier.weight(1f))
                    }
                }

                val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
                // Lower picker for blinking frequency.
                Slider(
                    value = MIN_TIME_MS + MAX_TIME_MS - sliderPositionFrequency,
                    onValueChange = { newValue ->
                        sliderPositionFrequency =
                            MIN_TIME_MS + MAX_TIME_MS - newValue    //update local state continuously during dragging
                    },
                    interactionSource = interactionSource,
                    valueRange = MIN_SLIDER_PROGRESS..MAX_SLIDER_PROGRESS,
                    steps = 8,
                    modifier = Modifier
//                        .background(Color.Blue) //test
//                        .padding(start = 10.dp, end = 10.dp)
                        .semantics { contentDescription = "Localized Description" },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTickColor = Color.Green
                    ),
                    onValueChangeFinished = {
                        // Update the persisted state only when the lightsignals finishes sliding
                        viewModel.updateFrequencyProgress(sliderPositionFrequency)
                    },
                    thumb = {
                        Label(
                            label = {
                                // The little “tooltip” box above the thumb
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                {
                                    Text(
                                        text = "${sliderPositionFrequency.roundToInt()} ms",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            },
                            interactionSource = interactionSource,
                        ) {
                            // The actual thumb content; any Icon/Box here
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .size(35.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xffffc27e),
                                                Color(0xffed6100)
                                            )
                                        ), shape = CircleShape
                                    )
                                    .border(1.dp, color = Color.White, shape = CircleShape)
                            )
                        }
                    },
                    track = { sliderState ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(15.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0XFFf9f9f9),
                                            Color(0xFF545454)
                                        )
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        )
                    },
                )
            }

            if (isLaunchingOnWear) {
//                Box(
//                    modifier = Modifier
//                        .size(160.dp, 150.dp)
//                        .background(color = Color.Black)
//                        .align(Alignment.Center)   // ← center this child only,
//                        .clip(RoundedCornerShape(size = 20.dp)),
//                    contentAlignment = Alignment.Center,
//                ) {
                Column(
                    modifier = Modifier
                        .size(160.dp, 150.dp)
                        .clip(RoundedCornerShape(size = 20.dp))
                        .background(color = Color.Black)
                        .align(Alignment.Center),  // ← center this child only,
//                            .clip(RoundedCornerShape(size = 20.dp)),

                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(40.dp), // adjust size if you like
                        color = Color.Green,
                        strokeWidth = ProgressIndicatorDefaults.CircularStrokeWidth,
                        trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                        strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.circular_indicator_text),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 5.dp, end = 5.dp)
                    )
                }
//                }
            }
        }
    }
}

