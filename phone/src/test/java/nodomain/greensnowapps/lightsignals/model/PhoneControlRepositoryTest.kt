@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.testing.FakePreferencesDataStore
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import org.junit.Assert.*
import org.junit.Test

class PhoneControlRepositoryTest {
    private class Fixture(scope: TestScope, saved: Preferences = emptyPreferences()) {
        val store = FakePreferencesDataStore(saved)
        val remote = MutableStateFlow<PhoneControlState?>(null)
        val errors = mutableListOf<IOException>()
        var serviceStarts = 0
        var serviceStops = 0
        var serviceAvailable = true
        val repo = PhoneControlRepository(store, remote,
            { serviceStarts++; serviceAvailable }, { serviceStops++ },
            scope.backgroundScope, scope.backgroundScope, { errors += it })
    }

    @Test fun initializationWaitsForPreferencesAndIsIdempotent() = runTest {
        val f = Fixture(this, preferencesOf(KEY_ACTIVE_SOUND to 2, KEY_BLINK_DELAY to 325L))
        val gate = CompletableDeferred<Unit>()
        f.store.readGate = gate
        f.repo.initFromPrefs()
        f.repo.initFromPrefs()
        runCurrent()
        assertFalse(f.repo.initialized.value)
        gate.complete(Unit)
        runCurrent()
        assertTrue(f.repo.initialized.value)
        assertEquals(325L, f.repo.blinkDelay.value)
        assertEquals(2, f.repo.activeSound.value)
        assertEquals(1, f.remote.subscriptionCount.value)
        assertEquals(0, f.serviceStarts)
    }

    @Test fun restorationGivesSosPrecedenceWithoutStartingSavedTorch() = runTest {
        val f = Fixture(this, preferencesOf(KEY_SOS_ON to true, KEY_BLINK_ON to true))
        f.repo.initFromPrefs()
        runCurrent()
        assertTrue(f.repo.sosOn.value)
        assertFalse(f.repo.blinkOn.value)
        assertFalse(f.repo.flashOn.value)
        assertEquals(0, f.serviceStarts)
    }

    @Test fun remoteStateIsValidatedAppliedAndPersistedTogether() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        f.remote.value = PhoneControlState(true, true, false, -1L, 99, Float.NaN, 99, 99)
        runCurrent()
        assertTrue(f.repo.sosOn.value)
        assertFalse(f.repo.blinkOn.value)
        assertEquals(100L, f.repo.blinkDelay.value)
        assertEquals(3, f.repo.activeSound.value)
        assertEquals(1, f.repo.screenIndex.value)
        assertEquals(Active.FLASH, f.repo.activeButton.value)
        assertEquals(DEFAULT_COLOR_PROGRESS, f.store.snapshot[KEY_COLOR_PROGRESS])
        assertEquals(100f, f.store.snapshot[KEY_FREQUENCY_PROGRESS])
        assertEquals(false, f.store.snapshot[KEY_BLINK_ON])
        assertEquals(true, f.store.snapshot[KEY_SOS_ON])
    }

    @Test fun startingTorchIsIdempotentAndStoppingItDoesNotClearScreenMode() = runTest {
        val f = Fixture(this)
        f.repo.setBlinking(true, 300L)
        runCurrent()
        f.repo.setFlash(true)
        f.repo.setFlash(true)
        runCurrent()
        assertEquals(1, f.serviceStarts)
        assertTrue(f.repo.flashOn.value)
        assertFalse(f.repo.colorBlinkActive.value)
        f.repo.setFlash(false)
        runCurrent()
        assertTrue(f.repo.blinkOn.value)
        assertTrue(f.repo.colorBlinkActive.value)
    }

    @Test fun failedServiceStartReturnsToSafeScreenState() = runTest {
        val f = Fixture(this)
        f.serviceAvailable = false
        f.repo.setBlinking(true, 300L)
        f.repo.setFlash(true)
        runCurrent()
        assertFalse(f.repo.flashOn.value)
        assertEquals(false, f.store.snapshot[KEY_FLASH_ON])
        assertTrue(f.repo.colorBlinkActive.value)
        assertTrue(f.serviceStops > 0)
    }

    @Test fun modeSwitchCancelsSosAndUsesOnlyTheNewBlinkPeriod() = runTest {
        val f = Fixture(this)
        f.repo.switchSOS(true)
        runCurrent()
        advanceTimeBy(100L)
        f.repo.setBlinking(true, 350L)
        runCurrent()
        assertFalse(f.repo.sosOn.value)
        assertTrue(f.repo.blinkOn.value)
        advanceTimeBy(100L)
        runCurrent()
        assertTrue("The old SOS edge at 200 ms must be cancelled", f.repo.colorBlinkActive.value)
        advanceTimeBy(250L)
        runCurrent()
        assertFalse(f.repo.colorBlinkActive.value)
    }

    @Test fun clearingCancelsSignalsAndRemoteCollectionButKeepsQueuedWrites() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        f.repo.switchSOS(true)
        runCurrent()
        f.repo.setActiveSound(3)
        f.repo.clearAll()
        runCurrent()
        assertFalse(f.repo.initialized.value)
        assertFalse(f.repo.flashOn.value)
        assertFalse(f.repo.colorBlinkActive.value)
        assertEquals(0, f.remote.subscriptionCount.value)
        assertEquals(3, f.store.snapshot[KEY_ACTIVE_SOUND])
        f.remote.value = PhoneControlState(torchOn = true)
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(0, f.serviceStarts)
        assertFalse(f.repo.colorBlinkActive.value)
    }

    @Test fun invalidUiValuesAreClampedAndLatestWriteWins() = runTest {
        val f = Fixture(this)
        f.repo.setActiveSound(-9)
        f.repo.setActiveSound(99)
        f.repo.updateScreenIndex(99)
        f.repo.updateColorProgress(Float.NaN)
        f.repo.updateFrequencyProgress(Float.POSITIVE_INFINITY)
        f.repo.setBlinking(true, Long.MIN_VALUE)
        runCurrent()
        assertEquals(3, f.store.snapshot[KEY_ACTIVE_SOUND])
        assertEquals(1, f.store.snapshot[KEY_ACTIVE_SCREEN])
        assertEquals(DEFAULT_COLOR_PROGRESS, f.store.snapshot[KEY_COLOR_PROGRESS])
        assertEquals(500f, f.store.snapshot[KEY_FREQUENCY_PROGRESS])
        assertEquals(100L, f.repo.blinkDelay.value)
    }

    @Test fun readFailureInitializesDefaultsAndReportsTheError() = runTest {
        val f = Fixture(this)
        val failure = IOException("unreadable preferences")
        f.store.readFailure = failure
        f.repo.initFromPrefs()
        runCurrent()
        assertTrue(f.repo.initialized.value)
        assertEquals(500L, f.repo.blinkDelay.value)
        assertEquals(listOf(failure), f.errors)
        assertEquals(0, f.serviceStarts)
    }

    @Test fun failedWriteDoesNotPreventLaterWrites() = runTest {
        val f = Fixture(this)
        val failure = IOException("disk unavailable")
        f.store.nextWriteFailure = failure
        f.repo.setActiveSound(1)
        runCurrent()
        f.repo.setActiveSound(2)
        runCurrent()
        assertEquals(listOf(failure), f.errors)
        assertEquals(2, f.store.snapshot[KEY_ACTIVE_SOUND])
        assertEquals(2, f.repo.activeSound.value)
    }
}
