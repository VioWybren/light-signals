package nodomain.greensnowapps.lightsignals.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Checks optional notification permission independently of flashlight availability.
 */
object PermissionUtils {

    /**
     * Checks notification runtime permission and app-wide notification availability.
     *
     * Pre-Android-13 versions return true without querying app-wide settings. On Android 13 and
     * later, both POST_NOTIFICATIONS and enabled app notifications are required by this helper.
     *
     * @param context Context used to query the platform service or permission state.
     * @return True under this helper's version-specific notification-access checks.
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        val perm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        return perm && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

}