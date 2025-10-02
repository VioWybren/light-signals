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

    // Activity-scoped ViewModel: created by the Activity's ViewModelStoreOwner
//    private val vm: WearMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)

//        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            LightSignalNewTheme {
                WearScreenHost()
//                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                    Text("Hello")
//                }
            }
        }
        // Keep the display on at full brightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
//        android.os.Process.killProcess(Process.myPid())
    }

    override fun onStop() {
        super.onStop()
        android.os.Process.killProcess(Process.myPid())
    }

}


