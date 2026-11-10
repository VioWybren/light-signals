package nodomain.greensnowapps.lightsignals.model

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
import nodomain.greensnowapps.lightsignals.core.signal.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.core.signal.runSosSignal
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchModeChange
import nodomain.greensnowapps.lightsignals.utils.WearUtils
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import java.util.UUID
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
internal val KEY_ACTIVE_COLOR = intPreferencesKey("active_btn")

@Singleton
class WearControlRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val stopSounds: () -> Unit,
    private val newSessionScope: () -> CoroutineScope,
    private val preferenceScope: CoroutineScope,
    private val onPreferenceError: (IOException) -> Unit = {
        Log.w("WearControlRepository", "Unable to access saved settings", it)
    },
    private val sendLinkedCommand: suspend (String, WatchControlCommand) -> Boolean = { _, _ -> false }
) {
    @Inject constructor(
        dataStore: DataStore<Preferences>,
        soundPool: WearSoundPoolPlayer,
        syncRepository: WearSyncStateRepository
    ) : this(
        dataStore,
        soundPool::stopAll,
        { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) },
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        sendLinkedCommand = syncRepository::sendCommand
    )
    private val _flashOn = MutableStateFlow(false)
    private val _colorBlinkActive = MutableStateFlow(false)
    private val _blinkDelay = MutableStateFlow(400L)
    private val _sosOn = MutableStateFlow(false)
    private val _blinkOn = MutableStateFlow(false)
    private val _activeSound = MutableStateFlow(0)
    private val _screenIndex = MutableStateFlow(0)
    private val _colorProgress = MutableStateFlow(WearConstants.DEFAULT_COLOR_PROGRESS)
    private val _activeColorButton = MutableStateFlow(0)
    private val _middleColor = MutableStateFlow(
        WearUtils.computeSpecialColor(WearConstants.DEFAULT_COLOR_PROGRESS.toInt())
    )
    private val _phoneConnection = MutableStateFlow(false)
    private val _manualPhoneConnection = MutableStateFlow(false)
    private val _isInitialized = MutableStateFlow(false)
    private val _isForeground = MutableStateFlow(false)

    val flashOn = _flashOn.asStateFlow()
    val colorBlinkActive = _colorBlinkActive.asStateFlow()
    val blinkDelay = _blinkDelay.asStateFlow()
    val sosOn = _sosOn.asStateFlow()
    val blinkOn = _blinkOn.asStateFlow()
    val activeSound = _activeSound.asStateFlow()
    val screenIndex = _screenIndex.asStateFlow()
    val colorProgress = _colorProgress.asStateFlow()
    val activeColorButton = _activeColorButton.asStateFlow()
    val middleColor = _middleColor.asStateFlow()
    val phoneConnection = _phoneConnection.asStateFlow()
    internal val manualPhoneConnection = _manualPhoneConnection.asStateFlow()
    internal var manualLinkStartedAt: Long? = null
        private set
    val isInitialized = _isInitialized.asStateFlow()
    val isForeground = _isForeground.asStateFlow()

    private var sessionScope: CoroutineScope? = null
    private var signalJob: Job? = null
    private val foregroundOwners = mutableSetOf<Any>()
    private class LinkedPhone(val nodeId: String, val requestId: UUID) {
        private var sequence = 0L

        fun command(control: WatchControl, value: Int) =
            WatchControlCommand(requestId, ++sequence, control, value)
    }

    private class PendingHandoff(val nodeId: String, val id: UUID) {
        val changes = ArrayDeque<Pair<WatchControl, Int>>()
    }

    private var pendingHandoff: PendingHandoff? = null
    private var linkedPhone: LinkedPhone? = null
    private var standaloneBeforeImport: PhoneSignalState? = null
    private var linkedCommands: Channel<WatchControlCommand>? = null
    private var linkedSender: Job? = null

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
        linkedPhone?.let { phone ->
            startLinkedSender(phone)
            sendControl(WatchControl.CHECK, 0)
        }
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
                _isInitialized.value = true
            }
            restartSignal()
        }
    }

    fun stop(owner: Any) {
        if (!foregroundOwners.remove(owner) || foregroundOwners.isNotEmpty()) return
        _isForeground.value = false
        pendingHandoff = null
        stopLinkedSender()
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
        sendControl(WatchControl.BLINK, WatchModeChange.forSelection(blinkOn, _flashOn.value).wireValue)
    }

    fun setBlinkDelay(delayMs: Long) {
        val delay = delayMs.coerceIn(100L, 1000L)
        _blinkDelay.value = delay
        writePref(KEY_BLINK_DELAY, delay)
        writePref(KEY_FREQUENCY_PROGRESS, delay.toFloat())
        restartSignal()
        sendControl(WatchControl.FREQUENCY, delay.toInt())
    }

    fun setFlash(on: Boolean) {
        updateFlash(on)
        sendControl(WatchControl.TORCH, if (on) 1 else 0)
    }

    internal fun selectColorButton(button: Int) {
        val color = button.coerceIn(0, 2)
        _activeColorButton.value = color
        writePref(KEY_ACTIVE_COLOR, color)
        updateFlash(false)
        sendControl(WatchControl.COLOR_BUTTON, color)
    }

    private fun updateFlash(on: Boolean) {
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
        sendControl(WatchControl.SOS, WatchModeChange.forSelection(on, _flashOn.value).wireValue)
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
        sendControl(WatchControl.SOUND, sound)
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        val index = newScreenIndex.coerceIn(0, 1)
        if (_screenIndex.value == index) return
        _screenIndex.value = index
        writePref(KEY_ACTIVE_SCREEN, index)
        sendControl(WatchControl.SCREEN, index)
    }

    fun togglePhoneConnection(isConnect: Boolean) {
        val previous = linkedPhone
        pendingHandoff = null
        disarmPhoneLink()
        manualLinkStartedAt = if (isConnect) System.currentTimeMillis() else null
        _manualPhoneConnection.value = isConnect
        _phoneConnection.value = isConnect
        if (previous != null) {
            sessionScope?.launch {
                sendLinkedCommand(previous.nodeId, previous.command(WatchControl.DISCONNECT, 0))
            }
        }
    }

    internal fun enableLegacyPhoneLink(): Boolean {
        if (!_isForeground.value || !_isInitialized.value || _phoneConnection.value) return false
        togglePhoneConnection(true)
        return true
    }

    internal fun preparePhoneHandoff(nodeId: String): WatchConnectionRequest? {
        if (!_isForeground.value || !_isInitialized.value || _phoneConnection.value) return null
        val request = WatchConnectionRequest.create(currentSignal())
        pendingHandoff = PendingHandoff(nodeId, request.id)
        return request
    }

    internal fun isPhoneHandoffPending(nodeId: String, requestId: UUID): Boolean =
        pendingHandoff?.let { it.nodeId == nodeId && it.id == requestId } == true

    internal fun finishPhoneHandoff(nodeId: String, requestId: UUID): Boolean {
        val pending = pendingHandoff ?: return false
        if (pending.nodeId != nodeId || pending.id != requestId ||
            !_isForeground.value || !_isInitialized.value
        ) return false
        pendingHandoff = null
        establishPhoneLink(nodeId, requestId)
        pending.changes.forEach { (control, value) -> sendControl(control, value) }
        return true
    }

    internal fun cancelPhoneHandoff(nodeId: String, requestId: UUID) {
        if (isPhoneHandoffPending(nodeId, requestId)) {
            pendingHandoff = null
        }
    }

    internal fun connectFromPhone(phoneNodeId: String, request: WatchConnectionRequest): Boolean {
        if (!_isForeground.value || !_isInitialized.value || !request.isFresh()) return false
        if (hasPhoneLink(phoneNodeId, request.id)) return true
        if (linkedPhone?.let { it.nodeId != phoneNodeId } == true) return false

        // Imported controls are the linked session's baseline. Keep standalone preferences intact.
        pendingHandoff = null
        _manualPhoneConnection.value = false
        manualLinkStartedAt = null
        if (standaloneBeforeImport == null) standaloneBeforeImport = currentSignal().copy(torchOn = false)
        applySignal(request.signal)
        establishPhoneLink(phoneNodeId, request.id)
        return true
    }

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

    internal fun hasPhoneLink(nodeId: String, requestId: UUID): Boolean =
        linkedPhone?.let { it.nodeId == nodeId && it.requestId == requestId } == true

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

    private fun establishPhoneLink(nodeId: String, requestId: UUID) {
        _manualPhoneConnection.value = false
        manualLinkStartedAt = null
        val phone = LinkedPhone(nodeId, requestId)
        linkedPhone = phone
        startLinkedSender(phone)
        _phoneConnection.value = true
    }

    private fun disarmPhoneLink() {
        linkedPhone = null
        stopLinkedSender()
        manualLinkStartedAt = null
        _manualPhoneConnection.value = false
        _phoneConnection.value = false
        val standalone = standaloneBeforeImport
        standaloneBeforeImport = null
        if (standalone != null) {
            stopSounds()
            applySignal(standalone)
        } else if (_flashOn.value) {
            updateFlash(false)
        }
    }

    internal fun commitColor(progress: Float) {
        applyColor(progress)
        val color = _colorProgress.value
        writePref(KEY_COLOR_PROGRESS, color)
        sendControl(WatchControl.COLOR, color.toBits())
    }

    private fun sendControl(control: WatchControl, value: Int) {
        val phone = linkedPhone
        if (phone == null) {
            val pending = pendingHandoff ?: return
            // Keep local changes made while the initial snapshot is in transit in order.
            if (pending.changes.size >= MAX_QUEUED_CONTROLS) pendingHandoff = null
            else pending.changes.addLast(control to value)
            return
        }
        val commands = linkedCommands ?: return
        if (commands.trySend(phone.command(control, value)).isFailure) togglePhoneConnection(false)
    }

    private fun startLinkedSender(phone: LinkedPhone) {
        stopLinkedSender()
        val scope = sessionScope ?: return
        val commands = Channel<WatchControlCommand>(capacity = MAX_QUEUED_CONTROLS)
        linkedCommands = commands
        linkedSender = scope.launch {
            for (command in commands) {
                if (!command.isFresh() || !sendLinkedCommand(phone.nodeId, command)) {
                    if (linkedPhone === phone) {
                        if (command.control == WatchControl.CHECK) disarmPhoneLink()
                        else togglePhoneConnection(false)
                    }
                    break
                }
            }
        }
    }

    private fun stopLinkedSender() {
        linkedSender?.cancel()
        linkedSender = null
        linkedCommands?.cancel()
        linkedCommands = null
    }

    private fun applyColor(progressColor: Float) {
        val progress = SignalColorPalette.clampProgress(progressColor)
        _colorProgress.value = progress
        _middleColor.value = WearUtils.computeSpecialColor(progress.toInt())
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        if (standaloneBeforeImport == null) preferenceWrites.trySend { prefs -> prefs[key] = value }
    }

    private companion object {
        const val MAX_QUEUED_CONTROLS = 16
    }
}
