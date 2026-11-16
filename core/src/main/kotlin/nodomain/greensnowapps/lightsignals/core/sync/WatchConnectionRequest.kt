package nodomain.greensnowapps.lightsignals.core.sync

import java.nio.ByteBuffer
import java.util.UUID
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette

/**
 * Holds the shared signal configuration transferred during handoff, reconnect or a watch action.
 *
 * Fields describe selections rather than instantaneous hardware or screen phases. Serialization
 * and receivers validate bounds; this data holder does not activate a signal.
 *
 * @property torchOn Whether the phone LED is selected; false selects screen output.
 * @property blinkEnabled Whether the Blink control is enabled.
 * @property sosEnabled Whether the SOS control is enabled.
 * @property frequency Blink half-period in milliseconds, constrained to 100..1000 by receivers.
 * @property color Encoded shared palette position used by the custom-colour control.
 * @property activeSound Selected sample number: 0 disables sound; 1..3 select signal samples.
 * @property screenIndex Selected pager screen: 0 for controls, 1 for the signal display.
 * @property activeColorButton Selected colour/output ordinal: red, custom, green or torch.
 */
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

/**
 * Carries a handoff invitation ID, creation time and complete shared signal configuration.
 *
 * Decoding validates layout and value bounds. Callers separately check freshness, sender
 * capability and session authorization before applying its settings.
 *
 * @property id Unique invitation or session identifier.
 * @property sentAt Request creation time in epoch milliseconds.
 * @property signal Complete shared signal configuration.
 */
@ConsistentCopyVisibility
data class WatchConnectionRequest private constructor(
    val id: UUID,
    private val sentAt: Long,
    val signal: PhoneSignalState
) {
    /**
     * Checks that an attached snapshot belongs to the command's ID and creation timestamp.
     *
     * @param id Unique invitation or session identifier.
     * @param sentAt Request creation time in epoch milliseconds.
     * @return True when both correlation fields match.
     */
    internal fun matchesCommand(id: UUID, sentAt: Long): Boolean = this.id == id && this.sentAt == sentAt

    /**
     * Checks the request timestamp against the allowed age and clock-skew window.
     *
     * @param nowMillis Current epoch time in milliseconds, supplied for deterministic
     *     freshness checks.
     * @return True when the request can still be accepted.
     */
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isRecentWearRequest(sentAt, nowMillis, LIFETIME_MS)

    /**
     * Encodes the invitation ID, creation timestamp and complete signal configuration.
     *
     * @return The fixed-size invitation payload for handoff or an attached command snapshot.
     */
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

    /**
     * Defines invitation endpoints, capabilities, byte layout and UUID helpers.
     */
    companion object {
        /**
         * RPC namespace for phone-side connection and control endpoints.
         */
        const val PHONE_PREFIX = "/light_signals/phone/v3/"
        /**
         * RPC namespace for watch-side connection endpoints.
         */
        const val WATCH_PREFIX = "/light_signals/watch/v3/"
        /**
         * Watch handoff endpoint.
         */
        const val PATH = WATCH_PREFIX + "handoff"
        /**
         * Phone endpoint for a watch-led handoff that applies watch settings.
         */
        const val PHONE_PATH = PHONE_PREFIX + "handoff"
        /**
         * Phone endpoint that confirms a fresh link and returns current phone settings without
         * applying watch settings.
         */
        const val PHONE_STATE_PATH = PHONE_PREFIX + "handoff_state"
        /**
         * Watch endpoint that revokes a remembered phone relationship.
         */
        const val REVOKE_PATH = WATCH_PREFIX + "release"
        /**
         * Watch endpoint that pauses a matching command session while retaining its
         * relationship.
         */
        const val PAUSE_PATH = WATCH_PREFIX + "pause"
        /**
         * Watch endpoint announcing that a remembered phone can accept a resume.
         */
        const val AVAILABLE_PATH = WATCH_PREFIX + "available"
        /**
         * Legacy phone endpoint that acknowledges a remembered-link resume without a snapshot.
         */
        const val RESUME_PATH = PHONE_PREFIX + "resume"
        /**
         * Phone endpoint that resumes a remembered link and returns current settings.
         */
        const val RESUME_STATE_PATH = PHONE_PREFIX + "resume_state"
        /**
         * Phone endpoint that checks whether a command session is still active.
         */
        const val STATUS_PATH = PHONE_PREFIX + "status"
        /**
         * Phone endpoint that revokes the matching remembered relationship.
         */
        const val RELEASE_PATH = PHONE_PREFIX + "release"
        /**
         * Watch capability used to verify support for phone-led handoffs.
         */
        const val CAPABILITY = "light_signals_watch_connect_v3"
        /**
         * Phone capability used to verify support for authenticated watch controls.
         */
        const val PHONE_CAPABILITY = "light_signals_phone_control_v3"
        /**
         * Maximum accepted age of this request in milliseconds.
         */
        const val LIFETIME_MS = 8_000L
        /**
         * Encoded connection-snapshot size in bytes.
         */
        internal const val SIZE = 49
        /**
         * Byte count of a UUID encoded as two 64-bit values.
         */
        private const val ID_SIZE = 16
        /**
         * Bit mask for the torch selection in an invitation's flag byte.
         */
        private const val TORCH = 1
        /**
         * Bit mask for the blink selection in an invitation's flag byte.
         */
        private const val BLINK = 2
        /**
         * Bit mask for the sos selection in an invitation's flag byte.
         */
        private const val SOS = 4

        /**
         * Creates a signal invitation with the supplied timestamp and correlation ID.
         *
         * @param signal Complete shared signal configuration.
         * @param nowMillis Current epoch time in milliseconds, supplied for deterministic
         *     freshness checks.
         * @param id Unique invitation or session identifier.
         * @return An invitation carrying the supplied signal settings, timestamp and ID.
         */
        fun create(
            signal: PhoneSignalState,
            nowMillis: Long = System.currentTimeMillis(),
            id: UUID = UUID.randomUUID()
        ) = WatchConnectionRequest(id, nowMillis, signal)

        /**
         * Encodes a UUID for a connection-management endpoint.
         *
         * @param id Unique invitation or session identifier.
         * @return The 16-byte UUID representation.
         */
        fun encodeId(id: UUID): ByteArray = ByteBuffer.allocate(ID_SIZE)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .array()

        /**
         * Decodes a connection-management UUID payload.
         *
         * @param payload Encoded request or reply bytes.
         * @return The UUID, or null when the payload is not exactly 16 bytes.
         */
        fun decodeId(payload: ByteArray): UUID? {
            if (payload.size != ID_SIZE) return null
            val buffer = ByteBuffer.wrap(payload)
            return UUID(buffer.long, buffer.long)
        }

        /**
         * Decodes the exact invitation layout and validates flag and signal-value bounds.
         *
         * Does not check age or sender identity; handlers use isFresh and their peer/session
         * checks before applying settings. Receivers resolve simultaneous Blink and SOS
         * selections.
         *
         * @param bytes Encoded wire payload.
         * @return The structurally valid invitation, or null for a malformed layout or invalid
         *     control value.
         */
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

/**
 * Carries a stable relationship ID, replacement command ID and creation time for a resume
 * request.
 *
 * @property linkId Stable identifier of the remembered relationship.
 * @property commandId Fresh identifier for the resumed command session.
 * @property sentAt Request creation time in epoch milliseconds.
 */
@ConsistentCopyVisibility
data class WatchResumeRequest private constructor(
    val linkId: UUID,
    val commandId: UUID,
    val sentAt: Long
) {
    /**
     * Checks the request timestamp against the allowed age and clock-skew window.
     *
     * @param nowMillis Current epoch time in milliseconds, supplied for deterministic
     *     freshness checks.
     * @return True when the request can still be accepted.
     */
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isRecentWearRequest(sentAt, nowMillis, WatchConnectionRequest.LIFETIME_MS)

    /**
     * Encodes the remembered relationship ID, replacement command ID and creation timestamp.
     *
     * @return The 40-byte resume request, without signal settings.
     */
    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(WatchConnectionRequest.encodeId(linkId))
        .put(WatchConnectionRequest.encodeId(commandId))
        .putLong(sentAt)
        .array()

    /**
     * Defines the fixed resume layout and creates or decodes resume invitations.
     */
    companion object {
        /**
         * Fixed resume payload size: two UUIDs and an epoch-millisecond timestamp.
         */
        private const val SIZE = 40

        /**
         * Creates a resume request for the remembered relationship with a random replacement
         * command ID.
         *
         * @param linkId Stable identifier of the remembered relationship.
         * @param nowMillis Current epoch time in milliseconds, supplied for deterministic
         *     freshness checks.
         * @return A resume request with the supplied relationship and timestamp and a new
         *     command ID.
         */
        fun create(linkId: UUID, nowMillis: Long = System.currentTimeMillis()) =
            WatchResumeRequest(linkId, UUID.randomUUID(), nowMillis)

        /**
         * Decodes the two UUIDs and timestamp from an exact-size resume payload.
         *
         * Freshness, relationship matching and command-ID eligibility are checked by the session
         * owner after decoding.
         *
         * @param payload Encoded request or reply bytes.
         * @return The decoded resume request, or null for a different payload size.
         */
        fun decode(payload: ByteArray): WatchResumeRequest? {
            if (payload.size != SIZE) return null
            val buffer = ByteBuffer.wrap(payload)
            return WatchResumeRequest(UUID(buffer.long, buffer.long), UUID(buffer.long, buffer.long), buffer.long)
        }
    }
}

/**
 * Carries a connection status and optional current-phone settings for resume or state handoff.
 *
 * Modern accepted replies encode a correlated, timestamped snapshot. The legacy status-only
 * fallback can represent acceptance without a snapshot; callers select that compatibility path
 * explicitly.
 *
 * @property status Peer connection status.
 * @property signal Current phone settings for an accepted snapshot reply, or null for a
 *     status-only reply.
 */
data class WatchResumeReply(val status: WatchConnectionReply, val signal: PhoneSignalState? = null) {
    /**
     * Encodes the status and appends a fresh snapshot only for acceptance with supplied
     * settings.
     *
     * @param commandId Request or command ID used to correlate the returned snapshot.
     * @param nowMillis Current epoch time in milliseconds, supplied for deterministic freshness
     *     checks.
     * @return Status bytes followed by the correlated snapshot when one is eligible.
     */
    fun encode(commandId: UUID, nowMillis: Long = System.currentTimeMillis()): ByteArray {
        val snapshot = signal?.takeIf { status == WatchConnectionReply.ACCEPTED }
            ?.let { WatchConnectionRequest.create(it, nowMillis, commandId).encode() }
        return status.encode() + (snapshot ?: byteArrayOf())
    }

    /**
     * Validates current-state replies and their request correlation and freshness.
     */
    companion object {
        /**
         * Decodes a correlated, fresh phone snapshot and normalizes malformed replies to
         * RETRY.
         *
         * @param payload Encoded request or reply bytes.
         * @param commandId Fresh identifier for the resumed command session.
         * @param nowMillis Current epoch time in milliseconds, supplied for deterministic
         *     freshness checks.
         * @return The decoded status and valid phone snapshot, if present.
         */
        fun decode(payload: ByteArray?, commandId: UUID, nowMillis: Long = System.currentTimeMillis()): WatchResumeReply {
            if (payload == null || payload.isEmpty()) return WatchResumeReply(WatchConnectionReply.RETRY)
            val status = WatchConnectionReply.decode(byteArrayOf(payload[0]))
            if (status != WatchConnectionReply.ACCEPTED) {
                return WatchResumeReply(if (payload.size == 1) status else WatchConnectionReply.RETRY)
            }
            val snapshot = WatchConnectionRequest.decode(payload.copyOfRange(1, payload.size))
            if (snapshot == null || snapshot.id != commandId || !snapshot.isFresh(nowMillis)) {
                return WatchResumeReply(WatchConnectionReply.RETRY)
            }
            return WatchResumeReply(status, snapshot.signal)
        }
    }
}

/**
 * Reports whether a peer accepted, deferred or rejected a connection request.
 *
 * @property code Stable encoded reply byte.
 */
enum class WatchConnectionReply(private val code: Byte) {
    /**
     * The peer accepted the requested relationship or session.
     */
    ACCEPTED(1),
    /**
     * The peer is not ready; a fresh retry may succeed.
     */
    RETRY(2),
    /**
     * The peer rejected the request.
     */
    REJECTED(3);

    /**
     * Encodes this connection status as its single stable wire byte.
     *
     * @return One byte identifying this connection status.
     */
    fun encode(): ByteArray = byteArrayOf(code)

    /**
     * Decodes stable connection-status bytes with a retry-safe default.
     */
    companion object {
        /**
         * Decodes one recognized connection-status byte and treats every other reply as RETRY.
         *
         * @param payload Peer reply bytes, or null when no transport result is available.
         * @return The matching status, or RETRY for missing, empty, unknown or multi-byte
         *     replies.
         */
        fun decode(payload: ByteArray?): WatchConnectionReply {
            val code = payload?.singleOrNull()
            // A response without an application status can arrive before the watch endpoint is ready.
            return entries.firstOrNull { it.code == code } ?: RETRY
        }
    }
}

/**
 * Checks a peer timestamp against a bounded age and five-second future clock skew.
 *
 * @param sentAt Request creation time in epoch milliseconds.
 * @param nowMillis Current epoch time in milliseconds, supplied for deterministic freshness
 *     checks.
 * @param lifetimeMillis Maximum accepted request age in milliseconds.
 * @return True when the timestamp lies in the accepted window.
 */
internal fun isRecentWearRequest(sentAt: Long, nowMillis: Long, lifetimeMillis: Long): Boolean =
    sentAt in (nowMillis - lifetimeMillis)..(nowMillis + 5_000L)
