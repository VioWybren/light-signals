package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

/**
 * Snapshots the phone controls restored from preferences or received through the legacy Data
 * Layer.
 *
 * @property blinkEnabled Whether the Blink control is enabled.
 * @property sosEnabled Whether the SOS control is enabled.
 * @property torchOn Whether the phone LED is selected; false selects screen output.
 * @property frequency Blink half-period in milliseconds, constrained to 100..1000 by
 *     receivers.
 * @property activeSound Selected sample number: 0 disables sound; 1..3 select signal samples.
 * @property color Encoded shared palette position used by the custom-colour control.
 * @property screenIndex Selected pager screen: 0 for controls, 1 for the signal display.
 * @property activeColorButton Selected colour/output ordinal: red, custom, green or torch.
 */
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
    /**
     * Bounds persisted or received control values before applying them to the phone UI.
     *
     * @return A copy with valid timing, colour, sound, pager and selection values.
     */
    internal fun validated(): PhoneControlState = copy(
        frequency = frequency.coerceIn(100L, 1000L),
        activeSound = activeSound.coerceIn(0, 3),
        color = SignalColorPalette.clampProgress(color),
        screenIndex = screenIndex.coerceIn(0, 1),
        activeColorButton = activeColorButton.coerceIn(0, 3)
    )

    /**
     * Restores bounded phone controls from preference snapshots or DataStore.
     */
    companion object {
        /**
         * Suspends until one preferences snapshot is available, then decodes safe phone
         * controls.
         *
         * @param dataStore Preferences store used to read and persist settings.
         * @return The restored control state with torch output disabled.
         */
        suspend fun fromDataStore(dataStore: DataStore<Preferences>): PhoneControlState =
            fromPreferences(dataStore.safePreferences().first())

        /**
         * Decodes and bounds saved phone controls without reviving an earlier torch session.
         *
         * Always restores torch off. If the saved torch flag was on, also suppresses its saved
         * Blink and SOS selections; otherwise retains screen-mode preferences. Finite
         * frequency-slider values take precedence over the legacy delay key.
         *
         * @param prefs Preferences snapshot used by this operation.
         * @return The bounded restored phone state.
         */
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
