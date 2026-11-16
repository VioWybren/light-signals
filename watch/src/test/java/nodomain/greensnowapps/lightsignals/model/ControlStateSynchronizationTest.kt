@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies legacy publication gates and suppression of transient signal phases.
 */
class ControlStateSynchronizationTest {
    /**
     * Owns the fakes, observed events and coroutine test state used by this test suite.
     *
     * @param scope Coroutine test scope that owns the fixture's jobs and virtual-time
     *     scheduler.
     */
    private class Fixture(scope: TestScope) {
        /**
         * Control state or injected repository used by this test fixture.
         */
        val controls = MutableStateFlow(WearControlState())
        /**
         * Mutable connection gate used by the synchronization test.
         */
        val connected = MutableStateFlow(false)
        /**
         * Mutable foreground gate used by the synchronization test.
         */
        val foreground = MutableStateFlow(false)
        /**
         * Mutable initialization gate used by the synchronization test.
         */
        val initialized = MutableStateFlow(false)
        /**
         * Snapshots observed by the fake publisher.
         */
        val sent = mutableListOf<WearControlState>()
        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                statesToSynchronize(controls, connected, foreground, initialized).collect { sent += it }
            }
        }
    }

    /**
     * Verifies that no snapshot is sent until connection foreground and preferences are ready.
     */
    @Test fun noSnapshotIsSentUntilConnectionForegroundAndPreferencesAreReady() = runTest {
        val f = Fixture(this)
        f.connected.value = true
        runCurrent()
        f.foreground.value = true
        runCurrent()
        assertTrue(f.sent.isEmpty())
        f.controls.value = WearControlState(activeSound = 2)
        f.initialized.value = true
        runCurrent()
        assertEquals(listOf(f.controls.value), f.sent)
    }

    /**
     * Verifies that reconnect resends unchanged controls and includes offline changes.
     */
    @Test fun reconnectResendsUnchangedControlsAndIncludesOfflineChanges() = runTest {
        val f = Fixture(this)
        f.connected.value = true
        f.foreground.value = true
        f.initialized.value = true
        runCurrent()
        val original = f.controls.value
        f.connected.value = false
        runCurrent()
        f.connected.value = true
        runCurrent()
        assertEquals(listOf(original, original), f.sent)
        f.connected.value = false
        runCurrent()
        f.controls.value = original.copy(sosEnabled = true)
        runCurrent()
        assertEquals(2, f.sent.size)
        f.connected.value = true
        runCurrent()
        assertEquals(f.controls.value, f.sent.last())
        assertEquals(3, f.sent.size)
    }

    /**
     * Verifies that background state changes are sent only after foreground resume.
     */
    @Test fun backgroundStateChangesAreSentOnlyAfterForegroundResume() = runTest {
        val f = Fixture(this)
        f.connected.value = true
        f.foreground.value = true
        f.initialized.value = true
        runCurrent()
        f.foreground.value = false
        runCurrent()
        f.controls.value = WearControlState(blinkEnabled = true)
        runCurrent()
        assertEquals(1, f.sent.size)
        f.foreground.value = true
        runCurrent()
        assertEquals(listOf(WearControlState(), f.controls.value), f.sent)
    }

    /**
     * Verifies that losing readiness stops sending until the new state is ready.
     */
    @Test fun losingReadinessStopsSendingUntilTheNewStateIsReady() = runTest {
        val f = Fixture(this)
        f.connected.value = true
        f.foreground.value = true
        f.initialized.value = true
        runCurrent()
        f.initialized.value = false
        runCurrent()
        f.controls.value = WearControlState(activeSound = 3)
        runCurrent()
        assertEquals(1, f.sent.size)
        f.initialized.value = true
        runCurrent()
        assertEquals(f.controls.value, f.sent.last())
        assertEquals(2, f.sent.size)
    }
}
