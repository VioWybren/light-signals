package nodomain.greensnowapps.lightsignals

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Initializes the watch application's Hilt dependency graph.
 */
@HiltAndroidApp
class WearLightSignalsApplication : Application()
