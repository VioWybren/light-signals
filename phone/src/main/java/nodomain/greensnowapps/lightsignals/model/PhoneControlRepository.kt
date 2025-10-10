package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nodomain.greensnowapps.lightsignals.ACTION_PERMISSIONS_MISSING
import nodomain.greensnowapps.lightsignals.EXTRA_MISSING_CAMERA
import nodomain.greensnowapps.lightsignals.EXTRA_MISSING_NOTIFICATIONS
import nodomain.greensnowapps.lightsignals.PREFERENCE_ACTIVE_SCREEN
import nodomain.greensnowapps.lightsignals.PREFERENCE_ACTIVE_SOUND
import nodomain.greensnowapps.lightsignals.PREFERENCE_BLINK_DELAY
import nodomain.greensnowapps.lightsignals.PREFERENCE_BLINK_DON
import nodomain.greensnowapps.lightsignals.PREFERENCE_FLASH_ON
import nodomain.greensnowapps.lightsignals.PREFERENCE_PREF_ACTIVE_COLOR_BUTTON
import nodomain.greensnowapps.lightsignals.PREFERENCE_PROGRESS_COLOR_PICKER
import nodomain.greensnowapps.lightsignals.PREFERENCE_PROGRESS_FREQUENCY_PICKER
import nodomain.greensnowapps.lightsignals.PREFERENCE_SIGNAL_SCREEN_COLOR
import nodomain.greensnowapps.lightsignals.PREFERENCE_SOS_ON
import nodomain.greensnowapps.lightsignals.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import nodomain.greensnowapps.lightsignals.utils.PermissionUtils
import javax.inject.Inject
import javax.inject.Singleton


internal val KEY_FLASH_ON = booleanPreferencesKey(PREFERENCE_FLASH_ON)
internal val KEY_BLINK_DELAY = longPreferencesKey(PREFERENCE_BLINK_DELAY)
internal val KEY_BLINK_ON = booleanPreferencesKey(PREFERENCE_BLINK_DON)
internal val KEY_ACTIVE_SOUND = intPreferencesKey(PREFERENCE_ACTIVE_SOUND)
internal val KEY_SOS_ON = booleanPreferencesKey(PREFERENCE_SOS_ON)
internal val KEY_COLOR_PROGRESS = floatPreferencesKey(PREFERENCE_PROGRESS_COLOR_PICKER)
internal val KEY_FREQUENCY_PROGRESS = floatPreferencesKey(PREFERENCE_PROGRESS_FREQUENCY_PICKER)

internal val KEY_PREF_ACTIVE_COLOR_BUTTON = intPreferencesKey(PREFERENCE_PREF_ACTIVE_COLOR_BUTTON)
internal val KEY_ACTIVE_SCREEN = intPreferencesKey(PREFERENCE_ACTIVE_SCREEN)
internal val KEY_SIGNAL_SCREEN_COLOR = intPreferencesKey(PREFERENCE_SIGNAL_SCREEN_COLOR)

@Singleton
class PhoneControlRepository @OptIn(ExperimentalHorologistApi::class)
@Inject constructor(
    @param: ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val phoneSyncStateRepository: PhoneSyncStateRepository,
    private val soundPool: PhoneSoundPoolPlayer,
) {

    private enum class CallOrigin {
        APP_START,
        RUNTIME
    }

    private val initialScreenIndex: Int = runBlocking(Dispatchers.IO) {
        dataStore.data.first()[KEY_ACTIVE_SCREEN] ?: 0
    }

    private var initialActiveBtnIndex: Int = runBlocking(Dispatchers.IO) {
        dataStore.data.first()[KEY_PREF_ACTIVE_COLOR_BUTTON] ?: 0
    }

    private val _flashOn = MutableStateFlow(false)
    private val _colorBlinkActive = MutableStateFlow(false)
    private val _blinkDelay = MutableStateFlow(500L)
    private val _sosOn = MutableStateFlow(false)
    private val _blinkOn = MutableStateFlow(false)
    private val _activeSound = MutableStateFlow(0)
    private val _screenIndex = MutableStateFlow(initialScreenIndex)

    val flashOn: StateFlow<Boolean> = _flashOn.asStateFlow()
    val colorBlinkActive: StateFlow<Boolean> = _colorBlinkActive.asStateFlow()
    val blinkDelay: StateFlow<Long> = _blinkDelay.asStateFlow()
    val sosOn: StateFlow<Boolean> = _sosOn.asStateFlow()
    val blinkOn: StateFlow<Boolean> = _blinkOn.asStateFlow()
    val activeSound: StateFlow<Int> = _activeSound
    val screenIndex: StateFlow<Int> = _screenIndex.asStateFlow()

    private lateinit var repoScope: CoroutineScope
    private var colorJob: Job? = null
    private var sosJob: Job? = null

    private val _activeButton = MutableStateFlow(
        Active.entries.getOrNull(initialActiveBtnIndex) ?: Active.RED
    )

    val activeButton: StateFlow<Active> = _activeButton.asStateFlow()

    fun initFromPrefs() {
        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        repoScope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .take(1)
                .collect { prefs ->

                    val pcs = PhoneControlState.Companion.fromDataStore(dataStore)

                    _blinkOn.value = pcs.blinkEnabled
                    _flashOn.value = pcs.torchOn
                    _blinkDelay.value = pcs.frequency
                    _activeSound.value = pcs.activeSound
                    _activeButton.value = Active.entries.getOrNull(pcs.activeColorButton) ?: Active.RED
                    _screenIndex.value = pcs.screenIndex
                    updateSos(pcs.sosEnabled)

                    getWearState()
                    initBlink()
                    tryStartServiceIfPermissionsGranted(CallOrigin.APP_START)
                }
        }
        initSound()
    }

    private fun getWearState() {
        repoScope.launch {
            phoneSyncStateRepository.stateFlow
                .filterNotNull()
                .collect { controlState ->
                    _blinkOn.value = controlState.blinkEnabled
                    setActiveSound(controlState.activeSound)
                    _screenIndex.value = controlState.screenIndex
                    updateSos(controlState.sosEnabled)
                    updateColorProgress(controlState.color)
                    updateFlash(controlState.torchOn)
                    updateFrequencyProgress(controlState.frequency.toFloat())
                    updateActiveButton(Active.entries[controlState.activeColorButton])
                }
        }
    }

    private fun updateSos(newSos: Boolean) {
        _sosOn.value != newSos
        switchSOS(newSos)
    }

    private suspend fun updateFlash(newFlash: Boolean) {
        if (_flashOn.value != newFlash) {
            if (newFlash) select(Active.FLASH)
            else select(_activeButton.value)
        }
    }

    suspend fun updateColorProgress(newColorProgress: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_COLOR_PROGRESS] = newColorProgress
        }
    }

    suspend fun updateFrequencyProgress(newFrequencyProgress: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_FREQUENCY_PROGRESS] = newFrequencyProgress
        }
    }

    private suspend fun updateActiveButton(newActiveButton: Active) {
        dataStore.edit { preferences ->
            preferences[KEY_PREF_ACTIVE_COLOR_BUTTON] = newActiveButton.ordinal
        }
        _activeButton.value = newActiveButton
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        _screenIndex.value = newScreenIndex
        writePref(KEY_ACTIVE_SCREEN, newScreenIndex)
    }

    private fun initSound() {
        if (colorBlinkActive.value && activeSound.value != 0) {
            soundPool.playOnce(activeSound.value)
        }
    }

    private fun initBlink() {
        repoScope.launch {
            blinkOn.collect {
                setBlinking(_blinkOn.value, _blinkDelay.value)
            }
        }
    }

    suspend fun select(button: Active) {
        if (button == Active.FLASH) {
            switchFlash(true)
        } else {
            updateActiveButton(button)
            val s = sosOn.value
            switchFlash(false)
            if (s) {
                switchSOS(true)
            }
        }
        if (!sosOn.value) {
            setBlinking(blinkOn.value, blinkDelay.value)
        } else {
            switchSOS(true)
        }
    }

    fun switchFlash(isOn: Boolean) {
        if (!isOn) {
            if (sosOn.value) {
                switchSOS(false)
            }
        }
        setFlash(isOn)
    }

    fun setBlinking(blinkOn: Boolean, delayMs: Long) {
        _blinkDelay.value = delayMs
        writePref(KEY_BLINK_DELAY, delayMs)
        if (blinkOn) {
            if (_flashOn.value) {
                _blinkOn.value = true
                stopColorBlink()
            } else {
                _flashOn.value = false
                _blinkOn.value = true
                startColorBlink(delayMs)
            }
        } else {
            _blinkOn.value = false
            stopColorBlink()
        }
        writePref(KEY_BLINK_ON, blinkOn)
    }

    fun setFlash(on: Boolean) {
        _flashOn.value = on
        writePref(KEY_FLASH_ON, on)
        if (on) {
            tryStartServiceIfPermissionsGranted(CallOrigin.RUNTIME)
        }
        if (!on) {
            if (_blinkOn.value) {
                setBlinking(true, _blinkDelay.value)
            }
        }
    }

    fun setFlashBlinkMode(on: Boolean) {
        if (_flashOn.value) {
            _blinkOn.value = on
            writePref(KEY_BLINK_ON, on)
        }
        _flashOn.value = on
        writePref(KEY_FLASH_ON, on)
    }

    private fun startColorBlink(delayMs: Long) {
        setColorSOS(false)
        _blinkOn.value = true
        colorJob?.cancel()
        colorJob = repoScope.launch {
            while (isActive && _blinkOn.value) {
                _colorBlinkActive.value = true
                delay(delayMs)
                _colorBlinkActive.value = false
                delay(delayMs)
            }
        }
    }

    private fun stopColorBlink() {
        colorJob?.cancel()
        colorJob = null
        _colorBlinkActive.value = false
    }

    fun switchSOS(on: Boolean) {
        if (_flashOn.value) {
            setColorSOS(false)
            setFlashSos(on)
        } else {
            setFlashSos(false)
            setColorSOS(on)
        }
    }

    private fun setColorSOS(on: Boolean) {
        _sosOn.value = on
        if (on) {
            stopColorBlink()
            _blinkOn.value = false
            writePref(KEY_BLINK_ON, false)
            startColorSos()
        } else {
            stopColorSOS()
        }
    }

    private fun stopColorSOS() {
        sosJob?.cancel()
        writePref(KEY_SOS_ON, false)
    }

    private fun startColorSos() {
        writePref(KEY_SOS_ON, true)
        sosJob?.cancel()
        sosJob = repoScope.launch {
            val dot = SOS_UNIT_MS
            val dash = 3 * SOS_UNIT_MS
            val gap = SOS_UNIT_MS
            val letter = 3 * SOS_UNIT_MS
            val word = 7 * SOS_UNIT_MS

            suspend fun signal(duration: Long) {
                _colorBlinkActive.value = true
                delay(duration)
                _colorBlinkActive.value = false
                delay(gap)
            }

            while (isActive && _sosOn.value) {
                repeat(3) { signal(dot) }
                delay(letter - gap)
                repeat(3) { signal(dash) }
                delay(letter - gap)
                repeat(3) { signal(dot) }
                delay(word - gap)
            }
        }
    }

    private fun setFlashSos(on: Boolean) {
        if (on) {
            stopColorBlink()
            startFlashSos()
        } else stopFlashSos()
    }

    private fun startFlashSos() {
        _sosOn.value = true
        _blinkOn.value = false
    }

    private fun stopFlashSos() {
        val sosF = _sosOn.value
        _sosOn.value = false
        if (!_blinkOn.value) {
            _flashOn.value = false
        }
        if (sosF) {
            _sosOn.value = true
            setColorSOS(true)
        }
    }

    fun setActiveSound(n: Int) {
        _activeSound.value = n.coerceIn(0, 3)
        writePref(KEY_ACTIVE_SOUND, n)
    }

    private fun notifyUiPermissionsMissing() {
        val intent = Intent(ACTION_PERMISSIONS_MISSING)
        intent.putExtra(EXTRA_MISSING_CAMERA, !PermissionUtils.hasCameraPermission(context))
        intent.putExtra(
            EXTRA_MISSING_NOTIFICATIONS,
            !PermissionUtils.hasPostNotificationsPermission(context)
        )
        context.sendBroadcast(intent)
    }

    private fun tryStartServiceIfPermissionsGranted(callOrigin: CallOrigin = CallOrigin.RUNTIME): Boolean {
        if (!PermissionUtils.hasPostNotificationsPermission(context)) {
            if (callOrigin == CallOrigin.RUNTIME) {
                notifyUiPermissionsMissing()
            }
            return false
        }
        if (!PermissionUtils.hasCameraPermission(context)) {
            if (callOrigin == CallOrigin.RUNTIME) {
                notifyUiPermissionsMissing()
            }
            return false
        } else {
            return startService()
        }
    }

    private fun startService(): Boolean {
        try {
            val flashLightServiceIntent = Intent(context, FlashLightService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, flashLightServiceIntent)
            } else {
                context.startService(flashLightServiceIntent)
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun clearAll() {
        colorJob?.cancel()
        sosJob?.cancel()
        repoScope.cancel()
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        repoScope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

}


