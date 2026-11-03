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
}
