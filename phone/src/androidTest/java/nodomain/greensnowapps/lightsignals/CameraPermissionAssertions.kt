package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

@Suppress("DEPRECATION")
internal fun assertCameraPermissionAbsent() {
    val permissions = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty()
    assertFalse("The merged app manifest still requests CAMERA", Manifest.permission.CAMERA in permissions)
    assertEquals("Torch tests must run without camera permission", PackageManager.PERMISSION_DENIED,
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
}
