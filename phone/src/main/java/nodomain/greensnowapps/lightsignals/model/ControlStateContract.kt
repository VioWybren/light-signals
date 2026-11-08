package nodomain.greensnowapps.lightsignals.model

import com.google.android.gms.wearable.DataMap
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette
import nodomain.greensnowapps.lightsignals.core.sync.ControlStateWire
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS

internal const val CONTROL_STATE_PATH = ControlStateWire.PATH

internal fun decodeControlState(data: DataMap): PhoneControlState = PhoneControlState(
    blinkEnabled = data.getBoolean(ControlStateWire.BLINK_ENABLED, false),
    sosEnabled = data.getBoolean(ControlStateWire.SOS_ENABLED, false),
    frequency = data.getLong(ControlStateWire.FREQUENCY, 500L),
    color = SignalColorPalette.decodeProgress(
        legacy = data.getFloat(ControlStateWire.COLOR, DEFAULT_COLOR_PROGRESS),
        extended = data.getFloat(ControlStateWire.EXTENDED_COLOR_PROGRESS, Float.NaN),
    ),
    torchOn = data.getBoolean(ControlStateWire.TORCH_ON, false),
    activeSound = data.getInt(ControlStateWire.ACTIVE_SOUND, 0),
    screenIndex = data.getInt(ControlStateWire.SCREEN_INDEX, 0),
    activeColorButton = data.getInt(ControlStateWire.ACTIVE_COLOR_BUTTON, 0)
).validated()
