package nodomain.greensnowapps.lightsignals

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

internal const val OPEN_CONTROLS_EXTRA = "nodomain.greensnowapps.lightsignals.extra.OPEN_CONTROLS"

class LightSignalsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val intent = Intent.makeMainActivity(ComponentName(this, PhoneMainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(OPEN_CONTROLS_EXTRA, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else if (isLocked) {
            unlockAndRun { startActivityAndCollapse(intent) }
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
