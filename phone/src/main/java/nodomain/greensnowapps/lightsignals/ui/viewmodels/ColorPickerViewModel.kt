package nodomain.greensnowapps.lightsignals.ui.viewmodels


import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

import androidx.lifecycle.ViewModel

// Create an extension property for DataStore
private val Context.dataStore by preferencesDataStore(name = "blink_preferences")

// Define keys for the DataStore
//private val PROGRESS_KEY = floatPreferencesKey("progress_color_picker")
//private val FREQUENCY_KEY = floatPreferencesKey("progress_frequency_picker")


class ColorPickerViewModel(context: Context) : ViewModel() {

//    // Use the application context.
//    private val appContext = context.applicationContext
//
//    // Create a Flow for progress value.
//    val progressColor: Flow<Float> = appContext.dataStore.data
//        .map { preferences ->
//            preferences[PROGRESS_KEY] ?: 0f    // Default value is 0f
//        }
//        // Convert the Flow to StateFlow if needed (or use directly in composable)
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
//
//    /**
//     * Update the progress in our DataStore.
//     */
//    fun updateColorProgress(newProgress: Float) {
//        viewModelScope.launch {
//            appContext.dataStore.edit { preferences ->
//                preferences[PROGRESS_KEY] = newProgress
//            }
//        }
//    }
//
//
//    /**
//     * Update the progress in our DataStore.
//     */
//    fun updateFrequencyProgress(newFrequencyProgress: Float) {
//        viewModelScope.launch {
//            appContext.dataStore.edit { preferences ->
//                preferences[FREQUENCY_KEY] = newFrequencyProgress
//            }
//        }
//    }
//
//    // Create a Flow for progress value.
//    val progressFrequency: Flow<Float> = appContext.dataStore.data
//        .map { preferences ->
//            preferences[FREQUENCY_KEY] ?: 0f    // Default value is 0f
//        }
//        // Convert the Flow to StateFlow if needed (or use directly in composable)
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
//
//    /**
//     * Save progress explicitly (for greensnowapps, on a change effect)
//     */
//    fun saveProgress(newProgress: Float) {
//        updateColorProgress(newProgress)
//    }
}
