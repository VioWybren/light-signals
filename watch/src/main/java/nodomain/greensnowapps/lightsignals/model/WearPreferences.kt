package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import java.io.IOException

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
internal fun DataStore<Preferences>.safeData(
    onError: (IOException) -> Unit = { Log.w("WearPreferences", "Unable to read settings; using defaults", it) }
) = data.catch { error ->
    if (error !is IOException) throw error
    onError(error)
    emit(emptyPreferences())
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
    onError: (IOException) -> Unit = { Log.w("WearPreferences", "Unable to save settings", it) },
    transform: suspend (MutablePreferences) -> Unit
) {
    try {
        edit(transform)
    } catch (error: IOException) {
        onError(error)
    }
}
