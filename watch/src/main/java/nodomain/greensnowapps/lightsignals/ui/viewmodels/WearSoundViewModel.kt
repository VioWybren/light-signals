package nodomain.greensnowapps.lightsignals.ui.viewmodels

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.WearConstants.DEFAULT_VOLUME
import nodomain.greensnowapps.lightsignals.model.KEY_VOLUME
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.model.WearSoundPoolPlayer
import nodomain.greensnowapps.lightsignals.model.editSafely
import nodomain.greensnowapps.lightsignals.model.safeData
import javax.inject.Inject

@HiltViewModel
class WearSoundViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val wearCtrl: WearControlRepository,
    private val soundPool: WearSoundPoolPlayer
) : ViewModel() {
    val activeSound = wearCtrl.activeSound

    val volumeFlow = dataStore.safeData()
        .map { prefs -> safeVolume(prefs[KEY_VOLUME] ?: DEFAULT_VOLUME) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_VOLUME)

    private val _showSoundSelector = MutableStateFlow(false)
    val showSoundSelector = _showSoundSelector.asStateFlow()

    init {
        viewModelScope.launch {
            wearCtrl.colorBlinkActive.filter { it }.collect {
                if (wearCtrl.canPlayLocalSound) soundPool.playOnce(activeSound.value)
            }
        }
    }

    fun setShowSoundSelector(show: Boolean) {
        _showSoundSelector.value = show
    }

    fun setActiveSound(n: Int) = wearCtrl.setActiveSound(n)

    fun setVolume(v: Float) {
        val volume = safeVolume(v)
        soundPool.setVolume(volume)
        viewModelScope.launch {
            dataStore.editSafely { prefs -> prefs[KEY_VOLUME] = volume }
        }
    }

    override fun onCleared() {
        soundPool.stopAll()
        super.onCleared()
    }

    private fun safeVolume(value: Float) =
        value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: DEFAULT_VOLUME
}
