@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package nodomain.greensnowapps.lightsignals

import android.view.Window
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import nodomain.greensnowapps.lightsignals.model.WearControlRepository
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.context
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.device
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.eventually
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.onMain
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.focusTask
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class WearAndroidLifecycleTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @Inject lateinit var controls: WearControlRepository
    private var scenario: ActivityScenario<WearMainActivity>? = null
    private var previousMediaVolume: Int? = null

    @Before fun launch() {
        device.wakeUp()
        previousMediaVolume = context.getSystemService(AudioManager::class.java)
            .getStreamVolume(AudioManager.STREAM_MUSIC)
        hilt.inject()
        scenario = ActivityScenario.launch(WearMainActivity::class.java)
        awaitMainControls()
    }

    @After fun close() {
        scenario?.close()
        previousMediaVolume?.let { volume ->
            context.getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        }
    }

    @Test fun homeStopsTheForegroundSessionAndResumeStartsItAgain() {
        val window = window()
        var taskId = -1
        lateinit var activityLifecycle: Lifecycle
        scenario!!.onActivity {
            taskId = it.taskId
            activityLifecycle = it.lifecycle
        }
        assertTrue(controls.isForeground.value)
        assertTrue(keepsScreenOn(window))
        onMain { controls.setBlinking(true, 100L) }
        eventually("Watch signal never started") { controls.colorBlinkActive.value }
        device.pressHome()
        eventually("Watch Activity did not stop after Home") {
            onMain { activityLifecycle.currentState == Lifecycle.State.CREATED } && !controls.isForeground.value
        }
        assertFalse(controls.colorBlinkActive.value)
        assertFalse(keepsScreenOn(window))
        focusTask(taskId)
        eventually("Watch Activity did not resume") { onMain { activityLifecycle.currentState == Lifecycle.State.RESUMED } }
        eventually("Watch signal did not resume") { controls.isForeground.value && controls.colorBlinkActive.value }
        assertTrue(keepsScreenOn(window))
    }

    @Test fun cachedSignalPageDoesNotChangeBrightnessUntilSelectedAndRestoresOnExit() {
        val window = window()
        setBrightness(window, 0.37f)
        compose.waitForIdle()
        assertEquals(0.37f, brightness(window), 0.001f)
        onMain { controls.updateScreenIndex(1) }
        awaitBrightness(window, 1f)
        onMain { controls.updateScreenIndex(0) }
        awaitBrightness(window, 0.37f)
    }

    @Test fun homeRestoresBrightnessAndResumeReappliesTheSignalBrightness() {
        val window = window()
        var taskId = -1
        lateinit var activityLifecycle: Lifecycle
        scenario!!.onActivity {
            taskId = it.taskId
            activityLifecycle = it.lifecycle
        }
        setBrightness(window, 0.37f)
        onMain { controls.updateScreenIndex(1) }
        awaitBrightness(window, 1f)
        device.pressHome()
        eventually("Signal Activity did not stop") { !controls.isForeground.value }
        eventually("Brightness was not restored when stopped") { brightness(window) == 0.37f }
        focusTask(taskId)
        eventually("Watch Activity did not resume") { onMain { activityLifecycle.currentState == Lifecycle.State.RESUMED } }
        awaitBrightness(window, 1f)
    }

    @Test fun recreationRetainsSelectedModeAndReplacesTheActivityOwner() {
        compose.onNodeWithContentDescription(context.getString(R.string.control_sos)).performClick()
        eventually("SOS was not selected") { controls.sosOn.value }
        scenario!!.recreate()
        awaitMainControls()
        assertTrue(controls.isForeground.value)
        assertTrue(controls.sosOn.value)
        assertFalse(controls.blinkOn.value)
        scenario!!.moveToState(Lifecycle.State.CREATED)
        assertFalse(controls.isForeground.value)
        assertFalse(controls.colorBlinkActive.value)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        eventually("New owner did not restart SOS") { controls.isForeground.value && controls.colorBlinkActive.value }
    }

    @Test fun finishingReleasesTheWindowAndSignalSession() {
        val window = window()
        setBrightness(window, 0.25f)
        onMain {
            controls.setBlinking(true, 100L)
            controls.updateScreenIndex(1)
        }
        awaitBrightness(window, 1f)
        scenario!!.close()
        scenario = null
        assertFalse(controls.isForeground.value)
        assertFalse(controls.colorBlinkActive.value)
        assertFalse(keepsScreenOn(window))
        assertEquals(0.25f, brightness(window), 0.001f)
    }

    private fun awaitMainControls() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithContentDescription(context.getString(R.string.control_sos))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun window(): Window {
        val result = AtomicReference<Window>()
        scenario!!.onActivity { result.set(it.window) }
        return result.get()
    }

    private fun brightness(window: Window) = onMain { window.attributes.screenBrightness }

    private fun setBrightness(window: Window, value: Float) = onMain {
        window.attributes = window.attributes.also { it.screenBrightness = value }
    }

    private fun keepsScreenOn(window: Window) = onMain {
        window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    }

    private fun awaitBrightness(window: Window, expected: Float) {
        compose.waitUntil(timeoutMillis = 15_000) { kotlin.math.abs(brightness(window) - expected) < 0.001f }
    }
}
