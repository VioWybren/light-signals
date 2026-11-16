package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries rear-flash availability without requesting camera permission or opening a camera.
 *
 * @property query Synchronous hardware query, replaceable by tests.
 */
@Singleton
class PhoneTorchAvailability internal constructor(private val query: () -> Boolean) {
    /**
     * Creates an application-context rear-flash query without running it during construction.
     *
     * The caller chooses the query dispatcher; the torch ViewModel uses IO.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     */
    @Inject constructor(@ApplicationContext context: Context) : this({ checkHasTorch(context) })

    /**
     * Runs the injected rear-flash availability query without opening a camera.
     *
     * The production query handles platform failures as unavailable. Run this synchronous query
     * off Main, as the torch ViewModel does.
     *
     * @return The availability result supplied by the query.
     */
    fun hasTorch(): Boolean = query()

    /**
     * Queries rear-flash characteristics and converts platform failures to unavailable hardware.
     */
    private companion object {
        /**
         * Queries rear-flash characteristics and handles platform lookup failures.
         *
         * Run this synchronous query off Main.
         *
         * @param context Context used to query the platform service or permission state.
         * @return True when a usable rear flash is advertised.
         */
        fun checkHasTorch(context: Context): Boolean {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return true
            return try {
                val manager = ContextCompat.getSystemService(context, CameraManager::class.java) ?: return false
                manager.cameraIdList.any { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            } catch (_: CameraAccessException) {
                false
            } catch (_: RuntimeException) {
                false
            }
        }
    }
}
