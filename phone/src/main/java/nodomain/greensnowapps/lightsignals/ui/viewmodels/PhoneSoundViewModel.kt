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
        ProcessLifecycleOwner.Companion.get().lifecycle.addObserver(this)
        startCollectingBlinks()
    }

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
                soundPool.stopAll()
                stopCollectingBlinks()
            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.Companion.get().lifecycle.removeObserver(this)
    }

    fun setActiveSound(n: Int) {
        flashCtrl.setActiveSound(n.coerceIn(0, 3))
    }
}