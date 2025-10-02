package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.app.Activity

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.provider.Settings

@Composable
fun BundledPermissionRequester(
    request: Boolean,
    onResult: (Map<String, Boolean>) -> Unit
) {
    if (!request) return

    val context = LocalContext.current
    val activity = remember(context) { context as? Activity } ?: return

    // 1) Permissions array
    val perms = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    // 2) State flags
    var askedOnce by remember { mutableStateOf(false) }
    var showInitialExplain by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // 3) Launcher
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        onResult(result)
        // If any denied, decide next step
        val denied = result.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            // if any shouldShowRationale → showRationaleDialog
            if (denied.any { perm ->
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                }) {
                showRationaleDialog = true
            } else {
                showSettingsDialog = true
            }
        }
    }

    // 4) Kick-off logic
    LaunchedEffect(request) {
        if (askedOnce) return@LaunchedEffect

        // Already all granted?
        val cameraOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (cameraOk && notifOk) {
            onResult(
                mapOf(
                    Manifest.permission.CAMERA to true,
                    Manifest.permission.POST_NOTIFICATIONS to notifOk
                )
            )
            askedOnce = true
        } else {
            // first time: show our own explanation
            showInitialExplain = true
        }
    }

    // 5a) Initial “why we need both” dialog
    if (showInitialExplain) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("We need two permissions") },
            text = {
                Column {
                    Text("• CAMERA: only to toggle your flashlight/torch.")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Text("• NOTIFICATIONS: so our background Service can post alerts.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInitialExplain = false
                    askedOnce = true
                    launcher.launch(perms)
                }) {
                    Text("Continue")
                }
            }
        )
    }

    // 5b) Rationale dialog after a denial (Light Signals didn’t check “Never ask again”)
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Permissions needed to proceed") },
            text = {
                Column {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            activity, Manifest.permission.CAMERA
                        )
                    ) {
                        Text("We still need CAMERA for the flashlight.")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            activity, Manifest.permission.POST_NOTIFICATIONS
                        )
                    ) {
                        Text("We still need NOTIFICATIONS for alerts.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    launcher.launch(perms)
                }) {
                    Text("Request Again")
                }
            }
        )
    }

    // 5c) “Go to Settings” dialog when permissions are permanently denied
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Enable permissions in Settings") },
            text = {
                Text(
                    "You have permanently denied one or more permissions. " +
                            "Please open App Settings and enable them manually."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    // Deep-link to your app’s notification & perm settings
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }) {
                    Text("Open Settings")
                }
            }
        )
    }
}


