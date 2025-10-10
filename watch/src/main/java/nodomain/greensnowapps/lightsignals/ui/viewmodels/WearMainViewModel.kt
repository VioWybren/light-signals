package nodomain.greensnowapps.lightsignals.ui.viewmodels

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nodomain.greensnowapps.lightsignals.WearConstants.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.model.KEY_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.model.KEY_FREQUENCY_PROGRESS
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.model.WearControlState
import nodomain.greensnowapps.lightsignals.model.WearPhoneOperations
import nodomain.greensnowapps.lightsignals.model.WearSyncStateRepository
import javax.inject.Inject


private val KEY_ACTIVE_COLOR = intPreferencesKey("active_btn")

@HiltViewModel
class WearMainViewModel @OptIn(ExperimentalHorologistApi::class)
@Inject constructor(
    private val wearDataLayerAppHelper: WearDataLayerAppHelper,
    private val wearCtrl: WearControlRepository,
    private val dataStore: DataStore<Preferences>,
    private val wearSyncStateRepository: WearSyncStateRepository,
    private val wearPhoneOperations: WearPhoneOperations
) : ViewModel() {

    enum class Active { RED, MIDDLE, GREEN, FLASH }

    private val initialIndex: Int = runBlocking {
        dataStore.data
            .map { preference -> preference[KEY_ACTIVE_COLOR] ?: 0 }
            .first()
    }

    private val _active = MutableStateFlow(
        Active.entries.getOrNull(initialIndex) ?: Active.MIDDLE
    )
    val activeButton: StateFlow<Active> = _active.asStateFlow()

    private val initialFrequency: Float = runBlocking(Dispatchers.IO) {
        dataStore.data
            .map { prefs -> prefs[KEY_FREQUENCY_PROGRESS] ?: 400f }
            .first()
    }

    val progressFrequency: StateFlow<Float> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_FREQUENCY_PROGRESS] ?: initialFrequency }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5_000L),
            initialValue = initialFrequency
        )

    private val initialProgressColor: Float = runBlocking(Dispatchers.IO) {
        dataStore.data
            .map { prefs -> prefs[KEY_COLOR_PROGRESS] ?: DEFAULT_COLOR_PROGRESS }
            .first()
    }

    val progressColor: StateFlow<Float> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[KEY_COLOR_PROGRESS] ?: initialProgressColor
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = initialProgressColor
        )

    val flashOn = wearCtrl.flashOn
    val blinkOn = wearCtrl.blinkOn
    val colorBlinkActive = wearCtrl.colorBlinkActive
    val blinkDelay = wearCtrl.blinkDelay
    val middleColor = wearCtrl.middleColor
    val activeSound = wearCtrl.activeSound
    val screenIndex = wearCtrl.screenIndex
    val sosOn: StateFlow<Boolean> = wearCtrl.sosOn
    val phoneConnection = wearCtrl.phoneConnection

    val controlMessageFlow = wearPhoneOperations.controlMessageFlow
    val isLaunchingOnPhone = wearPhoneOperations.isLaunchingOnPhone

    private val _controlState = MutableStateFlow(WearControlState())
    val controlState: StateFlow<WearControlState> = _controlState.asStateFlow()

    @OptIn(ExperimentalHorologistApi::class)
    val installedPhoneNodes = wearDataLayerAppHelper.connectedAndInstalledNodes

    val isPhoneAppAvailable: StateFlow<Boolean> =
        installedPhoneNodes
            .map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            wearCtrl.initFromPrefs()
        }

        viewModelScope.launch {
            progressColor.collect { prog ->
                wearCtrl.calcMiddleColor(prog)
            }
        }

        viewModelScope.launch {
            blinkOn.collect { newBlink ->
                _controlState.update { it.copy(blinkEnabled = newBlink) }
            }
        }
        viewModelScope.launch {
            sosOn.collect { newSos ->
                _controlState.update { it.copy(sosEnabled = newSos) }
            }
        }
        viewModelScope.launch {
            flashOn.collect { newFlash ->
                _controlState.update { it.copy(torchOn = newFlash) }
            }
        }
        viewModelScope.launch {
            blinkDelay.collect { newDelay ->
                _controlState.update { it.copy(frequency = newDelay) }
            }
        }
        viewModelScope.launch {
            activeSound.collect { newSound ->
                _controlState.update { it.copy(activeSound = newSound) }
            }
        }
        viewModelScope.launch {
            colorBlinkActive.collect { newColorBlink ->
                _controlState.update { it.copy(colorBlinkEnabled = newColorBlink) }
            }
        }

        viewModelScope.launch {
            progressColor.collect { newColor ->
                _controlState.update { it.copy(color = newColor) }
            }
        }

        viewModelScope.launch {
            screenIndex.collect { newIndex ->
                _controlState.update { it.copy(screenIndex = newIndex) }
            }
        }

        viewModelScope.launch {
            activeButton.collect { activeBtn ->
                _controlState.update {
                    it.copy(activeColorButton = activeBtn.ordinal)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            controlState
                .collect { state ->
                    if (phoneConnection.value) {
                        wearSyncStateRepository.sendState(state)
                    }
                }
        }
    }

    fun select(button: Active) {
        if (button == Active.FLASH) {
            switchFlash(true)
        } else {
            _active.value = button
            writePref(KEY_ACTIVE_COLOR, button.ordinal)
            val s = sosOn.value
            switchFlash(false)
            if (s) {
                toggleSos(true)
            }
        }

        if (!sosOn.value) {
            wearCtrl.setBlinking(blinkOn.value, blinkDelay.value)
        } else {
            toggleSos(true)
        }
    }

    fun switchFlash(isOn: Boolean) {
        if (!isOn) {
            if (sosOn.value) {
                toggleSos(false)
            }
        }
        wearCtrl.setFlash(isOn)
    }

    fun toggleControl(on: Boolean) {
        if (on) {
            if (sosOn.value) {
                val f = flashOn.value
                toggleSos(false)
                wearCtrl.setFlash(f)
            }
        }
        wearCtrl.setBlinking(on, blinkDelay.value)
    }

    fun updateBlinkDelay(ms: Long) {
        wearCtrl.setBlinking(blinkOn.value, ms)
    }

    val activeColor: StateFlow<Color> = activeButton
        .combine(middleColor) { activeBtn, midCol ->
            when (activeBtn) {
                Active.RED -> Color.Companion.Red
                Active.MIDDLE -> midCol
                Active.GREEN -> Color.Companion.Green
                Active.FLASH -> Color.Companion.Black
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.Eagerly,
            initialValue = Color.Companion.Red
        )

    fun toggleSos(on: Boolean) {
        wearCtrl.switchSOS(on)
    }

    fun updateColorProgress(newProgress: Float) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_COLOR_PROGRESS] = newProgress
            }
        }
    }

    fun updateFrequencyProgress(newFrequencyProgress: Float) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_FREQUENCY_PROGRESS] = newFrequencyProgress
            }
        }
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        wearCtrl.updateScreenIndex(newScreenIndex)
    }

    fun togglePhoneConnection(isConnect: Boolean) {
        wearCtrl.togglePhoneConnection(isConnect)
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun performWearOperations(command: String) {
        viewModelScope.launch {
            wearPhoneOperations.performWearOperations(command)
        }
    }

    override fun onCleared() {
        super.onCleared()
        wearSyncStateRepository.clear()
        wearCtrl.clearAll()
    }

}