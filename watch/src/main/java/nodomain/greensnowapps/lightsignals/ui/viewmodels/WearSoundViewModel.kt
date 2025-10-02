package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nodomain.greensnowapps.lightsignals.WearConstants.DEFAULT_VOLUME
import nodomain.greensnowapps.lightsignals.model.KEY_VOLUME
import nodomain.greensnowapps.lightsignals.model.WearSoundPoolPlayer
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import javax.inject.Inject

private const val TAG = "WearSoundViewModel"

@HiltViewModel
class WearSoundViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val wearCtrl: WearControlRepository,
    private val soundPool: WearSoundPoolPlayer
) : ViewModel(), LifecycleEventObserver {

    val colorBlinkActive = wearCtrl.colorBlinkActive
    val activeSound = wearCtrl.activeSound

    // Read the real stored frequency *synchronously* once
    private val initialVolume: Float = runBlocking(Dispatchers.IO) {
        Log.d(TAG,"initialVolume")
        dataStore.data
            .map { prefs -> prefs[KEY_VOLUME] ?:DEFAULT_VOLUME }
            .first()
    }

    /** Flow that emits current saved volume (0f..1f) */
//    val volumeFlow: Flow<Float> = dataStore.data
    val volumeFlow: StateFlow<Float> = dataStore.data
        .drop(1)
        .map { prefs -> prefs[KEY_VOLUME] ?: initialVolume }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialVolume)


    private var blinkJob: Job? = null
    private var isAppInForeground = true

//    private var _volume = MutableStateFlow(0.5f)
//    val volume: StateFlow<Float> = _volume


    init {
//        // Listen for app‐level lifecycle events
        ProcessLifecycleOwner.Companion.get().lifecycle.addObserver(this)
        startCollectingBlinks()
    }

    //  Backing property, mutable within the VM
    private val _showSoundSelector = MutableStateFlow(false)
    //  Public, read‑only view of that state
    val showSoundSelector: StateFlow<Boolean> = _showSoundSelector.asStateFlow()
    //  Call this to flip the flag
    fun setShowSoundSelector(show: Boolean) {
        _showSoundSelector.value = show
    }

    // whenever a color‐button blink phase arrives:
    private fun startCollectingBlinks() {
        // Cancel any existing collector
        blinkJob?.cancel()
        blinkJob = viewModelScope.launch {
            colorBlinkActive
                .filter { it && activeSound.value != 0 }
                .drop(1)
                .collect {
                    Log.d(TAG,"startCollectingBlinks() isAppInForeground: $isAppInForeground")
                    // Only fire when app is actually in the foreground
                    if (isAppInForeground) {
                        soundPool.playOnce(activeSound.value)
                    }
                }
        }
    }

    private fun stopCollectingBlinks() {
        Log.d(TAG,"stopCollectingBlinks()")
        blinkJob?.cancel()
        blinkJob = null
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event
    ) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                Log.d(TAG,"Lifecycle.Event.ON_START")
                // App has come to foreground
                isAppInForeground = true
                startCollectingBlinks()
            }
            Lifecycle.Event.ON_STOP -> {
                Log.d(TAG,"Lifecycle.Event.ON_STOP")
                // App going to background
                isAppInForeground = false
                // Immediately stop all sounds and cancel
                stopCollectingBlinks()
                soundPool.stopAll()
//                stopCollectingBlinks()

            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up the observer
        Log.d(TAG,"onCleared()")
        ProcessLifecycleOwner.Companion.get().lifecycle.removeObserver(this)

//        soundPool.stopAll()
    }

    //Ensures that this value n lies in the specified range
    fun setActiveSound(n: Int) {
        wearCtrl.setActiveSound(n.coerceIn(0, 3))
    }

    fun setVolume( context: Context? = null, v: Float,) {
        viewModelScope.launch {
            val newVolume = v.coerceIn(0f, 1f)
            soundPool.setVolumeBoth(context, newVolume)
            saveVolume(newVolume)
            Log.d(TAG,"volumeValue: $newVolume")
        }
    }

    /** Save the volume (suspend) */
    suspend fun saveVolume(v: Float) {
        dataStore.edit { prefs -> prefs[KEY_VOLUME] = v.coerceIn(0f, 1f)}
    }

    /** Read current persisted value once (suspend) */
    suspend fun readVolumeOnce(): Float =
       dataStore.data.map { prefs -> prefs[KEY_VOLUME] ?: DEFAULT_VOLUME }
            .first() // requires kotlinx.coroutines.flow.first

}