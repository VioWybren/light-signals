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
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchModeChange
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stable preferences key for the torch selection.
 */
internal val KEY_FLASH_ON = booleanPreferencesKey(PREFERENCE_FLASH_ON)
/**
 * Stable preferences key for the blink half-period in milliseconds.
 */
internal val KEY_BLINK_DELAY = longPreferencesKey(PREFERENCE_BLINK_DELAY)
/**
 * Stable preferences key for the Blink selection.
 */
internal val KEY_BLINK_ON = booleanPreferencesKey(PREFERENCE_BLINK_DON)
/**
 * Stable preferences key for the selected sample number.
 */
internal val KEY_ACTIVE_SOUND = intPreferencesKey(PREFERENCE_ACTIVE_SOUND)
/**
 * Stable preferences key for the SOS selection.
 */
internal val KEY_SOS_ON = booleanPreferencesKey(PREFERENCE_SOS_ON)
/**
 * Stable preferences key for the encoded custom-colour position.
 */
internal val KEY_COLOR_PROGRESS = floatPreferencesKey(PREFERENCE_PROGRESS_COLOR_PICKER)
/**
 * Stable preferences key for the frequency-slider interval.
 */
internal val KEY_FREQUENCY_PROGRESS = floatPreferencesKey(PREFERENCE_PROGRESS_FREQUENCY_PICKER)
/**
 * Stable preferences key for the selected colour/output ordinal.
 */
internal val KEY_PREF_ACTIVE_COLOR_BUTTON = intPreferencesKey(PREFERENCE_PREF_ACTIVE_COLOR_BUTTON)
/**
 * Stable preferences key for the selected pager page.
 */
internal val KEY_ACTIVE_SCREEN = intPreferencesKey(PREFERENCE_ACTIVE_SCREEN)
/**
 * Stable preferences key for the saved signal-screen colour.
 */
internal val KEY_SIGNAL_SCREEN_COLOR = intPreferencesKey(PREFERENCE_SIGNAL_SCREEN_COLOR)

/**
 * Owns phone signal settings, screen phases, preference restoration and watch commands.
 *
 * Exposes read-only state flows to ViewModels. Call state-changing methods on Main; production
 * scopes serialize restoration, UI events and incoming commands there. Preference writes
 * outlive session cleanup, while the foreground service owns physical torch timing.
 *
 * @property dataStore Preferences store used to read and persist settings.
 * @property remoteStates Latest decoded legacy watch state, or null when none is available.
 * @property startTorchService Starts the foreground torch service and reports whether startup
 *     was accepted.
 * @property stopTorchService Stops the foreground torch service.
 * @property repoScope Main-thread scope for phone state restoration and screen-signal jobs.
 * @property preferenceScope Scope for preference writes that outlive foreground/session
 *     cleanup.
 * @property onPreferenceError Receives preference IO failures handled by the repository.
 */
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
    /**
     * Creates production service callbacks and separate Main scopes for control and preference
     * work.
     *
     * Uses the application context for service requests and the transport's legacy state flow.
     * Session cleanup cancels its tracked jobs while independent preference writes can finish;
     * DataStore owns storage dispatching.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     * @param dataStore Preferences store used to read and persist settings.
     * @param phoneSyncStateRepository Phone-side transport and authenticated watch-session
     *     repository.
     */
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

    /**
     * Main job for preference restoration and legacy watch-state collection; cancelled by
     * clearAll.
     */
    // UI commands, restoration, remote commands and signal jobs all mutate state on Main.
    private var sessionJob: Job? = null
    /**
     * Current Main coroutine producing local screen phases; replaced when mode or output
     * changes.
     */
    private var signalJob: Job? = null
    /**
     * Whether phone preferences have been restored and controls are ready.
     *
     * Main-confined mutable state exposed through [initialized].
     */
    private val _initialized = MutableStateFlow(false)
    /**
     * Read-only readiness state; true after preferences have been restored.
     */
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()
    /**
     * Requested phone LED selection, independently of the instantaneous hardware phase.
     *
     * Main-confined mutable state exposed through [flashOn].
     */
    private val _flashOn = MutableStateFlow(false)
    /**
     * Lit phase of the local Blink or SOS screen pattern.
     *
     * Main-confined mutable state exposed through [colorBlinkActive].
     */
    private val _colorBlinkActive = MutableStateFlow(false)
    /**
     * Blink half-period in milliseconds.
     *
     * Main-confined mutable state exposed through [blinkDelay].
     */
    private val _blinkDelay = MutableStateFlow(500L)
    /**
     * SOS selection, with precedence over Blink when settings are imported.
     *
     * Main-confined mutable state exposed through [sosOn].
     */
    private val _sosOn = MutableStateFlow(false)
    /**
     * Blink selection, cleared when SOS is enabled.
     *
     * Main-confined mutable state exposed through [blinkOn].
     */
    private val _blinkOn = MutableStateFlow(false)
    /**
     * Logical sound selection: zero for silence and 1..3 for signal samples.
     *
     * Main-confined mutable state exposed through [activeSound].
     */
    private val _activeSound = MutableStateFlow(0)
    /**
     * Pager selection: zero for controls and one for the signal display.
     *
     * Main-confined mutable state exposed through [screenIndex].
     */
    private val _screenIndex = MutableStateFlow(0)
    /**
     * Selected colour/output enum retained by the phone control repository.
     *
     * Main-confined mutable state exposed through [activeButton].
     */
    private val _activeButton = MutableStateFlow(Active.RED)
    /**
     * Encoded custom-colour position, including supported historical palette encodings.
     *
     * Main-confined mutable state exposed through [colorProgress].
     */
    private val _colorProgress = MutableStateFlow(SignalColorPalette.DEFAULT_PROGRESS)

    /**
     * Non-replaying notification-refresh requests with one buffered event for active collectors.
     */
    private val _torchNotificationRefreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /**
     * Events requesting notification reposting after permission becomes available.
     */
    internal val torchNotificationRefreshes = _torchNotificationRefreshes.asSharedFlow()

    /**
     * Read-only selection state of the phone-torch control; this is not the instantaneous LED
     * phase.
     */
    val flashOn = _flashOn.asStateFlow()
    /**
     * Read-only lit/dark phase used to render and sonify timed screen signals.
     */
    val colorBlinkActive = _colorBlinkActive.asStateFlow()
    /**
     * Read-only blink half-period in milliseconds.
     */
    val blinkDelay = _blinkDelay.asStateFlow()
    /**
     * Read-only selection state of the SOS control.
     */
    val sosOn = _sosOn.asStateFlow()
    /**
     * Read-only selection state of the Blink control.
     */
    val blinkOn = _blinkOn.asStateFlow()
    /**
     * Read-only selected sample number: 0 for silence, 1..3 for a signal sample.
     */
    val activeSound = _activeSound.asStateFlow()
    /**
     * Read-only pager selection: 0 for controls, 1 for the signal display.
     */
    val screenIndex = _screenIndex.asStateFlow()
    /**
     * Read-only selected colour or phone-torch control.
     */
    val activeButton = _activeButton.asStateFlow()
    /**
     * Read-only encoded palette position for the custom-colour control.
     */
    val colorProgress = _colorProgress.asStateFlow()

    /**
     * Captures the current shared phone settings for a watch handoff or reconnect reply.
     *
     * Call on Main to read a coherent configuration alongside state-changing events. The
     * snapshot contains selected modes and output, not the instantaneous LED or screen
     * phase.
     *
     * @return The current signal settings, including torch selection, sound, colour and
     *     pager page.
     */
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

    /**
     * Applies a complete watch configuration after phone controls have initialized.
     *
     * Call on Main after the transport authorizes the handoff. When enabling a previously
     * unselected torch, checks synchronous service startup before changing the remaining
     * settings. Normalizes and applies the snapshot through [applyRemoteState], which
     * queues persistence. Acceptance does not confirm that a physical torch write has
     * completed.
     *
     * @param signal Complete shared configuration received from the watch.
     * @return True when the configuration is accepted; false before initialization or when
     *     an immediate torch-service start fails.
     */
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

    /**
     * Applies a watch command after checking that its attached snapshot agrees with the
     * action.
     *
     * Call on Main after the transport verifies the peer, session and command ordering. A
     * complete snapshot replaces the shared phone settings through [applyWatchHandoff]; a
     * command without a snapshot uses the per-control overload. This method does not
     * perform peer authorization or freshness checks itself.
     *
     * @param command Authorized watch action with an optional complete signal snapshot.
     * @return True when the requested change is accepted; false for an inconsistent
     *     snapshot or a change rejected by the handoff or per-control handler.
     */
    internal fun applyWatchControl(command: WatchControlCommand): Boolean {
        if (!command.hasConsistentSignal()) return false
        // Every user action restores the watch configuration, including its signal mode.
        return command.signal?.let(::applyWatchHandoff) ?: applyWatchControl(command.control, command.value)
    }

    /**
     * Applies one legacy-format watch control value without replacing the entire
     * configuration.
     *
     * Call on Main after transport authorization. Validates the value for its control and
     * queues persistence for accepted changes. Colour-button selection disables torch
     * output; frequency changes retain the existing timed-mode selection. Connection
     * checks and disconnect requests belong to the session layer and are rejected here.
     *
     * @param control Signal setting to change.
     * @param value TORCH: 0 or 1; BLINK/SOS: WatchModeChange wire value; FREQUENCY:
     *     100..1000 milliseconds; COLOR: finite Float bits; COLOR_BUTTON: 0..2; SCREEN: 0
     *     or 1; SOUND: 0..3.
     * @return True when the change is accepted; false before initialization, for an
     *     invalid value or unsupported action, or when the requested torch output cannot
     *     be enabled.
     */
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

    /**
     * Applies a watch Blink or SOS mode change together with its selected screen or torch
     * output.
     *
     * Call on Main. Enabling a mode disables the other timed mode. If the output changes,
     * uses [applyWatchHandoff] so a failed torch-service start leaves the other settings
     * untouched. Disabling a mode preserves the current output and the other mode.
     *
     * @param control BLINK or SOS; the caller filters other control kinds.
     * @param value WatchModeChange wire value specifying disable, screen output or torch
     *     output.
     * @return True when the mode change is accepted; false for an unknown wire value or a
     *     rejected output-changing handoff.
     */
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

    /**
     * Launches preference restoration followed by legacy watch-state collection.
     *
     * Call on Main. Reuses an active session job and returns without waiting for storage.
     * The repoScope coroutine reads a validated preferences snapshot, keeps saved torch
     * output off, restores screen timing and marks controls ready. Read IO failures are
     * reported through onPreferenceError and recover with default preferences.
     *
     * Continues collecting non-null remote states until the session is cancelled, applying
     * only an object that is still the current remote value. [clearAll] cancels
     * restoration and collection; DataStore owns storage dispatching.
     */
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

    /**
     * Normalizes and applies a complete received configuration, then queues its
     * persistence.
     *
     * Call on Main. Gives SOS precedence over Blink, updates shared controls and routes
     * the selected output through [applyFlash]. Queues the remaining settings in one
     * preferences edit; torch selection is persisted separately by [applyFlash].
     * Service-start failure can clear torch selection while retaining the other received
     * settings.
     *
     * @param incoming Received control snapshot whose numeric values will be bounded.
     * @param torchAlreadyStarted True when the caller already started the torch service
     *     for this transition, avoiding a duplicate start request.
     */
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

    /**
     * Updates the custom-colour position and queues its persistence on Main.
     *
     * Delegates palette bounds and non-finite input handling to [applyColorProgress].
     * Changing this value retains the selected output and timed modes.
     *
     * @param progress Encoded shared palette position supplied by the colour slider.
     */
    fun updateColorProgress(progress: Float) = applyColorProgress(progress)

    /**
     * Bounds a custom-colour position, publishes it and queues a preferences write.
     *
     * Call on Main. Uses SignalColorPalette.clampProgress, including its default for
     * non-finite input, without changing the selected colour button or signal mode.
     *
     * @param progress Encoded palette position to normalize and store.
     */
    private fun applyColorProgress(progress: Float) {
        _colorProgress.value = SignalColorPalette.clampProgress(progress)
        writePref(KEY_COLOR_PROGRESS, _colorProgress.value)
    }

    /**
     * Queues persistence of the frequency-slider value without changing live blink timing.
     *
     * Call on Main. Bounds finite input to 100..1000 and replaces non-finite input with
     * 500. Use [setBlinking] to apply a half-period to the running signal as well.
     *
     * @param progress Frequency-slider value representing a blink half-period in
     *     milliseconds.
     */
    fun updateFrequencyProgress(progress: Float) {
        val value = progress.takeIf { it.isFinite() }?.coerceIn(100f, 1000f) ?: 500f
        writePref(KEY_FREQUENCY_PROGRESS, value)
    }

    /**
     * Selects and queues persistence of a pager page when the bounded index changes.
     *
     * Call on Main. Clamps the request to the controls or signal page and avoids writing
     * the same selection again.
     *
     * @param index Requested pager page: 0 for controls or 1 for the signal display.
     */
    fun updateScreenIndex(index: Int) {
        val page = index.coerceIn(0, 1)
        if (_screenIndex.value != page) applyScreenIndex(page)
    }

    /**
     * Publishes a bounded pager selection and queues its persistence on Main.
     *
     * @param index Pager page clamped to 0 for controls or 1 for the signal display.
     */
    private fun applyScreenIndex(index: Int) {
        _screenIndex.value = index.coerceIn(0, 1)
        writePref(KEY_ACTIVE_SCREEN, _screenIndex.value)
    }

    /**
     * Selects a screen colour or enables torch output through [applySelection].
     *
     * Call on Main. A screen-colour choice disables the torch while retaining Blink or
     * SOS; FLASH retains the remembered screen colour for a later return to screen output.
     *
     * @param button Red, custom-colour or green screen control, or FLASH for phone LED
     *     output.
     */
    fun select(button: Active) = applySelection(button)

    /**
     * Routes a colour-button selection to screen or torch output on Main.
     *
     * FLASH enables the torch without replacing the stored screen-colour selection. Other
     * choices publish and persist the selected colour button, then disable torch output.
     * Keeps Blink and SOS selections unchanged.
     *
     * @param button Screen-colour control to select, or FLASH to enable the phone LED.
     */
    private fun applySelection(button: Active) {
        if (button == Active.FLASH) {
            applyFlash(true)
        } else {
            _activeButton.value = button
            writePref(KEY_PREF_ACTIVE_COLOR_BUTTON, button.ordinal)
            applyFlash(false)
        }
    }

    /**
     * Delegates a Main-thread torch-selection change to [setFlash].
     *
     * Preserves the selected screen colour and timed modes while switching the output.
     *
     * @param on True to select torch output, or false to return to screen output.
     */
    fun switchFlash(on: Boolean) = setFlash(on)

    /**
     * Attempts to emit a notification-refresh request for the active torch service.
     *
     * Call on Main after notification permission changes. Returns immediately without
     * waiting for collection. Delivery requires an active collector and buffer capacity;
     * the service checks torch selection before reposting its notification.
     */
    internal fun refreshTorchNotification() {
        _torchNotificationRefreshes.tryEmit(Unit)
    }

    /**
     * Changes torch selection and rebuilds screen-signal routing through [applyFlash].
     *
     * Call on Main. Starts the service only when enabling a previously unselected torch,
     * clears selection if that start fails and stops the service when torch output is off.
     * Preserves the remembered screen colour and timed modes.
     *
     * @param on True to select phone LED output, or false to use the screen.
     */
    fun setFlash(on: Boolean) = applyFlash(on)

    /**
     * Updates torch selection, manages its service and rebuilds screen-signal phases.
     *
     * Call on Main. Starts the service only for an off-to-on transition unless the caller
     * already started it. A failed start restores torch selection to false. Queues the
     * resulting selection for persistence, stops the service when off and refreshes screen
     * timing without clearing Blink or SOS.
     *
     * @param on Requested torch selection; false routes the selected signal to the screen.
     * @param torchAlreadyStarted True when the caller already started the service for this
     *     transition and another startup request is unnecessary.
     */
    private fun applyFlash(on: Boolean, torchAlreadyStarted: Boolean = false) {
        val wasOn = _flashOn.value
        _flashOn.value = on
        if (on && !wasOn && !torchAlreadyStarted && !startTorchService()) _flashOn.value = false
        writePref(KEY_FLASH_ON, _flashOn.value)
        if (!_flashOn.value) stopTorchService()
        refreshScreenSignal()
    }

    /**
     * Updates Blink selection and its half-period through [applyBlinking] on Main.
     *
     * Enabling Blink disables SOS. Disabling Blink keeps SOS unchanged. Bounds and
     * persists the delay even when Blink is disabled, then refreshes screen-signal timing.
     *
     * @param on Whether Blink should be selected.
     * @param delayMs Requested blink half-period, clamped to 100..1000 milliseconds.
     */
    fun setBlinking(on: Boolean, delayMs: Long) = applyBlinking(on, delayMs)

    /**
     * Publishes and persists Blink selection and a bounded half-period on Main.
     *
     * Enabling Blink clears and persists SOS selection; disabling Blink leaves SOS intact.
     * Always stores the bounded delay and refreshes screen phases. Frequency-slider
     * persistence is handled separately by [updateFrequencyProgress] or watch commands.
     *
     * @param on Whether Blink should be selected.
     * @param delayMs Half-period in milliseconds, bounded even when Blink is disabled.
     */
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

    /**
     * Updates SOS selection through [applySos] on Main.
     *
     * Enabling SOS disables Blink; disabling SOS preserves Blink and the selected output.
     *
     * @param on Whether SOS should be selected.
     */
    fun switchSOS(on: Boolean) = applySos(on)

    /**
     * Publishes and persists SOS selection, then refreshes screen phases on Main.
     *
     * Enabling SOS clears and persists Blink selection. Disabling SOS leaves Blink
     * unchanged and preserves the current screen or torch output.
     *
     * @param on Whether SOS should be selected.
     */
    private fun applySos(on: Boolean) {
        _sosOn.value = on
        if (on) {
            _blinkOn.value = false
            writePref(KEY_BLINK_ON, false)
        }
        writePref(KEY_SOS_ON, on)
        refreshScreenSignal()
    }

    /**
     * Replaces the screen-phase job with the currently selected SOS or Blink pattern.
     *
     * Call on Main. Cancels the previous job and resets colorBlinkActive immediately.
     * Leaves the phase false for torch output or an untimed screen signal. Otherwise
     * launches SOS, or Blink with the current half-period, in repoScope. The coroutine
     * suspends between phases; this function returns without waiting and does not drive
     * physical LED hardware.
     */
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
                    delay(delayMs.milliseconds)
                    _colorBlinkActive.value = false
                    delay(delayMs.milliseconds)
                }
            }
        }
    }

    /**
     * Selects and queues persistence of a bounded signal-sample number through
     * [applySound].
     *
     * Call on Main. Playback remains owned by the sound ViewModel or torch service.
     *
     * @param sound Requested sample number, clamped to 0 for silence or 1..3 for a signal
     *     sample.
     */
    fun setActiveSound(sound: Int) = applySound(sound)

    /**
     * Publishes a bounded sound selection and queues its persistence on Main.
     *
     * Updates selection without directly starting or stopping an audio stream.
     *
     * @param sound Sample number clamped to 0 for silence or 1..3 for a signal sample.
     */
    private fun applySound(sound: Int) {
        _activeSound.value = sound.coerceIn(0, 3)
        writePref(KEY_ACTIVE_SOUND, _activeSound.value)
    }

    /**
     * Clears torch selection and resumes the selected screen signal after a service
     * failure.
     *
     * Call on Main. Queues the cleared torch preference and rebuilds screen phases while
     * retaining the selected colour, Blink and SOS. The caller remains responsible for
     * stopping the failed service.
     */
    fun onTorchServiceUnavailable() {
        _flashOn.value = false
        writePref(KEY_FLASH_ON, false)
        refreshScreenSignal()
    }

    /**
     * Provides platform-dependent torch-service startup for the production repository.
     */
    private companion object {
        /**
         * Requests startup of FlashLightService and logs synchronous platform failures.
         *
         * Call on Main. Uses the foreground-service start API on Android 8 and later and
         * startService on earlier versions. The returned result describes the start request,
         * not successful foreground promotion or a completed physical torch write.
         *
         * @param context Application context used to start the service without retaining an
         *     Activity.
         * @return True when the platform start call returns without a runtime exception; false
         *     when a runtime exception is caught and logged.
         */
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

    /**
     * Cancels restoration, remote collection and screen timing, then requests torch
     * shutdown.
     *
     * Call on Main. Clears readiness, the screen phase and torch selection, and queues
     * persistence of torch-off state. Retains the other control selections. The repository
     * and preference scopes remain active, so pending writes can complete and
     * [initFromPrefs] can start a new session.
     */
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

    /**
     * Queues one typed preference update in the independent preferenceScope.
     *
     * Call on Main. Returns without waiting for the edit; DataStore owns storage
     * dispatching. Reports IO failures through onPreferenceError. Cancelling session or
     * screen jobs does not cancel this write; non-IO failures propagate within the write
     * coroutine.
     *
     * @param T Value type associated with the preference key.
     * @param key Typed preference key to update.
     * @param value Value to commit for the key.
     */
    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        preferenceScope.launch { dataStore.editSafely(onPreferenceError) { it[key] = value } }
    }
}


