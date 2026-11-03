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

/** An atomic in-memory store with controllable read and write failures. */
class FakePreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val values = MutableStateFlow(initial)
    private val mutex = Mutex()
    var readGate: Deferred<Unit>? = null
    var readFailure: Throwable? = null
    var nextWriteFailure: Throwable? = null
    val writes = mutableListOf<Preferences>()
    val snapshot: Preferences get() = values.value

    override val data = flow {
        readGate?.await()
        readFailure?.let { throw it }
        emitAll(values)
    }

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
