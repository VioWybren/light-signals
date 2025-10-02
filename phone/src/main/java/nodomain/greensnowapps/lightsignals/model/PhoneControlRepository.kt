package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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

private const val TAG = "PhoneControlRepository"

// Define preference keys for the DataStore
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

    /**
     * Indicates where/when the start attempt is invoked from.
     * - APP_START: invoked from inside the repository during application startup.
     *              In this case the repo will NOT auto-notify the UI about missing permissions.
     * - RUNTIME: invoked from outside the repository while the app is already running.
     *            In this case the repo WILL call notifyUiPermissionsMissing() when permissions are missing.
     */
    enum class CallOrigin {
        APP_START,
        RUNTIME
    }

    // Read the saved index *synchronously* so we can seed our StateFlow
    private val initialScreenIndex: Int = runBlocking(Dispatchers.IO) {
        dataStore.data.first()[KEY_ACTIVE_SCREEN] ?: 0
//        dataStore.data
//            .map { preference ->
//                preference[KEY_ACTIVE_SCREEN] ?: 0
//                Log.d("MY_TAG", "PCR screen = ${preference[KEY_ACTIVE_SCREEN] ?: 0}")
//            }
//            .first()   // grab the very first emission
    }

//    private val initialProgressColor: Float = runBlocking(Dispatchers.IO) {
//        dataStore.data
//            .map { prefs -> prefs[PROGRESS_KEY] ?: DEFAULT_COLOR_PROGRESS   }
//            .first()
//    }

    // Read the saved index *synchronously* so we can seed our StateFlow
    private var initialActiveBtnIndex: Int = runBlocking(Dispatchers.IO) {
        dataStore.data.first()[KEY_PREF_ACTIVE_COLOR_BUTTON] ?: 0
    }

    // StateFlows
    private val _flashOn = MutableStateFlow(false)
    private val _colorBlinkActive = MutableStateFlow(false)
    private val _blinkDelay = MutableStateFlow(500L)
    private val _sosOn = MutableStateFlow(false)
    private val _blinkOn = MutableStateFlow(false)
    private val _activeSound = MutableStateFlow(0)   // 0 = off, 1..3 = which beep
    private val _screenIndex = MutableStateFlow(initialScreenIndex)

    val flashOn: StateFlow<Boolean> = _flashOn.asStateFlow()
    val colorBlinkActive: StateFlow<Boolean> = _colorBlinkActive.asStateFlow()
    val blinkDelay: StateFlow<Long> = _blinkDelay.asStateFlow()
    val sosOn: StateFlow<Boolean> = _sosOn.asStateFlow()
    val blinkOn: StateFlow<Boolean> = _blinkOn.asStateFlow()
    val activeSound: StateFlow<Int> = _activeSound
    val screenIndex: StateFlow<Int> = _screenIndex.asStateFlow()

    // one single scope for _all_ jobs in this repo
//    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repoScope: CoroutineScope
    private var colorJob: Job? = null
    private var sosJob: Job? = null

    // Read the saved index *synchronously* so we can seed our StateFlow
//    private val initialIndex: Int = runBlocking {
//        dataStore.data
//            .map { preference ->
//                preference[KEY_ACTIVE_COLOR] ?: 2
//                Log.d("MY_TAG", "PCR color = ${preference[KEY_ACTIVE_COLOR] ?: 2}")
//            }
//            .first()   // grab the very first emission
//    }

//    // Read the saved index *synchronously* so we can seed our StateFlow
//    private val initialIndex: Int = runBlocking(Dispatchers.IO) {
//        dataStore.data.first()[KEY_ACTIVE_COLOR] ?: 0
//    }

    // Now initialize the MutableStateFlow with the real saved value
    private val _activeButton = MutableStateFlow(
        Active.entries.getOrNull(initialActiveBtnIndex) ?: Active.RED
    )
    //    private val _activeButton = MutableStateFlow(Active.RED)
    val activeButton: StateFlow<Active> = _activeButton.asStateFlow()

//    private val capabilityClient = Wearable.getCapabilityClient(context)

    //    private val capabilityName = "wear_sync"
//    private val capabilityName = "data_layer_app_helper_device_watch"

//    private val _isWatchConnected = MutableStateFlow(false)
//    val isWatchConnected: StateFlow<Boolean> = _isWatchConnected


    init {
//        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        Log.d(TAG,"screen init block")
        Log.d(TAG,"INIT initialIndex init = $initialActiveBtnIndex")
    }

    //  Initialize states from DataStore preferences
    fun initFromPrefs() {
        // one single scope for _all_ jobs in this repo
        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        repoScope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }  // Handle errors by emitting empty preferences
                .take(1)            // <-- only one emission
                .collect { prefs ->

                    // load initial data
                    val pcs = PhoneControlState.Companion.fromDataStore(dataStore)

                    _blinkOn.value = pcs.blinkEnabled
                    _flashOn.value = pcs.torchOn
                    _blinkDelay.value = pcs.frequency
                    _activeSound.value = pcs.activeSound
                    _activeButton.value = Active.entries.getOrNull(pcs.activeColorButton ?: 0) ?: Active.RED
                    _screenIndex.value = pcs.screenIndex
                    updateSos(pcs.sosEnabled)

                    Log.d(TAG,"INIT -----------------------------")
                    Log.d(TAG,"INIT _flashOn.value = ${_flashOn.value}")
                    Log.d(TAG,"INIT _blinkDelay.value = ${_blinkDelay.value}")
                    Log.d(TAG,"INIT _blinkOn.value = ${_blinkOn.value}")
                    Log.d(TAG,"INIT _activeSound.value = ${_activeSound.value}")
                    Log.d(TAG,"INIT _sosOn.value = ${_sosOn.value}")
                    Log.d(TAG,"INIT _activeButton.value = ${_activeButton.value}")
                    Log.d(TAG,"INIT _screenIndex.value = ${_screenIndex.value}")
                    Log.d(TAG,"INIT initialIndex = $initialActiveBtnIndex")

                    getWearState()
//                    startCapabilityMonitoring()
                    initBlink()
//                    checkConnect()// temporary, for testing only
//                    checkNodes()// temporary, for testing only
                    tryStartServiceIfPermissionsGranted(CallOrigin.APP_START)
                }
        }
        initSound()
    }

    fun getWearState() {
        repoScope.launch {
            phoneSyncStateRepository.stateFlow
                .filterNotNull()    // ignore the null
                .collect { controlState ->
                    Log.d(TAG,"update collect")
                    Log.d(TAG,"update ---------------")
                    _blinkOn.value = controlState!!.blinkEnabled
                    Log.d(TAG,"update _blinkOn.value = ${_blinkOn.value}")
                    setActiveSound(controlState.activeSound)
                    _screenIndex.value = controlState.screenIndex
                    Log.d(TAG,"update _screenIndex.value = ${_screenIndex.value}")
                    updateSos(controlState.sosEnabled)
                    updateColorProgress(controlState.color)
                    updateFlash(controlState.torchOn)
                    updateFrequencyProgress(controlState.frequency.toFloat())
                    updateActiveButton(Active.entries[controlState.activeColorButton])
                }
        }
    }

    suspend fun updateSos(newSos: Boolean) {
        Log.d(TAG,"updateSos($newSos)")
        _sosOn.value != newSos
        switchSOS(newSos)
    }

    suspend fun updateFlash(newFlash: Boolean) {
        Log.d(TAG,"updateFlash($newFlash)")
        if (_flashOn.value != newFlash) {
            if (newFlash) select(Active.FLASH)
            else select(_activeButton.value)
        }
    }

    suspend fun updateColorProgress(newColorProgress: Float) {
        Log.d(TAG,"updateColorProgress($newColorProgress)")
        dataStore.edit { preferences ->
            preferences[KEY_COLOR_PROGRESS] = newColorProgress
        }
    }

    suspend fun updateFrequencyProgress(newFrequencyProgress: Float) {
        Log.d(TAG,"updateFrequencyProgress($newFrequencyProgress)")
        dataStore.edit { preferences ->
            preferences[KEY_FREQUENCY_PROGRESS] = newFrequencyProgress
        }
    }

    suspend fun updateActiveButton(newActiveButton:Active) {
        dataStore.edit { preferences ->
            preferences[KEY_PREF_ACTIVE_COLOR_BUTTON] = newActiveButton.ordinal
        }
        _activeButton.value = newActiveButton
        Log.d(TAG,"updateActiveButton newActiveButton($newActiveButton)")
        Log.d(TAG,"updateActiveButton newActiveButton newActiveButton.ordinal = ${newActiveButton.ordinal}")
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        Log.d(TAG,"updateScreenIndex(${newScreenIndex})")
        _screenIndex.value = newScreenIndex
        writePref(KEY_ACTIVE_SCREEN, newScreenIndex)
    }

//    private fun startCapabilityMonitoring() {
//        //  Listen for changes in reachable nodes with our wear_sync capability
//        capabilityClient.addListener(capListener, capabilityName)
//        //  Prime the initial value
//        capabilityClient.getCapability(capabilityName, CapabilityClient.FILTER_REACHABLE)
//            .addOnSuccessListener { info ->
//                _isWatchConnected.value = info.nodes.isNotEmpty()
//            }
//    }
//
//    private val capListener =
//        CapabilityClient.OnCapabilityChangedListener { info ->
//            _isWatchConnected.value = info.nodes.isNotEmpty()
//        }
//
//    private fun stopCapabilityMonitoring() {
//        capabilityClient.removeListener(capListener, capabilityName)
//    }

//        fun checkConnect() {
//        CoroutineScope(Dispatchers.IO).launch {
//            isWatchConnected.collect { connect ->
//                Log.d(TAG,"checkNodes isWatchConnected: $connect")
//            }
//        }
//    }

    //  Flow<Set<Node>>
//    @OptIn(ExperimentalHorologistApi::class)
//    val installedWatchNodes = phoneDataLayerAppHelper.connectedAndInstalledNodes

//            /**
//             * A simple boolean flow you can use to enable/disable UI.
//             */
//    val isWatchAppAvailable: StateFlow<Boolean> =
//        installedWatchNodes
//            .map { it.isNotEmpty() }
//            .stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.WhileSubscribed(5_000), false)

//    fun checkNodes() {
//        CoroutineScope(Dispatchers.IO).launch {
//            installedWatchNodes.collect { set ->
//                Log.d(TAG,"set of Nodes: $set")
//            }
//        }
//    }


    fun initSound() {
        if (colorBlinkActive.value && activeSound.value != 0) {
            soundPool.playOnce(activeSound.value)
        }
    }

    fun initBlink() {
        repoScope.launch {
            blinkOn.collect {
                setBlinking(_blinkOn.value, _blinkDelay.value)
                Log.d(TAG,"_blinkOn.value: ${_blinkOn.value}")
            }
        }
    }

    suspend fun select(button:Active) {
        Log.d(TAG,"Selected button = $button ")
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
        Log.d(TAG,"switchFlash($isOn)")
        if (!isOn) {
            if (sosOn.value) {
                switchSOS(false)
            }
        }
        setFlash(isOn)
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
        if (on) {
            if (tryStartServiceIfPermissionsGranted(CallOrigin.RUNTIME)) {
            }
        }
        if (!on) {
            if (_blinkOn.value) {
                setBlinking(true, _blinkDelay.value)
            }
        }
    }

    /** Toggle hardware flash blink mode */
    fun setFlashBlinkMode(on: Boolean) {
        Log.d(TAG,"setFlashBlinkMode $on")
        if (_flashOn.value) {
            _blinkOn.value = on
            writePref(KEY_BLINK_ON, on)
        }
        _flashOn.value = on
        writePref(KEY_FLASH_ON, on)
    }

    private fun startColorBlink(delayMs: Long) {
        Log.d(TAG,"startColorBlink() colorJob?_1 = $colorJob?")
        setColorSOS(false)
        _blinkOn.value = true
        colorJob?.cancel()
//        colorJob = null
        Log.d(TAG,"startColorBlink() colorJob?_2 = $colorJob?")
        colorJob = repoScope.launch {
            Log.d(TAG,"startColorBlink() colorJob?_3 = $colorJob?")
            while (isActive && _blinkOn.value) {
                _colorBlinkActive.value = true
                Log.d(TAG,"startColorBlink() _colorBlinkActive.value = true")
                delay(delayMs)
                _colorBlinkActive.value = false
                Log.d(TAG,"startColorBlink() _colorBlinkActive.value = false")
                delay(delayMs)
            }
        }
    }

    private fun stopColorBlink() {
        Log.d(TAG,"stopColorBlink()")
        colorJob?.cancel()
        colorJob = null
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
        sosJob?.cancel()
        writePref(KEY_SOS_ON, false)
    }

    private fun startColorSos() {
        Log.d(TAG,"startColorSos() sosJob? = $sosJob")
        writePref(KEY_SOS_ON, true)
        sosJob?.cancel()
        sosJob = repoScope.launch {
            // SOS pattern: ... --- ...
            val dot = SOS_UNIT_MS
            val dash = 3 * SOS_UNIT_MS
            val gap = SOS_UNIT_MS       // between parts
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

    /**
     * Notifies UI  via broadcast that permissions are missing and UI should prompt the user.
     */
    private fun notifyUiPermissionsMissing() {
        Log.w("MY_LOG", "PCR notifyUiPermissionsMissing()")
        val intent = Intent(ACTION_PERMISSIONS_MISSING)
        intent.putExtra(EXTRA_MISSING_CAMERA, !PermissionUtils.hasCameraPermission(context))
        intent.putExtra(
            EXTRA_MISSING_NOTIFICATIONS,
            !PermissionUtils.hasPostNotificationsPermission(context)
        )
        context.sendBroadcast(intent)
    }

    /**
     * Try to start the service if required runtime permissions are already granted.
     *
     * Behavior:
     *  - Checks POST_NOTIFICATIONS and CAMERA permissions via PermissionUtils.
     *  - If a required permission is missing, returns false and:
     *      - If [callOrigin] == CallOrigin.RUNTIME, calls notifyUiPermissionsMissing() so UI can prompt user.
     *      - If [callOrigin] == CallOrigin.APP_START, does NOT call notifyUiPermissionsMissing(); the caller is expected
     *        to decide what to do (useful for silent app-start flows).
     *  - If permissions are OK, attempts to start the service and returns true on success.
     *
     * Note: This method does NOT request permissions. The caller/UI should request missing permissions when false is returned.
     *
     * @param callOrigin describes where the call comes from; default = CallOrigin.RUNTIME (repo will notify UI).
     * @return true if the service was successfully started, false otherwise (permissions missing or start failed).
     */
    private fun tryStartServiceIfPermissionsGranted(callOrigin: CallOrigin = CallOrigin.RUNTIME): Boolean {
        Log.d("MY_LOG", "PCR startService() method")
        if (!PermissionUtils.hasPostNotificationsPermission(context)) {
            Log.d("MY_LOG", "PCR Notifications permission missing; do not start FlashService")
            // Can't post notifications — we cannot run as a foreground service
            // Inform UI to ask lightsignals to enable permissions (via broadcast)
            if (callOrigin == CallOrigin.RUNTIME) {
                notifyUiPermissionsMissing()
            }
            return false
        }
        // If we also need to toggle torch (camera API), check camera permission too
        // perform degraded behavior, inform UI, or stop action that needs camera
        if (!PermissionUtils.hasCameraPermission(context)) {
            Log.d("MY_LOG", "PCR Camera permission missing; do not start FlashService")
            if (callOrigin == CallOrigin.RUNTIME) {
                notifyUiPermissionsMissing()
            }
            return false
        } else {
            return startService()
        }
    }

    /**
     * Builds and starts the FlashService as a foreground service
     * with exceptions handling.
     */
    private fun startService(): Boolean {
        try {
            val flashServiceIntent = Intent(context, FlashService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, flashServiceIntent)
            } else {
                context.startService(flashServiceIntent)
            }
            return true
        } catch (e: Exception) {
            Log.e("MY_LOG", "Failed to start service", e)
            return false
        }
    }


    fun clearAll() {
        colorJob?.cancel()
        sosJob?.cancel()
        // cancel everything this repo ever launched
        repoScope.cancel()
    }

    //universal DataStore pref writer
    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        repoScope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

}


