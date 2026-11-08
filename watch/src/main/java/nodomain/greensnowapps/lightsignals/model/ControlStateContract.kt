package nodomain.greensnowapps.lightsignals.model

import com.google.android.gms.wearable.DataMap
import nodomain.greensnowapps.lightsignals.core.signal.SignalColorPalette
import nodomain.greensnowapps.lightsignals.core.sync.ControlStateWire

internal const val CONTROL_STATE_PATH = ControlStateWire.PATH

internal fun WearControlState.toDataMap(sentAt: Long): DataMap = DataMap().apply {
    putBoolean(ControlStateWire.BLINK_ENABLED, blinkEnabled)
    putBoolean(ControlStateWire.SOS_ENABLED, sosEnabled)
    putLong(ControlStateWire.FREQUENCY, frequency)
    val progress = SignalColorPalette.clampProgress(color)
    putFloat(ControlStateWire.COLOR, SignalColorPalette.legacyProgress(progress))
    if (progress > SignalColorPalette.LEGACY_MAX_PROGRESS) {
        putFloat(ControlStateWire.EXTENDED_COLOR_PROGRESS, progress)
    }
    putBoolean(ControlStateWire.TORCH_ON, torchOn)
    putInt(ControlStateWire.ACTIVE_SOUND, activeSound)
    putInt(ControlStateWire.SCREEN_INDEX, screenIndex)
    putInt(ControlStateWire.ACTIVE_COLOR_BUTTON, activeColorButton)
    putLong(ControlStateWire.SENT_AT, sentAt)
}
