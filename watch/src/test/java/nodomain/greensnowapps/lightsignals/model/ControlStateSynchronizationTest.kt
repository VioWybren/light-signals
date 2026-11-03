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

class ControlStateSynchronizationTest {
    private class Fixture(scope: TestScope) {
        val controls = MutableStateFlow(WearControlState())
        val connected = MutableStateFlow(false)
        val foreground = MutableStateFlow(false)
        val initialized = MutableStateFlow(false)
        val sent = mutableListOf<WearControlState>()
        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                statesToSynchronize(controls, connected, foreground, initialized).collect { sent += it }
            }
        }
    }

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
