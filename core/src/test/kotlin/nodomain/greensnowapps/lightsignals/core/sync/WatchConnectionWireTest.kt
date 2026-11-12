package nodomain.greensnowapps.lightsignals.core.sync

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchConnectionWireTest {
    @Test fun connectionRetriesWithoutAStatusAndStopsOnAnExplicitRejection() {
        assertEquals(WatchConnectionReply.RETRY, WatchConnectionReply.decode(null))
        assertEquals(WatchConnectionReply.RETRY, WatchConnectionReply.decode(byteArrayOf()))
        for (reply in WatchConnectionReply.entries) {
            assertEquals(reply, WatchConnectionReply.decode(reply.encode()))
        }
    }

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

    @Test fun releaseContainsOnlyTheOwningSessionId() {
        val id = UUID.randomUUID()
        assertEquals(id, WatchConnectionRequest.decodeId(WatchConnectionRequest.encodeId(id)))
        assertNull(WatchConnectionRequest.decodeId(ByteArray(15)))
    }

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

    @Test fun explicitControlCarriesSessionAndSequence() {
        val now = 100_000L
        val command = WatchControlCommand(UUID.randomUUID(), 7L, WatchControl.COLOR, 17f.toBits(), now)
        assertEquals(command, WatchControlCommand.decode(command.encode()))
        assertFalse(command.isFresh(now + WatchControlCommand.LIFETIME_MS + 1))
        assertNull(WatchControlCommand.decode(command.copy(sequence = 0).encode()))
        assertNull(WatchControlCommand.decode(command.encode().copyOf(40)))
    }
}
