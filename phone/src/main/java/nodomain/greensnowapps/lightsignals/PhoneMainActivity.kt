package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import nodomain.greensnowapps.lightsignals.ui.screens.PhoneScreenHost
import nodomain.greensnowapps.lightsignals.ui.theme.LightSignalNewTheme
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import nodomain.greensnowapps.lightsignals.utils.PermissionUtils

@AndroidEntryPoint
class PhoneMainActivity : ComponentActivity() {
    private val viewModel: PhoneMainViewModel by viewModels()
    private var notificationPermissionRequested = false

    // The LED request is independent of this optional permission and its result.
    private val notificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshTorchNotification()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the launch icon visible until saved controls are restored.
        splashScreen.setKeepOnScreenCondition { !viewModel.isReady.value }
        Log.d(TAG, "Light Signals ${BuildConfig.VERSION_NAME} (versionCode=${BuildConfig.VERSION_CODE})")
        notificationPermissionRequested = savedInstanceState?.getBoolean(NOTIFICATIONS_REQUESTED) ?: false
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleControlsIntent(intent)
        setContent {
            LightSignalNewTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PhoneScreenHost()
                }
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleControlsIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setHostResumed(true)
    }

    override fun onPause() {
        viewModel.setHostResumed(false)
        super.onPause()
    }

    private fun handleControlsIntent(intent: Intent) {
        if (intent.getBooleanExtra(OPEN_CONTROLS_EXTRA, false)) {
            intent.removeExtra(OPEN_CONTROLS_EXTRA)
            viewModel.requestOpenControls()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(NOTIFICATIONS_REQUESTED, notificationPermissionRequested)
        super.onSaveInstanceState(outState)
    }

    internal fun requestQuickSettingsTile(onAvailable: () -> Unit, onManualSetup: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onManualSetup()
            return
        }
        val statusBarManager = getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onManualSetup()
            return
        }
        try {
            statusBarManager.requestAddTileService(
                ComponentName(this, LightSignalsTileService::class.java),
                applicationInfo.loadLabel(packageManager),
                Icon.createWithResource(this, R.drawable.outline_traffic_24),
                mainExecutor,
            ) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> onAvailable()
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED,
                    StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS -> Unit
                    else -> {
                        Log.w(TAG, "Quick Settings tile request failed: $result")
                        onManualSetup()
                    }
                }
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to request a Quick Settings tile", error)
            onManualSetup()
        }
    }

    fun tryEnableFlash() {
        viewModel.select(Active.FLASH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionRequested &&
            !PermissionUtils.hasPostNotificationsPermission(this) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            // Ask in response to the first LED request. Respect a previous denial rather
            // than automatically showing the request again on the next app launch.
            notificationPermissionRequested = true
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val TAG = "PhoneMainActivity"
        const val NOTIFICATIONS_REQUESTED = "notifications_requested"
    }
}
