package nodomain.greensnowapps.lightsignals

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.model.WearPhoneConnectionRequests
import nodomain.greensnowapps.lightsignals.ui.screens.WearScreenHost
import nodomain.greensnowapps.lightsignals.ui.theme.LightSignalNewTheme
import javax.inject.Inject

/**
 * Hosts the watch control and signal pager, including the sound-selection flow.
 *
 * Installs the themed [WearScreenHost] and keeps the splash visible until control
 * settings are initialized. Acquires foreground ownership of shared control work and
 * phone-request handling in [onStart], then releases it in [onStop]. Settings
 * restoration can proceed independently of the first Compose draw.
 *
 * Android invokes these lifecycle callbacks on Main. [foregroundOwner] identifies this
 * Activity within both shared components; they apply their own final-owner cleanup
 * rules. The window keep-screen-on flag follows the started lifetime.
 */
@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {
    /**
     * Hilt-injected shared owner of watch settings, signal phases and remembered phone
     * control.
     *
     * Accessed after base Activity creation and used on Main. This Activity acquires
     * [foregroundOwner] through the repository in [onStart] and releases it in [onStop].
     * The splash reads its initialization state; the repository owns asynchronous
     * restoration, signal jobs and reconnection monitoring.
     */
    @Inject lateinit var controlRepository: WearControlRepository
    /**
     * Hilt-injected shared handler for phone-led handoffs and relationship notices.
     *
     * Used on Main after base Activity creation. Foreground ownership follows [onStart]
     * and [onStop]; the handler owns RPC registration and verification scope cleanup when
     * its final foreground owner leaves.
     */
    @Inject lateinit var phoneConnectionRequests: WearPhoneConnectionRequests
    /**
     * Per-Activity ownership token shared by the control repository and phone-request
     * handler.
     *
     * Acquired in [onStart] and released in [onStop]. Reused when this instance starts
     * again; a recreated Activity has its own token, keeping shared-component ownership
     * separate across Activity instances.
     */
    private val foregroundOwner = Any()

    /**
     * Installs the watch splash and themed control/signal content on Main.
     *
     * Installs the splash before base Activity creation, then reads the injected
     * repository's initialization state to decide when it can disappear. [onStart]
     * acquires the foreground session and starts restoration when needed. Splash readiness
     * follows settings initialization, independently of phone pairing. Logs generated
     * version metadata before installing the Compose host.
     *
     * @param savedInstanceState Previously saved Activity state, or null for a fresh
     *     instance.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // onStart restores settings asynchronously, independently of drawing.
        splashScreen.setKeepOnScreenCondition { !controlRepository.isInitialized.value }
        Log.d(TAG, "Light Signals ${BuildConfig.VERSION_NAME} (versionCode=${BuildConfig.VERSION_CODE})")
        setContent {
            LightSignalNewTheme {
                WearScreenHost()
            }
        }
    }

    /**
     * Acquires shared foreground resources and sets the window keep-screen-on flag on
     * Main.
     *
     * After the base callback, starts control ownership first and phone-request ownership
     * second using [foregroundOwner]. Repositories can reuse work already owned by another
     * component. Returns without awaiting preference restoration or RPC listener
     * registration; the splash follows control readiness.
     */
    override fun onStart() {
        super.onStart()
        controlRepository.start(foregroundOwner)
        phoneConnectionRequests.start(foregroundOwner)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Releases this Activity's foreground resources and clears its keep-screen-on flag on
     * Main.
     *
     * Releases phone-request ownership before control ownership, then clears the window
     * flag and calls the base callback. Shared components stop work only when their
     * respective final owner leaves. Final-owner cleanup cancels watch signal and request
     * jobs, stops watch sound and pauses a remembered phone relationship for later resume.
     * Preference writes retain their independent lifetime.
     */
    override fun onStop() {
        phoneConnectionRequests.stop(foregroundOwner)
        controlRepository.stop(foregroundOwner)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    /**
     * Holds the log tag used by this Activity's startup version message.
     */
    private companion object {
        /**
         * Log tag for the version name and version code recorded in
         * [WearMainActivity.onCreate].
         */
        const val TAG = "WearMainActivity"
    }
}
