package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.APP_FAILED_TO_LAUNCH_ON_WATCH
import nodomain.greensnowapps.lightsignals.COMMAND_INSTALL_ON_WATCH
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_FAILED
import nodomain.greensnowapps.lightsignals.model.PhoneControlRepository
import nodomain.greensnowapps.lightsignals.model.PhoneSyncStateRepository
import nodomain.greensnowapps.lightsignals.model.PhoneWearOperations
import nodomain.greensnowapps.lightsignals.utils.PhoneUtils
import javax.inject.Inject

/**
 * Owns the phone control-screen events and derives its observable UI state.
 *
 * Exposes StateFlows for readiness, selection, timing, colour and torch modes, plus companion
 * message and connection events. Main-thread events delegate to the repository; derived flows
 * and asynchronous companion work use viewModelScope. Clearing releases phone control
 * sessions.
 *
 * @property phoneCtrl Owner of the phone control and signal state.
 * @property phoneSyncStateRepository Phone-side transport and authenticated watch-session
 *     repository.
 * @property phoneWearOperations Phone-to-watch discovery, launch and handoff operations.
 */
@HiltViewModel
class PhoneMainViewModel @Inject constructor(
    private val phoneCtrl: PhoneControlRepository,
    private val phoneSyncStateRepository: PhoneSyncStateRepository,
    private val phoneWearOperations: PhoneWearOperations
) : ViewModel() {
    /**
     * Defines the saved colour/output enum used by phone controls and legacy snapshots.
     */
    companion object {
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
    }

    /**
     * Read-only readiness state used by the splash screen and phone controls.
     */
    val isReady = phoneCtrl.initialized
    /**
     * One-shot flow announcing a newly confirmed watch connection for phone feedback.
     */
    internal val watchConnectedEvents = phoneSyncStateRepository.watchConnectedEvents
    /**
     * Conflated controls-page requests retained until the pager collects them; multiple pending
     * requests collapse to one.
     */
    private val openControlsChannel = Channel<Unit>(Channel.CONFLATED)
    /**
     * Conflated navigation events requesting the phone controls page.
     */
    internal val openControlsRequests = openControlsChannel.receiveAsFlow()
    /**
     * Read-only blink interval represented as a Float for the frequency slider.
     */
    val progressFrequency = phoneCtrl.blinkDelay.map(Long::toFloat)
        .stateIn(viewModelScope, SharingStarted.Eagerly, phoneCtrl.blinkDelay.value.toFloat())
    /**
     * Read-only encoded palette position displayed by the colour slider.
     */
    val progressColor = phoneCtrl.colorProgress
    /**
     * Read-only Compose colour displayed by the custom-colour button.
     */
    val middleColor = progressColor.map { PhoneUtils.computeSpecialColor(it.toInt()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PhoneUtils.computeSpecialColor(DEFAULT_COLOR_PROGRESS.toInt()))

    /**
     * Read-only selection state of the phone-torch control; this is not the instantaneous LED
     * phase.
     */
    val flashOn = phoneCtrl.flashOn
    /**
     * Read-only selection state of the Blink control.
     */
    val blinkOn = phoneCtrl.blinkOn
    /**
     * Read-only lit/dark phase used to render and sonify timed screen signals.
     */
    val colorBlinkActive = phoneCtrl.colorBlinkActive
    /**
     * Read-only blink half-period in milliseconds.
     */
    val blinkDelay = phoneCtrl.blinkDelay
    /**
     * Read-only pager selection: 0 for controls, 1 for the signal display.
     */
    val screenIndex = phoneCtrl.screenIndex
    /**
     * Read-only selected colour or phone-torch control.
     */
    val activeButton = phoneCtrl.activeButton
    /**
     * Read-only selection state of the SOS control.
     */
    val sosOn = phoneCtrl.sosOn
    /**
     * Companion-operation message events translated into user feedback by the UI.
     */
    val controlMessageFlow = phoneWearOperations.controlMessageFlow
    /**
     * Read-only progress state while opening the watch companion and confirming its
     * connection.
     */
    val isLaunchingOnWear = phoneWearOperations.isLaunchingOnWear
    /**
     * Selected control exposed to the phone UI as the repository selection.
     */
    val uiActiveButton = activeButton

    /**
     * Read-only Compose colour used by the selected screen signal.
     */
    val activeColor: StateFlow<Color> = activeButton.combine(middleColor) { button, middle ->
        when (button) {
            Active.RED -> Color.Red
            Active.MIDDLE -> middle
            Active.GREEN -> Color.Green
            Active.FLASH -> Color.Black
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Color.Red)

    init {
        phoneCtrl.initFromPrefs()
        phoneSyncStateRepository.initFromViewModel(
            isReady = { phoneCtrl.initialized.value },
            currentSignal = phoneCtrl::connectionSignal,
            applyHandoff = phoneCtrl::applyWatchHandoff,
            applyControl = phoneCtrl::applyWatchControl,
        )
        viewModelScope.launch {
            isReady.first { it }
            phoneSyncStateRepository.announceAvailability()
        }
    }

    /**
     * Announces the ready phone to a remembered watch when the host resumes on Main.
     */
    internal fun onHostResumed() = phoneSyncStateRepository.onHostResumed()

    /**
     * Applies a colour or torch selection on Main, waiting for restoration when necessary.
     *
     * Ready controls update immediately. Otherwise launches a viewModelScope job that waits for
     * readiness before selecting; clearing the ViewModel cancels that wait.
     *
     * @param button Selected colour or torch control.
     */
    fun select(button: Active) {
        if (isReady.value) {
            phoneCtrl.select(button)
        } else {
            viewModelScope.launch {
                isReady.first { it }
                phoneCtrl.select(button)
            }
        }
    }

    /**
     * Changes the phone-torch selection on Main.
     *
     * @param isOn Whether the phone torch should be selected.
     */
    fun switchFlash(isOn: Boolean) = phoneCtrl.switchFlash(isOn)

    /**
     * Emits a Main-thread request to repost the active torch notification after permission
     * changes.
     */
    internal fun refreshTorchNotification() = phoneCtrl.refreshTorchNotification()

    /**
     * Updates Blink selection on Main using the current blink interval.
     *
     * @param on Whether to select Blink mode.
     */
    fun toggleControl(on: Boolean) {
        phoneCtrl.setBlinking(on, blinkDelay.value)
    }

    /**
     * Updates the blink half-period on Main while preserving Blink selection.
     *
     * @param ms Blink half-period in milliseconds.
     */
    fun updateBlinkDelay(ms: Long) = phoneCtrl.setBlinking(blinkOn.value, ms)

    /**
     * Updates SOS selection on Main through the control repository.
     *
     * @param on Whether to select SOS mode.
     */
    fun toggleSos(on: Boolean) = phoneCtrl.switchSOS(on)

    /**
     * Updates the custom-colour selection on Main using a bounded shared palette position.
     *
     * @param progress Encoded shared palette position shown by the custom-colour slider.
     */
    fun updateColorProgress(progress: Float) {
        phoneCtrl.updateColorProgress(progress)
    }

    /**
     * Queues the bounded frequency-slider preference on Main without changing the running delay.
     *
     * Use updateBlinkDelay when applying timing to the signal; this event only persists the
     * slider value through the repository.
     *
     * @param progress Requested frequency-slider value, bounded to a 100..1000 millisecond
     *     half-period.
     */
    fun updateFrequencyProgress(progress: Float) {
        phoneCtrl.updateFrequencyProgress(progress)
    }

    /**
     * Updates and persists the selected controls/signal pager page on Main.
     *
     * @param index Pager index: 0 for controls, 1 for the signal display.
     */
    fun updateScreenIndex(index: Int) = phoneCtrl.updateScreenIndex(index)

    /**
     * Queues a conflated Main-thread navigation event for the phone controls page.
     */
    internal fun requestOpenControls() {
        openControlsChannel.trySend(Unit)
    }

    /**
     * Launches a watch operation in viewModelScope and converts operation failures to UI
     * feedback.
     *
     * Call on Main; the coroutine is cancelled when this ViewModel is cleared.
     *
     * @param command COMMAND_START_ON_WATCH or COMMAND_INSTALL_ON_WATCH.
     */
    fun startWearOperations(command: String) {
        viewModelScope.launch {
            try {
                phoneWearOperations.startWearOperations(command)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("PhoneMainViewModel", "Remote operation failed", error)
                phoneWearOperations.emitMessage(
                    if (command == COMMAND_INSTALL_ON_WATCH) INSTALLATION_ON_WATCH_FAILED
                    else APP_FAILED_TO_LAUNCH_ON_WATCH
                )
            }
        }
    }

    /**
     * Clears phone transport and control sessions when the Activity-scoped ViewModel ends on
     * Main.
     *
     * The transport retains remembered identity for resume. Control cleanup cancels screen work
     * and requests torch shutdown; independently queued preference writes can finish.
     */
    override fun onCleared() {
        phoneSyncStateRepository.clear()
        phoneCtrl.clearAll()
        super.onCleared()
    }
}
