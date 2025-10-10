package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.content.Context
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
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.model.WearSoundPoolPlayer
import javax.inject.Inject


@HiltViewModel
class WearSoundViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val wearCtrl: WearControlRepository,
    private val soundPool: WearSoundPoolPlayer
) : ViewModel(), LifecycleEventObserver {

    val colorBlinkActive = wearCtrl.colorBlinkActive
    val activeSound = wearCtrl.activeSound

    private val initialVolume: Float = runBlocking(Dispatchers.IO) {
        dataStore.data
            .map { prefs -> prefs[KEY_VOLUME] ?: DEFAULT_VOLUME }
            .first()
    }

    val volumeFlow: StateFlow<Float> = dataStore.data
        .drop(1)
        .map { prefs -> prefs[KEY_VOLUME] ?: initialVolume }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialVolume)


    private var blinkJob: Job? = null
    private var isAppInForeground = true

    init {
        ProcessLifecycleOwner.Companion.get().lifecycle.addObserver(this)
        startCollectingBlinks()
    }

    private val _showSoundSelector = MutableStateFlow(false)
    val showSoundSelector: StateFlow<Boolean> = _showSoundSelector.asStateFlow()
    fun setShowSoundSelector(show: Boolean) {
        _showSoundSelector.value = show
    }

    private fun startCollectingBlinks() {
        blinkJob?.cancel()
        blinkJob = viewModelScope.launch {
            colorBlinkActive
                .filter { it && activeSound.value != 0 }
                .drop(1)
                .collect {
                    if (isAppInForeground) {
                        soundPool.playOnce(activeSound.value)
                    }
                }
        }
    }

    private fun stopCollectingBlinks() {
        blinkJob?.cancel()
        blinkJob = null
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event
    ) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                isAppInForeground = true
                startCollectingBlinks()
            }
            Lifecycle.Event.ON_STOP -> {
                isAppInForeground = false
                stopCollectingBlinks()
                soundPool.stopAll()
            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.Companion.get().lifecycle.removeObserver(this)
    }

    fun setActiveSound(n: Int) {
        wearCtrl.setActiveSound(n.coerceIn(0, 3))
    }

    fun setVolume(context: Context? = null, v: Float) {
        viewModelScope.launch {
            val newVolume = v.coerceIn(0f, 1f)
            soundPool.setVolumeBoth(context, newVolume)
            saveVolume(newVolume)
        }
    }

    suspend fun saveVolume(v: Float) {
        dataStore.edit { prefs -> prefs[KEY_VOLUME] = v.coerceIn(0f, 1f) }
    }

}