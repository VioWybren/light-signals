package nodomain.greensnowapps.lightsignals

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dagger.hilt.android.HiltAndroidApp
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.model.WearPhoneOperations
import nodomain.greensnowapps.lightsignals.model.WearSyncStateRepository
import javax.inject.Inject


@HiltAndroidApp
class WearLightSignalsApplication : Application() {

    @Inject
    lateinit var wearControlRepository: WearControlRepository

    @Inject
    lateinit var wearSyncStateRepository: WearSyncStateRepository


    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {

            private var started = 0
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                started--
                if (started <= 0) {
                    wearControlRepository.clearAll()
                    wearSyncStateRepository.clear()
                }
            }
        })
    }
}
