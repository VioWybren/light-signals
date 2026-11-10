package nodomain.greensnowapps.lightsignals.ui.screens

import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import android.annotation.SuppressLint
import android.content.res.Configuration
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalResources
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
import nodomain.greensnowapps.lightsignals.BuildConfig
import nodomain.greensnowapps.lightsignals.COMMAND_INSTALL_ON_WATCH
import nodomain.greensnowapps.lightsignals.COMMAND_START_ON_WATCH
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_FAILED
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_STARTED
import nodomain.greensnowapps.lightsignals.NO_CONNECTED_WATCHES
import nodomain.greensnowapps.lightsignals.PhoneMainActivity
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.WATCH_CONNECTION_UNCONFIRMED
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

private const val MIN_TIME_MS = 100
private const val MAX_TIME_MS = 1000
private const val MIN_SLIDER_PROGRESS = 100f
private const val MAX_SLIDER_PROGRESS = 1000f


@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneMainScreen(
    viewModel: PhoneMainViewModel,
    onNext: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {

    val phoneTorchViewModel: PhoneTorchViewModel = hiltViewModel()
    val hasTorch by phoneTorchViewModel.hasTorch.collectAsStateWithLifecycle()


    val context = LocalContext.current
    val resources = LocalResources.current

    val persistedFrequencyProgress by viewModel.progressFrequency.collectAsStateWithLifecycle()

    var sliderPositionFrequency by remember { mutableFloatStateOf(persistedFrequencyProgress) }

    LaunchedEffect(persistedFrequencyProgress) {
        sliderPositionFrequency = persistedFrequencyProgress
    }

    val persistedColorProgress by viewModel.progressColor.collectAsStateWithLifecycle()
    var sliderPositionColor by remember { mutableFloatStateOf(persistedColorProgress) }

    LaunchedEffect(persistedColorProgress) {
        sliderPositionColor = persistedColorProgress
    }

    val liveMiddleColor = remember(sliderPositionColor) {
        PhoneUtils.computeSpecialColor(sliderPositionColor.toInt())
    }

    val activeButton by viewModel.activeButton.collectAsStateWithLifecycle()
    val flashOn by viewModel.flashOn.collectAsStateWithLifecycle()
    val blinkOn by viewModel.blinkOn.collectAsStateWithLifecycle()
    val colorBlinkActive by viewModel.colorBlinkActive.collectAsStateWithLifecycle()
    val sosOn by viewModel.sosOn.collectAsStateWithLifecycle()
    val controlMessage by viewModel.controlMessageFlow.collectAsStateWithLifecycle(initialValue = "")
    val isLaunchingOnWear by viewModel.isLaunchingOnWear.collectAsStateWithLifecycle()

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

    var showConnectToWatchDialog by rememberSaveable { mutableStateOf(false) }
    var showWearApiNotAvailableDialog by rememberSaveable { mutableStateOf(false) }

    if (showConnectToWatchDialog) {
        UniversalDialog(
            onConfirm = {
                viewModel.startWearOperations(COMMAND_START_ON_WATCH)

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

    var showInstallOnWatchDialog by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun showSnack(msg: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = duration
            )
        }
    }

    LaunchedEffect(controlMessage) {
        val inputCommand = Regex("[0-9]").replace(controlMessage, "")
        if (inputCommand.isNotEmpty()) {
            when (inputCommand) {
                APP_IS_NOT_INSTALLED_ON_WATCH -> {
                    showInstallOnWatchDialog = true
                }
                INSTALLATION_ON_WATCH_STARTED -> showSnack(resources.getString(R.string.snackbar_app_installed_on_watch_success))
                INSTALLATION_ON_WATCH_FAILED -> showSnack(resources.getString(R.string.snackbar_installation_on_watch_failed))
                APP_FAILED_TO_LAUNCH_ON_WATCH -> showSnack(resources.getString(R.string.snackbar_app_failed_to_launch_on_watch))
                WATCH_CONNECTION_UNCONFIRMED -> showSnack(
                    resources.getString(R.string.snackbar_watch_connection_unconfirmed),
                    duration = SnackbarDuration.Long,
                )
                WEARABLE_API_UNAVAILABLE -> showWearApiNotAvailableDialog = true
                NO_CONNECTED_WATCHES -> showSnack(resources.getString(R.string.snackbar_no_connected_watches))
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

    val activity = context as PhoneMainActivity
    val quickSettingsUsage = stringResource(R.string.quick_settings_tile_usage)
    var showQuickSettingsInstructions by rememberSaveable { mutableStateOf(false) }
    if (showQuickSettingsInstructions) {
        val tileLabel = remember(activity) { activity.applicationInfo.loadLabel(activity.packageManager) }
        val instructions = stringResource(R.string.quick_settings_tile_instructions, tileLabel)
        UniversalDialog(
            title = stringResource(R.string.quick_settings_tile_help_title),
            message = "$instructions\n\n$quickSettingsUsage",
            confirmText = stringResource(R.string._ok),
            onConfirm = { showQuickSettingsInstructions = false },
            onDismiss = { showQuickSettingsInstructions = false },
        )
    }

    val iconSize = 30.dp
    val intervalDescription = stringResource(R.string.signal_interval)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                expandedHeight = 50.dp,
                scrollBehavior = null,
                actions = {
                    var expanded by remember { mutableStateOf(false) }

                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_filled_more_vert_24),
                            contentDescription = stringResource(R.string.menu),
                            tint = Color.White
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_item_synchronize_with_watch)) },
                            enabled = !isLaunchingOnWear,
                            leadingIcon = {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.ic_outlined_watch_24),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showConnectToWatchDialog = true
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_item_add_quick_settings_tile)) },
                            leadingIcon = {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.outline_traffic_24),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                expanded = false
                                activity.requestQuickSettingsTile(
                                    onAvailable = { showSnack(quickSettingsUsage) },
                                    onManualSetup = { scope.launch { showQuickSettingsInstructions = true } },
                                )
                            },
                        )
                        HorizontalDivider()
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0),
    )
    { innerPadding ->
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
            val reference = min(maxWidth, maxHeight)

            var colorButtonSize = 160.dp
            var toggleBtnHeight = 40.dp
            var toggleBtnWidth = 80.dp
            var leftColumnPaddingStart = 10.dp
            var rightColumnPaddingEnd = 10.dp

            when {
                reference >= 700.dp -> {
                    colorButtonSize = 300.dp
                    toggleBtnWidth = 100.dp
                    toggleBtnHeight = 50.dp
                    leftColumnPaddingStart = 20.dp
                    rightColumnPaddingEnd = 20.dp
                }
                reference >= 600.dp -> {
                    colorButtonSize = 200.dp
                    toggleBtnWidth = 100.dp
                    toggleBtnHeight = 50.dp
                    leftColumnPaddingStart = 20.dp
                    rightColumnPaddingEnd = 20.dp
                }
                reference > 360.dp -> {
                    colorButtonSize = 160.dp
                    toggleBtnWidth = 80.dp
                    toggleBtnHeight = 40.dp
                    leftColumnPaddingStart = 10.dp
                    rightColumnPaddingEnd = 10.dp
                }
                reference <= 360.dp -> {
                    colorButtonSize = 120.dp
                    toggleBtnWidth = 80.dp
                    toggleBtnHeight = 40.dp
                    leftColumnPaddingStart = 10.dp
                    rightColumnPaddingEnd = 10.dp
                }
                else -> 160.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                PhoneColorPickerSlider(
                    progress = sliderPositionColor,
                    onProgressChanged = { newValue ->
                        sliderPositionColor = newValue
                    },
                    onValueChangeFinished = {
                        viewModel.updateColorProgress(sliderPositionColor)
                    }
                )

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                {
                    Column(
                        modifier = Modifier
                            .padding(start = leftColumnPaddingStart)
                            .wrapContentWidth()
                            .align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(7f))

                        if (hasTorch == true) {
                            PhoneFlashButton(
                                toggleBtnWidth, toggleBtnHeight, checkedFlash, viewModel, activity, iconSize,
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        PhoneSosButton(
                            toggleBtnWidth, toggleBtnHeight, checkedSOS, viewModel, iconSize,
                        )

                        Spacer(Modifier.weight(1f))
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLandscape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        Color.Red,
                                        activeButton == Active.RED,
                                        blinkOn,
                                        colorBlinkActive,
                                        labelRes = R.string.red_signal,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.RED) }
                                    )
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        liveMiddleColor,
                                        activeButton == Active.MIDDLE,
                                        blinkOn,
                                        colorBlinkActive,
                                        labelRes = R.string.custom_color_signal,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.MIDDLE) }
                                    )
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        Color.Green,
                                        activeButton == Active.GREEN,
                                        blinkOn,
                                        colorBlinkActive,
                                        labelRes = R.string.green_signal,
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
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        Color.Red,
                                        activeButton == Active.RED,
                                        blinkOn,
                                        colorBlinkActive,
                                        labelRes = R.string.red_signal,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.RED) }
                                    )
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        liveMiddleColor,
                                        activeButton == Active.MIDDLE,
                                        blinkOn,
                                        colorBlinkActive,
                                        labelRes = R.string.custom_color_signal,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.MIDDLE) }
                                    )
                                    PhoneColorButton(
                                        sosOn,
                                        flashOn,
                                        Color.Green,
                                        activeButton == Active.GREEN,
                                        blinkOn,
                                        colorBlinkActive,
                                        labelRes = R.string.green_signal,
                                        size = colorButtonSize,
                                        onClick = { viewModel.select(Active.GREEN) }
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(end = rightColumnPaddingEnd)
                            .wrapContentWidth()
                            .align(Alignment.BottomEnd),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {

                        Spacer(Modifier.weight(7f))

                        PhoneSoundMenuButton(toggleBtnWidth, toggleBtnHeight, iconSize)

                        Spacer(Modifier.weight(1f))

                        PhoneBlinkButton(
                            toggleBtnWidth, toggleBtnHeight, checkedBlink, sosOn, viewModel, iconSize,
                        )

                        Spacer(Modifier.weight(1f))
                    }
                }

                val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }

                Slider(
                    value = MIN_TIME_MS + MAX_TIME_MS - sliderPositionFrequency,
                    onValueChange = { newValue ->
                        val delay = MIN_TIME_MS + MAX_TIME_MS - newValue
                        sliderPositionFrequency = delay
                        viewModel.updateBlinkDelay(delay.toLong())
                    },
                    interactionSource = interactionSource,
                    valueRange = MIN_SLIDER_PROGRESS..MAX_SLIDER_PROGRESS,
                    steps = 8,
                    modifier = Modifier
                        .semantics { contentDescription = intervalDescription },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTickColor = Color.Green
                    ),
                    onValueChangeFinished = {
                        viewModel.updateFrequencyProgress(sliderPositionFrequency)
                    },
                    thumb = {
                        Label(
                            label = {
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
                                        text = stringResource(R.string.interval_milliseconds, sliderPositionFrequency.roundToInt()),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            },
                            interactionSource = interactionSource,
                        ) {
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
                Column(
                    modifier = Modifier
                        .size(160.dp, 150.dp)
                        .clip(RoundedCornerShape(size = 20.dp))
                        .background(color = Color.Black)
                        .align(Alignment.Center),

                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(40.dp),
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
            }
        }
    }
}

