package nodomain.greensnowapps.lightsignals

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Intent extra requesting the controls page when the Quick Settings tile opens the phone app.
 */
internal const val OPEN_CONTROLS_EXTRA = "nodomain.greensnowapps.lightsignals.extra.OPEN_CONTROLS"

/**
 * Opens the phone control screen from Quick Settings without directly changing a signal.
 */
class LightSignalsTileService : TileService() {
    /**
     * Publishes an inactive Quick Settings tile state while the system listens on Main.
     *
     * This tile opens the controls screen, so its state does not mirror the current phone LED
     * selection.
     */
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    /**
     * Opens the phone controls and collapses Quick Settings using the API-appropriate launch
     * path.
     *
     * Android calls this callback on Main; older locked devices first use the unlock callback.
     */
    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated") // API 24-33 compatibility.
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
