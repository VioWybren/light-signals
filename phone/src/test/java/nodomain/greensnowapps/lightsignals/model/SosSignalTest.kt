@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nodomain.greensnowapps.lightsignals.testing.ContractFixtures
import org.junit.Assert.*
import org.junit.Test

class SosSignalTest {
    @Test fun fullCycleMatchesTheSharedMorseTimingContract() = runTest {
        val edges = mutableListOf<Pair<Long, Boolean>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            runSosSignal(nodomain.greensnowapps.lightsignals.SOS_UNIT_MS) { enabled -> edges += currentTime to enabled; true }
        }
        val expected = ContractFixtures.sosEdges()
        advanceTimeBy(expected.last().first)
        runCurrent()
        assertEquals(expected, edges)
        job.cancelAndJoin()
    }

    @Test fun cancellationDuringDotDashOrGapPreventsEveryLaterEdge() = runTest {
        for (cancelAt in listOf(100L, 1800L, 5700L)) {
            val edges = mutableListOf<Pair<Long, Boolean>>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                runSosSignal(nodomain.greensnowapps.lightsignals.SOS_UNIT_MS) { enabled -> edges += currentTime to enabled; true }
            }
            advanceTimeBy(cancelAt)
            runCurrent()
            job.cancelAndJoin()
            val before = edges.toList()
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals("An edge escaped cancellation at $cancelAt ms", before, edges)
        }
    }

    @Test fun failedEnableStopsWithoutSchedulingAnotherPulse() = runTest {
        val writes = mutableListOf<Boolean>()
        runSosSignal(nodomain.greensnowapps.lightsignals.SOS_UNIT_MS) { enabled -> writes += enabled; false }
        assertEquals(listOf(true), writes)
        assertEquals(0L, currentTime)
    }
}
