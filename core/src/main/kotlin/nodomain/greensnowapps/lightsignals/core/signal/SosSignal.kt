package nodomain.greensnowapps.lightsignals.core.signal

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

const val SOS_UNIT_MS = 200L

/** Repeats SOS. The owning session is responsible for switching off on cancellation. */
suspend fun runSosSignal(
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
