@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import javax.inject.Inject
import nodomain.greensnowapps.lightsignals.model.FlashLightService
import nodomain.greensnowapps.lightsignals.model.PhoneControlRepository
import nodomain.greensnowapps.lightsignals.model.PhoneTorchDriver
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.assertPermissionDenied
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.context
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.device
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.eventually
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.grantPermission
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.focusTask
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.onMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.CoreMatchers.allOf

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class PhoneAndroidBehaviorTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    private val writes = CopyOnWriteArrayList<Boolean>()
    private val torchEnabled = AtomicBoolean(false)
    private val failEnable = AtomicBoolean(false)

    // Android starts the real service and checks real permissions. Only the LED is replaced.
    @BindValue @JvmField val torchDriver = PhoneTorchDriver { enabled ->
        writes += enabled
        if (enabled && failEnable.get()) false else {
            torchEnabled.set(enabled)
            true
        }
    }

    @Inject lateinit var controls: PhoneControlRepository
    private var scenario: ActivityScenario<PhoneMainActivity>? = null
    private val serviceName get() = FlashLightService::class.java.name
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    @Before fun inject() {
        device.wakeUp()
        hilt.inject()
    }

    @After fun cleanup() {
        device.wakeUp()
        scenario?.close()
        if (!this::controls.isInitialized) return
        onMain { controls.clearAll() }
        eventually("Torch service survived test cleanup") {
            !DeviceTestSupport.serviceRunning(serviceName) && !torchEnabled.get() && !signalCpuLockHeld()
        }
    }

    @Test fun missingCameraExplainsPermissionWithoutStartingTheService() {
        assertPermissionDenied(Manifest.permission.CAMERA)
        launchPhone()
        clickTorch()
        compose.onNodeWithText(text(R.string.dialog_title_pre_explain)).assertIsDisplayed()
        assertFalse(controls.flashOn.value)
        assertFalse(DeviceTestSupport.serviceRunning(serviceName))
        assertFalse(writes.contains(true))
    }

    @Test fun grantingCameraInSystemDialogStartsForegroundServiceWithNotification() {
        grantNotifications()
        assertPermissionDenied(Manifest.permission.CAMERA)
        launchPhone()
        clickTorch()
        compose.onNodeWithText(text(R.string._continue)).performClick()
        permissionButton("permission_allow_foreground_only_button", "permission_allow_button").click()
        awaitTorchRunning()
        assertEquals(PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
        eventually("Foreground notification was not posted") {
            notifications.activeNotifications.any { it.id == NOTIF_ID }
        }
        val notification = notifications.activeNotifications.single { it.id == NOTIF_ID }.notification
        assertEquals(text(R.string.notification_title), notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        val pendingIntent = checkNotNull(notification.contentIntent) { "Notification has no launch intent" }
        if (Build.VERSION.SDK_INT >= 26) assertEquals("light_signals_channel_id", notification.channelId)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(pendingIntent.isImmutable)
    }

    @Test @SdkSuppress(minSdkVersion = 30)
    fun repeatedCameraDenialOffersSettingsAndOpensTheCorrectAppPage() {
        grantNotifications()
        assertPermissionDenied(Manifest.permission.CAMERA)
        launchPhone()
        clickTorch()
        compose.onNodeWithText(text(R.string._continue)).performClick()
        permissionButton("permission_deny_button").click()
        awaitText(R.string.confirm_button_rationale_dialog)
        compose.onNodeWithText(text(R.string.confirm_button_rationale_dialog)).assertIsDisplayed().performClick()
        permissionButton("permission_deny_button", "permission_deny_and_dont_ask_again_button").click()
        awaitText(R.string.confirm_button_setting_dialog)
        val settingsUri = Uri.fromParts("package", context.packageName, null)
        Intents.init()
        try {
            compose.onNodeWithText(text(R.string.confirm_button_setting_dialog)).assertIsDisplayed().performClick()
            Intents.intended(allOf(hasAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS), hasData(settingsUri)))
            eventually("App settings did not open") { device.currentPackageName != context.packageName }
        } finally {
            Intents.release()
        }
        assertPermissionDenied(Manifest.permission.CAMERA)
        assertFalse(controls.flashOn.value)
        assertFalse(writes.contains(true))
        device.pressBack()
        scenario!!.moveToState(Lifecycle.State.RESUMED)
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    fun cameraGrantWorksWhenNotificationsAreDenied() {
        assertPermissionDenied(Manifest.permission.POST_NOTIFICATIONS)
        grantPermission(Manifest.permission.CAMERA)
        launchPhone()
        clickTorch()
        awaitTorchRunning()
        assertPermissionDenied(Manifest.permission.POST_NOTIFICATIONS)
        compose.onNodeWithText(text(R.string.dialog_title_pre_explain)).assertDoesNotExist()
        clickTorch()
        awaitTorchStopped()
    }

    @Test fun leavingTheActivityKeepsAnExplicitTorchRequestRunning() {
        grantPermission(Manifest.permission.CAMERA)
        grantNotifications()
        launchPhone()
        clickTorch()
        awaitTorchRunning()
        var taskId = -1
        scenario!!.onActivity { taskId = it.taskId }
        device.pressHome()
        eventually("Phone Activity did not stop after Home") { scenario!!.state == Lifecycle.State.CREATED }
        assertTrue(controls.flashOn.value)
        assertTrue(torchEnabled.get())
        assertTrue(DeviceTestSupport.serviceRunning(serviceName, foregroundOnly = true))
        focusTask(taskId)
        eventually("Phone Activity did not resume") { scenario!!.state == Lifecycle.State.RESUMED }
        clickTorch()
        awaitTorchStopped()
    }

    @Test fun finishingTheActivityStopsTorchServiceAndRemovesNotification() {
        grantPermission(Manifest.permission.CAMERA)
        grantNotifications()
        launchPhone()
        onMain { controls.setBlinking(true, 200) }
        clickTorch()
        awaitTorchRunning()
        eventually("Blinking did not acquire its CPU lock") { signalCpuLockHeld() }
        scenario!!.close()
        scenario = null
        awaitTorchStopped()
        assertTrue(writes.contains(false))
    }

    @Test fun blinkingContinuesWithScreenOffAndSteadyModeReleasesTheCpuLock() {
        grantPermission(Manifest.permission.CAMERA)
        grantNotifications()
        launchPhone()
        clickTorch()
        awaitTorchRunning()
        assertFalse("Steady torch unnecessarily holds a CPU lock", signalCpuLockHeld())
        onMain { controls.setBlinking(true, 200) }
        eventually("Blinking did not acquire its CPU lock") { signalCpuLockHeld() }
        try {
            device.sleep()
            awaitScreenOffPhases()
            onMain { controls.setBlinking(false, 200) }
            eventually("Changing to steady torch did not release its CPU lock") {
                !signalCpuLockHeld() && torchEnabled.get() && controls.flashOn.value
            }
            assertTrue(DeviceTestSupport.serviceRunning(serviceName, foregroundOnly = true))
            onMain { controls.setFlash(false) }
            awaitTorchStopped()
        } finally {
            device.wakeUp()
        }
    }

    @Test fun sosContinuesWithScreenOffAndStoppingReleasesTheCpuLock() {
        grantPermission(Manifest.permission.CAMERA)
        grantNotifications()
        launchPhone()
        clickTorch()
        awaitTorchRunning()
        onMain { controls.switchSOS(true) }
        eventually("SOS did not acquire its CPU lock") { signalCpuLockHeld() }
        try {
            device.sleep()
            awaitScreenOffPhases()
            onMain { controls.setFlash(false) }
            awaitTorchStopped()
        } finally {
            device.wakeUp()
        }
    }

    @Test fun failedBlinkingTorchReleasesTheCpuLockAndStopsTheService() {
        grantPermission(Manifest.permission.CAMERA)
        grantNotifications()
        launchPhone()
        onMain { controls.setBlinking(true, 200) }
        failEnable.set(true)
        clickTorch()
        eventually("Blinking service never attempted to enable the torch") { writes.contains(true) }
        awaitTorchStopped()
    }

    @Test fun recreationPreservesScreenSelectionAndBlinkMode() {
        launchPhone()
        compose.onNodeWithContentDescription(text(R.string.green_signal)).performClick()
        compose.onNodeWithContentDescription(text(R.string.blink_signal)).performClick()
        assertTrue(controls.blinkOn.value)
        scenario!!.recreate()
        awaitMainControls()
        compose.onNodeWithContentDescription(text(R.string.green_signal)).assertIsSelected()
        compose.onNodeWithContentDescription(text(R.string.blink_signal)).assertIsOn()
        assertTrue(controls.blinkOn.value)
        assertFalse(controls.flashOn.value)
    }

    @Test fun receiverIsRegisteredOnlyWhileTheActivityIsStarted() {
        assertPermissionDenied(Manifest.permission.CAMERA)
        launchPhone()
        scenario!!.moveToState(Lifecycle.State.CREATED)
        sendPermissionBroadcast()
        DeviceTestSupport.instrumentation.waitForIdleSync()
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText(text(R.string.dialog_title_pre_explain)).assertDoesNotExist()
        sendPermissionBroadcast()
        awaitText(R.string.dialog_title_pre_explain)
        compose.onNodeWithText(text(R.string.dialog_title_pre_explain)).assertIsDisplayed()
        scenario!!.recreate()
        awaitMainControls()
        sendPermissionBroadcast()
        awaitText(R.string.dialog_title_pre_explain)
        compose.onNodeWithText(text(R.string.dialog_title_pre_explain)).assertIsDisplayed()
    }

    @Test fun serviceRejectsMissingCameraEvenIfStartedDirectly() {
        assertPermissionDenied(Manifest.permission.CAMERA)
        launchPhone()
        scenario!!.onActivity { activity ->
            ContextCompat.startForegroundService(activity, Intent(activity, FlashLightService::class.java))
        }
        awaitText(R.string.dialog_title_pre_explain)
        compose.onNodeWithText(text(R.string.dialog_title_pre_explain)).assertIsDisplayed()
        eventually("Service did not stop after detecting missing camera permission") {
            !DeviceTestSupport.serviceRunning(serviceName)
        }
        assertFalse(controls.flashOn.value)
        assertFalse(writes.contains(true))
    }

    @Test fun unavailableTorchStopsTheRealServiceAndRecoversUiState() {
        grantPermission(Manifest.permission.CAMERA)
        grantNotifications()
        failEnable.set(true)
        launchPhone()
        clickTorch()
        eventually("Service never attempted to enable the torch") { writes.contains(true) }
        awaitTorchStopped()
        assertFalse(controls.flashOn.value)
    }

    private fun launchPhone() {
        scenario = ActivityScenario.launch(PhoneMainActivity::class.java)
        awaitMainControls()
    }

    private fun awaitMainControls() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithContentDescription(text(R.string.flashlight))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun awaitText(id: Int) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text(id)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun clickTorch() = compose.onNodeWithContentDescription(text(R.string.flashlight)).performClick()
    private fun text(id: Int) = context.getString(id)

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= 33) grantPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun awaitTorchRunning() = eventually("Torch did not enter the foreground") {
        controls.flashOn.value && torchEnabled.get() && DeviceTestSupport.serviceRunning(serviceName, true)
    }

    private fun awaitTorchStopped() = eventually("Torch service or notification survived shutdown") {
        !controls.flashOn.value && !torchEnabled.get() && !DeviceTestSupport.serviceRunning(serviceName) &&
            notifications.activeNotifications.none { it.id == NOTIF_ID } && !signalCpuLockHeld()
    }

    private fun signalCpuLockHeld(): Boolean {
        val tag = "${context.packageName}:FlashLightService"
        return device.executeShellCommand("dumpsys power").lineSequence().any {
            it.contains("PARTIAL_WAKE_LOCK") && it.contains(tag)
        }
    }

    private fun awaitScreenOffPhases() {
        val power = context.getSystemService(PowerManager::class.java)
        eventually("Display did not turn off") { !power.isInteractive }
        assertTrue("Service lost its CPU lock with the display off", signalCpuLockHeld())
        val before = writes.size
        eventually("LED phases stopped while the display was off", timeoutMillis = 10_000) {
            val phases = writes.drop(before)
            phases.count { it } >= 3 && phases.count { !it } >= 3
        }
        assertFalse("Display woke while checking LED phases", power.isInteractive)
        assertTrue("Service released its CPU lock during an active signal", signalCpuLockHeld())
    }

    private fun sendPermissionBroadcast() {
        context.sendBroadcast(Intent(ACTION_PERMISSIONS_MISSING).setPackage(context.packageName))
    }

    private fun permissionButton(vararg ids: String): androidx.test.uiautomator.UiObject2 {
        val pattern = Pattern.compile(".*:id/(" + ids.joinToString("|") + ")")
        return checkNotNull(device.wait(Until.findObject(By.res(pattern)), 10_000)) {
            "System permission button missing: ${ids.toList()}"
        }
    }
}
