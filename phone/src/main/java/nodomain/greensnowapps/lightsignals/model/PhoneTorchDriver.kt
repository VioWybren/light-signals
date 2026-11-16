package nodomain.greensnowapps.lightsignals.model

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes physical torch writes and ignores writes from superseded service owners.
 *
 * Suspend functions dispatch hardware operations to IO through the session controller.
 *
 * @param writeTorch Hardware write callback; returns false when the requested torch write
 *     fails.
 */
@Singleton
class PhoneTorchDriver internal constructor(writeTorch: (Boolean) -> Boolean) {
    /**
     * Creates the production rear-flash adapter and its shared serialized ownership controller.
     *
     * Hardware operations run on the controller's IO dispatcher; this constructor does not open
     * a camera or switch the LED.
     *
     * @param cameraManager Application-scoped camera service used only for flash-unit discovery
     *     and torch writes.
     */
    @Inject constructor(cameraManager: CameraManager) : this(CameraTorchOutput(cameraManager)::writeTorch)

    /**
     * IO-backed controller serializing physical writes and ownership across service instances.
     */
    private val sessions = TorchSessionController(writeTorch)

    /**
     * Acquires serialized torch ownership in a suspending IO operation.
     *
     * @param owner Identity token for the foreground or hardware owner.
     */
    suspend fun acquire(owner: Any) = sessions.acquire(owner)

    /**
     * Suspends while serializing an ownership-checked hardware write on IO.
     *
     * @param owner Identity token for the foreground or hardware owner.
     * @param enabled Whether to enable the requested output or mode.
     * @return True on success or for a superseded owner; false when the current owner's
     *     hardware write fails.
     */
    suspend fun setEnabled(owner: Any, enabled: Boolean): Boolean =
        sessions.setEnabled(owner, enabled)

    /**
     * Suspends on IO to attempt final torch-off and release this service's hardware ownership.
     *
     * Superseded owners leave the replacement signal untouched. The controller clears ownership
     * after the off-write attempt even when the hardware callback returns false.
     *
     * @param owner Identity token for the foreground or hardware owner.
     */
    suspend fun release(owner: Any) = sessions.release(owner)
}

/**
 * Finds a rear flash unit and converts camera-service failures into unsuccessful torch writes.
 *
 * The session controller calls this adapter under its mutex on IO.
 *
 * @property cameraManager Application-scoped camera service used only for flash-unit discovery
 *     and torch writes.
 */
private class CameraTorchOutput(private val cameraManager: CameraManager) {
    /**
     * Cached rear-flash camera ID, cleared after platform access failures before rediscovery.
     */
    private var cameraId: String? = null

    /**
     * Writes torch state on IO while the session controller holds its hardware mutex.
     *
     * Logs camera-service failures and clears the cached camera ID instead of crashing.
     *
     * @param enabled Whether to enable the requested output or mode.
     * @return True when the write succeeds or no off-write is needed; false when hardware
     *     access fails.
     */
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
            Log.w("PhoneTorchDriver", "Torch access was rejected", error)
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

    /**
     * Caches the first rear-facing camera that advertises a flash unit.
     *
     * Called on the torch controller's IO dispatcher while its mutex is held. Reads
     * characteristics without opening a camera; writeTorch handles platform exceptions.
     *
     * @return The selected camera ID, or null when no rear flash is advertised.
     */
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

