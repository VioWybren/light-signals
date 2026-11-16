package nodomain.greensnowapps.lightsignals.core.sync

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Identifies a watch control action independently of its encoded value.
 *
 * @property wireId Stable action identifier used by the wire codec.
 */
enum class WatchControl(internal val wireId: Byte) {
    /**
     * Changes the phone LED selection.
     */
    TORCH(1),
    /**
     * Changes Blink selection and its screen/torch output.
     */
    BLINK(2),
    /**
     * Changes SOS selection and its screen/torch output.
     */
    SOS(3),
    /**
     * Changes the blink half-period.
     */
    FREQUENCY(4),
    /**
     * Changes the encoded custom-colour position.
     */
    COLOR(5),
    /**
     * Selects a screen-colour button.
     */
    COLOR_BUTTON(6),
    /**
     * Changes the selected controls/signal pager page.
     */
    SCREEN(7),
    /**
     * Changes the selected signal sample.
     */
    SOUND(8),
    /**
     * Revokes this command session without replacing the running phone signal.
     */
    DISCONNECT(9),
    /**
     * Checks session liveness without changing signal settings.
     */
    CHECK(10)
}

/**
 * Encodes disabling a timed mode or enabling it with screen or torch output.
 *
 * @property wireValue Encoded mode selection sent to the phone.
 */
enum class WatchModeChange(val wireValue: Int) {
    /**
     * Disables the mode while preserving the current output.
     */
    DISABLE(0),
    /**
     * Enables the mode using screen output.
     */
    ENABLE_ON_SCREEN(1),
    /**
     * Enables the mode using the phone LED.
     */
    ENABLE_ON_TORCH(2);

    /**
     * Maps mode/output selections to stable wire values and rejects unknown mode values.
     */
    companion object {
        /**
         * Converts a timed-mode selection and output choice into the wire mode value.
         *
         * @param enabled Whether to enable the requested output or mode.
         * @param torchSelected Whether the user selected the torch rather than screen output.
         * @return The disable, screen or torch mode selection.
         */
        fun forSelection(enabled: Boolean, torchSelected: Boolean) = when {
            !enabled -> DISABLE
            torchSelected -> ENABLE_ON_TORCH
            else -> ENABLE_ON_SCREEN
        }

        /**
         * Looks up a supported timed-mode wire value.
         *
         * @param value Encoded timed-mode selection to look up.
         * @return The matching mode, or null for an unknown value.
         */
        fun fromWireValue(value: Int) = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Carries a sequenced watch action and optional complete settings for phone-side validation.
 *
 * A user-action snapshot lets the phone restore the watch configuration after local phone edits.
 * Connection checks and disconnect messages carry no snapshot. The receiver still checks peer
 * identity, session, ordering and freshness.
 *
 * @property id Unique invitation or session identifier.
 * @property sequence Positive, increasing command number within this session.
 * @property control Control action to apply.
 * @property value Value encoded for the selected control action.
 * @property sentAt Request creation time in epoch milliseconds.
 * @property signal Complete per-action settings, or null for legacy commands and connection
 *     checks.
 */
data class WatchControlCommand(
    val id: UUID,
    val sequence: Long,
    val control: WatchControl,
    val value: Int,
    private val sentAt: Long = System.currentTimeMillis(),
    val signal: PhoneSignalState? = null
) {
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
     * Encodes the ordered control action and appends a correlated snapshot when supplied.
     *
     * @return The original action layout, optionally followed by the complete signal snapshot.
     */
    fun encode(): ByteArray {
        val action = ByteBuffer.allocate(SIZE)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .putLong(sequence)
            .put(control.wireId)
            .putInt(value)
            .putLong(sentAt)
            .array()
        return signal?.let { action + WatchConnectionRequest.create(it, sentAt, id).encode() } ?: action
    }

    /**
     * Validates that an attached full snapshot agrees with the encoded user action.
     *
     * @return True for a consistent snapshot or a legacy command without a snapshot.
     */
    fun hasConsistentSignal(): Boolean {
        val state = signal ?: return true
        return when (control) {
            WatchControl.TORCH -> value in 0..1 && state.torchOn == (value == 1)
            WatchControl.BLINK, WatchControl.SOS -> {
                val change = WatchModeChange.fromWireValue(value) ?: return false
                val enabled = if (control == WatchControl.BLINK) state.blinkEnabled else state.sosEnabled
                if (change == WatchModeChange.DISABLE) !enabled
                else enabled && !(state.blinkEnabled && state.sosEnabled) &&
                    state.torchOn == (change == WatchModeChange.ENABLE_ON_TORCH)
            }
            WatchControl.FREQUENCY -> value in 100..1000 && state.frequency == value.toLong()
            WatchControl.COLOR -> Float.fromBits(value) == state.color
            WatchControl.COLOR_BUTTON -> value in 0..2 && state.activeColorButton == value && !state.torchOn
            WatchControl.SCREEN -> value in 0..1 && state.screenIndex == value
            WatchControl.SOUND -> value in 0..3 && state.activeSound == value
            WatchControl.CHECK, WatchControl.DISCONNECT -> false
        }
    }

    /**
     * Defines control endpoints, acknowledgments and the backward-compatible command decoder.
     */
    companion object {
        /**
         * Phone endpoint for legacy-sized authenticated control commands.
         */
        const val PATH = WatchConnectionRequest.PHONE_PREFIX + "control"
        /**
         * Phone endpoint for authenticated commands carrying complete signal snapshots.
         */
        const val STATE_PATH = WatchConnectionRequest.PHONE_PREFIX + "control_state"
        /**
         * Explicit byte acknowledgment of an accepted control command.
         */
        const val ACCEPTED: Byte = 1
        /**
         * Explicit byte rejection of a snapshot control command.
         */
        const val REJECTED: Byte = 0
        /**
         * Maximum accepted age of this request in milliseconds.
         */
        const val LIFETIME_MS = 10_000L
        /**
         * Byte size of the original command header, excluding an optional appended signal
         * snapshot.
         */
        private const val SIZE = 37

        /**
         * Decodes an original command or a command with a correlated complete signal snapshot.
         *
         * Requires a positive sequence, known control ID and exact payload size. Attached
         * snapshots must match the command ID and timestamp and agree with its action. Freshness
         * and peer/session authorization remain the receiver's responsibility.
         *
         * @param bytes Encoded wire payload.
         * @return The decoded consistent command, or null for an invalid layout, correlation or
         *     action snapshot.
         */
        fun decode(bytes: ByteArray): WatchControlCommand? {
            if (bytes.size != SIZE && bytes.size != SIZE + WatchConnectionRequest.SIZE) return null
            val buffer = ByteBuffer.wrap(bytes)
            val id = UUID(buffer.long, buffer.long)
            val sequence = buffer.long
            if (sequence <= 0) return null
            val wireId = buffer.get()
            val control = WatchControl.entries.firstOrNull { it.wireId == wireId } ?: return null
            val value = buffer.int
            val sentAt = buffer.long
            val signal = if (bytes.size == SIZE) null else {
                val snapshot = WatchConnectionRequest.decode(bytes.copyOfRange(SIZE, bytes.size)) ?: return null
                if (!snapshot.matchesCommand(id, sentAt)) return null
                snapshot.signal
            }
            return WatchControlCommand(id, sequence, control, value, sentAt, signal)
                .takeIf { it.hasConsistentSignal() }
        }
    }
}
