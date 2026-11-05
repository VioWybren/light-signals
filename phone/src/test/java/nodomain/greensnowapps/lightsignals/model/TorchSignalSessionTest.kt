@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TorchSignalSessionTest {
    @Test fun timedSignalAcquiresBeforeItsFirstPhaseAndReleasesAfterSwitchOff() = runTest {
        val f = Fixture()
        f.session.run(keepCpuAwake = true) {
            assertTrue(f.held)
            f.events += "phase"
        }
        assertEquals(listOf("acquire", "phase", "off", "release"), f.events)
        assertFalse(f.held)
    }

    @Test fun steadyTorchDoesNotAcquireACpuWakeLock() = runTest {
        val f = Fixture()
        f.session.run(keepCpuAwake = false) { f.events += "steady" }
        assertEquals(listOf("steady", "off"), f.events)
        assertFalse(f.held)
    }

    @Test fun cancellationKeepsTheLockUntilSuspendingTorchCleanupFinishes() = runTest {
        val f = Fixture()
        f.cleanup = {
            assertTrue(f.held)
            f.events += "cleanup"
            delay(250)
            assertTrue(f.held)
            f.events += "off"
        }
        val job = launch { f.session.run(keepCpuAwake = true) { awaitCancellation() } }
        runCurrent()
        job.cancel()
        runCurrent()
        assertTrue(f.held)
        assertEquals(listOf("acquire", "cleanup"), f.events)
        advanceUntilIdle()
        job.join()
        assertEquals(listOf("acquire", "cleanup", "off", "release"), f.events)
        assertFalse(f.held)
    }

    @Test fun sosKeepsTheCpuAwakeAcrossPulsesAndGapsThenReleasesOnCancellation() = runTest {
        val f = Fixture()
        val phases = mutableListOf<Boolean>()
        val job = launch {
            f.session.run(keepCpuAwake = true) {
                runSosSignal(200) { enabled ->
                    assertTrue("CPU lock missing during SOS phase", f.held)
                    phases += enabled
                    true
                }
            }
        }
        runCurrent()
        advanceTimeBy(6_100)
        runCurrent()
        assertTrue(f.held)
        assertTrue(phases.containsAll(listOf(true, false)))
        job.cancelAndJoin()
        assertFalse(f.held)
        assertEquals(listOf("acquire", "off", "release"), f.events)
    }

    @Test fun switchingFromBlinkToSteadyReleasesThePreviousModesLock() = runTest {
        val f = Fixture()
        val blink = MutableStateFlow(true)
        val job = launch {
            blink.collectLatest { blinking ->
                f.session.run(keepCpuAwake = blinking) {
                    f.events += if (blinking) "blink" else "steady"
                    awaitCancellation()
                }
            }
        }
        runCurrent()
        blink.value = false
        runCurrent()
        assertEquals(listOf("acquire", "blink", "off", "release", "steady"), f.events)
        assertFalse(f.held)
        job.cancelAndJoin()
        assertEquals("off", f.events.last())
    }

    @Test fun signalFailureSwitchesOffAndReleasesBeforePropagating() = runTest {
        val f = Fixture()
        val failure = IllegalStateException("Torch failed")
        val actual = try {
            f.session.run(keepCpuAwake = true) { throw failure }
            null
        } catch (error: IllegalStateException) { error }
        assertEquals(failure.javaClass, actual?.javaClass)
        assertEquals(failure.message, actual?.message)
        assertEquals(listOf("acquire", "off", "release"), f.events)
        assertFalse(f.held)
    }

    @Test fun cleanupFailureStillReleasesTheCpuLock() = runTest {
        val f = Fixture()
        val failure = IllegalStateException("Torch cleanup failed")
        f.cleanup = { throw failure }
        val actual = try {
            f.session.run(keepCpuAwake = true) {}
            null
        } catch (error: IllegalStateException) { error }
        // Coroutine stack-trace recovery can copy exceptions across withContext.
        assertEquals(failure.javaClass, actual?.javaClass)
        assertEquals(failure.message, actual?.message)
        assertEquals(listOf("acquire", "release"), f.events)
        assertFalse(f.held)
    }

    @Test fun partiallyFailedAcquisitionStillCleansUpWithoutStartingTheSignal() = runTest {
        val f = Fixture()
        f.failAcquire = true
        val actual = try {
            f.session.run(keepCpuAwake = true) { fail("Signal started after acquisition failure") }
            null
        } catch (error: SecurityException) { error }
        assertNotNull(actual)
        assertEquals(listOf("acquire", "off", "release"), f.events)
        assertFalse(f.held)
    }

    private class Fixture {
        val events = mutableListOf<String>()
        var held = false
        var failAcquire = false
        var cleanup: suspend () -> Unit = { events += "off" }
        val session = TorchSignalSession(
            acquireWakeLock = {
                events += "acquire"
                held = true
                if (failAcquire) throw SecurityException("Acquisition failed")
            },
            releaseWakeLock = {
                if (held) {
                    events += "release"
                    held = false
                }
            },
            switchOffTorch = { cleanup() }
        )
    }
}
