package nodomain.greensnowapps.lightsignals.model

/**
 * Snapshots the watch controls published through the legacy Data Layer transport.
 *
 * @property blinkEnabled Whether the Blink control is enabled.
 * @property sosEnabled Whether the SOS control is enabled.
 * @property torchOn Whether the phone LED is selected; false selects screen output.
 * @property frequency Blink half-period in milliseconds, constrained to 100..1000 by
 *     receivers.
 * @property activeSound Selected sample number: 0 disables sound; 1..3 select signal samples.
 * @property color Encoded shared palette position used by the custom-colour control.
 * @property screenIndex Selected pager screen: 0 for controls, 1 for the signal display.
 * @property activeColorButton Selected colour/output ordinal: red, custom, green or torch.
 */
data class WearControlState(
    val blinkEnabled: Boolean = false,
    val sosEnabled: Boolean = false,
    val torchOn: Boolean = false,
    val frequency: Long = 500L,
    val activeSound: Int = 0,
    val color: Float = nodomain.greensnowapps.lightsignals.WearConstants.DEFAULT_COLOR_PROGRESS,
    val screenIndex: Int = 0,
    val activeColorButton: Int = 2
)
