package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.content.Intent
import android.os.Process
import android.util.Log
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
import nodomain.greensnowapps.lightsignals.model.FlashService
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

private const val TAG = "PhoneMainViewModel"

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
//    Note: Calling runBlocking in your ViewModel’s constructor is generally acceptable for a single,
//    short I/O call like reading one preference, because it happens off the main thread
//    (Hilt-instantiated ViewModels are created on a background thread).
//    If you have a large number of prefs to load, or very heavy work, consider showing
//    a splash/loading screen instead—but for one integer, this pattern is totally fine.

//    // Read the saved index *synchronously* so we can seed our StateFlow
//    private val initialIndex: Int = runBlocking {
//        dataStore.data
//            .map { preference -> preference[KEY_ACTIVE_COLOR] ?: 0 }
//            .first()   // grab the very first emission
//    }
//
//    // Now initialize the MutableStateFlow with the real saved value
//    private val _activeButton = MutableStateFlow(
//        Active.entries.getOrNull(initialIndex) ?: Active.RED
//    )
//    val activeButton: StateFlow<Active> = _activeButton.asStateFlow()

    //Read the real stored frequency *synchronously* once
    private val initialFrequency: Float = runBlocking(Dispatchers.IO) {
        dataStore.data
            .map { prefs -> prefs[KEY_FREQUENCY_PROGRESS] ?: 400f }
            .first()
    }

    // Build StateFlow seeded with that true initial value
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

    // Create a Flow for progress value.
    val progressColor: StateFlow<Float> = dataStore.data
        .catch { emit(emptyPreferences()) }    // safety on error
        .map { preferences ->
            preferences[KEY_COLOR_PROGRESS] ?: initialProgressColor    // Default value is 0f
        }
        // Convert the Flow to StateFlow if needed (or use directly in composable)
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), initialProgressColor)

    // Read the saved index *synchronously* so we can seed our StateFlow
    private val initialColorIndex: Int = runBlocking(Dispatchers.IO) {
        dataStore.data.first()[KEY_SIGNAL_SCREEN_COLOR] ?: 0
    }

    // Now initialize the MutableStateFlow with the real saved value
    private val initialColor = Active.entries.getOrNull(initialColorIndex) ?: Active.RED

    //    middle‐button color as a Flow (or StateFlow)
    private val _middleColor =
        MutableStateFlow(PhoneUtils.computeSpecialColor(initialProgressColor.toInt()))
    val middleColor: StateFlow<Color> = _middleColor.asStateFlow()


    // forward flows
    val flashOn = phoneCtrl.flashOn
    val blinkOn = phoneCtrl.blinkOn
    val colorBlinkActive = phoneCtrl.colorBlinkActive
    val blinkDelay = phoneCtrl.blinkDelay
    val screenIndex = phoneCtrl.screenIndex
    val activeButton = phoneCtrl.activeButton
    val sosOn = phoneCtrl.sosOn

    val controlMessageFlow = phoneWearOperations.controlMessageFlow
    val isLaunchingOnWear = phoneWearOperations.isLaunchingOnWear

    // A Flow<Active> that emits exactly one element:
    //    the saved index → Active enum (or RED if missing)
    private val prefsFlow: Flow<Active> = dataStore.data
        .map { prefs ->
            val savedIndex = prefs[KEY_PREF_ACTIVE_COLOR_BUTTON] ?: 0
            Active.entries.getOrNull(savedIndex) ?: Active.RED
        }

    /**
     * A Flow<Active> that first emits saved preference,
     * then emits every value from repo.activeButton.
     */
    val uiActiveButton: Flow<Active> = flow {
        //  Read the saved index from DataStore (suspends once)
        val prefs = dataStore.data.first()
        val savedIndex = prefs[KEY_PREF_ACTIVE_COLOR_BUTTON] ?: 0
        val initial = Active.entries.getOrNull(savedIndex) ?: Active.RED
        //  Emit it as the very first value
        emit(initial)
        //  Then emit all subsequent values from the repo
        emitAll(activeButton)
    }

    /**
     * Convert into a hot StateFlow for the UI, with a safe fallback value.
     * UI will immediately get Active.RED if nothing has emitted yet,
     * then promptly get the real saved value,
     * then any live updates.
     */
//    val uiActiveButton: StateFlow<Active> = combinedFlow
//        .stateIn(
//            scope        = viewModelScope,
//            started      = SharingStarted.WhileSubscribed(5_000),
//            initialValue = Active.RED
//        )


    // Combine  `active` enum + `middleColor` into one activeColor flow
//    val activeColor: StateFlow<Color> = activeButton
    val activeColor: StateFlow<Color> = uiActiveButton
        .combine(middleColor) { activeBtn, midCol ->
            Log.d(TAG, "activeBtn = $activeBtn ; midCol = $midCol")
            Log.d(TAG, "initialColorIndex = $initialColorIndex")
            when (activeBtn) {
                Active.RED -> Color.Companion.Red
                Active.MIDDLE -> midCol
                Active.GREEN -> Color.Companion.Green
                Active.FLASH -> Color.Companion.Black   // or whatever a flash‐on color is
            }
        }
        .stateIn(
            scope = viewModelScope,     // or controller's CoroutineScope
            started = SharingStarted.Companion.Eagerly,
//            initialValue = Color.Red
            initialValue = Color(initialColorIndex)
        )


    init {
        viewModelScope.launch {
            Log.d("MY_TAG", "MainViewModel call initFromViewModel()")
            phoneSyncStateRepository.initFromViewModel()
        }
        // Load the persisted state from DataStore when the ViewModel is created
        viewModelScope.launch {
            Log.d("MY_TAG", "MainViewModel call initFromPrefs()")
            phoneCtrl.initFromPrefs()
        }

        viewModelScope.launch {
            progressColor.collect { prog ->
                _middleColor.value = PhoneUtils.computeSpecialColor(prog.toInt())
                Log.d("MY_TAG", "MainViewModel _middleColor1.value  = ${_middleColor.value}")
            }
        }

        viewModelScope.launch {
            activeColor.filterNotNull()
                .collect { color ->
                    writePref(KEY_SIGNAL_SCREEN_COLOR, color.toArgb())
                    Log.d(TAG, "activeColor  = $color")
                }
        }

//        loadActiveState()
    }


//    private fun loadActiveState() {
//        viewModelScope.launch {
//            dataStore.data
//                .catch { emit(emptyPreferences()) }
//                .collect { prefs ->
//                    val idx = prefs[KEY_ACTIVE_COLOR] ?: 0
//                    Log.d(TAG,"Restored active index = $idx")
//                    _activeButton.value = Active.entries.getOrNull(idx) ?: Active.RED
//                }
//        }
//    }

    fun select(button: Active) {
        viewModelScope.launch {
            phoneCtrl.select(button)
        }
    }

    fun switchFlash(isOn: Boolean) {
        phoneCtrl.switchFlash(isOn)
    }

    fun toggleControl(on: Boolean) {
        Log.d(TAG, "toggleControl($on)")
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
        Log.d(TAG, "updateBlinkDelay($ms); blinkOn.value = ${blinkOn.value}")
        phoneCtrl.setBlinking(blinkOn.value, ms)
    }

    fun toggleSos(on: Boolean) {
        Log.d(TAG, "toggleSos($on)")
        phoneCtrl.switchSOS(on)
    }

    /**
     * Update the progress in our DataStore.
     */
    fun updateColorProgress(newColorProgress: Float) {
        viewModelScope.launch {
            phoneCtrl.updateColorProgress(newColorProgress)
        }
    }

    /**
     * Update the progress in our DataStore.
     */
    fun updateFrequencyProgress(newFrequencyProgress: Float) {
        viewModelScope.launch {
            phoneCtrl.updateFrequencyProgress(newFrequencyProgress)
        }
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        Log.d(TAG, "updateScreenIndex($newScreenIndex)")
        viewModelScope.launch {
            phoneCtrl.updateScreenIndex(newScreenIndex)
        }
    }

    fun startWearOperations(command: String) {
        viewModelScope.launch {
//            phoneCtrl.startWearOperations(command)
            phoneWearOperations.startWearOperations(command)
        }
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    private fun stopFlashLightService() {
        val serviceFlashLightIntent = Intent(appContext, FlashService::class.java)
        appContext.stopService(serviceFlashLightIntent)
    }

    override fun onCleared() {
        super.onCleared()
        phoneCtrl.clearAll()
//        phoneSyncStateRepository.clear()
        phoneSoundPoolPlayer.release()
        stopFlashLightService()
        Process.killProcess(Process.myPid())
    }
}