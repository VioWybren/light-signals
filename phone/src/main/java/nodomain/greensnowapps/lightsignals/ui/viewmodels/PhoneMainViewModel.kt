package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.content.Intent
import android.os.Process
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.appContext
import nodomain.greensnowapps.lightsignals.model.FlashLightService
import nodomain.greensnowapps.lightsignals.model.KEY_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.model.KEY_FREQUENCY_PROGRESS
import nodomain.greensnowapps.lightsignals.model.KEY_PREF_ACTIVE_COLOR_BUTTON
import nodomain.greensnowapps.lightsignals.model.KEY_SIGNAL_SCREEN_COLOR
import nodomain.greensnowapps.lightsignals.model.PhoneControlRepository
import nodomain.greensnowapps.lightsignals.model.PhoneSoundPoolPlayer
import nodomain.greensnowapps.lightsignals.model.PhoneSyncStateRepository
import nodomain.greensnowapps.lightsignals.model.PhoneWearOperations
import nodomain.greensnowapps.lightsignals.utils.PhoneUtils
import javax.inject.Inject


@HiltViewModel
class PhoneMainViewModel @Inject constructor(
    private val phoneCtrl: PhoneControlRepository,
    private val dataStore: DataStore<Preferences>,
    private val phoneSyncStateRepository: PhoneSyncStateRepository,
    private val phoneWearOperations: PhoneWearOperations,
    private val phoneSoundPoolPlayer: PhoneSoundPoolPlayer
) : ViewModel() {

companion object {
    enum class Active { RED, MIDDLE, GREEN, FLASH }
}

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
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), initialProgressColor)

    private val initialColorIndex: Int = runBlocking(Dispatchers.IO) {
        dataStore.data.first()[KEY_SIGNAL_SCREEN_COLOR] ?: 0
    }


    private val _middleColor =
        MutableStateFlow(PhoneUtils.computeSpecialColor(initialProgressColor.toInt()))
    val middleColor: StateFlow<Color> = _middleColor.asStateFlow()

    val flashOn = phoneCtrl.flashOn
    val blinkOn = phoneCtrl.blinkOn
    val colorBlinkActive = phoneCtrl.colorBlinkActive
    val blinkDelay = phoneCtrl.blinkDelay
    val screenIndex = phoneCtrl.screenIndex
    val activeButton = phoneCtrl.activeButton
    val sosOn = phoneCtrl.sosOn

    val controlMessageFlow = phoneWearOperations.controlMessageFlow
    val isLaunchingOnWear = phoneWearOperations.isLaunchingOnWear

    val uiActiveButton: Flow<Active> = flow {
        val prefs = dataStore.data.first()
        val savedIndex = prefs[KEY_PREF_ACTIVE_COLOR_BUTTON] ?: 0
        val initial = Active.entries.getOrNull(savedIndex) ?: Active.RED
        emit(initial)
        emitAll(activeButton)
    }

    val activeColor: StateFlow<Color> = uiActiveButton
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
            initialValue = Color(initialColorIndex)
        )


    init {
        viewModelScope.launch {
            phoneSyncStateRepository.initFromViewModel()
        }
        viewModelScope.launch {
            phoneCtrl.initFromPrefs()
        }

        viewModelScope.launch {
            progressColor.collect { prog ->
                _middleColor.value = PhoneUtils.computeSpecialColor(prog.toInt())
            }
        }

        viewModelScope.launch {
            activeColor.filterNotNull()
                .collect { color ->
                    writePref(KEY_SIGNAL_SCREEN_COLOR, color.toArgb())
                }
        }
    }

    fun select(button: Active) {
        viewModelScope.launch {
            phoneCtrl.select(button)
        }
    }

    fun switchFlash(isOn: Boolean) {
        phoneCtrl.switchFlash(isOn)
    }

    fun toggleControl(on: Boolean) {
        if (on) {
            if (sosOn.value) {
                val f = flashOn.value
                toggleSos(false)
                phoneCtrl.setFlash(f)
            }
        }
        phoneCtrl.setBlinking(on, blinkDelay.value)
    }

    fun updateBlinkDelay(ms: Long) {
        phoneCtrl.setBlinking(blinkOn.value, ms)
    }

    fun toggleSos(on: Boolean) {
        phoneCtrl.switchSOS(on)
    }

    fun updateColorProgress(newColorProgress: Float) {
        viewModelScope.launch {
            phoneCtrl.updateColorProgress(newColorProgress)
        }
    }

    fun updateFrequencyProgress(newFrequencyProgress: Float) {
        viewModelScope.launch {
            phoneCtrl.updateFrequencyProgress(newFrequencyProgress)
        }
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        viewModelScope.launch {
            phoneCtrl.updateScreenIndex(newScreenIndex)
        }
    }

    fun startWearOperations(command: String) {
        viewModelScope.launch {
            phoneWearOperations.startWearOperations(command)
        }
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    private fun stopFlashLightService() {
        val serviceFlashLightIntent = Intent(appContext, FlashLightService::class.java)
        appContext.stopService(serviceFlashLightIntent)
    }

    override fun onCleared() {
        super.onCleared()
        phoneCtrl.clearAll()
        phoneSoundPoolPlayer.release()
        stopFlashLightService()
        Process.killProcess(Process.myPid())
    }
}