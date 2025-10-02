package nodomain.greensnowapps.lightsignals.model

data class WearControlState(
    val blinkEnabled: Boolean = false,
    val sosEnabled: Boolean = false,
    val torchOn: Boolean = false,
    val frequency: Long = 500L,
    val activeSound: Int = 0,
    val colorBlinkEnabled: Boolean = true,
    val color: Float =0xFFFFFFFF.toFloat(),
    val screenIndex: Int = 0,
    val activeColorButton: Int = 2 //  0 = Red Button, 1 = Middle Button, 2 = Green Button,  3= Flash Button,
)