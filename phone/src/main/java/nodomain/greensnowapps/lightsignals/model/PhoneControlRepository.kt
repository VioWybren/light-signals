package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette
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
import nodomain.greensnowapps.lightsignals.core.signal.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.core.signal.runSosSignal
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchModeChange
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException

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
class PhoneControlRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val remoteStates: StateFlow<PhoneControlState?>,
    private val startTorchService: () -> Boolean,
    private val stopTorchService: () -> Unit,
    private val repoScope: CoroutineScope,
    private val preferenceScope: CoroutineScope,
    private val onPreferenceError: (IOException) -> Unit = {
        Log.w("PhoneControlRepository", "Unable to access saved settings", it)
    }
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
        phoneSyncStateRepository: PhoneSyncStateRepository
    ) : this(
        dataStore,
        phoneSyncStateRepository.stateFlow,
        { tryStartService(context) },
        { context.stopService(Intent(context, FlashLightService::class.java)); Unit },
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    // UI commands, restoration, remote commands and signal jobs all mutate state on Main.
    private var sessionJob: Job? = null
    private var signalJob: Job? = null
    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()
    private val _flashOn = MutableStateFlow(false)
    private val _colorBlinkActive = MutableStateFlow(false)
    private val _blinkDelay = MutableStateFlow(500L)
    private val _sosOn = MutableStateFlow(false)
    private val _blinkOn = MutableStateFlow(false)
    private val _activeSound = MutableStateFlow(0)
    private val _screenIndex = MutableStateFlow(0)
    private val _activeButton = MutableStateFlow(Active.RED)
    private val _colorProgress = MutableStateFlow(SignalColorPalette.DEFAULT_PROGRESS)

    private val _torchNotificationRefreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal val torchNotificationRefreshes = _torchNotificationRefreshes.asSharedFlow()

    val flashOn = _flashOn.asStateFlow()
    val colorBlinkActive = _colorBlinkActive.asStateFlow()
    val blinkDelay = _blinkDelay.asStateFlow()
    val sosOn = _sosOn.asStateFlow()
    val blinkOn = _blinkOn.asStateFlow()
    val activeSound = _activeSound.asStateFlow()
    val screenIndex = _screenIndex.asStateFlow()
    val activeButton = _activeButton.asStateFlow()
    val colorProgress = _colorProgress.asStateFlow()

    internal fun connectionSignal() = PhoneSignalState(
        torchOn = _flashOn.value,
        blinkEnabled = _blinkOn.value,
        sosEnabled = _sosOn.value,
        frequency = _blinkDelay.value,
        color = _colorProgress.value,
        activeSound = _activeSound.value,
        screenIndex = _screenIndex.value,
        activeColorButton = _activeButton.value.ordinal
    )

    internal fun applyWatchHandoff(signal: PhoneSignalState): Boolean {
        if (!_initialized.value) return false
        val state = PhoneControlState(
            blinkEnabled = signal.blinkEnabled,
            sosEnabled = signal.sosEnabled,
            torchOn = signal.torchOn,
            frequency = signal.frequency,
            activeSound = signal.activeSound,
            color = signal.color,
            screenIndex = signal.screenIndex,
            activeColorButton = signal.activeColorButton
        )
        // Check synchronous startup before changing any other state.
        val startingTorch = state.torchOn && !_flashOn.value
        if (startingTorch && !startTorchService()) return false
        applyRemoteState(state, startingTorch)
        return true
    }

    internal fun applyWatchControl(control: WatchControl, value: Int): Boolean {
        if (!_initialized.value) return false
        when (control) {
            WatchControl.TORCH -> {
                if (value !in 0..1) return false
                val enabled = value == 1
                applyFlash(enabled)
                return _flashOn.value == enabled
            }
            WatchControl.BLINK, WatchControl.SOS -> return applyWatchMode(control, value)
            WatchControl.FREQUENCY -> {
                if (value !in 100..1000) return false
                applyBlinking(_blinkOn.value, value.toLong())
                writePref(KEY_FREQUENCY_PROGRESS, value.toFloat())
            }
            WatchControl.COLOR -> {
                val progress = Float.fromBits(value)
                if (!progress.isFinite()) return false
                applyColorProgress(progress)
            }
            WatchControl.COLOR_BUTTON -> {
                val button = Active.entries.getOrNull(value)
                    ?.takeUnless { it == Active.FLASH } ?: return false
                applySelection(button)
            }
            WatchControl.SCREEN -> {
                if (value !in 0..1) return false
                applyScreenIndex(value)
            }
            WatchControl.SOUND -> {
                if (value !in 0..3) return false
                applySound(value)
            }
            WatchControl.CHECK, WatchControl.DISCONNECT -> return false
        }
        return true
    }

    private fun applyWatchMode(control: WatchControl, value: Int): Boolean {
        val change = WatchModeChange.fromWireValue(value) ?: return false
        val blink = control == WatchControl.BLINK
        val enabled = change != WatchModeChange.DISABLE
        val useTorch = change == WatchModeChange.ENABLE_ON_TORCH
        if (enabled && _flashOn.value != useTorch) {
            // Apply the new mode with its output, using the current phone settings for other controls.
            return applyWatchHandoff(connectionSignal().copy(
                torchOn = useTorch,
                blinkEnabled = blink,
                sosEnabled = !blink
            ))
        }
        if (blink) applyBlinking(enabled, _blinkDelay.value) else applySos(enabled)
        return true
    }

    fun initFromPrefs() {
        if (sessionJob?.isActive == true) return
        sessionJob = repoScope.launch {
            val saved = PhoneControlState.fromPreferences(dataStore.safePreferences(onPreferenceError).first())
            _blinkDelay.value = saved.frequency
            _blinkOn.value = saved.blinkEnabled && !saved.sosEnabled
            _sosOn.value = saved.sosEnabled
            _activeSound.value = saved.activeSound
            _screenIndex.value = saved.screenIndex
            _activeButton.value = Active.entries[saved.activeColorButton]
            _colorProgress.value = saved.color
            // A previous session's torch is never restarted without a new command.
            _flashOn.value = false
            refreshScreenSignal()
            _initialized.value = true

            remoteStates.filterNotNull().collect { incoming ->
                if (remoteStates.value === incoming) applyRemoteState(incoming)
            }
        }
    }

    private fun applyRemoteState(incoming: PhoneControlState, torchAlreadyStarted: Boolean = false) {
        val state = incoming.validated()
        _activeButton.value = Active.entries[state.activeColorButton]
        _activeSound.value = state.activeSound
        _screenIndex.value = state.screenIndex
        _blinkDelay.value = state.frequency
        _sosOn.value = state.sosEnabled
        _blinkOn.value = state.blinkEnabled && !state.sosEnabled
        _colorProgress.value = state.color
        applyFlash(state.torchOn, torchAlreadyStarted)
        preferenceScope.launch { dataStore.editSafely(onPreferenceError) { prefs ->
            prefs[KEY_ACTIVE_SOUND] = state.activeSound
            prefs[KEY_ACTIVE_SCREEN] = state.screenIndex
            prefs[KEY_PREF_ACTIVE_COLOR_BUTTON] = state.activeColorButton
            prefs[KEY_COLOR_PROGRESS] = state.color
            prefs[KEY_FREQUENCY_PROGRESS] = state.frequency.toFloat()
            prefs[KEY_BLINK_DELAY] = state.frequency
            prefs[KEY_BLINK_ON] = state.blinkEnabled && !state.sosEnabled
            prefs[KEY_SOS_ON] = state.sosEnabled
        } }
    }

    fun updateColorProgress(progress: Float) = applyColorProgress(progress)

    private fun applyColorProgress(progress: Float) {
        _colorProgress.value = SignalColorPalette.clampProgress(progress)
        writePref(KEY_COLOR_PROGRESS, _colorProgress.value)
    }

    fun updateFrequencyProgress(progress: Float) {
        val value = progress.takeIf { it.isFinite() }?.coerceIn(100f, 1000f) ?: 500f
        writePref(KEY_FREQUENCY_PROGRESS, value)
    }

    fun updateScreenIndex(index: Int) {
        val page = index.coerceIn(0, 1)
        if (_screenIndex.value != page) applyScreenIndex(page)
    }

    private fun applyScreenIndex(index: Int) {
        _screenIndex.value = index.coerceIn(0, 1)
        writePref(KEY_ACTIVE_SCREEN, _screenIndex.value)
    }

    fun select(button: Active) = applySelection(button)

    private fun applySelection(button: Active) {
        if (button == Active.FLASH) {
            applyFlash(true)
        } else {
            _activeButton.value = button
            writePref(KEY_PREF_ACTIVE_COLOR_BUTTON, button.ordinal)
            applyFlash(false)
        }
    }

    fun switchFlash(on: Boolean) = setFlash(on)

    internal fun refreshTorchNotification() {
        _torchNotificationRefreshes.tryEmit(Unit)
    }

    fun setFlash(on: Boolean) = applyFlash(on)

    private fun applyFlash(on: Boolean, torchAlreadyStarted: Boolean = false) {
        val wasOn = _flashOn.value
        _flashOn.value = on
        if (on && !wasOn && !torchAlreadyStarted && !startTorchService()) _flashOn.value = false
        writePref(KEY_FLASH_ON, _flashOn.value)
        if (!_flashOn.value) stopTorchService()
        refreshScreenSignal()
    }

    fun setBlinking(on: Boolean, delayMs: Long) = applyBlinking(on, delayMs)

    private fun applyBlinking(on: Boolean, delayMs: Long) {
        _blinkDelay.value = delayMs.coerceIn(100L, 1000L)
        _blinkOn.value = on
        if (on) {
            _sosOn.value = false
            writePref(KEY_SOS_ON, false)
        }
        writePref(KEY_BLINK_DELAY, _blinkDelay.value)
        writePref(KEY_BLINK_ON, on)
        refreshScreenSignal()
    }

    fun switchSOS(on: Boolean) = applySos(on)

    private fun applySos(on: Boolean) {
        _sosOn.value = on
        if (on) {
            _blinkOn.value = false
            writePref(KEY_BLINK_ON, false)
        }
        writePref(KEY_SOS_ON, on)
        refreshScreenSignal()
    }

    private fun refreshScreenSignal() {
        signalJob?.cancel()
        signalJob = null
        _colorBlinkActive.value = false
        if (_flashOn.value) return
        if (_sosOn.value) {
            signalJob = repoScope.launch {
                runSosSignal(SOS_UNIT_MS) { enabled ->
                    _colorBlinkActive.value = enabled
                    true
                }
            }
        } else if (_blinkOn.value) {
            val delayMs = _blinkDelay.value
            signalJob = repoScope.launch {
                while (isActive) {
                    _colorBlinkActive.value = true
                    delay(delayMs)
                    _colorBlinkActive.value = false
                    delay(delayMs)
                }
            }
        }
    }

    fun setActiveSound(sound: Int) = applySound(sound)

    private fun applySound(sound: Int) {
        _activeSound.value = sound.coerceIn(0, 3)
        writePref(KEY_ACTIVE_SOUND, _activeSound.value)
    }

    fun onTorchServiceUnavailable() {
        _flashOn.value = false
        writePref(KEY_FLASH_ON, false)
        refreshScreenSignal()
    }

    private companion object {
        fun tryStartService(context: Context): Boolean {
            return try {
                val intent = Intent(context, FlashLightService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (error: RuntimeException) {
                Log.w("PhoneControlRepository", "Unable to start torch service", error)
                false
            }
        }
    }

    fun clearAll() {
        sessionJob?.cancel()
        sessionJob = null
        signalJob?.cancel()
        signalJob = null
        _colorBlinkActive.value = false
        _initialized.value = false
        _flashOn.value = false
        writePref(KEY_FLASH_ON, false)
        stopTorchService()
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        preferenceScope.launch { dataStore.editSafely(onPreferenceError) { it[key] = value } }
    }
}


