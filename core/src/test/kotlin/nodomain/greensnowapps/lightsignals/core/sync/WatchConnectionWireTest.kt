package nodomain.greensnowapps.lightsignals.core.sync

import java.util.UUID
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies handoff, resume and authenticated-command wire contracts.
 */
class WatchConnectionWireTest {
    /**
     * Verifies that connection retries without a status and stops on an explicit rejection.
     */
    @Test fun connectionRetriesWithoutAStatusAndStopsOnAnExplicitRejection() {
        assertEquals(WatchConnectionReply.RETRY, WatchConnectionReply.decode(null))
        assertEquals(WatchConnectionReply.RETRY, WatchConnectionReply.decode(byteArrayOf()))
        for (reply in WatchConnectionReply.entries) {
            assertEquals(reply, WatchConnectionReply.decode(reply.encode()))
        }
    }

    /**
     * Verifies that invitation carries the complete signal configuration and expires.
     */
    @Test fun invitationCarriesTheCompleteSignalConfigurationAndExpires() {
        val now = 100_000L
        val signal = PhoneSignalState(true, false, true, 400L, 170f, 3, 1, 2)
        val request = WatchConnectionRequest.create(signal, now)
        assertEquals(request, WatchConnectionRequest.decode(request.encode()))
        assertTrue(request.isFresh(now))
        assertFalse(request.isFresh(now + WatchConnectionRequest.LIFETIME_MS + 1))
        assertFalse(request.isFresh(now - 5_001))
        assertNull(WatchConnectionRequest.decode(request.encode().copyOf(33)))
        assertNull(WatchConnectionRequest.decode(
            WatchConnectionRequest.create(signal.copy(activeSound = 4), now).encode()
        ))
        assertNull(WatchConnectionRequest.decode(
            WatchConnectionRequest.create(signal.copy(color = Float.NaN), now).encode()
        ))
    }

    /**
     * Verifies that resume carries only session IDS and expires.
     */
    @Test fun resumeCarriesOnlySessionIdsAndExpires() {
        val now = 100_000L
        val linkId = UUID.randomUUID()
        val request = WatchResumeRequest.create(linkId, now)
        assertEquals(request, WatchResumeRequest.decode(request.encode()))
        assertFalse(request.commandId == linkId)
        assertTrue(request.isFresh(now))
        assertFalse(request.isFresh(now + WatchConnectionRequest.LIFETIME_MS + 1))
        assertNull(WatchResumeRequest.decode(request.encode().copyOf(39)))
    }

    /**
     * Verifies that release contains only the owning session ID.
     */
    @Test fun releaseContainsOnlyTheOwningSessionId() {
        val id = UUID.randomUUID()
        assertEquals(id, WatchConnectionRequest.decodeId(WatchConnectionRequest.encodeId(id)))
        assertNull(WatchConnectionRequest.decodeId(ByteArray(15)))
    }

    /**
     * Verifies that a resume reply carries the complete current state for its command session.
     */
    @Test fun aResumeReplyCarriesTheCompleteCurrentStateForItsCommandSession() {
        val now = 100_000L
        val commandId = UUID.randomUUID()
        val signal = PhoneSignalState(true, false, true, 625L, 1234f, 3, 1, 2)
        val reply = WatchResumeReply(WatchConnectionReply.ACCEPTED, signal)
        assertEquals(reply, WatchResumeReply.decode(reply.encode(commandId, now), commandId, now))
        for (status in listOf(WatchConnectionReply.RETRY, WatchConnectionReply.REJECTED)) {
            assertEquals(WatchResumeReply(status),
                WatchResumeReply.decode(WatchResumeReply(status).encode(commandId, now), commandId, now))
        }
    }

    /**
     * Verifies that resume requires a fresh complete snapshot bound to its command session.
     */
    @Test fun resumeRequiresAFreshCompleteSnapshotBoundToItsCommandSession() {
        val now = 100_000L
        val commandId = UUID.randomUUID()
        val reply = WatchResumeReply(WatchConnectionReply.ACCEPTED, PhoneSignalState(false, true, false, 350L))
        val encoded = reply.encode(commandId, now)
        assertEquals(WatchConnectionReply.RETRY, WatchResumeReply.decode(encoded, UUID.randomUUID(), now).status)
        assertEquals(WatchConnectionReply.RETRY,
            WatchResumeReply.decode(encoded, commandId, now + WatchConnectionRequest.LIFETIME_MS + 1).status)
        assertEquals(WatchConnectionReply.RETRY,
            WatchResumeReply.decode(encoded.copyOf(encoded.size - 1), commandId, now).status)
        assertEquals(WatchConnectionReply.RETRY,
            WatchResumeReply.decode(WatchConnectionReply.ACCEPTED.encode(), commandId, now).status)
        assertEquals(WatchConnectionReply.RETRY,
            WatchResumeReply.decode(reply.copy(signal = reply.signal?.copy(color = Float.NaN)).encode(commandId, now),
                commandId, now).status)
    }

    /**
     * Verifies that explicit control carries session and sequence.
     */
    @Test fun explicitControlCarriesSessionAndSequence() {
        val now = 100_000L
        val command = WatchControlCommand(UUID.randomUUID(), 7L, WatchControl.COLOR, 17f.toBits(), now)
        assertEquals(command, WatchControlCommand.decode(command.encode()))
        assertFalse(command.isFresh(now + WatchControlCommand.LIFETIME_MS + 1))
        assertNull(WatchControlCommand.decode(command.copy(sequence = 0).encode()))
        assertNull(WatchControlCommand.decode(command.encode().copyOf(40)))
    }

    /**
     * Verifies that user action carries a complete correlated signal without changing legacy
     * commands.
     */
    @Test fun userActionCarriesACompleteCorrelatedSignalWithoutChangingLegacyCommands() {
        val now = 100_000L
        val signal = PhoneSignalState(true, true, false, 350L, 3400f, 2, 1, 1)
        val command = WatchControlCommand(UUID.randomUUID(), 7L, WatchControl.COLOR, signal.color.toBits(), now, signal)
        assertEquals(86, command.encode().size)
        assertEquals(command, WatchControlCommand.decode(command.encode()))
        val legacy = command.copy(signal = null)
        assertEquals(37, legacy.encode().size)
        assertEquals(legacy, WatchControlCommand.decode(legacy.encode()))
        assertFalse(command.isFresh(now + WatchControlCommand.LIFETIME_MS + 1))
    }

    /**
     * Verifies that full control rejects truncated invalid and uncorrelated snapshots.
     */
    @Test fun fullControlRejectsTruncatedInvalidAndUncorrelatedSnapshots() {
        val signal = PhoneSignalState(true, true, false, 350L, 3400f)
        val command = WatchControlCommand(UUID.randomUUID(), 1, WatchControl.COLOR, signal.color.toBits(), 100_000L, signal)
        assertNull(WatchControlCommand.decode(command.encode().dropLast(1).toByteArray()))
        val wrongId = command.encode().apply { this[37] = (this[37].toInt() xor 1).toByte() }
        assertNull(WatchControlCommand.decode(wrongId))
        val wrongTime = command.encode().apply { this[37 + 16] = (this[37 + 16].toInt() xor 1).toByte() }
        assertNull(WatchControlCommand.decode(wrongTime))
        val invalidColor = command.encode().also { ByteBuffer.wrap(it).putFloat(37 + 33, Float.NaN) }
        assertNull(WatchControlCommand.decode(invalidColor))
    }

    /**
     * Verifies that snapshot must agree with the action and cannot be attached to connection
     * checks.
     */
    @Test fun snapshotMustAgreeWithTheActionAndCannotBeAttachedToConnectionChecks() {
        val state = PhoneSignalState(true, true, false, 350L, 3400f)
        val command = WatchControlCommand(UUID.randomUUID(), 1, WatchControl.TORCH, 1, signal = state)
        assertEquals(command, WatchControlCommand.decode(command.encode()))
        assertNull(WatchControlCommand.decode(command.copy(value = 0).encode()))
        assertNull(WatchControlCommand.decode(command.copy(control = WatchControl.BLINK, value = WatchModeChange.ENABLE_ON_SCREEN.wireValue).encode()))
        assertNull(WatchControlCommand.decode(command.copy(control = WatchControl.CHECK, value = 0).encode()))
        assertNull(WatchControlCommand.decode(command.copy(control = WatchControl.DISCONNECT, value = 0).encode()))
    }
}
