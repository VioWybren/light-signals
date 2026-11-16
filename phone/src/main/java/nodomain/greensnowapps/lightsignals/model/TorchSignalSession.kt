package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Keeps timed torch signals awake until cancellation-safe hardware cleanup finishes.
 *
 * Runs the supplied signal in its caller's coroutine and performs final cleanup without
 * cancellation.
 *
 * @property acquireWakeLock Acquires the partial wake lock for a timed signal.
 * @property releaseWakeLock Releases the partial wake lock after torch cleanup.
 * @property switchOffTorch Suspending callback that performs the final torch-off write.
 */
internal class TorchSignalSession(
    private val acquireWakeLock: () -> Unit,
    private val releaseWakeLock: () -> Unit,
    private val switchOffTorch: suspend () -> Unit
) {
    /**
     * Runs the supplied signal and attempts final torch-off before releasing any acquired CPU
     * lock.
     *
     * Suspends in the caller's context. Timed sessions acquire the lock before signaling.
     * Cleanup runs in NonCancellable, and lock release still runs if torch cleanup throws.
     * Cancellation and signal or cleanup failures propagate to the caller.
     *
     * @param keepCpuAwake Whether the signal requires a partial wake lock.
     * @param signal Suspending signal operation executed inside the wake-lock and cleanup
     *     lifetime.
     */
    suspend fun run(keepCpuAwake: Boolean, signal: suspend () -> Unit) {
        try {
            if (keepCpuAwake) acquireWakeLock()
            signal()
        } finally {
            withContext(NonCancellable) {
                try {
                    switchOffTorch()
                } finally {
                    if (keepCpuAwake) releaseWakeLock()
                }
            }
        }
    }
}
