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

/**
 * Owns the watch sound-selection screen, persisted volume and local signal playback.
 *
 * Exposes [activeSound], [volumeFlow] and [showSoundSelector] StateFlows. Main-thread events
 * change sound, selector visibility and volume; viewModelScope owns collection and
 * persistence. Playback follows foreground and remote-routing eligibility from the repository.
 *
 * @property dataStore Preferences store used to read and persist settings.
 * @property wearCtrl Owner of the watch signal and companion-link state.
 * @property soundPool Signal-sample player used for local playback.
 */
@HiltViewModel
class WearSoundViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val wearCtrl: WearControlRepository,
    private val soundPool: WearSoundPoolPlayer
) : ViewModel() {
    /**
     * Read-only selected sample number: 0 for silence, 1..3 for a signal sample.
     */
    val activeSound = wearCtrl.activeSound

    /**
     * Eager StateFlow of the bounded saved media-volume fraction used by the sound slider.
     *
     * Owned by viewModelScope. Represents the stored selection, not external volume changes made
     * through system controls or another app.
     */
    val volumeFlow = dataStore.safeData()
        .map { prefs -> safeVolume(prefs[KEY_VOLUME] ?: DEFAULT_VOLUME) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_VOLUME)

    /**
     * Main-confined visibility state for the watch sound-selection screen, initially hidden.
     */
    private val _showSoundSelector = MutableStateFlow(false)
    /**
     * Read-only visibility state for the watch sound-selection screen.
     */
    val showSoundSelector = _showSoundSelector.asStateFlow()

    init {
        viewModelScope.launch {
            wearCtrl.colorBlinkActive.filter { it }.collect {
                if (wearCtrl.canPlayLocalSound) soundPool.playOnce(activeSound.value)
            }
        }
    }

    /**
     * Updates watch sound-selector visibility on Main.
     *
     * @param show Whether the sound selector should be visible.
     */
    fun setShowSoundSelector(show: Boolean) {
        _showSoundSelector.value = show
    }

    /**
     * Selects sound through the watch repository on Main, including its offline playback policy.
     *
     * The repository bounds the value, routes linked playback to the phone and allows an
     * explicit offline choice to re-enable local watch sound.
     *
     * @param n Selected sample number: 0 for silence, 1..3 for a signal sound.
     */
    fun setActiveSound(n: Int) = wearCtrl.setActiveSound(n)

    /**
     * Applies a bounded media-volume selection and launches its preference write on Main.
     *
     * Normalizes through safeVolume, asks the player to update the shared media stream and
     * persists the selected fraction in viewModelScope. The saved selection can remain even if
     * the player logs a platform adjustment failure; clearing the ViewModel cancels an
     * unfinished preference-write job.
     *
     * @param v Requested media-volume fraction in 0..1.
     */
    fun setVolume(v: Float) {
        val volume = safeVolume(v)
        soundPool.setVolume(volume)
        viewModelScope.launch {
            dataStore.editSafely { prefs -> prefs[KEY_VOLUME] = volume }
        }
    }

    /**
     * Stops active and pending watch sample playback when this ViewModel is cleared on Main.
     *
     * ViewModel scope cleanup cancels its phase collector and preference jobs. The
     * application-owned sound pool remains available for later use.
     */
    override fun onCleared() {
        soundPool.stopAll()
        super.onCleared()
    }

    /**
     * Bounds a finite volume fraction and substitutes DEFAULT_VOLUME for non-finite input.
     *
     * @param value Requested or stored media-volume fraction.
     * @return A finite fraction in 0..1.
     */
    private fun safeVolume(value: Float) =
        value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: DEFAULT_VOLUME
}
