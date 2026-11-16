package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.WearConstants
import nodomain.greensnowapps.lightsignals.core.signal.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.core.signal.runSosSignal
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.ACCEPTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.REJECTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.RETRY
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchModeChange
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeReply
import nodomain.greensnowapps.lightsignals.utils.WearUtils
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import java.util.UUID
import android.util.Log
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stable standalone watch preference key for torch selection.
 */
private val KEY_FLASH_ON = booleanPreferencesKey(WearConstants.PREFERENCE_FLASH_ON)
/**
 * Stable standalone watch preference key for blink half-period.
 */
private val KEY_BLINK_DELAY = longPreferencesKey(WearConstants.PREFERENCE_BLINK_DELAY)
/**
 * Stable standalone watch preference key for Blink selection.
 */
private val KEY_BLINK_ON = booleanPreferencesKey(WearConstants.PREFERENCE_BLINK_DON)
/**
 * Stable standalone watch preference key for sound selection.
 */
private val KEY_ACTIVE_SOUND = intPreferencesKey(WearConstants.PREFERENCE_ACTIVE_SOUND)
/**
 * Stable standalone watch preference key for SOS selection.
 */
private val KEY_SOS_ON = booleanPreferencesKey(WearConstants.PREFERENCE_SOS_ON)
/**
 * Stable preferences key for the encoded custom-colour position.
 */
internal val KEY_COLOR_PROGRESS = floatPreferencesKey(WearConstants.PREFERENCE_PROGRESS_COLOR_PICKER)
/**
 * Stable preferences key for the frequency-slider interval.
 */
internal val KEY_FREQUENCY_PROGRESS = floatPreferencesKey(WearConstants.PREFERENCE_PROGRESS_FREQUENCY_PICKER)
/**
 * Stable preferences key for the selected pager page.
 */
internal val KEY_ACTIVE_SCREEN = intPreferencesKey(WearConstants.PREFERENCE_ACTIVE_SCREEN)
/**
 * Stable preferences key for the watch media-volume fraction.
 */
internal val KEY_VOLUME = floatPreferencesKey(WearConstants.PREFERENCE_VOLUME)
/**
 * Stable preferences key for the selected colour-button ordinal.
 */
internal val KEY_ACTIVE_COLOR = intPreferencesKey("active_btn")
/**
 * Preference key for the remembered phone node authorized for automatic resume.
 */
private val KEY_LINKED_PHONE_NODE = stringPreferencesKey("linked_phone_node")
/**
 * Preference key for an encoded stable relationship ID and its last saved linked settings.
 */
private val KEY_LINKED_PHONE_SETTINGS = byteArrayPreferencesKey("linked_phone_settings")
/**
 * Preference key for the last-phone routing hint, retained after explicit disconnect without
 * granting authorization.
 */
private val KEY_LAST_PHONE_NODE = stringPreferencesKey("last_connected_phone_node")

/**
 * Owns watch signal settings, foreground signal jobs and authenticated phone-control sessions.
 *
 * Exposes read-only state flows for controls, colour phases and connection status. Call
 * session and control methods on Main. Foreground owners bound signal and link-monitor
 * lifetimes; queued preference writes use a separate scope. Reconnection imports the current
 * phone configuration.
 *
 * @property dataStore Preferences store used to read and persist settings.
 * @property stopSounds Stops watch playback when routing or foreground ownership changes.
 * @property newSessionScope Creates a Main-thread scope for the next foreground session.
 * @property preferenceScope Scope for preference writes that outlive foreground/session
 *     cleanup.
 * @property onPreferenceError Receives preference IO failures handled by the repository.
 * @property sendLinkedCommand Sends an ordered linked-session command and reports
 *     acknowledgment.
 * @property reachablePhones Flow of currently reachable installed phone node IDs.
 * @property requestPhoneLink Sends a session-management request to the phone.
 * @property requestPhoneResume Resumes a remembered link and optionally returns the current
 *     phone state.
 */
@OptIn(ExperimentalHorologistApi::class)
@Singleton
class WearControlRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val stopSounds: () -> Unit,
    private val newSessionScope: () -> CoroutineScope,
    private val preferenceScope: CoroutineScope,
    private val onPreferenceError: (IOException) -> Unit = {
        Log.w("WearControlRepository", "Unable to access saved settings", it)
    },
    private val sendLinkedCommand: suspend (String, WatchControlCommand) -> Boolean = { _, _ -> false },
    private val reachablePhones: Flow<Set<String>> = emptyFlow(),
    private val requestPhoneLink: suspend (String, String, ByteArray) -> WatchConnectionReply? = { _, _, _ -> RETRY },
    private val requestPhoneResume: suspend (String, WatchResumeRequest) -> WatchResumeReply? = { _, _ ->
        WatchResumeReply(RETRY)
    }
) {
    /**
     * Wires production playback and transport with Main foreground sessions and an independent
     * IO preference writer.
     *
     * Discovery supplies installed phone node IDs. The transport owns acknowledged commands,
     * session requests and state-bearing resume replies; foreground owners bound session
     * lifetimes without cancelling queued preference edits.
     *
     * @param dataStore Preferences store used to read and persist settings.
     * @param soundPool Signal-sample player used for local playback.
     * @param syncRepository Watch transport for legacy and authenticated phone commands.
     * @param appHelper Horologist helper that supplies reachable installed phone nodes.
     */
    @Inject constructor(
        dataStore: DataStore<Preferences>,
        soundPool: WearSoundPoolPlayer,
        syncRepository: WearSyncStateRepository,
        appHelper: WearDataLayerAppHelper
    ) : this(
        dataStore,
        soundPool::stopAll,
        { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) },
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        sendLinkedCommand = syncRepository::sendCommand,
        reachablePhones = appHelper.connectedAndInstalledNodes.map { nodes -> nodes.map { it.id }.toSet() },
        requestPhoneLink = syncRepository::requestLink,
        requestPhoneResume = syncRepository::resumeLink
    )
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
    private val _blinkDelay = MutableStateFlow(400L)
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
     * Encoded custom-colour position, including supported historical palette encodings.
     *
     * Main-confined mutable state exposed through [colorProgress].
     */
    private val _colorProgress = MutableStateFlow(WearConstants.DEFAULT_COLOR_PROGRESS)
    /**
     * Selected colour/output ordinal shared with the phone.
     *
     * Main-confined mutable state exposed through [activeColorButton].
     */
    private val _activeColorButton = MutableStateFlow(0)
    /**
     * Compose colour derived from the watch custom-colour position.
     *
     * Main-confined mutable state exposed through [middleColor].
     */
    private val _middleColor = MutableStateFlow(
        WearUtils.computeSpecialColor(WearConstants.DEFAULT_COLOR_PROGRESS.toInt())
    )
    /**
     * Phone control indicator for a confirmed modern session or selected legacy control.
     *
     * Main-confined mutable state exposed through [phoneConnection].
     */
    private val _phoneConnection = MutableStateFlow(false)
    /**
     * Explicit legacy connection selection that gates DataItem publication.
     *
     * Main-confined mutable state exposed through [manualPhoneConnection].
     */
    private val _manualPhoneConnection = MutableStateFlow(false)
    /**
     * Whether watch preferences have been restored for this process.
     *
     * Main-confined mutable state exposed through [isInitialized].
     */
    private val _isInitialized = MutableStateFlow(false)
    /**
     * Whether at least one watch foreground owner is active.
     *
     * Main-confined mutable state exposed through [isForeground].
     */
    private val _isForeground = MutableStateFlow(false)

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
     * Read-only encoded palette position for the custom-colour control.
     */
    val colorProgress = _colorProgress.asStateFlow()
    /**
     * Read-only selected colour/output ordinal used by the watch controls.
     */
    val activeColorButton = _activeColorButton.asStateFlow()
    /**
     * Read-only Compose colour displayed by the custom-colour button.
     */
    val middleColor = _middleColor.asStateFlow()
    /**
     * Read-only confirmed or legacy connection state displayed by the watch Phone icon.
     */
    val phoneConnection = _phoneConnection.asStateFlow()
    /**
     * Explicit legacy-link state that gates DataItem publication.
     */
    internal val manualPhoneConnection = _manualPhoneConnection.asStateFlow()
    /**
     * Epoch time of the explicit legacy link request, or null while disabled.
     */
    internal var manualLinkStartedAt: Long? = null
        private set
    /**
     * Read-only readiness state used to enable controls after preference restoration.
     */
    val isInitialized = _isInitialized.asStateFlow()
    /**
     * Read-only state indicating at least one active watch foreground owner.
     */
    val isForeground = _isForeground.asStateFlow()

    /**
     * Routing gate that suppresses watch playback during phone control or a paused link.
     *
     * Main-confined mutable state exposed through [localSoundSuppressed].
     */
    private val _localSoundSuppressed = MutableStateFlow(false)
    /**
     * Read-only routing state that prevents duplicate watch sound while controlling the phone.
     */
    internal val localSoundSuppressed = _localSoundSuppressed.asStateFlow()
    /**
     * Whether initialized foreground watch state currently permits local sound playback.
     */
    internal val canPlayLocalSound: Boolean
        get() = _isInitialized.value && _isForeground.value && !_localSoundSuppressed.value

    /**
     * Suppresses local watch playback and stops active or pending watch sounds on Main.
     *
     * Retains the selected sample number for phone playback or later standalone use.
     */
    private fun suppressLocalSound() {
        _localSoundSuppressed.value = true
        stopSounds()
    }

    /**
     * Main foreground-session scope for restoration, screen phases, command sending and
     * reachability monitoring.
     */
    private var sessionScope: CoroutineScope? = null
    /**
     * Current foreground screen-phase job, cancelled before replacing the pattern.
     */
    private var signalJob: Job? = null
    /**
     * Main-confined set of owner tokens; the last release cancels shared foreground work.
     */
    private val foregroundOwners = mutableSetOf<Any>()
    /**
     * Assigns increasing sequence numbers to commands for one verified phone session.
     *
     * @property nodeId Wearable node ID of the peer.
     * @property requestId Identifier of the handoff or command session being checked.
     */
    private class LinkedPhone(val nodeId: String, val requestId: UUID) {
        /**
         * Last allocated command number in this phone session; incremented on Main before
         * creating a command.
         */
        private var sequence = 0L

        /**
         * Allocates the next sequence number for this phone command session.
         *
         * Call on Main, where command ordering is serialized. Uses this session's requestId
         * and a fresh command timestamp. The caller attaches a complete signal snapshot when
         * needed.
         *
         * @param control User control or session-check action to encode.
         * @param value Wire value for the selected action.
         * @return A command with this session ID and its next increasing sequence number.
         */
        fun command(control: WatchControl, value: Int) =
            WatchControlCommand(requestId, ++sequence, control, value)
    }

    /**
     * Retains a user action and its per-tap signal snapshot while a handoff is pending.
     *
     * @property control Control action to apply.
     * @property value Value encoded for the selected control action.
     * @property settings Complete settings captured for this user action, or null for a
     *     connection check.
     */
    private data class ControlChange(val control: WatchControl, val value: Int, val settings: PhoneSignalState?)

    /**
     * Queues user edits until a matching phone handoff acknowledgment arrives.
     *
     * @property nodeId Wearable node ID of the peer.
     * @property id Unique invitation or session identifier.
     * @property refreshFromPhone Whether this handoff must import phone state instead of
     *     pushing standalone watch settings.
     */
    private class PendingHandoff(val nodeId: String, val id: UUID, val refreshFromPhone: Boolean) {
        /**
         * FIFO user edits captured while this handoff awaits acknowledgment.
         */
        val changes = ArrayDeque<ControlChange>()
    }

    /**
     * Identifies a remembered phone relationship that can be resumed with fresh commands.
     *
     * @property nodeId Wearable node ID of the peer.
     * @property linkId Stable identifier of the remembered relationship.
     */
    private data class RememberedPhone(val nodeId: String, val linkId: UUID) {
        /**
         * Compares a phone node and stable relationship ID with this remembered relationship.
         *
         * @param nodeId Wearable phone node ID to compare.
         * @param linkId Stable relationship ID, retained across fresh command sessions.
         * @return True when both identifiers match, independently of current connectivity.
         */
        fun matches(nodeId: String, linkId: UUID): Boolean =
            this.nodeId == nodeId && this.linkId == linkId
    }

    /**
     * Stable phone relationship retained during temporary loss or app stop, and cleared on
     * explicit revocation.
     */
    private var rememberedPhone: RememberedPhone? = null
    /**
     * Last-phone routing hint used by explicit reconnect; it alone never enables automatic
     * resume.
     */
    // A routing hint only: explicit disconnect still revokes the link and disables automatic resume.
    private var lastConnectedPhoneNodeId: String? = null
    /**
     * Saved linked configuration available as a legacy resume fallback after process
     * restoration.
     */
    private var linkedSettingsToRestore: PhoneSignalState? = null
    /**
     * Foreground job observing the remembered phone and running resume or liveness checks.
     */
    private var linkMonitor: Job? = null

    /**
     * Unconfirmed explicit invitation and ordered user edits made while its reply is pending.
     */
    private var pendingHandoff: PendingHandoff? = null
    /**
     * In-memory command-session sequencer, retained while paused for compatible legacy
     * verification.
     */
    private var linkedPhone: LinkedPhone? = null
    /**
     * Standalone settings backed up before phone import, with torch off; its presence suppresses
     * standalone preference writes.
     */
    private var standaloneBeforeImport: PhoneSignalState? = null
    /**
     * Bounded FIFO of timestamped user commands for the current sender, or null when delivery is
     * paused.
     */
    private var linkedCommands: Channel<WatchControlCommand>? = null
    /**
     * Foreground coroutine awaiting each command acknowledgment in order.
     */
    private var linkedSender: Job? = null

    /**
     * Ordered edits consumed by an independent preference coroutine so foreground stop does not
     * discard pending writes.
     */
    // Writes outlive an Activity stop and retain user-action order.
    private val preferenceWrites =
        Channel<suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit>(Channel.UNLIMITED)

    init {
        preferenceScope.launch {
            for (change in preferenceWrites) dataStore.editSafely(onPreferenceError, change)
        }
    }

    /**
     * Acquires foreground ownership and launches restoration for the first active owner.
     *
     * Call on Main with a token paired with [stop]. Repeated acquisition by the same owner
     * or another owner during an active foreground session reuses that session. Returns
     * without waiting for preferences.
     *
     * The new session coroutine restores bounded settings only when not already
     * initialized, gives SOS precedence over Blink and keeps a saved remote torch
     * selection off. Restores any remembered phone without marking it connected, then
     * starts screen timing and link monitoring. Read IO failures are reported and recover
     * with empty preferences. Cancelling the session cancels unfinished restoration.
     *
     * @param owner Stable foreground-owner token to release through stop.
     */
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
                _activeColorButton.value = (prefs[KEY_ACTIVE_COLOR] ?: 0).takeIf { it in 0..2 } ?: 0
                applyColor(prefs[KEY_COLOR_PROGRESS] ?: WearConstants.DEFAULT_COLOR_PROGRESS)
                // A previous process's remote torch selection is not an active connection.
                _flashOn.value = false
                restoreRememberedPhone(prefs)
                _isInitialized.value = true
            }
            restartSignal()
            restartLinkMonitor()
        }
    }

    /**
     * Releases foreground ownership and ends session work when the last owner leaves.
     *
     * Call on Main. Clears a pending handoff, cancels monitoring and the session scope,
     * resets the screen phase and stops watch sounds. Pauses modern phone control while
     * retaining its remembered relationship. Keeps initialized settings and legacy-link
     * flags; queued preference writes use their independent scope.
     *
     * @param owner Previously acquired foreground-owner token; an unknown token has no
     *     effect.
     */
    fun stop(owner: Any) {
        if (!foregroundOwners.remove(owner) || foregroundOwners.isNotEmpty()) return
        _isForeground.value = false
        pendingHandoff = null
        linkMonitor?.cancel()
        linkMonitor = null
        if (rememberedPhone != null) pausePhoneLink()
        sessionScope?.cancel()
        sessionScope = null
        signalJob = null
        _colorBlinkActive.value = false
        stopSounds()
    }

    /**
     * Sets Blink and its bounded half-period, then queues the corresponding phone action.
     *
     * Call on Main. Enabling Blink clears SOS; disabling Blink preserves SOS. Queues
     * standalone preferences through [writePref], restarts screen phases and includes the
     * current screen or torch output in the encoded mode selection.
     *
     * @param blinkOn Whether Blink should be selected.
     * @param delayMs Requested blink half-period, clamped to 100..1000 milliseconds.
     */
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
        sendControl(WatchControl.BLINK, WatchModeChange.forSelection(blinkOn, _flashOn.value).wireValue)
    }

    /**
     * Updates blink timing and queues a frequency action while retaining timed-mode
     * selection.
     *
     * Call on Main. Bounds the delay, queues both standalone delay and frequency-slider
     * preferences through [writePref], and restarts the selected screen pattern.
     *
     * @param delayMs Requested blink half-period, clamped to 100..1000 milliseconds.
     */
    fun setBlinkDelay(delayMs: Long) {
        val delay = delayMs.coerceIn(100L, 1000L)
        _blinkDelay.value = delay
        writePref(KEY_BLINK_DELAY, delay)
        writePref(KEY_FREQUENCY_PROGRESS, delay.toFloat())
        restartSignal()
        sendControl(WatchControl.FREQUENCY, delay.toInt())
    }

    /**
     * Selects phone LED output locally and queues a torch action for the phone.
     *
     * Call on Main. [updateFlash] handles local state and screen phases; [sendControl]
     * captures the resulting complete configuration for an active or pending phone link.
     *
     * @param on True to select phone LED output, or false to use screen output.
     */
    fun setFlash(on: Boolean) {
        updateFlash(on)
        sendControl(WatchControl.TORCH, if (on) 1 else 0)
    }

    /**
     * Selects a watch screen colour, clears torch selection and queues one colour-button
     * action.
     *
     * Call on Main. Bounds the ordinal to red, custom or green, queues eligible standalone
     * preferences through [writePref] and retains Blink or SOS selection. The queued
     * action captures the resulting complete configuration.
     *
     * @param button Colour-button ordinal: 0 for red, 1 for custom colour or 2 for green.
     */
    internal fun selectColorButton(button: Int) {
        val color = button.coerceIn(0, 2)
        _activeColorButton.value = color
        writePref(KEY_ACTIVE_COLOR, color)
        updateFlash(false)
        sendControl(WatchControl.COLOR_BUTTON, color)
    }

    /**
     * Updates local phone-torch selection and restarts screen-phase routing on Main.
     *
     * Queues the standalone torch preference through [writePref]. Callers queue the
     * appropriate phone action after composing their local state changes.
     *
     * @param on Whether the shared configuration selects phone LED output.
     */
    private fun updateFlash(on: Boolean) {
        _flashOn.value = on
        writePref(KEY_FLASH_ON, on)
        restartSignal()
    }

    /**
     * Sets SOS selection and queues its screen-or-torch mode action for the phone.
     *
     * Call on Main. Enabling SOS clears Blink; disabling SOS preserves Blink. Queues
     * standalone preferences through [writePref] and restarts screen phases.
     *
     * @param on Whether SOS should be selected.
     */
    fun switchSOS(on: Boolean) {
        _sosOn.value = on
        if (on) {
            _blinkOn.value = false
            writePref(KEY_BLINK_ON, false)
        }
        writePref(KEY_SOS_ON, on)
        restartSignal()
        sendControl(WatchControl.SOS, WatchModeChange.forSelection(on, _flashOn.value).wireValue)
    }

    /**
     * Replaces the local screen-phase job with the selected SOS or Blink pattern.
     *
     * Call on Main. Cancels the previous job and resets colorBlinkActive. Starts a pattern
     * only with an initialized session scope and screen output; SOS takes precedence. The
     * session coroutine suspends between phases. Steady or torch output leaves the phase
     * false; physical phone LED writes belong to the phone app.
     */
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
                    delay(_blinkDelay.value.milliseconds)
                    _colorBlinkActive.value = false
                    delay(_blinkDelay.value.milliseconds)
                }
            }
            else -> null
        }
    }

    /**
     * Updates sound selection and queues the resulting phone sound action on Main.
     *
     * Bounds the sample to 0..3, queues eligible standalone persistence and stops watch
     * sounds for zero. An offline choice re-enables local playback. A nonzero offline
     * choice also switches to screen output when a remembered phone's torch remains
     * selected. Connected choices retain local sound suppression to avoid duplicate
     * playback.
     *
     * @param n Requested sample number: 0 for silence or 1..3 for a signal sample.
     */
    fun setActiveSound(n: Int) {
        val sound = n.coerceIn(0, 3)
        val localPlayback = !_phoneConnection.value
        if (localPlayback) _localSoundSuppressed.value = false
        _activeSound.value = sound
        writePref(KEY_ACTIVE_SOUND, sound)
        if (sound == 0) stopSounds()
        if (localPlayback && sound != 0 && _flashOn.value && rememberedPhone != null) updateFlash(false)
        sendControl(WatchControl.SOUND, sound)
    }

    /**
     * Selects a bounded pager page and queues its phone action when the selection changes.
     *
     * Call on Main. Queues eligible standalone persistence through [writePref]; an
     * unchanged bounded selection produces neither a write nor a command.
     *
     * @param newScreenIndex Requested pager page: 0 for controls or 1 for the signal
     *     display.
     */
    fun updateScreenIndex(newScreenIndex: Int) {
        val index = newScreenIndex.coerceIn(0, 1)
        if (_screenIndex.value == index) return
        _screenIndex.value = index
        writePref(KEY_ACTIVE_SCREEN, index)
        sendControl(WatchControl.SCREEN, index)
    }

    /**
     * Explicitly disconnects modern control or enables manual legacy DataItem control.
     *
     * Call on Main. Clears pending and remembered modern links and restores the standalone
     * profile. A true value then selects legacy control, records its start timestamp and
     * suppresses local sound. A false value leaves automatic resume disabled. The saved
     * last-phone routing hint is retained.
     *
     * With a previous modern relationship and an active session scope, schedules RELEASE.
     * An unsupported release endpoint can fall back to DISCONNECT using the previous
     * in-memory command session. Legacy selection does not await peer confirmation.
     *
     * @param isConnect True to enable manual legacy control, or false to explicitly
     *     disconnect.
     */
    fun togglePhoneConnection(isConnect: Boolean) {
        val previous = linkedPhone
        val saved = rememberedPhone
        pendingHandoff = null
        disarmPhoneLink()
        manualLinkStartedAt = if (isConnect) System.currentTimeMillis() else null
        _manualPhoneConnection.value = isConnect
        _phoneConnection.value = isConnect
        if (isConnect) suppressLocalSound()
        if (saved != null) {
            sessionScope?.launch {
                val result = requestPhoneLink(
                    saved.nodeId, WatchConnectionRequest.RELEASE_PATH, WatchConnectionRequest.encodeId(saved.linkId)
                )
                if (result == null && previous != null) {
                    sendLinkedCommand(previous.nodeId, previous.command(WatchControl.DISCONNECT, 0))
                }
            }
        }
    }

    /**
     * Disarms a remembered but inactive phone link before an explicit connection attempt.
     *
     * Call on Main. Uses [togglePhoneConnection] to restore standalone controls and revoke
     * the suspended relationship while retaining the last-phone routing hint. An active
     * connection is left unchanged.
     */
    internal fun forgetSuspendedPhoneLink() {
        if (rememberedPhone != null && !_phoneConnection.value) togglePhoneConnection(false)
    }

    /**
     * Enables manual legacy control when foreground settings are ready and no link is
     * selected.
     *
     * Call on Main. Delegates to [togglePhoneConnection]; success describes local legacy
     * selection, not an acknowledged modern connection.
     *
     * @return True when legacy control is enabled; false while background, uninitialized
     *     or already connected.
     */
    internal fun enableLegacyPhoneLink(): Boolean {
        if (!_isForeground.value || !_isInitialized.value || _phoneConnection.value) return false
        togglePhoneConnection(true)
        return true
    }

    /**
     * Captures current watch settings and creates a pending watch-led handoff.
     *
     * Call on Main while foreground, initialized and disconnected. Replaces any pending
     * invitation and records whether this phone should supply the reconnect baseline. User
     * actions can be buffered until confirmation; transport negotiation remains the
     * caller's responsibility.
     *
     * @param nodeId Wearable phone node ID selected for the new handoff.
     * @return The fresh invitation, or null while background, uninitialized or already
     *     connected.
     */
    internal fun preparePhoneHandoff(nodeId: String): WatchConnectionRequest? {
        if (!_isForeground.value || !_isInitialized.value || _phoneConnection.value) return null
        val request = WatchConnectionRequest.create(currentSignal())
        pendingHandoff = PendingHandoff(nodeId, request.id, shouldRefreshFromPhone(nodeId))
        return request
    }

    /**
     * Checks whether an explicit connection should import the last connected phone's
     * settings.
     *
     * Call on Main. This routing hint selects the handoff path; it grants no authorization
     * and does not enable automatic reconnect after an explicit disconnect.
     *
     * @param nodeId Discovered phone node ID to compare with the saved routing hint.
     * @return True when this node is the last connected phone.
     */
    internal fun shouldRefreshFromPhone(nodeId: String): Boolean = lastConnectedPhoneNodeId == nodeId

    /**
     * Checks the node and invitation ID of the current pending handoff on Main.
     *
     * @param nodeId Phone node ID expected by the pending handoff.
     * @param requestId Fresh invitation ID expected by the pending handoff.
     * @return True for the matching pending invitation; lifecycle validity is checked when
     *     finishing.
     */
    internal fun isPhoneHandoffPending(nodeId: String, requestId: UUID): Boolean =
        pendingHandoff?.let { it.nodeId == nodeId && it.id == requestId } == true

    /**
     * Activates an acknowledged handoff and handles user edits made while it was pending.
     *
     * Call on Main after transport confirmation. Requires matching pending identity and
     * ready foreground state. A phone-refresh handoff also requires phoneSignal. When
     * settings are supplied, saves the standalone profile with torch off, suppresses local
     * sound and imports that baseline before enabling control.
     *
     * Without a phone baseline, queues edits with their original snapshots. With a phone
     * baseline, replays their intent in order to create updated snapshots. Returns after
     * local activation and replay without awaiting command acknowledgments.
     *
     * @param nodeId Phone node ID that acknowledged the invitation.
     * @param requestId Pending invitation ID acknowledged by the phone.
     * @param phoneSignal Current phone settings, required for a phone-refresh handoff.
     * @return True when the handoff is activated; false for a missing or mismatched
     *     invitation, inactive lifecycle state or required missing phone settings.
     */
    internal fun finishPhoneHandoff(nodeId: String, requestId: UUID, phoneSignal: PhoneSignalState? = null): Boolean {
        val pending = pendingHandoff ?: return false
        if (pending.nodeId != nodeId || pending.id != requestId ||
            !_isForeground.value || !_isInitialized.value
        ) return false
        if (pending.refreshFromPhone && phoneSignal == null) return false
        pendingHandoff = null
        if (phoneSignal != null) {
            if (standaloneBeforeImport == null) standaloneBeforeImport = currentSignal().copy(torchOn = false)
            suppressLocalSound()
            applySignal(phoneSignal)
        }
        establishPhoneLink(nodeId, requestId)
        if (phoneSignal == null) pending.changes.forEach(::queueControl)
        else pending.changes.forEach(::replayControl)
        return true
    }

    /**
     * Reapplies a buffered user choice against the newly imported phone baseline.
     *
     * Call on Main. Uses normal control mutators to queue resulting actions with current
     * snapshots rather than the edit's stale standalone snapshot. Preserves an explicit
     * timed-mode output choice and ignores session checks, disconnects and unknown mode
     * values.
     *
     * @param change Buffered action and wire value whose intent should be replayed.
     */
    private fun replayControl(change: ControlChange) {
        when (change.control) {
            WatchControl.TORCH -> setFlash(change.value != 0)
            WatchControl.BLINK, WatchControl.SOS -> {
                val mode = WatchModeChange.fromWireValue(change.value) ?: return
                val enabled = mode != WatchModeChange.DISABLE
                if (enabled) updateFlash(mode == WatchModeChange.ENABLE_ON_TORCH)
                if (change.control == WatchControl.BLINK) setBlinking(enabled, _blinkDelay.value)
                else switchSOS(enabled)
            }
            WatchControl.FREQUENCY -> setBlinkDelay(change.value.toLong())
            WatchControl.COLOR -> commitColor(Float.fromBits(change.value))
            WatchControl.COLOR_BUTTON -> selectColorButton(change.value)
            WatchControl.SCREEN -> updateScreenIndex(change.value)
            WatchControl.SOUND -> setActiveSound(change.value)
            WatchControl.CHECK, WatchControl.DISCONNECT -> Unit
        }
    }

    /**
     * Drops the matching local pending handoff and its buffered edits on Main.
     *
     * Leaves a different pending invitation or an established phone relationship
     * unchanged.
     *
     * @param nodeId Phone node ID of the attempt to cancel.
     * @param requestId Invitation ID of the attempt to cancel.
     */
    internal fun cancelPhoneHandoff(nodeId: String, requestId: UUID) {
        if (isPhoneHandoffPending(nodeId, requestId)) {
            pendingHandoff = null
        }
    }

    /**
     * Accepts a fresh phone-led invitation and imports the phone's shared settings.
     *
     * Call on Main after sender verification. Requires initialized foreground state and a
     * fresh request. An already remembered matching relationship succeeds without
     * reimporting; a different active modern peer is rejected. Otherwise clears pending
     * and legacy state, backs up standalone settings with torch off, suppresses local
     * sound and establishes the imported link.
     *
     * @param phoneNodeId Verified phone node ID supplied by the request handler.
     * @param request Fresh invitation containing the relationship ID and phone
     *     configuration.
     * @return True for an accepted invitation or remembered matching relationship; false
     *     when lifecycle, freshness or active-peer checks fail.
     */
    internal fun connectFromPhone(phoneNodeId: String, request: WatchConnectionRequest): Boolean {
        if (!_isForeground.value || !_isInitialized.value || !request.isFresh()) return false
        if (hasPhoneLink(phoneNodeId, request.id)) return true
        if (_phoneConnection.value && linkedPhone?.nodeId?.let { it != phoneNodeId } == true) return false

        // Imported controls are the linked session's baseline. Keep standalone preferences intact.
        pendingHandoff = null
        _manualPhoneConnection.value = false
        manualLinkStartedAt = null
        if (standaloneBeforeImport == null) standaloneBeforeImport = currentSignal().copy(torchOn = false)
        suppressLocalSound()
        applySignal(request.signal)
        establishPhoneLink(phoneNodeId, request.id)
        return true
    }

    /**
     * Captures the current shared watch control selections on Main.
     *
     * Includes selected torch and timed modes even while a phone link is paused; it is a
     * configuration snapshot rather than a measurement of phone hardware.
     *
     * @return Complete colour, timing, output, sound and pager settings for handoff or
     *     commands.
     */
    private fun currentSignal() = PhoneSignalState(
        torchOn = _flashOn.value,
        blinkEnabled = _blinkOn.value,
        sosEnabled = _sosOn.value,
        frequency = _blinkDelay.value,
        color = _colorProgress.value,
        activeSound = _activeSound.value,
        screenIndex = _screenIndex.value,
        activeColorButton = _activeColorButton.value
    )

    /**
     * Publishes a bounded phone or standalone configuration and restarts local screen
     * phases.
     *
     * Call on Main. Gives SOS precedence over Blink and stops watch sounds for a zero
     * sample. Updates the custom-colour preview as well as the encoded palette position.
     * Callers own persistence, command delivery and local-sound suppression.
     *
     * @param signal Complete shared configuration to import or restore.
     */
    private fun applySignal(signal: PhoneSignalState) {
        _flashOn.value = signal.torchOn
        _sosOn.value = signal.sosEnabled
        _blinkOn.value = signal.blinkEnabled && !signal.sosEnabled
        _blinkDelay.value = signal.frequency.coerceIn(100L, 1000L)
        _activeSound.value = signal.activeSound.coerceIn(0, 3)
        if (_activeSound.value == 0) stopSounds()
        _screenIndex.value = signal.screenIndex.coerceIn(0, 1)
        _activeColorButton.value = signal.activeColorButton.coerceIn(0, 3)
        applyColor(signal.color)
        restartSignal()
    }

    /**
     * Checks a remembered phone relationship independently of its current connection
     * state.
     *
     * Call on Main. Compares the stable relationship ID rather than a resumed command ID.
     *
     * @param nodeId Phone node ID to check.
     * @param requestId Stable relationship ID from the original handoff.
     * @return True when the remembered relationship matches both identifiers, including
     *     while paused.
     */
    internal fun hasPhoneLink(nodeId: String, requestId: UUID): Boolean =
        rememberedPhone?.matches(nodeId, requestId) == true

    /**
     * Accepts an availability announcement for a remembered phone and restarts resume
     * monitoring.
     *
     * Call on Main. Requires initialized foreground state and the matching relationship.
     * Restarts the monitor only while the connection indicator is off.
     *
     * @param nodeId Verified phone node ID announcing availability.
     * @param linkId Stable relationship ID carried by the announcement.
     * @return True when the announcement is eligible; connection confirmation still
     *     happens through resume.
     */
    internal fun phoneAvailable(nodeId: String, linkId: UUID): Boolean {
        if (!_isForeground.value || !_isInitialized.value || !hasPhoneLink(nodeId, linkId)) return false
        if (!_phoneConnection.value) restartLinkMonitor()
        return true
    }

    /**
     * Pauses a matching command session and restarts remembered-phone monitoring on Main.
     *
     * Retains the relationship and current control selections for a later resume.
     *
     * @param nodeId Verified phone node ID sending the pause notice.
     * @param commandId Current command-session ID, not the stable relationship ID.
     * @return True when the in-memory command session matches; false for a stale or
     *     unrelated notice.
     */
    internal fun phonePaused(nodeId: String, commandId: UUID): Boolean {
        val phone = linkedPhone ?: return false
        if (phone.nodeId != nodeId || phone.requestId != commandId) return false
        pausePhoneLink()
        restartLinkMonitor()
        return true
    }

    /**
     * Disarms a matching remembered relationship or drops its pending invitation on Main.
     *
     * A remembered-link revocation restores standalone controls and disables automatic
     * resume. A pending-only match discards that attempt without changing another link.
     *
     * @param nodeId Verified phone node ID sending the revocation.
     * @param requestId Stable relationship or pending invitation ID to revoke.
     * @return True when a remembered link or pending invitation matches; false otherwise.
     */
    internal fun revokePhoneLink(nodeId: String, requestId: UUID): Boolean {
        if (hasPhoneLink(nodeId, requestId)) {
            disarmPhoneLink()
            return true
        }
        if (isPhoneHandoffPending(nodeId, requestId)) {
            pendingHandoff = null
            return true
        }
        return false
    }

    /**
     * Remembers a newly confirmed phone relationship and activates its command session.
     *
     * Call on Main in an active foreground session. Clears legacy selection, queues
     * relationship settings, activates the sender and restarts monitoring. The initial
     * command ID equals the relationship ID; later resumes use a fresh command ID.
     *
     * @param nodeId Confirmed phone node ID.
     * @param requestId Confirmed invitation ID defining the stable relationship.
     */
    private fun establishPhoneLink(nodeId: String, requestId: UUID) {
        _manualPhoneConnection.value = false
        manualLinkStartedAt = null
        saveRememberedPhone(RememberedPhone(nodeId, requestId))
        activatePhoneLink(LinkedPhone(nodeId, requestId))
        restartLinkMonitor()
    }

    /**
     * Suppresses duplicate watch sound and activates the supplied phone command session.
     *
     * Call on Main with an active foreground scope. Replaces the sender and its queue,
     * then selects the connection indicator. Relationship persistence and monitoring
     * remain owned by the caller.
     *
     * @param phone Phone node and command-session sequencer to activate.
     */
    private fun activatePhoneLink(phone: LinkedPhone) {
        suppressLocalSound()
        linkedPhone = phone
        startLinkedSender(phone)
        _phoneConnection.value = true
    }

    /**
     * Stops command delivery and clears the phone connection indicator on Main.
     *
     * Retains the in-memory session, remembered relationship, controls and current local
     * sound suppression so monitoring can resume the link later.
     */
    private fun pausePhoneLink() {
        stopLinkedSender()
        _phoneConnection.value = false
    }

    /**
     * Restores the last-phone routing hint and decodes a saved relationship on Main.
     *
     * A valid stored node and settings record restore remembered identity and a linked
     * settings fallback. Suppresses local sound while awaiting confirmation; importing
     * those settings and activating commands belong to a later successful resume.
     *
     * @param prefs Preferences snapshot read during first-session restoration.
     */
    private fun restoreRememberedPhone(prefs: Preferences) {
        lastConnectedPhoneNodeId = (prefs[KEY_LAST_PHONE_NODE] ?: prefs[KEY_LINKED_PHONE_NODE])
            ?.takeIf { it.isNotBlank() }
        val nodeId = prefs[KEY_LINKED_PHONE_NODE]?.takeIf { it.isNotBlank() } ?: return
        val saved = prefs[KEY_LINKED_PHONE_SETTINGS]?.let(WatchConnectionRequest::decode) ?: return
        rememberedPhone = RememberedPhone(nodeId, saved.id)
        linkedSettingsToRestore = saved.signal
        suppressLocalSound()
    }

    /**
     * Updates remembered phone identity and queues its ordered persistence on Main.
     *
     * Clears the restoration fallback. A non-null relationship stores current linked
     * settings; null removes saved link records while preserving the last-phone routing
     * hint. Returns before the independent preference writer completes.
     *
     * @param phone Confirmed relationship to remember, or null to forget automatic resume.
     */
    private fun saveRememberedPhone(phone: RememberedPhone?) {
        rememberedPhone = phone
        linkedSettingsToRestore = null
        if (phone == null) {
            val lastNode = lastConnectedPhoneNodeId
            preferenceWrites.trySend { prefs ->
                prefs.remove(KEY_LINKED_PHONE_NODE)
                prefs.remove(KEY_LINKED_PHONE_SETTINGS)
                if (lastNode != null) prefs[KEY_LAST_PHONE_NODE] = lastNode
            }
        } else {
            saveLinkedSettings(phone, currentSignal())
        }
    }

    /**
     * Queues a relationship settings record and updates the last-phone routing hint on
     * Main.
     *
     * Encodes the supplied settings with the stable link ID before enqueuing one edit for
     * the node, settings bytes and routing hint. Linked settings remain separate from
     * standalone control preferences.
     *
     * @param phone Remembered relationship owning the settings record.
     * @param settings Complete configuration to retain for this confirmed relationship.
     */
    private fun saveLinkedSettings(phone: RememberedPhone, settings: PhoneSignalState) {
        lastConnectedPhoneNodeId = phone.nodeId
        val snapshot = WatchConnectionRequest.create(settings, id = phone.linkId).encode()
        preferenceWrites.trySend { prefs ->
            prefs[KEY_LINKED_PHONE_NODE] = phone.nodeId
            prefs[KEY_LINKED_PHONE_SETTINGS] = snapshot
            prefs[KEY_LAST_PHONE_NODE] = phone.nodeId
        }
    }

    /**
     * Replaces reachability and liveness monitoring for the remembered phone on Main.
     *
     * Uses the foreground session scope and only the already remembered relationship.
     * Reachability loss pauses control; return starts resume and status checks. Discovery
     * flow failures are logged, emit an unreachable result and retry after the check
     * interval. Cancellation propagates, including when a newer monitor replaces this one.
     */
    private fun restartLinkMonitor() {
        linkMonitor?.cancel()
        val saved = rememberedPhone ?: return
        linkMonitor = sessionScope?.launch {
            reachablePhones.retryWhen { error, _ ->
                if (error is CancellationException) throw error
                Log.w("WearControlRepository", "Phone discovery failed", error)
                emit(emptySet())
                delay(LINK_CHECK_INTERVAL_MS.milliseconds)
                true
            }.map { saved.nodeId in it }.distinctUntilChanged().collectLatest { reachable ->
                if (reachable) monitorReachablePhone(saved) else pausePhoneLink()
            }
        }
    }

    /**
     * Suspends while resuming and checking one reachable remembered phone.
     *
     * Runs in the caller's Main session coroutine. Unconfirmed resumes back off from
     * LINK_CHECK_INTERVAL_MS up to MAX_LINK_RETRY_INTERVAL_MS. Connected sessions are
     * checked at the normal interval; RETRY pauses them and REJECTED disarms the link. An
     * unsupported status endpoint falls back to acknowledged CHECK commands. Cancellation
     * interrupts delays and transport awaits.
     *
     * @param saved Remembered relationship captured by the reachability monitor.
     */
    private suspend fun monitorReachablePhone(saved: RememberedPhone) {
        var useLegacyStatus = false
        var retryDelay = LINK_CHECK_INTERVAL_MS
        while (true) {
            if (!_phoneConnection.value) {
                if (!resumePhoneLink(saved)) {
                    if (rememberedPhone != saved) return
                    delay(retryDelay.milliseconds)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_LINK_RETRY_INTERVAL_MS)
                    continue
                }
                retryDelay = LINK_CHECK_INTERVAL_MS
                useLegacyStatus = false
            }
            delay(LINK_CHECK_INTERVAL_MS.milliseconds)
            val phone = linkedPhone ?: return
            if (_phoneConnection.value) {
                if (useLegacyStatus) {
                    sendControl(WatchControl.CHECK, 0)
                    continue
                }
                when (requestPhoneLink(
                    phone.nodeId, WatchConnectionRequest.STATUS_PATH, WatchConnectionRequest.encodeId(phone.requestId)
                )) {
                    ACCEPTED -> if (_phoneConnection.value) continue
                    REJECTED -> {
                        disarmPhoneLink()
                        return
                    }
                    null -> {
                        // Verify with the old protocol before trusting a response without a status.
                        useLegacyStatus = true
                        if (_phoneConnection.value) {
                            sendControl(WatchControl.CHECK, 0)
                            continue
                        }
                    }
                    RETRY -> Unit
                }
            }
            pausePhoneLink()
        }
    }

    /**
     * Suspends while requesting a fresh command session for a remembered phone
     * relationship.
     *
     * Call from the Main session coroutine. Retries one RETRY response after
     * LINK_RETRY_DELAY_MS using the same invitation. Acceptance imports returned phone
     * settings, or a saved linked fallback when available, before activating the sender. A
     * rejected relationship is disarmed.
     *
     * An unsupported resume endpoint can reuse an existing in-memory session only after an
     * acknowledged CHECK. Cancellation propagates through waits and transport calls.
     *
     * @param saved Remembered phone node and stable relationship ID to resume.
     * @return True when the connection is selected after the attempt; false while resume
     *     remains unconfirmed.
     */
    private suspend fun resumePhoneLink(saved: RememberedPhone): Boolean {
        val request = WatchResumeRequest.create(saved.linkId)
        var result = requestPhoneResume(saved.nodeId, request)
        if (result?.status == RETRY) {
            delay(LINK_RETRY_DELAY_MS.milliseconds)
            result = requestPhoneResume(saved.nodeId, request)
        }
        when (result?.status) {
            ACCEPTED -> {
                suppressLocalSound()
                val signal = result.signal ?: linkedSettingsToRestore
                if (signal != null) {
                    if (standaloneBeforeImport == null) standaloneBeforeImport = currentSignal().copy(torchOn = false)
                    applySignal(signal)
                    linkedSettingsToRestore = null
                    saveLinkedSettings(saved, signal)
                }
                activatePhoneLink(LinkedPhone(saved.nodeId, request.commandId))
            }
            REJECTED -> disarmPhoneLink()
            null -> {
                // A v3 phone without resume support can still verify an in-memory link.
                val previous = linkedPhone ?: return false
                if (!sendLinkedCommand(previous.nodeId, previous.command(WatchControl.CHECK, 0))) return false
                activatePhoneLink(previous)
            }
            RETRY -> Unit
        }
        return _phoneConnection.value
    }

    /**
     * Forgets local phone authorization and restores standalone control on Main.
     *
     * Cancels monitoring and delivery, clears remembered and legacy state, and queues
     * saved-link removal. Restores the pre-import profile when present, otherwise clears a
     * remaining torch selection. Re-enables local sound eligibility and retains the
     * last-phone routing hint. The caller owns any release request to the phone.
     */
    private fun disarmPhoneLink() {
        linkMonitor?.cancel()
        linkMonitor = null
        if (rememberedPhone != null) saveRememberedPhone(null)
        linkedPhone = null
        pausePhoneLink()
        manualLinkStartedAt = null
        _manualPhoneConnection.value = false
        val standalone = standaloneBeforeImport
        standaloneBeforeImport = null
        if (standalone != null) {
            stopSounds()
            applySignal(standalone)
        } else if (_flashOn.value) {
            updateFlash(false)
        }
        _localSoundSuppressed.value = false
    }

    /**
     * Commits a bounded custom colour and queues its complete-state phone action on Main.
     *
     * Updates the button preview, queues eligible standalone persistence through
     * [writePref] and encodes the normalized Float position as command bits.
     *
     * @param progress Encoded shared palette position chosen by the user.
     */
    internal fun commitColor(progress: Float) {
        applyColor(progress)
        val color = _colorProgress.value
        writePref(KEY_COLOR_PROGRESS, color)
        sendControl(WatchControl.COLOR, color.toBits())
    }

    /**
     * Captures current settings for a user action and passes it to the command queue on
     * Main.
     *
     * CHECK carries no snapshot. User actions retain their per-tap configuration so a
     * phone-side edit cannot leave the requested mode partially applied.
     *
     * @param control User control action or CHECK heartbeat; disconnect uses its separate
     *     path.
     * @param value Wire value for the selected control action.
     */
    private fun sendControl(control: WatchControl, value: Int) {
        val settings = if (control == WatchControl.CHECK) null else currentSignal()
        queueControl(ControlChange(control, value, settings))
    }

    /**
     * Buffers a pending handoff edit or queues an ordered command for an active sender.
     *
     * Call on Main. Without an in-memory phone session, buffers up to MAX_QUEUED_CONTROLS
     * edits for the pending invitation; overflow discards that invitation. With a sender
     * queue, assigns the next sequence number and retains the edit's snapshot. A failed
     * enqueue pauses the link. Edits with no pending handoff or available sender are
     * ignored.
     *
     * @param change Action, encoded value and per-tap configuration to queue.
     */
    private fun queueControl(change: ControlChange) {
        val phone = linkedPhone
        if (phone == null) {
            val pending = pendingHandoff ?: return
            // Keep local changes made while the initial snapshot is in transit in order.
            if (pending.changes.size >= MAX_QUEUED_CONTROLS) pendingHandoff = null
            else pending.changes.addLast(change)
            return
        }
        val commands = linkedCommands ?: return
        val queued = phone.command(change.control, change.value).copy(signal = change.settings)
        if (commands.trySend(queued).isFailure) pausePhoneLink()
    }

    /**
     * Replaces the bounded command queue and launches its sequential sender on Main.
     *
     * Uses the foreground session scope. A stale command or negative acknowledgment stops
     * delivery and pauses the link only if this session is still current. After successful
     * delivery, saves an attached snapshot for the remembered relationship. Scope
     * cancellation interrupts the sender and its transport awaits.
     *
     * @param phone Phone node and command-session sequencer that own this sender.
     */
    private fun startLinkedSender(phone: LinkedPhone) {
        stopLinkedSender()
        val scope = sessionScope ?: return
        val commands = Channel<WatchControlCommand>(capacity = MAX_QUEUED_CONTROLS)
        linkedCommands = commands
        linkedSender = scope.launch {
            for (command in commands) {
                if (!command.isFresh() || !sendLinkedCommand(phone.nodeId, command)) {
                    if (linkedPhone === phone) pausePhoneLink()
                    break
                }
                val saved = rememberedPhone
                if (saved != null && linkedPhone === phone) command.signal?.let { saveLinkedSettings(saved, it) }
            }
        }
    }

    /**
     * Cancels the sender and discards its queued commands on Main.
     *
     * Clears both handles without waiting for completion. Connection flags and remembered
     * settings remain owned by the calling pause, activation or cleanup operation.
     */
    private fun stopLinkedSender() {
        linkedSender?.cancel()
        linkedSender = null
        linkedCommands?.cancel()
        linkedCommands = null
    }

    /**
     * Publishes a normalized palette position and derived custom-button colour on Main.
     *
     * Uses the palette default for non-finite input. Computes the preview from the integer
     * position accepted by WearUtils; persistence and phone commands belong to
     * [commitColor].
     *
     * @param progressColor Encoded palette position to normalize and preview.
     */
    private fun applyColor(progressColor: Float) {
        val progress = SignalColorPalette.clampProgress(progressColor)
        _colorProgress.value = progress
        _middleColor.value = WearUtils.computeSpecialColor(progress.toInt())
    }

    /**
     * Attempts to queue one typed standalone preference update on Main.
     *
     * Skips updates while standaloneBeforeImport holds a profile, preserving standalone
     * preferences while phone settings are imported. Otherwise the independent preference
     * scope consumes edits in order through editSafely and reports IO failures. Returns
     * without waiting; stopping the foreground session does not cancel queued writes.
     *
     * @param T Value type associated with the preference key.
     * @param key Typed standalone preference key to update.
     * @param value Value to commit for the key.
     */
    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        if (standaloneBeforeImport == null) preferenceWrites.trySend { prefs -> prefs[key] = value }
    }

    /**
     * Bounds command queues, phone liveness checks and resume backoff.
     */
    private companion object {
        /**
         * Maximum number of user actions buffered for a linked session.
         */
        const val MAX_QUEUED_CONTROLS = 16
        /**
         * Delay between linked-phone liveness checks, in milliseconds.
         */
        const val LINK_CHECK_INTERVAL_MS = 5_000L
        /**
         * Delay before retrying a phone resume, in milliseconds.
         */
        const val LINK_RETRY_DELAY_MS = 500L
        /**
         * Maximum backoff between phone resume attempts, in milliseconds.
         */
        const val MAX_LINK_RETRY_INTERVAL_MS = 30_000L
    }
}
