package nodomain.greensnowapps.lightsignals.ui.screens

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

    val persistedFrequencyProgress by viewModel.progressFrequency.collectAsState()
    var sliderPositionFrequency by remember { mutableFloatStateOf(persistedFrequencyProgress) }

    val blinkDelay = sliderPositionFrequency.toLong()

    LaunchedEffect(persistedFrequencyProgress) {
        sliderPositionFrequency = persistedFrequencyProgress
    }

    LaunchedEffect(blinkDelay) {
        viewModel.updateBlinkDelay(blinkDelay)
    }

    val persistedColorProgress by viewModel.progressColor.collectAsState()
    var sliderPositionColor by remember { mutableFloatStateOf(persistedColorProgress) }

    LaunchedEffect(persistedColorProgress) {
        sliderPositionColor = persistedColorProgress
    }

    val liveMiddleColor = remember(sliderPositionColor) {
        WearUtils.computeSpecialColor(sliderPositionColor.toInt())
    }

    val activeButton by viewModel.activeButton.collectAsState()
    val flashOn by viewModel.flashOn.collectAsState()
    val blinkOn by viewModel.blinkOn.collectAsState()
    val colorBlinkActive by viewModel.colorBlinkActive.collectAsState()
    val sosOn by viewModel.sosOn.collectAsState()
    val phoneConnection by viewModel.phoneConnection.collectAsState()
    val controlMessage by viewModel.controlMessageFlow.collectAsState("")
    val isPhoneAppAvailable by viewModel.isPhoneAppAvailable.collectAsState()
    val isLaunchingOnPhone by viewModel.isLaunchingOnPhone.collectAsState()
    val activeSound by viewModel.activeSound.collectAsState()

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

    var showConnectToPhoneDialog by remember { mutableStateOf(false) }

    WearUniversalDialog(
        visible = showConnectToPhoneDialog,
        onConfirm = {
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

    var showSuccess by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }

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

    val showSoundSelector by soundViewModel.showSoundSelector.collectAsState()
    var selectedSound by rememberSaveable { mutableIntStateOf(activeSound) }
    var isSoundOn by remember { mutableStateOf(selectedSound != 0) }

    if (showSoundSelector) {

        WearSoundSelectionScreen(
            onSoundSelected = { newSound ->
                selectedSound = newSound
                isSoundOn = if (newSound == 0) false else true
            },
            onHideSoundSelector = { soundViewModel.setShowSoundSelector(false) }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        )
        {
            Column(
                modifier = Modifier
                    .height(columnHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconToggleButton(
                        checked = phoneConnection,
                        onCheckedChange = {
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

                    IconToggleButton(
                        checked = checkedFlash,
                        onCheckedChange = {
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
                    WearColorButton(sosOn, flashOn, Color.Red, activeButton == WearMainViewModel.Active.RED, blinkOn, colorBlinkActive)
                    { viewModel.select(WearMainViewModel.Active.RED) }

                    WearColorButton(sosOn, flashOn, liveMiddleColor, activeButton == WearMainViewModel.Active.MIDDLE, blinkOn, colorBlinkActive)
                    { viewModel.select(WearMainViewModel.Active.MIDDLE) }

                    WearColorButton(sosOn, flashOn, Color.Green, activeButton == WearMainViewModel.Active.GREEN, blinkOn, colorBlinkActive)
                    { viewModel.select(WearMainViewModel.Active.GREEN) }
                }

                Spacer(modifier = Modifier.height(colorBtnsSliderSpacing))

                InlineSlider(
                    value = MIN_TIME_MS + MAX_TIME_MS - sliderPositionFrequency,
                    onValueChange = { newValue ->
                        sliderPositionFrequency =
                            MIN_TIME_MS + MAX_TIME_MS - newValue
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
                        sliderPositionColor = newValue
                    },
                    onValueChangeFinished = {
                        viewModel.updateColorProgress(sliderPositionColor)
                    }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
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
                        IconToggleButton(
                            checked = checkedBlink,
                            onCheckedChange = {
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
                        IconToggleButton(
                            checked = isSoundOn,
                            onCheckedChange = {
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
                                .clip(CircleShape)
                        ) {
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
                        IconToggleButton(
                            checked = checkedSOS,
                            onCheckedChange = {
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
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(32.dp),
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

