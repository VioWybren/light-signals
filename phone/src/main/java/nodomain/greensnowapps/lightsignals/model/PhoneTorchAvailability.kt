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

/** Allows recording-driver UI tests to supply matching hardware availability on emulators. */
@Singleton
class PhoneTorchAvailability internal constructor(private val query: () -> Boolean) {
    @Inject constructor(@ApplicationContext context: Context) : this({ checkHasTorch(context) })

    fun hasTorch(): Boolean = query()

    private companion object {
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
