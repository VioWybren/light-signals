package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun NotificationPermissionRequest(
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                onPermissionGranted()
            } else {
                Toast.makeText(
                    context,
                    "Notification permission required to show alerts",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        // Only request on Android Tiramisu+; older versions grant at install-time
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onPermissionGranted()
            }
        } else {
            // pre-API 33, the OS grants this at install time
            onPermissionGranted()
        }
    }
}
