package nodomain.greensnowapps.lightsignals.model

import com.google.android.gms.wearable.DataMap

internal const val CONTROL_STATE_PATH = "/control_state"

internal fun WearControlState.toDataMap(sentAt: Long): DataMap = DataMap().apply {
    putBoolean("blinkEnabled", blinkEnabled)
    putBoolean("sosEnabled", sosEnabled)
    putLong("frequency", frequency)
    putFloat("color", color)
    putBoolean("torchOn", torchOn)
    putInt("activeSound", activeSound)
    putInt("screenIndex", screenIndex)
    putInt("key_active_color_button", activeColorButton)
    putLong("sentAt", sentAt)
}
