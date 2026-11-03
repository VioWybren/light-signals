package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes hardware across service instances, including delayed destruction. */
internal class TorchSessionController(
    private val writeTorch: (Boolean) -> Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private var owner: Any? = null

    suspend fun acquire(session: Any) = withContext(dispatcher) {
        mutex.withLock { owner = session }
    }

    suspend fun setEnabled(session: Any, enabled: Boolean): Boolean = withContext(dispatcher) {
        mutex.withLock {
            // Superseded services must neither touch hardware nor report hardware failure.
            if (owner !== session) true else writeTorch(enabled)
        }
    }

    suspend fun release(session: Any) = withContext(dispatcher) {
        mutex.withLock {
            if (owner === session) {
                writeTorch(false)
                owner = null
            }
        }
    }
}

