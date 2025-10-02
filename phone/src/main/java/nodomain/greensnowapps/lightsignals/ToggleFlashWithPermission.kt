package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.AlertDialog
//import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
//import androidx.compose.material3.ToggleButton
//import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import nodomain.greensnowapps.lightsignals.utils.PermissionUtils

//@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToggleFlashWithPermissions(
    checkedFlash: Boolean,
    viewModel: PhoneMainViewModel,
    startServiceIfNeeded: (() -> Unit)? = null,   // optional callback to start service
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    // one-time UI flags to drive dialogs
    var showExplain by remember { mutableStateOf(false) }
    var showRationaleAfterDeny by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }
    var askedOnce by remember { mutableStateOf(false) }

    // build permission list (POST_NOTIFICATIONS only on API 33+)
    val perms = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraOk = result[Manifest.permission.CAMERA] == true
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            result[Manifest.permission.POST_NOTIFICATIONS] == true
        else true

        if (cameraOk) {
            // enable flash in viewModel only after camera permission is present
            viewModel.select(Active.FLASH)
        } else {
            // ensure toggle visually and logically stays off
            viewModel.switchFlash(false)
        }

        // if you need to start a service only when notifications granted:
        if (notifOk) {
            startServiceIfNeeded?.invoke()
        }

        // if any denied — decide whether to show rationale (can re-request) or direct to settings
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            val anyShouldShowRationale = denied.any { perm ->
                // fallback: if we don't have Activity, treat as permanent deny -> settings
                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, perm)
                } ?: false
            }
            if (anyShouldShowRationale) showRationaleAfterDeny = true
            else showSettingsPrompt = true
        }
    }

    // The ToggleButton itself — we don't call select() immediately; we request perms first.
    IconToggleButton(
        checked = checkedFlash,
        onCheckedChange = {
            if (!checkedFlash) {
                // lightsignals wants to turn flash ON -> ensure permissions
                // if already granted, proceed immediately
                val cameraGranted = PermissionUtils.hasCameraPermission(context)
                val notifGranted = PermissionUtils.hasPostNotificationsPermission(context)

                if (cameraGranted) {
                    // camera ok — enable flash immediately
                    viewModel.select(Active.FLASH)
                }
                if (!cameraGranted || !notifGranted) {
                    // show one-line explain dialog then ask system permissions
                    showExplain = true
                } else {
                    // both ok, optionally start service
                    startServiceIfNeeded?.invoke()
                }
            } else {
                // turning OFF -> call existing method
                viewModel.switchFlash(false)
            }
        },
        modifier = Modifier
            .padding(2.dp)
            .height(40.dp)
            .width(80.dp),
//        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
//        colors = ToggleButtonDefaults.toggleButtonColors(
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color(0xffff6f00),
            contentColor = Color.White,
            checkedContainerColor = Color(0xFFc43e00),
            checkedContentColor = Color.White
        ),
    ) {
        Log.d("MY_LOG", "MainScreen  checkedFlash = $checkedFlash")
        Icon(
            if (!checkedFlash) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
            contentDescription = "Localized description",
            modifier = Modifier
                .size(50.dp)
                .rotate(90f)
        )
    }

    // 1) initial small explanation (shown when lightsignals first tries to enable)
    if (showExplain) {
        AlertDialog(
            onDismissRequest = { showExplain = false },
            title = { Text("Permissions required") },
            text = {
                Column {
                    Text("Camera — to toggle the torch (we don't take pictures).")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Text("Notifications — so background alerts (service) can post notifications.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExplain = false
                    askedOnce = true
                    launcher.launch(perms)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showExplain = false }) { Text("Cancel") }
            }
        )
    }

    // 2) if the lightsignals denied and shouldShowRequestPermissionRationale is true => re-explain & re-request
    if (showRationaleAfterDeny && activity != null) {
        AlertDialog(
            onDismissRequest = { /* block */ },
            title = { Text("Permissions needed") },
            text = {
                Column {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                        Text("We still need camera permission to control the torch.")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        Text("We still need notifications permission for background alerts.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleAfterDeny = false
                    launcher.launch(perms)
                }) { Text("Request again") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationaleAfterDeny = false
                    viewModel.switchFlash(false)
                }) { Text("Cancel") }
            }
        )
    }

    // 3) permanently denied -> prompt to open Settings
    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { /* block */ },
            title = { Text("Enable permissions in Settings") },
            text = { Text("One or more permissions were permanently denied. Open App Settings to enable them.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsPrompt = false
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSettingsPrompt = false
                    viewModel.switchFlash(false)
                }) { Text("Cancel") }
            }
        )
    }
}
