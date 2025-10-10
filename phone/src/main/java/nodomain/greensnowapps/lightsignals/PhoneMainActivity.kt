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
import dagger.hilt.android.AndroidEntryPoint
import nodomain.greensnowapps.lightsignals.ui.screens.PhoneScreenHost
import nodomain.greensnowapps.lightsignals.ui.theme.LightSignalNewTheme
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import nodomain.greensnowapps.lightsignals.utils.PermissionUtils

lateinit var appContext: Context

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

                        showPreExplain = showPreExplain.value,
                        preExplainMessage = preExplainMessage.value,
                        onPreExplainContinue = {
                            showPreExplain.value = false
                            permsLauncher.launch(perms)
                        },
                        onDismissPreExplain = { showPreExplain.value = false },

                        showRationale = showRationale.value,
                        rationaleMessage = rationaleMessage.value,
                        onRequestAgain = {
                            showRationale.value = false
                            permsLauncher.launch(perms)
                        },
                        onDismissRationale = { showRationale.value = false },

                        showSettings = showSettings.value,
                        settingsMessage = settingsMessage.value,
                        onOpenSettings = { openAppSettings() },
                        onDismissSettings = { showSettings.value = false }
                    )
                }
            }
        }

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
            if (intent?.action == ACTION_PERMISSIONS_MISSING) {
                tryEnableFlash()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private val showRationale = mutableStateOf(false)
    private val rationaleMessage = mutableStateOf("")
    private val showSettings = mutableStateOf(false)
    private val settingsMessage = mutableStateOf("")
    private val showPreExplain = mutableStateOf(false)
    private val preExplainMessage = mutableStateOf("")


    private val perms = arrayOf(
        Manifest.permission.CAMERA,
        *(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
    )

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        handlePermResults(results)
    }

    fun tryEnableFlash() {
        val cameraOk = PermissionUtils.hasCameraPermission(this)
        val notifOk = PermissionUtils.hasPostNotificationsPermission(this)

        if (cameraOk && notifOk) {
            viewModel.select(Active.FLASH)
        } else {
            viewModel.switchFlash(false)
        }

        if (!cameraOk || !notifOk) {
            preExplainMessage.value = buildPreExplainMessage(cameraOk, notifOk)
            showPreExplain.value = true
        } else {
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

        val denied = results.filterValues { !it }.keys.toList()
        if (denied.isEmpty()) return

        val shouldShowRationaleList = denied.filter { perm ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
        }

        if (shouldShowRationaleList.isNotEmpty()) {
            rationaleMessage.value = buildRationaleMessage(shouldShowRationaleList)
            showRationale.value = true
        } else {
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
