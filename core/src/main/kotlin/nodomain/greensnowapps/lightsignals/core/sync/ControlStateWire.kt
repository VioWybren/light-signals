package nodomain.greensnowapps.lightsignals.core.sync

/**
 * Defines the stable Data Layer path and field names shared by the phone and watch.
 */
object ControlStateWire {
    /**
     * Stable DataItem path for legacy complete control snapshots.
     */
    const val PATH = "/control_state"
    /**
     * Stable DataMap field name for Blink selection.
     */
    const val BLINK_ENABLED = "blinkEnabled"
    /**
     * Stable DataMap field name for SOS selection.
     */
    const val SOS_ENABLED = "sosEnabled"
    /**
     * Stable DataMap field name for blink half-period in milliseconds.
     */
    const val FREQUENCY = "frequency"
    /**
     * Stable DataMap field name for legacy-compatible colour progress.
     */
    const val COLOR = "color"
    /**
     * Stable DataMap field name for newer encoded palette progress.
     */
    const val EXTENDED_COLOR_PROGRESS = "extendedColorProgress"
    /**
     * Stable DataMap field name for torch selection.
     */
    const val TORCH_ON = "torchOn"
    /**
     * Stable DataMap field name for selected sound.
     */
    const val ACTIVE_SOUND = "activeSound"
    /**
     * Stable DataMap field name for selected pager page.
     */
    const val SCREEN_INDEX = "screenIndex"
    /**
     * Stable DataMap field name for selected colour/output ordinal.
     */
    const val ACTIVE_COLOR_BUTTON = "key_active_color_button"
    /**
     * Stable DataMap field name for snapshot creation time.
     */
    const val SENT_AT = "sentAt"
    /**
     * Stable DataMap field name for explicit legacy connection start time.
     */
    const val MANUAL_LINK_STARTED_AT = "manualLinkStartedAt"
}
