package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

data class PhoneControlState(
    val blinkEnabled: Boolean = false,
    val sosEnabled: Boolean = false,
    val torchOn: Boolean = false,
    val frequency: Long = 500L,
    val activeSound: Int = 0,
    val color: Float = DEFAULT_COLOR_PROGRESS,
    val screenIndex: Int = 0,
    val activeColorButton: Int = 0
) {
    internal fun validated(): PhoneControlState = copy(
        frequency = frequency.coerceIn(100L, 1000L),
        activeSound = activeSound.coerceIn(0, 3),
        color = SignalColorPalette.clampProgress(color),
        screenIndex = screenIndex.coerceIn(0, 1),
        activeColorButton = activeColorButton.coerceIn(0, 3)
    )

    companion object {
        suspend fun fromDataStore(dataStore: DataStore<Preferences>): PhoneControlState =
            fromPreferences(dataStore.safePreferences().first())

        internal fun fromPreferences(prefs: Preferences): PhoneControlState {
            val previousTorch = prefs[KEY_FLASH_ON] ?: false
            return PhoneControlState(
                blinkEnabled = !previousTorch && (prefs[KEY_BLINK_ON] ?: false),
                sosEnabled = !previousTorch && (prefs[KEY_SOS_ON] ?: false),
                frequency = prefs[KEY_FREQUENCY_PROGRESS]?.takeIf { it.isFinite() }?.toLong()
                    ?: prefs[KEY_BLINK_DELAY] ?: 500L,
                color = prefs[KEY_COLOR_PROGRESS] ?: DEFAULT_COLOR_PROGRESS,
                torchOn = false,
                activeSound = prefs[KEY_ACTIVE_SOUND] ?: 0,
                screenIndex = prefs[KEY_ACTIVE_SCREEN] ?: 0,
                activeColorButton = prefs[KEY_PREF_ACTIVE_COLOR_BUTTON] ?: 0
            ).validated()
        }
    }
}
