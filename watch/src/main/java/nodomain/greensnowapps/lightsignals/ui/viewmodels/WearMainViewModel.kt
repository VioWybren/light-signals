package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.model.WearControlState
import nodomain.greensnowapps.lightsignals.model.WearPhoneOperations
import nodomain.greensnowapps.lightsignals.model.WearSyncStateRepository
import nodomain.greensnowapps.lightsignals.model.statesToSynchronize

/**
 * Owns watch control-screen events, companion discovery and legacy state publication.
 *
 * Exposes StateFlows for modes, selected colours, sound, readiness and phone connectivity,
 * plus companion messages. Main-thread events update the repository; viewModelScope owns
 * derived UI flows, discovery retries and legacy synchronization.
 *
 * @param wearDataLayerAppHelper Horologist helper for watch-to-phone discovery and companion
 *     operations.
 * @property wearCtrl Owner of the watch signal and companion-link state.
 * @property wearSyncStateRepository Watch transport for legacy state publication and
 *     acknowledged commands.
 * @property wearPhoneOperations Watch-to-phone launch, installation and connection operations.
 */
@HiltViewModel
@OptIn(ExperimentalHorologistApi::class)
class WearMainViewModel @Inject constructor(
    wearDataLayerAppHelper: WearDataLayerAppHelper,
    private val wearCtrl: WearControlRepository,
    private val wearSyncStateRepository: WearSyncStateRepository,
    private val wearPhoneOperations: WearPhoneOperations
) : ViewModel() {
    /**
     * Identifies the selected red, custom-colour, green or phone-torch control.
     */
    enum class Active {
        /**
         * Selects the red screen-colour control.
         */
        RED,
        /**
         * Selects the custom colour shown by the shared palette slider.
         */
        MIDDLE,
        /**
         * Selects the green screen-colour control.
         */
        GREEN,
        /**
         * Selects the companion phone's LED output.
         */
        FLASH
    }

    /**
     * Read-only selected colour or phone-torch control.
     */
    val activeButton: StateFlow<Active> = wearCtrl.activeColorButton
        .map { Active.entries.getOrNull(it) ?: Active.RED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Active.RED)

    /**
     * Read-only blink interval represented as a Float for the frequency slider.
     */
    val progressFrequency: StateFlow<Float> = wearCtrl.blinkDelay
        .map(Long::toFloat)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 400f)

    /**
     * Read-only encoded palette position displayed by the colour slider.
     */
    val progressColor = wearCtrl.colorProgress
    /**
     * Read-only selection state of the phone-torch control; this is not the instantaneous LED
     * phase.
     */
    val flashOn = wearCtrl.flashOn
    /**
     * Read-only selection state of the Blink control.
     */
    val blinkOn = wearCtrl.blinkOn
    /**
     * Read-only lit/dark phase used to render and sonify timed screen signals.
     */
    val colorBlinkActive = wearCtrl.colorBlinkActive
    /**
     * Read-only blink half-period in milliseconds.
     */
    val blinkDelay = wearCtrl.blinkDelay
    /**
     * Read-only Compose colour displayed by the custom-colour button.
     */
    val middleColor = wearCtrl.middleColor
    /**
     * Read-only selected sample number: 0 for silence, 1..3 for a signal sample.
     */
    val activeSound = wearCtrl.activeSound
    /**
     * Read-only pager selection: 0 for controls, 1 for the signal display.
     */
    val screenIndex = wearCtrl.screenIndex
    /**
     * Read-only selection state of the SOS control.
     */
    val sosOn = wearCtrl.sosOn
    /**
     * Read-only confirmed or legacy connection state displayed by the watch Phone icon.
     */
    val phoneConnection = wearCtrl.phoneConnection
    /**
     * Read-only state indicating at least one active watch foreground owner.
     */
    val isForeground = wearCtrl.isForeground
    /**
     * Read-only readiness state used to enable controls after preference restoration.
     */
    val isInitialized = wearCtrl.isInitialized

    /**
     * Read-only sound-icon state derived from sample selection and local/remote routing
     * eligibility.
     *
     * A nonzero sample appears enabled when connected or when local suppression is off. This
     * describes UI selection and routing, not an active audible stream.
     */
    val isSoundOn: StateFlow<Boolean> = combine(activeSound, phoneConnection, wearCtrl.localSoundSuppressed) {
            sound, connected, suppressed ->
        sound != 0 && (connected || !suppressed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Companion-operation message events translated into user feedback by the UI.
     */
    val controlMessageFlow = wearPhoneOperations.controlMessageFlow
    /**
     * Read-only progress state that prevents overlapping phone launch/install operations.
     */
    val isLaunchingOnPhone = wearPhoneOperations.isLaunchingOnPhone

    /**
     * Read-only discovery state indicating a reachable phone with the companion installed.
     */
    val isPhoneAppAvailable: StateFlow<Boolean> =
        wearDataLayerAppHelper.connectedAndInstalledNodes
            .retryWhen { error, attempt ->
                if (error is CancellationException) throw error
                Log.w("WearMainViewModel", "Device discovery failed", error)
                emit(emptySet())
                delay(1_000L * (attempt + 1).coerceAtMost(10L))
                true
            }
            .map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Cold combination of selected modes, timing and sound, excluding transient on/off signal
     * phases.
     */
    private val controlModes = combine(blinkOn, sosOn, flashOn, blinkDelay, activeSound) {
            blink, sos, torch, frequency, sound ->
        WearControlState(
            blinkEnabled = blink,
            sosEnabled = sos,
            torchOn = torch,
            frequency = frequency,
            activeSound = sound
        )
    }

    /**
     * Cold complete-control snapshots assembled for the gated legacy DataItem publisher.
     */
    private val legacyState = combine(
        controlModes,
        progressColor,
        screenIndex,
        wearCtrl.activeColorButton
    ) { modes, color, screen, active ->
        modes.copy(color = color, screenIndex = screen, activeColorButton = active)
    }

    /**
     * Read-only Compose colour used by the selected screen signal.
     */
    val activeColor: StateFlow<Color> = combine(activeButton, middleColor) { active, middle ->
        when (active) {
            Active.RED -> Color.Red
            Active.MIDDLE -> middle
            Active.GREEN -> Color.Green
            Active.FLASH -> Color.Black
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Color.Red)

    init {
        viewModelScope.launch {
            statesToSynchronize(legacyState, wearCtrl.manualPhoneConnection, isForeground, isInitialized)
                .collect { state ->
                    val manualLinkStartedAt = wearCtrl.manualLinkStartedAt ?: return@collect
                    wearSyncStateRepository.sendState(state, manualLinkStartedAt)
                }
        }
    }

    /**
     * Selects a colour control or phone-torch output on Main.
     *
     * @param button Selected colour or torch control.
     */
    fun select(button: Active) {
        if (button == Active.FLASH) wearCtrl.setFlash(true)
        else wearCtrl.selectColorButton(button.ordinal)
    }

    /**
     * Changes the phone-torch selection on Main.
     *
     * @param isOn Whether the phone torch should be selected.
     */
    fun switchFlash(isOn: Boolean) = wearCtrl.setFlash(isOn)

    /**
     * Updates Blink selection on Main using the current blink interval.
     *
     * @param on Whether to select Blink mode.
     */
    fun toggleControl(on: Boolean) = wearCtrl.setBlinking(on, blinkDelay.value)

    /**
     * Updates the blink half-period on Main while preserving Blink selection.
     *
     * @param ms Blink half-period in milliseconds.
     */
    fun updateBlinkDelay(ms: Long) = wearCtrl.setBlinking(blinkOn.value, ms)

    /**
     * Updates SOS selection on Main through the control repository.
     *
     * @param on Whether to select SOS mode.
     */
    fun toggleSos(on: Boolean) = wearCtrl.switchSOS(on)

    /**
     * Updates the custom-colour selection on Main using a bounded shared palette position.
     *
     * @param newProgress Encoded shared palette position shown by the custom-colour slider.
     */
    fun updateColorProgress(newProgress: Float) = wearCtrl.commitColor(newProgress)

    /**
     * Updates the persisted frequency-slider value on Main after bounding non-finite or
     * out-of-range input.
     *
     * Applies the bounded interval to the watch repository as well as its persisted frequency
     * slider.
     *
     * @param newProgress Requested blink half-period from the frequency slider, in
     *     milliseconds.
     */
    fun updateFrequencyProgress(newProgress: Float) {
        val progress = newProgress.takeIf { it.isFinite() }?.coerceIn(100f, 1000f) ?: 400f
        wearCtrl.setBlinkDelay(progress.toLong())
    }

    /**
     * Updates and persists the selected controls/signal pager page on Main.
     *
     * @param newScreenIndex Pager index: 0 for controls, 1 for the signal display.
     */
    fun updateScreenIndex(newScreenIndex: Int) = wearCtrl.updateScreenIndex(newScreenIndex)

    /**
     * Applies explicit disconnect or legacy-link selection through the watch repository on Main.
     *
     * Modern acknowledged connections use the handoff operations. A false value revokes the
     * current relationship and disables automatic resume.
     *
     * @param isConnect True to select manual legacy control, or false to explicitly disconnect.
     */
    fun togglePhoneConnection(isConnect: Boolean) = wearCtrl.togglePhoneConnection(isConnect)

    /**
     * Launches phone discovery, connection or installation in viewModelScope on Main.
     *
     * @param command COMMAND_START_ON_PHONE or COMMAND_INSTALL_ON_PHONE.
     */
    fun performWearOperations(command: String) {
        viewModelScope.launch {
            wearPhoneOperations.performWearOperations(command)
        }
    }
}
