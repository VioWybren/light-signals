package nodomain.greensnowapps.lightsignals.model

import com.google.android.gms.wearable.DataMap
import nodomain.greensnowapps.lightsignals.testing.ContractFixtures
import org.junit.Assert.*
import org.junit.Test

class PhoneControlStateContractTest {
    @Test fun productionWatchFixtureDecodesWithAllFieldsIntact() {
        val fixture = ContractFixtures.wireState("control-state-v1.properties")
        assertEquals(fixture.path, CONTROL_STATE_PATH)
        assertEquals(PhoneControlState(true, false, true, 275L, 2, 621.5f, 1, 1),
            decodeControlState(fixture.toDataMap()))
    }

    @Test fun olderWatchPayloadUsesDefaultsForMissingOptionalFields() {
        val fixture = ContractFixtures.wireState("control-state-legacy-v1.properties")
        assertEquals(fixture.path, CONTROL_STATE_PATH)
        assertEquals(PhoneControlState(sosEnabled = true, frequency = 800L),
            decodeControlState(fixture.toDataMap()))
    }

    @Test fun emptyPayloadUsesSafeDefaults() {
        assertEquals(PhoneControlState(), decodeControlState(DataMap()))
    }

    @Test fun futureFieldsAndDeliveryTimestampDoNotAlterControlState() {
        val payload = ContractFixtures.wireState("control-state-v1.properties").toDataMap()
        val expected = decodeControlState(payload)
        payload.putString("futureSetting", "unknown")
        payload.putLong("sentAt", Long.MAX_VALUE)
        assertEquals(expected, decodeControlState(payload))
    }

    @Test fun invalidRemoteValuesAreValidatedAtTheDecodeBoundary() {
        val payload = DataMap().apply {
            putLong("frequency", -10L)
            putFloat("color", Float.POSITIVE_INFINITY)
            putInt("activeSound", 999)
            putInt("screenIndex", -1)
            putInt("key_active_color_button", 99)
        }
        assertEquals(PhoneControlState(frequency = 100L, activeSound = 3, activeColorButton = 3),
            decodeControlState(payload))
    }

    @Test fun supportedExtendedColorsTakePrecedenceOverTheLegacyFallback() {
        for ((fallback, extended) in listOf(1467f to 2040f, 1275f to 2295f, 0f to 2296f, 1467f to 2806f, 1785f to 4591f)) {
            val payload = DataMap().apply {
                putFloat("color", fallback)
                putFloat("extendedColorProgress", extended)
                putInt("key_active_color_button", 1)
            }
            val state = decodeControlState(payload)
            assertEquals(extended, state.color, 0f)
            assertEquals(1, state.activeColorButton)
        }
    }

    @Test fun unsupportedExtendedColorsPreserveTheLegacyFallback() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, 1785f, 2295.5f, 4592f)) {
            val payload = DataMap().apply {
                putFloat("color", 621.5f)
                putFloat("extendedColorProgress", invalid)
            }
            assertEquals(621.5f, decodeControlState(payload).color, 0f)
        }
    }
}
