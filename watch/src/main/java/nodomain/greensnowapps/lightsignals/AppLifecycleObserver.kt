package nodomain.greensnowapps.lightsignals

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import nodomain.greensnowapps.lightsignals.moduls.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLifecycleObserver @Inject constructor(
//    @ApplicationScope private val appScope: CoroutineScope,
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
//        Log.d("MY_TAG", "AppLifecycleObserver onStop()")

        // App moved to background (no visible activities).
        // Cancel children so you can restart them later when the app returns.
//        appScope.coroutineContext[Job]?.cancelChildren()
//        appScope.coroutineContext[Job]?.cancel()

    }

    // optional: do something onStart
    override fun onStart(owner: LifecycleOwner) {

//        Log.d("MY_TAG", "AppLifecycleObserver onStart()")

        // Optionally restart or reinitialize app-level work here if you want
        // (but usually you let collectors/start logic re-create what is needed)
    }
}
