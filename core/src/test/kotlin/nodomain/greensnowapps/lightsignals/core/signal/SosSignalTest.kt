@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.core.signal

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies SOS pulse timing, cancellation and output failure behavior.
 */
class SosSignalTest {
    /**
     * Verifies that full cycle matches the shared morse timing contract.
     */
    @Test fun fullCycleMatchesTheSharedMorseTimingContract() = runTest {
        val edges = mutableListOf<Pair<Long, Boolean>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            runSosSignal(SOS_UNIT_MS) { enabled -> edges += currentTime to enabled; true }
        }
        val expected = expectedEdges()
        advanceTimeBy(expected.last().first)
        runCurrent()
        assertEquals(expected, edges)
        job.cancelAndJoin()
    }

    /**
     * Verifies that cancellation during dot dash or gap prevents every later edge.
     */
    @Test fun cancellationDuringDotDashOrGapPreventsEveryLaterEdge() = runTest {
        for (cancelAt in listOf(100L, 1800L, 5700L)) {
            val edges = mutableListOf<Pair<Long, Boolean>>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                runSosSignal(SOS_UNIT_MS) { enabled -> edges += currentTime to enabled; true }
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

    /**
     * Verifies that failed enable stops without scheduling another pulse.
     */
    @Test fun failedEnableStopsWithoutSchedulingAnotherPulse() = runTest {
        val writes = mutableListOf<Boolean>()
        runSosSignal(SOS_UNIT_MS) { enabled -> writes += enabled; false }
        assertEquals(listOf(true), writes)
        assertEquals(0L, currentTime)
    }

    /**
     * Loads expected absolute SOS edges from the independent CSV contract and closes the reader.
     *
     * @return Timestamp and output-state pairs from the shared timing fixture.
     * @throws IllegalStateException If the independent timing fixture is missing.
     */
    private fun expectedEdges(): List<Pair<Long, Boolean>> = checkNotNull(
        javaClass.getResourceAsStream("/contracts/sos-v1.csv")
    ) { "Missing independent SOS timing fixture" }.bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val (millis, enabled) = line.split(',')
            millis.toLong() to enabled.toBooleanStrict()
        }.toList()
    }
}
