package nodomain.greensnowapps.lightsignals.model

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
