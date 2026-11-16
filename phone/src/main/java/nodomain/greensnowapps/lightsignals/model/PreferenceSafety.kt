package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Returns a preferences flow that reports IO failures and emits empty preferences as recovery.
 *
 * Collection suspends on the caller's coroutine and preserves cancellation and non-IO
 * failures.
 *
 * @param onError Receives handled preference IO failures.
 * @receiver Preferences DataStore used by this operation.
 * @return The recoverable preferences flow.
 */
internal fun DataStore<Preferences>.safePreferences(
    onError: (IOException) -> Unit = { Log.w("Preferences", "Unable to read saved settings", it) }
): Flow<Preferences> =
    data.catch { error ->
        if (error is IOException) {
            onError(error)
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

/**
 * Suspends while atomically editing preferences and reports handled IO failures.
 *
 * Preserves cancellation and propagates non-IO failures; DataStore owns storage dispatching.
 *
 * @param onError Receives handled preference IO failures.
 * @param transform Suspending transformation applied atomically to the stored preferences.
 * @receiver Preferences DataStore used by this operation.
 */
internal suspend fun DataStore<Preferences>.editSafely(
    onError: (IOException) -> Unit = { Log.w("Preferences", "Unable to save settings", it) },
    transform: suspend (MutablePreferences) -> Unit
) {
    try {
        edit(transform)
    } catch (error: IOException) {
        onError(error)
    }
}

