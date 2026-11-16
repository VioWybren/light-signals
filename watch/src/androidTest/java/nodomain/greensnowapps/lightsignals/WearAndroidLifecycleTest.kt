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

/**
 * Verifies watch foreground restoration, navigation and brightness cleanup.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class WearAndroidLifecycleTest {
    /**
     * JUnit rule that initializes and injects the Hilt test component.
     */
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    /**
     * JUnit rule used to query and operate the target app's Compose UI.
     */
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    /**
     * Control state or injected repository used by this test fixture.
     */
    @Inject lateinit var controls: WearControlRepository
    /**
     * Test-owned ActivityScenario, closed and cleared during cleanup.
     */
    private var scenario: ActivityScenario<WearMainActivity>? = null
    /**
     * Original media-volume index restored after the watch lifecycle case.
     */
    private var previousMediaVolume: Int? = null

    /**
     * Saves the media volume, injects test dependencies and launches the isolated watch
     * Activity.
     */
    @Before fun launch() {
        device.wakeUp()
        previousMediaVolume = context.getSystemService(AudioManager::class.java)
            .getStreamVolume(AudioManager.STREAM_MUSIC)
        hilt.inject()
        scenario = ActivityScenario.launch(WearMainActivity::class.java)
        awaitMainControls()
    }

    /**
     * Stops test-owned signals and restores test-owned Activity, service or device resources
     * after each case.
     */
    @After fun close() {
        scenario?.close()
        previousMediaVolume?.let { volume ->
            context.getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        }
    }

    /**
     * Verifies that home stops the foreground session and resume starts it again.
     */
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

    /**
     * Verifies that cached signal page does not change brightness until selected and restores
     * on exit.
     */
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

    /**
     * Verifies that home restores brightness and resume reapplies the signal brightness.
     */
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

    /**
     * Verifies that recreation retains selected mode and replaces the activity owner.
     */
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

    /**
     * Verifies that finishing releases the window and signal session.
     */
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

    /**
     * Waits up to 20 seconds on the test thread for the watch SOS control semantics.
     */
    private fun awaitMainControls() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithContentDescription(context.getString(R.string.control_sos))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    /**
     * Obtains the launched test Activity's window through ActivityScenario on Main.
     *
     * Call on the instrumentation thread with a live scenario.
     *
     * @return The target watch Activity window.
     */
    private fun window(): Window {
        val result = AtomicReference<Window>()
        scenario!!.onActivity { result.set(it.window) }
        return result.get()
    }

    /**
     * Marshals a window brightness-override read to Main.
     *
     * @param window Target Activity window to inspect.
     * @return The brightness fraction, or the platform default sentinel.
     */
    private fun brightness(window: Window) = onMain { window.attributes.screenBrightness }

    /**
     * Marshals a window brightness-override update to Main.
     *
     * @param window Target Activity window to update.
     * @param value Brightness override used as the test baseline.
     */
    private fun setBrightness(window: Window, value: Float) = onMain {
        window.attributes = window.attributes.also { it.screenBrightness = value }
    }

    /**
     * Reads the target window's keep-screen-on flag on Main.
     *
     * @param window Target Activity window to inspect.
     * @return True when FLAG_KEEP_SCREEN_ON is set.
     */
    private fun keepsScreenOn(window: Window) = onMain {
        window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    }

    /**
     * Waits on the test thread for the Main-thread window brightness override to match within
     * 0.001.
     *
     * @param window Target Activity window to inspect.
     * @param expected Expected brightness override.
     */
    private fun awaitBrightness(window: Window, expected: Float) {
        compose.waitUntil(timeoutMillis = 15_000) { kotlin.math.abs(brightness(window) - expected) < 0.001f }
    }
}
