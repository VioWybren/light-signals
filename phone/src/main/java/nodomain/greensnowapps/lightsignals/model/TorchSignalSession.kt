package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Keeps timed LED signals awake until their cancellation-safe hardware cleanup finishes. */
internal class TorchSignalSession(
    private val acquireWakeLock: () -> Unit,
    private val releaseWakeLock: () -> Unit,
    private val switchOffTorch: suspend () -> Unit
) {
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
