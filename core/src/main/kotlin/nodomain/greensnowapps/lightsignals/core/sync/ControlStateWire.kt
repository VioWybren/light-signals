package nodomain.greensnowapps.lightsignals.core.sync

/** Shared wire names. Platform adapters own DataMap encoding, decoding and validation. */
object ControlStateWire {
    const val PATH = "/control_state"
    const val BLINK_ENABLED = "blinkEnabled"
    const val SOS_ENABLED = "sosEnabled"
    const val FREQUENCY = "frequency"
    const val COLOR = "color"
    const val EXTENDED_COLOR_PROGRESS = "extendedColorProgress"
    const val TORCH_ON = "torchOn"
    const val ACTIVE_SOUND = "activeSound"
    const val SCREEN_INDEX = "screenIndex"
    const val ACTIVE_COLOR_BUTTON = "key_active_color_button"
    const val SENT_AT = "sentAt"
    const val MANUAL_LINK_STARTED_AT = "manualLinkStartedAt"
}
