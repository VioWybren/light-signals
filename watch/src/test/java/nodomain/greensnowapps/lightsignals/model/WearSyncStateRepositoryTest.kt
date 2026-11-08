@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import com.google.android.gms.wearable.DataMap
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import nodomain.greensnowapps.lightsignals.testing.ContractFixtures
import org.junit.Assert.*
import org.junit.Test

class WearSyncStateRepositoryTest {
    @Test fun senderUsesTheSharedWirePathTypesAndValues() = runTest {
        val fixture = ContractFixtures.wireState("control-state-v1.properties")
        val sent = mutableListOf<Pair<String, DataMap>>()
        val repo = WearSyncStateRepository({ path, values -> sent += path to values },
            { 1_700_000_000_000L }, { throw AssertionError(it) })
        assertTrue(repo.sendState(WearControlState(true, false, true, 275L, 2, 621.5f, 1, 1)))
        assertEquals(1, sent.size)
        assertEquals(fixture.path, sent.single().first)
        fixture.assertMatches(sent.single().second)
    }

    @Test fun unchangedControlsGetFreshDeliveryMetadataOnAnotherSend() = runTest {
        val payloads = mutableListOf<DataMap>()
        var now = 100L
        val repo = WearSyncStateRepository({ _, values -> payloads += values }, { now++ },
            { throw AssertionError(it) })
        val controls = WearControlState(sosEnabled = true)
        repo.sendState(controls)
        repo.sendState(controls)
        assertEquals(100L, payloads[0].getLong("sentAt"))
        assertEquals(101L, payloads[1].getLong("sentAt"))
        payloads.forEach { it.remove("sentAt") }
        assertEquals(payloads[0], payloads[1])
        assertFalse(payloads[0].containsKey("colorBlinkActive"))
    }

    @Test fun transportFailureIsReportedAndTheNextSendCanSucceed() = runTest {
        val failure = IOException("disconnected")
        val errors = mutableListOf<Exception>()
        var attempts = 0
        val repo = WearSyncStateRepository({ _, _ -> if (attempts++ == 0) throw failure },
            { 100L }, { errors += it })
        assertFalse(repo.sendState(WearControlState()))
        assertTrue(repo.sendState(WearControlState()))
        assertEquals(listOf(failure), errors)
        assertEquals(2, attempts)
    }

    @Test fun cancellationIsPropagatedAndIsNotReportedAsATransportFailure() = runTest {
        val started = CompletableDeferred<Unit>()
        val errors = mutableListOf<Exception>()
        val repo = WearSyncStateRepository({ _, _ -> started.complete(Unit); awaitCancellation() },
            { 100L }, { errors += it })
        val send = async { repo.sendState(WearControlState()) }
        started.await()
        send.cancelAndJoin()
        assertTrue(send.isCancelled)
        assertTrue(errors.isEmpty())
    }

    @Test fun extendedColorsKeepAUsableLegacyValueAndSendTheExactNewPosition() = runTest {
        val payloads = mutableListOf<DataMap>()
        val repo = WearSyncStateRepository(
            { _, values -> payloads += values },
            { 100L },
            { throw AssertionError(it) }
        )
        assertTrue(repo.sendState(WearControlState(color = 2040f, activeColorButton = 1)))
        assertTrue(repo.sendState(WearControlState(color = 2295f, activeColorButton = 1)))
        assertTrue(repo.sendState(WearControlState(color = 621.5f, activeColorButton = 1)))

        assertEquals(1467f, payloads[0].getFloat("color"), 0f)
        assertEquals(2040f, payloads[0].getFloat("extendedColorProgress"), 0f)
        assertEquals(1275f, payloads[1].getFloat("color"), 0f)
        assertEquals(2295f, payloads[1].getFloat("extendedColorProgress"), 0f)
        assertEquals(621.5f, payloads[2].getFloat("color"), 0f)
        assertFalse(payloads[2].containsKey("extendedColorProgress"))
        payloads.forEach { assertEquals(1, it.getInt("key_active_color_button")) }
    }

    @Test fun orderedSliderColorsSendExactProgressAndAnOldCompatibleFallback() = runTest {
        val payloads = mutableListOf<DataMap>()
        val repo = WearSyncStateRepository(
            { _, values -> payloads += values },
            { 100L },
            { throw AssertionError(it) }
        )
        val positions = listOf(2296f, 2806f, 4081f, 4591f)
        for (stored in positions) {
            assertTrue(repo.sendState(WearControlState(color = stored, activeColorButton = 1)))
        }
        assertEquals(positions, payloads.map { it.getFloat("extendedColorProgress") })
        payloads.forEach { payload ->
            assertTrue(payload.getFloat("color", Float.NaN) in 0f..1785f)
            assertEquals(1, payload.getInt("key_active_color_button"))
        }
        assertEquals(0f, payloads.first().getFloat("color"), 0f)
        assertEquals(1785f, payloads.last().getFloat("color"), 0f)
    }
}
