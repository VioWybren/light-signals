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

@HiltViewModel
@OptIn(ExperimentalHorologistApi::class)
class WearMainViewModel @Inject constructor(
    wearDataLayerAppHelper: WearDataLayerAppHelper,
    private val wearCtrl: WearControlRepository,
    private val wearSyncStateRepository: WearSyncStateRepository,
    private val wearPhoneOperations: WearPhoneOperations
) : ViewModel() {
    enum class Active { RED, MIDDLE, GREEN, FLASH }

    val activeButton: StateFlow<Active> = wearCtrl.activeColorButton
        .map { Active.entries.getOrNull(it) ?: Active.RED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Active.RED)

    val progressFrequency: StateFlow<Float> = wearCtrl.blinkDelay
        .map(Long::toFloat)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 400f)

    val progressColor = wearCtrl.colorProgress
    val flashOn = wearCtrl.flashOn
    val blinkOn = wearCtrl.blinkOn
    val colorBlinkActive = wearCtrl.colorBlinkActive
    val blinkDelay = wearCtrl.blinkDelay
    val middleColor = wearCtrl.middleColor
    val activeSound = wearCtrl.activeSound
    val screenIndex = wearCtrl.screenIndex
    val sosOn = wearCtrl.sosOn
    val phoneConnection = wearCtrl.phoneConnection
    val isForeground = wearCtrl.isForeground
    val isInitialized = wearCtrl.isInitialized

    val isSoundOn: StateFlow<Boolean> = combine(activeSound, phoneConnection, wearCtrl.localSoundSuppressed) {
            sound, connected, suppressed ->
        sound != 0 && (connected || !suppressed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val controlMessageFlow = wearPhoneOperations.controlMessageFlow
    val isLaunchingOnPhone = wearPhoneOperations.isLaunchingOnPhone

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

    private val legacyState = combine(
        controlModes,
        progressColor,
        screenIndex,
        wearCtrl.activeColorButton
    ) { modes, color, screen, active ->
        modes.copy(color = color, screenIndex = screen, activeColorButton = active)
    }

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

    fun select(button: Active) {
        if (button == Active.FLASH) wearCtrl.setFlash(true)
        else wearCtrl.selectColorButton(button.ordinal)
    }

    fun switchFlash(isOn: Boolean) = wearCtrl.setFlash(isOn)

    fun toggleControl(on: Boolean) = wearCtrl.setBlinking(on, blinkDelay.value)

    fun updateBlinkDelay(ms: Long) = wearCtrl.setBlinking(blinkOn.value, ms)

    fun toggleSos(on: Boolean) = wearCtrl.switchSOS(on)

    fun updateColorProgress(newProgress: Float) = wearCtrl.commitColor(newProgress)

    fun updateFrequencyProgress(newProgress: Float) {
        val progress = newProgress.takeIf { it.isFinite() }?.coerceIn(100f, 1000f) ?: 400f
        wearCtrl.setBlinkDelay(progress.toLong())
    }

    fun updateScreenIndex(newScreenIndex: Int) = wearCtrl.updateScreenIndex(newScreenIndex)

    fun togglePhoneConnection(isConnect: Boolean) = wearCtrl.togglePhoneConnection(isConnect)

    fun performWearOperations(command: String) {
        viewModelScope.launch {
            wearPhoneOperations.performWearOperations(command)
        }
    }
}
