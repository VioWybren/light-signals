package nodomain.greensnowapps.lightsignals.model

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.WearConstants
import nodomain.greensnowapps.lightsignals.WearConstants.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.utils.WearUtils
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import android.util.Log

private val KEY_FLASH_ON = booleanPreferencesKey(WearConstants.PREFERENCE_FLASH_ON)
private val KEY_BLINK_DELAY = longPreferencesKey(WearConstants.PREFERENCE_BLINK_DELAY)
private val KEY_BLINK_ON = booleanPreferencesKey(WearConstants.PREFERENCE_BLINK_DON)
private val KEY_ACTIVE_SOUND = intPreferencesKey(WearConstants.PREFERENCE_ACTIVE_SOUND)
private val KEY_SOS_ON = booleanPreferencesKey(WearConstants.PREFERENCE_SOS_ON)
internal val KEY_COLOR_PROGRESS = floatPreferencesKey(WearConstants.PREFERENCE_PROGRESS_COLOR_PICKER)
internal val KEY_FREQUENCY_PROGRESS = floatPreferencesKey(WearConstants.PREFERENCE_PROGRESS_FREQUENCY_PICKER)
internal val KEY_ACTIVE_SCREEN = intPreferencesKey(WearConstants.PREFERENCE_ACTIVE_SCREEN)
internal val KEY_VOLUME = floatPreferencesKey(WearConstants.PREFERENCE_VOLUME)

@Singleton
class WearControlRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val stopSounds: () -> Unit,
    private val newSessionScope: () -> CoroutineScope,
    private val preferenceScope: CoroutineScope,
    private val onPreferenceError: (IOException) -> Unit = {
        Log.w("WearControlRepository", "Unable to access saved settings", it)
    }
) {
    @Inject constructor(
        dataStore: DataStore<Preferences>,
        soundPool: WearSoundPoolPlayer
    ) : this(
        dataStore,
        soundPool::stopAll,
        { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) },
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )
    private val _flashOn = MutableStateFlow(false)
    private val _colorBlinkActive = MutableStateFlow(false)
    private val _blinkDelay = MutableStateFlow(400L)
    private val _sosOn = MutableStateFlow(false)
    private val _blinkOn = MutableStateFlow(false)
    private val _activeSound = MutableStateFlow(0)
    private val _screenIndex = MutableStateFlow(0)
    private val _middleColor = MutableStateFlow(Color.White)
    private val _phoneConnection = MutableStateFlow(false)
    private val _isInitialized = MutableStateFlow(false)
    private val _isForeground = MutableStateFlow(false)

    val flashOn = _flashOn.asStateFlow()
    val colorBlinkActive = _colorBlinkActive.asStateFlow()
    val blinkDelay = _blinkDelay.asStateFlow()
    val sosOn = _sosOn.asStateFlow()
    val blinkOn = _blinkOn.asStateFlow()
    val activeSound = _activeSound.asStateFlow()
    val screenIndex = _screenIndex.asStateFlow()
    val middleColor = _middleColor.asStateFlow()
    val phoneConnection = _phoneConnection.asStateFlow()
    val isInitialized = _isInitialized.asStateFlow()
    val isForeground = _isForeground.asStateFlow()

    private var sessionScope: CoroutineScope? = null
    private var signalJob: Job? = null
    private val foregroundOwners = mutableSetOf<Any>()

    // Writes outlive an Activity stop and retain user-action order.
    private val preferenceWrites =
        Channel<suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit>(Channel.UNLIMITED)

    init {
        preferenceScope.launch {
            for (change in preferenceWrites) dataStore.editSafely(onPreferenceError, change)
        }
    }

    // All foreground/session state is serialized on the main thread.
    fun start(owner: Any) {
        if (!foregroundOwners.add(owner) || _isForeground.value) return
        val scope = newSessionScope()
        sessionScope = scope
        _isForeground.value = true
        scope.launch {
            if (!_isInitialized.value) {
                val prefs = dataStore.safeData(onPreferenceError).first()
                val savedFrequency = prefs[KEY_FREQUENCY_PROGRESS]
                _blinkDelay.value = if (savedFrequency?.isFinite() == true) {
                    savedFrequency.toLong().coerceIn(100L, 1000L)
                } else {
                    (prefs[KEY_BLINK_DELAY] ?: 400L).coerceIn(100L, 1000L)
                }
                _sosOn.value = prefs[KEY_SOS_ON] ?: false
                _blinkOn.value = (prefs[KEY_BLINK_ON] ?: false) && !_sosOn.value
                _activeSound.value = (prefs[KEY_ACTIVE_SOUND] ?: 0).coerceIn(0, 3)
                _screenIndex.value = (prefs[KEY_ACTIVE_SCREEN] ?: 0).coerceIn(0, 1)
                // A previous process's remote torch selection is not an active connection.
                _flashOn.value = false
                _isInitialized.value = true
            }
            restartSignal()
        }
    }

    fun stop(owner: Any) {
        if (!foregroundOwners.remove(owner) || foregroundOwners.isNotEmpty()) return
        _isForeground.value = false
        sessionScope?.cancel()
        sessionScope = null
        signalJob = null
        _colorBlinkActive.value = false
        stopSounds()
    }

    fun setBlinking(blinkOn: Boolean, delayMs: Long) {
        val safeDelay = delayMs.coerceIn(100L, 1000L)
        _blinkDelay.value = safeDelay
        _blinkOn.value = blinkOn
        if (blinkOn && _sosOn.value) {
            _sosOn.value = false
            writePref(KEY_SOS_ON, false)
        }
        writePref(KEY_BLINK_DELAY, safeDelay)
        writePref(KEY_BLINK_ON, blinkOn)
        restartSignal()
    }

    fun setFlash(on: Boolean) {
        _flashOn.value = on
        writePref(KEY_FLASH_ON, on)
        restartSignal()
    }

    fun switchSOS(on: Boolean) {
        _sosOn.value = on
        if (on) {
            _blinkOn.value = false
            writePref(KEY_BLINK_ON, false)
        }
        writePref(KEY_SOS_ON, on)
        restartSignal()
    }

    private fun restartSignal() {
        signalJob?.cancel()
        signalJob = null
        _colorBlinkActive.value = false
        val scope = sessionScope ?: return
        if (!_isInitialized.value || _flashOn.value) return
        signalJob = when {
            _sosOn.value -> scope.launch {
                runSosSignal(SOS_UNIT_MS) { enabled ->
                    _colorBlinkActive.value = enabled
                    true
                }
            }
            _blinkOn.value -> scope.launch {
                while (isActive) {
                    _colorBlinkActive.value = true
                    delay(_blinkDelay.value)
                    _colorBlinkActive.value = false
                    delay(_blinkDelay.value)
                }
            }
            else -> null
        }
    }

    fun setActiveSound(n: Int) {
        val sound = n.coerceIn(0, 3)
        _activeSound.value = sound
        writePref(KEY_ACTIVE_SOUND, sound)
        if (sound == 0) stopSounds()
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        val index = newScreenIndex.coerceIn(0, 1)
        if (_screenIndex.value == index) return
        _screenIndex.value = index
        writePref(KEY_ACTIVE_SCREEN, index)
    }

    fun togglePhoneConnection(isConnect: Boolean) {
        _phoneConnection.value = isConnect
        if (!isConnect && _flashOn.value) setFlash(false)
    }

    fun calcMiddleColor(progressColor: Float) {
        val progress = progressColor.takeIf { it.isFinite() }
            ?: WearConstants.DEFAULT_COLOR_PROGRESS
        _middleColor.value = WearUtils.computeSpecialColor(progress.toInt())
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        preferenceWrites.trySend { prefs -> prefs[key] = value }
    }
}
