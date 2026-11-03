package nodomain.greensnowapps.lightsignals.model

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneTorchDriver internal constructor(writeTorch: (Boolean) -> Boolean) {
    @Inject constructor(cameraManager: CameraManager) : this(CameraTorchOutput(cameraManager)::writeTorch)

    private val sessions = TorchSessionController(writeTorch)

    suspend fun acquire(owner: Any) = sessions.acquire(owner)

    suspend fun setEnabled(owner: Any, enabled: Boolean): Boolean =
        sessions.setEnabled(owner, enabled)

    suspend fun release(owner: Any) = sessions.release(owner)
}

private class CameraTorchOutput(private val cameraManager: CameraManager) {
    private var cameraId: String? = null

    // Only the session controller calls this, under its mutex on Dispatchers.IO.
    fun writeTorch(enabled: Boolean): Boolean {
        return try {
            val id = cameraId ?: if (enabled) discoverCamera() else return true
            if (id == null) {
                Log.w("PhoneTorchDriver", "No rear flash unit is available")
                false
            } else {
                cameraManager.setTorchMode(id, enabled)
                true
            }
        } catch (error: CameraAccessException) {
            cameraId = null
            Log.w("PhoneTorchDriver", "Camera service unavailable", error)
            false
        } catch (error: SecurityException) {
            cameraId = null
            Log.w("PhoneTorchDriver", "Camera permission unavailable", error)
            false
        } catch (error: IllegalArgumentException) {
            cameraId = null
            Log.w("PhoneTorchDriver", "Flash camera disconnected", error)
            false
        } catch (error: RuntimeException) {
            cameraId = null
            Log.w("PhoneTorchDriver", "Unexpected camera service failure", error)
            false
        }
    }

    private fun discoverCamera(): String? {
        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
        return cameraId
    }
}

