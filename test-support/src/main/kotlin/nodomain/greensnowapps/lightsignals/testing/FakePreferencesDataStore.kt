package nodomain.greensnowapps.lightsignals.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provides controllable in-memory preferences for deterministic repository tests.
 *
 * Tests can suspend reads, inject failures and inspect committed snapshots without Android
 * storage.
 *
 * @param initial Initial preferences supplied to the test store.
 */
class FakePreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    /**
     * Current in-memory preferences exposed through the controllable test data flow.
     */
    private val values = MutableStateFlow(initial)
    /**
     * Serializes fake-store transformations and recorded commits during concurrent coroutine
     * tests.
     */
    private val mutex = Mutex()
    /**
     * Optional gate that suspends test-store reads until completed.
     */
    var readGate: Deferred<Unit>? = null
    /**
     * Optional failure raised when the test preferences flow is collected.
     */
    var readFailure: Throwable? = null
    /**
     * Optional failure consumed by the next test-store write.
     */
    var nextWriteFailure: Throwable? = null
    /**
     * Committed preference snapshots recorded in write order.
     */
    val writes = mutableListOf<Preferences>()
    /**
     * Current in-memory preferences snapshot.
     */
    val snapshot: Preferences get() = values.value

    /**
     * Preferences flow with controllable read gating and failure injection.
     */
    override val data = flow {
        readGate?.await()
        readFailure?.let { throw it }
        emitAll(values)
    }

    /**
     * Suspends while applying a serialized test-store transformation.
     *
     * Consumes and throws nextWriteFailure before applying the transformation, when
     * configured.
     *
     * @param transform Suspending transformation applied atomically to the stored preferences.
     * @return The committed preferences snapshot.
     */
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            nextWriteFailure?.let {
                nextWriteFailure = null
                throw it
            }
            transform(values.value).also {
                values.value = it
                writes += it
            }
        }
}
