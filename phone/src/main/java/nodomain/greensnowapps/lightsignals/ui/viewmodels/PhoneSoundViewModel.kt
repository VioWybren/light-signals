package nodomain.greensnowapps.lightsignals.ui.viewmodels

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.model.PhoneControlRepository
import nodomain.greensnowapps.lightsignals.model.PhoneSoundPoolPlayer
import javax.inject.Inject


/**
 * Routes phone sound selection and screen-signal phases to playback.
 *
 * Exposes [activeSound] and [colorBlinkActive] flows. Lifecycle events gate screen playback;
 * the torch service owns LED-associated sound. Main-thread events update the repository.
 *
 * @property flashCtrl Owner of the phone control and signal state.
 * @property soundPool Signal-sample player used for local playback.
 */
@HiltViewModel
class PhoneSoundViewModel @Inject constructor(
    private val flashCtrl: PhoneControlRepository,
    private val soundPool: PhoneSoundPoolPlayer
) : ViewModel(), LifecycleEventObserver {

    /**
     * Read-only lit/dark phase used to render and sonify timed screen signals.
     */
    val colorBlinkActive = flashCtrl.colorBlinkActive
    /**
     * Read-only selected sample number: 0 for silence, 1..3 for a signal sample.
     */
    val activeSound = flashCtrl.activeSound

    /**
     * ViewModel-scoped screen-phase collector, reused while active and cancelled when the
     * process stops.
     */
    private var blinkJob: Job? = null
    /**
     * Main-confined playback gate, initially true and updated by process ON_START and ON_STOP
     * events.
     */
    private var isAppInForeground = true

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        startCollectingBlinks()
    }

    /**
     * Starts a single screen-phase collector in viewModelScope.
     *
     * Call on Main. Reuses an active collector; otherwise replaces the previous job and
     * returns without waiting. Requests the current nonzero sample for collected lit phases
     * while isAppInForeground permits playback. Reads sound selection when the phase emits, so
     * a sound-selection change alone does not trigger a sample. Collection ends when cancelled
     * or when the ViewModel scope is cleared.
     */
    private fun startCollectingBlinks() {
        if (blinkJob?.isActive == true) {
            return
        }

        blinkJob?.cancel()
        blinkJob = viewModelScope.launch {
            colorBlinkActive
                .filter { it && activeSound.value != 0 }
                .collect {
                    if (isAppInForeground) {
                        soundPool.playOnce(activeSound.value)
                    }
                }
        }
    }

    /**
     * Requests cancellation of the screen-phase collector and clears its job handle on Main.
     *
     * Returns without waiting for coroutine completion. The lifecycle callback handles
     * stopping audio playback separately.
     */
    private fun stopCollectingBlinks() {
        blinkJob?.cancel()
        blinkJob = null
    }

    /**
     * Updates screen-signal playback for process lifecycle transitions on Main.
     *
     * ProcessLifecycleOwner supplies these events. ON_START permits playback and starts phase
     * collection. ON_STOP clears foreground eligibility, stops the player's active stream and
     * pending sample requests, and cancels collection. Other events leave playback eligibility
     * unchanged.
     *
     * @param source Lifecycle owner dispatching the event; this observer does not read the
     *     argument.
     * @param event Lifecycle transition used to start or stop screen-phase playback.
     */
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
                soundPool.stopAll()
                stopCollectingBlinks()
            }
            else -> {}
        }
    }

    /**
     * Removes this ViewModel from the process lifecycle on Main.
     *
     * Collector cancellation follows viewModelScope cleanup. This override releases the
     * observer registration; playback stopping is handled by the ON_STOP branch of
     * [onStateChanged].
     */
    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.Companion.get().lifecycle.removeObserver(this)
    }

    /**
     * Updates the selected sample through the phone control repository on Main.
     *
     * Clamps the requested number to 0..3 and delegates persistence to the repository. The
     * phase collector reads the new selection when the signal phase next emits.
     *
     * @param n Requested sample number: 0 selects silence and 1..3 select signal samples.
     */
    fun setActiveSound(n: Int) {
        flashCtrl.setActiveSound(n.coerceIn(0, 3))
    }
}