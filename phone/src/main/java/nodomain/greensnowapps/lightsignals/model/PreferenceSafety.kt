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

