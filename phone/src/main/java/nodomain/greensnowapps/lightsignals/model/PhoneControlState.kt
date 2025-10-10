package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first

data class PhoneControlState(
    val blinkEnabled: Boolean = false,
    val sosEnabled: Boolean = false,
    val torchOn: Boolean = false,
    val frequency: Long = 500L,
    val activeSound: Int = 0,
    val color: Float = 0xFFFFFFFF.toFloat(),
    val screenIndex: Int = 0,
    val activeColorButton: Int = 0
) {
    companion object {
        suspend fun fromDataStore(dataStore: DataStore<Preferences>): PhoneControlState {
            val prefs = dataStore.data.first()
            val initialSosEnabled: Boolean
            val initialBlinkEnabled: Boolean
            val initialTorch = prefs[KEY_FLASH_ON] ?: false
            if (initialTorch) {
                initialBlinkEnabled = false
                initialSosEnabled = false
            } else {
                initialBlinkEnabled = prefs[KEY_BLINK_ON] ?: false
                initialSosEnabled = prefs[KEY_SOS_ON] ?: false
            }

            return PhoneControlState(
                blinkEnabled = initialBlinkEnabled,
                sosEnabled = initialSosEnabled,
                frequency = prefs[KEY_FREQUENCY_PROGRESS]?.toLong() ?: 500L,
                color = prefs[KEY_COLOR_PROGRESS] ?: 0xFFFFFFFF.toFloat(),
                torchOn = false,
                activeSound = prefs[KEY_ACTIVE_SOUND] ?: 0,
                screenIndex = prefs[KEY_ACTIVE_SCREEN] ?: 0,
                activeColorButton = prefs[KEY_PREF_ACTIVE_COLOR_BUTTON] ?: 0
            )
        }
    }
}