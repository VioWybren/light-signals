package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.util.Log
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

private const val TAG = "PhoneSoundViewModel"

@HiltViewModel
class PhoneSoundViewModel @Inject constructor(
    private val flashCtrl: PhoneControlRepository,
    private val soundPool: PhoneSoundPoolPlayer
) : ViewModel(), LifecycleEventObserver {

    val colorBlinkActive = flashCtrl.colorBlinkActive
    val activeSound = flashCtrl.activeSound

    private var blinkJob: Job? = null
    private var isAppInForeground = true

    init {
        // Listen for app‐level lifecycle events
        ProcessLifecycleOwner.Companion.get().lifecycle.addObserver(this)
        startCollectingBlinks()
    }

    // whenever a color‐button blink phase arrives:
    private fun startCollectingBlinks() {
        // idempotent: if already collecting, do nothing
        if (blinkJob?.isActive == true) {
            Log.d(TAG, "Already collecting — skipping start")
            return
        }

        // Cancel any existing collector
        blinkJob?.cancel()
        blinkJob = viewModelScope.launch {
              colorBlinkActive
                .filter { it && activeSound.value != 0 }
                .collect {
                    Log.d(TAG, "startCollectingBlinks() isAppInForeground: $isAppInForeground")
                    // Only fire when app is actually in the foreground
                    if (isAppInForeground) {
                        soundPool.playOnce(activeSound.value)
                    }
                }
        }
    }

    private fun stopCollectingBlinks() {
        Log.d(TAG, "stopCollectingBlinks()")
        blinkJob?.cancel()
        blinkJob = null
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event
    ) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                Log.d(TAG, "Lifecycle.Event.ON_START")
                // App has come to foreground
                isAppInForeground = true
                startCollectingBlinks()
            }
            Lifecycle.Event.ON_STOP -> {
                Log.d(TAG, "Lifecycle.Event.ON_STOP")
                // App going to background
                isAppInForeground = false
                // Immediately stop all sounds and cancel
//                stopCollectingBlinks()
                soundPool.stopAll()
                stopCollectingBlinks()
            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up the observer
        Log.d(TAG, "onCleared()")
        ProcessLifecycleOwner.Companion.get().lifecycle.removeObserver(this)

//        soundPool.stopAll()
    }

    //Ensures that this value n lies in the specified range
    fun setActiveSound(n: Int) {
        flashCtrl.setActiveSound(n.coerceIn(0, 3))
    }
}