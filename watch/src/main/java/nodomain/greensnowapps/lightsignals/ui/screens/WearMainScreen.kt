package nodomain.greensnowapps.lightsignals.ui.screens

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.WearConstants
import nodomain.greensnowapps.lightsignals.WearConstants.COMMAND_INSTALL_ON_PHONE
import nodomain.greensnowapps.lightsignals.ui.elements.WearColorButton
import nodomain.greensnowapps.lightsignals.ui.elements.WearColorPicker
import nodomain.greensnowapps.lightsignals.ui.elements.WearFailureConfirmationDialog
import nodomain.greensnowapps.lightsignals.ui.elements.WearSuccessConfirmationDialog
import nodomain.greensnowapps.lightsignals.ui.elements.WearUniversalDialog
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearMainViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.WearSoundViewModel
import nodomain.greensnowapps.lightsignals.utils.WearUtils

private const val TAG = "WearMainScreen"

private const val COLOR_CHECKED_BUTTON = 0xFFc43e00
private const val COLOR_BUTTON_BORDER = 0xffff6f00

private const val MIN_TIME_MS = 100
private const val MAX_TIME_MS = 1000
private const val MIN_SLIDER_PROGRESS = 100f
private const val MAX_SLIDER_PROGRESS = 1000f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearMainScreen(
    viewModel: WearMainViewModel,
    soundViewModel: WearSoundViewModel,
    onNext: () -> Unit,
) {

    val context = LocalContext.current

    val btnSize = dimensionResource(R.dimen.btn_size)
    val columnHeight = dimensionResource(R.dimen.column_height)
    val phoneTorchBtnSpacing = dimensionResource(R.dimen.phone_torch_btn_spacing)
    val colorBtnsSliderSpacing = dimensionResource(R.dimen.color_btns_slider_spacing)
    val sliderSpacing = dimensionResource(R.dimen.slider_spacing)

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
        WearUtils.computeSpecialColor(sliderPositionColor.toInt())
    }

    val activeButton by viewModel.activeButton.collectAsState()
    Log.d(TAG, "!! active = $activeButton")
    val flashOn by viewModel.flashOn.collectAsState()
    Log.d(TAG, "!! flashOn = $flashOn")
    val blinkOn by viewModel.blinkOn.collectAsState()
    Log.d(TAG, "!!  blinkOn = $blinkOn")
    val colorBlinkActive by viewModel.colorBlinkActive.collectAsState()
    Log.d(TAG, "!!  colorBlinkActive  = $colorBlinkActive")
    val sosOn by viewModel.sosOn.collectAsState()
    Log.d(TAG, "!!  sosOn = $sosOn")
    val phoneConnection by viewModel.phoneConnection.collectAsState()
    Log.d(TAG, "!!  phoneConnection = $phoneConnection")
    val controlMessage by viewModel.controlMessageFlow.collectAsState("")
    Log.d(TAG, "!!  controlMessage = $controlMessage")
    val isPhoneAppAvailable by viewModel.isPhoneAppAvailable.collectAsState()
    Log.d(TAG, "!!  isPhoneAppAvailable = $isPhoneAppAvailable")
    val isLaunchingOnPhone by viewModel.isLaunchingOnPhone.collectAsState()
    Log.d(TAG, "!!  isLaunchingOnPhone = $isLaunchingOnPhone")
    val activeSound by viewModel.activeSound.collectAsState()
    Log.d(TAG, "!!  activeSound = $activeSound")
    Log.d(TAG, "!! *******")

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
    var showConnectToPhoneDialog by remember { mutableStateOf(false) }

    WearUniversalDialog(
        visible = showConnectToPhoneDialog,
        onConfirm = {
//            viewModel.performWearOperations("testCommandToPhone")
            viewModel.performWearOperations(COMMAND_INSTALL_ON_PHONE)
            showConnectToPhoneDialog = false
        },
        onDismiss = {
            viewModel.togglePhoneConnection(false)
            showConnectToPhoneDialog = false
        },
        title = stringResource(R.string.dialog_title_app_unavailable_on_phone),
        message = stringResource(R.string.dialog_message_app_unavailable_on_phone)
    )

    // State to hold the dialog message and control visibility
    var showInstallOnPhoneDialog by remember { mutableStateOf(false) }

    WearUniversalDialog(
        visible = showInstallOnPhoneDialog,
        onConfirm = {
            viewModel.performWearOperations(COMMAND_INSTALL_ON_PHONE)
            showInstallOnPhoneDialog = false
        },
        onDismiss = {
            viewModel.togglePhoneConnection(false)
            showInstallOnPhoneDialog = false
        },
        title = stringResource(R.string.dialog_title_install_on_phone),
        message = stringResource(R.string.dialog_message_install_on_phone)
    )

    // Success dialog state
    var showSuccess by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }

    // Failure dialog state
    var showFailure by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf("") }

    WearSuccessConfirmationDialog(
        showDialog = showSuccess,
        message = successMessage,
        onDismiss = { showSuccess = false }
    )

    WearFailureConfirmationDialog(
        showDialog = showFailure,
        message = failureMessage,
        onDismiss = { showFailure = false }
    )

    LaunchedEffect(controlMessage) {
        Log.d(TAG, "commandState: $controlMessage")
        val inputCommand = Regex("[0-9]").replace(controlMessage, "")
        if (inputCommand.isNotEmpty()) {
            when (inputCommand) {
                WearConstants.APP_IS_NOT_INSTALLED_ON_PHONE -> {
                    showInstallOnPhoneDialog = true
                }
                WearConstants.INSTALLATION_HAS_BEEN_STARTED_ON_PHONE -> {
                    successMessage = context.getString(R.string.dialog_success_installation_has_been_started_on_phone)
                    showSuccess = true
                }
                WearConstants.APP_LAUNCHED_ON_PHONE -> {
                    successMessage = context.getString(R.string.dialog_success_open_on_phone)
                    showSuccess = true
                }
                WearConstants.INSTALLATION_ON_PHONE_FAILED -> {
                    viewModel.togglePhoneConnection(false)
                    failureMessage = context.getString(R.string.dialog_fail_installation_on_phone_failed)
                    showFailure = true
                }
                WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE -> {
                    viewModel.togglePhoneConnection(false)
                    showConnectToPhoneDialog = true
                }
                WearConstants.NO_CONNECTED_PHONES -> {
                    viewModel.togglePhoneConnection(false)
                    failureMessage = context.getString(R.string.dialog_fail_no_connected_phones)
                    showFailure = true
                }
            }
        }
    }

    val focusRequester: FocusRequester = remember { FocusRequester() }

    // Grab AudioManager and the max volume for STREAM_MUSIC
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    //Track the *current* volume index in 0..maxVol
    var currentVol by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    // 4. Keep the screen on if you like
//    LaunchedEffect(Unit) {
//        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//    }


    // A small Float accumulator
    var scrollVolumeAccum by remember { mutableFloatStateOf(0f) }

    // Grab focus when this screen enters composition
    LaunchedEffect(Unit) {
//        focusRequester.requestFocus()
//        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    val showSoundSelector by soundViewModel.showSoundSelector.collectAsState()
    var selectedSound by rememberSaveable { mutableIntStateOf(activeSound) }
    var isSoundOn by remember { mutableStateOf(selectedSound != 0) }

    if (showSoundSelector) {
        Log.d(TAG, "sound showSoundSelector  = true")
        Log.d(TAG, "sound selectedSound    = $selectedSound ")
        Log.d(TAG, "sound isSoundOn   = $isSoundOn")
        Log.d(TAG, "sound *******")

        WearSoundSelectionScreen(
            onSoundSelected = { newSound ->
                selectedSound = newSound
                isSoundOn = if (newSound == 0) false else true
            },
            onHideSoundSelector = { soundViewModel.setShowSoundSelector(false) }
        )
    } else {
        Log.d(TAG, "sound showSoundSelector 0 = false")
        Log.d(TAG, "sound selectedSound 0   = $selectedSound ")
        Log.d(TAG, "sound isSoundOn 0  = $isSoundOn")
        Log.d(TAG, "sound *******")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
//                .onRotaryScrollEvent { event ->
//                    // Add the new pixels into our accumulator
//                    scrollVolumeAccum += event.verticalScrollPixels
//                    // Define how many pixels = one volume step
//                    val pixelsPerStep = 50f
//                    // While we've crossed a step boundary, bump the volume
//                    while (scrollVolumeAccum.absoluteValue >= pixelsPerStep) {
//                        // Determine direction: +1 or –1
//                        val direction = if (scrollVolumeAccum > 0) +1 else -1
//                        // Update your volume state by one step
//                        currentVol = (currentVol + direction).coerceIn(0, maxVol)
//                        // Push it to the AudioManager (no UI)
//                        audioManager.setStreamVolume(
//                            AudioManager.STREAM_MUSIC,
//                            currentVol,
//                            FLAG_SHOW_UI
//                        )
//                        // Remove one “step” worth of pixels from the accumulator
//                        scrollVolumeAccum -= direction * pixelsPerStep
//                    }
//                    true  // consume the event
//                }
//                .requestFocusOnHierarchyActive()
//                .focusRequester(focusRequester)
//                .focusable(),
        )
        {
            Column(
                modifier = Modifier
                    .height(columnHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                //Top buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    //Phone connection Button
                    IconToggleButton(
                        checked = phoneConnection,
                        onCheckedChange = {
                            Log.d(TAG, "Button phoneConnection= $phoneConnection")
                            if (isPhoneAppAvailable) {
                                if (!phoneConnection) {
                                    viewModel.performWearOperations(WearConstants.COMMAND_START_ON_PHONE)
                                }
                                viewModel.togglePhoneConnection(!phoneConnection)
                            } else {
                                showConnectToPhoneDialog = true
                            }
                        },
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape),
                        colors = IconToggleButtonDefaults.colors(
                            uncheckedContainerColor = Color.Transparent,
                            uncheckedContentColor = Color.White,
                            checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
                            checkedContentColor = Color.White
                        ),
                        border = BorderStroke(width = 2.dp, color = Color(COLOR_BUTTON_BORDER))
                    ) {
                        Icon(
                            Icons.Outlined.PhoneAndroid,
                            contentDescription = "Localized description",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(phoneTorchBtnSpacing))
                    //Torch Button
                    IconToggleButton(
                        checked = checkedFlash,
                        onCheckedChange = {
                            Log.d(TAG, " checkedFlash_0 = $checkedFlash")
                            if (phoneConnection && isPhoneAppAvailable) {
                                if (!checkedFlash) viewModel.select(WearMainViewModel.Active.FLASH)
                                else viewModel.switchFlash(false)
                            } else {
                                failureMessage = context.getString(R.string.dialog_fail_connect_app_on_phone_for_torch)
                                showFailure = true
                            }
                        },
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape),
                        colors = IconToggleButtonDefaults.colors(
                            uncheckedContainerColor = Color.Transparent,
                            uncheckedContentColor = Color.White,
                            checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
                            checkedContentColor = Color.White
                        ),
                        border = BorderStroke(width = 2.dp, color = Color(COLOR_BUTTON_BORDER))
                    ) {
                        Log.d(TAG, " flashOn (ICON) = $flashOn ; checkedFlash = $checkedFlash")
                        Icon(
                            Icons.Outlined.FlashlightOn,
                            contentDescription = "Localized description",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left button: Always Red.
                    WearColorButton(sosOn, flashOn, Color.Red, activeButton == WearMainViewModel.Active.RED, blinkOn, colorBlinkActive)
                    { viewModel.select(WearMainViewModel.Active.RED) }
                    // Middle button: Uses the lightsignals-selected color.
                    WearColorButton(sosOn, flashOn, liveMiddleColor, activeButton == WearMainViewModel.Active.MIDDLE, blinkOn, colorBlinkActive)
                    { viewModel.select(WearMainViewModel.Active.MIDDLE) }
                    // Right button: Always Green.
                    WearColorButton(sosOn, flashOn, Color.Green, activeButton == WearMainViewModel.Active.GREEN, blinkOn, colorBlinkActive)
                    { viewModel.select(WearMainViewModel.Active.GREEN) }
                }

                Spacer(modifier = Modifier.height(colorBtnsSliderSpacing))

                InlineSlider(
                    value = MIN_TIME_MS + MAX_TIME_MS - sliderPositionFrequency,
                    onValueChange = { newValue ->
                        sliderPositionFrequency =
                            MIN_TIME_MS + MAX_TIME_MS - newValue    //update local state continuously during dragging
                        viewModel.updateFrequencyProgress(sliderPositionFrequency)
                    },
                    increaseIcon = { Icon(InlineSliderDefaults.Increase, "Increase", tint = Color.White) },
                    decreaseIcon = { Icon(InlineSliderDefaults.Decrease, "Decrease", tint = Color.White) },
                    valueRange = MIN_SLIDER_PROGRESS..MAX_SLIDER_PROGRESS,
                    steps = 8,
                    segmented = true,
                    modifier = Modifier
                        .height(10.dp)
                )

                Spacer(modifier = Modifier.height(sliderSpacing))

                WearColorPicker(
                    progress = sliderPositionColor,
                    onProgressChanged = { newValue ->
                        sliderPositionColor = newValue  // update local state continuously during dragging
                    },
                    onValueChangeFinished = {
                        // Update the persisted state only when the lightsignals finishes sliding
                        viewModel.updateColorProgress(sliderPositionColor)
                    }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Three buttons along curved bottom
            CurvedLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 10.dp),
                anchor = 90f
            ) {
                curvedRow(
                    radialAlignment = CurvedAlignment.Radial.Inner,
                    angularDirection = CurvedDirection.Angular.Clockwise
                ) {
                    curvedComposable(
                        rotationLocked = true
                    ) {
                        // Blink Button
                        IconToggleButton(
                            checked = checkedBlink,
                            onCheckedChange = {
                                Log.d(TAG, "ToggleButton checkedBlink = $checkedBlink")
                                Log.d(TAG, "ToggleButton blinkOn= $blinkOn")
                                if (!sosOn) {
                                    checkedBlink
                                }
                                if (checkedBlink) {
                                    viewModel.toggleControl(false)
                                } else {
                                    viewModel.toggleControl(true)
                                }
                            },
                            modifier = Modifier
                                .size(btnSize)
                                .clip(CircleShape),
                            colors = IconToggleButtonDefaults.colors(
                                uncheckedContainerColor = Color.Transparent,
                                uncheckedContentColor = Color.White,
                                checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
                                checkedContentColor = Color.White
                            ),
                            border = BorderStroke(width = 2.dp, color = Color(COLOR_BUTTON_BORDER)),
                        ) {
                            Icon(
                                if (checkedBlink) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Localized description",
                                tint = Color.White
                            )
                        }
                    }

                    curvedComposable(
                        rotationLocked = true
                    ) {
                        //Sound Button
                        IconToggleButton(
                            checked = isSoundOn,
                            onCheckedChange = {
                                Log.d(TAG, "sound isSoundOn 1 = $isSoundOn")
                                soundViewModel.setShowSoundSelector(true)
                            },
                            colors = IconToggleButtonDefaults.colors(
                                uncheckedContainerColor = Color.Transparent,
                                uncheckedContentColor = Color.White,
                                checkedContainerColor = Color.Transparent,
                                checkedContentColor = Color.White
                            ),
                            border = BorderStroke(width = 2.dp, color = Color(COLOR_BUTTON_BORDER)),
                            modifier = Modifier
                                .size(btnSize)
                                .clip(CircleShape) // Makes the button round
                        ) {
                            Log.d(TAG, "sound isSoundOn 2 = $isSoundOn")
                            Icon(
                                if (isSoundOn) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                                contentDescription = "Localized description",
                                tint = Color.White
                            )
                        }
                    }
                    curvedComposable(
                        rotationLocked = true
                    )
                    {
                        //SOS button
                        IconToggleButton(
                            checked = checkedSOS,
                            onCheckedChange = {
                                Log.d(TAG, "SOS ToggleButton checkedSOS = $checkedSOS")
                                viewModel.toggleSos(!checkedSOS)
                            },
                            modifier = Modifier
                                .size(btnSize)
                                .clip(CircleShape),
                            colors = IconToggleButtonDefaults.colors(
                                uncheckedContainerColor = Color.Transparent,
                                uncheckedContentColor = Color.White,
                                checkedContainerColor = Color(COLOR_CHECKED_BUTTON),
                                checkedContentColor = Color.White
                            ),
                            border = BorderStroke(width = 2.dp, color = Color(COLOR_BUTTON_BORDER)),
                        ) {
                            Icon(
                                if (checkedSOS) Icons.Filled.Sos else Icons.Filled.Sos,
                                contentDescription = "Localized description",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            if (isLaunchingOnPhone) {
                Box(
                    modifier = Modifier
                        .size(130.dp, 120.dp)
                        .clip(RoundedCornerShape(size = 10.dp))
                        .background(color = Color.Black)
                        .align(Alignment.Center),  // ← center this child only,
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(32.dp), // adjust size if you like
                            colors = ProgressIndicatorDefaults.colors(
                                indicatorColor = Color.DarkGray,
                                trackColor = Color.Green,
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.circular_indicator_text),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 5.dp, end = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

