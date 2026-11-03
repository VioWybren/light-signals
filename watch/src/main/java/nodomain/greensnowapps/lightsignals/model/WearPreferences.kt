package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import java.io.IOException

internal fun DataStore<Preferences>.safeData(
    onError: (IOException) -> Unit = { Log.w("WearPreferences", "Unable to read settings; using defaults", it) }
) = data.catch { error ->
    if (error !is IOException) throw error
    onError(error)
    emit(emptyPreferences())
}

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
