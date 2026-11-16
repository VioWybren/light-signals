package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes hardware ownership and writes across overlapping service instances.
 *
 * Suspend operations switch to dispatcher and hold a mutex; stale owners cannot switch off a
 * newer signal.
 *
 * @property writeTorch Hardware write callback; returns false when the requested torch write
 *     fails.
 * @property dispatcher Dispatcher for serialized hardware operations; IO by default.
 */
internal class TorchSessionController(
    private val writeTorch: (Boolean) -> Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Serializes owner replacement, physical torch writes and final release on the configured
     * dispatcher.
     */
    private val mutex = Mutex()
    /**
     * Current hardware-owner token, or null when released; read and written only while holding
     * mutex.
     */
    private var owner: Any? = null

    /**
     * Suspends while assigning hardware ownership under the mutex on the configured
     * dispatcher.
     *
     * The production dispatcher is IO.
     *
     * @param session Identity token or record for the session being changed.
     */
    suspend fun acquire(session: Any) = withContext(dispatcher) {
        mutex.withLock { owner = session }
    }

    /**
     * Suspends while serializing an ownership-checked write on the configured dispatcher.
     *
     * The production dispatcher is IO.
     *
     * @param session Identity token or record for the session being changed.
     * @param enabled Whether to enable the requested output or mode.
     * @return True on success or for a superseded owner; false when the current owner's write
     *     fails.
     */
    suspend fun setEnabled(session: Any, enabled: Boolean): Boolean = withContext(dispatcher) {
        mutex.withLock {
            // Superseded services must neither touch hardware nor report hardware failure.
            if (owner !== session) true else writeTorch(enabled)
        }
    }

    /**
     * Attempts an off-write and clears matching ownership under the dispatcher-bound mutex.
     *
     * Suspends on the configured dispatcher, IO in production. Superseded owners do nothing. A
     * false hardware result still clears ownership; a thrown exception propagates before that
     * assignment.
     *
     * @param session Owner token whose matching session should be released.
     */
    suspend fun release(session: Any) = withContext(dispatcher) {
        mutex.withLock {
            if (owner === session) {
                writeTorch(false)
                owner = null
            }
        }
    }
}

