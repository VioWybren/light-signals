package nodomain.greensnowapps.lightsignals

import android.Manifest
import android.app.KeyguardManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.RequiresDevice
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeReply
import nodomain.greensnowapps.lightsignals.model.FlashLightService
import nodomain.greensnowapps.lightsignals.model.PhoneControlRepository
import nodomain.greensnowapps.lightsignals.model.PhoneSyncStateRepository
import nodomain.greensnowapps.lightsignals.model.PhoneTorchAvailability
import nodomain.greensnowapps.lightsignals.model.PhoneTorchDriver
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.context
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.device
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.eventually
import nodomain.greensnowapps.lightsignals.testing.DeviceTestSupport.onMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies verified watch control and state-preserving reconnect while the phone is stopped or
 * locked.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class PhoneWatchBackgroundBehaviorTest {
    /**
     * JUnit rule that initializes and injects the Hilt test component.
     */
    @get:Rule val hilt = HiltAndroidRule(this)
    /**
     * Hilt test binding that makes torch controls available without requiring real hardware.
     */
    @BindValue @JvmField val torchAvailability = PhoneTorchAvailability { true }
    /**
     * Atomic fake hardware state updated by the test torch driver.
     */
    private val torchEnabled = AtomicBoolean(false)
    /**
     * Injected serialized torch driver; tests can replace it with a recording backend.
     */
    @BindValue @JvmField val torchDriver = PhoneTorchDriver { enabled -> torchEnabled.set(enabled); true }
    /**
     * Control state or injected repository used by this test fixture.
     */
    @Inject lateinit var controls: PhoneControlRepository
    /**
     * Injected phone transport used to exercise the actual RPC handlers in this test.
     */
    @Inject lateinit var sync: PhoneSyncStateRepository
    /**
     * Test-owned ActivityScenario, closed and cleared during cleanup.
     */
    private var scenario: ActivityScenario<PhoneMainActivity>? = null

    /**
     * Prepares the isolated test application, bindings and device state before each case.
     */
    @Before fun prepare() {
        device.wakeUp()
        hilt.inject()
        assertCameraPermissionAbsent()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            DeviceTestSupport.grantPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Stops test-owned signals and restores test-owned Activity, service or device resources
     * after each case.
     */
    @After fun cleanup() {
        device.wakeUp()
        scenario?.close()
        if (this::controls.isInitialized) onMain { controls.clearAll() }
        if (this::sync.isInitialized) onMain { sync.clear() }
        eventually("Test torch service survived cleanup") {
            !DeviceTestSupport.serviceRunning(FlashLightService::class.java.name) && !torchEnabled.get()
        }
    }

    /**
     * Verifies that verified watch can connect and reconnect while phone activity is stopped.
     */
    @Test @RequiresDevice
    fun verifiedWatchCanConnectAndReconnectWhilePhoneActivityIsStopped() {
        val nodes = Tasks.await(Wearable.getCapabilityClient(context).getCapability(
            WatchConnectionRequest.CAPABILITY, CapabilityClient.FILTER_ALL
        ), 15, TimeUnit.SECONDS).nodes
        assumeTrue("This integration case requires a watch with the matching test app installed", nodes.isNotEmpty())
        val nodeId = nodes.first().id
        scenario = ActivityScenario.launch(PhoneMainActivity::class.java)
        eventually("Phone controls did not initialize") { controls.initialized.value && linkRestored() }
        lateinit var lifecycle: Lifecycle
        scenario!!.onActivity { lifecycle = it.lifecycle }
        device.pressHome()
        eventually("Phone Activity did not stop") { onMain { lifecycle.currentState == Lifecycle.State.CREATED } }

        val signal = PhoneSignalState(true, true, false, 350L)
        val first = WatchConnectionRequest.create(signal)
        assertEquals(WatchConnectionReply.ACCEPTED, handoff(nodeId, first))
        eventually("Background watch request did not start the recording torch") { torchEnabled.get() }
        assertTrue(controls.blinkOn.value)

        val disconnectCommand = WatchControlCommand(first.id, 1, WatchControl.DISCONNECT, 0)
        assertArrayEquals(byteArrayOf(WatchControlCommand.ACCEPTED), disconnect(nodeId, disconnectCommand))
        assertTrue("Disconnect must preserve the explicitly running phone signal", controls.flashOn.value)
        assertTrue(controls.blinkOn.value)

        val reconnect = WatchConnectionRequest.create(signal.copy(frequency = 450L))
        assertEquals(WatchConnectionReply.ACCEPTED, handoff(nodeId, reconnect))
        assertEquals(450L, controls.blinkDelay.value)
        assertEquals(Lifecycle.State.CREATED, onMain { lifecycle.currentState })
    }

    /**
     * Verifies that explicit reconnect reads phone state without replacing its running signal.
     */
    @Test @RequiresDevice
    fun explicitReconnectReadsPhoneStateWithoutReplacingItsRunningSignal() {
        val nodes = Tasks.await(Wearable.getCapabilityClient(context).getCapability(
            WatchConnectionRequest.CAPABILITY, CapabilityClient.FILTER_ALL
        ), 15, TimeUnit.SECONDS).nodes
        assumeTrue("This integration case requires a watch with the matching test app installed", nodes.isNotEmpty())
        val nodeId = nodes.first().id
        scenario = ActivityScenario.launch(PhoneMainActivity::class.java)
        eventually("Phone controls did not initialize") { controls.initialized.value && linkRestored() }
        lateinit var lifecycle: Lifecycle
        scenario!!.onActivity { lifecycle = it.lifecycle }
        device.pressHome()
        eventually("Phone Activity did not stop") { onMain { lifecycle.currentState == Lifecycle.State.CREATED } }

        val signal = PhoneSignalState(true, true, false, 350L, 789f, 2, 1, 2)
        val first = WatchConnectionRequest.create(signal)
        assertEquals(WatchConnectionReply.ACCEPTED, handoff(nodeId, first))
        assertEquals(WatchConnectionReply.ACCEPTED, release(nodeId, first))
        // The watch has returned to standalone/off; this must not replace the phone signal.
        val reconnect = WatchConnectionRequest.create(PhoneSignalState(false, false, false, 400L))
        val response = WatchResumeReply.decode(handoffBytes(nodeId, reconnect, "handleWatchStateHandoff"), reconnect.id)
        assertEquals(WatchConnectionReply.ACCEPTED, response.status)
        assertEquals(signal, response.signal)
        assertTrue(controls.flashOn.value)
        assertTrue(controls.blinkOn.value)
        assertFalse(controls.sosOn.value)
        assertEquals(350L, controls.blinkDelay.value)
        assertEquals(Lifecycle.State.CREATED, onMain { lifecycle.currentState })
        eventually("Reconnect must preserve the running torch service") {
            DeviceTestSupport.serviceRunning(FlashLightService::class.java.name)
        }

        assertEquals("A delayed old release must not revoke the fresh session",
            WatchConnectionReply.REJECTED, release(nodeId, first))
        val repeated = WatchResumeReply.decode(handoffBytes(nodeId, reconnect, "handleWatchStateHandoff"), reconnect.id)
        assertEquals(WatchConnectionReply.ACCEPTED, repeated.status)
        assertEquals(signal, repeated.signal)
        val command = WatchControlCommand(reconnect.id, 1, WatchControl.COLOR, 1200f.toBits(),
            signal = signal.copy(color = 1200f))
        assertArrayEquals(byteArrayOf(WatchControlCommand.ACCEPTED), disconnect(nodeId, command))
        assertTrue(controls.flashOn.value)
        assertTrue(controls.blinkOn.value)
        assertEquals(1200f, controls.colorProgress.value)
    }

    /**
     * Verifies that locked cold launch initializes controls before keyguard is dismissed.
     */
    @Test fun lockedColdLaunchInitializesControlsBeforeKeyguardIsDismissed() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assumeTrue("This case requires a secure device lock", keyguard.isDeviceSecure)
        assertFalse("Orchestrator must start this case without a phone Activity", controls.initialized.value)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        var launched: PhoneMainActivity? = null
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is PhoneMainActivity && stage == Stage.CREATED) launched = activity
        }
        onMain { monitor.addLifecycleCallback(callback) }
        try {
            device.sleep()
            device.wakeUp() // A remote launch wakes the display; it must not unlock keyguard.
            assumeTrue("Keyguard must remain locked for this case", keyguard.isKeyguardLocked)
            val result = device.executeShellCommand(
                "am start -n ${context.packageName}/${PhoneMainActivity::class.java.name}"
            )
            assertFalse("Activity launch failed: $result", result.contains("Error:"))
            eventually("Locked launch did not prepare companion controls") {
                onMain { launched != null && controls.initialized.value } && linkRestored()
            }
            assertTrue("Companion initialization must not dismiss keyguard", keyguard.isKeyguardLocked)
        } finally {
            onMain {
                monitor.removeLifecycleCallback(callback)
                launched?.finish()
            }
            device.wakeUp()
        }
    }

    /**
     * Reads the real transport's restored-link gate through test-only reflection on Main.
     *
     * @return True after the repository finishes its initial remembered-link read.
     */
    private fun linkRestored(): Boolean = onMain {
        sync.javaClass.getDeclaredField("linkRestored").apply { isAccessible = true }.getBoolean(sync)
    }

    /**
     * Invokes the real watch-settings handoff handler through the test-only reflection adapter.
     *
     * @param nodeId Simulated watch node ID, still subject to handler verification.
     * @param request Fresh handoff invitation passed to the real handler.
     * @return The decoded connection status.
     */
    // Invoke the real RPC handlers while preserving their token/session/capability checks.
    // Reflection is test-only; the uitest target is not obfuscated.
    private fun handoff(nodeId: String, request: WatchConnectionRequest): WatchConnectionReply {
        return WatchConnectionReply.decode(handoffBytes(nodeId, request, "handleWatchHandoff"))
    }

    /**
     * Invokes a real RPC handler on Main and blocks the test thread for its task result.
     *
     * Preserves the active registration token, scope and capability checks. Reflection targets
     * the unobfuscated uitest application and is not production transport.
     *
     * @param nodeId Simulated peer node ID passed to the handler.
     * @param request Invitation encoded for the handoff.
     * @param handler Private four-argument handoff handler name.
     * @return Raw RPC reply bytes.
     * @throws java.util.concurrent.TimeoutException If the handler task has not completed within
     *     15 seconds.
     */
    @Suppress("UNCHECKED_CAST")
    private fun handoffBytes(nodeId: String, request: WatchConnectionRequest, handler: String): ByteArray {
        val task = onMain {
            val registration = registration()
            val token = registration.javaClass.getDeclaredField("token").apply { isAccessible = true }.get(registration)
            val scope = registration.javaClass.getDeclaredField("scope").apply { isAccessible = true }.get(registration)
            sync.javaClass.getDeclaredMethod(handler, Any::class.java,
                CoroutineScope::class.java, String::class.java, ByteArray::class.java)
                .apply { isAccessible = true }.invoke(sync, token, scope, nodeId, request.encode()) as Task<ByteArray>
        }
        return Tasks.await(task, 15, TimeUnit.SECONDS)
    }

    /**
     * Invokes the real relationship-release handler on Main with the current listener token.
     *
     * @param nodeId Simulated phone-control peer being released.
     * @param request Invitation whose stable relationship ID should be released.
     * @return The decoded release status.
     */
    private fun release(nodeId: String, request: WatchConnectionRequest): WatchConnectionReply = onMain {
        val registration = registration()
        val token = registration.javaClass.getDeclaredField("token").apply { isAccessible = true }.get(registration)
        val result = sync.javaClass.getDeclaredMethod("handleWatchRelease", Any::class.java,
            String::class.java, ByteArray::class.java).apply { isAccessible = true }
            .invoke(sync, token, nodeId, WatchConnectionRequest.encodeId(request.id)) as ByteArray
        WatchConnectionReply.decode(result)
    }

    /**
     * Invokes the real command handler on Main using a test-encoded disconnect command.
     *
     * @param nodeId Simulated sender node ID.
     * @param command Ordered DISCONNECT command to validate.
     * @return Raw command acknowledgment bytes.
     */
    private fun disconnect(nodeId: String, command: WatchControlCommand): ByteArray = onMain {
        val registration = registration()
        val token = registration.javaClass.getDeclaredField("token").apply { isAccessible = true }.get(registration)
        sync.javaClass.getDeclaredMethod("handleWatchControl", Any::class.java, String::class.java,
            ByteArray::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(sync, token, nodeId, command.encode(), false) as ByteArray
    }

    /**
     * Obtains the active RPC registration through test-only reflection.
     *
     * Call on Main after initializing transport.
     *
     * @return The current private registration object.
     * @throws IllegalStateException If transport has no current registration.
     */
    private fun registration(): Any = checkNotNull(
        sync.javaClass.getDeclaredField("rpcRegistration").apply { isAccessible = true }.get(sync)
    )
}
