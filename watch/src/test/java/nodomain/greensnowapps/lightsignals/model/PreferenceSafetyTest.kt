package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nodomain.greensnowapps.lightsignals.testing.FakePreferencesDataStore
import org.junit.Assert.*
import org.junit.Test

class PreferenceSafetyTest {
    @Test fun programmingErrorsDuringReadAreNotConvertedToDefaults() = runTest {
        val store = FakePreferencesDataStore()
        val failure = IllegalStateException("broken reader")
        store.readFailure = failure
        val reported = mutableListOf<IOException>()
        try {
            store.safeData { reported += it }.first()
            fail("Programming errors must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertTrue(reported.isEmpty())
    }

    @Test fun cancellationDuringReadIsNotConvertedToDefaults() = runTest {
        val store = FakePreferencesDataStore()
        val cancellation = CancellationException("session stopped")
        store.readFailure = cancellation
        try {
            store.safeData { throw AssertionError(it) }.first()
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test fun programmingErrorsDuringWriteAreNotSwallowed() = runTest {
        val store = FakePreferencesDataStore()
        val failure = IllegalStateException("broken writer")
        store.nextWriteFailure = failure
        try {
            store.editSafely({ throw AssertionError(it) }) { it[intPreferencesKey("value")] = 1 }
            fail("Programming errors must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertTrue(store.snapshot.asMap().isEmpty())
    }

    @Test fun cancellationDuringWriteIsNotSwallowed() = runTest {
        val store = FakePreferencesDataStore()
        val cancellation = CancellationException("write cancelled")
        store.nextWriteFailure = cancellation
        try {
            store.editSafely({ throw AssertionError(it) }) { it[intPreferencesKey("value")] = 1 }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertTrue(store.snapshot.asMap().isEmpty())
    }
}
