package nodomain.greensnowapps.lightsignals.core.signal

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Duration of one SOS Morse unit in milliseconds.
 */
const val SOS_UNIT_MS = 200L

/**
 * Repeats the SOS pulse pattern using suspending delays in the caller's coroutine.
 *
 * Stops on cancellation or a failed on-phase write. The owning session must perform final
 * output-off cleanup; this engine does not force a dispatcher or acquire Android resources.
 *
 * @param unitMillis Positive duration of one Morse unit in milliseconds, supplied by the caller.
 * @param writePhase Suspending output callback; true switches on, false switches off. Returning
 *     false on an on-phase stops repetition.
 */
suspend fun runSosSignal(
    unitMillis: Long,
    writePhase: suspend (Boolean) -> Boolean
) {
    /**
     * Suspends through one on-phase and one Morse-unit off gap in the caller's coroutine.
     *
     * Returns immediately if the on-write fails. A false off-write does not stop this pulse; the
     * enclosing session still owns final output-off cleanup. Cancellation propagates through
     * phase writes and delays.
     *
     * @param duration On-phase duration in milliseconds; the enclosing pattern uses one or three
     *     units.
     * @return False if the on-write fails, or true after the on interval and off gap finish.
     */
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
