package nodomain.greensnowapps.lightsignals

import android.Manifest
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
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Light Signals ${BuildConfig.VERSION_NAME} (versionCode=${BuildConfig.VERSION_CODE})")
        notificationPermissionRequested = savedInstanceState?.getBoolean(NOTIFICATIONS_REQUESTED) ?: false
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LightSignalNewTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PhoneScreenHost()
                }
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(NOTIFICATIONS_REQUESTED, notificationPermissionRequested)
        super.onSaveInstanceState(outState)
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
