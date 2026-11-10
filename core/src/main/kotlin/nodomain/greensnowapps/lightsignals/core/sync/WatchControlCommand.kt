package nodomain.greensnowapps.lightsignals.core.sync

import java.nio.ByteBuffer
import java.util.UUID

enum class WatchControl(internal val wireId: Byte) {
    TORCH(1),
    BLINK(2),
    SOS(3),
    FREQUENCY(4),
    COLOR(5),
    COLOR_BUTTON(6),
    SCREEN(7),
    SOUND(8),
    DISCONNECT(9),
    CHECK(10)
}

/** Value for a BLINK or SOS action. Disabling the mode preserves the phone's current output. */
enum class WatchModeChange(val wireValue: Int) {
    DISABLE(0),
    ENABLE_ON_SCREEN(1),
    ENABLE_ON_TORCH(2);

    companion object {
        fun forSelection(enabled: Boolean, torchSelected: Boolean) = when {
            !enabled -> DISABLE
            torchSelected -> ENABLE_ON_TORCH
            else -> ENABLE_ON_SCREEN
        }

        fun fromWireValue(value: Int) = entries.firstOrNull { it.wireValue == value }
    }
}

/** An action authorized by the current watch connection. */
data class WatchControlCommand(
    val id: UUID,
    val sequence: Long,
    val control: WatchControl,
    val value: Int,
    private val sentAt: Long = System.currentTimeMillis()
) {
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isRecentWearRequest(sentAt, nowMillis, LIFETIME_MS)

    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .putLong(id.mostSignificantBits)
        .putLong(id.leastSignificantBits)
        .putLong(sequence)
        .put(control.wireId)
        .putInt(value)
        .putLong(sentAt)
        .array()

    companion object {
        const val PATH = WatchConnectionRequest.PHONE_PREFIX + "control"
        const val ACCEPTED: Byte = 1
        const val LIFETIME_MS = 10_000L
        private const val SIZE = 37

        fun decode(bytes: ByteArray): WatchControlCommand? {
            if (bytes.size != SIZE) return null
            val buffer = ByteBuffer.wrap(bytes)
            val id = UUID(buffer.long, buffer.long)
            val sequence = buffer.long
            if (sequence <= 0) return null
            val wireId = buffer.get()
            val control = WatchControl.entries.firstOrNull { it.wireId == wireId } ?: return null
            return WatchControlCommand(id, sequence, control, buffer.int, buffer.long)
        }
    }
}
