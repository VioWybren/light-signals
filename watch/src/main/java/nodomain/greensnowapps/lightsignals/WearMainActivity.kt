package nodomain.greensnowapps.lightsignals

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.ui.screens.WearScreenHost
import nodomain.greensnowapps.lightsignals.ui.theme.LightSignalNewTheme
import javax.inject.Inject

@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {
    @Inject lateinit var controlRepository: WearControlRepository
    private val foregroundOwner = Any()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Light Signals ${BuildConfig.VERSION_NAME} (versionCode=${BuildConfig.VERSION_CODE})")
        setContent {
            LightSignalNewTheme {
                WearScreenHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controlRepository.start(foregroundOwner)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        controlRepository.stop(foregroundOwner)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    private companion object {
        const val TAG = "WearMainActivity"
    }
}
