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
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeRequest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies watch transport acknowledgment, legacy fallback and cancellation behavior.
 */
class WearSyncStateRepositoryTest {
    /**
     * Verifies that user action sends the full signal in one acknowledged request.
     */
    @Test fun userActionSendsTheFullSignalInOneAcknowledgedRequest() = runTest {
        val signal = PhoneSignalState(true, true, false, 350L, 3400f, 2, 1, 1)
        val command = WatchControlCommand(UUID.randomUUID(), 1, WatchControl.COLOR, signal.color.toBits(), signal = signal)
        val paths = mutableListOf<String>()
        val repo = WearSyncStateRepository({ _, _ -> fail("Linked controls must not publish cached DataItems") },
            System::currentTimeMillis, { throw AssertionError(it) },
            { node, path, payload ->
                assertEquals("phone", node)
                paths += path
                assertEquals(command, WatchControlCommand.decode(payload))
                byteArrayOf(WatchControlCommand.ACCEPTED)
            })
        assertTrue(repo.sendCommand("phone", command))
        assertEquals(listOf(WatchControlCommand.STATE_PATH), paths)
    }

    /**
     * Verifies that older endpoint falls back without changing session or sequence.
     */
    @Test fun olderEndpointFallsBackWithoutChangingSessionOrSequence() = runTest {
        val signal = PhoneSignalState(true, true, false, 350L, 3400f)
        val command = WatchControlCommand(UUID.randomUUID(), 4, WatchControl.COLOR, signal.color.toBits(), signal = signal)
        val paths = mutableListOf<String>()
        val repo = WearSyncStateRepository({ _, _ -> }, System::currentTimeMillis, { throw AssertionError(it) },
            { _, path, payload ->
                paths += path
                if (path == WatchControlCommand.STATE_PATH) byteArrayOf()
                else {
                    assertEquals(command.copy(signal = null), WatchControlCommand.decode(payload))
                    byteArrayOf(WatchControlCommand.ACCEPTED)
                }
            })
        assertTrue(repo.sendCommand("phone", command))
        assertEquals(listOf(WatchControlCommand.STATE_PATH, WatchControlCommand.PATH), paths)
    }

    /**
     * Verifies that rejected or timed out snapshot does not fall back to a different command.
     */
    @Test fun rejectedOrTimedOutSnapshotDoesNotFallBackToADifferentCommand() = runTest {
        val signal = PhoneSignalState(true, true, false, 350L)
        val command = WatchControlCommand(UUID.randomUUID(), 1, WatchControl.TORCH, 1, signal = signal)
        for (timeout in listOf(false, true)) {
            val paths = mutableListOf<String>()
            val repo = WearSyncStateRepository({ _, _ -> }, System::currentTimeMillis, { throw AssertionError(it) },
                { _, path, _ ->
                    paths += path
                    if (timeout) awaitCancellation() else byteArrayOf(WatchControlCommand.REJECTED)
                })
            assertFalse(repo.sendCommand("phone", command))
            assertEquals(listOf(WatchControlCommand.STATE_PATH), paths)
        }
    }

    /**
     * Verifies that heartbeat carries no snapshot and uses the original endpoint.
     */
    @Test fun heartbeatCarriesNoSnapshotAndUsesTheOriginalEndpoint() = runTest {
        val command = WatchControlCommand(UUID.randomUUID(), 1, WatchControl.CHECK, 0)
        val paths = mutableListOf<String>()
        val repo = WearSyncStateRepository({ _, _ -> }, System::currentTimeMillis, { throw AssertionError(it) },
            { _, path, payload ->
                paths += path
                assertEquals(37, payload.size)
                assertNull(checkNotNull(WatchControlCommand.decode(payload)).signal)
                byteArrayOf(WatchControlCommand.ACCEPTED)
            })
        assertTrue(repo.sendCommand("phone", command))
        assertEquals(listOf(WatchControlCommand.PATH), paths)
    }

    /**
     * Verifies that resume fetches the complete phone state for the new command session.
     */
    @Test fun resumeFetchesTheCompletePhoneStateForTheNewCommandSession() = runTest {
        val invitation = WatchResumeRequest.create(UUID.randomUUID())
        val current = PhoneSignalState(true, false, true, 725L, 1450f, 3, 1, 2)
        val paths = mutableListOf<String>()
        val repo = WearSyncStateRepository({ _, _ -> fail("Resume must not publish watch settings") },
            System::currentTimeMillis, { throw AssertionError(it) },
            { node, path, payload ->
                assertEquals("phone", node)
                assertEquals(invitation, WatchResumeRequest.decode(payload))
                paths += path
                WatchResumeReply(WatchConnectionReply.ACCEPTED, current).encode(invitation.commandId)
            })
        assertEquals(WatchResumeReply(WatchConnectionReply.ACCEPTED, current), repo.resumeLink("phone", invitation))
        assertEquals(listOf(WatchConnectionRequest.RESUME_STATE_PATH), paths)
    }

    /**
     * Verifies that an older phone can use the original resume endpoint.
     */
    @Test fun anOlderPhoneCanUseTheOriginalResumeEndpoint() = runTest {
        val invitation = WatchResumeRequest.create(UUID.randomUUID())
        val paths = mutableListOf<String>()
        val repo = WearSyncStateRepository({ _, _ -> }, System::currentTimeMillis, { throw AssertionError(it) },
            { _, path, _ ->
                paths += path
                if (path == WatchConnectionRequest.RESUME_STATE_PATH) byteArrayOf()
                else WatchConnectionReply.ACCEPTED.encode()
            })
        assertEquals(WatchResumeReply(WatchConnectionReply.ACCEPTED), repo.resumeLink("phone", invitation))
        assertEquals(listOf(WatchConnectionRequest.RESUME_STATE_PATH, WatchConnectionRequest.RESUME_PATH), paths)
    }

    /**
     * Verifies that an incomplete snapshot keeps the watch waiting for a complete reply.
     */
    @Test fun anIncompleteSnapshotKeepsTheWatchWaitingForACompleteReply() = runTest {
        val paths = mutableListOf<String>()
        val repo = WearSyncStateRepository({ _, _ -> }, System::currentTimeMillis, { throw AssertionError(it) },
            { _, path, _ ->
                paths += path
                WatchConnectionReply.ACCEPTED.encode()
            })
        assertEquals(WatchResumeReply(WatchConnectionReply.RETRY),
            repo.resumeLink("phone", WatchResumeRequest.create(UUID.randomUUID())))
        assertEquals(listOf(WatchConnectionRequest.RESUME_STATE_PATH), paths)
    }

    /**
     * Verifies that sender uses the shared wire path types and values.
     */
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

    /**
     * Verifies that unchanged controls get fresh delivery metadata on another send.
     */
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

    /**
     * Verifies that transport failure is reported and the next send can succeed.
     */
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

    /**
     * Verifies that cancellation is propagated and is not reported as a transport failure.
     */
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

    /**
     * Verifies that extended colors keep a usable legacy value and send the exact new
     * position.
     */
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

    /**
     * Verifies that ordered slider colors send exact progress and an old compatible fallback.
     */
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
