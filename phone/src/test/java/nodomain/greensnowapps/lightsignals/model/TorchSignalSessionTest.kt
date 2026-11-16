@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import nodomain.greensnowapps.lightsignals.core.signal.runSosSignal

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

/**
 * Verifies wake-lock ownership and cancellation-safe torch cleanup.
 */
class TorchSignalSessionTest {
    /**
     * Verifies that timed signal acquires before its first phase and releases after switch
     * off.
     */
    @Test fun timedSignalAcquiresBeforeItsFirstPhaseAndReleasesAfterSwitchOff() = runTest {
        val f = Fixture()
        f.session.run(keepCpuAwake = true) {
            assertTrue(f.held)
            f.events += "phase"
        }
        assertEquals(listOf("acquire", "phase", "off", "release"), f.events)
        assertFalse(f.held)
    }

    /**
     * Verifies that steady torch does not acquire a CPU wake lock.
     */
    @Test fun steadyTorchDoesNotAcquireACpuWakeLock() = runTest {
        val f = Fixture()
        f.session.run(keepCpuAwake = false) { f.events += "steady" }
        assertEquals(listOf("steady", "off"), f.events)
        assertFalse(f.held)
    }

    /**
     * Verifies that cancellation keeps the lock until suspending torch cleanup finishes.
     */
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

    /**
     * Verifies that SOS keeps the CPU awake across pulses and gaps then releases on
     * cancellation.
     */
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

    /**
     * Verifies that switching from blink to steady releases the previous modes lock.
     */
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

    /**
     * Verifies that signal failure switches off and releases before propagating.
     */
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

    /**
     * Verifies that cleanup failure still releases the CPU lock.
     */
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

    /**
     * Verifies that partially failed acquisition still cleans up without starting the signal.
     */
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

    /**
     * Owns the fakes, observed events and coroutine test state used by this test suite.
     */
    private class Fixture {
        /**
         * Ordered hardware and wake-lock events recorded by the fixture.
         */
        val events = mutableListOf<String>()
        /**
         * Whether the fake wake lock is currently held.
         */
        var held = false
        /**
         * Whether fake wake-lock acquisition should fail.
         */
        var failAcquire = false
        /**
         * Suspending fake torch cleanup operation used by the test.
         */
        var cleanup: suspend () -> Unit = { events += "off" }
        /**
         * Torch signal session exercised by this fixture.
         */
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
