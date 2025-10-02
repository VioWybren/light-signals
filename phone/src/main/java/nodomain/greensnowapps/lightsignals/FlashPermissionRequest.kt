package nodomain.greensnowapps.lightsignals

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest


@Composable
fun FlashPermissionRequest(
    onPermissionGranted: () -> Unit
) {

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                onPermissionGranted()
            } else {
                Toast.makeText(context, "Camera permission required for flash", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        // If not already granted, request it
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.CAMERA)
        } else {
            onPermissionGranted()
        }
    }
}
