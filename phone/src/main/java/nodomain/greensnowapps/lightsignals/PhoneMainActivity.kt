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

/**
 * Hosts the phone controls and signal pager, Quick Settings entry and
 * notification-permission flow.
 *
 * Initializes the Activity-scoped [viewModel] during creation so companion setup does
 * not depend on the first Compose draw or Activity resumption. Keeps the splash
 * visible until controls are ready and hosts [PhoneScreenHost] in the app theme.
 *
 * Android invokes lifecycle and permission callbacks on Main. The ViewModel owns
 * control events and companion integration; the foreground service owns ongoing
 * physical LED timing. Notification permission remains optional for LED selection.
 */
@AndroidEntryPoint
class PhoneMainActivity : ComponentActivity() {
    /**
     * Activity-scoped owner of phone control state, navigation events and companion
     * operations.
     *
     * Obtained lazily through the viewModels delegate and retained across configuration
     * changes. Accessed in [onCreate] so a new ViewModel begins control restoration and
     * companion registration before the first draw. Activity event handlers use it on
     * Main.
     */
    private val viewModel: PhoneMainViewModel by viewModels()
    /**
     * Whether this saved Activity state has already attempted the optional notification
     * request.
     *
     * Main-confined flag set before launching the permission request and saved through
     * [onSaveInstanceState]. Restored in [onCreate], or reset to false when no saved value
     * exists. Android remains the source of permission-grant state.
     */
    private var notificationPermissionRequested = false

    /**
     * Lifecycle-aware launcher for the optional POST_NOTIFICATIONS permission request.
     *
     * Registered during Activity initialization. Its Main-thread result callback requests
     * a torch-notification refresh after a grant. A denial leaves the already requested
     * LED selection unchanged.
     */
    private val notificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshTorchNotification()
    }

    /**
     * Installs the splash screen and themed phone pager, and prepares companion control on
     * Main.
     *
     * Installs the splash before base Activity creation, then accesses the ViewModel and
     * gates splash removal on readiness. Companion initialization can proceed before a
     * first draw or onResume, including when keyguard delays visible UI.
     *
     * Logs generated version metadata, restores the notification-request flag, enables
     * edge-to-edge layout, consumes a controls-navigation intent and installs the Compose
     * content. Adds the window keep-screen-on flag for the controls and signal UI.
     *
     * @param savedInstanceState Previously saved Activity state, or null for a fresh
     *     instance.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Prepare companion controls even when keyguard prevents the first draw or onResume.
        val controlsReady = viewModel.isReady
        splashScreen.setKeepOnScreenCondition { !controlsReady.value }
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

    /**
     * Handles a controls-navigation intent delivered to this existing Activity on Main.
     *
     * Calls the base callback and passes the delivered intent to [handleControlsIntent],
     * which consumes a recognized navigation extra and queues the controls-page event.
     *
     * @param intent New intent delivered by Android to the existing Activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleControlsIntent(intent)
    }

    /**
     * Notifies the ViewModel that the phone host has resumed on Main.
     *
     * The companion repository can reuse or recreate its transport registration and
     * announce readiness to a remembered watch, subject to its readiness and announcement
     * gates.
     */
    override fun onResume() {
        super.onResume()
        viewModel.onHostResumed()
    }

    /**
     * Consumes a controls-page request and queues navigation through the ViewModel on
     * Main.
     *
     * When OPEN_CONTROLS_EXTRA is true, removes it from the supplied intent before
     * emitting the navigation event. The ViewModel retains that event until the pager can
     * handle it. Other launches leave the selected page unchanged.
     *
     * @param intent Launch or redelivery intent whose recognized navigation extra will be
     *     removed.
     */
    private fun handleControlsIntent(intent: Intent) {
        if (intent.getBooleanExtra(OPEN_CONTROLS_EXTRA, false)) {
            intent.removeExtra(OPEN_CONTROLS_EXTRA)
            viewModel.requestOpenControls()
        }
    }

    /**
     * Saves the notification-request attempt flag alongside base Activity state on Main.
     *
     * Records whether this Activity state attempted the request, allowing recreation to
     * avoid repeating that attempt. Permission-grant state remains managed by Android.
     *
     * @param outState Bundle receiving the attempt flag and the base Activity saved state.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(NOTIFICATIONS_REQUESTED, notificationPermissionRequested)
        super.onSaveInstanceState(outState)
    }

    /**
     * Requests Android's add-tile confirmation flow and reports its outcome through
     * callbacks.
     *
     * Call on Main. On Android 13 and later, submits the app's tile component, label and
     * icon and dispatches results through mainExecutor. Earlier versions or an unavailable
     * status-bar service immediately request manual setup.
     *
     * Added and already-added results invoke onAvailable. TILE_NOT_ADDED and
     * REQUEST_IN_PROGRESS invoke neither callback. Other result errors and runtime
     * failures while submitting the request are logged and invoke onManualSetup. Returns
     * without waiting for the user's response.
     *
     * @param onAvailable Main-thread callback when the tile is added or already present.
     * @param onManualSetup Main-thread callback for explaining manual tile setup when the
     *     programmatic flow is unavailable or fails.
     */
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

    /**
     * Requests phone LED output before considering optional notification permission.
     *
     * Call on Main. The ViewModel selects FLASH immediately when ready or queues selection
     * until restoration completes. On Android 13 and later, attempts the permission
     * request only when this saved Activity state has not attempted it, the
     * notification-access helper reports it unavailable and the platform rationale check
     * is false.
     *
     * Marks the attempt before launching. A grant requests notification refresh; denial
     * leaves the LED request selected. Returns without waiting for service startup or the
     * permission response.
     */
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

    /**
     * Holds the Activity log tag and saved-state key for notification-request attempts.
     */
    private companion object {
        /**
         * Log tag for Activity version metadata and Quick Settings request failures.
         */
        const val TAG = "PhoneMainActivity"
        /**
         * Saved-instance-state key for the notification-permission request attempt flag.
         */
        const val NOTIFICATIONS_REQUESTED = "notifications_requested"
    }
}
