package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.view.WindowCompat
import nodomain.greensnowapps.lightsignals.ui.theme.LightSignalNewTheme
import dagger.hilt.android.AndroidEntryPoint
import nodomain.greensnowapps.lightsignals.model.FlashService
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import nodomain.greensnowapps.lightsignals.ui.screens.PhoneScreenHost
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel
import nodomain.greensnowapps.lightsignals.utils.PermissionUtils

lateinit var appContext: Context

// PhoneMainActivity hosting the Compose content.
@AndroidEntryPoint
class PhoneMainActivity : ComponentActivity() {
    private val viewModel: PhoneMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LightSignalNewTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PhoneScreenHost(
                        onToggleClick = { tryEnableFlash() },

                        // Pre-explain dialog
                        showPreExplain = showPreExplain.value,
                        preExplainMessage = preExplainMessage.value,
                        onPreExplainContinue = {
                            showPreExplain.value = false
                            permsLauncher.launch(perms)     // Launch the system permission dialog
                        },
                        onDismissPreExplain = { showPreExplain.value = false },

                        // Rationale dialog (after denial)
                        showRationale = showRationale.value,
                        rationaleMessage = rationaleMessage.value,
                        onRequestAgain = {
                            showRationale.value = false
                            permsLauncher.launch(perms)
                        },
                        onDismissRationale = { showRationale.value = false },

                        // Settings dialog (permanently denied)
                        showSettings = showSettings.value,
                        settingsMessage = settingsMessage.value,
                        onOpenSettings = { openAppSettings() },
                        onDismissSettings = { showSettings.value = false }
                    )
                }
            }
        }

        // Keep the display on at full brightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        appContext = this.applicationContext
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            this,
            broadcastReceiver,
            IntentFilter(ACTION_PERMISSIONS_MISSING),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MY_LOG", "PhoneMainActivity onReceive() intent?.action ${intent?.action}")
            // Call on UI thread
            if (intent?.action == ACTION_PERMISSIONS_MISSING) {
//                runOnUiThread { tryEnableFlash() }
                tryEnableFlash()
            }
        }
    }

    private fun stopFlashLightService() {
        val serviceFlashLightIntent = Intent(appContext, FlashService::class.java)
        stopService(serviceFlashLightIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
//        stopFlashLightService()
        Log.d("MY_LOG", "PhoneMainActivity onDestroy()")
    }

    // Compose-observed UI state for dialogs
    private val showRationale = mutableStateOf(false)
    private val rationaleMessage = mutableStateOf("")
    private val showSettings = mutableStateOf(false)
    private val settingsMessage = mutableStateOf("")
    private val showPreExplain = mutableStateOf(false)
    private val preExplainMessage = mutableStateOf("")


    private val perms = arrayOf(
        Manifest.permission.CAMERA,
        // only include POST_NOTIFICATIONS on API 33+
        *(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
    )

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // handle result (enable flash / start service / show settings dialog)
        handlePermResults(results)
    }

    // Single place that contains the toggle/permission logic.
    // It shows pre-explain dialog instead of directly launching the system dialog.
    fun tryEnableFlash() {
        val cameraOk = PermissionUtils.hasCameraPermission(this)
        val notifOk = PermissionUtils.hasPostNotificationsPermission(this)

        if (cameraOk && notifOk) {
            viewModel.select(Active.FLASH) // reuse your viewModel logic
        } else {
            viewModel.switchFlash(false)
        }

        // If any permission missing -> show pre-explain dialog first
        if (!cameraOk || !notifOk) {
            preExplainMessage.value = buildPreExplainMessage(cameraOk, notifOk)
            showPreExplain.value = true
        } else {
            // both OK: optionally start service
            Active.FLASH
        }
    }

    private fun handlePermResults(results: Map<String, Boolean>) {
        val camOk = results[Manifest.permission.CAMERA] == true
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            results[Manifest.permission.POST_NOTIFICATIONS] == true else true

        if (camOk && notifOk) viewModel.select(Active.FLASH) else viewModel.switchFlash(
            false
        )

        // Decide which dialog to show for denied permissions
        val denied = results.filterValues { !it }.keys.toList()
        if (denied.isEmpty()) return

        // If any denied permission still wants a rationale -> show rationale dialog
        val shouldShowRationaleList = denied.filter { perm ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
        }

        if (shouldShowRationaleList.isNotEmpty()) {
            rationaleMessage.value = buildRationaleMessage(shouldShowRationaleList)
            showRationale.value = true
        } else {
            // Permanently denied -> show Settings dialog
            settingsMessage.value = buildSettingsMessage(denied)
            showSettings.value = true
        }
    }


    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }

    //  helpers to create lightsignals-facing text
    private fun buildPreExplainMessage(cameraOk: Boolean, notifOk: Boolean) = buildString {
        append(appContext.getString(R.string.dialog_message_pre_explain_base))
        if (!cameraOk) append(appContext.getString(R.string.dialog_message_pre_explain_camera))
        if (!notifOk) append(appContext.getString(R.string.dialog_message_pre_explain_notification))
    }

    private fun buildRationaleMessage(perms: List<String>) = buildString {
        append(appContext.getString(R.string.dialog_message_rationale_dialog_base))
        if (perms.contains(Manifest.permission.CAMERA)) append(appContext.getString(R.string.dialog_message_rationale_dialog_camera))
        if (perms.contains(Manifest.permission.POST_NOTIFICATIONS)) append(appContext.getString(R.string.dialog_message_rationale_dialog_notification))
    }

    private fun buildSettingsMessage(perms: List<String>) = buildString {
        append(appContext.getString(R.string.dialog_message_setting_dialog_base))
        if (perms.contains(Manifest.permission.CAMERA)) append(appContext.getString(R.string.dialog_message_setting_dialog_camera))
        if (perms.contains(Manifest.permission.POST_NOTIFICATIONS)) append(appContext.getString(R.string.dialog_message_setting_dialog_notification))
    }
}
