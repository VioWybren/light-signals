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

@HiltViewModel
class PhoneMainViewModel @Inject constructor(
    private val phoneCtrl: PhoneControlRepository,
    private val phoneSyncStateRepository: PhoneSyncStateRepository,
    private val phoneWearOperations: PhoneWearOperations
) : ViewModel() {
    companion object {
        enum class Active { RED, MIDDLE, GREEN, FLASH }
    }

    val isReady = phoneCtrl.initialized
    internal val watchConnectedEvents = phoneSyncStateRepository.watchConnectedEvents
    private val openControlsChannel = Channel<Unit>(Channel.CONFLATED)
    internal val openControlsRequests = openControlsChannel.receiveAsFlow()
    val progressFrequency = phoneCtrl.blinkDelay.map(Long::toFloat)
        .stateIn(viewModelScope, SharingStarted.Eagerly, phoneCtrl.blinkDelay.value.toFloat())
    val progressColor = phoneCtrl.colorProgress
    val middleColor = progressColor.map { PhoneUtils.computeSpecialColor(it.toInt()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PhoneUtils.computeSpecialColor(DEFAULT_COLOR_PROGRESS.toInt()))

    val flashOn = phoneCtrl.flashOn
    val blinkOn = phoneCtrl.blinkOn
    val colorBlinkActive = phoneCtrl.colorBlinkActive
    val blinkDelay = phoneCtrl.blinkDelay
    val screenIndex = phoneCtrl.screenIndex
    val activeButton = phoneCtrl.activeButton
    val sosOn = phoneCtrl.sosOn
    val controlMessageFlow = phoneWearOperations.controlMessageFlow
    val isLaunchingOnWear = phoneWearOperations.isLaunchingOnWear
    val uiActiveButton = activeButton

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
            applyHandoff = phoneCtrl::applyWatchHandoff,
            applyControl = phoneCtrl::applyWatchControl,
        )
    }

    internal fun setHostResumed(resumed: Boolean) = phoneSyncStateRepository.setHostResumed(resumed)

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

    fun switchFlash(isOn: Boolean) = phoneCtrl.switchFlash(isOn)

    internal fun refreshTorchNotification() = phoneCtrl.refreshTorchNotification()

    fun toggleControl(on: Boolean) {
        phoneCtrl.setBlinking(on, blinkDelay.value)
    }

    fun updateBlinkDelay(ms: Long) = phoneCtrl.setBlinking(blinkOn.value, ms)

    fun toggleSos(on: Boolean) = phoneCtrl.switchSOS(on)

    fun updateColorProgress(progress: Float) {
        phoneCtrl.updateColorProgress(progress)
    }

    fun updateFrequencyProgress(progress: Float) {
        phoneCtrl.updateFrequencyProgress(progress)
    }

    fun updateScreenIndex(index: Int) = phoneCtrl.updateScreenIndex(index)

    internal fun requestOpenControls() {
        openControlsChannel.trySend(Unit)
    }

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

    override fun onCleared() {
        phoneSyncStateRepository.clear()
        phoneCtrl.clearAll()
        super.onCleared()
    }
}
