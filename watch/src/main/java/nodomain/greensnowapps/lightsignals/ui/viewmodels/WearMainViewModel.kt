package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.WearConstants.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.model.KEY_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.model.KEY_FREQUENCY_PROGRESS
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.model.WearControlState
import nodomain.greensnowapps.lightsignals.model.WearPhoneOperations
import nodomain.greensnowapps.lightsignals.model.WearSyncStateRepository
import nodomain.greensnowapps.lightsignals.model.editSafely
import nodomain.greensnowapps.lightsignals.model.safeData
import nodomain.greensnowapps.lightsignals.model.statesToSynchronize
import nodomain.greensnowapps.lightsignals.utils.WearUtils
import javax.inject.Inject

private val KEY_ACTIVE_COLOR = intPreferencesKey("active_btn")

@HiltViewModel
@OptIn(ExperimentalHorologistApi::class)
class WearMainViewModel @Inject constructor(
    wearDataLayerAppHelper: WearDataLayerAppHelper,
    private val wearCtrl: WearControlRepository,
    private val dataStore: DataStore<Preferences>,
    private val wearSyncStateRepository: WearSyncStateRepository,
    private val wearPhoneOperations: WearPhoneOperations
) : ViewModel() {
    enum class Active { RED, MIDDLE, GREEN, FLASH }

    private val _active = MutableStateFlow(Active.RED)
    val activeButton = _active.asStateFlow()
    private val preferencesLoaded = MutableStateFlow(false)

    val progressFrequency: StateFlow<Float> = dataStore.safeData()
        .map { prefs -> safeFrequency(prefs[KEY_FREQUENCY_PROGRESS] ?: 400f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 400f)

    val progressColor: StateFlow<Float> = dataStore.safeData()
        .map { prefs -> safeColor(prefs[KEY_COLOR_PROGRESS] ?: DEFAULT_COLOR_PROGRESS) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_COLOR_PROGRESS)

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
    val isInitialized = combine(wearCtrl.isInitialized, preferencesLoaded) { controlReady, uiReady ->
        controlReady && uiReady
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val controlMessageFlow = wearPhoneOperations.controlMessageFlow
    val isLaunchingOnPhone = wearPhoneOperations.isLaunchingOnPhone

    val isPhoneAppAvailable: StateFlow<Boolean> =
        wearDataLayerAppHelper.connectedAndInstalledNodes
            .retryWhen { error, attempt ->
                if (error is CancellationException) throw error
                Log.w("WearMainViewModel", "Device discovery failed", error)
                emit(emptySet())
                delay((1_000L * (attempt + 1).coerceAtMost(10L)))
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

    val controlState: StateFlow<WearControlState> =
        combine(controlModes, progressColor, screenIndex, activeButton) { modes, color, screen, active ->
            modes.copy(color = color, screenIndex = screen, activeColorButton = active.ordinal)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WearControlState())

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
            val prefs = dataStore.safeData().first()
            _active.value = Active.entries.getOrNull(prefs[KEY_ACTIVE_COLOR] ?: 0)
                ?.takeUnless { it == Active.FLASH } ?: Active.RED
            preferencesLoaded.value = true
        }
        viewModelScope.launch {
            progressColor.collect(wearCtrl::calcMiddleColor)
        }
        viewModelScope.launch {
            statesToSynchronize(controlState, phoneConnection, isForeground, isInitialized).collect { state ->
                wearSyncStateRepository.sendState(state)
            }
        }
    }

    fun select(button: Active) {
        if (button == Active.FLASH) {
            wearCtrl.setFlash(true)
        } else {
            _active.value = button
            writePref(KEY_ACTIVE_COLOR, button.ordinal)
            wearCtrl.setFlash(false)
        }
    }

    fun switchFlash(isOn: Boolean) = wearCtrl.setFlash(isOn)

    fun toggleControl(on: Boolean) = wearCtrl.setBlinking(on, blinkDelay.value)

    fun updateBlinkDelay(ms: Long) = wearCtrl.setBlinking(blinkOn.value, ms)

    fun toggleSos(on: Boolean) = wearCtrl.switchSOS(on)

    fun updateColorProgress(newProgress: Float) {
        val progress = safeColor(newProgress)
        wearCtrl.calcMiddleColor(progress)
        writePref(KEY_COLOR_PROGRESS, progress)
    }

    fun updateFrequencyProgress(newFrequencyProgress: Float) {
        val progress = safeFrequency(newFrequencyProgress)
        wearCtrl.setBlinking(blinkOn.value, progress.toLong())
        writePref(KEY_FREQUENCY_PROGRESS, progress)
    }

    fun updateScreenIndex(newScreenIndex: Int) = wearCtrl.updateScreenIndex(newScreenIndex)

    fun togglePhoneConnection(isConnect: Boolean) = wearCtrl.togglePhoneConnection(isConnect)

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            dataStore.editSafely { prefs -> prefs[key] = value }
        }
    }

    fun performWearOperations(command: String) {
        viewModelScope.launch {
            wearPhoneOperations.performWearOperations(command)
        }
    }

    private fun safeFrequency(value: Float) =
        value.takeIf { it.isFinite() }?.coerceIn(100f, 1000f) ?: 400f

    private fun safeColor(value: Float) =
        value.takeIf { it.isFinite() }?.coerceIn(0f, WearUtils.MAX_COLOR_PROGRESS.toFloat())
            ?: DEFAULT_COLOR_PROGRESS
}
