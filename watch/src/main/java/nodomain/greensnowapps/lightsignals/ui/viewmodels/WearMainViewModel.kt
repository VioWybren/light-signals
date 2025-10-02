package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.os.Process
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.WearDataLayerRegistry
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nodomain.greensnowapps.lightsignals.WearConstants.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.model.WearSyncStateRepository
import nodomain.greensnowapps.lightsignals.model.KEY_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.model.KEY_FREQUENCY_PROGRESS
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.model.WearControlState
import nodomain.greensnowapps.lightsignals.model.WearPhoneOperations
import nodomain.greensnowapps.lightsignals.moduls.ApplicationScope
import javax.inject.Inject

private const val TAG = "WearMainViewModel"

private val KEY_ACTIVE_COLOR = intPreferencesKey("active_btn")

@HiltViewModel
class WearMainViewModel @OptIn(ExperimentalHorologistApi::class)
@Inject constructor(
//    @ApplicationScope private val appScope: CoroutineScope,
    private val wearDataLayerAppHelper: WearDataLayerAppHelper,
    private val wearCtrl: WearControlRepository,
    private val dataStore: DataStore<Preferences>,
    private val wearSyncStateRepository: WearSyncStateRepository,
    private val wearPhoneOperations: WearPhoneOperations
) : ViewModel() {

    enum class Active { RED, MIDDLE, GREEN, FLASH }

//    Note: Calling runBlocking in your ViewModel’s constructor is generally acceptable for a single,
//    short I/O call like reading one preference, because it happens off the main thread
//    (Hilt-instantiated ViewModels are created on a background thread).
//    If you have a large number of prefs to load, or very heavy work, consider showing
//    a splash/loading screen instead—but for one integer, this pattern is totally fine.
//
//     Read the saved index *synchronously* so we can seed our StateFlow
    private val initialIndex: Int = runBlocking {
        dataStore.data
            .map { preference -> preference[KEY_ACTIVE_COLOR] ?: 0 }
            .first()   // grab the very first emission
    }

    // Now initialize the MutableStateFlow with the real saved value
    private val _active = MutableStateFlow(
        Active.entries.getOrNull(initialIndex) ?: Active.MIDDLE
    )
    val activeButton: StateFlow<Active> = _active.asStateFlow()

    // Read the real stored frequency *synchronously* once
    private val initialFrequency: Float = runBlocking(Dispatchers.IO) {
        dataStore.data
            .map { prefs -> prefs[KEY_FREQUENCY_PROGRESS] ?: 400f }
            .first()
    }

    // Build your StateFlow seeded with that true initial value
    val progressFrequency: StateFlow<Float> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_FREQUENCY_PROGRESS] ?: initialFrequency }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5_000L),
            initialValue = initialFrequency
        )

//    val flashOn = wearCtrl.flashOn
//    val blinkOn = wearCtrl.blinkOn
//    val colorBlinkActive = wearCtrl.colorBlinkActive
//    val blinkDelay = wearCtrl.blinkDelay
//    val middleColor = wearCtrl.middleColor
//    val activeSound = wearCtrl.activeSound
//    val screenIndex = wearCtrl.screenIndex
//    val sosOn: StateFlow<Boolean> = wearCtrl.sosOn      // forward the sosOn flow
//    val phoneConnection = wearCtrl.phoneConnection
//
//    val controlMessageFlow = wearPhoneOperations.controlMessageFlow
//    val isLaunchingOnPhone = wearPhoneOperations.isLaunchingOnPhone
////    val isPhoneAppAvailable = wearPhoneOperations.isPhoneAppAvailable

    private val initialProgressColor: Float = runBlocking(Dispatchers.IO) {
        dataStore.data
            .map { prefs -> prefs[KEY_COLOR_PROGRESS] ?: DEFAULT_COLOR_PROGRESS}
            .first()
    }

    // Create a Flow for progress value.
    val progressColor: StateFlow<Float> = dataStore.data
        .catch { emit(emptyPreferences()) }    // safety on error
        .map { preferences ->
            preferences[KEY_COLOR_PROGRESS] ?: initialProgressColor
        }
        // Convert the Flow to StateFlow if needed (or use directly in composable)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue =  initialProgressColor)


    val flashOn = wearCtrl.flashOn
    val blinkOn = wearCtrl.blinkOn
    val colorBlinkActive = wearCtrl.colorBlinkActive
    val blinkDelay = wearCtrl.blinkDelay
    val middleColor = wearCtrl.middleColor
    val activeSound = wearCtrl.activeSound
    val screenIndex = wearCtrl.screenIndex
    val sosOn: StateFlow<Boolean> = wearCtrl.sosOn      // forward the sosOn flow
    val phoneConnection = wearCtrl.phoneConnection

    val controlMessageFlow = wearPhoneOperations.controlMessageFlow
    val isLaunchingOnPhone = wearPhoneOperations.isLaunchingOnPhone
//    val isPhoneAppAvailable = wearPhoneOperations.isPhoneAppAvailable

//    // Create a Flow for progress value.
//    val progressColor: Flow<Float> = dataStore.data
//        .catch { emit(emptyPreferences()) }    // safety on error
//        .map { preferences ->
//            preferences[KEY_COLOR_PROGRESS] ?: DEFAULT_COLOR_PROGRESS     // Default value is 0f
//        }
//        // Convert the Flow to StateFlow if needed (or use directly in composable)
//        .stateIn(
//            viewModelScope,
//            SharingStarted.Companion.WhileSubscribed(5000),
//            DEFAULT_COLOR_PROGRESS
//        )

    private val _controlState = MutableStateFlow(WearControlState())
    val controlState: StateFlow<WearControlState> = _controlState.asStateFlow()

    @OptIn(ExperimentalHorologistApi::class)
    val installedPhoneNodes = wearDataLayerAppHelper.connectedAndInstalledNodes
   /**
     * A simple boolean you can use to enable/disable UI.
     */
    val isPhoneAppAvailable: StateFlow<Boolean> =
        installedPhoneNodes
            .map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Load the persisted state from DataStore when the ViewModel is created
        viewModelScope.launch {
            wearCtrl.initFromPrefs()
        }

        viewModelScope.launch {
            progressColor.collect { prog ->
                wearCtrl.calcMiddleColor(prog)
            }
        }
//        loadActiveState()

        // Collect each flow separately and update only its field
        viewModelScope.launch {
            blinkOn.collect { newBlink ->
                _controlState.update { it.copy(blinkEnabled = newBlink) }
            }
        }
        viewModelScope.launch {
            sosOn.collect { newSos ->
                _controlState.update { it.copy(sosEnabled = newSos) }
            }
        }
        viewModelScope.launch {
            flashOn.collect { newFlash ->
                _controlState.update { it.copy(torchOn = newFlash) }
            }
        }
        viewModelScope.launch {
            blinkDelay.collect { newDelay ->
                _controlState.update { it.copy(frequency = newDelay) }
            }
        }
        viewModelScope.launch {
            activeSound.collect { newSound ->
                _controlState.update { it.copy(activeSound = newSound) }
            }
        }
        viewModelScope.launch {
            colorBlinkActive.collect { newColorBlink ->
                _controlState.update { it.copy(colorBlinkEnabled = newColorBlink) }
            }
        }

        viewModelScope.launch {
            progressColor.collect { newColor ->
                _controlState.update { it.copy(color = newColor) }
            }
        }

        viewModelScope.launch {
            screenIndex.collect { newIndex ->
                _controlState.update { it.copy(screenIndex = newIndex) }
            }
        }

        viewModelScope.launch {
            activeButton.collect { activeBtn ->
                _controlState.update {
                    it.copy(activeColorButton = activeBtn.ordinal)
                }
                Log.d(TAG,"activeBtn.ordinal = ${activeBtn.ordinal}")
            }
        }

        // Finally: send to phone by collecting only the single StateFlow:
        viewModelScope.launch(Dispatchers.IO) {
            controlState
                .collect { state ->
                    if (phoneConnection.value) {
                        wearSyncStateRepository.sendState(state)
                    }
                }
        }
    }

//    private fun loadActiveState() {
//        viewModelScope.launch {
//            dataStore.data
//                .catch { emit(emptyPreferences()) }
//                .collect { prefs ->
//                    val idx = prefs[KEY_ACTIVE_COLOR] ?: 0
//                    Log.d(TAG,"Restored active index = $idx")
//                    _active.value = Active.entries.getOrNull(idx) ?: Active.MIDDLE
//                }
//        }
//    }

    fun select(button: Active) {
        Log.d(TAG,"Selected button = $button ")
        if (button == Active.FLASH) {
            switchFlash(true)
        } else {
            _active.value = button
            writePref(KEY_ACTIVE_COLOR, button.ordinal)
            val s = sosOn.value
            switchFlash(false)
            if (s) {
                toggleSos(true)
            }
        }

        if (!sosOn.value) {
            wearCtrl.setBlinking(blinkOn.value, blinkDelay.value)
        } else {
            toggleSos(true)
        }
    }

    fun switchFlash(isOn: Boolean) {
        Log.d(TAG,"switchFlash($isOn)")
        if (!isOn) {
            if (sosOn.value) {
                toggleSos(false)
            }
        }
        wearCtrl.setFlash(isOn)
    }

    fun toggleControl(on: Boolean) {
        Log.d(TAG,"toggleControl($on)")
        if (on) {
            if (sosOn.value) {
                val f = flashOn.value
                toggleSos(false)
                wearCtrl.setFlash(f)
            }
        }
        wearCtrl.setBlinking(on, blinkDelay.value)
    }

    fun updateBlinkDelay(ms: Long) {
        Log.d(TAG,"updateBlinkDelay($ms); blinkOn.value = ${blinkOn.value}")
        wearCtrl.setBlinking(blinkOn.value, ms)
    }

    // Combine  `active` enum + `middleColor` into one activeColor flow
    val activeColor: StateFlow<Color> = activeButton
        .combine(middleColor) { activeBtn, midCol ->
            Log.d(TAG,"activeBtn = $activeBtn")
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
            initialValue = Color.Companion.Red
        )

    fun toggleSos(on: Boolean) {
        Log.d(TAG,"toggleSos($on)")
        wearCtrl.switchSOS(on)
    }

    /**
     * Update the progress in DataStore.
     */
    fun updateColorProgress(newProgress: Float) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_COLOR_PROGRESS] = newProgress
                Log.d("MY_TAG", "updateColorProgress newProgress = $newProgress")
            }
        }
    }

    /**
     * Update the progress in DataStore.
     */
    fun updateFrequencyProgress(newFrequencyProgress: Float) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_FREQUENCY_PROGRESS] = newFrequencyProgress
            }
        }
    }

    fun updateScreenIndex(newScreenIndex: Int) {
        Log.d(TAG,"updateScreenIndex($newScreenIndex)")
        wearCtrl.updateScreenIndex(newScreenIndex)
    }

    fun togglePhoneConnection(isConnect: Boolean) {
        wearCtrl.togglePhoneConnection(isConnect)
    }

    private fun <T> writePref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun performWearOperations(command: String) {
        viewModelScope.launch {
            wearPhoneOperations.performWearOperations(command)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG,"onCleared()")
        wearSyncStateRepository.clear()
        wearPhoneOperations.clearAll()
        wearCtrl.clearAll()
    }

    /** Stop long-running coroutines started by this VM on request. */
    fun stopWork() {
        // cancel only children, keep viewModelScope usable if recreated (idempotent)
        viewModelScope.coroutineContext[Job]?.cancelChildren()
        Log.d("MainVM", "stopWork() called")
    }

}