package nodomain.greensnowapps.lightsignals.core.sync

import java.nio.ByteBuffer
import java.util.UUID
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

/** Complete shared signal configuration for the device handing off control. */
data class PhoneSignalState(
    val torchOn: Boolean,
    val blinkEnabled: Boolean,
    val sosEnabled: Boolean,
    val frequency: Long,
    val color: Float = SignalColorPalette.DEFAULT_PROGRESS,
    val activeSound: Int = 0,
    val screenIndex: Int = 0,
    val activeColorButton: Int = 0
)

/** A one-shot invitation carrying the initiating device's current configuration. */
@ConsistentCopyVisibility
data class WatchConnectionRequest private constructor(
    val id: UUID,
    private val sentAt: Long,
    val signal: PhoneSignalState
) {
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isRecentWearRequest(sentAt, nowMillis, LIFETIME_MS)

    fun encode(): ByteArray {
        val flags = (if (signal.torchOn) TORCH else 0) or
            (if (signal.blinkEnabled) BLINK else 0) or (if (signal.sosEnabled) SOS else 0)
        return ByteBuffer.allocate(SIZE)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .putLong(sentAt)
            .put(flags.toByte())
            .putLong(signal.frequency)
            .putFloat(signal.color)
            .putInt(signal.activeSound)
            .putInt(signal.screenIndex)
            .putInt(signal.activeColorButton)
            .array()
    }

    companion object {
        const val PHONE_PREFIX = "/light_signals/phone/v3/"
        const val WATCH_PREFIX = "/light_signals/watch/v3/"
        const val PATH = WATCH_PREFIX + "handoff"
        const val PHONE_PATH = PHONE_PREFIX + "handoff"
        const val REVOKE_PATH = WATCH_PREFIX + "release"
        const val PAUSE_PATH = WATCH_PREFIX + "pause"
        const val AVAILABLE_PATH = WATCH_PREFIX + "available"
        const val RESUME_PATH = PHONE_PREFIX + "resume"
        const val STATUS_PATH = PHONE_PREFIX + "status"
        const val RELEASE_PATH = PHONE_PREFIX + "release"
        const val CAPABILITY = "light_signals_watch_connect_v3"
        const val PHONE_CAPABILITY = "light_signals_phone_control_v3"
        const val LIFETIME_MS = 8_000L
        private const val SIZE = 49
        private const val ID_SIZE = 16
        private const val TORCH = 1
        private const val BLINK = 2
        private const val SOS = 4

        fun create(
            signal: PhoneSignalState,
            nowMillis: Long = System.currentTimeMillis(),
            id: UUID = UUID.randomUUID()
        ) = WatchConnectionRequest(id, nowMillis, signal)

        fun encodeId(id: UUID): ByteArray = ByteBuffer.allocate(ID_SIZE)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .array()

        fun decodeId(payload: ByteArray): UUID? {
            if (payload.size != ID_SIZE) return null
            val buffer = ByteBuffer.wrap(payload)
            return UUID(buffer.long, buffer.long)
        }

        fun decode(bytes: ByteArray): WatchConnectionRequest? {
            if (bytes.size != SIZE) return null
            val buffer = ByteBuffer.wrap(bytes)
            val id = UUID(buffer.long, buffer.long)
            val sentAt = buffer.long
            val flags = buffer.get().toInt()
            val frequency = buffer.long
            val color = buffer.float
            val activeSound = buffer.int
            val screenIndex = buffer.int
            val activeColorButton = buffer.int
            if (flags !in 0..7 || frequency !in 100L..1000L) return null
            if (color != SignalColorPalette.clampProgress(color) ||
                activeSound !in 0..3 || screenIndex !in 0..1 || activeColorButton !in 0..3
            ) return null
            return WatchConnectionRequest(
                id,
                sentAt,
                PhoneSignalState(
                    torchOn = flags and TORCH != 0,
                    blinkEnabled = flags and BLINK != 0,
                    sosEnabled = flags and SOS != 0,
                    frequency = frequency,
                    color = color,
                    activeSound = activeSound,
                    screenIndex = screenIndex,
                    activeColorButton = activeColorButton
                )
            )
        }
    }
}

/** Resumes a confirmed link with a fresh command ID, without importing signal settings. */
@ConsistentCopyVisibility
data class WatchResumeRequest private constructor(
    val linkId: UUID,
    val commandId: UUID,
    val sentAt: Long
) {
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isRecentWearRequest(sentAt, nowMillis, WatchConnectionRequest.LIFETIME_MS)

    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(WatchConnectionRequest.encodeId(linkId))
        .put(WatchConnectionRequest.encodeId(commandId))
        .putLong(sentAt)
        .array()

    companion object {
        private const val SIZE = 40

        fun create(linkId: UUID, nowMillis: Long = System.currentTimeMillis()) =
            WatchResumeRequest(linkId, UUID.randomUUID(), nowMillis)

        fun decode(payload: ByteArray): WatchResumeRequest? {
            if (payload.size != SIZE) return null
            val buffer = ByteBuffer.wrap(payload)
            return WatchResumeRequest(UUID(buffer.long, buffer.long), UUID(buffer.long, buffer.long), buffer.long)
        }
    }
}

enum class WatchConnectionReply(private val code: Byte) {
    ACCEPTED(1),
    RETRY(2),
    REJECTED(3);

    fun encode(): ByteArray = byteArrayOf(code)

    companion object {
        fun decode(payload: ByteArray?): WatchConnectionReply {
            val code = payload?.singleOrNull()
            // A response without an application status can arrive before the watch endpoint is ready.
            return entries.firstOrNull { it.code == code } ?: RETRY
        }
    }
}

internal fun isRecentWearRequest(sentAt: Long, nowMillis: Long, lifetimeMillis: Long): Boolean =
    sentAt in (nowMillis - lifetimeMillis)..(nowMillis + 5_000L)
