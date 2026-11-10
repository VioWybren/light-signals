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

    @Test fun releaseContainsOnlyTheOwningSessionId() {
        val id = UUID.randomUUID()
        assertEquals(id, WatchConnectionRequest.decodeId(WatchConnectionRequest.encodeId(id)))
        assertNull(WatchConnectionRequest.decodeId(ByteArray(15)))
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
