package nodomain.greensnowapps.lightsignals.ui.viewmodels

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// PhoneTorchViewModel.kt
@HiltViewModel
class PhoneTorchViewModel @Inject constructor(
    @param: ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _hasTorch = MutableStateFlow<Boolean?>(null) // null = loading
    val hasTorch: StateFlow<Boolean?> = _hasTorch

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _hasTorch.value = checkHasTorch(appContext)
        }
    }
    fun getCameraManager(context: Context): CameraManager? =
        ContextCompat.getSystemService(context, CameraManager::class.java)

    private fun checkHasTorch(context: Context): Boolean {
        // cheap quick check first (fast)
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return true

        // more reliable: scan camera2 characteristics
        try {
            val cm = getCameraManager(context) ?: return false /* handle missing manager */
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val available = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (available) return true
            }
        } catch (e: CameraAccessException) {
            // treat as "no torch" or log
        } catch (e: Exception) {
            // defensive
        }
        return false
    }
}