package nodomain.greensnowapps.lightsignals.model

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies serialized torch ownership across competing service sessions.
 */
class TorchSessionControllerTest {
    /**
     * Verifies that old service cannot disable or overwrite replacement service.
     */
    @Test
    fun oldServiceCannotDisableOrOverwriteReplacementService() = runBlocking {
        val writes = mutableListOf<Boolean>()
        val controller = TorchSessionController({ writes += it; true }, Dispatchers.Unconfined)
        val oldService = Any()
        val replacement = Any()
        controller.acquire(oldService)
        controller.setEnabled(oldService, true)
        controller.acquire(replacement)
        controller.setEnabled(replacement, true)

        controller.release(oldService)
        controller.setEnabled(oldService, false)
        assertEquals(listOf(true, true), writes)

        controller.release(replacement)
        assertEquals(listOf(true, true, false), writes)
    }

    /**
     * Verifies that concurrent commands never enter hardware together.
     */
    @Test
    fun concurrentCommandsNeverEnterHardwareTogether() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val writes = Collections.synchronizedList(mutableListOf<Boolean>())
        val controller = TorchSessionController({ enabled ->
            val count = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, count) }
            try {
                Thread.sleep(2)
                writes += enabled
                true
            } finally {
                active.decrementAndGet()
            }
        })
        val service = Any()
        controller.acquire(service)
        val commands = List(30) { index ->
            launch(Dispatchers.Default) { controller.setEnabled(service, index % 2 == 0) }
        }
        commands.forEach { it.join() }
        assertEquals(1, maximum.get())
        assertEquals(30, writes.size)
    }

    /**
     * Verifies that cancellation still releases active session.
     */
    @Test
    fun cancellationStillReleasesActiveSession() = runBlocking {
        val writes = mutableListOf<Boolean>()
        val controller = TorchSessionController({ writes += it; true }, Dispatchers.Unconfined)
        val service = Any()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = launch {
            try {
                controller.acquire(service)
                controller.setEnabled(service, true)
                started.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { controller.release(service) }
            }
        }
        started.await()
        job.cancelAndJoin()
        assertEquals(listOf(true, false), writes)
    }

    /**
     * Verifies that hardware failure is reported and does not keep session locked.
     */
    @Test
    fun hardwareFailureIsReportedAndDoesNotKeepSessionLocked() = runBlocking {
        val controller = TorchSessionController({ enabled -> !enabled }, Dispatchers.Unconfined)
        val service = Any()
        controller.acquire(service)
        assertFalse(controller.setEnabled(service, true))
        assertTrue(controller.setEnabled(service, false))
        controller.release(service)
        controller.acquire(Any())
    }
}
