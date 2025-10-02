package nodomain.greensnowapps.lightsignals.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// Permission helper
object PermissionUtils {

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        // On API < 33 this permission is granted at install time
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        val perm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        // also ensure the lightsignals hasn't globally disabled notifications for the app
        return perm && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    //  ensure the lightsignals hasn't globally disabled notifications for the app
    fun areNotificationsGloballyEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}