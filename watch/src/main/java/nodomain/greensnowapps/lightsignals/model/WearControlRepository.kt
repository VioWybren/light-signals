package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nodomain.greensnowapps.lightsignals.WearConstants
import nodomain.greensnowapps.lightsignals.WearConstants.PREFERENCE_ACTIVE_SCREEN
import nodomain.greensnowapps.lightsignals.WearConstants.PREFERENCE_VOLUME
import nodomain.greensnowapps.lightsignals.WearConstants.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.moduls.ApplicationScope
import nodomain.greensnowapps.lightsignals.utils.WearUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WearControlRepository"

// Define preference keys for the DataStore
private val KEY_FLASH_ON = booleanPreferencesKey(WearConstants.PREFERENCE_FLASH_ON)
private val KEY_BLINK_DELAY = longPreferencesKey(WearConstants.PREFERENCE_BLINK_DELAY)
private val KEY_BLINK_ON = booleanPreferencesKey(WearConstants.PREFERENCE_BLINK_DON)
private val KEY_ACTIVE_SOUND = intPreferencesKey(WearConstants.PREFERENCE_ACTIVE_SOUND)
private val KEY_SOS_ON = booleanPreferencesKey(WearConstants.PREFERENCE_SOS_ON)
internal val KEY_COLOR_PROGRESS = floatPreferencesKey(WearConstants.PREFERENCE_PROGRESS_COLOR_PICKER)
internal val KEY_FREQUENCY_PROGRESS = floatPreferencesKey(WearConstants.PREFERENCE_PROGRESS_FREQUENCY_PICKER)
internal val KEY_ACTIVE_SCREEN = intPreferencesKey(PREFERENCE_ACTIVE_SCREEN)
internal val KEY_VOLUME = floatPreferencesKey(PREFERENCE_VOLUME)

@Singleton
class WearControlRepository @OptIn(ExperimentalHorologistApi::class)
@Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val soundPool: WearSoundPoolPlayer,
) {
    private val initialScreenIndex: Int = runBlocking(Dispatchers.IO) {
        dataStore.data.first()[KEY_ACTIVE_SCREEN] ?: 0}

    // StateFlows
    private val _flashOn = MutableStateFlow(false)
    private val _colorBlinkActive = MutableStateFlow(false)
    private val _blinkDelay = MutableStateFlow(500L)
    private val _sosOn = MutableStateFlow(false)
    private val _blinkOn = MutableStateFlow(false)
    private val _activeSound = MutableStateFlow(0)   // 0 = off, 1..3 = which beep
    private val _screenIndex = MutableStateFlow(initialScreenIndex)
    private val _middleColor = MutableStateFlow(Color.Blue)
    private val _phoneConnection = MutableStateFlow(false)

    val flashOn: StateFlow<Boolean> = _flashOn.asStateFlow()
    val colorBlinkActive: StateFlow<Boolean> = _colorBlinkActive.asStateFlow()
    val blinkDelay: StateFlow<Long> = _blinkDelay.asStateFlow()
    val sosOn: StateFlow<Boolean> = _sosOn.asStateFlow()
    val blinkOn: StateFlow<Boolean> = _blinkOn.asStateFlow()
    val activeSound: StateFlow<Int> = _activeSound
    val screenIndex: StateFlow<Int> = _screenIndex.asStateFlow()
    val middleColor: StateFlow<Color> = _middleColor.asStateFlow()
    val phoneConnection = _phoneConnection.asStateFlow()

    private var colorJob: Job? = null
    private var sosJob: Job? = null
    private lateinit var repoScope: CoroutineScope
    private val localJob = SupervisorJob()


    //  Initialize states from DataStore preferences
    fun initFromPrefs() {
        repoScope = CoroutineScope(localJob + Dispatchers.Default)
//        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Use coroutine scope for collecting from DataStore
        repoScope.launch {
//        appScope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }  // Handle errors by emitting empty preferences
                .take(1)            // <-- only one emission
                .collect { prefs ->
                    _flashOn.value = prefs[KEY_FLASH_ON] ?: false
                    _blinkDelay.value = prefs[KEY_BLINK_DELAY] ?: 500L
                    _blinkOn.value = prefs[KEY_BLINK_ON] ?: false
                    _activeSound.value = prefs[KEY_ACTIVE_SOUND] ?: 0
                    _sosOn.value = prefs[KEY_SOS_ON] ?: false
//                    _screenIndex.value = prefs[KEY_ACTIVE_SCREEN] ?: 0

                    Log.d(TAG,"INIT _flashOn.value = ${_flashOn.value}")
                    Log.d(TAG,"INIT _blinkDelay.value = ${_blinkDelay.value}")
                    Log.d(TAG,"INIT _blinkOn.value = ${_blinkOn.value}")
                    Log.d(TAG,"INIT _activeSound.value = ${_activeSound.value}")
                    Log.d(TAG,"INIT _sosOn.value = ${_sosOn.value}")
                    Log.d(TAG,"INIT _screenIndex.value = ${_screenIndex.value}")

                    initSound()
                    initBlink()
                    initSOS()
                }
        }
//        initSound()
    }

    fun initBlink() {
        repoScope.launch {
            blinkOn.collect {
                setBlinking(_blinkOn.value, _blinkDelay.value)
                Log.d(TAG, "_blinkOn.value: ${_blinkOn.value}")
            }
        }
    }

    fun initSound() {
        if (colorBlinkActive.value && _activeSound.value != 0) {
            soundPool.playOnce(_activeSound.value)
        }
    }

    private fun initSOS() {
        Log.d(TAG,"initSOS()")
        if (_sosOn.value) {
            if (!_flashOn.value && !_blinkOn.value) {
                startColorSos()
            }
        }
    }

    /** Toggle blink mode (applies either to hardware flash or to UI color buttons) */
    fun setBlinking(blinkOn: Boolean, delayMs: Long) {
        Log.d(TAG,"setBlinking($blinkOn, $delayMs)")
        _blinkDelay.value = delayMs
        writePref(KEY_BLINK_DELAY, delayMs)

        if (blinkOn) {
            if (_flashOn.value) {
                // Hardware flash blinking
                _blinkOn.value = true
                stopColorBlink()
            } else {
                // In‐App color‐button blinking
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

    /** Turn flash steady on/off */
    fun setFlash(on: Boolean) {
        Log.d(TAG,"setFlash $on")
        _flashOn.value = on
        writePref(KEY_FLASH_ON, on)
        if (!on) {
            // also cancel any blinking
            if (_blinkOn.value) {
                setBlinking(true, _blinkDelay.value)
            }
        }
    }

    private fun startColorBlink(delayMs: Long) {
        Log.d(TAG,"startColorBlink()")
        stopColorSOS()
        _blinkOn.value = true
        colorJob?.cancel()
        colorJob = repoScope.launch {
//        colorJob = appScope.launch {
            while (isActive && _blinkOn.value) {
                _colorBlinkActive.value = true
                delay(delayMs)
                _colorBlinkActive.value = false
                delay(delayMs)
            }
        }
    }

    private fun stopColorBlink() {
        Log.d(TAG,"stopColorBlink()")
        colorJob?.cancel()
        _colorBlinkActive.value = false
    }

    fun switchSOS(on: Boolean) {
        Log.d(TAG,"switchSOS($on)")
        if (_flashOn.value) {
            setColorSOS(false)
            setFlashSos(on)
        } else {
            setFlashSos(false)
            setColorSOS(on)
        }
    }

    fun setColorSOS(on: Boolean) {
        Log.d(TAG,"setColorSOS($on)")
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

    fun stopColorSOS() {
        Log.d(TAG,"stopColorSOS()")
        _sosOn.value = false
        sosJob?.cancel()
        writePref(KEY_SOS_ON, false)
    }

    private fun startColorSos() {
        Log.d(TAG,"startColorSos() _blinkOn.value = ${blinkOn.value}")
        writePref(KEY_SOS_ON, true)
        sosJob?.cancel()
        sosJob = repoScope.launch {
//        sosJob = appScope.launch {
            // SOS pattern: ... --- ...
            val dot = SOS_UNIT_MS
            val dash = 3 * SOS_UNIT_MS
            val gap = SOS_UNIT_MS      // between parts
            val letter = 3 * SOS_UNIT_MS  // between letters
            val word = 7 * SOS_UNIT_MS  // after word

            suspend fun signal(duration: Long) {
                _colorBlinkActive.value = true
                delay(duration)
                _colorBlinkActive.value = false
                delay(gap)
            }

            while (isActive && _sosOn.value) {
                // S: dot dot dot
                repeat(3) { signal(dot) }
                delay(letter - gap)
                // O: dash dash dash
                repeat(3) { signal(dash) }
                delay(letter - gap)
                // S: dot dot dot
                repeat(3) { signal(dot) }
                delay(word - gap)
            }
        }
    }

    fun setFlashSos(on: Boolean) {
        Log.d(TAG," setFlashSos($on)")
        if (on) {
            stopColorBlink()
            startFlashSos()
        } else stopFlashSos()
    }

    private fun startFlashSos() {
        Log.d(TAG,"startFlashSos()")
        _sosOn.value = true
        _blinkOn.value = false
    }

    private fun stopFlashSos() {
        Log.d(TAG,"stopFlashSos()")
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

    //Ensures that this value n lies in the specified range
    fun setActiveSound(n: Int) {
        _activeSound.value = n.coerceIn(0, 3)
        writePref(KEY_ACTIVE_SOUND, n)
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        _screenIndex.value = newScreenIndex
        writePref(KEY_ACTIVE_SCREEN, newScreenIndex)
        Log.d(TAG,"_screenIndex.value ${_screenIndex.value}")
    }

    fun togglePhoneConnection(isConnect: Boolean) {
        _phoneConnection.value = isConnect
        if (!isConnect && _flashOn.value) {
            setFlash(false)
        }
    }

    fun calcMiddleColor(progressColor: Float) {
        _middleColor.value = WearUtils.computeSpecialColor(progressColor.toInt())
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        repoScope.launch {
//      appScope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun clearAll() {
        colorJob?.cancel()
        sosJob?.cancel()
        // cancel everything this repo ever launched
        localJob.cancelChildren()

        // Optional: If you need an explicit stop exposed:
        // Cancel children so the root appScope is still reusable later
//        appScope.coroutineContext[Job]?.cancelChildren()
//        appScope.coroutineContext[Job]?.cancel()

        repoScope.cancel()
    }

}


