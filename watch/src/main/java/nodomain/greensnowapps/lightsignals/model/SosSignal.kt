package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Repeats SOS. The owning session is responsible for switching off on cancellation. */
internal suspend fun runSosSignal(
    unitMillis: Long,
    writePhase: suspend (Boolean) -> Boolean
) {
    suspend fun pulse(duration: Long): Boolean {
        if (!writePhase(true)) return false
        delay(duration)
        writePhase(false)
        delay(unitMillis)
        return true
    }
    while (currentCoroutineContext().isActive) {
        repeat(3) { if (!pulse(unitMillis)) return }
        delay(2 * unitMillis)
        repeat(3) { if (!pulse(3 * unitMillis)) return }
        delay(2 * unitMillis)
        repeat(3) { if (!pulse(unitMillis)) return }
        delay(6 * unitMillis)
    }
}
