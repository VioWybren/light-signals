package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.app.NotificationManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.RequiresDevice
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import nodomain.greensnowapps.lightsignals.model.FlashLightService
import nodomain.greensnowapps.lightsignals.model.PhoneControlRepository
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.context
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.device
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.eventually
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.grantPermission
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.onMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses production Hilt bindings and the real camera service; no recording torch replacement. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
@RequiresDevice
class PhoneTorchHardwareTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var controls: PhoneControlRepository

    private val edges = CopyOnWriteArrayList<Boolean>()
    private val enabled = AtomicBoolean(false)
    private var cameraId: String? = null
    private var callbackRegistered = false
    private var scenario: ActivityScenario<PhoneMainActivity>? = null
    private val cameras get() = context.getSystemService(CameraManager::class.java)
    private val serviceName get() = FlashLightService::class.java.name
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, on: Boolean) {
            if (id == cameraId) {
                enabled.set(on)
                edges += on
            }
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) enabled.set(false)
        }
    }

    @Before fun prepare() {
        device.wakeUp()
        hilt.inject()
        assertCameraPermissionAbsent()
        cameraId = cameras.cameraIdList.firstOrNull { id ->
            val characteristics = cameras.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        assumeTrue("A real rear LED is required for this hardware test", cameraId != null)
        cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        callbackRegistered = true
        if (Build.VERSION.SDK_INT >= 33) grantPermission(Manifest.permission.POST_NOTIFICATIONS)
        scenario = ActivityScenario.launch(PhoneMainActivity::class.java)
        eventually("Phone controls did not initialize") { controls.initialized.value }
    }

    @After fun cleanup() {
        device.wakeUp()
        try {
            scenario?.close()
            if (this::controls.isInitialized) {
                onMain { controls.clearAll() }
                eventually("Hardware torch session survived cleanup") {
                    !DeviceTestSupport.serviceRunning(serviceName) && !enabled.get() && !cpuLockHeld()
                }
            }
        } finally {
            if (callbackRegistered) cameras.unregisterTorchCallback(callback)
        }
    }

    @Test fun steadyLedWorksThroughTheRealCameraServiceWithoutCameraPermission() {
        onMain { controls.setFlash(true) }
        eventually("Camera service did not enable the rear LED without CAMERA permission") {
            enabled.get() && DeviceTestSupport.serviceRunning(serviceName, foregroundOnly = true)
        }
        assertFalse("Steady LED unnecessarily holds the signal CPU lock", cpuLockHeld())
        assertCameraPermissionAbsent()
        stopAndAwaitLedOff()
    }

    @Test fun realLedKeepsBlinkingWithTheScreenOffWithoutCameraPermission() {
        onMain {
            controls.setBlinking(true, 200)
            controls.setFlash(true)
        }
        awaitScreenOffEdges()
        stopAndAwaitLedOff()
    }

    @Test fun realLedKeepsSendingSosWithTheScreenOffWithoutCameraPermission() {
        onMain {
            controls.switchSOS(true)
            controls.setFlash(true)
        }
        awaitScreenOffEdges()
        stopAndAwaitLedOff()
    }

    private fun awaitScreenOffEdges() {
        eventually("Timed torch service did not acquire its CPU lock") {
            DeviceTestSupport.serviceRunning(serviceName, foregroundOnly = true) && cpuLockHeld()
        }
        device.sleep()
        val power = context.getSystemService(PowerManager::class.java)
        eventually("Display did not turn off") { !power.isInteractive }
        val before = edges.size
        eventually("Real camera-service LED transitions stopped with the screen off", timeoutMillis = 15_000) {
            val phases = edges.drop(before)
            phases.count { it } >= 3 && phases.count { !it } >= 3
        }
        assertFalse("Display woke during the hardware check", power.isInteractive)
        assertTrue("Timed signal lost its CPU lock", cpuLockHeld())
        assertCameraPermissionAbsent()
    }

    private fun stopAndAwaitLedOff() {
        onMain { controls.setFlash(false) }
        eventually("Real LED, foreground notification, or CPU lock survived stop") {
            !enabled.get() && !DeviceTestSupport.serviceRunning(serviceName) && !cpuLockHeld() &&
                context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == NOTIF_ID }
        }
    }

    private fun cpuLockHeld(): Boolean = device.executeShellCommand("dumpsys power").lineSequence().any {
        it.contains("PARTIAL_WAKE_LOCK") && it.contains("${context.packageName}:FlashLightService")
    }
}
