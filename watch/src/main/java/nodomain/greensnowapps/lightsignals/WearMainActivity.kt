package nodomain.greensnowapps.lightsignals

import android.os.Bundle
import android.os.Process
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import nodomain.greensnowapps.lightsignals.ui.screens.WearScreenHost
import nodomain.greensnowapps.lightsignals.ui.theme.LightSignalNewTheme

@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)

        setContent {
            LightSignalNewTheme {
                WearScreenHost()
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        Process.killProcess(Process.myPid())
    }

}


