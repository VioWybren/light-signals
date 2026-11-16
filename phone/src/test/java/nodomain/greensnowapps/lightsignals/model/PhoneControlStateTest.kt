package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies phone preference restoration and bounds validation.
 */
class PhoneControlStateTest {
    /**
     * Verifies that empty preferences restore safe defaults.
     */
    @Test fun emptyPreferencesRestoreSafeDefaults() {
        assertEquals(PhoneControlState(), PhoneControlState.fromPreferences(emptyPreferences()))
    }

    /**
     * Verifies that previous torch selection never restarts hardware or its saved modes.
     */
    @Test fun previousTorchSelectionNeverRestartsHardwareOrItsSavedModes() {
        val state = PhoneControlState.fromPreferences(preferencesOf(
            KEY_FLASH_ON to true, KEY_BLINK_ON to true, KEY_SOS_ON to true,
            KEY_ACTIVE_SOUND to 2, KEY_ACTIVE_SCREEN to 1
        ))
        assertFalse(state.torchOn)
        assertFalse(state.blinkEnabled)
        assertFalse(state.sosEnabled)
        assertEquals(2, state.activeSound)
        assertEquals(1, state.screenIndex)
    }

    /**
     * Verifies that current frequency preference takes precedence over legacy delay.
     */
    @Test fun currentFrequencyPreferenceTakesPrecedenceOverLegacyDelay() {
        val state = PhoneControlState.fromPreferences(preferencesOf(
            KEY_FREQUENCY_PROGRESS to 725f, KEY_BLINK_DELAY to 300L
        ))
        assertEquals(725L, state.frequency)
    }

    /**
     * Verifies that legacy delay remains readable.
     */
    @Test fun legacyDelayRemainsReadable() {
        assertEquals(350L, PhoneControlState.fromPreferences(preferencesOf(KEY_BLINK_DELAY to 350L)).frequency)
    }

    /**
     * Verifies that non finite saved frequency falls back to legacy value.
     */
    @Test fun nonFiniteSavedFrequencyFallsBackToLegacyValue() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val state = PhoneControlState.fromPreferences(preferencesOf(
                KEY_FREQUENCY_PROGRESS to value, KEY_BLINK_DELAY to 350L
            ))
            assertEquals("Invalid frequency $value", 350L, state.frequency)
        }
    }

    /**
     * Verifies that non finite frequency without a legacy value uses the default.
     */
    @Test fun nonFiniteFrequencyWithoutALegacyValueUsesTheDefault() {
        val state = PhoneControlState.fromPreferences(preferencesOf(KEY_FREQUENCY_PROGRESS to Float.NaN))
        assertEquals(500L, state.frequency)
    }

    /**
     * Verifies that invalid numbers and indices are clamped before use.
     */
    @Test fun invalidNumbersAndIndicesAreClampedBeforeUse() {
        assertEquals(
            PhoneControlState(frequency = 100L, activeSound = 3, color = DEFAULT_COLOR_PROGRESS,
                screenIndex = 0, activeColorButton = 3),
            PhoneControlState(frequency = Long.MIN_VALUE, activeSound = Int.MAX_VALUE, color = Float.NaN,
                screenIndex = -1, activeColorButton = 100).validated()
        )
        val upper = PhoneControlState(frequency = Long.MAX_VALUE, activeSound = -1, color = 9000f,
            screenIndex = 99, activeColorButton = -1).validated()
        assertEquals(1000L, upper.frequency)
        assertEquals(0, upper.activeSound)
        assertEquals(4591f, upper.color, 0f)
        assertEquals(1, upper.screenIndex)
        assertEquals(0, upper.activeColorButton)
    }

    /**
     * Verifies that valid state is unchanged by repeated validation.
     */
    @Test fun validStateIsUnchangedByRepeatedValidation() {
        val state = PhoneControlState(true, false, true, 275L, 2, 621.5f, 1, 1)
        assertEquals(state, state.validated().validated())
    }

    /**
     * Verifies that saved extended colors from both encodings restore unchanged.
     */
    @Test fun savedExtendedColorsFromBothEncodingsRestoreUnchanged() {
        for (stored in listOf(2040f, 2295f, 2296f, 2806f, 4591f)) {
            val state = PhoneControlState.fromPreferences(preferencesOf(KEY_COLOR_PROGRESS to stored))
            assertEquals(stored, state.color, 0f)
        }
    }
}
