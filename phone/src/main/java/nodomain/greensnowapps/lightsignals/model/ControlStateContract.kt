package nodomain.greensnowapps.lightsignals.model

import com.google.android.gms.wearable.DataMap
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS

internal const val CONTROL_STATE_PATH = "/control_state"

internal fun decodeControlState(data: DataMap): PhoneControlState = PhoneControlState(
    blinkEnabled = data.getBoolean("blinkEnabled", false),
    sosEnabled = data.getBoolean("sosEnabled", false),
    frequency = data.getLong("frequency", 500L),
    color = data.getFloat("color", DEFAULT_COLOR_PROGRESS),
    torchOn = data.getBoolean("torchOn", false),
    activeSound = data.getInt("activeSound", 0),
    screenIndex = data.getInt("screenIndex", 0),
    activeColorButton = data.getInt("key_active_color_button", 0)
).validated()
